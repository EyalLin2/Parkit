"""app_feedback: tester feedback + crash reports

Revision ID: e0a2c4e6f8b1
Revises: d9f1b3c5e7a0
Create Date: 2026-10-13 14:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql


# revision identifiers, used by Alembic.
revision: str = 'e0a2c4e6f8b1'
down_revision: Union[str, None] = 'd9f1b3c5e7a0'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        'app_feedback',
        sa.Column('id', sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column('user_id', postgresql.UUID(as_uuid=True), sa.ForeignKey('users.id', ondelete='SET NULL'), nullable=True),
        sa.Column('kind', sa.String(length=16), nullable=False),
        sa.Column('message', sa.String(length=8000), nullable=False),
        sa.Column('app_version', sa.String(length=40), nullable=True),
        sa.Column('device', sa.String(length=120), nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index('ix_app_feedback_created_at', 'app_feedback', ['created_at'])


def downgrade() -> None:
    op.drop_table('app_feedback')
