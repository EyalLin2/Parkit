TLV = {"lat": 32.0809, "lng": 34.7806}


async def _report(client, headers, **overrides):
    payload = {**TLV, "spot_type": "street", "payment": "free", **overrides}
    return await client.post("/spots", json=payload, headers=headers)


async def test_profile_requires_a_token(client):
    resp = await client.get("/users/me")
    assert resp.status_code in (401, 403)


async def test_profile_reflects_points_after_a_confirmed_report(client, make_user):
    _, headers_reporter = await make_user("reporter")
    _, headers_seeker = await make_user("seeker")
    report = await _report(client, headers_reporter)
    spot_id = report.json()["id"]

    fb = await client.post(f"/spots/{spot_id}/feedback", json={"type": "confirmed_taken"}, headers=headers_seeker)
    assert fb.status_code == 204

    profile = await client.get("/users/me", headers=headers_reporter)
    assert profile.status_code == 200
    body = profile.json()
    assert body["points"] == 15  # +10 report, +5 confirm bonus
    assert body["weekly_points"] == 15
    assert body["successful_reports"] == 1
    assert body["badges"] == []  # below the 10-report threshold


async def test_profile_activity_lists_own_reports(client, make_user):
    _, headers = await make_user("reporter")
    report = await _report(client, headers)
    spot_id = report.json()["id"]

    profile = await client.get("/users/me", headers=headers)
    activity_ids = [a["spot_id"] for a in profile.json()["activity"]]
    assert spot_id in activity_ids


async def test_leaderboard_requires_a_token(client):
    resp = await client.get("/leaderboard")
    assert resp.status_code in (401, 403)


async def test_leaderboard_orders_by_weekly_points_descending(client, make_user):
    _, headers_a = await make_user("a")
    _, headers_b = await make_user("b")
    _, headers_seeker = await make_user("seeker")

    report_a = await _report(client, headers_a, lat=32.09, lng=34.79)
    await client.post(
        f"/spots/{report_a.json()['id']}/feedback", json={"type": "confirmed_taken"}, headers=headers_seeker
    )
    report_b = await _report(client, headers_b, lat=32.10, lng=34.80)
    await client.post(
        f"/spots/{report_b.json()['id']}/feedback", json={"type": "flagged_false"}, headers=headers_seeker
    )

    board = await client.get("/leaderboard", headers=headers_a)
    assert board.status_code == 200
    entries = board.json()
    a_entry = next(e for e in entries if e["display_name"] == "a")
    assert a_entry["weekly_points"] == 15
    assert a_entry["rank"] == 1


async def test_leaderboard_excludes_users_with_no_weekly_points(client, make_user):
    _, headers = await make_user("bystander")
    board = await client.get("/leaderboard", headers=headers)
    assert "bystander" not in [e["display_name"] for e in board.json()]
