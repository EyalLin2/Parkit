import uuid
from datetime import datetime, timezone

import jwt
import pytest

import otp
from auth import create_access_token
from config import settings
from redis_client import redis_client

PHONE = "054-1234567"
E164 = "+972541234567"


@pytest.mark.parametrize(
    ("raw", "expected"),
    [
        ("0541234567", E164),
        ("054-123-4567", E164),
        ("+972541234567", E164),
        ("972541234567", E164),
        ("031234567", None),
        ("12345", None),
    ],
)
def test_normalise_phone(raw, expected):
    assert otp.normalise_phone(raw) == expected


async def _code(client) -> str:
    r = await client.post("/auth/otp/request", json={"phone": PHONE})
    assert r.status_code == 200, r.text
    assert r.json()["phone"] == E164
    return r.json()["dev_code"]  # local env only


async def test_first_login_creates_an_account_that_needs_details(client):
    code = await _code(client)
    r = await client.post("/auth/otp/verify", json={"phone": PHONE, "code": code})
    assert r.status_code == 200
    token = r.json()
    assert token["needs_profile"] is True
    headers = {"Authorization": f"Bearer {token['access_token']}"}

    r = await client.patch(
        "/users/me/details",
        json={"first_name": "דנה", "last_name": "כהן", "email": "Dana@Example.com"},
        headers=headers,
    )
    assert r.status_code == 200
    me = (await client.get("/users/me", headers=headers)).json()
    assert me["display_name"] == "דנה כ."  # public name: last initial only
    assert me["account"] == {"phone": E164, "first_name": "דנה", "last_name": "כהן", "email": "dana@example.com"}

    # Second login: same account, details already there.
    await redis_client.delete(f"otp:cooldown:{E164}")
    again = await client.post("/auth/otp/verify", json={"phone": PHONE, "code": await _code(client)})
    assert again.json()["user_id"] == token["user_id"]
    assert again.json()["needs_profile"] is False


async def test_wrong_code_then_lockout(client):
    code = await _code(client)
    wrong = "000000" if code != "000000" else "111111"
    for _ in range(otp.MAX_ATTEMPTS):
        assert (await client.post("/auth/otp/verify", json={"phone": PHONE, "code": wrong})).status_code == 400
    # Sixth try is refused even with the right code: the code is burned.
    assert (await client.post("/auth/otp/verify", json={"phone": PHONE, "code": code})).status_code == 429


async def test_code_is_single_use(client):
    code = await _code(client)
    assert (await client.post("/auth/otp/verify", json={"phone": PHONE, "code": code})).status_code == 200
    assert (await client.post("/auth/otp/verify", json={"phone": PHONE, "code": code})).status_code == 400


async def test_resend_is_throttled(client):
    await _code(client)
    assert (await client.post("/auth/otp/request", json={"phone": PHONE})).status_code == 429


async def test_bad_number_and_bad_email_are_rejected(client):
    assert (await client.post("/auth/otp/request", json={"phone": "03-1234567"})).status_code == 422
    code = await _code(client)
    token = (await client.post("/auth/otp/verify", json={"phone": PHONE, "code": code})).json()["access_token"]
    r = await client.patch(
        "/users/me/details",
        json={"first_name": "a", "last_name": "b", "email": "not-an-email"},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert r.status_code == 422


async def test_dev_login_is_closed_outside_local(client, monkeypatch):
    monkeypatch.setattr(settings, "app_env", "production")
    r = await client.post("/auth/dev-login", json={"auth_provider": "dev", "external_id": "x", "display_name": "x"})
    assert r.status_code == 404
    # ...and the SMS code is never echoed back outside local.
    r = await client.post("/auth/otp/request", json={"phone": "0549999999"})
    assert r.json()["dev_code"] is None


async def test_refresh_keeps_you_signed_in(client):
    code = await _code(client)
    token = (await client.post("/auth/otp/verify", json={"phone": PHONE, "code": code})).json()
    r = await client.post("/auth/refresh", headers={"Authorization": f"Bearer {token['access_token']}"})
    assert r.status_code == 200
    assert r.json()["user_id"] == token["user_id"]
    assert (await client.post("/auth/refresh", headers={"Authorization": "Bearer nonsense"})).status_code == 401


def test_sessions_expire_after_the_idle_window():
    token = create_access_token(uuid.uuid4())
    exp = jwt.decode(token, settings.jwt_secret, algorithms=["HS256"])["exp"]
    minutes_left = (exp - datetime.now(timezone.utc).timestamp()) / 60
    assert settings.session_idle_minutes - 1 < minutes_left <= settings.session_idle_minutes


async def test_twilio_sender_posts_the_message(monkeypatch):
    import httpx

    import sms

    sent = {}

    async def fake_post(self, url, auth=None, data=None):
        sent.update(url=url, auth=auth, data=data)
        return httpx.Response(201, json={"sid": "SM1"})

    monkeypatch.setattr(httpx.AsyncClient, "post", fake_post)
    await sms.TwilioSmsSender("AC123", "secret", "+15551234567").send(E164, "code 123456")
    assert sent["url"].endswith("/Accounts/AC123/Messages.json")
    assert sent["data"] == {"To": E164, "From": "+15551234567", "Body": "code 123456"}


async def test_failed_sms_frees_the_number_to_retry(client, monkeypatch):
    import sms
    from main import app

    class Broken:
        async def send(self, phone, text):
            raise sms.SmsError("down")

    app.dependency_overrides[sms.get_sms_sender] = lambda: Broken()
    try:
        assert (await client.post("/auth/otp/request", json={"phone": PHONE})).status_code == 503
    finally:
        app.dependency_overrides.pop(sms.get_sms_sender)
    # No cooldown left behind: the next attempt goes through.
    assert (await client.post("/auth/otp/request", json={"phone": PHONE})).status_code == 200


async def test_twilio_prefers_the_api_key(monkeypatch):
    import httpx

    import sms

    seen = {}

    async def fake_post(self, url, auth=None, data=None):
        seen["auth"] = auth
        return httpx.Response(201, json={"sid": "SM1"})

    monkeypatch.setattr(httpx.AsyncClient, "post", fake_post)
    await sms.TwilioSmsSender("AC1", "", "+15550001111", api_key=("SK1", "s3cret")).send(E164, "hi")
    assert seen["auth"] == ("SK1", "s3cret")
