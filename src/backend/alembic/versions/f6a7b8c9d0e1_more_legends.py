"""more legend avatars (Begin, Rabin, Allon, Aloni, Szenes) + price tune

Revision ID: f6a7b8c9d0e1
Revises: e5f6a7b8c9d0
Create Date: 2026-10-15 03:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'f6a7b8c9d0e1'
down_revision: Union[str, None] = 'e5f6a7b8c9d0'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

# Hand-drawn look-alikes (tools/avatars/legends.py).
NEW = [
    ("avatar_legend_szenes", "חנה סנש", "Hannah Szenes", 260),
    ("avatar_legend_allon", "יגאל אלון", "Yigal Allon", 220),
    ("avatar_legend_begin", "מנחם בגין", "Menachem Begin", 180),
    ("avatar_legend_rabin", "יצחק רבין", "Yitzhak Rabin", 260),
    ("avatar_legend_aloni", "שולמית אלוני", "Shulamit Aloni", 350),
]
NEW_PRICES = {"avatar_legend_golda": 180}


def upgrade() -> None:
    rewards = sa.table(
        'rewards',
        sa.column('kind', sa.String),
        sa.column('key', sa.String),
        sa.column('title_he', sa.String),
        sa.column('title_en', sa.String),
        sa.column('cost', sa.Integer),
    )
    op.bulk_insert(rewards, [
        {"kind": "cosmetic", "key": k, "title_he": he, "title_en": en, "cost": c} for k, he, en, c in NEW
    ])
    for key, cost in NEW_PRICES.items():
        op.execute(sa.text("UPDATE rewards SET cost = :c WHERE key = :k").bindparams(c=cost, k=key))


def downgrade() -> None:
    op.execute(sa.text("UPDATE rewards SET cost = 260 WHERE key = 'avatar_legend_golda'"))
    keys = ", ".join(f"'{k}'" for k, *_ in NEW)
    op.execute(f"DELETE FROM rewards WHERE key IN ({keys})")
