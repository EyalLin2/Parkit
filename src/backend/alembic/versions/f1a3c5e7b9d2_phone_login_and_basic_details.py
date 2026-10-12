"""phone login + basic details on users

Revision ID: f1a3c5e7b9d2
Revises: e2b6c8d0f4a7
Create Date: 2026-10-12 22:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'f1a3c5e7b9d2'
down_revision: Union[str, None] = 'e2b6c8d0f4a7'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column('users', sa.Column('phone', sa.String(length=20), nullable=True))
    op.add_column('users', sa.Column('first_name', sa.String(length=60), nullable=True))
    op.add_column('users', sa.Column('last_name', sa.String(length=60), nullable=True))
    op.add_column('users', sa.Column('email', sa.String(length=254), nullable=True))
    op.create_unique_constraint('uq_users_phone', 'users', ['phone'])


def downgrade() -> None:
    op.drop_constraint('uq_users_phone', 'users', type_='unique')
    for col in ('email', 'last_name', 'first_name', 'phone'):
        op.drop_column('users', col)
