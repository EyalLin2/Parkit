"""Local sponsored cards ("ממומן").

One card at most, from a business near where the driver is looking, chosen
by location only — nothing about the person. Businesses are added by hand
on /admin/ads (direct sales); each card counts views and taps so the
business can be shown what it got.
"""

import hmac
import html
from datetime import datetime, timezone
from typing import Literal

from fastapi import APIRouter, Depends, Form, HTTPException, Query
from fastapi.responses import HTMLResponse, RedirectResponse
from pydantic import BaseModel
from sqlalchemy import select, text, update
from sqlalchemy.ext.asyncio import AsyncSession

from config import settings
from database import get_db
from models import Ad

router = APIRouter(tags=["ads"])

CATEGORIES = ("car_wash", "garage", "cafe", "parking", "other")


class AdOut(BaseModel):
    id: int
    advertiser: str
    title_he: str
    title_en: str
    body_he: str | None
    body_en: str | None
    category: str
    lat: float
    lng: float
    distance_m: float
    link_url: str | None


@router.get("/ads/nearby", response_model=AdOut | None)
async def nearby_ad(
    lat: float = Query(ge=-90, le=90),
    lng: float = Query(ge=-180, le=180),
    placement: Literal["card", "open"] = "card",
    db: AsyncSession = Depends(get_db),
) -> AdOut | None:
    """The closest active card whose area covers this point, or null."""
    row = (
        await db.execute(
            text(
                "SELECT id, ST_Y(geom::geometry) AS lat, ST_X(geom::geometry) AS lng, "
                "ST_Distance(geom, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) AS distance_m "
                "FROM ads WHERE active AND placement = :placement AND (ends_at IS NULL OR ends_at > now()) "
                "AND ST_DWithin(geom, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, radius_m) "
                "ORDER BY distance_m LIMIT 1"
            ),
            {"lat": lat, "lng": lng, "placement": placement},
        )
    ).first()
    if row is None:
        return None
    ad = await db.get(Ad, row.id)
    ad_lat, ad_lng, dist = row.lat, row.lng, row.distance_m
    return AdOut(
        id=ad.id,
        advertiser=ad.advertiser,
        title_he=ad.title_he,
        title_en=ad.title_en,
        body_he=ad.body_he,
        body_en=ad.body_en,
        category=ad.category,
        lat=ad_lat,
        lng=ad_lng,
        distance_m=dist,
        link_url=ad.link_url,
    )


@router.post("/ads/{ad_id}/view", status_code=204)
async def ad_view(ad_id: int, db: AsyncSession = Depends(get_db)) -> None:
    """Counted when the card is actually on screen — not every time the map
    moves — so the numbers shown to a business are honest."""
    await db.execute(update(Ad).where(Ad.id == ad_id).values(impressions=Ad.impressions + 1))
    await db.commit()


@router.post("/ads/{ad_id}/click", status_code=204)
async def ad_click(ad_id: int, db: AsyncSession = Depends(get_db)) -> None:
    await db.execute(update(Ad).where(Ad.id == ad_id).values(clicks=Ad.clicks + 1))
    await db.commit()


# --- Admin --------------------------------------------------------------------


def _check(token: str) -> None:
    if not settings.admin_token or not hmac.compare_digest(token, settings.admin_token):
        raise HTTPException(404, "Not found")


@router.get("/admin/ads", response_class=HTMLResponse, include_in_schema=False)
async def admin_ads(token: str = Query(""), db: AsyncSession = Depends(get_db)) -> HTMLResponse:
    _check(token)
    ads = (await db.execute(select(Ad).order_by(Ad.id.desc()))).scalars().all()
    t = html.escape(token)
    rows = "".join(
        f"<tr><td>{a.id}</td><td>{html.escape(a.advertiser)}</td><td>{html.escape(a.title_he)}</td>"
        f"<td>{html.escape(a.category)} / {html.escape(a.placement)}</td><td>{a.radius_m} m</td>"
        f"<td>{a.impressions}</td><td>{a.clicks}</td>"
        f"<td>{(a.clicks / a.impressions * 100 if a.impressions else 0):.1f}%</td>"
        f"<td>{a.ends_at.strftime('%d/%m/%Y') if a.ends_at else '—'}</td><td>{'on' if a.active else 'off'}</td>"
        f"<td><form method=post action='/admin/ads/{a.id}/toggle?token={t}'>"
        f"<button>{'Turn off' if a.active else 'Turn on'}</button></form></td></tr>"
        for a in ads
    )
    options = "".join(f"<option>{c}</option>" for c in CATEGORIES)
    page = (
        "<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width'>"
        "<title>ParkIt — ads</title><style>body{font:14px system-ui;margin:16px}"
        "table{border-collapse:collapse;width:100%}td,th{border-top:1px solid #ddd;padding:6px;text-align:left}"
        "input,select{margin:2px 0;width:100%;max-width:420px}</style>"
        "<h1>Sponsored cards</h1><table><tr><th>#</th><th>Business</th><th>Title</th><th>Kind</th>"
        "<th>Radius</th><th>Views</th><th>Taps</th><th>Tap rate</th><th>Ends</th><th>State</th><th></th></tr>"
        f"{rows}</table>"
        f"<h2>Add a business</h2><form method=post action='/admin/ads?token={t}'>"
        "<input name=advertiser placeholder='Business name' required><br>"
        "<input name=title_he placeholder='כותרת (עד 80 תווים)' required><br>"
        "<input name=title_en placeholder='Title (English)' required><br>"
        "<input name=body_he placeholder='שורה קצרה, למשל: 10% הנחה למשתמשי ParkIt'><br>"
        "<input name=body_en placeholder='Short line (English)'><br>"
        f"<select name=category>{options}</select><br>"
        "<select name=placement><option value=card>Card in the nearby list</option>"
        "<option value=open>Full screen when the app opens</option></select><br>"
        "<input name=lat placeholder='Latitude, e.g. 32.0809' required><br>"
        "<input name=lng placeholder='Longitude, e.g. 34.7806' required><br>"
        "<input name=radius_m type=number value=1500 min=100 max=10000> metres around it<br>"
        "<input name=link_url placeholder='Link (optional), https://…'><br>"
        "<input name=ends_at type=date> last day (optional)<br><button>Add</button></form>"
    )
    return HTMLResponse(page)


@router.post("/admin/ads", include_in_schema=False)
async def admin_add_ad(
    token: str = Query(""),
    advertiser: str = Form(..., max_length=120),
    title_he: str = Form(..., max_length=80),
    title_en: str = Form(..., max_length=80),
    body_he: str = Form("", max_length=160),
    body_en: str = Form("", max_length=160),
    category: str = Form("other"),
    placement: str = Form("card"),
    lat: float = Form(..., ge=-90, le=90),
    lng: float = Form(..., ge=-180, le=180),
    radius_m: int = Form(1500, ge=100, le=10000),
    link_url: str = Form("", max_length=300),
    ends_at: str = Form(""),
    db: AsyncSession = Depends(get_db),
) -> RedirectResponse:
    _check(token)
    if category not in CATEGORIES:
        category = "other"
    if link_url and not link_url.startswith("https://"):
        raise HTTPException(422, "The link must start with https://")
    try:
        last_day = datetime.fromisoformat(ends_at).replace(hour=23, minute=59, tzinfo=timezone.utc) if ends_at else None
    except ValueError:
        raise HTTPException(422, "The end date should look like 2026-12-31") from None
    db.add(
        Ad(
            advertiser=advertiser.strip(),
            title_he=title_he.strip(),
            title_en=title_en.strip(),
            body_he=body_he.strip() or None,
            body_en=body_en.strip() or None,
            category=category,
            placement="open" if placement == "open" else "card",
            geom=f"SRID=4326;POINT({lng} {lat})",
            radius_m=radius_m,
            link_url=link_url.strip() or None,
            ends_at=last_day,
        )
    )
    await db.commit()
    return RedirectResponse(f"/admin/ads?token={token}", status_code=303)


@router.post("/admin/ads/{ad_id}/toggle", include_in_schema=False)
async def admin_toggle_ad(ad_id: int, token: str = Query(""), db: AsyncSession = Depends(get_db)) -> RedirectResponse:
    _check(token)
    ad = await db.get(Ad, ad_id)
    if ad is None:
        raise HTTPException(404, "Not found")
    ad.active = not ad.active
    await db.commit()
    return RedirectResponse(f"/admin/ads?token={token}", status_code=303)
