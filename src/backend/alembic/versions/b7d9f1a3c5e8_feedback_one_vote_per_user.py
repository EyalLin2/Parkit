"""spot_feedback: one vote per user per spot

Revision ID: b7d9f1a3c5e8
Revises: a4c6e8f0b2d1
Create Date: 2026-10-13 12:00:00.000000

"""
from typing import Sequence, Union

from alembic import op


# revision identifiers, used by Alembic.
revision: str = 'b7d9f1a3c5e8'
down_revision: Union[str, None] = 'a4c6e8f0b2d1'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # Keep the earliest vote if a race ever stored two.
    op.execute(
        "DELETE FROM spot_feedback a USING spot_feedback b "
        "WHERE a.spot_id = b.spot_id AND a.user_id = b.user_id AND a.ctid > b.ctid"
    )
    op.create_unique_constraint('uq_spot_feedback_spot_user', 'spot_feedback', ['spot_id', 'user_id'])


def downgrade() -> None:
    op.drop_constraint('uq_spot_feedback_spot_user', 'spot_feedback', type_='unique')
