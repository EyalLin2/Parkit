"""Self-hosted reverse geocoding over OpenStreetMap data in our own PostGIS.

Replaces calls to the public Nominatim server, whose policy allows ~1
request/second for the *whole app* — fine for a demo, a guaranteed outage at
real scale. Here a lookup is two indexed nearest-neighbour queries against
tables loaded once by ``import_osm_geodata.py``, cached in Redis on a ~11m
grid, so it costs no external request at all.

Resolution order, mirroring how a driver describes where they parked —
by the street the car is ON, not the nearest building (which at a corner is
often on the cross street):
1. inside a named square / pedestrian plaza → "Rabin Square, Tel Aviv"
2. ON a street (within ON_STREET_M of one): that street, plus a house
   number only from an address on that SAME street (names compared
   word-by-word, so OSM's "בגין מנחם" matches the street "מנחם בגין")
   → "Dizengoff 50, Tel Aviv"
3. away from every street (inside a block / courtyard / lot) and a building
   is closer than any street: that building's address — this is where the
   street-first rule picked the wrong side of a corner in our comparison
   against Nominatim.
4. otherwise the nearest street, else the nearest address, else the town.
"""

import json
from dataclasses import asdict, dataclass

from redis.asyncio import Redis
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

ADDRESS_RADIUS_M = 35
NUMBER_RADIUS_M = 120
ON_STREET_M = 15
STREET_RADIUS_M = 60
STREET_FALLBACK_RADIUS_M = 150
PLACE_RADIUS_M = 15_000
CACHE_TTL_SECONDS = 7 * 24 * 3600


@dataclass
class ReverseResult:
    label: str | None  # "Dizengoff 50, Tel Aviv" — for the pin / report header
    street: str | None  # "Dizengoff 50" — stored on reported spots


_POINT = "ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography"


def _localized(name: str | None, he: str | None, en: str | None, lang: str) -> str | None:
    if lang == "en":
        return en or name
    return he or name


_GENERIC_WORDS = {
    "רחוב",
    "רח",
    "שדרות",
    "שד",
    "דרך",
    "street",
    "st",
    "road",
    "rd",
    "avenue",
    "ave",
    "blvd",
    "boulevard",
}
# Honorifics that addr:street often adds or drops ("ד״ר בודנהיימר" vs "בודנהיימר").
_TITLE_WORDS = {
    "דר",
    "דוקטור",
    "פרופ",
    "פרופסור",
    "הרב",
    "רבי",
    "השופט",
    "המשורר",
    "הנשיא",
    "סרן",
    "רסן",
    "אלוף",
    "תאל",
    "סאל",
}


def _name_tokens(name: str) -> frozenset[str]:
    cleaned = "".join(ch if ch.isalnum() or ch.isspace() else " " for ch in name.lower())
    tokens = set()
    for word in cleaned.split():
        if word in _GENERIC_WORDS or word in _TITLE_WORDS:
            continue
        # Hebrew is written with and without vowel letters ("ברנדיס" /
        # "ברנדייס", "הגבור" / "הגיבור"); compare the consonant skeleton.
        skeleton = word[0] + "".join(ch for ch in word[1:] if ch not in "יו")
        tokens.add(skeleton)
    return frozenset(tokens)


def same_street(a: str, b: str) -> bool:
    """Same street despite spelling, titles, word order and first names:
    "בן גוריון" ~ "שדרות דוד בן גוריון", "רוקח ישראל" ~ "שדרות רוקח".
    Only ever compared against streets within ~100m, so a partial match
    (one name's words all appearing in the other) is safe."""
    ta, tb = _name_tokens(a), _name_tokens(b)
    if not ta or not tb:
        return False
    return ta <= tb or tb <= ta


async def _first(db: AsyncSession, sql: str, params: dict):
    return (await db.execute(text(sql), params)).first()


async def _address_label(db: AsyncSession, params: dict, address, lang: str) -> str:
    """ "Street N" for an address point, spelled the way the street itself is
    named (OSM addr:street is often "Last First" or only in Hebrew)."""
    rows = (
        await db.execute(
            text(
                f"""
                SELECT name, name_he, name_en FROM osm_streets
                WHERE ST_DWithin(geom, {_POINT}, 250)
                ORDER BY geom <-> {_POINT} LIMIT 25
                """
            ),
            params,
        )
    ).all()
    named = next((r for r in rows if same_street(address.street, r.name)), None)
    name = _localized(named.name, named.name_he, named.name_en, lang) if named else address.street
    return f"{name} {address.housenumber}"


async def reverse(db: AsyncSession, lat: float, lng: float, lang: str = "he") -> ReverseResult:
    params = {"lat": lat, "lng": lng}

    place = await _first(
        db,
        f"""
        SELECT name, name_he, name_en FROM osm_places
        WHERE ST_DWithin(geom, {_POINT}, :r)
        ORDER BY CASE kind WHEN 'city' THEN 0 WHEN 'town' THEN 1 ELSE 2 END, geom <-> {_POINT}
        LIMIT 1
        """,
        {**params, "r": PLACE_RADIUS_M},
    )
    city = _localized(place.name, place.name_he, place.name_en, lang) if place else None

    street: str | None = None

    square = await _first(
        db,
        f"""
        SELECT name, name_he, name_en FROM osm_areas
        WHERE ST_Intersects(geom, {_POINT})
        ORDER BY ST_Area(geom) LIMIT 1
        """,
        params,
    )
    if square is not None:
        street = _localized(square.name, square.name_he, square.name_en, lang)
    else:
        street_row = None
        for radius in (STREET_RADIUS_M, STREET_FALLBACK_RADIUS_M):
            street_row = await _first(
                db,
                f"""
                SELECT name, name_he, name_en, ST_Distance(geom, {_POINT}) AS dist FROM osm_streets
                WHERE ST_DWithin(geom, {_POINT}, :r)
                ORDER BY geom <-> {_POINT} LIMIT 1
                """,
                {**params, "r": radius},
            )
            if street_row is not None:
                break

        nearest_address = await _first(
            db,
            f"""
            SELECT street, housenumber, city, ST_Distance(geom, {_POINT}) AS dist FROM osm_addresses
            WHERE street IS NOT NULL AND ST_DWithin(geom, {_POINT}, :r)
            ORDER BY geom <-> {_POINT} LIMIT 1
            """,
            {**params, "r": ADDRESS_RADIUS_M},
        )
        in_block = (
            street_row is not None
            and street_row.dist > ON_STREET_M
            and nearest_address is not None
            and nearest_address.dist < street_row.dist
        )

        if in_block:
            street = await _address_label(db, params, nearest_address, lang)
        elif street_row is not None:
            street = _localized(street_row.name, street_row.name_he, street_row.name_en, lang)
            candidates = (
                await db.execute(
                    text(
                        f"""
                        SELECT
                          COALESCE(a.street, (
                            SELECT s.name FROM osm_streets s
                            WHERE ST_DWithin(s.geom, a.geom, 100)
                            ORDER BY s.geom <-> a.geom LIMIT 1
                          )) AS street,
                          a.housenumber
                        FROM osm_addresses a
                        WHERE ST_DWithin(a.geom, {_POINT}, :r)
                        ORDER BY a.geom <-> {_POINT} LIMIT 12
                        """
                    ),
                    {**params, "r": NUMBER_RADIUS_M},
                )
            ).all()
            match = next((c for c in candidates if c.street and same_street(c.street, street_row.name)), None)
            if match is not None:
                street = f"{street} {match.housenumber}"
        elif nearest_address is not None:
            street = await _address_label(db, params, nearest_address, lang)
            if not city and nearest_address.city and lang != "en":
                city = nearest_address.city

    if street and city:
        label = f"{street}, {city}"
    else:
        label = street or city
    return ReverseResult(label=label, street=street)


def _cache_key(lat: float, lng: float, lang: str) -> str:
    return f"geo:{lang}:{lat:.4f},{lng:.4f}"


async def reverse_cached(db: AsyncSession, redis: Redis, lat: float, lng: float, lang: str = "he") -> ReverseResult:
    key = _cache_key(lat, lng, lang)
    hit = await redis.get(key)
    if hit is not None:
        return ReverseResult(**json.loads(hit))
    result = await reverse(db, lat, lng, lang)
    await redis.set(key, json.dumps(asdict(result)), ex=CACHE_TTL_SECONDS)
    return result
