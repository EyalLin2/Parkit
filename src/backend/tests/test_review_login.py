"""The single Google Play review account (REVIEW_LOGIN): fixed code, nothing
sent, still rate-limited, and no effect on anyone else."""

import pytest

from config import settings


@pytest.fixture
def review_login(monkeypatch):
    monkeypatch.setattr(settings, "review_login", "play-review@parkit.test:246810")


async def test_review_account_signs_in_with_its_fixed_code(client, review_login):
    sent = await client.post("/auth/email/request", json={"email": "Play-Review@parkit.test"})
    assert sent.status_code == 200
    assert sent.json()["dev_code"] is None  # the fixed code is never echoed

    resp = await client.post("/auth/email/verify", json={"email": "play-review@parkit.test", "code": "246810"})
    assert resp.status_code == 200
    assert resp.json()["access_token"]


async def test_review_account_rejects_a_wrong_code_and_locks_after_five(client, review_login):
    for _ in range(5):
        wrong = await client.post("/auth/email/verify", json={"email": "play-review@parkit.test", "code": "000000"})
        assert wrong.status_code == 400
    locked = await client.post("/auth/email/verify", json={"email": "play-review@parkit.test", "code": "246810"})
    assert locked.status_code == 429


async def test_other_addresses_get_no_fixed_code(client, review_login):
    resp = await client.post("/auth/email/verify", json={"email": "someone@parkit.test", "code": "246810"})
    assert resp.status_code == 400


async def test_phone_review_account(client, monkeypatch):
    monkeypatch.setattr(settings, "review_login", "050-000-0001:135790")
    resp = await client.post("/auth/otp/verify", json={"phone": "0500000001", "code": "135790"})
    assert resp.status_code == 200


async def test_off_when_unset(client):
    resp = await client.post("/auth/email/verify", json={"email": "play-review@parkit.test", "code": "246810"})
    assert resp.status_code == 400
