"""Forecasts from report history: silent without enough data, then a
plain number."""

from datetime import datetime, timedelta, timezone

from sqlalchemy import text

from database import engine
from routers import insights

HERE = {"lat": 32.0809, "lng": 34.7806}


async def _history(user_id, per_day, days, hour_offset_h=0, where=HERE):
    """per_day reports on each of `days` past days, at this time of day."""
    now = datetime.now(timezone.utc)
    async with engine.begin() as conn:
        for d in range(1, days + 1):
            for i in range(per_day):
                at = now - timedelta(days=d, hours=hour_offset_h) + timedelta(minutes=i)
                await conn.execute(
                    text(
                        "INSERT INTO spots (id, reporter_id, lat, lng, location, spot_type, payment, status, "
                        "reported_at, ttl_minutes) VALUES (gen_random_uuid(), :u, :lat, :lng, "
                        "ST_GeogFromText(:wkt), 'street', 'free', 'expired', :at, 30)"
                    ),
                    {
                        "u": user_id,
                        "lat": where["lat"],
                        "lng": where["lng"],
                        "wkt": f"SRID=4326;POINT({where['lng']} {where['lat']})",
                        "at": at,
                    },
                )


async def test_silent_without_enough_history(client, make_user):
    user_id, _ = await make_user("reporter")
    await _history(user_id, per_day=2, days=2)
    assert (await client.get("/insights/area", params=HERE)).json() is None
    assert (await client.get("/insights/best-hour", params=HERE)).json() is None


async def test_area_forecast(client, make_user):
    user_id, _ = await make_user("reporter")
    await _history(user_id, per_day=6, days=5)  # 30 reports over 5 days, 3-hour window each
    body = (await client.get("/insights/area", params=HERE)).json()
    assert body["reports"] == 30 and body["days"] == 5
    assert body["every_minutes"] == round(5 * 3 * 60 / 30)  # = 30


async def test_best_hour(client, make_user):
    user_id, _ = await make_user("reporter")
    await _history(user_id, per_day=7, days=5)
    body = (await client.get("/insights/best-hour", params=HERE)).json()
    assert body["hour"] == insights._hour_now() and body["reports"] >= 30


async def test_area_forecast_widens_to_the_neighbourhood(client, make_user):
    user_id, _ = await make_user("reporter")
    # ~1 km north: outside the street circle, inside the neighbourhood one.
    await _history(user_id, per_day=6, days=5, where={"lat": HERE["lat"] + 0.009, "lng": HERE["lng"]})
    body = (await client.get("/insights/area", params=HERE)).json()
    assert body["scope"] == "area"
    # Scaled to a street-sized circle: rarer than the raw count suggests.
    assert body["every_minutes"] > round(5 * 3 * 60 / 30)


async def test_best_hour_skips_hours_already_past(client, make_user, monkeypatch):
    user_id, _ = await make_user("reporter")
    await _history(user_id, per_day=7, days=5, hour_offset_h=3)
    monkeypatch.setattr(insights, "_hour_now", lambda: 23)
    if (datetime.now(timezone.utc) - timedelta(hours=3)).astimezone(insights.ISRAEL).hour < 23:
        assert (await client.get("/insights/best-hour", params=HERE)).json() is None
