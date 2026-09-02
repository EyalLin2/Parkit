"""Test fixtures.

Tests run against real Postgres/PostGIS + Redis (same services as dev,
different database/db-index), matching how this project is verified
everywhere else — no mocked persistence layer.
"""

import os
import uuid

if os.environ.get("GITHUB_ACTIONS") != "true":
    # Inside the backend dev container, DATABASE_URL/REDIS_URL are already
    # set in the environment (docker-compose env_file) to the real dev
    # database — setdefault would silently no-op there and the autouse
    # cleanup fixture below would then wipe every row in the dev database
    # after each test. CI sets its own already-correct test URLs (see
    # backend-ci.yml), so leave those alone rather than overriding with
    # docker-compose hostnames that don't resolve on the runner.
    os.environ["DATABASE_URL"] = "postgresql+asyncpg://parkit:parkit_dev_password@db:5432/parkit_test"
    os.environ["REDIS_URL"] = "redis://redis:6379/1"
os.environ.setdefault("JWT_SECRET", "test-secret")

import asyncpg
import pytest_asyncio
from httpx import ASGITransport, AsyncClient

from config import settings
from database import engine
from media import MEDIA_DIR
from models import Base
from redis_client import redis_client


def _admin_dsn() -> str:
    base = settings.database_url.replace("+asyncpg", "")
    return base.rsplit("/", 1)[0] + "/postgres"


def _db_name() -> str:
    return settings.database_url.rsplit("/", 1)[1]


@pytest_asyncio.fixture(scope="session", loop_scope="session", autouse=True)
async def _test_database():
    conn = await asyncpg.connect(_admin_dsn())
    try:
        exists = await conn.fetchval("SELECT 1 FROM pg_database WHERE datname = $1", _db_name())
        if not exists:
            await conn.execute(f'CREATE DATABASE "{_db_name()}"')
    finally:
        await conn.close()

    conn = await asyncpg.connect(_admin_dsn().rsplit("/", 1)[0] + f"/{_db_name()}")
    try:
        await conn.execute("CREATE EXTENSION IF NOT EXISTS postgis")
    finally:
        await conn.close()

    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    await engine.dispose()  # drop the pool bound to this fixture's own loop

    yield

    await engine.dispose()


@pytest_asyncio.fixture(autouse=True)
async def _clean_state():
    # Each test runs in its own event loop (pytest-asyncio function scope),
    # but `engine` / `redis_client` are process-wide singletons imported by
    # the app modules — their connection pools must be torn down after every
    # test or the next test's loop reuses connections bound to a dead loop.
    yield
    assert _db_name().endswith("_test"), (
        f"refusing to wipe non-test database {_db_name()!r} — DATABASE_URL is misconfigured"
    )
    async with engine.begin() as conn:
        for table in reversed(Base.metadata.sorted_tables):
            await conn.execute(table.delete())
    await engine.dispose()
    await redis_client.flushdb()
    await redis_client.aclose()
    for f in MEDIA_DIR.glob("*.jpg"):
        f.unlink()


@pytest_asyncio.fixture
async def client():
    from main import app

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac


@pytest_asyncio.fixture
def make_user(client):
    async def _make(name: str = "tester") -> tuple[str, dict[str, str]]:
        resp = await client.post(
            "/auth/dev-login",
            json={
                "auth_provider": "dev",
                "external_id": f"ext-{name}-{uuid.uuid4()}",
                "display_name": name,
            },
        )
        data = resp.json()
        return data["user_id"], {"Authorization": f"Bearer {data['access_token']}"}

    return _make
