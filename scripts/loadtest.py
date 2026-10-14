"""ParkIt load test — simulated drivers hitting the API concurrently.

Mix per virtual user (roughly what the app does while someone looks for
parking): 85% map searches (GET /spots), 12% pin-address lookups
(GET /geocode/reverse), 3% photo uploads (POST /photos/stage — the face-blur
path). Reports per-endpoint latency percentiles, throughput and errors.

    python scripts/loadtest.py --users 50 --seconds 30
    python scripts/loadtest.py --base http://localhost:8000 --users 200
"""

import argparse
import asyncio
import io
import random
import statistics
import time
import uuid
from collections import defaultdict

import httpx

TLV_BOX = (32.05, 32.11, 34.76, 34.81)  # lat_min, lat_max, lng_min, lng_max


def _point() -> tuple[float, float]:
    return random.uniform(TLV_BOX[0], TLV_BOX[1]), random.uniform(TLV_BOX[2], TLV_BOX[3])


def _test_jpeg() -> bytes:
    """A phone-camera-sized (1280×960) noisy JPEG for the upload path."""
    try:
        from PIL import Image  # noqa: PLC0415

        img = Image.effect_noise((1280, 960), 64).convert("RGB")
        buf = io.BytesIO()
        img.save(buf, "JPEG", quality=85)
        return buf.getvalue()
    except ImportError:
        raise SystemExit("pip install pillow (needed to generate the upload photo)") from None


async def _login(client: httpx.AsyncClient) -> dict[str, str]:
    resp = await client.post(
        "/auth/dev-login",
        json={"auth_provider": "dev", "external_id": f"load-{uuid.uuid4()}", "display_name": "Load tester"},
    )
    resp.raise_for_status()
    return {"Authorization": f"Bearer {resp.json()['access_token']}"}


async def _user(client, headers, photo, deadline, stats, failures, sizes, only=None):
    while time.monotonic() < deadline:
        roll = {"spots": 0.0, "geocode": 0.9, "photos": 0.99}.get(only, random.random())
        lat, lng = _point()
        if roll < 0.85:
            name, call = "GET /spots", client.get("/spots", params={"lat": lat, "lng": lng, "radius_m": 1500})
        elif roll < 0.97:
            name, call = "GET /geocode/reverse", client.get("/geocode/reverse", params={"lat": lat, "lng": lng})
        else:
            name = "POST /photos/stage"
            call = client.post("/photos/stage", files={"file": ("p.jpg", photo, "image/jpeg")}, headers=headers)
        started = time.monotonic()
        try:
            resp = await call
            ok = resp.status_code < 400
            if not ok:
                failures[f"{name} → HTTP {resp.status_code}"] += 1
            sizes[name].append(len(resp.content))
        except httpx.HTTPError as e:
            ok = False
            failures[f"{name} → {type(e).__name__}"] += 1
        stats[name].append(((time.monotonic() - started) * 1000, ok))
        await asyncio.sleep(random.uniform(0.2, 0.6))  # think time between taps


def _pct(values, p):
    return statistics.quantiles(values, n=100)[p - 1] if len(values) >= 2 else (values[0] if values else 0)


async def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8000")
    ap.add_argument("--users", type=int, default=50)
    ap.add_argument("--seconds", type=int, default=30)
    ap.add_argument("--only", choices=["spots", "geocode", "photos"], help="hit one endpoint only")
    args = ap.parse_args()

    photo = _test_jpeg()
    limits = httpx.Limits(max_connections=args.users + 10)
    async with httpx.AsyncClient(base_url=args.base, timeout=30, limits=limits) as client:
        headers = await _login(client)
        stats: dict[str, list[tuple[float, bool]]] = defaultdict(list)
        failures: dict[str, int] = defaultdict(int)
        sizes: dict[str, list[int]] = defaultdict(list)
        deadline = time.monotonic() + args.seconds
        await asyncio.gather(
            *(_user(client, headers, photo, deadline, stats, failures, sizes, args.only) for _ in range(args.users))
        )

    total = sum(len(v) for v in stats.values())
    errors = sum(1 for v in stats.values() for _, ok in v if not ok)
    print(f"\n{args.users} users × {args.seconds}s → {total} requests, {total / args.seconds:.0f} req/s, "
          f"{errors} errors ({100 * errors / max(total, 1):.1f}%)")
    print(f"{'endpoint':24} {'count':>6} {'p50 ms':>8} {'p95 ms':>8} {'p99 ms':>8} {'errors':>7}")
    for name, rows in sorted(stats.items()):
        lat = [ms for ms, _ in rows]
        print(f"{name:24} {len(rows):6} {_pct(lat, 50):8.0f} {_pct(lat, 95):8.0f} {_pct(lat, 99):8.0f} "
              f"{sum(1 for _, ok in rows if not ok):7}   avg body {statistics.mean(sizes[name] or [0]) / 1024:.0f} KB")
    for reason, count in sorted(failures.items(), key=lambda kv: -kv[1]):
        print(f"  failure: {reason} ×{count}")


if __name__ == "__main__":
    asyncio.run(main())
