import uuid

import pytest
from sqlalchemy import text

from database import engine
from levels import LEVELS, level_for, next_level
from redis_client import redis_client

TLV = {"lat": 32.0809, "lng": 34.7806}


async def _set_points(user_id: str, points: int) -> None:
    async with engine.begin() as conn:
        await conn.execute(text("UPDATE users SET points = :p WHERE id = :id"), {"p": points, "id": uuid.UUID(user_id)})


async def _report(client, headers, **overrides):
    payload = {**TLV, "spot_type": "street", "payment": "free", **overrides}
    return await client.post("/spots", json=payload, headers=headers)


@pytest.mark.parametrize(
    ("points", "key"),
    [
        (-20, "newbie"),
        (0, "newbie"),
        (99, "newbie"),
        (100, "bronze"),
        (400, "silver"),
        (1199, "silver"),
        (1200, "gold"),
        (3000, "legend"),
        (99_999, "legend"),
    ],
)
def test_level_thresholds(points, key):
    assert level_for(points).key == key


def test_next_level_and_top_of_the_ladder():
    assert next_level(0).key == "bronze"
    assert next_level(450).key == "gold"
    assert next_level(LEVELS[-1].min_points) is None


async def test_profile_reports_level_and_points_to_next(client, make_user):
    user_id, headers = await make_user("climber")
    await _set_points(user_id, 450)
    body = (await client.get("/users/me", headers=headers)).json()
    assert body["level"] == "silver"
    assert body["level_min_points"] == 400
    assert body["next_level"] == "gold"
    assert body["next_level_points"] == 1200


async def test_silver_reports_stay_on_the_map_longer(client, make_user):
    newbie_id, newbie = await make_user("newbie")
    silver_id, silver = await make_user("silver")
    await _set_points(silver_id, 400)
    a = (await _report(client, newbie)).json()
    b = (await _report(client, silver, lat=TLV["lat"] + 0.01)).json()
    async with engine.connect() as conn:
        ttl = dict((await conn.execute(text("SELECT id::text, ttl_minutes FROM spots"))).all())
    assert ttl[a["id"]] == 30
    assert ttl[b["id"]] == 45


async def test_gold_waits_one_minute_between_reports(client, make_user):
    gold_id, gold = await make_user("gold")
    await _set_points(gold_id, 1200)
    assert (await _report(client, gold)).status_code == 201
    remaining = await redis_client.ttl(f"report_cooldown:{gold_id}")
    assert 0 < remaining <= 60


async def test_nearby_spots_carry_the_reporters_level(client, make_user):
    legend_id, legend = await make_user("legend")
    await _set_points(legend_id, 3000)
    await _report(client, legend)
    spots = (await client.get("/spots", params={**TLV, "radius_m": 500})).json()
    assert spots[0]["reporter_level"] == "legend"


async def test_leaderboard_entries_carry_level(client, make_user):
    user_id, headers = await make_user("ranked")
    await _report(client, headers)  # weekly points > 0 so it appears
    await _set_points(user_id, 1300)
    board = (await client.get("/leaderboard", headers=headers)).json()
    assert board[0]["level"] == "gold"
