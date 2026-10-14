"""Hot areas: places where free spots get snapped up fast.

Measured from our own history, never guessed: for past reports near a new
one, at a similar hour of day, how long until someone headed there ("I'm
on my way") or confirmed it was taken. If enough of them went fast, the new
spot is marked hot — a flame on the pin tells drivers to move now.

Computed once, when a spot is reported (or refreshed), and stored on the
spot, so reading the map costs nothing extra.
"""

from datetime import datetime
from zoneinfo import ZoneInfo

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

RADIUS_M = 250
LOOKBACK_DAYS = 28
HOUR_WINDOW = 2  # ± hours of day, Israel time (Friday 20:00 ≠ Tuesday 04:00)
MIN_SAMPLES = 15  # below this we don't know enough — no flame
HOT_MEDIAN_MIN = 5  # half of nearby spots were gone within 5 minutes

_QUERY = text(
    """
    SELECT count(*) AS n,
           percentile_cont(0.5) WITHIN GROUP (
               ORDER BY extract(epoch FROM COALESCE(claimed_at, removed_at) - reported_at)
           ) AS median_s
    FROM spots
    WHERE ST_DWithin(location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radius)
      AND reported_at > CAST(:now AS timestamptz) - make_interval(days => CAST(:days AS int))
      AND reported_at < CAST(:now AS timestamptz)
      AND (claimed_at IS NOT NULL OR removed_reason = 'taken_confirmed')
      AND COALESCE(claimed_at, removed_at) > reported_at
      AND least(
            abs(extract(hour FROM reported_at AT TIME ZONE 'Asia/Jerusalem') - :hour),
            24 - abs(extract(hour FROM reported_at AT TIME ZONE 'Asia/Jerusalem') - :hour)
          ) <= :window
    """
)


async def hot_minutes(db: AsyncSession, lat: float, lng: float, now: datetime) -> int | None:
    """Median minutes until nearby spots were taken, if this is a hot area;
    None when it isn't (or when there isn't enough history to say)."""
    hour = now.astimezone(ZoneInfo("Asia/Jerusalem")).hour
    row = (
        await db.execute(
            _QUERY,
            {
                "lat": lat,
                "lng": lng,
                "radius": RADIUS_M,
                "days": LOOKBACK_DAYS,
                "now": now,
                "hour": hour,
                "window": HOUR_WINDOW,
            },
        )
    ).one()
    if row.n < MIN_SAMPLES or row.median_s is None:
        return None
    minutes = row.median_s / 60
    return max(1, round(minutes)) if minutes <= HOT_MEDIAN_MIN else None
