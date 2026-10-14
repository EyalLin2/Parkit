"""add self-hosted geocoder tables (osm_addresses, osm_streets, osm_areas, osm_places)

Revision ID: 9d4f6b2c8e31
Revises: 7c2e9a1d4b10
Create Date: 2026-10-12 13:30:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from geoalchemy2 import Geography


# revision identifiers, used by Alembic.
revision: str = '9d4f6b2c8e31'
down_revision: Union[str, None] = '7c2e9a1d4b10'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        'osm_addresses',
        sa.Column('id', sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column('geom', Geography(geometry_type='POINT', srid=4326, spatial_index=False), nullable=False),
        sa.Column('street', sa.String(length=200), nullable=True),
        sa.Column('housenumber', sa.String(length=40), nullable=False),
        sa.Column('city', sa.String(length=120), nullable=True),
    )
    op.create_table(
        'osm_streets',
        sa.Column('id', sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column('geom', Geography(geometry_type='LINESTRING', srid=4326, spatial_index=False), nullable=False),
        sa.Column('name', sa.String(length=200), nullable=False),
        sa.Column('name_he', sa.String(length=200), nullable=True),
        sa.Column('name_en', sa.String(length=200), nullable=True),
    )
    op.create_table(
        'osm_areas',
        sa.Column('id', sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column('geom', Geography(geometry_type='MULTIPOLYGON', srid=4326, spatial_index=False), nullable=False),
        sa.Column('name', sa.String(length=200), nullable=False),
        sa.Column('name_he', sa.String(length=200), nullable=True),
        sa.Column('name_en', sa.String(length=200), nullable=True),
    )
    op.create_table(
        'osm_places',
        sa.Column('id', sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column('geom', Geography(geometry_type='POINT', srid=4326, spatial_index=False), nullable=False),
        sa.Column('kind', sa.String(length=20), nullable=False),
        sa.Column('name', sa.String(length=200), nullable=False),
        sa.Column('name_he', sa.String(length=200), nullable=True),
        sa.Column('name_en', sa.String(length=200), nullable=True),
    )
    for table in ('osm_addresses', 'osm_streets', 'osm_areas', 'osm_places'):
        op.create_index(f'ix_{table}_geom', table, ['geom'], postgresql_using='gist')
    op.create_index('ix_osm_streets_name', 'osm_streets', ['name'])


def downgrade() -> None:
    op.drop_table('osm_places')
    op.drop_table('osm_areas')
    op.drop_table('osm_streets')
    op.drop_table('osm_addresses')
