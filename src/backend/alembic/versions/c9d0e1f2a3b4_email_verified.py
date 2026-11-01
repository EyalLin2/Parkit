"""users.email_verified: only a proved address signs into an account

Revision ID: c9d0e1f2a3b4
Revises: b8c9d0e1f2a3
Create Date: 2026-11-01 12:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'c9d0e1f2a3b4'
down_revision: Union[str, None] = 'b8c9d0e1f2a3'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column('users', sa.Column('email_verified', sa.Boolean(), nullable=False, server_default='false'))
    # Proved at sign-up: email accounts still on their original address, and
    # Google accounts. Addresses typed into a profile stay unproved.
    op.execute(
        "UPDATE users SET email_verified = true WHERE email IS NOT NULL "
        "AND (auth_provider = 'google' OR (auth_provider = 'email' AND external_id = 'email:' || email))"
    )


def downgrade() -> None:
    op.drop_column('users', 'email_verified')
