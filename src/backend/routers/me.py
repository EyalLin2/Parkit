import json
import uuid

import httpx
from fastapi import APIRouter, Depends, HTTPException, Query
from redis.asyncio import Redis
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from auth import get_current_user_id
from database import get_db
from models import User
from redis_client import get_redis
from schemas import CityOut, Preferences, PreferencesUpdate, VehicleLookupOut
from vehicles import lookup, normalise_plate

router = APIRouter(tags=["driver profile"])


def preferences_of(user: User) -> Preferences:
    return Preferences(
        vehicle_size=user.vehicle_size,
        car_label=user.car_label,
        has_disabled_permit=user.has_disabled_permit,
        home_city=user.home_city,
    )


@router.patch("/users/me/preferences", response_model=Preferences)
async def update_preferences(
    payload: PreferencesUpdate,
    user_id: uuid.UUID = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> Preferences:
    user = await db.get(User, user_id)
    if user is None:
        raise HTTPException(404, "Unknown user")
    changes = payload.model_dump(exclude_unset=True)
    for field, value in changes.items():
        setattr(user, field, value)
    await db.commit()
    return preferences_of(user)


@router.get("/vehicles/lookup", response_model=VehicleLookupOut)
async def vehicle_lookup(
    plate: str = Query(min_length=5, max_length=12),
    _user_id: uuid.UUID = Depends(get_current_user_id),
    redis: Redis = Depends(get_redis),
) -> VehicleLookupOut:
    """Make, model and parking size for an Israeli licence plate (open
    Ministry of Transport data). The plate is not stored."""
    digits = normalise_plate(plate)
    if digits is None:
        raise HTTPException(422, "A plate has 5–8 digits")
    try:
        info = await lookup(digits, redis)
    except Exception:
        raise HTTPException(503, "Vehicle registry unavailable, try again or choose a size manually") from None
    if info is None:
        raise HTTPException(404, "Plate not found in the registry")
    return VehicleLookupOut(
        make=info.make, model=info.model, year=info.year, body=info.body, size=info.size, label=info.label
    )


CBS_LOCALITIES = "5c78e9fa-c2e2-4771-93ff-7f400a12f7ba"  # official Israeli locality list (CBS), data.gov.il


async def _official_localities(redis: Redis) -> set[str]:
    cached = await redis.get("cbs:localities")
    if cached is not None:
        return set(json.loads(cached))
    names: set[str] = set()
    async with httpx.AsyncClient(timeout=15) as client:
        resp = await client.get(
            "https://data.gov.il/api/3/action/datastore_search",
            params={"resource_id": CBS_LOCALITIES, "fields": "שם_ישוב", "limit": 5000},
        )
        resp.raise_for_status()
        for rec in resp.json()["result"]["records"]:
            name = " ".join((rec.get("שם_ישוב") or "").split())
            if name:
                names.add(name)
    await redis.set("cbs:localities", json.dumps(sorted(names), ensure_ascii=False), ex=30 * 24 * 3600)
    return names


def _norm(name: str) -> str:
    """Spelling-insensitive key: "תל אביב - יפו" == "תל אביב-יפו",
    "קריית ים" == "קרית ים" (vowel letters dropped after each word's first)."""
    cleaned = "".join(ch if ch.isalnum() else " " for ch in name)
    return " ".join(w[0] + "".join(c for c in w[1:] if c not in "יו") for w in cleaned.split())


@router.get("/places/cities", response_model=list[CityOut])
async def cities(db: AsyncSession = Depends(get_db), redis: Redis = Depends(get_redis)) -> list[CityOut]:
    """Israeli cities and towns for the preferred-city picker: OSM places of
    kind city/town that are also on the official CBS locality list. The
    centre point lets the app open the map there when GPS isn't available."""
    rows = await db.execute(
        text(
            "SELECT DISTINCT ON (n) n, name_en, ST_Y(geom::geometry) AS lat, ST_X(geom::geometry) AS lng "
            "FROM (SELECT COALESCE(name_he, name) AS n, name_en, geom, kind FROM osm_places "
            "WHERE kind IN ('city', 'town')) p ORDER BY n, kind"
        )
    )
    try:
        official = {_norm(n) for n in await _official_localities(redis)}
    except Exception:
        official = None  # registry down: fall back to the map's list rather than nothing
    return [
        CityOut(name=r.n, name_en=r.name_en, lat=r.lat, lng=r.lng)
        for r in rows
        if official is None or _norm(r.n) in official
    ]
