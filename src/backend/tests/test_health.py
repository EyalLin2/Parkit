async def test_healthz_reports_ok_when_dependencies_are_up(client):
    resp = await client.get("/healthz")
    assert resp.status_code == 200
    body = resp.json()
    assert body == {"status": "ok", "postgres": "ok", "redis": "ok"}
