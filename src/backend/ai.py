"""One small door to Claude's vision model, shared by the sign reader and
the report photo analysis. Off (AiOff) while ANTHROPIC_API_KEY is empty."""

import base64
import json

import httpx

from config import settings


class AiOff(Exception):
    """No API key configured — callers answer 503 / skip the feature."""


async def ask_json(image: bytes, media_type: str, prompt: str, max_tokens: int = 400) -> dict:
    """Send one image + instructions, return the JSON object the model wrote.
    Raises AiOff, httpx.HTTPError, or ValueError (no / bad JSON)."""
    if not settings.anthropic_api_key:
        raise AiOff()
    body = {
        "model": settings.sign_reader_model,
        "max_tokens": max_tokens,
        "messages": [
            {
                "role": "user",
                "content": [
                    {
                        "type": "image",
                        "source": {
                            "type": "base64",
                            "media_type": media_type,
                            "data": base64.b64encode(image).decode(),
                        },
                    },
                    {"type": "text", "text": prompt},
                ],
            }
        ],
    }
    async with httpx.AsyncClient(timeout=30) as client:
        resp = await client.post(
            "https://api.anthropic.com/v1/messages",
            headers={"x-api-key": settings.anthropic_api_key, "anthropic-version": "2023-06-01"},
            json=body,
        )
    resp.raise_for_status()
    text = "".join(part.get("text", "") for part in resp.json().get("content", []))
    start, end = text.find("{"), text.rfind("}")
    if start < 0 or end < start:
        raise ValueError("no JSON in the model's answer")
    return json.loads(text[start : end + 1])
