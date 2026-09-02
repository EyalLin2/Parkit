TLV = {"lat": 32.0809, "lng": 34.7806}


async def _report(client, headers, **overrides):
    payload = {**TLV, "spot_type": "street", "payment": "free", **overrides}
    return await client.post("/spots", json=payload, headers=headers)


async def _nearby_ids(client, **overrides):
    params = {**TLV, "radius_m": 500, **overrides}
    resp = await client.get("/spots", params=params)
    return [s["id"] for s in resp.json()]


async def test_reported_spot_is_findable_via_nearby_search(client, make_user):
    _, headers = await make_user("reporter")
    report = await _report(client, headers)
    assert report.status_code == 201
    assert report.json()["status"] == "active"
    assert report.json()["id"] in await _nearby_ids(client)


async def test_nearby_search_filters_by_spot_type(client, make_user):
    _, headers = await make_user("reporter")
    report = await _report(client, headers, spot_type="ev_charging")
    spot_id = report.json()["id"]

    matching = await _nearby_ids(client, spot_type="ev_charging")
    assert spot_id in matching

    non_matching = await _nearby_ids(client, spot_type="disabled")
    assert spot_id not in non_matching


async def test_nearby_search_filters_by_multiple_spot_types(client, make_user):
    _, headers = await make_user("reporter")
    report = await _report(client, headers, spot_type="lot")
    spot_id = report.json()["id"]

    matching = await _nearby_ids(client, spot_type=["street", "lot"])
    assert spot_id in matching


async def test_nearby_search_filters_by_payment(client, make_user):
    _, headers = await make_user("reporter")
    report = await _report(client, headers, payment="paid")
    spot_id = report.json()["id"]

    matching = await _nearby_ids(client, payment="paid")
    assert spot_id in matching

    non_matching = await _nearby_ids(client, payment="free")
    assert spot_id not in non_matching


async def test_reported_spot_carries_optional_vehicle_size(client, make_user):
    _, headers_a = await make_user("a")
    _, headers_b = await make_user("b")

    report = await _report(client, headers_a, vehicle_size="large")
    assert report.status_code == 201
    assert report.json()["vehicle_size"] == "large"

    no_size = await _report(client, headers_b, lat=32.09, lng=34.79)
    assert no_size.json()["vehicle_size"] is None


async def test_second_report_from_the_same_user_within_cooldown_is_rate_limited(client, make_user):
    _, headers = await make_user("reporter")
    first = await _report(client, headers, lat=32.09, lng=34.79)
    assert first.status_code == 201
    second = await _report(client, headers, lat=32.10, lng=34.80)
    assert second.status_code == 429


async def test_nearby_report_from_a_different_user_refreshes_instead_of_duplicating(client, make_user):
    _, headers_a = await make_user("a")
    _, headers_b = await make_user("b")
    first = await _report(client, headers_a)
    spot_id = first.json()["id"]

    second = await _report(client, headers_b, lat=TLV["lat"] + 0.00005)
    assert second.status_code == 201
    assert second.json()["id"] == spot_id


async def test_claiming_a_spot_locks_it_and_a_second_claim_is_rejected(client, make_user):
    _, headers_a = await make_user("a")
    _, headers_b = await make_user("b")
    report = await _report(client, headers_a)
    spot_id = report.json()["id"]

    first_claim = await client.post(f"/spots/{spot_id}/claim", headers=headers_b)
    assert first_claim.status_code == 200
    assert first_claim.json()["status"] == "claimed"

    second_claim = await client.post(f"/spots/{spot_id}/claim", headers=headers_a)
    assert second_claim.status_code == 404


async def test_confirmed_taken_feedback_removes_the_spot(client, make_user):
    _, headers_reporter = await make_user("reporter")
    _, headers_seeker = await make_user("seeker")
    report = await _report(client, headers_reporter)
    spot_id = report.json()["id"]

    feedback = await client.post(f"/spots/{spot_id}/feedback", json={"type": "confirmed_taken"}, headers=headers_seeker)
    assert feedback.status_code == 204
    assert spot_id not in await _nearby_ids(client)


async def test_two_false_flags_remove_the_spot(client, make_user):
    _, headers_reporter = await make_user("reporter")
    _, headers_s1 = await make_user("s1")
    _, headers_s2 = await make_user("s2")
    report = await _report(client, headers_reporter)
    spot_id = report.json()["id"]

    r1 = await client.post(f"/spots/{spot_id}/feedback", json={"type": "flagged_false"}, headers=headers_s1)
    assert r1.status_code == 204
    r2 = await client.post(f"/spots/{spot_id}/feedback", json={"type": "flagged_false"}, headers=headers_s2)
    assert r2.status_code == 204
    assert spot_id not in await _nearby_ids(client)


async def test_single_false_flag_is_not_enough_to_remove_the_spot(client, make_user):
    _, headers_reporter = await make_user("reporter")
    _, headers_s1 = await make_user("s1")
    report = await _report(client, headers_reporter)
    spot_id = report.json()["id"]

    await client.post(f"/spots/{spot_id}/feedback", json={"type": "flagged_false"}, headers=headers_s1)
    assert spot_id in await _nearby_ids(client)


async def test_feedback_on_your_own_report_is_rejected(client, make_user):
    _, headers = await make_user("reporter")
    report = await _report(client, headers)
    spot_id = report.json()["id"]

    resp = await client.post(f"/spots/{spot_id}/feedback", json={"type": "confirmed_taken"}, headers=headers)
    assert resp.status_code == 400


async def test_double_feedback_from_the_same_user_is_rejected(client, make_user):
    _, headers_reporter = await make_user("reporter")
    _, headers_seeker = await make_user("seeker")
    report = await _report(client, headers_reporter)
    spot_id = report.json()["id"]

    first = await client.post(f"/spots/{spot_id}/feedback", json={"type": "flagged_false"}, headers=headers_seeker)
    assert first.status_code == 204
    second = await client.post(f"/spots/{spot_id}/feedback", json={"type": "flagged_false"}, headers=headers_seeker)
    assert second.status_code == 409


async def test_self_cancel_within_window_removes_the_spot(client, make_user):
    _, headers = await make_user("reporter")
    report = await _report(client, headers)
    spot_id = report.json()["id"]

    cancel = await client.delete(f"/spots/{spot_id}", headers=headers)
    assert cancel.status_code == 204
    assert spot_id not in await _nearby_ids(client)


async def test_cancel_by_a_non_reporter_is_rejected(client, make_user):
    _, headers_reporter = await make_user("reporter")
    _, headers_other = await make_user("other")
    report = await _report(client, headers_reporter)
    spot_id = report.json()["id"]

    resp = await client.delete(f"/spots/{spot_id}", headers=headers_other)
    assert resp.status_code == 404
    assert spot_id in await _nearby_ids(client)
