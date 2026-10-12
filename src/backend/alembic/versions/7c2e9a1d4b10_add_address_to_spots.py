"""add address to spots

Revision ID: 7c2e9a1d4b10
Revises: 411b6faf3198
Create Date: 2026-10-12 11:20:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '7c2e9a1d4b10'
down_revision: Union[str, None] = '411b6faf3198'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column('spots', sa.Column('address', sa.String(length=200), nullable=True))


def downgrade() -> None:
    op.drop_column('spots', 'address')
