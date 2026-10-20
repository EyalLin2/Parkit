import asyncio
import base64
import logging
import uuid

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from pydantic import BaseModel
from redis.asyncio import Redis

import ai
from auth import get_current_user_id
from blur import blur_sensitive
from config import settings
from redis_client import get_redis

log = logging.getLogger("parkit.photos")
router = APIRouter(prefix="/photos", tags=["photos"])

STAGING_TTL_SECONDS = 600
# OpenCV releases the GIL, so unbounded parallel blurs eat every CPU core and
# starve map requests (seen on a 1-core server: /spots p50 2.5 s at 100
# users). One blur at a time per worker keeps the map responsive; extra
# photos wait their turn instead.
_BLUR_SLOTS = asyncio.Semaphore(settings.blur_concurrency)
MAX_UPLOAD_BYTES = 8 * 1024 * 1024


class StagedPhotoOut(BaseModel):
    staging_id: str
    faces_blurred: int
    plates_blurred: int = 0
    preview_base64: str


@router.post("/stage", response_model=StagedPhotoOut, status_code=201)
async def stage_photo(
    file: UploadFile = File(...),
    _user_id: uuid.UUID = Depends(get_current_user_id),
    redis: Redis = Depends(get_redis),
) -> StagedPhotoOut:
    raw = await file.read()
    if len(raw) > MAX_UPLOAD_BYTES:
        raise HTTPException(413, "Photo too large")

    try:
        # CPU-heavy (OpenCV): run it in a worker thread so the event loop keeps
        # serving everyone else meanwhile (load-test bug #3 — it used to stall
        # every request for the duration of each blur).
        async with _BLUR_SLOTS:
            blurred, faces_blurred, plates_blurred = await asyncio.to_thread(blur_sensitive, raw)
    except ValueError:
        raise HTTPException(400, "Could not process image") from None

    staging_id = str(uuid.uuid4())
    await redis.set(f"photo_staging:{staging_id}", base64.b64encode(blurred).decode(), ex=STAGING_TTL_SECONDS)

    return StagedPhotoOut(
        staging_id=staging_id,
        faces_blurred=faces_blurred,
        plates_blurred=plates_blurred,
        preview_base64=base64.b64encode(blurred).decode(),
    )


# --- Report auto-fill: what the photo shows, so the driver taps less --------

ANALYZE_PER_HOUR = 30
ANALYZE_PROMPT = """This photo was just taken by a driver reporting a free street parking spot in Israel.
Look at it and answer with JSON only, no other text:
{"looks_like_free_spot": true | false,   // an empty kerbside space a car could park in
 "blue_white_curb": true | false,          // the kerb is painted blue and white (paid parking)
 "disabled_spot": true | false}           // a disabled-parking sign or wheelchair marking
Answer true ONLY when it is clearly visible in the photo; if in any doubt, answer false."""


class PhotoHints(BaseModel):
    looks_like_free_spot: bool = True
    blue_white_curb: bool = False
    disabled_spot: bool = False
    # No size: a photo has no scale, so a guess would be wrong too often.


@router.post("/{staging_id}/analyze", response_model=PhotoHints)
async def analyze_photo(
    staging_id: str,
    user_id: uuid.UUID = Depends(get_current_user_id),
    redis: Redis = Depends(get_redis),
) -> PhotoHints:
    """Suggestions for the report form from the (already blurred) staged
    photo. Advisory only: the app pre-fills, the driver decides."""
    staged = await redis.get(f"photo_staging:{staging_id}")
    if staged is None:
        raise HTTPException(404, "Photo expired or not found")
    key = f"photo_analyze:{user_id}"
    count = await redis.incr(key)
    if count == 1:
        await redis.expire(key, 3600)
    if count > ANALYZE_PER_HOUR:
        raise HTTPException(429, "Too many photos for now")
    try:
        return PhotoHints(**await ai.ask_json(base64.b64decode(staged), "image/jpeg", ANALYZE_PROMPT, max_tokens=150))
    except ai.AiOff:
        raise HTTPException(503, "Photo analysis isn't switched on") from None
    except Exception as e:  # noqa: BLE001 — any model/network hiccup just means no hints
        log.warning("photo analysis failed: %s", e)
        raise HTTPException(503, "No hints this time") from None
