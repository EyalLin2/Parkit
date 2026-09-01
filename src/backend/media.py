"""Local disk storage for attached spot photos.

Stand-in for S3 (ADR-0004), same spirit as blur.py — a single place to
swap out for real object storage later. Photo retention is tied to the
spot lifecycle (PRODUCT_SPEC.md): callers delete a spot's photo the
moment it leaves the active state, not on a separate cleanup job.
"""

import uuid
from pathlib import Path

MEDIA_DIR = Path(__file__).resolve().parent / "media"
MEDIA_DIR.mkdir(exist_ok=True)


def save_photo(spot_id: uuid.UUID, jpeg_bytes: bytes) -> str:
    path = MEDIA_DIR / f"{spot_id}.jpg"
    path.write_bytes(jpeg_bytes)
    return f"/media/{spot_id}.jpg"


def delete_photo(photo_url: str | None) -> None:
    if not photo_url or not photo_url.startswith("/media/"):
        return
    (MEDIA_DIR / Path(photo_url).name).unlink(missing_ok=True)
