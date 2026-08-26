import enum
import uuid
from datetime import datetime, timezone

from geoalchemy2 import Geography
from sqlalchemy import DateTime, Enum, Float, ForeignKey, String
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column


class Base(DeclarativeBase):
    pass


class SpotType(str, enum.Enum):
    street = "street"
    lot = "lot"
    disabled = "disabled"
    ev_charging = "ev_charging"


class Payment(str, enum.Enum):
    free = "free"
    paid = "paid"


class SpotStatus(str, enum.Enum):
    active = "active"
    claimed = "claimed"
    removed = "removed"
    expired = "expired"


class RemovedReason(str, enum.Enum):
    taken_confirmed = "taken_confirmed"
    flagged_false = "flagged_false"
    expired = "expired"


class FeedbackType(str, enum.Enum):
    confirmed_taken = "confirmed_taken"
    flagged_false = "flagged_false"


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


class User(Base):
    __tablename__ = "users"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    auth_provider: Mapped[str] = mapped_column(String(20))
    external_id: Mapped[str] = mapped_column(String(255), unique=True)
    display_name: Mapped[str] = mapped_column(String(80))

    points: Mapped[int] = mapped_column(default=0)
    weekly_points: Mapped[int] = mapped_column(default=0)
    consecutive_bad_reports: Mapped[int] = mapped_column(default=0)
    reporting_blocked_until: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)


class Spot(Base):
    __tablename__ = "spots"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    reporter_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("users.id"))

    lat: Mapped[float] = mapped_column(Float)
    lng: Mapped[float] = mapped_column(Float)
    location: Mapped[str] = mapped_column(Geography(geometry_type="POINT", srid=4326))

    spot_type: Mapped[SpotType] = mapped_column(Enum(SpotType, name="spot_type"))
    payment: Mapped[Payment] = mapped_column(Enum(Payment, name="payment"))
    photo_url: Mapped[str | None] = mapped_column(String(500), nullable=True)

    status: Mapped[SpotStatus] = mapped_column(Enum(SpotStatus, name="spot_status"), default=SpotStatus.active)
    reported_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)

    claimed_by: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=True)
    claimed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    removed_reason: Mapped[RemovedReason | None] = mapped_column(
        Enum(RemovedReason, name="removed_reason"), nullable=True
    )
    removed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


class SpotFeedback(Base):
    __tablename__ = "spot_feedback"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    spot_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("spots.id"))
    user_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("users.id"))
    type: Mapped[FeedbackType] = mapped_column(Enum(FeedbackType, name="feedback_type"))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)
