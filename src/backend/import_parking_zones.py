"""Load resident parking-permit zones into PostGIS (re-runnable).

Source: Tel Aviv-Yafo municipality open GIS, layer 544 "אזורי חניה"
(polygons with a zone number, updated by the city). Other cities can be
added to SOURCES when they publish equivalent data.

Usage (inside the backend container):
    python import_parking_zones.py
"""

import asyncio
import json
import urllib.parse
import urllib.request

import asyncpg

from config import settings

SOURCES = [
    {
        "city": "תל אביב-יפו",
        "slug": "tel-aviv",
        "url": "https://gisn.tel-aviv.gov.il/arcgis/rest/services/IView2/MapServer/544/query?"
        + urllib.parse.urlencode({"where": "1=1", "outFields": "ms_ezor,LABEL", "outSR": 4326, "f": "geojson"}),
        "number_field": "ms_ezor",
        "label_field": "LABEL",
    },
]


def _fetch(url: str) -> dict:
    req = urllib.request.Request(url, headers={"User-Agent": "ParkIt-zones-import/1.0"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.load(resp)


async def main() -> None:
    rows = []
    for src in SOURCES:
        data = _fetch(src["url"])
        for feature in data.get("features", []):
            props = feature["properties"]
            number = props[src["number_field"]]
            rows.append(
                (
                    f"{src['slug']}:{number}",
                    src["city"],
                    props.get(src["label_field"]) or f"אזור {number}",
                    json.dumps(feature["geometry"]),
                )
            )
        print(f"  {src['city']}: {len(data.get('features', []))} zones", flush=True)

    dsn = settings.database_url.replace("+asyncpg", "")
    conn = await asyncpg.connect(dsn, ssl="require" if settings.app_env != "local" else None)
    try:
        async with conn.transaction():
            await conn.execute("TRUNCATE permit_zones RESTART IDENTITY")
            await conn.executemany(
                "INSERT INTO permit_zones (key, city, label, geom) "
                "VALUES ($1, $2, $3, ST_Multi(ST_GeomFromGeoJSON($4))::geography)",
                rows,
            )
    finally:
        await conn.close()
    print(f"Loaded {len(rows)} permit zones", flush=True)


if __name__ == "__main__":
    asyncio.run(main())
