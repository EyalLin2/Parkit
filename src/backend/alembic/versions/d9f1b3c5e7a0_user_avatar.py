"""users.avatar (bundled illustrated avatar id)

Revision ID: d9f1b3c5e7a0
Revises: c8e0a2b4d6f9
Create Date: 2026-10-13 13:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'd9f1b3c5e7a0'
down_revision: Union[str, None] = 'c8e0a2b4d6f9'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column('users', sa.Column('avatar', sa.String(length=20), nullable=True))


def downgrade() -> None:
    op.drop_column('users', 'avatar')
