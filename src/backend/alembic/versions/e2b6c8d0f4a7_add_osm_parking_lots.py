"""add osm_parking_lots

Revision ID: e2b6c8d0f4a7
Revises: d7f3a1c9e2b4
Create Date: 2026-10-12 20:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from geoalchemy2 import Geography


# revision identifiers, used by Alembic.
revision: str = 'e2b6c8d0f4a7'
down_revision: Union[str, None] = 'd7f3a1c9e2b4'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        'osm_parking_lots',
        sa.Column('id', sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column('geom', Geography(geometry_type='POINT', srid=4326, spatial_index=False), nullable=False),
        sa.Column('name', sa.String(length=200), nullable=True),
        sa.Column('name_he', sa.String(length=200), nullable=True),
        sa.Column('name_en', sa.String(length=200), nullable=True),
        sa.Column('kind', sa.String(length=40), nullable=True),
        sa.Column('fee', sa.String(length=40), nullable=True),
        sa.Column('capacity', sa.Integer(), nullable=True),
        sa.Column('operator', sa.String(length=120), nullable=True),
    )
    op.create_index('ix_osm_parking_lots_geom', 'osm_parking_lots', ['geom'], postgresql_using='gist')


def downgrade() -> None:
    op.drop_table('osm_parking_lots')
