from sqlalchemy import text
from sqlalchemy.ext.asyncio import create_async_engine

from config import settings

engine = create_async_engine(settings.database_url, pool_pre_ping=True)


async def check_postgres() -> bool:
    async with engine.connect() as conn:
        await conn.execute(text("SELECT 1"))
    return True
