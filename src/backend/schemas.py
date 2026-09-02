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


class SpotOut(BaseModel):
    id: uuid.UUID
    reporter_id: uuid.UUID
    lat: float
    lng: float
    spot_type: SpotType
    payment: Payment
    vehicle_size: VehicleSize | None
    photo_url: str | None
    status: SpotStatus
    reported_at: datetime
    claimed_by: uuid.UUID | None

    model_config = {"from_attributes": True}


class FeedbackCreate(BaseModel):
    type: FeedbackType


class ActivityItem(BaseModel):
    spot_id: uuid.UUID
    spot_type: SpotType
    payment: Payment
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
    badges: list[int]
    activity: list[ActivityItem]


class LeaderboardEntry(BaseModel):
    rank: int
    user_id: uuid.UUID
    display_name: str
    weekly_points: int
