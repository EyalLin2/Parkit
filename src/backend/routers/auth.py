import uuid

from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from auth import create_access_token
from database import get_db
from models import User

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


@router.post("/dev-login", response_model=TokenOut)
async def dev_login(payload: DevLoginRequest, db: AsyncSession = Depends(get_db)) -> TokenOut:
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
