import logging
import uuid

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, Field
from redis.asyncio import Redis
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

import otp
from auth import create_access_token, get_current_user_id
from config import settings
from database import get_db
from models import User
from redis_client import get_redis
from sms import SmsError, SmsSender, get_sms_sender

log = logging.getLogger("parkit.auth")

router = APIRouter(prefix="/auth", tags=["auth"])


class DevLoginRequest(BaseModel):
    """Stand-in for real Google/Apple sign-in + SMS phone verification
    (PRODUCT_SPEC.md -> User Roles & Access). Get-or-creates a user by
    (auth_provider, external_id) and returns a real JWT, so the rest of
    the API can be built and tested against real token auth. Swapping
    this for actual provider verification later doesn't change any
    other endpoint.
    """

    auth_provider: str
    external_id: str
    display_name: str


class TokenOut(BaseModel):
    access_token: str
    user_id: uuid.UUID
    # True until the user filled in first/last name + email (phone sign-in).
    needs_profile: bool = False


class OtpRequest(BaseModel):
    phone: str = Field(min_length=9, max_length=20)


class OtpRequested(BaseModel):
    phone: str  # normalised, so the app shows exactly where the code went
    resend_after_seconds: int = otp.RESEND_COOLDOWN_SECONDS
    # Local development only: the code, so the emulator flow needs no real SMS.
    dev_code: str | None = None


class OtpVerify(BaseModel):
    phone: str = Field(min_length=9, max_length=20)
    code: str = Field(min_length=6, max_length=6, pattern=r"^\d{6}$")


def _phone_or_422(raw: str) -> str:
    phone = otp.normalise_phone(raw)
    if phone is None:
        raise HTTPException(422, "Enter an Israeli mobile number (05X-XXXXXXX)")
    return phone


@router.post("/otp/request", response_model=OtpRequested)
async def request_code(
    payload: OtpRequest,
    request: Request,
    redis: Redis = Depends(get_redis),
    sms: SmsSender = Depends(get_sms_sender),
) -> OtpRequested:
    """Text a 6-digit login code. Same answer whether or not the number has
    an account, so this can't be used to find out who uses ParkIt."""
    phone = _phone_or_422(payload.phone)
    ip = request.client.host if request.client else "unknown"
    try:
        code = await otp.issue(redis, phone, ip)
    except otp.OtpError as e:
        raise HTTPException(e.status, e.detail) from None
    try:
        await sms.send(phone, f"ParkIt: קוד הכניסה שלך הוא {code}. בתוקף 5 דקות.")
    except SmsError:
        await otp.forget(redis, phone)  # nothing was delivered — let them retry right away
        raise HTTPException(503, "Couldn't send the SMS right now — try again in a moment") from None
    return OtpRequested(phone=phone, dev_code=code if settings.app_env == "local" else None)


@router.post("/otp/verify", response_model=TokenOut)
async def verify_code(
    payload: OtpVerify, db: AsyncSession = Depends(get_db), redis: Redis = Depends(get_redis)
) -> TokenOut:
    """Right code → log in, creating the account on first sign-in."""
    phone = _phone_or_422(payload.phone)
    try:
        await otp.check(redis, phone, payload.code)
    except otp.OtpError as e:
        raise HTTPException(e.status, e.detail) from None

    user = (await db.execute(select(User).where(User.phone == phone))).scalar_one_or_none()
    if user is None:
        user = User(auth_provider="phone", external_id=f"phone:{phone}", phone=phone, display_name="")
        db.add(user)
        await db.commit()
        await db.refresh(user)
    needs_profile = not (user.first_name and user.last_name and user.email)
    return TokenOut(access_token=create_access_token(user.id), user_id=user.id, needs_profile=needs_profile)


@router.post("/dev-login", response_model=TokenOut)
async def dev_login(payload: DevLoginRequest, db: AsyncSession = Depends(get_db)) -> TokenOut:
    # Anyone could log in as anyone with this — it must never be reachable
    # outside local development and tests.
    if settings.app_env not in ("local", "test"):
        raise HTTPException(404, "Not found")
    result = await db.execute(select(User).where(User.external_id == payload.external_id))
    user = result.scalar_one_or_none()

    if user is None:
        user = User(
            auth_provider=payload.auth_provider,
            external_id=payload.external_id,
            display_name=payload.display_name,
        )
        db.add(user)
        await db.commit()
        await db.refresh(user)

    return TokenOut(access_token=create_access_token(user.id), user_id=user.id)


@router.post("/refresh", response_model=TokenOut)
async def refresh(user_id: uuid.UUID = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)) -> TokenOut:
    """Swap a still-valid token for a fresh one — the app calls this while
    it's open, so only real inactivity ends a session."""
    user = await db.get(User, user_id)
    if user is None:
        raise HTTPException(401, "Invalid or expired token")
    needs_profile = user.auth_provider == "phone" and not (user.first_name and user.last_name and user.email)
    return TokenOut(access_token=create_access_token(user.id), user_id=user.id, needs_profile=needs_profile)
