"""Sending SMS — one small interface so the provider is a config choice.

- dev:    logs the message instead of sending it (local development).
- smsgate: free — the open-source "SMS Gateway for Android" app sends from
          your own phone's SIM (your plan's SMS). Good for testing and the
          first users; a phone isn't a bulk gateway.
- twilio: real SMS through Twilio's Messages API. We generate and check the
          codes ourselves (otp.py), so this is plain SMS — no Twilio Verify fee.
Another provider (an Israeli SMS gateway, Firebase) is one more class here.
"""

import logging
from typing import Protocol

import httpx

from config import settings

log = logging.getLogger("parkit.sms")


class SmsSender(Protocol):
    async def send(self, phone: str, text: str) -> None: ...


class DevSmsSender:
    async def send(self, phone: str, text: str) -> None:
        log.warning("DEV SMS to %s: %s", phone, text)


class SmsError(Exception):
    """The provider refused or couldn't be reached."""


class TwilioSmsSender:
    def __init__(self, account_sid: str, auth_token: str, sender: str, api_key: tuple[str, str] | None = None) -> None:
        if not (account_sid and sender and (api_key or auth_token)):
            raise RuntimeError(
                "SMS_PROVIDER=twilio needs TWILIO_ACCOUNT_SID, TWILIO_FROM and an API key (or TWILIO_AUTH_TOKEN)"
            )
        self.url = f"https://api.twilio.com/2010-04-01/Accounts/{account_sid}/Messages.json"
        self.auth = api_key or (account_sid, auth_token)
        self.sender = sender

    async def send(self, phone: str, text: str) -> None:
        sender_field = "MessagingServiceSid" if self.sender.startswith("MG") else "From"
        try:
            async with httpx.AsyncClient(timeout=10) as client:
                r = await client.post(
                    self.url, auth=self.auth, data={"To": phone, sender_field: self.sender, "Body": text}
                )
        except httpx.HTTPError as e:
            raise SmsError(str(e)) from e
        if r.status_code >= 400:
            # Twilio's message explains why (unverified trial number, bad sender…);
            # it goes to our logs, never to the user.
            log.error("Twilio refused SMS to %s: %s %s", phone, r.status_code, r.text[:300])
            raise SmsError(f"twilio {r.status_code}")


class SmsGateSender:
    def __init__(self, base_url: str, user: str, password: str) -> None:
        if not (user and password):
            raise RuntimeError("SMS_PROVIDER=smsgate needs SMSGATE_USER and SMSGATE_PASSWORD (from the app)")
        self.url = base_url.rstrip("/") + "/messages"
        self.auth = (user, password)

    async def send(self, phone: str, text: str) -> None:
        try:
            async with httpx.AsyncClient(timeout=15) as client:
                r = await client.post(
                    self.url, auth=self.auth, json={"textMessage": {"text": text}, "phoneNumbers": [phone]}
                )
        except httpx.HTTPError as e:
            raise SmsError(str(e)) from e
        if r.status_code >= 400:
            log.error("SMS gateway refused SMS to %s: %s %s", phone, r.status_code, r.text[:300])
            raise SmsError(f"smsgate {r.status_code}")


def get_sms_sender() -> SmsSender:
    if settings.sms_provider == "dev":
        return DevSmsSender()
    if settings.sms_provider == "smsgate":
        return SmsGateSender(settings.smsgate_url, settings.smsgate_user, settings.smsgate_password)
    if settings.sms_provider == "twilio":
        key = settings.twilio_api_key_sid, settings.twilio_api_key_secret
        return TwilioSmsSender(
            settings.twilio_account_sid,
            settings.twilio_auth_token,
            settings.twilio_from,
            api_key=key if all(key) else None,
        )
    raise RuntimeError(f"Unknown SMS_PROVIDER {settings.sms_provider!r}")
