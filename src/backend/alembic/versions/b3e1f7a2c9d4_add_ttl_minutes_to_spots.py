"""add per-spot ttl_minutes (reporter level perk)

Revision ID: b3e1f7a2c9d4
Revises: 9d4f6b2c8e31
Create Date: 2026-10-12 15:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'b3e1f7a2c9d4'
down_revision: Union[str, None] = '9d4f6b2c8e31'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column('spots', sa.Column('ttl_minutes', sa.Integer(), nullable=False, server_default='30'))


def downgrade() -> None:
    op.drop_column('spots', 'ttl_minutes')
