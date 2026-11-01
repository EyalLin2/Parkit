"""legend avatars in the rewards shop: illustrated Jewish & Israeli figures

Revision ID: e5f6a7b8c9d0
Revises: d4e5f6a7b8c9
Create Date: 2026-10-15 01:30:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'e5f6a7b8c9d0'
down_revision: Union[str, None] = 'd4e5f6a7b8c9'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

# Illustrated in the same DiceBear Personas style as the free avatars.
LEGENDS = [
    ("avatar_legend_bialik", "ביאליק", "Bialik", 150),
    ("avatar_legend_benyehuda", "אליעזר בן־יהודה", "Eliezer Ben-Yehuda", 150),
    ("avatar_legend_scientist", "המדען הגאון", "The Genius Scientist", 180),
    ("avatar_legend_trumpeldor", "טרומפלדור", "Trumpeldor", 180),
    ("avatar_legend_dayan", "משה דיין", "Moshe Dayan", 220),
    ("avatar_legend_peres", "שמעון פרס", "Shimon Peres", 220),
    ("avatar_legend_rambam", "הרמב״ם", "Maimonides", 260),
    ("avatar_legend_golda", "גולדה מאיר", "Golda Meir", 260),
    ("avatar_legend_bengurion", "בן־גוריון", "Ben-Gurion", 300),
    ("avatar_legend_herzl", "הרצל", "Herzl", 350),
    # Two more car finishes.
    ("paint_galaxy", "גלקסיה", "Galaxy", 80),
    ("paint_flag", "צבעי הדגל", "Flag colours", 120),
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
        {"kind": "cosmetic", "key": k, "title_he": he, "title_en": en, "cost": c} for k, he, en, c in LEGENDS
    ])


def downgrade() -> None:
    op.execute("DELETE FROM rewards WHERE key LIKE 'avatar_legend_%' OR key IN ('paint_galaxy', 'paint_flag')")
