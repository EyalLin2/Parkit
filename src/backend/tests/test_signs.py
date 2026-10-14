"""Parking-sign reader: off without a key, answers through Claude (mocked
here), rate-limited, and never crashes on a bad model answer."""

import io

import pytest

from config import settings
from routers import signs


def _photo():
    return {"photo": ("sign.jpg", io.BytesIO(b"\xff\xd8fake-jpeg"), "image/jpeg")}


async def test_off_without_a_key(client, make_user):
    _, headers = await make_user("reader")
    assert (await client.post("/signs/read", files=_photo(), headers=headers)).status_code == 503


@pytest.fixture
def key(monkeypatch):
    monkeypatch.setattr(settings, "anthropic_api_key", "test-key")


async def test_reads_a_sign(client, make_user, key, monkeypatch):
    async def fake(image, media_type, now):
        assert media_type == "image/jpeg" and image.startswith(b"\xff\xd8")
        return signs.SignReading(
            verdict="allowed", until="19:00", paid=True, summary_he="מותר עד 19:00", summary_en="OK until 19:00"
        )

    monkeypatch.setattr(signs, "_ask_claude", fake)
    _, headers = await make_user("reader")
    body = (await client.post("/signs/read", files=_photo(), headers=headers)).json()
    assert body["verdict"] == "allowed" and body["until"] == "19:00" and body["paid"] is True


async def test_a_garbled_answer_is_a_503_not_a_crash(client, make_user, key, monkeypatch):
    async def broken(image, media_type, now):
        raise ValueError("not json")

    monkeypatch.setattr(signs, "_ask_claude", broken)
    _, headers = await make_user("reader")
    assert (await client.post("/signs/read", files=_photo(), headers=headers)).status_code == 503


async def test_rate_limited(client, make_user, key, monkeypatch):
    async def fake(image, media_type, now):
        return signs.SignReading(verdict="unclear", summary_he="לא ברור", summary_en="Unclear")

    monkeypatch.setattr(signs, "_ask_claude", fake)
    monkeypatch.setattr(signs, "READS_PER_HOUR", 2)
    _, headers = await make_user("reader")
    codes = [(await client.post("/signs/read", files=_photo(), headers=headers)).status_code for _ in range(3)]
    assert codes == [200, 200, 429]
