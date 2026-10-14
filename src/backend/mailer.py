"""Sending email — same idea as sms.py: one interface, provider by config.

- dev:  logs the message (local development).
- smtp: any SMTP server — Brevo's free tier (300/day, smtp-relay.brevo.com)
        or Gmail with an app password (smtp.gmail.com). Uses the standard
        library, run in a thread so it never blocks the event loop.
"""

import asyncio
import logging
import smtplib
from email.message import EmailMessage
from typing import Protocol

from config import settings

log = logging.getLogger("parkit.mail")


class MailError(Exception):
    """The mail server refused or couldn't be reached."""


class Mailer(Protocol):
    async def send(self, to: str, subject: str, text: str) -> None: ...


class DevMailer:
    async def send(self, to: str, subject: str, text: str) -> None:
        log.warning("DEV EMAIL to %s — %s: %s", to, subject, text)


class SmtpMailer:
    def __init__(self, host: str, port: int, user: str, password: str, sender: str) -> None:
        if not (host and user and password and sender):
            raise RuntimeError("EMAIL_PROVIDER=smtp needs SMTP_HOST, SMTP_USER, SMTP_PASSWORD and EMAIL_FROM")
        self.host, self.port, self.user, self.password, self.sender = host, port, user, password, sender

    def _send_blocking(self, message: EmailMessage) -> None:
        with smtplib.SMTP(self.host, self.port, timeout=15) as smtp:
            smtp.starttls()
            smtp.login(self.user, self.password)
            smtp.send_message(message)

    async def send(self, to: str, subject: str, text: str) -> None:
        message = EmailMessage()
        message["From"] = self.sender
        message["To"] = to
        message["Subject"] = subject
        message.set_content(text)
        try:
            await asyncio.to_thread(self._send_blocking, message)
        except (smtplib.SMTPException, OSError) as e:
            log.error("SMTP send to %s failed: %s", to, e)
            raise MailError(str(e)) from e


def get_mailer() -> Mailer:
    if settings.email_provider == "dev":
        return DevMailer()
    if settings.email_provider == "smtp":
        return SmtpMailer(
            settings.smtp_host, settings.smtp_port, settings.smtp_user, settings.smtp_password, settings.email_from
        )
    raise RuntimeError(f"Unknown EMAIL_PROVIDER {settings.email_provider!r}")
