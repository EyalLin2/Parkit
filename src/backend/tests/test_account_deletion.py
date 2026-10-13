from sqlalchemy import text

from database import engine

TLV = {"lat": 32.0809, "lng": 34.7806}


async def _count(sql: str, **params) -> int:
    async with engine.begin() as conn:
        return (await conn.execute(text(sql), params)).scalar_one()


async def test_deleting_an_account_removes_the_user_and_their_data(client, make_user):
    user_id, headers = await make_user("leaving")
    _, other = await make_user("staying")
    mine = (await client.post("/spots", json={**TLV, "spot_type": "street", "payment": "free"}, headers=headers)).json()
    theirs = (
        await client.post(
            "/spots", json={"lat": 32.09, "lng": 34.79, "spot_type": "street", "payment": "free"}, headers=other
        )
    ).json()
    await client.post(f"/spots/{theirs['id']}/claim", headers=headers)
    await client.post(f"/spots/{mine['id']}/feedback", json={"type": "confirmed_taken"}, headers=other)

    assert (await client.delete("/users/me", headers=headers)).status_code == 204

    assert await _count("SELECT count(*) FROM users WHERE id = CAST(:id AS uuid)", id=user_id) == 0
    assert await _count("SELECT count(*) FROM spots WHERE id = CAST(:id AS uuid)", id=mine["id"]) == 0
    assert await _count("SELECT count(*) FROM spot_feedback WHERE user_id = CAST(:id AS uuid)", id=user_id) == 0
    # The other driver's spot survives, just without this claimer.
    assert (
        await _count("SELECT count(*) FROM spots WHERE id = CAST(:id AS uuid) AND claimed_by IS NULL", id=theirs["id"])
        == 1
    )
    # The token no longer works.
    assert (await client.get("/users/me", headers=headers)).status_code in (401, 404)
