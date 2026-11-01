import uuid
from datetime import datetime, timedelta, timezone

import jwt
from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from config import settings

JWT_ALGORITHM = "HS256"
# Sessions are sliding: a token lives settings.session_idle_minutes (two
# weeks by default) and the app swaps it for a fresh one while it's in use
# (POST /auth/refresh). Stay away longer than that and the next open asks
# for a new code.

_security = HTTPBearer()


def create_access_token(user_id: uuid.UUID) -> str:
    payload = {
        "sub": str(user_id),
        "exp": datetime.now(timezone.utc) + timedelta(minutes=settings.session_idle_minutes),
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm=JWT_ALGORITHM)


async def get_current_user_id(
    creds: HTTPAuthorizationCredentials = Depends(_security),
) -> uuid.UUID:
    try:
        payload = jwt.decode(creds.credentials, settings.jwt_secret, algorithms=[JWT_ALGORITHM])
        return uuid.UUID(payload["sub"])
    except (jwt.PyJWTError, ValueError, KeyError):
        raise HTTPException(status_code=401, detail="Invalid or expired token")
