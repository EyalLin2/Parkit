import base64
import uuid
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, Query
from geoalchemy2.functions import ST_Distance, ST_DWithin, ST_MakePoint, ST_SetSRID
from redis.asyncio import Redis
from sqlalchemy import func, select, text, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from auth import get_current_user_id
from database import get_db
from geocoder import reverse_cached
from hotspots import hot_minutes
from levels import level_for
from media import delete_photo, save_photo
from models import FeedbackType, Payment, RemovedReason, Spot, SpotFeedback, SpotStatus, SpotType, User
from points import award_points, reconcile_weekly_points
from redis_client import get_redis
from schemas import FeedbackCreate, SpotCreate, SpotOut

router = APIRouter(prefix="/spots", tags=["spots"])

CLAIM_TTL_SECONDS = 120
NEARBY_LIMIT = 50
RECONCILE_EVERY_SECONDS = 15
SPOT_TTL_MINUTES = 30  # the same for everyone: a stale spot helps no one
REPORT_COOLDOWN_SECONDS = 120
CANCEL_WINDOW_MINUTES = 2
DEDUP_RADIUS_M = 15
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
    expiring_photos = (
        (
            await db.execute(
                select(Spot.photo_url).where(
                    Spot.status == SpotStatus.active,
                    Spot.reported_at + func.make_interval(0, 0, 0, 0, 0, Spot.ttl_minutes) < now,
                    Spot.photo_url.is_not(None),
                )
            )
        )
        .scalars()
        .all()
    )
    for photo_url in expiring_photos:
        delete_photo(photo_url)

    await db.execute(
        update(Spot)
        .where(
            Spot.status == SpotStatus.active,
            Spot.reported_at + func.make_interval(0, 0, 0, 0, 0, Spot.ttl_minutes) < now,
        )
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

    # The server is the source of truth for a spot's address: the client's
    # label is only a fallback for areas our OSM tables don't cover.
    address = (await reverse_cached(db, redis, payload.lat, payload.lng, "he")).street
    address = address or (payload.address or "").strip() or None

    origin = ST_SetSRID(ST_MakePoint(payload.lng, payload.lat), 4326)
    # Serialise reports around the same place (≈100 m cell) for the rest of
    # this transaction, so two simultaneous reports of one spot can't both
    # miss each other and create a duplicate (found by the load test).
    await db.execute(
        text("SELECT pg_advisory_xact_lock(hashtext(:cell))"),
        {"cell": f"report:{round(payload.lat, 3)}:{round(payload.lng, 3)}"},
    )
    existing = (
        await db.execute(
            select(Spot)
            .where(Spot.status == SpotStatus.active)
            .where(ST_DWithin(Spot.location, origin, DEDUP_RADIUS_M))
            .limit(1)
        )
    ).scalar_one_or_none()

    hot = await hot_minutes(db, payload.lat, payload.lng, now)
    if existing is not None:
        existing.reported_at = now
        existing.hot_minutes = hot
        if existing.address is None and address:
            existing.address = address
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
            vehicle_size=payload.vehicle_size,
            address=address,
            ttl_minutes=SPOT_TTL_MINUTES,
            hot_minutes=hot,
        )
        db.add(spot)
        award_points(user, POINTS_REPORT)

    if payload.photo_staging_id:
        await db.flush()  # assigns spot.id (a new spot's PK default runs at INSERT, not construction)
        staged = await redis.get(f"photo_staging:{payload.photo_staging_id}")
        if staged is None:
            raise HTTPException(400, "Photo expired or not found — please re-upload")
        await redis.delete(f"photo_staging:{payload.photo_staging_id}")
        spot.photo_url = save_photo(spot.id, base64.b64decode(staged))

    await redis.set(f"report_cooldown:{user_id}", "1", ex=REPORT_COOLDOWN_SECONDS)
    same_spot_key = f"same_spot_count:{user_id}:{bucket}"
    count = await redis.incr(same_spot_key)
    if count == 1:
        await redis.expire(same_spot_key, SAME_SPOT_WINDOW_SECONDS)
    if count > SAME_SPOT_THRESHOLD:
        await redis.set(f"same_spot_block:{user_id}:{bucket}", "1", ex=SAME_SPOT_BLOCK_SECONDS)

    await db.commit()
    await db.refresh(spot)
    spot.reporter_level = level_for(user.points).key if spot.reporter_id == user_id else "newbie"
    return spot


@router.get("", response_model=list[SpotOut])
async def nearby_spots(
    lat: float = Query(..., ge=-90, le=90),
    lng: float = Query(..., ge=-180, le=180),
    radius_m: int = Query(1000, gt=0, le=5000),
    spot_type: list[SpotType] | None = Query(None),
    payment: Payment | None = Query(None),
    db: AsyncSession = Depends(get_db),
    redis: Redis = Depends(get_redis),
) -> list[Spot]:
    # The map's hot path. Persisting expiries (UPDATEs + photo deletes) runs
    # at most once per RECONCILE_EVERY_SECONDS across all servers instead of
    # on every map move (load-test bug #7); the query below filters expired
    # spots itself, so results are exact in between.
    if await redis.set("reconcile:spots", "1", nx=True, ex=RECONCILE_EVERY_SECONDS):
        await _reconcile_spot_states(db)

    now = datetime.now(timezone.utc)
    origin = ST_SetSRID(ST_MakePoint(lng, lat), 4326)
    stmt = (
        select(Spot, User.points)
        .join(User, User.id == Spot.reporter_id)
        .where(Spot.status == SpotStatus.active)
        .where(Spot.reported_at + func.make_interval(0, 0, 0, 0, 0, Spot.ttl_minutes) >= now)
        .where(ST_DWithin(Spot.location, origin, radius_m))
        .order_by(ST_Distance(Spot.location, origin))
        # The nearest ones are what a driver can use; this also caps each
        # map move's payload (load-test bug #6: ~145 KB before).
        .limit(NEARBY_LIMIT)
    )
    if spot_type:
        stmt = stmt.where(Spot.spot_type.in_(spot_type))
    if payment:
        stmt = stmt.where(Spot.payment == payment)
    spots = []
    for spot, reporter_points in (await db.execute(stmt)).all():
        # Plain attribute, not a column: the reporter's level is derived from
        # their points at read time (badge + legend pin in the app).
        spot.reporter_level = level_for(reporter_points).key
        spots.append(spot)
    return spots


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

    # Row lock: feedback on one spot is handled one at a time, so two
    # simultaneous "already taken" flags can't both close the spot and
    # penalise the reporter twice (found by the load test).
    spot = (await db.execute(select(Spot).where(Spot.id == spot_id).with_for_update())).scalar_one_or_none()
    if spot is None:
        raise HTTPException(404, "Spot not found")
    if spot.reporter_id == user_id:
        raise HTTPException(400, "Cannot leave feedback on your own report")
    still_open = spot.status in (SpotStatus.active, SpotStatus.claimed)

    already_voted = (
        await db.execute(select(SpotFeedback).where(SpotFeedback.spot_id == spot_id, SpotFeedback.user_id == user_id))
    ).scalar_one_or_none()
    if already_voted is not None:
        raise HTTPException(409, "Feedback already submitted for this spot")

    db.add(SpotFeedback(spot_id=spot_id, user_id=user_id, type=payload.type))

    reporter = await db.get(User, spot.reporter_id)
    now = datetime.now(timezone.utc)

    # Points and penalties only on the transition that closes the spot.
    if not still_open:
        pass
    elif payload.type == FeedbackType.confirmed_taken:
        spot.status = SpotStatus.removed
        spot.removed_reason = RemovedReason.taken_confirmed
        spot.removed_at = now
        delete_photo(spot.photo_url)
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
            delete_photo(spot.photo_url)
            award_points(reporter, -POINTS_FALSE_PENALTY)
            reporter.consecutive_bad_reports += 1
            if reporter.consecutive_bad_reports >= BAD_REPORT_BLOCK_THRESHOLD:
                reporter.reporting_blocked_until = now + timedelta(hours=BAD_REPORT_BLOCK_HOURS)

    try:
        await db.commit()
    except IntegrityError:
        # Same driver, two simultaneous votes: the database kept one.
        await db.rollback()
        raise HTTPException(409, "Feedback already submitted for this spot") from None


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
    delete_photo(spot.photo_url)

    bucket = _location_bucket(spot.lat, spot.lng)
    await redis.delete(f"report_cooldown:{user_id}")
    await redis.decr(f"same_spot_count:{user_id}:{bucket}")

    await db.delete(spot)
    await db.commit()
