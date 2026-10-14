import enum
import uuid
from datetime import datetime, timezone

from geoalchemy2 import Geography
from sqlalchemy import DateTime, Enum, Float, ForeignKey, Integer, String, UniqueConstraint
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


class VehicleSize(str, enum.Enum):
    compact = "compact"
    regular = "regular"
    large = "large"


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
    # Phone sign-in (E.164, verified by SMS code) and the basic details asked
    # right after the first login. The leaderboard shows only first name +
    # last initial (display_name); the rest is visible to the user alone.
    phone: Mapped[str | None] = mapped_column(String(20), unique=True, nullable=True)
    first_name: Mapped[str | None] = mapped_column(String(60), nullable=True)
    last_name: Mapped[str | None] = mapped_column(String(60), nullable=True)
    email: Mapped[str | None] = mapped_column(String(254), unique=True, nullable=True)
    # One of the bundled illustrated avatars ("avatar_01".."avatar_16"); None
    # = the initial-letter avatar. Shown to others in the league.
    avatar: Mapped[str | None] = mapped_column(String(20), nullable=True)

    points: Mapped[int] = mapped_column(default=0)
    weekly_points: Mapped[int] = mapped_column(default=0)
    weekly_points_reset_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    consecutive_bad_reports: Mapped[int] = mapped_column(default=0)
    reporting_blocked_until: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    # Driver preferences ("My car" in the app). The licence plate itself is
    # never stored — only what was derived from it (size + a display label).
    vehicle_size: Mapped[VehicleSize | None] = mapped_column(Enum(VehicleSize, name="vehicle_size"), nullable=True)
    car_label: Mapped[str | None] = mapped_column(String(120), nullable=True)
    has_disabled_permit: Mapped[bool] = mapped_column(default=False, server_default="false")
    home_city: Mapped[str | None] = mapped_column(String(120), nullable=True)

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
    vehicle_size: Mapped[VehicleSize | None] = mapped_column(Enum(VehicleSize, name="vehicle_size"), nullable=True)
    photo_url: Mapped[str | None] = mapped_column(String(500), nullable=True)
    # Human-readable street label ("Dizengoff 50") resolved by the reporting
    # client at report time — stored once so every viewer's nearby list can
    # show it without each client re-geocoding every spot.
    address: Mapped[str | None] = mapped_column(String(200), nullable=True)

    status: Mapped[SpotStatus] = mapped_column(Enum(SpotStatus, name="spot_status"), default=SpotStatus.active)
    # How long the report stays on the map; set from the reporter's level
    # when the spot is created (silver and up get longer — see levels.py).
    ttl_minutes: Mapped[int] = mapped_column(Integer, default=30, server_default="30")
    reported_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)
    # Set when the spot is in a hot area (see hotspots.py): the median
    # minutes nearby spots lasted. None = not hot / not enough history.
    hot_minutes: Mapped[int | None] = mapped_column(Integer, nullable=True)

    claimed_by: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=True)
    claimed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    removed_reason: Mapped[RemovedReason | None] = mapped_column(
        Enum(RemovedReason, name="removed_reason"), nullable=True
    )
    removed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


class SpotFeedback(Base):
    __tablename__ = "spot_feedback"
    # One vote per driver per spot — enforced by the database too, so two
    # simultaneous requests can't slip past the "already voted" check.
    __table_args__ = (UniqueConstraint("spot_id", "user_id", name="uq_spot_feedback_spot_user"),)

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    spot_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("spots.id"))
    user_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("users.id"))
    type: Mapped[FeedbackType] = mapped_column(Enum(FeedbackType, name="feedback_type"))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)


# --- Self-hosted reverse geocoding (OpenStreetMap data, ODbL) ---------------
# Loaded once by import_osm_geodata.py from the Geofabrik Israel extract, then
# queried locally — replaces per-request calls to the public Nominatim server
# (1 req/s for the whole app), so address lookups have no external rate limit.


class OsmAddress(Base):
    __tablename__ = "osm_addresses"

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    geom: Mapped[str] = mapped_column(Geography(geometry_type="POINT", srid=4326))
    # NULL for buildings that carry only a house number; like Nominatim, the
    # geocoder assigns those to the nearest street.
    street: Mapped[str | None] = mapped_column(String(200), nullable=True)
    housenumber: Mapped[str] = mapped_column(String(40))
    city: Mapped[str | None] = mapped_column(String(120), nullable=True)


class OsmStreet(Base):
    __tablename__ = "osm_streets"

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    geom: Mapped[str] = mapped_column(Geography(geometry_type="LINESTRING", srid=4326))
    name: Mapped[str] = mapped_column(String(200))
    name_he: Mapped[str | None] = mapped_column(String(200), nullable=True)
    name_en: Mapped[str | None] = mapped_column(String(200), nullable=True)


class OsmArea(Base):
    """Named squares / pedestrian plazas (e.g. Rabin Square) — mapped in OSM
    as polygons, not street lines, so a point inside one is "at the square"."""

    __tablename__ = "osm_areas"

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    geom: Mapped[str] = mapped_column(Geography(geometry_type="MULTIPOLYGON", srid=4326))
    name: Mapped[str] = mapped_column(String(200))
    name_he: Mapped[str | None] = mapped_column(String(200), nullable=True)
    name_en: Mapped[str | None] = mapped_column(String(200), nullable=True)


class OsmPlace(Base):
    __tablename__ = "osm_places"

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    geom: Mapped[str] = mapped_column(Geography(geometry_type="POINT", srid=4326))
    kind: Mapped[str] = mapped_column(String(20))
    name: Mapped[str] = mapped_column(String(200))
    name_he: Mapped[str | None] = mapped_column(String(200), nullable=True)
    name_en: Mapped[str | None] = mapped_column(String(200), nullable=True)


class OsmParkingLot(Base):
    """Public off-street car parks from OpenStreetMap (import_osm_geodata.py).
    Shown only when the driver turns on "Paid parking" on the map."""

    __tablename__ = "osm_parking_lots"

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    geom: Mapped[str] = mapped_column(Geography(geometry_type="POINT", srid=4326))
    name: Mapped[str | None] = mapped_column(String(200), nullable=True)
    name_he: Mapped[str | None] = mapped_column(String(200), nullable=True)
    name_en: Mapped[str | None] = mapped_column(String(200), nullable=True)
    kind: Mapped[str | None] = mapped_column(String(40), nullable=True)  # surface / underground / multi-storey …
    fee: Mapped[str | None] = mapped_column(String(40), nullable=True)  # "yes" / "no" / unknown
    capacity: Mapped[int | None] = mapped_column(Integer, nullable=True)
    operator: Mapped[str | None] = mapped_column(String(120), nullable=True)


class AppFeedback(Base):
    """What testers send from the app: written feedback, and crash reports
    the app files on its next launch after crashing."""

    __tablename__ = "app_feedback"

    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)
    user_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id", ondelete="SET NULL"), nullable=True
    )
    kind: Mapped[str] = mapped_column(String(16))  # "feedback" | "crash"
    message: Mapped[str] = mapped_column(String(8000))
    app_version: Mapped[str | None] = mapped_column(String(40), nullable=True)
    device: Mapped[str | None] = mapped_column(String(120), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=_utcnow)
