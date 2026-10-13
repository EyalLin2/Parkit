"""Per-client request limit, shared by every server process through Redis.

A fixed one-minute window per IP is plenty here: a normal driver makes a
few dozen requests a minute (map moves, address lookups); a script hammering
the API gets 429s instead of slowing the server for everyone (load-test bug
#4 — past ~85 req/s the server collapsed rather than pushing back).
"""

import time

from fastapi import Request
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware

from config import settings
from redis_client import redis_client

EXEMPT_PATHS = ("/healthz", "/docs", "/openapi.json")


class RateLimitMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        limit = settings.rate_limit_per_minute
        if limit <= 0 or request.url.path.startswith(EXEMPT_PATHS):
            return await call_next(request)
        ip = request.client.host if request.client else "unknown"
        key = f"rl:{ip}:{int(time.time() // 60)}"
        try:
            count = await redis_client.incr(key)
            if count == 1:
                await redis_client.expire(key, 70)
        except Exception:
            return await call_next(request)  # Redis down: don't take the API down with it
        if count > limit:
            return JSONResponse(
                {"detail": "Too many requests — slow down a little"}, status_code=429, headers={"Retry-After": "60"}
            )
        return await call_next(request)
