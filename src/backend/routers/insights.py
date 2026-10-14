"""Forecasts from our own report history — never guessed.

- /insights/area: at this hour, how often does a spot free up around here?
- /insights/best-hour: in this area, which hour of the day has the most
  free spots reported?

Both stay silent (null) until there's enough history to mean something,
so a new area never shows a made-up number.
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
AREA_HOUR_WINDOW = 1  # this hour ± 1
AREA_MIN_REPORTS = 12
AREA_MIN_DAYS = 4
BEST_RADIUS_M = 800
BEST_MIN_REPORTS = 30
BEST_MIN_IN_HOUR = 6


class AreaForecast(BaseModel):
    every_minutes: int  # a spot frees up about this often, at this hour
    reports: int
    days: int


class BestHour(BaseModel):
    hour: int  # 0-23, Israel time: the window hour:00–hour+1:00
    reports: int


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
    row = (
        await db.execute(
            _AREA,
            {
                "lat": lat,
                "lng": lng,
                "radius": AREA_RADIUS_M,
                "lookback": LOOKBACK_DAYS,
                "hour": _hour_now(),
                "window": AREA_HOUR_WINDOW,
            },
        )
    ).one()
    if row.n < AREA_MIN_REPORTS or row.days < AREA_MIN_DAYS:
        return None
    # Reports per minute across the days we saw this window (3 hours a day).
    minutes_observed = row.days * (2 * AREA_HOUR_WINDOW + 1) * 60
    every = max(1, round(minutes_observed / row.n))
    return AreaForecast(every_minutes=every, reports=row.n, days=row.days)


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
    rows = (await db.execute(_BEST, {"lat": lat, "lng": lng, "radius": BEST_RADIUS_M, "lookback": LOOKBACK_DAYS})).all()
    if sum(r.n for r in rows) < BEST_MIN_REPORTS or not rows or rows[0].n < BEST_MIN_IN_HOUR:
        return None
    return BestHour(hour=rows[0].hour, reports=rows[0].n)
