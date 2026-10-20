from sqlalchemy import text

from database import engine

DIZENGOFF = {"lat": 32.0779, "lng": 34.7740}


async def _lots() -> None:
    async with engine.begin() as conn:
        await conn.execute(
            text(
                "INSERT INTO osm_parking_lots (geom, name, name_en, kind, fee, capacity) VALUES "
                "(ST_GeogFromText('SRID=4326;POINT(34.7745 32.0782)'), 'חניון דיזנגוף', 'Dizengoff', "
                "'underground', 'yes', 400),"
                "(ST_GeogFromText('SRID=4326;POINT(34.7800 32.0800)'), 'חניון רחוק', NULL, 'surface', NULL, NULL),"
                "(ST_GeogFromText('SRID=4326;POINT(34.8500 32.1500)'), 'מחוץ לטווח', NULL, NULL, NULL, NULL)"
            )
        )


async def test_lots_are_nearest_first_within_radius(client):
    await _lots()
    lots = (await client.get("/parking-lots", params={**DIZENGOFF, "radius_m": 1500})).json()
    assert [lot["name"] for lot in lots] == ["חניון דיזנגוף", "חניון רחוק"]
    first = lots[0]
    assert (first["kind"], first["fee"], first["capacity"], first["name_en"]) == (
        "underground",
        "yes",
        400,
        "Dizengoff",
    )
    assert first["distance_m"] < 100


async def test_radius_is_capped(client):
    assert (await client.get("/parking-lots", params={**DIZENGOFF, "radius_m": 50000})).status_code == 422


async def test_lots_carry_charge_hours_and_data_date(client):
    async with engine.begin() as conn:
        await conn.execute(
            text(
                "INSERT INTO osm_parking_lots (geom, name, operator, charge, opening_hours) VALUES "
                "(ST_GeogFromText('SRID=4326;POINT(34.7745 32.0782)'), 'חניון עם מחיר', 'אחוזת החוף', "
                "'12 ILS/hour', '24/7')"
            )
        )
        await conn.execute(
            text(
                "INSERT INTO osm_meta (key, value) VALUES ('imported_at', '2026-10-15T03:00:00+00:00') "
                "ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value"
            )
        )
    lot = (await client.get("/parking-lots", params={**DIZENGOFF, "radius_m": 500})).json()[0]
    assert (lot["operator"], lot["charge"], lot["opening_hours"]) == ("אחוזת החוף", "12 ILS/hour", "24/7")
    assert lot["data_updated_at"].startswith("2026-10-15")
