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


async def test_profile_shows_my_place_in_the_weekly_league(client, make_user):
    _, headers_a = await make_user("a")
    _, headers_b = await make_user("b")
    _, headers_seeker = await make_user("seeker")
    report_a = await _report(client, headers_a, lat=32.09, lng=34.79)
    await client.post(
        f"/spots/{report_a.json()['id']}/feedback", json={"type": "confirmed_taken"}, headers=headers_seeker
    )
    await _report(client, headers_b, lat=32.10, lng=34.80)

    assert (await client.get("/users/me", headers=headers_a)).json()["weekly_rank"] == 1  # 15 points
    assert (await client.get("/users/me", headers=headers_b)).json()["weekly_rank"] == 2  # 10 points
    assert (await client.get("/users/me", headers=headers_seeker)).json()["weekly_rank"] is None  # no points yet


async def test_city_league_only_ranks_drivers_from_that_city(client, make_user):
    _, tlv_a = await make_user("tlv-a")
    _, tlv_b = await make_user("tlv-b")
    _, haifa = await make_user("haifa")
    for headers, city in ((tlv_a, "תל אביב–יפו"), (tlv_b, "תל אביב–יפו"), (haifa, "חיפה")):
        await client.patch("/users/me/preferences", json={"home_city": city}, headers=headers)
    haifa_spot = await _report(client, haifa, lat=32.80, lng=34.99)
    await client.post(f"/spots/{haifa_spot.json()['id']}/feedback", json={"type": "confirmed_taken"}, headers=tlv_a)
    await _report(client, tlv_b, lat=32.08, lng=34.78)  # 10 points vs Haifa's 15

    board = (await client.get("/leaderboard", params={"city": "תל אביב–יפו"}, headers=tlv_a)).json()
    assert [e["display_name"] for e in board] == ["tlv-b"]  # haifa has more points but isn't in this league
    me = (await client.get("/users/me", headers=tlv_b)).json()
    assert (me["weekly_rank"], me["weekly_city_rank"]) == (2, 1)
