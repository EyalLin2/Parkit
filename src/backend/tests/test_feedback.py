from sqlalchemy import text

from database import engine


async def _rows():
    async with engine.begin() as conn:
        return (await conn.execute(text("SELECT kind, message, user_id FROM app_feedback ORDER BY id"))).all()


async def test_signed_in_feedback_is_tied_to_the_user(client, make_user):
    user_id, headers = await make_user("tester")
    r = await client.post("/feedback", json={"message": "The map is great", "app_version": "0.3.0"}, headers=headers)
    assert r.status_code == 204
    ((kind, message, uid),) = await _rows()
    assert (kind, message, str(uid)) == ("feedback", "The map is great", user_id)


async def test_crash_reports_work_without_signing_in(client):
    r = await client.post("/feedback", json={"kind": "crash", "message": "java.lang.IllegalStateException at …"})
    assert r.status_code == 204
    ((kind, _, uid),) = await _rows()
    assert (kind, uid) == ("crash", None)


async def test_feedback_is_throttled(client, make_user):
    _, headers = await make_user("chatty")
    codes = [
        (await client.post("/feedback", json={"message": f"#{i}"}, headers=headers)).status_code for i in range(22)
    ]
    assert codes.count(204) == 20 and codes[-1] == 429
