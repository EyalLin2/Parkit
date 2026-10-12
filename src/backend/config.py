from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    app_env: str = "local"
    database_url: str
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
    # SMS_PROVIDER=twilio: credentials from the Twilio console. TWILIO_FROM is
    # the sending number (+1…) or a Messaging Service SID (MG…).
    twilio_account_sid: str = ""
    twilio_auth_token: str = ""
    # Preferred over the Auth Token: an API key made just for ParkIt can be
    # revoked on its own without touching the account's master credential.
    twilio_api_key_sid: str = ""
    twilio_api_key_secret: str = ""
    twilio_from: str = ""


settings = Settings()
