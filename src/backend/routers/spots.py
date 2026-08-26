from fastapi import APIRouter, Depends, Query
from geoalchemy2.functions import ST_DWithin, ST_Distance, ST_MakePoint, ST_SetSRID
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from database import get_db
from models import Spot, SpotStatus
from schemas import SpotCreate, SpotOut

router = APIRouter(prefix="/spots", tags=["spots"])


@router.post("", response_model=SpotOut, status_code=201)
async def report_spot(payload: SpotCreate, db: AsyncSession = Depends(get_db)) -> Spot:
    spot = Spot(
        reporter_id=payload.reporter_id,
        lat=payload.lat,
        lng=payload.lng,
        location=ST_SetSRID(ST_MakePoint(payload.lng, payload.lat), 4326),
        spot_type=payload.spot_type,
        payment=payload.payment,
        photo_url=payload.photo_url,
    )
    db.add(spot)
    await db.commit()
    await db.refresh(spot)
    return spot


@router.get("", response_model=list[SpotOut])
async def nearby_spots(
    lat: float = Query(..., ge=-90, le=90),
    lng: float = Query(..., ge=-180, le=180),
    radius_m: int = Query(1000, gt=0, le=5000),
    db: AsyncSession = Depends(get_db),
) -> list[Spot]:
    origin = ST_SetSRID(ST_MakePoint(lng, lat), 4326)
    stmt = (
        select(Spot)
        .where(Spot.status == SpotStatus.active)
        .where(ST_DWithin(Spot.location, origin, radius_m))
        .order_by(ST_Distance(Spot.location, origin))
    )
    result = await db.execute(stmt)
    return list(result.scalars().all())
