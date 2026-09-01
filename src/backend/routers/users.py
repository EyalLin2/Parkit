import uuid

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from auth import get_current_user_id
from database import get_db
from models import RemovedReason, Spot, User
from points import reconcile_weekly_points
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

    activity = (
        (
            await db.execute(
                select(Spot).where(Spot.reporter_id == user_id).order_by(Spot.reported_at.desc()).limit(ACTIVITY_LIMIT)
            )
        )
        .scalars()
        .all()
    )

    return ProfileOut(
        user_id=user.id,
        display_name=user.display_name,
        points=user.points,
        weekly_points=user.weekly_points,
        successful_reports=successful_reports,
        badges=[t for t in BADGE_THRESHOLDS if successful_reports >= t],
        activity=[
            ActivityItem(
                spot_id=s.id,
                spot_type=s.spot_type,
                payment=s.payment,
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
    _user_id: uuid.UUID = Depends(get_current_user_id),
    db: AsyncSession = Depends(get_db),
) -> list[LeaderboardEntry]:
    await reconcile_weekly_points(db)

    rows = (
        (await db.execute(select(User).where(User.weekly_points > 0).order_by(User.weekly_points.desc()).limit(limit)))
        .scalars()
        .all()
    )

    return [
        LeaderboardEntry(rank=i, user_id=u.id, display_name=u.display_name, weekly_points=u.weekly_points)
        for i, u in enumerate(rows, start=1)
    ]
