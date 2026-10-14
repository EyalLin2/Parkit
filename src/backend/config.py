from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    app_env: str = "local"
    database_url: str
    # TLS to Postgres: "require" for hosted databases (Supabase), "disable" when
    # Postgres runs next to the API on a private network (our production
    # compose). Empty = require everywhere except local dev.
    database_ssl: str = ""
    db_pool_size: int = 10
    redis_url: str
    jwt_secret: str = "dev-secret-change-me"
    # "dev" logs codes instead of texting them; a real provider plugs in via sms.py.
    sms_provider: str = "dev"
    # Email login codes: "dev" logs them; "smtp" sends through any SMTP server
    # (Brevo free tier: smtp-relay.brevo.com:587, or Gmail + app password).
    email_provider: str = "dev"
    smtp_host: str = ""
    smtp_port: int = 587
    smtp_user: str = ""
    smtp_password: str = ""
    email_from: str = ""
    # Signed out after this long without using the app (sign in again by SMS).
    # The app renews the session while it's open, so active use never expires.
    session_idle_minutes: int = 30
    # Requests per minute per client IP (0 = off). See ratelimit.py.
    rate_limit_per_minute: int = 240
    # Photo blurs running at once per worker process (see routers/photos.py).
    blur_concurrency: int = 1
    # Closed beta with no real SMS/email provider yet: hand the code back to
    # the app as if it had been sent, so testers can sign in on their own.
    # Anyone who types an address could then sign in as it — switch OFF
    # before the public launch (and once a real provider is set up).
    beta_show_codes: bool = False
    # Google Play app review (approved by the owner on 2026-10-14): one fixed
    # sign-in, "target:code" where target is a phone number or email address,
    # so the reviewer can get in without a real SMS or inbox. Typed into Play
    # Console → App access. Empty = off. Only this one account has a fixed code.
    review_login: str = ""
    # Opens /admin/feedback (tester feedback + crash reports). Empty = closed.
    admin_token: str = ""
    # SMS_PROVIDER=twilio: credentials from the Twilio console. TWILIO_FROM is
    # the sending number (+1…) or a Messaging Service SID (MG…).
    twilio_account_sid: str = ""
    twilio_auth_token: str = ""
    # Preferred over the Auth Token: an API key made just for ParkIt can be
    # revoked on its own without touching the account's master credential.
    twilio_api_key_sid: str = ""
    twilio_api_key_secret: str = ""
    # SMS_PROVIDER=smsgate: the free "SMS Gateway for Android" app (sms-gate.app)
    # sends from your own phone's SIM. Username/password come from the app's
    # Cloud Server section.
    smsgate_url: str = "https://api.sms-gate.app/3rdparty/v1"
    smsgate_user: str = ""
    smsgate_password: str = ""
    twilio_from: str = ""


settings = Settings()


def postgres_ssl() -> str | None:
    """The asyncpg ssl mode to use, or None for a plain connection."""
    mode = settings.database_ssl or ("disable" if settings.app_env == "local" else "require")
    return None if mode == "disable" else mode
