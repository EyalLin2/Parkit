import uuid
from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field

from models import FeedbackType, Payment, RemovedReason, SpotStatus, SpotType, VehicleSize


class SpotCreate(BaseModel):
    lat: float = Field(ge=-90, le=90)
    lng: float = Field(ge=-180, le=180)
    spot_type: SpotType
    payment: Payment
    vehicle_size: VehicleSize | None = None
    photo_staging_id: str | None = None
    address: str | None = Field(default=None, max_length=200)
    # Where the reporter's phone is (and how sure it is), so a spot can only
    # be reported by someone standing near it — not from a sofa in another
    # city by dragging the map. Optional only so 0.3.3 beta installs keep
    # working until they update; make it required after that.
    reporter_lat: float | None = Field(default=None, ge=-90, le=90)
    reporter_lng: float | None = Field(default=None, ge=-180, le=180)
    reporter_accuracy_m: float | None = Field(default=None, ge=0)


class SpotOut(BaseModel):
    id: uuid.UUID
    reporter_id: uuid.UUID
    lat: float
    lng: float
    spot_type: SpotType
    payment: Payment
    vehicle_size: VehicleSize | None
    photo_url: str | None
    address: str | None
    status: SpotStatus
    reported_at: datetime
    claimed_by: uuid.UUID | None
    reporter_level: str = "newbie"
    hot_minutes: int | None = None

    model_config = {"from_attributes": True}


class FeedbackCreate(BaseModel):
    type: FeedbackType


class ContentReport(BaseModel):
    """'This photo / this name is offensive' — Google Play requires a way to
    report what other users post, separate from 'the spot is taken'."""

    reason: Literal["photo", "user", "other"]
    note: str | None = Field(default=None, max_length=500)


class ActivityItem(BaseModel):
    spot_id: uuid.UUID
    spot_type: SpotType
    payment: Payment
    address: str | None
    reported_at: datetime
    status: SpotStatus
    removed_reason: RemovedReason | None

    model_config = {"from_attributes": True}


class ProfileOut(BaseModel):
    user_id: uuid.UUID
    display_name: str
    points: int
    weekly_points: int
    avatar: str | None = None
    weekly_rank: int | None = None  # place in this week's league; None = no points yet
    weekly_city_rank: int | None = None  # same, among drivers with the same preferred city
    successful_reports: int
    total_reports: int = 0  # all reports ever (the activity list is capped)
    badges: list[int]  # deprecated: replaced by levels, kept for older app builds
    level: str
    level_min_points: int
    next_level: str | None
    next_level_points: int | None
    preferences: "Preferences"
    account: "AccountOut | None" = None
    activity: list[ActivityItem]


class LeaderboardEntry(BaseModel):
    rank: int
    user_id: uuid.UUID
    display_name: str
    weekly_points: int
    level: str
    avatar: str | None = None


class ReverseGeocodeOut(BaseModel):
    label: str | None
    street: str | None


class Preferences(BaseModel):
    vehicle_size: VehicleSize | None = None
    car_label: str | None = Field(default=None, max_length=120)
    has_disabled_permit: bool = False
    home_city: str | None = Field(default=None, max_length=120)


class PreferencesUpdate(BaseModel):
    """PATCH semantics: only fields present in the request are changed."""

    vehicle_size: VehicleSize | None = None
    car_label: str | None = Field(default=None, max_length=120)
    has_disabled_permit: bool | None = None
    home_city: str | None = Field(default=None, max_length=120)


class CityOut(BaseModel):
    name: str
    name_en: str | None = None
    lat: float
    lng: float


class VehicleLookupOut(BaseModel):
    make: str
    model: str
    year: int | None
    body: str | None
    size: VehicleSize
    label: str


class DetailsUpdate(BaseModel):
    """Basic details, asked once right after the first phone sign-in."""

    first_name: str = Field(min_length=1, max_length=60)
    last_name: str = Field(min_length=1, max_length=60)
    # Format check only (no confirmation mail yet).
    email: str = Field(max_length=254, pattern=r"^[^@\s]+@[^@\s]+\.[^@\s]{2,}$")


class AccountOut(BaseModel):
    phone: str | None
    first_name: str | None
    last_name: str | None
    email: str | None


ProfileOut.model_rebuild()


class AvatarUpdate(BaseModel):
    avatar: str | None = Field(default=None, pattern=r"^avatar_(0[1-9]|1[0-6])$")
