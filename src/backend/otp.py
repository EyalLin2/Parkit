"""One-time login codes sent by SMS.

Security rules, all enforced in Redis so they hold across app servers:
- 6 random digits (secrets), valid 5 minutes, stored only as an HMAC —
  never in plain text.
- At most 5 wrong guesses per code; then the code is burned.
- Sending is throttled per phone (1 a minute, 5 an hour) and per IP (20 an
  hour) — so nobody can brute-force a code or run up our SMS bill by
  spamming a number ("SMS pumping").
"""

import hashlib
import hmac
import re
import secrets

from redis.asyncio import Redis

from config import settings

CODE_TTL_SECONDS = 300
MAX_ATTEMPTS = 5
RESEND_COOLDOWN_SECONDS = 60
MAX_PER_PHONE_PER_HOUR = 5
MAX_PER_IP_PER_HOUR = 20

_ISRAELI_MOBILE = re.compile(r"^(?:\+?972|0)?(5\d{8})$")


class OtpError(Exception):
    def __init__(self, status: int, detail: str) -> None:
        super().__init__(detail)
        self.status = status
        self.detail = detail


def normalise_phone(raw: str) -> str | None:
    """Israeli mobile → E.164 (+9725XXXXXXXX), or None if it isn't one."""
    digits = re.sub(r"[\s\-()]", "", raw)
    match = _ISRAELI_MOBILE.match(digits)
    return f"+972{match.group(1)}" if match else None


def _digest(phone: str, code: str) -> str:
    return hmac.new(settings.jwt_secret.encode(), f"{phone}:{code}".encode(), hashlib.sha256).hexdigest()


async def _bump(redis: Redis, key: str, ttl: int) -> int:
    count = await redis.incr(key)
    if count == 1:
        await redis.expire(key, ttl)
    return count


async def issue(redis: Redis, phone: str, ip: str) -> str:
    if await redis.exists(f"otp:cooldown:{phone}"):
        raise OtpError(429, "Please wait a minute before asking for another code")
    if await _bump(redis, f"otp:phone_hour:{phone}", 3600) > MAX_PER_PHONE_PER_HOUR:
        raise OtpError(429, "Too many codes for this number — try again later")
    if await _bump(redis, f"otp:ip_hour:{ip}", 3600) > MAX_PER_IP_PER_HOUR:
        raise OtpError(429, "Too many requests — try again later")

    code = f"{secrets.randbelow(1_000_000):06d}"
    await redis.set(f"otp:code:{phone}", _digest(phone, code), ex=CODE_TTL_SECONDS)
    await redis.delete(f"otp:attempts:{phone}")
    await redis.set(f"otp:cooldown:{phone}", "1", ex=RESEND_COOLDOWN_SECONDS)
    return code


async def check(redis: Redis, phone: str, code: str) -> None:
    stored = await redis.get(f"otp:code:{phone}")
    if stored is None:
        raise OtpError(400, "The code expired — ask for a new one")
    if await _bump(redis, f"otp:attempts:{phone}", CODE_TTL_SECONDS) > MAX_ATTEMPTS:
        await redis.delete(f"otp:code:{phone}")
        raise OtpError(429, "Too many wrong codes — ask for a new one")
    stored = stored.decode() if isinstance(stored, bytes) else stored
    if not hmac.compare_digest(stored, _digest(phone, code.strip())):
        raise OtpError(400, "Wrong code")
    await redis.delete(f"otp:code:{phone}", f"otp:attempts:{phone}")


async def forget(redis: Redis, phone: str) -> None:
    """Undo an issued code whose SMS never left (so the user isn't stuck in
    the resend cooldown for a message they never got)."""
    await redis.delete(f"otp:code:{phone}", f"otp:cooldown:{phone}")
