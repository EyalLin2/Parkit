from config import settings


async def test_a_client_over_the_limit_gets_429(client, monkeypatch):
    monkeypatch.setattr(settings, "rate_limit_per_minute", 5)
    codes = [(await client.get("/spots", params={"lat": 32.08, "lng": 34.78})).status_code for _ in range(7)]
    assert codes[:5] == [200] * 5
    assert codes[5:] == [429, 429]


async def test_health_checks_are_never_limited(client, monkeypatch):
    monkeypatch.setattr(settings, "rate_limit_per_minute", 1)
    codes = [(await client.get("/healthz")).status_code for _ in range(3)]
    assert 429 not in codes
