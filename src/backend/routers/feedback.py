"""Feedback and crash reports from the app (for testing with real people).

Signed-in users' feedback is tied to their account; crash reports may come
before sign-in, so the token is optional. Throttled per client so it can't
be used to flood the table.
"""

import hmac
import html
import uuid
from datetime import datetime
from typing import Literal
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, HTTPException, Query, Request, Response
from fastapi.responses import HTMLResponse
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, Field
from redis.asyncio import Redis
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

import otp
from auth import get_current_user_id
from config import settings
from database import get_db
from models import AppFeedback, User
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


_INBOX_CSS = (
    "body{font:14px system-ui;margin:16px}table{border-collapse:collapse;width:100%}"
    "td,th{border-top:1px solid #ddd;padding:8px;vertical-align:top;text-align:left}"
    "pre{white-space:pre-wrap;margin:0;font:13px ui-monospace,monospace}"
    "tr.crash td:nth-child(2){color:#c5221f;font-weight:600}"
)


@router.get("/admin/feedback", response_class=HTMLResponse, include_in_schema=False)
async def feedback_inbox(token: str = Query(""), db: AsyncSession = Depends(get_db)) -> HTMLResponse:
    """The tester inbox: newest feedback and crash reports, one page.
    Opened with ?token=ADMIN_TOKEN; closed entirely while it's unset."""
    if not settings.admin_token or not hmac.compare_digest(token, settings.admin_token):
        raise HTTPException(404, "Not found")
    rows = (
        await db.execute(
            select(AppFeedback, User.display_name)
            .outerjoin(User, User.id == AppFeedback.user_id)
            .order_by(AppFeedback.id.desc())
            .limit(200)
        )
    ).all()
    body = "".join(
        f"<tr class='{html.escape(f.kind)}'><td>{f.created_at:%d/%m %H:%M}</td><td>{html.escape(f.kind)}</td>"
        f"<td>{html.escape(name or '—')}</td><td><pre>{html.escape(f.message)}</pre></td>"
        f"<td>{html.escape(f.app_version or '')}<br><small>{html.escape(f.device or '')}</small></td></tr>"
        for f, name in rows
    )
    page = (
        "<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width'>"
        f"<title>ParkIt — tester inbox</title><style>{_INBOX_CSS}</style>"
        f"<h1>ParkIt — tester inbox ({len(rows)})</h1><table><tr><th>When</th><th>Kind</th><th>Who</th>"
        f"<th>Message</th><th>App / device</th></tr>{body}</table>"
    )
    return HTMLResponse(page)


_ISRAEL = ZoneInfo("Asia/Jerusalem")


@router.get("/admin/codes", response_class=HTMLResponse, include_in_schema=False)
async def undelivered_codes(token: str = Query(""), redis: Redis = Depends(get_redis)) -> HTMLResponse:
    """Login codes that had no real SMS/email provider to go out through
    (closed beta) — so the admin can pass them on. Same ADMIN_TOKEN gate as
    the inbox; refreshes itself every 5 seconds."""
    if not settings.admin_token or not hmac.compare_digest(token, settings.admin_token):
        raise HTTPException(404, "Not found")
    rows = await otp.undelivered(redis)
    body = (
        "".join(
            f"<tr><td>{datetime.fromtimestamp(r['at'], _ISRAEL):%H:%M:%S}</td>"
            f"<td>{html.escape(r['to'])}</td><td class=code>{html.escape(r['code'])}</td></tr>"
            for r in rows
        )
        or "<tr><td colspan=3>No codes in the last 5 minutes.</td></tr>"
    )
    page = (
        "<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width'>"
        "<meta http-equiv=refresh content=5>"
        "<title>ParkIt — login codes</title>"
        f"<style>{_INBOX_CSS} .code{{font:700 1.6rem monospace;letter-spacing:.15em}}</style>"
        "<h1>ParkIt — login codes</h1><p>Valid 5 minutes. Refreshes every 5 seconds.</p>"
        f"<table><tr><th>When</th><th>To</th><th>Code</th></tr>{body}</table>"
    )
    return HTMLResponse(page)
