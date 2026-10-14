"""One-time login codes, sent by SMS or email (the target is a phone or an
address — the rules are the same).

Security rules, all enforced in Redis so they hold across app servers:
- 6 random digits (secrets), valid 5 minutes, stored only as an HMAC —
  never in plain text.
- At most 5 wrong guesses per code; then the code is burned.
- Sending is throttled per target (1 a minute, 5 an hour) and per IP (20 an
  hour) — so nobody can brute-force a code or run up our SMS bill by
  spamming a number ("SMS pumping").
"""

import hashlib
import hmac
import json
import re
import secrets
import time

from redis.asyncio import Redis

from config import settings

CODE_TTL_SECONDS = 300
MAX_ATTEMPTS = 5
RESEND_COOLDOWN_SECONDS = 60
MAX_PER_PHONE_PER_HOUR = 5
MAX_PER_IP_PER_HOUR = 20

_EMAIL = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]{2,}$")
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


def normalise_email(raw: str) -> str | None:
    email = raw.strip().lower()
    return email if len(email) <= 254 and _EMAIL.match(email) else None


def review_code(target: str) -> str | None:
    """The fixed code of the Play review account, if `target` is it."""
    review_target, _, code = settings.review_login.rpartition(":")
    if not review_target or len(code) != 6 or not code.isdigit():
        return None
    normalised = normalise_phone(review_target) or normalise_email(review_target)
    return code if normalised == target else None


def _digest(target: str, code: str) -> str:
    return hmac.new(settings.jwt_secret.encode(), f"{target}:{code}".encode(), hashlib.sha256).hexdigest()


async def _bump(redis: Redis, key: str, ttl: int) -> int:
    count = await redis.incr(key)
    if count == 1:
        await redis.expire(key, ttl)
    return count


async def issue(redis: Redis, target: str, ip: str) -> str:
    if await redis.exists(f"otp:cooldown:{target}"):
        raise OtpError(429, "Please wait a minute before asking for another code")
    if await _bump(redis, f"otp:phone_hour:{target}", 3600) > MAX_PER_PHONE_PER_HOUR:
        raise OtpError(429, "Too many codes for this number or address — try again later")
    if await _bump(redis, f"otp:ip_hour:{ip}", 3600) > MAX_PER_IP_PER_HOUR:
        raise OtpError(429, "Too many requests — try again later")

    code = f"{secrets.randbelow(1_000_000):06d}"
    await redis.set(f"otp:code:{target}", _digest(target, code), ex=CODE_TTL_SECONDS)
    await redis.delete(f"otp:attempts:{target}")
    await redis.set(f"otp:cooldown:{target}", "1", ex=RESEND_COOLDOWN_SECONDS)
    return code


async def check(redis: Redis, target: str, code: str) -> None:
    fixed = review_code(target)
    if fixed is not None:
        # Still capped: 5 wrong guesses lock the review account for 5 minutes.
        if await _bump(redis, f"otp:attempts:{target}", CODE_TTL_SECONDS) > MAX_ATTEMPTS:
            raise OtpError(429, "Too many wrong codes — ask for a new one")
        if not hmac.compare_digest(fixed, code.strip()):
            raise OtpError(400, "Wrong code")
        await redis.delete(f"otp:attempts:{target}")
        return
    stored = await redis.get(f"otp:code:{target}")
    if stored is None:
        raise OtpError(400, "The code expired — ask for a new one")
    if await _bump(redis, f"otp:attempts:{target}", CODE_TTL_SECONDS) > MAX_ATTEMPTS:
        await redis.delete(f"otp:code:{target}")
        raise OtpError(429, "Too many wrong codes — ask for a new one")
    stored = stored.decode() if isinstance(stored, bytes) else stored
    if not hmac.compare_digest(stored, _digest(target, code.strip())):
        raise OtpError(400, "Wrong code")
    await redis.delete(f"otp:code:{target}", f"otp:attempts:{target}")


async def forget(redis: Redis, target: str) -> None:
    """Undo an issued code whose SMS never left (so the user isn't stuck in
    the resend cooldown for a message they never got)."""
    await redis.delete(f"otp:code:{target}", f"otp:cooldown:{target}")


# While there's no real SMS/email provider (EMAIL_PROVIDER/SMS_PROVIDER=dev)
# a code goes nowhere but the server log. For the closed beta the admin can
# read the last few on /admin/codes instead. Kept 10 minutes, at most 50.
ADMIN_CODES_KEY = "admin:undelivered_codes"


async def remember_undelivered(redis: Redis, target: str, code: str) -> None:
    entry = json.dumps({"to": target, "code": code, "at": int(time.time())})
    await redis.lpush(ADMIN_CODES_KEY, entry)
    await redis.ltrim(ADMIN_CODES_KEY, 0, 49)
    await redis.expire(ADMIN_CODES_KEY, 600)


async def undelivered(redis: Redis) -> list[dict]:
    now = time.time()
    rows = [json.loads(r) for r in await redis.lrange(ADMIN_CODES_KEY, 0, 49)]
    return [r for r in rows if now - r["at"] < CODE_TTL_SECONDS]
