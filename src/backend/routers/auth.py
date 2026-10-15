import asyncio
import logging
import uuid

import jwt
from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, Field
from redis.asyncio import Redis
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

import otp
from auth import create_access_token, get_current_user_id
from config import settings
from database import get_db
from mailer import Mailer, MailError, get_mailer
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


def _echo_code(provider: str) -> bool:
    """Show the code in the app: always in local dev, and in a closed beta
    (BETA_SHOW_CODES) only while nothing really delivers it."""
    return settings.app_env == "local" or (settings.beta_show_codes and provider == "dev")


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
    if otp.review_code(phone) is not None:
        return OtpRequested(phone=phone)  # Play reviewer: fixed code, nothing to send
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
    if settings.sms_provider == "dev":
        await otp.remember_undelivered(redis, phone, code)
    return OtpRequested(phone=phone, dev_code=code if _echo_code(settings.sms_provider) else None)


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


class EmailOtpRequest(BaseModel):
    email: str = Field(min_length=5, max_length=254)


class EmailOtpRequested(BaseModel):
    email: str
    resend_after_seconds: int = otp.RESEND_COOLDOWN_SECONDS
    dev_code: str | None = None


class EmailOtpVerify(BaseModel):
    email: str = Field(min_length=5, max_length=254)
    code: str = Field(min_length=6, max_length=6, pattern=r"^\d{6}$")


def _email_or_422(raw: str) -> str:
    email = otp.normalise_email(raw)
    if email is None:
        raise HTTPException(422, "Enter a valid email address")
    return email


@router.post("/email/request", response_model=EmailOtpRequested)
async def request_email_code(
    payload: EmailOtpRequest,
    request: Request,
    redis: Redis = Depends(get_redis),
    mailer: Mailer = Depends(get_mailer),
) -> EmailOtpRequested:
    """Email a 6-digit login code — the free alternative to SMS, with the
    same rules (expiry, attempts, throttling) from otp.py."""
    email = _email_or_422(payload.email)
    if otp.review_code(email) is not None:
        return EmailOtpRequested(email=email)  # Play reviewer: fixed code, nothing to send
    ip = request.client.host if request.client else "unknown"
    try:
        code = await otp.issue(redis, email, ip)
    except otp.OtpError as e:
        raise HTTPException(e.status, e.detail) from None
    try:
        await mailer.send(
            email,
            f"קוד הכניסה שלך ל-ParkIt: {code}",
            f"שלום,\n\nקוד הכניסה שלך ל-ParkIt הוא: {code}\nהקוד בתוקף 5 דקות.\n\n"
            "אם לא ביקשת להיכנס, אפשר להתעלם מהמייל הזה.",
        )
    except MailError:
        await otp.forget(redis, email)
        raise HTTPException(503, "Couldn't send the email right now — try again in a moment") from None
    if settings.email_provider == "dev":
        await otp.remember_undelivered(redis, email, code)
    return EmailOtpRequested(email=email, dev_code=code if _echo_code(settings.email_provider) else None)


@router.post("/email/verify", response_model=TokenOut)
async def verify_email_code(
    payload: EmailOtpVerify, db: AsyncSession = Depends(get_db), redis: Redis = Depends(get_redis)
) -> TokenOut:
    email = _email_or_422(payload.email)
    try:
        await otp.check(redis, email, payload.code)
    except otp.OtpError as e:
        raise HTTPException(e.status, e.detail) from None
    # Same person whether they first came in by phone or by email: an
    # address already on an account signs into that account.
    user = (await db.execute(select(User).where(User.email == email))).scalar_one_or_none()
    if user is None:
        user = User(auth_provider="email", external_id=f"email:{email}", email=email, display_name="")
        db.add(user)
        await db.commit()
        await db.refresh(user)
    needs_profile = not (user.first_name and user.last_name)
    return TokenOut(access_token=create_access_token(user.id), user_id=user.id, needs_profile=needs_profile)


@router.post("/refresh", response_model=TokenOut)
async def refresh(user_id: uuid.UUID = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)) -> TokenOut:
    """Swap a still-valid token for a fresh one — the app calls this while
    it's open, so only real inactivity ends a session."""
    user = await db.get(User, user_id)
    if user is None:
        raise HTTPException(401, "Invalid or expired token")
    needs_profile = user.auth_provider in ("phone", "email") and not (user.first_name and user.last_name and user.email)
    return TokenOut(access_token=create_access_token(user.id), user_id=user.id, needs_profile=needs_profile)


# --- Sign in with Google -------------------------------------------------------

_GOOGLE_KEYS = jwt.PyJWKClient("https://www.googleapis.com/oauth2/v3/certs", cache_keys=True)


class GoogleLogin(BaseModel):
    id_token: str = Field(min_length=20, max_length=4096)


def _verify_google(token: str) -> dict:
    """Google's ID token, checked against Google's own signing keys: issuer,
    audience (our client ID), expiry, and a verified email."""
    key = _GOOGLE_KEYS.get_signing_key_from_jwt(token).key
    claims = jwt.decode(
        token,
        key,
        algorithms=["RS256"],
        audience=settings.google_client_id,
        issuer=["https://accounts.google.com", "accounts.google.com"],
    )
    if not claims.get("email") or not claims.get("email_verified"):
        raise jwt.InvalidTokenError("no verified email")
    return claims


@router.post("/google", response_model=TokenOut)
async def google_login(payload: GoogleLogin, db: AsyncSession = Depends(get_db)) -> TokenOut:
    """One tap with the Google account on the phone. The same address as an
    email-code sign-in lands in the same account."""
    if not settings.google_client_id:
        raise HTTPException(503, "Google sign-in isn't switched on")
    try:
        claims = await asyncio.to_thread(_verify_google, payload.id_token)
    except (jwt.PyJWTError, jwt.PyJWKClientError):
        raise HTTPException(401, "Google sign-in failed — try again") from None
    email = otp.normalise_email(claims["email"])
    if email is None:
        raise HTTPException(401, "Google sign-in failed — try again")
    user = (await db.execute(select(User).where(User.email == email))).scalar_one_or_none()
    if user is None:
        first = (claims.get("given_name") or "").strip()[:60]
        last = (claims.get("family_name") or "").strip()[:60]
        user = User(
            auth_provider="google",
            external_id=f"google:{claims['sub']}",
            email=email,
            first_name=first or None,
            last_name=last or None,
            display_name=f"{first} {last[0]}." if first and last else first,
        )
        db.add(user)
        await db.commit()
        await db.refresh(user)
    needs_profile = not (user.first_name and user.last_name)
    return TokenOut(access_token=create_access_token(user.id), user_id=user.id, needs_profile=needs_profile)
