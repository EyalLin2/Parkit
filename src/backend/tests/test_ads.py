"""Local sponsored cards: nearest active card covering the point, counts
views and taps, respects its area and end date."""

from datetime import datetime, timedelta, timezone

from sqlalchemy import text

from database import engine

DIZENGOFF = {"lat": 32.0779, "lng": 34.7740}


async def _ad(name, lat, lng, radius=1500, active=True, ends_at=None, placement="card"):
    async with engine.begin() as conn:
        await conn.execute(
            text(
                "INSERT INTO ads (advertiser, title_he, title_en, category, geom, radius_m, active, ends_at, "
                "impressions, clicks, created_at, placement) "
                "VALUES (:n, :n, :n, 'car_wash', ST_GeogFromText(:wkt), :r, :a, :e, 0, 0, now(), :p)"
            ),
            {"n": name, "wkt": f"SRID=4326;POINT({lng} {lat})", "r": radius, "a": active, "e": ends_at, "p": placement},
        )


async def test_nearest_active_card_in_range(client):
    await _ad("Near wash", 32.0785, 34.7745)
    await _ad("Far wash", 32.0900, 34.7900)
    await _ad("Off wash", 32.0780, 34.7741, active=False)
    await _ad("Expired wash", 32.0779, 34.7740, ends_at=datetime.now(timezone.utc) - timedelta(days=1))
    ad = (await client.get("/ads/nearby", params=DIZENGOFF)).json()
    assert ad["advertiser"] == "Near wash" and ad["distance_m"] < 150


async def test_no_card_outside_its_area(client):
    await _ad("Small area", 32.0900, 34.7900, radius=200)
    assert (await client.get("/ads/nearby", params=DIZENGOFF)).json() is None


async def test_views_and_taps_are_counted(client):
    await _ad("Counted", 32.0785, 34.7745)
    ad = (await client.get("/ads/nearby", params=DIZENGOFF)).json()
    await client.get("/ads/nearby", params=DIZENGOFF)  # fetching alone isn't a view
    assert (await client.post(f"/ads/{ad['id']}/view")).status_code == 204
    assert (await client.post(f"/ads/{ad['id']}/view")).status_code == 204
    assert (await client.post(f"/ads/{ad['id']}/click")).status_code == 204
    async with engine.begin() as conn:
        views, taps = (
            await conn.execute(text("SELECT impressions, clicks FROM ads WHERE id = :i"), {"i": ad["id"]})
        ).one()
    assert (views, taps) == (2, 1)


async def test_open_ads_are_separate_from_list_cards(client):
    await _ad("List card", 32.0785, 34.7745)
    await _ad("Splash", 32.0786, 34.7746, placement="open")
    card = (await client.get("/ads/nearby", params=DIZENGOFF)).json()
    splash = (await client.get("/ads/nearby", params={**DIZENGOFF, "placement": "open"})).json()
    assert card["advertiser"] == "List card" and splash["advertiser"] == "Splash"
