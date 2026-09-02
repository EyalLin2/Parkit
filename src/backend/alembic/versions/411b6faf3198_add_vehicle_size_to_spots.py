"""add vehicle_size to spots

Revision ID: 411b6faf3198
Revises: bfe5459ef036
Create Date: 2026-09-02 11:06:48.662131

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '411b6faf3198'
down_revision: Union[str, None] = 'bfe5459ef036'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


vehicle_size_enum = sa.Enum('compact', 'regular', 'large', name='vehicle_size')


def upgrade() -> None:
    vehicle_size_enum.create(op.get_bind(), checkfirst=True)
    op.add_column('spots', sa.Column('vehicle_size', vehicle_size_enum, nullable=True))


def downgrade() -> None:
    op.drop_column('spots', 'vehicle_size')
    vehicle_size_enum.drop(op.get_bind(), checkfirst=True)
