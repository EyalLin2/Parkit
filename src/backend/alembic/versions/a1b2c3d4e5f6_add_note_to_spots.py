"""add an optional note to spots

Revision ID: a1b2c3d4e5f6
Revises: e0a2c4e6f8b1
Create Date: 2026-10-14 23:50:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'a1b2c3d4e5f6'
down_revision: Union[str, None] = 'e0a2c4e6f8b1'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column('spots', sa.Column('note', sa.String(length=120), nullable=True))


def downgrade() -> None:
    op.drop_column('spots', 'note')
