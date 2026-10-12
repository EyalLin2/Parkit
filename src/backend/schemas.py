import uuid
from datetime import datetime

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

    model_config = {"from_attributes": True}


class FeedbackCreate(BaseModel):
    type: FeedbackType


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
    successful_reports: int
    badges: list[int]  # deprecated: replaced by levels, kept for older app builds
    level: str
    level_min_points: int
    next_level: str | None
    next_level_points: int | None
    activity: list[ActivityItem]


class LeaderboardEntry(BaseModel):
    rank: int
    user_id: uuid.UUID
    display_name: str
    weekly_points: int
    level: str


class ReverseGeocodeOut(BaseModel):
    label: str | None
    street: str | None
