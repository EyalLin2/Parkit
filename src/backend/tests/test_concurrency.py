"""Races the load test found: many drivers acting on one spot at the same
instant. Each request runs in its own DB session, like real traffic."""

import asyncio

from sqlalchemy import text

from database import engine
from redis_client import redis_client

TLV = {"lat": 32.0809, "lng": 34.7806}


async def test_simultaneous_flags_penalise_the_reporter_once(client, make_user):
    reporter_id, reporter = await make_user("reporter")
    spot = (
        await client.post("/spots", json={**TLV, "spot_type": "street", "payment": "free"}, headers=reporter)
    ).json()
    flaggers = [(await make_user(f"flagger-{i}"))[1] for i in range(6)]

    await asyncio.gather(
        *(client.post(f"/spots/{spot['id']}/feedback", json={"type": "flagged_false"}, headers=h) for h in flaggers)
    )

    me = (await client.get("/users/me", headers=reporter)).json()
    # +10 for the report, −5 once for the false report — not once per flagger.
    assert me["points"] == 5


async def test_simultaneous_reports_of_one_place_make_one_spot(client, make_user):
    reporters = [(await make_user(f"r-{i}"))[1] for i in range(5)]
    results = await asyncio.gather(
        *(
            client.post(
                "/spots",
                json={"lat": 32.0809 + i * 0.00001, "lng": 34.7806, "spot_type": "street", "payment": "free"},
                headers=h,
            )
            for i, h in enumerate(reporters)
        )
    )
    ids = {r.json()["id"] for r in results if r.status_code == 201}
    assert len(ids) == 1


async def test_one_driver_cannot_vote_twice_at_once(client, make_user):
    _, reporter = await make_user("reporter")
    _, voter = await make_user("voter")
    spot = (
        await client.post("/spots", json={**TLV, "spot_type": "street", "payment": "free"}, headers=reporter)
    ).json()
    results = await asyncio.gather(
        *(client.post(f"/spots/{spot['id']}/feedback", json={"type": "flagged_false"}, headers=voter) for _ in range(4))
    )
    assert sorted(r.status_code for r in results).count(204) == 1


_INSERT_SPOT = text(
    "INSERT INTO spots (id, reporter_id, lat, lng, location, spot_type, payment, status, ttl_minutes, reported_at) "
    "VALUES (gen_random_uuid(), CAST(:u AS uuid), :lat, :lng, "
    "ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, 'street', 'free', 'active', 30, "
    "now() - make_interval(mins => :age))"
)


async def _insert_spot(user_id: str, lat: float, lng: float = 34.78, age_minutes: int = 0) -> None:
    async with engine.begin() as conn:
        await conn.execute(_INSERT_SPOT, {"u": user_id, "lat": lat, "lng": lng, "age": age_minutes})


async def test_nearby_returns_at_most_the_nearest_fifty(client, make_user):
    uid, _ = await make_user("bulk")
    for i in range(60):
        await _insert_spot(uid, 32.08 + i * 0.0001)
    spots = (await client.get("/spots", params={"lat": 32.08, "lng": 34.78, "radius_m": 2000})).json()
    assert len(spots) == 50
    assert spots[0]["lat"] == 32.08  # nearest first


async def test_expired_spots_are_hidden_even_before_the_sweep(client, make_user):
    uid, _ = await make_user("old")
    await _insert_spot(uid, 32.08, age_minutes=31)
    await redis_client.set("reconcile:spots", "1", ex=60)  # the sweep "just ran", so it won't now
    assert (await client.get("/spots", params={"lat": 32.08, "lng": 34.78})).json() == []
