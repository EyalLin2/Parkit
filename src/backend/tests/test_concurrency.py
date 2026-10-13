"""Races the load test found: many drivers acting on one spot at the same
instant. Each request runs in its own DB session, like real traffic."""

import asyncio

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
