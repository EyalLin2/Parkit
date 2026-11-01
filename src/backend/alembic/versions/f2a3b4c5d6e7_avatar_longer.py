"""users.avatar: long enough for every legend avatar

avatar_legend_trumpeldor is 24 characters and the column held 20, so saving
it failed after the points had already been spent.

Revision ID: f2a3b4c5d6e7
Revises: e1f2a3b4c5d6
Create Date: 2026-11-01 23:00:00.000000

"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = "f2a3b4c5d6e7"
down_revision: Union[str, None] = "e1f2a3b4c5d6"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.alter_column("users", "avatar", type_=sa.String(length=40), existing_nullable=True)


def downgrade() -> None:
    # Anything too long for 20 goes back to the default avatar rather than failing.
    op.execute("UPDATE users SET avatar = NULL WHERE length(avatar) > 20")
    op.alter_column("users", "avatar", type_=sa.String(length=20), existing_nullable=True)
