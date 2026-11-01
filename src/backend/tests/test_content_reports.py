"""Reporting an offensive photo or user (Google Play's user-generated
content rule): the photo comes down at once, the report reaches the admin."""

from media import MEDIA_DIR
from tests.test_photos import TLV, _upload_files


async def _spot_with_photo(client, headers):
    staged = await client.post("/photos/stage", files=_upload_files(), headers=headers)
    report = await client.post(
        "/spots",
        json={**TLV, "spot_type": "street", "payment": "free", "photo_staging_id": staged.json()["staging_id"]},
        headers=headers,
    )
    return report.json()["id"]


async def test_reported_photo_is_removed_and_spot_stays(client, make_user, monkeypatch):
    _, reporter = await make_user("reporter")
    _, viewer = await make_user("viewer")
    spot_id = await _spot_with_photo(client, reporter)
    assert (MEDIA_DIR / f"{spot_id}.jpg").exists()

    resp = await client.post(f"/spots/{spot_id}/report-content", json={"reason": "photo"}, headers=viewer)
    assert resp.status_code == 204
    assert not (MEDIA_DIR / f"{spot_id}.jpg").exists()

    nearby = (await client.get("/spots", params={**TLV, "radius_m": 500})).json()
    spot = next(s for s in nearby if s["id"] == spot_id)
    assert spot["photo_url"] is None

    from config import settings

    monkeypatch.setattr(settings, "admin_token", "s3cret")
    inbox = (await client.get("/admin/feedback", params={"token": "s3cret"})).text
    assert "class='report'" in inbox and spot_id in inbox and "Photo removed: yes" in inbox


async def test_reporting_a_user_keeps_the_photo_for_review(client, make_user):
    _, reporter = await make_user("reporter")
    _, viewer = await make_user("viewer")
    spot_id = await _spot_with_photo(client, reporter)

    resp = await client.post(
        f"/spots/{spot_id}/report-content", json={"reason": "user", "note": "rude name"}, headers=viewer
    )
    assert resp.status_code == 204
    assert (MEDIA_DIR / f"{spot_id}.jpg").exists()


async def test_cannot_report_your_own_spot(client, make_user):
    _, reporter = await make_user("reporter")
    spot_id = await _spot_with_photo(client, reporter)
    resp = await client.post(f"/spots/{spot_id}/report-content", json={"reason": "photo"}, headers=reporter)
    assert resp.status_code == 400


async def test_content_reports_are_rate_limited(client, make_user):
    _, reporter = await make_user("reporter")
    _, viewer = await make_user("viewer")
    spot_id = await _spot_with_photo(client, reporter)
    codes = [
        (await client.post(f"/spots/{spot_id}/report-content", json={"reason": "other"}, headers=viewer)).status_code
        for _ in range(11)
    ]
    assert codes[:10] == [204] * 10 and codes[10] == 429


async def test_reported_note_is_removed_at_once(client, make_user):
    _, reporter = await make_user("reporter")
    _, viewer = await make_user("viewer")
    report = await client.post(
        "/spots", json={**TLV, "spot_type": "street", "payment": "free", "note": "rude words"}, headers=reporter
    )
    spot_id = report.json()["id"]

    resp = await client.post(f"/spots/{spot_id}/report-content", json={"reason": "note"}, headers=viewer)
    assert resp.status_code == 204
    nearby = (await client.get("/spots", params={**TLV, "radius_m": 500})).json()
    assert next(s for s in nearby if s["id"] == spot_id)["note"] is None
