import cv2
import numpy as np

from media import MEDIA_DIR

TLV = {"lat": 32.0809, "lng": 34.7806}
TINY_JPEG = cv2.imencode(".jpg", np.zeros((10, 10, 3), dtype="uint8"))[1].tobytes()


def _upload_files():
    return {"file": ("test.jpg", TINY_JPEG, "image/jpeg")}


async def test_stage_photo_requires_a_token(client):
    resp = await client.post("/photos/stage", files=_upload_files())
    assert resp.status_code in (401, 403)


async def test_stage_photo_returns_a_preview(client, make_user):
    _, headers = await make_user("reporter")
    resp = await client.post("/photos/stage", files=_upload_files(), headers=headers)
    assert resp.status_code == 201
    body = resp.json()
    assert body["staging_id"]
    assert body["faces_blurred"] == 0  # synthetic blank image, no faces to find
    assert body["preview_base64"]


async def test_attaching_a_staged_photo_to_a_new_report(client, make_user):
    _, headers = await make_user("reporter")
    staged = await client.post("/photos/stage", files=_upload_files(), headers=headers)
    staging_id = staged.json()["staging_id"]

    report = await client.post(
        "/spots",
        json={**TLV, "spot_type": "street", "payment": "free", "photo_staging_id": staging_id},
        headers=headers,
    )
    assert report.status_code == 201
    photo_url = report.json()["photo_url"]
    assert photo_url is not None and photo_url.startswith("/media/")
    assert (MEDIA_DIR / f"{report.json()['id']}.jpg").exists()


async def test_a_staging_id_can_only_be_attached_once(client, make_user):
    _, headers_first = await make_user("reporter")
    _, headers_second = await make_user("other")  # different user: avoids the same-user report cooldown
    staged = await client.post("/photos/stage", files=_upload_files(), headers=headers_first)
    staging_id = staged.json()["staging_id"]

    first = await client.post(
        "/spots",
        json={**TLV, "spot_type": "street", "payment": "free", "photo_staging_id": staging_id},
        headers=headers_first,
    )
    assert first.status_code == 201

    second = await client.post(
        "/spots",
        json={"lat": 32.09, "lng": 34.79, "spot_type": "street", "payment": "free", "photo_staging_id": staging_id},
        headers=headers_second,
    )
    assert second.status_code == 400


async def test_unknown_staging_id_is_rejected(client, make_user):
    _, headers = await make_user("reporter")
    resp = await client.post(
        "/spots",
        json={**TLV, "spot_type": "street", "payment": "free", "photo_staging_id": "does-not-exist"},
        headers=headers,
    )
    assert resp.status_code == 400


async def test_photo_file_is_deleted_when_spot_is_confirmed_taken(client, make_user):
    _, headers_reporter = await make_user("reporter")
    _, headers_seeker = await make_user("seeker")
    staged = await client.post("/photos/stage", files=_upload_files(), headers=headers_reporter)

    report = await client.post(
        "/spots",
        json={**TLV, "spot_type": "street", "payment": "free", "photo_staging_id": staged.json()["staging_id"]},
        headers=headers_reporter,
    )
    spot_id = report.json()["id"]
    photo_path = MEDIA_DIR / f"{spot_id}.jpg"
    assert photo_path.exists()

    fb = await client.post(f"/spots/{spot_id}/feedback", json={"type": "confirmed_taken"}, headers=headers_seeker)
    assert fb.status_code == 204
    assert not photo_path.exists()


async def test_photo_file_is_deleted_on_self_cancel(client, make_user):
    _, headers = await make_user("reporter")
    staged = await client.post("/photos/stage", files=_upload_files(), headers=headers)

    report = await client.post(
        "/spots",
        json={**TLV, "spot_type": "street", "payment": "free", "photo_staging_id": staged.json()["staging_id"]},
        headers=headers,
    )
    spot_id = report.json()["id"]
    photo_path = MEDIA_DIR / f"{spot_id}.jpg"
    assert photo_path.exists()

    cancel = await client.delete(f"/spots/{spot_id}", headers=headers)
    assert cancel.status_code == 204
    assert not photo_path.exists()
