async def test_dev_login_creates_a_user_and_returns_a_token(client):
    resp = await client.post(
        "/auth/dev-login",
        json={"auth_provider": "dev", "external_id": "abc-123", "display_name": "Eyal"},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["access_token"]
    assert body["user_id"]


async def test_dev_login_is_idempotent_for_the_same_external_id(client):
    payload = {"auth_provider": "dev", "external_id": "same-user", "display_name": "Eyal"}
    first = await client.post("/auth/dev-login", json=payload)
    second = await client.post("/auth/dev-login", json=payload)
    assert first.json()["user_id"] == second.json()["user_id"]


async def test_reporting_a_spot_without_a_token_is_rejected(client):
    resp = await client.post("/spots", json={"lat": 32.08, "lng": 34.78, "spot_type": "street", "payment": "free"})
    assert resp.status_code in (401, 403)


async def test_reporting_a_spot_with_a_garbage_token_is_rejected(client):
    resp = await client.post(
        "/spots",
        json={"lat": 32.08, "lng": 34.78, "spot_type": "street", "payment": "free"},
        headers={"Authorization": "Bearer not-a-real-token"},
    )
    assert resp.status_code == 401
