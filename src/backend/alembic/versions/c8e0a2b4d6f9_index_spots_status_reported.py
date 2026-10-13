"""index spots(status, reported_at) for the nearby query and expiry sweep

Revision ID: c8e0a2b4d6f9
Revises: b7d9f1a3c5e8
Create Date: 2026-10-13 12:30:00.000000

"""
from typing import Sequence, Union

from alembic import op


# revision identifiers, used by Alembic.
revision: str = 'c8e0a2b4d6f9'
down_revision: Union[str, None] = 'b7d9f1a3c5e8'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_index('ix_spots_status_reported_at', 'spots', ['status', 'reported_at'])


def downgrade() -> None:
    op.drop_index('ix_spots_status_reported_at', 'spots')
