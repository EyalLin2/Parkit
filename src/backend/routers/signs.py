"""Parking-sign reader: a photo of an Israeli parking sign in, a plain
answer out — "you may park now, until 19:00" or "not now".

A sign doesn't change but the answer does, so Claude reads the sign's rules once
and this file works out the answer for the current time. Rules are shared: the
next driver photographing the same sign in the same place gets them without an
AI call. Photos are never stored, and the rules carry no user. The app always
says to double-check the sign itself.
"""

import asyncio
import logging
import uuid
from datetime import datetime, timedelta, timezone
from typing import Literal
from zoneinfo import ZoneInfo

import cv2
import httpx
import numpy as np
from fastapi import APIRouter, Depends, File, HTTPException, Query, UploadFile
from geoalchemy2.functions import ST_DWithin, ST_MakePoint, ST_SetSRID
from pydantic import BaseModel, Field
from redis.asyncio import Redis
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

import ai
from auth import get_current_user_id
from config import settings
from database import get_db
from models import SignRules as SignRulesRow
from redis_client import get_redis

log = logging.getLogger("parkit.signs")
router = APIRouter(tags=["sign reader"])

ISRAEL = ZoneInfo("Asia/Jerusalem")
MAX_PHOTO_BYTES = 6 * 1024 * 1024
AI_READS_PER_HOUR = 20

# An image costs about width × height / 750 input tokens. 1024 px keeps small
# Hebrew text readable at roughly a third of a full phone photo's cost.
MAX_SIDE_PX = 1024
HASH_SIDE = 16
# Signs look alike, and a wrong match can cost the driver a fine, so a match has
# to be close in both place and picture. A missed match only costs one AI call.
SAME_SIGN_RADIUS_M = 50
SAME_SIGN_MAX_BITS = 24
CANDIDATES = 20
# Municipalities do change signs.
RULES_KEPT_DAYS = 60

PROMPT = """You read Israeli street parking signs for a parking app.
The driver is a regular private car WITHOUT a resident permit (תו אזורי) and WITHOUT a disabled permit.
Treat "except permit holders / לבעלי תו" as not_allowed for this driver.
Describe the sign's whole weekly schedule, not any particular moment.

Answer with JSON only, no other text:
{"readable": true | false,      // false if there is no parking sign, it is unreadable, or the rules are ambiguous
 "windows": [{"days": [0-6],    // 0 = Sunday ... 6 = Saturday
              "from": "HH:MM", "to": "HH:MM",  // "to" after "from"; split overnight windows at 24:00
              "state": "allowed" | "paid" | "not_allowed"}],
 "otherwise": "allowed" | "paid" | "not_allowed",  // outside those windows
 "holidays": true | false,      // the sign has different rules for holidays or their eves
 "note_he": "..." or null,      // only a condition the schedule can't show, in short Hebrew
 "note_en": "..." or null}      // the same in English"""


class Window(BaseModel):
    days: list[int] = Field(min_length=1)
    start: str = Field(alias="from", pattern=r"^\d{2}:\d{2}$")
    end: str = Field(alias="to", pattern=r"^\d{2}:\d{2}$")
    state: Literal["allowed", "paid", "not_allowed"]

    model_config = {"populate_by_name": True}


class SignRules(BaseModel):
    readable: bool
    windows: list[Window] = []
    otherwise: Literal["allowed", "paid", "not_allowed"] = "allowed"
    holidays: bool = False
    note_he: str | None = None
    note_en: str | None = None


class SignReading(BaseModel):
    verdict: Literal["allowed", "not_allowed", "unclear"]
    until: str | None = None
    # The next change today ("from 19:00 parking is paid") — what the
    # reminder is set for.
    changes_at: str | None = None
    changes_to: Literal["allowed", "paid", "not_allowed"] | None = None
    paid: bool | None = None
    summary_he: str
    summary_en: str
    # Read earlier from a sign that looks the same here; the driver can ask for a fresh read.
    from_cache: bool = False


def _state_at(rules: SignRules, day: int, hhmm: str) -> str:
    for window in rules.windows:
        if day in window.days and window.start <= hhmm < window.end:
            return window.state
    return rules.otherwise


def evaluate(rules: SignRules, now: datetime) -> SignReading:
    """What the sign means right now. Plain code, so a cached reading can't go
    stale the way a stored "allowed until 19:00" would."""
    if not rules.readable:
        return SignReading(
            verdict="unclear",
            summary_he="לא הצלחנו להבין את השלט. בדקו אותו בעצמכם.",
            summary_en="We couldn't make out this sign. Please check it yourself.",
        )
    day = (now.weekday() + 1) % 7  # Python's Monday = 0, the sign's Sunday = 0
    hhmm = now.strftime("%H:%M")
    state = _state_at(rules, day, hhmm)

    boundaries = sorted({t for w in rules.windows if day in w.days for t in (w.start, w.end) if hhmm < t < "24:00"})
    changes_at = next((t for t in boundaries if _state_at(rules, day, t) != state), None)
    changes_to = _state_at(rules, day, changes_at) if changes_at else None

    until_he = f" עד {changes_at}" if changes_at else ""
    until_en = f" until {changes_at}" if changes_at else ""
    if state == "allowed":
        summary_he, summary_en = f"מותר לחנות עכשיו{until_he}.", f"You may park here now{until_en}."
    elif state == "paid":
        summary_he = f"מותר לחנות עכשיו בתשלום{until_he}."
        summary_en = f"You may park here now, for a fee{until_en}."
    else:
        summary_he, summary_en = f"אסור לחנות כאן עכשיו{until_he}.", f"You may not park here now{until_en}."
    if rules.holidays:
        summary_he += " בחגים ובערבי חג הכללים שונים."
        summary_en += " Different rules apply on holidays and their eves."
    if rules.note_he:
        summary_he += f" {rules.note_he}"
    if rules.note_en:
        summary_en += f" {rules.note_en}"

    return SignReading(
        verdict="not_allowed" if state == "not_allowed" else "allowed",
        until=changes_at,
        changes_at=changes_at,
        changes_to=changes_to,
        paid=state == "paid",
        summary_he=summary_he,
        summary_en=summary_en,
    )


def _prepare(raw: bytes) -> tuple[bytes, str]:
    """A smaller JPEG for the model, and a fingerprint of how the sign looks."""
    image = cv2.imdecode(np.frombuffer(raw, np.uint8), cv2.IMREAD_COLOR)
    if image is None:
        raise ValueError("not an image")
    height, width = image.shape[:2]
    scale = MAX_SIDE_PX / max(height, width)
    if scale < 1:
        image = cv2.resize(image, (round(width * scale), round(height * scale)), interpolation=cv2.INTER_AREA)
    ok, jpeg = cv2.imencode(".jpg", image, [cv2.IMWRITE_JPEG_QUALITY, 85])
    if not ok:
        raise ValueError("could not encode")
    gray = cv2.resize(cv2.cvtColor(image, cv2.COLOR_BGR2GRAY), (HASH_SIDE + 1, HASH_SIDE), interpolation=cv2.INTER_AREA)
    bits = (gray[:, 1:] > gray[:, :-1]).flatten()
    fingerprint = f"{int(''.join('1' if b else '0' for b in bits), 2):064x}"
    return jpeg.tobytes(), fingerprint


def _bits_apart(a: str, b: str) -> int:
    return (int(a, 16) ^ int(b, 16)).bit_count()


async def _read_rules(image: bytes) -> SignRules:
    return SignRules(**await ai.ask_json(image, "image/jpeg", PROMPT))


async def _known_rules(db: AsyncSession, lat: float, lng: float, fingerprint: str) -> SignRules | None:
    here = ST_SetSRID(ST_MakePoint(lng, lat), 4326)
    cutoff = datetime.now(timezone.utc) - timedelta(days=RULES_KEPT_DAYS)
    rows = await db.scalars(
        select(SignRulesRow)
        .where(ST_DWithin(SignRulesRow.location, here, SAME_SIGN_RADIUS_M), SignRulesRow.created_at >= cutoff)
        .order_by(SignRulesRow.created_at.desc())
        .limit(CANDIDATES)
    )
    for row in rows:
        if _bits_apart(row.fingerprint, fingerprint) <= SAME_SIGN_MAX_BITS:
            row.reuse_count += 1
            await db.commit()
            return SignRules(**row.rules)
    return None


async def _remember(db: AsyncSession, lat: float, lng: float, fingerprint: str, rules: SignRules) -> None:
    cutoff = datetime.now(timezone.utc) - timedelta(days=RULES_KEPT_DAYS)
    await db.execute(delete(SignRulesRow).where(SignRulesRow.created_at < cutoff))
    db.add(
        SignRulesRow(
            location=f"SRID=4326;POINT({lng} {lat})",
            fingerprint=fingerprint,
            rules=rules.model_dump(by_alias=True),
        )
    )
    await db.commit()


@router.post("/signs/read", response_model=SignReading)
async def read_sign(
    photo: UploadFile = File(...),
    lat: float | None = Query(None, ge=-90, le=90),
    lng: float | None = Query(None, ge=-180, le=180),
    fresh: bool = Query(False),
    user_id: uuid.UUID = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
    redis: Redis = Depends(get_redis),
) -> SignReading:
    if not settings.anthropic_api_key:
        raise HTTPException(503, "The sign reader isn't switched on yet")
    raw = await photo.read(MAX_PHOTO_BYTES + 1)
    if len(raw) > MAX_PHOTO_BYTES:
        raise HTTPException(413, "Photo too large")
    try:
        image, fingerprint = await asyncio.to_thread(_prepare, raw)
    except ValueError:
        raise HTTPException(400, "Could not process image") from None

    now = datetime.now(ISRAEL)
    placed = lat is not None and lng is not None
    if placed and not fresh:
        known = await _known_rules(db, lat, lng, fingerprint)
        if known is not None:
            return evaluate(known, now).model_copy(update={"from_cache": True})

    # Only real AI calls count: a sign someone already read costs nothing.
    key = f"sign_reads:{user_id}"
    count = await redis.incr(key)
    if count == 1:
        await redis.expire(key, 3600)
    if count > AI_READS_PER_HOUR:
        raise HTTPException(429, "Too many signs for now — try again later")
    try:
        rules = await _read_rules(image)
    except ai.AiOff:
        raise HTTPException(503, "The sign reader isn't switched on yet") from None
    except (httpx.HTTPError, ValueError, KeyError, TypeError) as e:
        log.warning("sign reader failed: %s", e)
        raise HTTPException(503, "Couldn't read the sign right now — try again") from None
    if placed and rules.readable:
        await _remember(db, lat, lng, fingerprint, rules)
    return evaluate(rules, now)
