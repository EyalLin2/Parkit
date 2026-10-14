"""Public parking lots near a point — the map's "Paid parking" layer.

Read-only OSM data (import_osm_geodata.py); no login needed, same as the
map tiles themselves.
"""

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from database import get_db

router = APIRouter(tags=["parking lots"])

MAX_LOTS = 150


class ParkingLotOut(BaseModel):
    id: int
    lat: float
    lng: float
    name: str | None
    name_en: str | None
    kind: str | None
    fee: str | None
    capacity: int | None
    operator: str | None
    charge: str | None = None
    opening_hours: str | None = None
    distance_m: float
    # When the car park data was last refreshed from OpenStreetMap (ISO date).
    data_updated_at: str | None = None


@router.get("/parking-lots", response_model=list[ParkingLotOut])
async def parking_lots(
    lat: float = Query(ge=-90, le=90),
    lng: float = Query(ge=-180, le=180),
    radius_m: int = Query(default=1500, ge=50, le=3000),
    db: AsyncSession = Depends(get_db),
) -> list[ParkingLotOut]:
    rows = await db.execute(
        text(
            "SELECT id, ST_Y(geom::geometry) AS lat, ST_X(geom::geometry) AS lng, "
            "COALESCE(name_he, name) AS name, name_en, kind, fee, capacity, operator, charge, opening_hours, "
            "ST_Distance(geom, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) AS distance_m "
            "FROM osm_parking_lots "
            "WHERE ST_DWithin(geom, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radius) "
            "ORDER BY geom <-> ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography LIMIT :limit"
        ),
        {"lat": lat, "lng": lng, "radius": radius_m, "limit": MAX_LOTS},
    )
    updated = (await db.execute(text("SELECT value FROM osm_meta WHERE key = 'imported_at'"))).scalar_one_or_none()
    return [ParkingLotOut(**r._mapping, data_updated_at=updated) for r in rows]
