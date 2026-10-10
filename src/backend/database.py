from sqlalchemy import text
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine

from config import settings

# Hosted Postgres (e.g. Supabase) requires TLS; the local docker-compose
# postgis/postgis image doesn't have it configured at all, so this only
# applies outside local dev.
connect_args = {"ssl": "require"} if settings.app_env != "local" else {}
engine = create_async_engine(settings.database_url, pool_pre_ping=True, connect_args=connect_args)
AsyncSessionLocal = async_sessionmaker(engine, expire_on_commit=False)


async def get_db():
    async with AsyncSessionLocal() as session:
        yield session


async def check_postgres() -> bool:
    async with engine.connect() as conn:
        await conn.execute(text("SELECT 1"))
    return True
