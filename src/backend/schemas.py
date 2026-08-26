import uuid
from datetime import datetime

from pydantic import BaseModel, Field

from models import Payment, SpotStatus, SpotType


class SpotCreate(BaseModel):
    reporter_id: uuid.UUID
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

    model_config = {"from_attributes": True}
