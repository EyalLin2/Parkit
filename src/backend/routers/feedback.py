"""Feedback and crash reports from the app (for testing with real people).

Signed-in users' feedback is tied to their account; crash reports may come
before sign-in, so the token is optional. Throttled per client so it can't
be used to flood the table.
"""

import uuid
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException, Request, Response
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, Field
from redis.asyncio import Redis
from sqlalchemy.ext.asyncio import AsyncSession

from auth import get_current_user_id
from database import get_db
from models import AppFeedback
from redis_client import get_redis

router = APIRouter(tags=["feedback"])
_optional_bearer = HTTPBearer(auto_error=False)
MAX_PER_HOUR = 20


class FeedbackIn(BaseModel):
    kind: Literal["feedback", "crash"] = "feedback"
    message: str = Field(min_length=1, max_length=8000)
    app_version: str | None = Field(default=None, max_length=40)
    device: str | None = Field(default=None, max_length=120)


@router.post("/feedback", status_code=204)
async def send_feedback(
    payload: FeedbackIn,
    request: Request,
    creds: HTTPAuthorizationCredentials | None = Depends(_optional_bearer),
    db: AsyncSession = Depends(get_db),
    redis: Redis = Depends(get_redis),
) -> Response:
    user_id: uuid.UUID | None = None
    if creds is not None:
        try:
            user_id = await get_current_user_id(creds)
        except HTTPException:
            user_id = None  # an expired token shouldn't lose a crash report
    who = str(user_id) if user_id else (request.client.host if request.client else "unknown")
    key = f"feedback:{who}"
    count = await redis.incr(key)
    if count == 1:
        await redis.expire(key, 3600)
    if count > MAX_PER_HOUR:
        raise HTTPException(429, "Too much feedback at once — thank you, try again later")
    db.add(AppFeedback(user_id=user_id, **payload.model_dump()))
    await db.commit()
    return Response(status_code=204)
