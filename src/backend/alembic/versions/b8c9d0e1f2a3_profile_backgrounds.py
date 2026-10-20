"""profile backgrounds; retire the scientist legend; data tidy-up

Revision ID: b8c9d0e1f2a3
Revises: a7b8c9d0e1f2
Create Date: 2026-10-20 16:30:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'b8c9d0e1f2a3'
down_revision: Union[str, None] = 'a7b8c9d0e1f2'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

# Drawn in the app behind the profile header (dark enough for white text).
BACKGROUNDS = [
    ("bg_sea", "ים", "Sea", 100),
    ("bg_sunset", "שקיעה בעיר", "City sunset", 120),
    ("bg_desert", "מדבר", "Desert", 150),
    ("bg_night", "לילה בעיר", "City at night", 180),
]


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
        {"kind": "cosmetic", "key": k, "title_he": he, "title_en": en, "cost": c} for k, he, en, c in BACKGROUNDS
    ])
    # Read as Einstein; owners keep it, it just leaves the shop.
    op.execute("UPDATE rewards SET active = false WHERE key = 'avatar_legend_scientist'")
    # Closed spots whose photo file was already deleted kept pointing at it.
    op.execute("UPDATE spots SET photo_url = NULL WHERE status <> 'active' AND photo_url IS NOT NULL")
    # The postgis image ships the US TIGER geocoder and topology extensions
    # (~40 empty tables); nothing here uses them.
    op.execute("DROP EXTENSION IF EXISTS postgis_tiger_geocoder CASCADE")
    op.execute("DROP EXTENSION IF EXISTS postgis_topology CASCADE")


def downgrade() -> None:
    op.execute("CREATE EXTENSION IF NOT EXISTS postgis_topology")
    op.execute("CREATE EXTENSION IF NOT EXISTS postgis_tiger_geocoder")
    op.execute("UPDATE rewards SET active = true WHERE key = 'avatar_legend_scientist'")
    op.execute("DELETE FROM rewards WHERE key LIKE 'bg_%'")
