from sqlalchemy import text
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine

from config import postgres_ssl, settings

# TLS for hosted Postgres, plain on a private network — see config.postgres_ssl.
ssl_mode = postgres_ssl()
connect_args = {"ssl": ssl_mode} if ssl_mode else {}
# Per worker process. The default (5 + 10 overflow) left requests queueing for
# a connection under load while the CPU sat at ~17%; 4 workers × (10 + 10)
# stays under Postgres's default max_connections of 100.
engine = create_async_engine(
    settings.database_url,
    pool_pre_ping=True,
    pool_size=settings.db_pool_size,
    max_overflow=settings.db_pool_size,
    connect_args=connect_args,
)
AsyncSessionLocal = async_sessionmaker(engine, expire_on_commit=False)


async def get_db():
    async with AsyncSessionLocal() as session:
        yield session


async def check_postgres() -> bool:
    async with engine.connect() as conn:
        await conn.execute(text("SELECT 1"))
    return True
