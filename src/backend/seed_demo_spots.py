"""Seed the dev database with realistic mock parking spots, for a
populated-feeling demo instead of an empty map on first run.

Usage (inside the backend container):
    python seed_demo_spots.py                       # Tel Aviv, 12 spots
    python seed_demo_spots.py --lat 32.08 --lng 34.78 --count 15
"""

import argparse
import asyncio
import random
import sys
from datetime import datetime, timedelta, timezone

from geoalchemy2.functions import ST_MakePoint, ST_SetSRID
from sqlalchemy import select

from database import AsyncSessionLocal
from models import Payment, Spot, SpotStatus, SpotType, User, VehicleSize

SEEDER_EXTERNAL_ID = "seed-demo-reporter"
SPOT_TYPE_WEIGHTS = [(SpotType.street, 85), (SpotType.disabled, 15)]
MINUTES_AGO_CHOICES = [1, 2, 4, 5, 8, 10, 12, 15, 18, 20, 24, 27]
# ~0.01 degrees is roughly 1km — keeps every seeded spot within easy demo
# reach of the center point without needing real geocoded street points.
SPREAD_DEGREES = 0.012


async def seed(lat: float, lng: float, count: int) -> None:
    async with AsyncSessionLocal() as db:
        reporter = (await db.execute(select(User).where(User.external_id == SEEDER_EXTERNAL_ID))).scalar_one_or_none()
        if reporter is None:
            reporter = User(auth_provider="dev", external_id=SEEDER_EXTERNAL_ID, display_name="Demo Seeder")
            db.add(reporter)
            await db.flush()

        now = datetime.now(timezone.utc)
        for _ in range(count):
            spot_lat = lat + random.uniform(-SPREAD_DEGREES, SPREAD_DEGREES)
            spot_lng = lng + random.uniform(-SPREAD_DEGREES, SPREAD_DEGREES)
            spot_type = random.choices([t for t, _ in SPOT_TYPE_WEIGHTS], weights=[w for _, w in SPOT_TYPE_WEIGHTS])[0]
            reported_at = now - timedelta(minutes=random.choice(MINUTES_AGO_CHOICES))
            vehicle_size = random.choice(list(VehicleSize))

            db.add(
                Spot(
                    reporter_id=reporter.id,
                    lat=spot_lat,
                    lng=spot_lng,
                    location=ST_SetSRID(ST_MakePoint(spot_lng, spot_lat), 4326),
                    spot_type=spot_type,
                    payment=Payment.free,
                    vehicle_size=vehicle_size,
                    status=SpotStatus.active,
                    reported_at=reported_at,
                )
            )

        await db.commit()
        print(f"Seeded {count} demo spots around ({lat}, {lng}).")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--lat", type=float, default=32.0809, help="center latitude (default: Tel Aviv)")
    parser.add_argument("--lng", type=float, default=34.7806, help="center longitude (default: Tel Aviv)")
    parser.add_argument("--count", type=int, default=12)
    args = parser.parse_args()

    if not 1 <= args.count <= 100:
        sys.exit("--count must be between 1 and 100")

    asyncio.run(seed(args.lat, args.lng, args.count))
