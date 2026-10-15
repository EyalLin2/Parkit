"""rewards shop: spendable points, rewards, redemptions (+ ParkIt's own paints)

Revision ID: d4e5f6a7b8c9
Revises: c3d4e5f6a7b8
Create Date: 2026-10-15 01:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'd4e5f6a7b8c9'
down_revision: Union[str, None] = 'c3d4e5f6a7b8'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None

PAINTS = [
    ("paint_neon", "צבע ניאון", "Neon paint", 60),
    ("paint_gold", "צבע זהב", "Gold paint", 90),
    ("paint_chrome", "צבע כרום", "Chrome paint", 150),
    ("paint_flames", "להבות על הרכב", "Flames decal", 250),
]


def upgrade() -> None:
    op.add_column('users', sa.Column('points_spent', sa.Integer(), nullable=False, server_default='0'))
    rewards = op.create_table(
        'rewards',
        sa.Column('id', sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column('kind', sa.String(length=16), nullable=False),
        sa.Column('key', sa.String(length=40), nullable=True, unique=True),
        sa.Column('title_he', sa.String(length=120), nullable=False),
        sa.Column('title_en', sa.String(length=120), nullable=False),
        sa.Column('partner', sa.String(length=120), nullable=True),
        sa.Column('description_he', sa.String(length=300), nullable=True),
        sa.Column('description_en', sa.String(length=300), nullable=True),
        sa.Column('cost', sa.Integer(), nullable=False),
        sa.Column('active', sa.Boolean(), nullable=False, server_default=sa.true()),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
    )
    op.create_table(
        'redemptions',
        sa.Column('id', sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column('user_id', sa.UUID(), sa.ForeignKey('users.id', ondelete='CASCADE'), nullable=False, index=True),
        sa.Column('reward_id', sa.Integer(), sa.ForeignKey('rewards.id', ondelete='CASCADE'), nullable=False),
        sa.Column('code', sa.String(length=12), nullable=False, unique=True),
        sa.Column('cost', sa.Integer(), nullable=False),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
    )
    op.bulk_insert(rewards, [
        {"kind": "cosmetic", "key": k, "title_he": he, "title_en": en, "cost": c} for k, he, en, c in PAINTS
    ])


def downgrade() -> None:
    op.drop_table('redemptions')
    op.drop_table('rewards')
    op.drop_column('users', 'points_spent')
