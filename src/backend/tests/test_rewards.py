"""The rewards shop: spending points keeps the level, can't overspend,
cosmetics are bought once, partner rewards give a code."""

from sqlalchemy import update

from database import engine
from models import Reward, User


async def _give_points(user_id, points):
    async with engine.begin() as conn:
        await conn.execute(update(User).where(User.id == user_id).values(points=points))


async def _add(**kw):
    async with engine.begin() as conn:
        result = await conn.execute(Reward.__table__.insert().values(active=True, **kw).returning(Reward.id))
        return result.scalar_one()


async def test_buying_a_paint_spends_points_but_keeps_the_level(client, make_user):
    user_id, headers = await make_user("buyer")
    await _give_points(user_id, 450)
    gold = await _add(kind="cosmetic", key="paint_gold", title_he="זהב", title_en="Gold", cost=300)

    shop = (await client.post(f"/rewards/{gold}/redeem", headers=headers)).json()
    assert shop["balance"] == 150
    assert next(r for r in shop["rewards"] if r["id"] == gold)["owned"] is True
    me = (await client.get("/users/me", headers=headers)).json()
    assert me["points"] == 450 and me["points_balance"] == 150  # level follows lifetime points

    again = await client.post(f"/rewards/{gold}/redeem", headers=headers)
    assert again.status_code == 409  # a cosmetic is bought once


async def test_cannot_spend_more_than_you_have(client, make_user):
    user_id, headers = await make_user("broke")
    await _give_points(user_id, 50)
    pricey = await _add(kind="cosmetic", key="paint_chrome", title_he="כרום", title_en="Chrome", cost=500)
    assert (await client.post(f"/rewards/{pricey}/redeem", headers=headers)).status_code == 409


async def test_partner_reward_gives_a_code(client, make_user):
    user_id, headers = await make_user("coffee")
    await _give_points(user_id, 200)
    coffee = await _add(kind="partner", title_he="קפה", title_en="Coffee", partner="Cafe", cost=100)
    shop = (await client.post(f"/rewards/{coffee}/redeem", headers=headers)).json()
    assert shop["balance"] == 100
    [code] = shop["redemptions"]
    assert code["partner"] == "Cafe" and len(code["code"]) == 8
