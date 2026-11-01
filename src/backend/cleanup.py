"""Housekeeping that must not wait for someone to open the map.

Every couple of minutes: expire spots whose time ran out (and delete their
photos), and wipe free-text notes older than 90 days. Old spots themselves
stay: badges, "drivers helped" and the forecasts are counted from them.
Safe to run in every worker at once — each step is idempotent.
"""

import asyncio
import logging

from sqlalchemy import text

from database import AsyncSessionLocal
from routers.spots import _reconcile_spot_states

log = logging.getLogger("parkit.cleanup")

CLEANUP_EVERY_S = 120
NOTE_RETENTION_DAYS = 90


async def run_once() -> None:
    async with AsyncSessionLocal() as db:
        await _reconcile_spot_states(db)
        await db.execute(
            text(
                "UPDATE spots SET note = NULL "
                "WHERE note IS NOT NULL AND reported_at < now() - make_interval(days => :days)"
            ),
            {"days": NOTE_RETENTION_DAYS},
        )
        await db.commit()


async def loop() -> None:
    while True:
        try:
            await run_once()
        except Exception:  # a bad pass must not kill the loop
            log.exception("cleanup pass failed")
        await asyncio.sleep(CLEANUP_EVERY_S)
