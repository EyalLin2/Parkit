"""car park charge + opening hours, and when OSM data was last imported

Revision ID: c3d4e5f6a7b8
Revises: b2c3d4e5f6a7
Create Date: 2026-10-15 00:30:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'c3d4e5f6a7b8'
down_revision: Union[str, None] = 'b2c3d4e5f6a7'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column('osm_parking_lots', sa.Column('charge', sa.String(length=120), nullable=True))
    op.add_column('osm_parking_lots', sa.Column('opening_hours', sa.String(length=200), nullable=True))
    op.create_table(
        'osm_meta',
        sa.Column('key', sa.String(length=40), primary_key=True),
        sa.Column('value', sa.String(length=200), nullable=False),
    )


def downgrade() -> None:
    op.drop_table('osm_meta')
    op.drop_column('osm_parking_lots', 'opening_hours')
    op.drop_column('osm_parking_lots', 'charge')
