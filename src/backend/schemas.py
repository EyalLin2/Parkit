import uuid
from datetime import datetime

from pydantic import BaseModel, Field

from models import FeedbackType, Payment, SpotStatus, SpotType


class SpotCreate(BaseModel):
    lat: float = Field(ge=-90, le=90)
    lng: float = Field(ge=-180, le=180)
    spot_type: SpotType
    payment: Payment
    photo_url: str | None = None


class SpotOut(BaseModel):
    id: uuid.UUID
    reporter_id: uuid.UUID
    lat: float
    lng: float
    spot_type: SpotType
    payment: Payment
    photo_url: str | None
    status: SpotStatus
    reported_at: datetime
    claimed_by: uuid.UUID | None

    model_config = {"from_attributes": True}


class FeedbackCreate(BaseModel):
    type: FeedbackType
