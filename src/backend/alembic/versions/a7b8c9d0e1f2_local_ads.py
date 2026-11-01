"""local sponsored cards (ads), chosen by location only

Revision ID: a7b8c9d0e1f2
Revises: f6a7b8c9d0e1
Create Date: 2026-10-15 01:20:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
import geoalchemy2


# revision identifiers, used by Alembic.
revision: str = 'a7b8c9d0e1f2'
down_revision: Union[str, None] = 'f6a7b8c9d0e1'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        'ads',
        sa.Column('id', sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column('advertiser', sa.String(length=120), nullable=False),
        sa.Column('title_he', sa.String(length=80), nullable=False),
        sa.Column('title_en', sa.String(length=80), nullable=False),
        sa.Column('body_he', sa.String(length=160), nullable=True),
        sa.Column('body_en', sa.String(length=160), nullable=True),
        sa.Column('category', sa.String(length=20), nullable=False, server_default='other'),
        sa.Column('geom', geoalchemy2.types.Geography(geometry_type='POINT', srid=4326), nullable=False),
        sa.Column('radius_m', sa.Integer(), nullable=False, server_default='1500'),
        sa.Column('placement', sa.String(length=10), nullable=False, server_default='card'),
        sa.Column('link_url', sa.String(length=300), nullable=True),
        sa.Column('active', sa.Boolean(), nullable=False, server_default=sa.true()),
        sa.Column('ends_at', sa.DateTime(timezone=True), nullable=True),
        sa.Column('impressions', sa.Integer(), nullable=False, server_default='0'),
        sa.Column('clicks', sa.Integer(), nullable=False, server_default='0'),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.func.now()),
    )


def downgrade() -> None:
    op.drop_table('ads')
