"""driver preferences on users

Revision ID: c5a8d2e4f6b1
Revises: b3e1f7a2c9d4
Create Date: 2026-10-12 17:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql


# revision identifiers, used by Alembic.
revision: str = 'c5a8d2e4f6b1'
down_revision: Union[str, None] = 'b3e1f7a2c9d4'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    vehicle_size = postgresql.ENUM('compact', 'regular', 'large', name='vehicle_size', create_type=False)
    op.add_column('users', sa.Column('vehicle_size', vehicle_size, nullable=True))
    op.add_column('users', sa.Column('car_label', sa.String(length=120), nullable=True))
    op.add_column('users', sa.Column('has_disabled_permit', sa.Boolean(), nullable=False, server_default='false'))
    op.add_column('users', sa.Column('home_city', sa.String(length=120), nullable=True))


def downgrade() -> None:
    for col in ('home_city', 'has_disabled_permit', 'car_label', 'vehicle_size'):
        op.drop_column('users', col)
