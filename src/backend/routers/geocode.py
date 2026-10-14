from typing import Literal

from fastapi import APIRouter, Depends, Query
from redis.asyncio import Redis
from sqlalchemy.ext.asyncio import AsyncSession

from database import get_db
from geocoder import reverse_cached
from redis_client import get_redis
from schemas import ReverseGeocodeOut

router = APIRouter(prefix="/geocode", tags=["geocode"])


@router.get("/reverse", response_model=ReverseGeocodeOut)
async def reverse_geocode(
    lat: float = Query(ge=-90, le=90),
    lng: float = Query(ge=-180, le=180),
    lang: Literal["he", "en"] = "he",
    db: AsyncSession = Depends(get_db),
    redis: Redis = Depends(get_redis),
) -> ReverseGeocodeOut:
    result = await reverse_cached(db, redis, lat, lng, lang)
    return ReverseGeocodeOut(label=result.label, street=result.street)
