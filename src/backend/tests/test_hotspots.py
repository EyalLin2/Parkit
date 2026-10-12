import uuid
from datetime import datetime, timedelta, timezone

from sqlalchemy import text

from database import engine

DIZENGOFF = {"lat": 32.0809, "lng": 34.7741}


async def _history(user_id: str, count: int, minutes_to_take: float, days_back_start: int = 1) -> None:
    """Past reports right here, at this hour of day, each claimed after
    `minutes_to_take` minutes."""
    now = datetime.now(timezone.utc)
    async with engine.begin() as conn:
        for i in range(count):
            reported = now - timedelta(days=days_back_start + i % 20, minutes=i)
            await conn.execute(
                text(
                    "INSERT INTO spots (id, reporter_id, lat, lng, location, spot_type, payment, status, "
                    "ttl_minutes, reported_at, claimed_at, removed_at, removed_reason) VALUES "
                    "(:id, :uid, :lat, :lng, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, "
                    "'street', 'free', 'removed', 30, :r, :c, :c, 'taken_confirmed')"
                ),
                {
                    "id": uuid.uuid4(),
                    "uid": uuid.UUID(user_id),
                    "lat": DIZENGOFF["lat"] + 0.0003 * (i % 5),
                    "lng": DIZENGOFF["lng"],
                    "r": reported,
                    "c": reported + timedelta(minutes=minutes_to_take),
                },
            )


async def _report(client, make_user) -> dict:
    _, headers = await make_user("reporter")
    r = await client.post("/spots", json={**DIZENGOFF, "spot_type": "street", "payment": "free"}, headers=headers)
    assert r.status_code == 201, r.text
    return r.json()


async def test_spot_in_a_fast_area_is_hot(client, make_user):
    uid, _ = await make_user("history")
    await _history(uid, count=20, minutes_to_take=2)
    assert (await _report(client, make_user))["hot_minutes"] == 2


async def test_not_enough_history_means_no_flame(client, make_user):
    uid, _ = await make_user("history")
    await _history(uid, count=10, minutes_to_take=2)
    assert (await _report(client, make_user))["hot_minutes"] is None


async def test_slow_area_is_not_hot(client, make_user):
    uid, _ = await make_user("history")
    await _history(uid, count=20, minutes_to_take=18)
    assert (await _report(client, make_user))["hot_minutes"] is None


async def test_old_history_does_not_count(client, make_user):
    uid, _ = await make_user("history")
    await _history(uid, count=20, minutes_to_take=2, days_back_start=40)
    assert (await _report(client, make_user))["hot_minutes"] is None


async def test_hot_flag_reaches_the_map(client, make_user):
    uid, _ = await make_user("history")
    await _history(uid, count=20, minutes_to_take=3)
    await _report(client, make_user)
    spots = (await client.get("/spots", params={**DIZENGOFF, "radius_m": 100})).json()
    assert spots[0]["hot_minutes"] == 3
