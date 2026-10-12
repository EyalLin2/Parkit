import otp
from redis_client import redis_client

EMAIL = "Dana.Cohen@Example.com"
NORMAL = "dana.cohen@example.com"


async def _code(client, email: str = EMAIL) -> str:
    r = await client.post("/auth/email/request", json={"email": email})
    assert r.status_code == 200, r.text
    assert r.json()["email"] == otp.normalise_email(email)
    return r.json()["dev_code"]


async def test_email_login_creates_the_account_and_keeps_the_address(client):
    token = (await client.post("/auth/email/verify", json={"email": EMAIL, "code": await _code(client)})).json()
    assert token["needs_profile"] is True
    headers = {"Authorization": f"Bearer {token['access_token']}"}
    await client.patch(
        "/users/me/details", json={"first_name": "דנה", "last_name": "כהן", "email": NORMAL}, headers=headers
    )
    me = (await client.get("/users/me", headers=headers)).json()
    assert me["account"]["email"] == NORMAL

    await redis_client.delete(f"otp:cooldown:{NORMAL}")
    again = (
        await client.post("/auth/email/verify", json={"email": NORMAL, "code": await _code(client, NORMAL)})
    ).json()
    assert again["user_id"] == token["user_id"]
    assert again["needs_profile"] is False


async def test_email_codes_follow_the_same_rules(client):
    code = await _code(client)
    assert (await client.post("/auth/email/request", json={"email": EMAIL})).status_code == 429  # resend cooldown
    wrong = "000000" if code != "000000" else "111111"
    for _ in range(otp.MAX_ATTEMPTS):
        assert (await client.post("/auth/email/verify", json={"email": EMAIL, "code": wrong})).status_code == 400
    assert (await client.post("/auth/email/verify", json={"email": EMAIL, "code": code})).status_code == 429


async def test_bad_address_is_rejected(client):
    assert (await client.post("/auth/email/request", json={"email": "not an email"})).status_code == 422


async def test_phone_account_can_sign_in_by_its_email(client):
    """Signed up by phone and gave an email → the email signs into that account."""
    r = await client.post("/auth/otp/request", json={"phone": "0541112233"})
    token = (await client.post("/auth/otp/verify", json={"phone": "0541112233", "code": r.json()["dev_code"]})).json()
    headers = {"Authorization": f"Bearer {token['access_token']}"}
    await client.patch(
        "/users/me/details", json={"first_name": "A", "last_name": "B", "email": NORMAL}, headers=headers
    )
    by_email = (
        await client.post("/auth/email/verify", json={"email": NORMAL, "code": await _code(client, NORMAL)})
    ).json()
    assert by_email["user_id"] == token["user_id"]


async def test_an_email_belongs_to_one_account(client, make_user):
    _, first = await make_user("first")
    _, second = await make_user("second")
    body = {"first_name": "A", "last_name": "B", "email": NORMAL}
    assert (await client.patch("/users/me/details", json=body, headers=first)).status_code == 200
    assert (await client.patch("/users/me/details", json=body, headers=second)).status_code == 409


async def test_failed_email_frees_the_address(client):
    import mailer
    from main import app

    class Broken:
        async def send(self, to, subject, text):
            raise mailer.MailError("down")

    app.dependency_overrides[mailer.get_mailer] = lambda: Broken()
    try:
        assert (await client.post("/auth/email/request", json={"email": EMAIL})).status_code == 503
    finally:
        app.dependency_overrides.pop(mailer.get_mailer)
    assert (await client.post("/auth/email/request", json={"email": EMAIL})).status_code == 200
