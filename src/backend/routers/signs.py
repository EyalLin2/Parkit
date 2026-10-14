"""Parking-sign reader: a photo of an Israeli parking sign in, a plain
answer out — "you may park now, until 19:00" or "not now".

The photo goes to Claude (vision) with the local date and time, and is never
stored. The answer is for a regular driver without a resident permit; the
app always says to double-check the sign itself.
"""

import logging
import uuid
from datetime import datetime
from typing import Literal
from zoneinfo import ZoneInfo

import httpx
from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from pydantic import BaseModel
from redis.asyncio import Redis

import ai
from auth import get_current_user_id
from config import settings
from redis_client import get_redis

log = logging.getLogger("parkit.signs")
router = APIRouter(tags=["sign reader"])

ISRAEL = ZoneInfo("Asia/Jerusalem")
MAX_PHOTO_BYTES = 6 * 1024 * 1024
READS_PER_HOUR = 20
DAYS_HE = ["שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת", "ראשון"]

PROMPT = """You read Israeli street parking signs for a parking app.
Now it is {weekday} ({weekday_he}), {date}, {time} local time in Israel.
The driver is a regular private car WITHOUT a resident permit (תו אזורי) and WITHOUT a disabled permit.
Treat "except permit holders / לבעלי תו" as NOT allowed for this driver.
Look at the photo and decide whether this driver may park here right now.

Answer with JSON only, no other text:
{{"verdict": "allowed" | "not_allowed" | "unclear",
  "until": "HH:MM" or null,       // when the current state ends today, if the sign says
  "paid": true | false | null,    // must pay (blue-white / pay hours)
  "summary_he": "...",            // one short Hebrew sentence for the driver
  "summary_en": "..."}}           // the same in English
Use "unclear" if there is no parking sign, the photo is unreadable, or the rules are ambiguous."""


class SignReading(BaseModel):
    verdict: Literal["allowed", "not_allowed", "unclear"]
    until: str | None = None
    paid: bool | None = None
    summary_he: str
    summary_en: str


async def _ask_claude(image: bytes, media_type: str, now: datetime) -> SignReading:
    prompt = PROMPT.format(
        weekday=now.strftime("%A"),
        weekday_he=DAYS_HE[now.weekday()],
        date=now.strftime("%d/%m/%Y"),
        time=now.strftime("%H:%M"),
    )
    return SignReading(**await ai.ask_json(image, media_type, prompt))


@router.post("/signs/read", response_model=SignReading)
async def read_sign(
    photo: UploadFile = File(...),
    user_id: uuid.UUID = Depends(get_current_user_id),
    redis: Redis = Depends(get_redis),
) -> SignReading:
    if not settings.anthropic_api_key:
        raise HTTPException(503, "The sign reader isn't switched on yet")
    key = f"sign_reads:{user_id}"
    count = await redis.incr(key)
    if count == 1:
        await redis.expire(key, 3600)
    if count > READS_PER_HOUR:
        raise HTTPException(429, "Too many signs for now — try again later")

    image = await photo.read(MAX_PHOTO_BYTES + 1)
    if len(image) > MAX_PHOTO_BYTES:
        raise HTTPException(413, "Photo too large")
    media_type = photo.content_type if photo.content_type in ("image/jpeg", "image/png", "image/webp") else "image/jpeg"
    try:
        return await _ask_claude(image, media_type, datetime.now(ISRAEL))
    except ai.AiOff:
        raise HTTPException(503, "The sign reader isn't switched on yet") from None
    except (httpx.HTTPError, ValueError, KeyError) as e:
        log.warning("sign reader failed: %s", e)
        raise HTTPException(503, "Couldn't read the sign right now — try again") from None
