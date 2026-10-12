"""Sending SMS — one small interface so the provider is a config choice.

- dev:    logs the message instead of sending it (local development).
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
    def __init__(self, account_sid: str, auth_token: str, sender: str) -> None:
        if not (account_sid and auth_token and sender):
            raise RuntimeError("SMS_PROVIDER=twilio needs TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN and TWILIO_FROM")
        self.url = f"https://api.twilio.com/2010-04-01/Accounts/{account_sid}/Messages.json"
        self.auth = (account_sid, auth_token)
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


def get_sms_sender() -> SmsSender:
    if settings.sms_provider == "dev":
        return DevSmsSender()
    if settings.sms_provider == "twilio":
        return TwilioSmsSender(settings.twilio_account_sid, settings.twilio_auth_token, settings.twilio_from)
    raise RuntimeError(f"Unknown SMS_PROVIDER {settings.sms_provider!r}")
