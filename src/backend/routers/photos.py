import asyncio
import base64
import uuid

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from pydantic import BaseModel
from redis.asyncio import Redis

from auth import get_current_user_id
from blur import blur_sensitive
from redis_client import get_redis

router = APIRouter(prefix="/photos", tags=["photos"])

STAGING_TTL_SECONDS = 600
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
