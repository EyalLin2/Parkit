import uuid
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, Query
from geoalchemy2.functions import ST_Distance, ST_DWithin, ST_MakePoint, ST_SetSRID
from redis.asyncio import Redis
from sqlalchemy import func, select, update
from sqlalchemy.ext.asyncio import AsyncSession

from auth import get_current_user_id
from database import get_db
from models import FeedbackType, Payment, RemovedReason, Spot, SpotFeedback, SpotStatus, SpotType, User
from points import award_points, reconcile_weekly_points
from redis_client import get_redis
from schemas import FeedbackCreate, SpotCreate, SpotOut

router = APIRouter(prefix="/spots", tags=["spots"])

CLAIM_TTL_SECONDS = 120
REPORT_COOLDOWN_SECONDS = 120
CANCEL_WINDOW_MINUTES = 2
DEDUP_RADIUS_M = 15
EXPIRY_MINUTES = 30
SAME_SPOT_WINDOW_SECONDS = 600
SAME_SPOT_THRESHOLD = 2
SAME_SPOT_BLOCK_SECONDS = 60
CONFIRM_BONUS_WINDOW_MINUTES = 5
FLAG_REMOVAL_THRESHOLD = 2
BAD_REPORT_BLOCK_THRESHOLD = 3
BAD_REPORT_BLOCK_HOURS = 1
POINTS_REPORT = 10
POINTS_CONFIRM_BONUS = 5
POINTS_REFRESH_BONUS = 3
POINTS_FALSE_PENALTY = 5


def _location_bucket(lat: float, lng: float) -> str:
    return f"{round(lat, 4)}:{round(lng, 4)}"


async def _reconcile_spot_states(db: AsyncSession) -> None:
    now = datetime.now(timezone.utc)
    await db.execute(
        update(Spot)
        .where(Spot.status == SpotStatus.claimed, Spot.claimed_at < now - timedelta(seconds=CLAIM_TTL_SECONDS))
        .values(status=SpotStatus.active, claimed_by=None, claimed_at=None)
    )
    await db.execute(
        update(Spot)
        .where(Spot.status == SpotStatus.active, Spot.reported_at < now - timedelta(minutes=EXPIRY_MINUTES))
        .values(status=SpotStatus.expired, removed_reason=RemovedReason.expired, removed_at=now)
    )
    await db.commit()


@router.post("", response_model=SpotOut, status_code=201)
async def report_spot(
    payload: SpotCreate,
    user_id: uuid.UUID = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
    redis: Redis = Depends(get_redis),
) -> Spot:
    await _reconcile_spot_states(db)
    await reconcile_weekly_points(db)

    user = await db.get(User, user_id)
    if user is None:
        raise HTTPException(404, "Unknown user")

    now = datetime.now(timezone.utc)
    if user.reporting_blocked_until and user.reporting_blocked_until > now:
        raise HTTPException(403, "Reporting temporarily blocked")

    if await redis.exists(f"report_cooldown:{user_id}"):
        raise HTTPException(429, "Please wait before reporting again")

    bucket = _location_bucket(payload.lat, payload.lng)
    if await redis.exists(f"same_spot_block:{user_id}:{bucket}"):
        raise HTTPException(429, "Too many reports at this location, try again shortly")

    origin = ST_SetSRID(ST_MakePoint(payload.lng, payload.lat), 4326)
    existing = (
        await db.execute(
            select(Spot)
            .where(Spot.status == SpotStatus.active)
            .where(ST_DWithin(Spot.location, origin, DEDUP_RADIUS_M))
            .limit(1)
        )
    ).scalar_one_or_none()

    if existing is not None:
        existing.reported_at = now
        if existing.reporter_id != user_id:
            award_points(user, POINTS_REFRESH_BONUS)
        spot = existing
    else:
        spot = Spot(
            reporter_id=user_id,
            lat=payload.lat,
            lng=payload.lng,
            location=origin,
            spot_type=payload.spot_type,
            payment=payload.payment,
            photo_url=payload.photo_url,
        )
        db.add(spot)
        award_points(user, POINTS_REPORT)

    await redis.set(f"report_cooldown:{user_id}", "1", ex=REPORT_COOLDOWN_SECONDS)
    same_spot_key = f"same_spot_count:{user_id}:{bucket}"
    count = await redis.incr(same_spot_key)
    if count == 1:
        await redis.expire(same_spot_key, SAME_SPOT_WINDOW_SECONDS)
    if count > SAME_SPOT_THRESHOLD:
        await redis.set(f"same_spot_block:{user_id}:{bucket}", "1", ex=SAME_SPOT_BLOCK_SECONDS)

    await db.commit()
    await db.refresh(spot)
    return spot


@router.get("", response_model=list[SpotOut])
async def nearby_spots(
    lat: float = Query(..., ge=-90, le=90),
    lng: float = Query(..., ge=-180, le=180),
    radius_m: int = Query(1000, gt=0, le=5000),
    spot_type: list[SpotType] | None = Query(None),
    payment: Payment | None = Query(None),
    db: AsyncSession = Depends(get_db),
) -> list[Spot]:
    await _reconcile_spot_states(db)

    origin = ST_SetSRID(ST_MakePoint(lng, lat), 4326)
    stmt = (
        select(Spot)
        .where(Spot.status == SpotStatus.active)
        .where(ST_DWithin(Spot.location, origin, radius_m))
        .order_by(ST_Distance(Spot.location, origin))
    )
    if spot_type:
        stmt = stmt.where(Spot.spot_type.in_(spot_type))
    if payment:
        stmt = stmt.where(Spot.payment == payment)
    result = await db.execute(stmt)
    return list(result.scalars().all())


@router.post("/{spot_id}/claim", response_model=SpotOut)
async def claim_spot(
    spot_id: uuid.UUID,
    user_id: uuid.UUID = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
    redis: Redis = Depends(get_redis),
) -> Spot:
    await _reconcile_spot_states(db)

    spot = await db.get(Spot, spot_id)
    if spot is None or spot.status != SpotStatus.active:
        raise HTTPException(404, "Spot not available")

    acquired = await redis.set(f"claim:{spot_id}", str(user_id), nx=True, ex=CLAIM_TTL_SECONDS)
    if not acquired:
        raise HTTPException(409, "Spot was just claimed by someone else")

    spot.status = SpotStatus.claimed
    spot.claimed_by = user_id
    spot.claimed_at = datetime.now(timezone.utc)
    await db.commit()
    await db.refresh(spot)
    return spot


@router.post("/{spot_id}/feedback", status_code=204)
async def submit_feedback(
    spot_id: uuid.UUID,
    payload: FeedbackCreate,
    user_id: uuid.UUID = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> None:
    await reconcile_weekly_points(db)

    spot = await db.get(Spot, spot_id)
    if spot is None:
        raise HTTPException(404, "Spot not found")
    if spot.reporter_id == user_id:
        raise HTTPException(400, "Cannot leave feedback on your own report")

    already_voted = (
        await db.execute(select(SpotFeedback).where(SpotFeedback.spot_id == spot_id, SpotFeedback.user_id == user_id))
    ).scalar_one_or_none()
    if already_voted is not None:
        raise HTTPException(409, "Feedback already submitted for this spot")

    db.add(SpotFeedback(spot_id=spot_id, user_id=user_id, type=payload.type))

    reporter = await db.get(User, spot.reporter_id)
    now = datetime.now(timezone.utc)

    if payload.type == FeedbackType.confirmed_taken:
        spot.status = SpotStatus.removed
        spot.removed_reason = RemovedReason.taken_confirmed
        spot.removed_at = now
        reporter.consecutive_bad_reports = 0
        if now - spot.reported_at <= timedelta(minutes=CONFIRM_BONUS_WINDOW_MINUTES):
            award_points(reporter, POINTS_CONFIRM_BONUS)
    else:
        flag_count = (
            await db.execute(
                select(func.count(func.distinct(SpotFeedback.user_id))).where(
                    SpotFeedback.spot_id == spot_id, SpotFeedback.type == FeedbackType.flagged_false
                )
            )
        ).scalar_one()
        if flag_count >= FLAG_REMOVAL_THRESHOLD:
            spot.status = SpotStatus.removed
            spot.removed_reason = RemovedReason.flagged_false
            spot.removed_at = now
            award_points(reporter, -POINTS_FALSE_PENALTY)
            reporter.consecutive_bad_reports += 1
            if reporter.consecutive_bad_reports >= BAD_REPORT_BLOCK_THRESHOLD:
                reporter.reporting_blocked_until = now + timedelta(hours=BAD_REPORT_BLOCK_HOURS)

    await db.commit()


@router.delete("/{spot_id}", status_code=204)
async def cancel_report(
    spot_id: uuid.UUID,
    user_id: uuid.UUID = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
    redis: Redis = Depends(get_redis),
) -> None:
    await reconcile_weekly_points(db)

    spot = await db.get(Spot, spot_id)
    if spot is None or spot.reporter_id != user_id:
        raise HTTPException(404, "Spot not found")
    if spot.status != SpotStatus.active:
        raise HTTPException(409, "Spot can no longer be canceled")

    now = datetime.now(timezone.utc)
    if now - spot.reported_at > timedelta(minutes=CANCEL_WINDOW_MINUTES):
        raise HTTPException(409, "Cancel window has passed")

    user = await db.get(User, user_id)
    award_points(user, -POINTS_REPORT)

    bucket = _location_bucket(spot.lat, spot.lng)
    await redis.delete(f"report_cooldown:{user_id}")
    await redis.decr(f"same_spot_count:{user_id}:{bucket}")

    await db.delete(spot)
    await db.commit()
