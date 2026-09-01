"""Points bookkeeping shared between the spots and users routers.

Weekly leaderboard points reset on the ISO week boundary (Monday 00:00
UTC) without a scheduled job: `reconcile_weekly_points` lazily zeroes any
user row whose bucket predates the current week, the same pattern the
spots router uses for claim-timeout and spot-expiry reconciliation.
"""

from datetime import datetime, timedelta, timezone

from sqlalchemy import update
from sqlalchemy.ext.asyncio import AsyncSession

from models import User


def _week_start(now: datetime) -> datetime:
    monday = now - timedelta(days=now.weekday())
    return monday.replace(hour=0, minute=0, second=0, microsecond=0)


async def reconcile_weekly_points(db: AsyncSession, now: datetime | None = None) -> None:
    now = now or datetime.now(timezone.utc)
    week_start = _week_start(now)
    await db.execute(
        update(User)
        .where((User.weekly_points_reset_at.is_(None)) | (User.weekly_points_reset_at < week_start))
        .values(weekly_points=0, weekly_points_reset_at=week_start)
    )
    await db.commit()


def award_points(user: User, delta: int) -> None:
    user.points += delta
    user.weekly_points += delta
