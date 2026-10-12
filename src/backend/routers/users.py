import uuid

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from auth import get_current_user_id
from database import get_db
from levels import level_for, next_level
from models import RemovedReason, Spot, User
from points import reconcile_weekly_points
from routers.me import preferences_of
from schemas import ActivityItem, LeaderboardEntry, ProfileOut

router = APIRouter(tags=["users"])

BADGE_THRESHOLDS = [10, 50, 200]
ACTIVITY_LIMIT = 50
LEADERBOARD_LIMIT = 50


@router.get("/users/me", response_model=ProfileOut)
async def my_profile(
    user_id: uuid.UUID = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> ProfileOut:
    await reconcile_weekly_points(db)

    user = await db.get(User, user_id)
    if user is None:
        raise HTTPException(404, "Unknown user")

    successful_reports = (
        await db.execute(
            select(func.count())
            .select_from(Spot)
            .where(Spot.reporter_id == user_id, Spot.removed_reason == RemovedReason.taken_confirmed)
        )
    ).scalar_one()

    total_reports = (
        await db.execute(select(func.count()).select_from(Spot).where(Spot.reporter_id == user_id))
    ).scalar_one()

    activity = (
        (
            await db.execute(
                select(Spot).where(Spot.reporter_id == user_id).order_by(Spot.reported_at.desc()).limit(ACTIVITY_LIMIT)
            )
        )
        .scalars()
        .all()
    )

    # Same ordering as /leaderboard: only drivers with points this week are
    # ranked, so 0 points means "not in the league yet", not last place.
    weekly_rank = weekly_city_rank = None
    if user.weekly_points > 0:
        ahead = select(func.count()).where(User.weekly_points > user.weekly_points)
        weekly_rank = (await db.execute(ahead)).scalar_one() + 1
        if user.home_city:
            # The city league: drivers who picked the same preferred city.
            weekly_city_rank = (await db.execute(ahead.where(User.home_city == user.home_city))).scalar_one() + 1

    level = level_for(user.points)
    upcoming = next_level(user.points)
    return ProfileOut(
        user_id=user.id,
        display_name=user.display_name,
        points=user.points,
        weekly_points=user.weekly_points,
        weekly_rank=weekly_rank,
        weekly_city_rank=weekly_city_rank,
        successful_reports=successful_reports,
        total_reports=total_reports,
        badges=[t for t in BADGE_THRESHOLDS if successful_reports >= t],
        level=level.key,
        level_min_points=level.min_points,
        next_level=upcoming.key if upcoming else None,
        next_level_points=upcoming.min_points if upcoming else None,
        preferences=preferences_of(user),
        activity=[
            ActivityItem(
                spot_id=s.id,
                spot_type=s.spot_type,
                payment=s.payment,
                address=s.address,
                reported_at=s.reported_at,
                status=s.status,
                removed_reason=s.removed_reason,
            )
            for s in activity
        ],
    )


@router.get("/leaderboard", response_model=list[LeaderboardEntry])
async def leaderboard(
    limit: int = Query(LEADERBOARD_LIMIT, gt=0, le=200),
    city: str | None = Query(None, max_length=120, description="Only drivers whose preferred city is this"),
    _user_id: uuid.UUID = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> list[LeaderboardEntry]:
    await reconcile_weekly_points(db)

    stmt = select(User).where(User.weekly_points > 0)
    if city:
        stmt = stmt.where(User.home_city == city)
    rows = (await db.execute(stmt.order_by(User.weekly_points.desc()).limit(limit))).scalars().all()

    return [
        LeaderboardEntry(
            rank=i,
            user_id=u.id,
            display_name=u.display_name,
            weekly_points=u.weekly_points,
            level=level_for(u.points).key,
        )
        for i, u in enumerate(rows, start=1)
    ]
