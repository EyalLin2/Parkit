"""Sign in with Google: a real RS256 ID token (signed by a test key standing
in for Google's), audience/issuer checked, same account as email login."""

import time
from types import SimpleNamespace

import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa

from config import settings
from routers import auth as auth_router

CLIENT_ID = "test-client.apps.googleusercontent.com"
_KEY = rsa.generate_private_key(public_exponent=65537, key_size=2048)


@pytest.fixture
def google(monkeypatch):
    monkeypatch.setattr(settings, "google_client_id", CLIENT_ID)
    monkeypatch.setattr(
        auth_router._GOOGLE_KEYS, "get_signing_key_from_jwt", lambda token: SimpleNamespace(key=_KEY.public_key())
    )


def _token(**over):
    claims = {
        "iss": "https://accounts.google.com",
        "aud": CLIENT_ID,
        "sub": "1234567890",
        "email": "Noa.Levi@gmail.com",
        "email_verified": True,
        "given_name": "Noa",
        "family_name": "Levi",
        "exp": int(time.time()) + 600,
        "iat": int(time.time()),
    } | over
    return jwt.encode(claims, _KEY, algorithm="RS256")


async def test_off_without_a_client_id(client):
    assert (await client.post("/auth/google", json={"id_token": _token()})).status_code == 503


async def test_google_sign_in_creates_the_account_with_names(client, google):
    resp = await client.post("/auth/google", json={"id_token": _token()})
    assert resp.status_code == 200
    body = resp.json()
    assert body["needs_profile"] is False
    me = (await client.get("/users/me", headers={"Authorization": f"Bearer {body['access_token']}"})).json()
    assert me["display_name"] == "Noa L."


async def test_same_email_same_account(client, google):
    first = (await client.post("/auth/google", json={"id_token": _token()})).json()["user_id"]
    again = (await client.post("/auth/google", json={"id_token": _token(sub="other")})).json()["user_id"]
    assert first == again


async def test_wrong_audience_or_expired_is_rejected(client, google):
    assert (await client.post("/auth/google", json={"id_token": _token(aud="someone-else")})).status_code == 401
    assert (await client.post("/auth/google", json={"id_token": _token(exp=int(time.time()) - 10)})).status_code == 401
    assert (await client.post("/auth/google", json={"id_token": _token(email_verified=False)})).status_code == 401
