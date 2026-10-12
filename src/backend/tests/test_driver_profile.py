import pytest
from sqlalchemy import text

import routers.me as me
from database import engine
from vehicles import VehicleInfo, classify_size, normalise_plate


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
    }


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
