import uuid

import pytest
from sqlalchemy import text

from database import engine
from levels import LEVELS, level_for, next_level

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


async def test_levels_do_not_change_how_long_a_spot_stays(client, make_user):
    """Perks are cosmetic: a legend's report expires like anyone's."""
    legend_id, legend = await make_user("legend")
    await _set_points(legend_id, 5000)
    newbie_spot = (await _report(client, (await make_user("newbie"))[1], lat=32.07, lng=34.77)).json()
    legend_spot = (await _report(client, legend)).json()
    async with engine.begin() as conn:
        ttls = (
            (
                await conn.execute(
                    text("SELECT ttl_minutes FROM spots WHERE id IN (:a, :b)"),
                    {"a": uuid.UUID(newbie_spot["id"]), "b": uuid.UUID(legend_spot["id"])},
                )
            )
            .scalars()
            .all()
        )
    assert ttls == [30, 30]
