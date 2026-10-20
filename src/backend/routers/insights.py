"""Forecasts from our own report history — never guessed.

- /insights/area: at this hour, how often does a spot free up around here?
- /insights/best-hour: in this area, which hour of the day has the most
  free spots reported?

Both widen the circle when the street has too little history (street →
neighbourhood → city) and say which one they used; they stay silent (null)
only when even the city has too little, so nobody sees a made-up number.
"""

from datetime import datetime, timezone
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from database import get_db

router = APIRouter(prefix="/insights", tags=["insights"])

ISRAEL = ZoneInfo("Asia/Jerusalem")
LOOKBACK_DAYS = 28
AREA_RADIUS_M = 400
# Street, neighbourhood, city — the first circle with enough history wins.
AREA_SCOPES = ((AREA_RADIUS_M, "street"), (1500, "area"), (6000, "city"))
AREA_HOUR_WINDOW = 1  # this hour ± 1
AREA_MIN_REPORTS = 12
AREA_MIN_DAYS = 4
BEST_RADIUS_M = 800
BEST_SCOPES = ((BEST_RADIUS_M, "street"), (2500, "area"), (8000, "city"))
BEST_MIN_REPORTS = 30
BEST_MIN_IN_HOUR = 6


class AreaForecast(BaseModel):
    every_minutes: int  # a spot frees up about this often, at this hour
    reports: int
    days: int
    scope: str = "street"  # street | area | city: how wide we had to look


class BestHour(BaseModel):
    hour: int  # 0-23, Israel time: the window hour:00–hour+1:00
    reports: int
    scope: str = "street"


def _hour_now() -> int:
    return datetime.now(timezone.utc).astimezone(ISRAEL).hour


_AREA = text(
    """
    SELECT count(*) AS n,
           count(DISTINCT (reported_at AT TIME ZONE 'Asia/Jerusalem')::date) AS days
    FROM spots
    WHERE ST_DWithin(location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radius)
      AND reported_at > now() - make_interval(days => :lookback)
      AND least(
            abs(extract(hour FROM reported_at AT TIME ZONE 'Asia/Jerusalem') - :hour),
            24 - abs(extract(hour FROM reported_at AT TIME ZONE 'Asia/Jerusalem') - :hour)
          ) <= :window
    """
)


@router.get("/area", response_model=AreaForecast | None)
async def area_forecast(
    lat: float = Query(ge=-90, le=90),
    lng: float = Query(ge=-180, le=180),
    db: AsyncSession = Depends(get_db),
) -> AreaForecast | None:
    hour = _hour_now()
    for radius, scope in AREA_SCOPES:
        row = (
            await db.execute(
                _AREA,
                {
                    "lat": lat,
                    "lng": lng,
                    "radius": radius,
                    "lookback": LOOKBACK_DAYS,
                    "hour": hour,
                    "window": AREA_HOUR_WINDOW,
                },
            )
        ).one()
        if row.n < AREA_MIN_REPORTS or row.days < AREA_MIN_DAYS:
            continue
        # Reports per minute across the days we saw this window (3 hours a
        # day), scaled down to a street-sized circle so a wide look doesn't
        # promise a spot every minute.
        street_share = (AREA_RADIUS_M / radius) ** 2
        minutes_observed = row.days * (2 * AREA_HOUR_WINDOW + 1) * 60
        every = max(1, round(minutes_observed / (row.n * street_share)))
        return AreaForecast(every_minutes=every, reports=row.n, days=row.days, scope=scope)
    return None


_BEST = text(
    """
    SELECT extract(hour FROM reported_at AT TIME ZONE 'Asia/Jerusalem')::int AS hour, count(*) AS n
    FROM spots
    WHERE ST_DWithin(location, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radius)
      AND reported_at > now() - make_interval(days => :lookback)
    GROUP BY 1
    ORDER BY n DESC, hour
    """
)


@router.get("/best-hour", response_model=BestHour | None)
async def best_hour(
    lat: float = Query(ge=-90, le=90),
    lng: float = Query(ge=-180, le=180),
    db: AsyncSession = Depends(get_db),
) -> BestHour | None:
    now = _hour_now()
    for radius, scope in BEST_SCOPES:
        rows = (await db.execute(_BEST, {"lat": lat, "lng": lng, "radius": radius, "lookback": LOOKBACK_DAYS})).all()
        if sum(r.n for r in rows) < BEST_MIN_REPORTS:
            continue
        # It's a tip for today: an hour that already passed is no help.
        ahead = [r for r in rows if r.hour >= now]
        if not ahead or ahead[0].n < BEST_MIN_IN_HOUR:
            return None
        return BestHour(hour=ahead[0].hour, reports=ahead[0].n, scope=scope)
    return None
