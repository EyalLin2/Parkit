"""add hot_minutes to spots

Revision ID: d7f3a1c9e2b4
Revises: c5a8d2e4f6b1
Create Date: 2026-10-12 19:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'd7f3a1c9e2b4'
down_revision: Union[str, None] = 'c5a8d2e4f6b1'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column('spots', sa.Column('hot_minutes', sa.Integer(), nullable=True))


def downgrade() -> None:
    op.drop_column('spots', 'hot_minutes')
