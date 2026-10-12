"""Licence plate → make, model and size, from Israel's open vehicle registry.

Two public datasets on data.gov.il (Ministry of Transport, no key needed):
- the registry (one row per plate): make/model codes, year, trade name
- model specs (one row per model): body type, gross weight, seats

Only the derived result is returned; the plate is never stored. Results are
cached in Redis under a hash of the plate, so repeated lookups cost nothing.
"""

import hashlib
import json
from dataclasses import asdict, dataclass

import httpx
from redis.asyncio import Redis

API = "https://data.gov.il/api/3/action/datastore_search"
REGISTRY = "053cea08-09bc-40ec-8f7a-156f0677aff3"
MODEL_SPECS = "142afde2-6228-49f9-8a29-9b6c3a0cbe40"
CACHE_TTL_SECONDS = 30 * 24 * 3600

LARGE_BODIES = {"פנאי-שטח", "ואן/נוסעים", "סגור/משלוח", "קומבי", "משא אחוד", "טנדר", "מיניבוס"}


@dataclass
class VehicleInfo:
    make: str
    model: str
    year: int | None
    body: str | None
    size: str  # compact | regular | large

    @property
    def label(self) -> str:
        return " ".join(p for p in (self.make, self.model, str(self.year) if self.year else "") if p)


def classify_size(body: str | None, gross_weight_kg: int | None, seats: int | None) -> str:
    """Parking-relevant size from body type first, weight/seats as tie-breakers."""
    if (body and body in LARGE_BODIES) or (seats and seats >= 7) or (gross_weight_kg and gross_weight_kg >= 2800):
        return "large"
    if body == "הצ'בק" and gross_weight_kg and gross_weight_kg <= 1700:
        return "compact"
    return "regular"


def normalise_plate(raw: str) -> str | None:
    digits = "".join(ch for ch in raw if ch.isdigit())
    return digits if 5 <= len(digits) <= 8 else None


async def _query(client: httpx.AsyncClient, resource: str, filters: dict) -> dict | None:
    resp = await client.get(API, params={"resource_id": resource, "filters": json.dumps(filters), "limit": 1})
    resp.raise_for_status()
    records = resp.json().get("result", {}).get("records", [])
    return records[0] if records else None


async def lookup(plate: str, redis: Redis) -> VehicleInfo | None:
    key = "vehicle:" + hashlib.sha256(plate.encode()).hexdigest()
    cached = await redis.get(key)
    if cached is not None:
        data = json.loads(cached)
        return VehicleInfo(**data) if data else None

    async with httpx.AsyncClient(timeout=10) as client:
        reg = await _query(client, REGISTRY, {"mispar_rechev": int(plate)})
        if reg is None:
            info = None
        else:
            spec = await _query(
                client,
                MODEL_SPECS,
                {
                    "tozeret_cd": reg.get("tozeret_cd"),
                    "degem_cd": reg.get("degem_cd"),
                    "shnat_yitzur": reg.get("shnat_yitzur"),
                },
            ) or await _query(
                client, MODEL_SPECS, {"tozeret_cd": reg.get("tozeret_cd"), "degem_cd": reg.get("degem_cd")}
            )
            body = (spec or {}).get("merkav")
            make = ((spec or {}).get("tozar") or reg.get("tozeret_nm") or "").strip()
            info = VehicleInfo(
                make=make,
                model=(reg.get("kinuy_mishari") or reg.get("degem_nm") or "").strip(),
                year=reg.get("shnat_yitzur"),
                body=body,
                size=classify_size(body, (spec or {}).get("mishkal_kolel"), (spec or {}).get("mispar_moshavim")),
            )
    await redis.set(key, json.dumps(asdict(info) if info else None), ex=CACHE_TTL_SECONDS)
    return info
