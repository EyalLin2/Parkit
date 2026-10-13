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


async def test_inbox_is_closed_without_the_admin_token(client, monkeypatch):
    from config import settings

    await client.post("/feedback", json={"message": "<script>alert(1)</script>"})
    assert (await client.get("/admin/feedback")).status_code == 404  # no token configured
    monkeypatch.setattr(settings, "admin_token", "s3cret")
    assert (await client.get("/admin/feedback", params={"token": "wrong"})).status_code == 404
    page = await client.get("/admin/feedback", params={"token": "s3cret"})
    assert page.status_code == 200
    assert "&lt;script&gt;" in page.text and "<script>alert" not in page.text  # escaped


async def test_undelivered_codes_page_shows_beta_codes_to_the_admin_only(client, monkeypatch):
    from config import settings

    monkeypatch.setattr(settings, "email_provider", "dev")
    r = await client.post("/auth/email/request", json={"email": "beta.tester@example.com"})
    assert r.status_code == 200
    assert (await client.get("/admin/codes")).status_code == 404  # no token configured
    monkeypatch.setattr(settings, "admin_token", "s3cret")
    assert (await client.get("/admin/codes", params={"token": "wrong"})).status_code == 404
    page = await client.get("/admin/codes", params={"token": "s3cret"})
    assert page.status_code == 200
    assert "beta.tester@example.com" in page.text
    assert r.json()["dev_code"] is None or r.json()["dev_code"] in page.text
