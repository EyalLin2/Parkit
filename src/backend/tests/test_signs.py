"""Parking-sign reader: off without a key, reads the rules through Claude (mocked
here) only once per sign, works out the answer for the current time, and never
crashes on a bad model answer."""

import io
from datetime import datetime

import cv2
import numpy as np
import pytest

from config import settings
from routers import signs

# A weekday, a Friday and a Saturday in Israel.
SUNDAY_10 = datetime(2026, 11, 1, 10, 0)
SUNDAY_20 = datetime(2026, 11, 1, 20, 0)
FRIDAY_14 = datetime(2026, 11, 6, 14, 0)
SATURDAY_10 = datetime(2026, 11, 7, 10, 0)
HERE = {"lat": 32.09, "lng": 34.79}

PAID_WEEKDAYS = signs.SignRules(
    readable=True,
    windows=[
        {"days": [0, 1, 2, 3, 4], "from": "08:00", "to": "19:00", "state": "paid"},
        {"days": [5], "from": "08:00", "to": "13:00", "state": "paid"},
    ],
    otherwise="allowed",
)


def _picture(seed=1):
    rng = np.random.default_rng(seed)
    blocks = rng.integers(0, 255, (16, 16, 3), dtype=np.uint8)
    big = cv2.resize(blocks, (1600, 1200), interpolation=cv2.INTER_NEAREST)
    return cv2.imencode(".jpg", big)[1].tobytes()


def _photo(seed=1):
    return {"photo": ("sign.jpg", io.BytesIO(_picture(seed)), "image/jpeg")}


def test_the_answer_follows_the_clock():
    paid = signs.evaluate(PAID_WEEKDAYS, SUNDAY_10)
    assert (paid.verdict, paid.paid, paid.changes_at, paid.changes_to) == ("allowed", True, "19:00", "allowed")
    assert "19:00" in paid.summary_he
    evening = signs.evaluate(PAID_WEEKDAYS, SUNDAY_20)
    assert (evening.verdict, evening.paid, evening.changes_at) == ("allowed", False, None)


def test_days_are_counted_from_sunday():
    assert signs.evaluate(PAID_WEEKDAYS, FRIDAY_14).paid is False
    assert signs.evaluate(PAID_WEEKDAYS, SATURDAY_10).paid is False


def test_a_no_parking_window_and_what_comes_after_it():
    rules = signs.SignRules(
        readable=True,
        windows=[{"days": [0], "from": "07:00", "to": "12:00", "state": "not_allowed"}],
        otherwise="allowed",
    )
    now = signs.evaluate(rules, SUNDAY_10)
    assert (now.verdict, now.changes_at, now.changes_to) == ("not_allowed", "12:00", "allowed")


def test_holidays_and_notes_are_passed_on():
    rules = signs.SignRules(readable=True, holidays=True, note_he="עד שעתיים", note_en="Two hours at most")
    reading = signs.evaluate(rules, SUNDAY_10)
    assert "חג" in reading.summary_he and "עד שעתיים" in reading.summary_he
    assert "holidays" in reading.summary_en and "Two hours" in reading.summary_en


def test_an_unreadable_sign_is_unclear():
    assert signs.evaluate(signs.SignRules(readable=False), SUNDAY_10).verdict == "unclear"


def test_photos_are_shrunk_before_they_cost_tokens():
    small, _ = signs._prepare(_picture())
    image = cv2.imdecode(np.frombuffer(small, np.uint8), cv2.IMREAD_COLOR)
    assert max(image.shape[:2]) == signs.MAX_SIDE_PX


def test_the_same_sign_matches_and_another_does_not():
    _, one = signs._prepare(_picture(1))
    _, again = signs._prepare(_picture(1))
    _, other = signs._prepare(_picture(2))
    assert signs._bits_apart(one, again) <= signs.SAME_SIGN_MAX_BITS
    assert signs._bits_apart(one, other) > signs.SAME_SIGN_MAX_BITS


async def test_off_without_a_key(client, make_user):
    _, headers = await make_user("reader")
    assert (await client.post("/signs/read", files=_photo(), headers=headers)).status_code == 503


@pytest.fixture
def key(monkeypatch):
    monkeypatch.setattr(settings, "anthropic_api_key", "test-key")


@pytest.fixture
def ai_calls(monkeypatch):
    calls = []

    async def fake(image):
        calls.append(image)
        return PAID_WEEKDAYS

    monkeypatch.setattr(signs, "_read_rules", fake)
    return calls


async def test_the_next_driver_at_the_same_sign_costs_no_ai_call(client, make_user, key, ai_calls):
    _, first = await make_user("first")
    _, second = await make_user("second")
    one = await client.post("/signs/read", params=HERE, files=_photo(), headers=first)
    assert one.status_code == 200 and one.json()["from_cache"] is False
    two = await client.post("/signs/read", params=HERE, files=_photo(), headers=second)
    assert two.status_code == 200 and two.json()["from_cache"] is True
    assert len(ai_calls) == 1


async def test_a_fresh_read_asks_the_ai_again(client, make_user, key, ai_calls):
    _, headers = await make_user("reader")
    await client.post("/signs/read", params=HERE, files=_photo(), headers=headers)
    await client.post("/signs/read", params={**HERE, "fresh": True}, files=_photo(), headers=headers)
    assert len(ai_calls) == 2


async def test_rules_are_not_reused_elsewhere_or_for_another_sign(client, make_user, key, ai_calls):
    _, headers = await make_user("reader")
    await client.post("/signs/read", params=HERE, files=_photo(), headers=headers)
    await client.post("/signs/read", params={"lat": 32.10, "lng": 34.79}, files=_photo(), headers=headers)
    await client.post("/signs/read", params=HERE, files=_photo(seed=2), headers=headers)
    assert len(ai_calls) == 3


async def test_an_unreadable_sign_is_not_remembered(client, make_user, key, monkeypatch):
    calls = []

    async def unreadable(image):
        calls.append(image)
        return signs.SignRules(readable=False)

    monkeypatch.setattr(signs, "_read_rules", unreadable)
    _, headers = await make_user("reader")
    for _ in range(2):
        body = (await client.post("/signs/read", params=HERE, files=_photo(), headers=headers)).json()
        assert body["verdict"] == "unclear"
    assert len(calls) == 2


async def test_not_a_picture_is_a_400(client, make_user, key, ai_calls):
    _, headers = await make_user("reader")
    bad = {"photo": ("sign.jpg", io.BytesIO(b"not a jpeg"), "image/jpeg")}
    assert (await client.post("/signs/read", files=bad, headers=headers)).status_code == 400
    assert ai_calls == []


async def test_a_garbled_answer_is_a_503_not_a_crash(client, make_user, key, monkeypatch):
    async def broken(image):
        raise ValueError("not json")

    monkeypatch.setattr(signs, "_read_rules", broken)
    _, headers = await make_user("reader")
    assert (await client.post("/signs/read", files=_photo(), headers=headers)).status_code == 503


async def test_only_ai_reads_count_toward_the_limit(client, make_user, key, ai_calls, monkeypatch):
    monkeypatch.setattr(signs, "AI_READS_PER_HOUR", 1)
    _, headers = await make_user("reader")
    assert (await client.post("/signs/read", params=HERE, files=_photo(), headers=headers)).status_code == 200
    assert (await client.post("/signs/read", params=HERE, files=_photo(), headers=headers)).status_code == 200
    assert (await client.post("/signs/read", files=_photo(seed=2), headers=headers)).status_code == 429
