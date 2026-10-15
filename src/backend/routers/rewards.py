"""The rewards shop: drivers spend points they earned.

Levels follow lifetime points (User.points); spending only moves
points_spent, so buying never costs anyone their level. Partner rewards are
added by the admin on /admin/rewards and redeemed for a one-time code the
driver shows at the business.
"""

import hmac
import html
import secrets
import uuid
from datetime import datetime

from fastapi import APIRouter, Depends, Form, HTTPException, Query
from fastapi.responses import HTMLResponse, RedirectResponse
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from auth import get_current_user_id
from config import settings
from database import get_db
from models import Redemption, Reward, User

router = APIRouter(tags=["rewards"])

# No 0/O or 1/I: codes are read aloud at a counter.
_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"


class RewardOut(BaseModel):
    id: int
    kind: str
    key: str | None
    title_he: str
    title_en: str
    partner: str | None
    description_he: str | None
    description_en: str | None
    cost: int
    owned: bool = False  # cosmetic already unlocked


class RedemptionOut(BaseModel):
    reward_id: int
    title_he: str
    title_en: str
    partner: str | None
    code: str
    created_at: datetime


class ShopOut(BaseModel):
    balance: int
    rewards: list[RewardOut]
    redemptions: list[RedemptionOut]


def balance_of(user: User) -> int:
    return max(0, user.points - user.points_spent)


async def _shop(db: AsyncSession, user: User) -> ShopOut:
    rewards = (
        await db.execute(select(Reward).where(Reward.active).order_by(Reward.kind.desc(), Reward.cost))
    ).scalars()
    mine = (
        await db.execute(
            select(Redemption, Reward)
            .join(Reward, Reward.id == Redemption.reward_id)
            .where(Redemption.user_id == user.id)
            .order_by(Redemption.id.desc())
        )
    ).all()
    owned = {r.id for _, r in mine if r.kind == "cosmetic"}
    return ShopOut(
        balance=balance_of(user),
        rewards=[
            RewardOut(**{c: getattr(r, c) for c in RewardOut.model_fields if c != "owned"}, owned=r.id in owned)
            for r in rewards
        ],
        redemptions=[
            RedemptionOut(
                reward_id=r.id,
                title_he=r.title_he,
                title_en=r.title_en,
                partner=r.partner,
                code=d.code,
                created_at=d.created_at,
            )
            for d, r in mine
            if r.kind == "partner"
        ],
    )


@router.get("/rewards", response_model=ShopOut)
async def shop(user_id: uuid.UUID = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)) -> ShopOut:
    user = await db.get(User, user_id)
    if user is None:
        raise HTTPException(404, "Unknown user")
    return await _shop(db, user)


@router.post("/rewards/{reward_id}/redeem", response_model=ShopOut)
async def redeem(
    reward_id: int, user_id: uuid.UUID = Depends(get_current_user_id), db: AsyncSession = Depends(get_db)
) -> ShopOut:
    # Row lock: two taps at once can't both spend the same points.
    user = (await db.execute(select(User).where(User.id == user_id).with_for_update())).scalar_one_or_none()
    if user is None:
        raise HTTPException(404, "Unknown user")
    reward = await db.get(Reward, reward_id)
    if reward is None or not reward.active:
        raise HTTPException(404, "This reward is no longer available")
    if reward.kind == "cosmetic":
        already = (
            await db.execute(
                select(Redemption.id).where(Redemption.user_id == user_id, Redemption.reward_id == reward_id)
            )
        ).first()
        if already is not None:
            raise HTTPException(409, "You already have this")
    if balance_of(user) < reward.cost:
        raise HTTPException(409, "Not enough points yet")
    user.points_spent += reward.cost
    code = "".join(secrets.choice(_CODE_ALPHABET) for _ in range(8))
    db.add(Redemption(user_id=user_id, reward_id=reward_id, code=code, cost=reward.cost))
    await db.commit()
    await db.refresh(user)
    return await _shop(db, user)


# --- Admin: add / switch off partner rewards ---------------------------------


def _check(token: str) -> None:
    if not settings.admin_token or not hmac.compare_digest(token, settings.admin_token):
        raise HTTPException(404, "Not found")


@router.get("/admin/rewards", response_class=HTMLResponse, include_in_schema=False)
async def admin_rewards(token: str = Query(""), db: AsyncSession = Depends(get_db)) -> HTMLResponse:
    _check(token)
    rows = (await db.execute(select(Reward).order_by(Reward.kind, Reward.id))).scalars().all()
    recent = (
        await db.execute(
            select(Redemption, Reward.title_he, User.display_name)
            .join(Reward, Reward.id == Redemption.reward_id)
            .join(User, User.id == Redemption.user_id)
            .order_by(Redemption.id.desc())
            .limit(100)
        )
    ).all()
    t = html.escape(token)
    items = "".join(
        f"<tr><td>{r.id}</td><td>{html.escape(r.kind)}</td><td>{html.escape(r.title_he)}</td>"
        f"<td>{html.escape(r.partner or '')}</td><td>{r.cost}</td><td>{'on' if r.active else 'off'}</td>"
        f"<td><form method=post action='/admin/rewards/{r.id}/toggle?token={t}'>"
        f"<button>{'Turn off' if r.active else 'Turn on'}</button></form></td></tr>"
        for r in rows
    )
    codes = "".join(
        f"<tr><td>{d.created_at:%d/%m %H:%M}</td><td>{html.escape(title)}</td><td>{html.escape(name)}</td>"
        f"<td><b>{html.escape(d.code)}</b></td><td>{d.cost}</td></tr>"
        for d, title, name in recent
    )
    page = (
        "<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width'>"
        "<title>ParkIt — rewards</title><style>body{font:14px system-ui;margin:16px}"
        "table{border-collapse:collapse;width:100%}td,th{border-top:1px solid #ddd;padding:6px;text-align:left}"
        "input{margin:2px 0;width:100%;max-width:420px}</style>"
        f"<h1>Rewards</h1><table><tr><th>#</th><th>Kind</th><th>Title</th><th>Partner</th>"
        f"<th>Cost</th><th>State</th><th></th></tr>{items}</table>"
        f"<h2>Add a partner reward</h2><form method=post action='/admin/rewards?token={t}'>"
        "<input name=title_he placeholder='כותרת (עברית)' required><br>"
        "<input name=title_en placeholder='Title (English)' required><br>"
        "<input name=partner placeholder='Partner (business name)' required><br>"
        "<input name=description_he placeholder='תיאור (עברית)'><br>"
        "<input name=description_en placeholder='Description (English)'><br>"
        "<input name=cost type=number min=1 placeholder='Cost in points' required><br><button>Add</button></form>"
        "<h2>Recent redemptions</h2><table><tr><th>When</th><th>Reward</th><th>Driver</th>"
        f"<th>Code</th><th>Points</th></tr>{codes}</table>"
    )
    return HTMLResponse(page)


@router.post("/admin/rewards", include_in_schema=False)
async def admin_add_reward(
    token: str = Query(""),
    title_he: str = Form(..., max_length=120),
    title_en: str = Form(..., max_length=120),
    partner: str = Form(..., max_length=120),
    cost: int = Form(..., ge=1),
    description_he: str = Form("", max_length=300),
    description_en: str = Form("", max_length=300),
    db: AsyncSession = Depends(get_db),
) -> RedirectResponse:
    _check(token)
    db.add(
        Reward(
            kind="partner",
            title_he=title_he.strip(),
            title_en=title_en.strip(),
            partner=partner.strip(),
            cost=cost,
            description_he=description_he.strip() or None,
            description_en=description_en.strip() or None,
        )
    )
    await db.commit()
    return RedirectResponse(f"/admin/rewards?token={token}", status_code=303)


@router.post("/admin/rewards/{reward_id}/toggle", include_in_schema=False)
async def admin_toggle_reward(
    reward_id: int, token: str = Query(""), db: AsyncSession = Depends(get_db)
) -> RedirectResponse:
    _check(token)
    reward = await db.get(Reward, reward_id)
    if reward is None:
        raise HTTPException(404, "Not found")
    reward.active = not reward.active
    await db.commit()
    return RedirectResponse(f"/admin/rewards?token={token}", status_code=303)
