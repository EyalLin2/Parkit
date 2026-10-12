import pytest
from sqlalchemy import text

import routers.me as me
from database import engine
from vehicles import VehicleInfo, classify_size, normalise_plate

TLV = {"lat": 32.0809, "lng": 34.7806}
ZONE = "MULTIPOLYGON(((34.77 32.07, 34.79 32.07, 34.79 32.09, 34.77 32.09, 34.77 32.07)))"


async def _add_zone() -> None:
    async with engine.begin() as conn:
        await conn.execute(
            text(
                "INSERT INTO permit_zones (key, city, label, geom) "
                f"VALUES ('tel-aviv:9', 'תל אביב-יפו', 'אזור 9', ST_GeogFromText('SRID=4326;{ZONE}'))"
            )
        )


@pytest.mark.parametrize(
    ("body", "weight", "seats", "size"),
    [
        ("הצ'בק", 1370, 5, "compact"),  # Kia Picanto
        ("סטיישן", 2000, 5, "regular"),  # Kia Niro
        ("פנאי-שטח", 2230, 5, "large"),  # Toyota RAV4
        ("סדאן", 1900, 7, "large"),  # 7 seats
        (None, None, None, "regular"),  # unknown model → safe middle
    ],
)
def test_classify_size(body, weight, seats, size):
    assert classify_size(body, weight, seats) == size


def test_normalise_plate():
    assert normalise_plate("12-345-67") == "1234567"
    assert normalise_plate("123") is None


async def test_preferences_patch_only_changes_what_is_sent(client, make_user):
    _, headers = await make_user("driver")
    r = await client.patch(
        "/users/me/preferences", json={"vehicle_size": "large", "car_label": "Toyota RAV4 2021"}, headers=headers
    )
    assert r.status_code == 200
    r = await client.patch("/users/me/preferences", json={"has_disabled_permit": True}, headers=headers)
    prefs = (await client.get("/users/me", headers=headers)).json()["preferences"]
    assert prefs == {
        "vehicle_size": "large",
        "car_label": "Toyota RAV4 2021",
        "has_disabled_permit": True,
        "home_city": None,
        "resident_zone": None,
    }


async def test_resident_zone_must_exist(client, make_user):
    _, headers = await make_user("resident")
    assert (
        await client.patch("/users/me/preferences", json={"resident_zone": "tel-aviv:9"}, headers=headers)
    ).status_code == 422
    await _add_zone()
    assert (
        await client.patch("/users/me/preferences", json={"resident_zone": "tel-aviv:9"}, headers=headers)
    ).status_code == 200
    zones = (await client.get("/permit-zones")).json()
    assert zones == [{"key": "tel-aviv:9", "city": "תל אביב-יפו", "label": "אזור 9"}]
    outline = (await client.get("/permit-zones/geojson")).json()["features"][0]
    assert outline["properties"]["number"] == "9"
    assert outline["geometry"]["type"] in ("Polygon", "MultiPolygon")


async def test_spots_inside_a_zone_carry_it(client, make_user):
    await _add_zone()
    _, headers = await make_user("reporter")
    await client.post("/spots", json={**TLV, "spot_type": "street", "payment": "free"}, headers=headers)
    await client.post(
        "/spots",
        json={"lat": 32.2, "lng": 34.9, "spot_type": "street", "payment": "free"},
        headers=(await make_user("other"))[1],
    )
    inside = (await client.get("/spots", params={**TLV, "radius_m": 500})).json()[0]
    assert (inside["permit_zone"], inside["permit_zone_label"]) == ("tel-aviv:9", "אזור 9")
    outside = (await client.get("/spots", params={"lat": 32.2, "lng": 34.9, "radius_m": 500})).json()[0]
    assert outside["permit_zone"] is None


async def test_vehicle_lookup(client, make_user, monkeypatch):
    async def fake_lookup(plate, redis):
        assert plate == "1234567"
        return VehicleInfo(make="קיה", model="PICANTO", year=2020, body="הצ'בק", size="compact")

    monkeypatch.setattr(me, "lookup", fake_lookup)
    _, headers = await make_user("driver")
    body = (await client.get("/vehicles/lookup", params={"plate": "12-345-67"}, headers=headers)).json()
    assert body == {
        "make": "קיה",
        "model": "PICANTO",
        "year": 2020,
        "body": "הצ'בק",
        "size": "compact",
        "label": "קיה PICANTO 2020",
    }
    assert (await client.get("/vehicles/lookup", params={"plate": "abcde"}, headers=headers)).status_code == 422


async def test_cities_are_map_places_on_the_official_list(client, monkeypatch):
    async with engine.begin() as conn:
        await conn.execute(
            text(
                "INSERT INTO osm_places (geom, kind, name, name_he, name_en) VALUES "
                "(ST_GeogFromText('SRID=4326;POINT(34.78 32.08)'), 'city', 'תל אביב-יפו', NULL, 'Tel Aviv'),"
                "(ST_GeogFromText('SRID=4326;POINT(35.0 31.0)'), 'town', 'מקום אחר', NULL, NULL)"
            )
        )

    async def official(redis):
        return {"תל אביב - יפו"}

    monkeypatch.setattr(me, "_official_localities", official)
    assert (await client.get("/places/cities")).json() == [
        {"name": "תל אביב-יפו", "name_en": "Tel Aviv", "lat": 32.08, "lng": 34.78}
    ]
