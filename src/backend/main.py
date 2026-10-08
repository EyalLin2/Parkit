import mimetypes
from pathlib import Path

from fastapi import FastAPI, Response
from fastapi.staticfiles import StaticFiles

from database import check_postgres
from media import MEDIA_DIR
from redis_client import check_redis
from routers.auth import router as auth_router
from routers.photos import router as photos_router
from routers.spots import router as spots_router
from routers.users import router as users_router

# Python's mimetypes module doesn't know this extension, so StaticFiles was
# serving .apk files as text/plain — harmless for curl, but real phone
# browsers can refuse to treat an unrecognized-content-type download as an
# installable package. Only matters for the ad-hoc /demo static mount below
# (used to hand out a debug build for testing), not the product itself.
mimetypes.add_type("application/vnd.android.package-archive", ".apk")

STATIC_DIR = Path(__file__).resolve().parent / "static"

app = FastAPI(title="ParkIt API")
app.include_router(auth_router)
app.include_router(spots_router)
app.include_router(users_router)
app.include_router(photos_router)
app.mount("/media", StaticFiles(directory=str(MEDIA_DIR)), name="media")
app.mount("/demo", StaticFiles(directory=str(STATIC_DIR), html=True), name="demo")


@app.get("/healthz")
async def healthz(response: Response):
    checks = {"postgres": "ok", "redis": "ok"}

    try:
        await check_postgres()
    except Exception:
        checks["postgres"] = "error"

    try:
        await check_redis()
    except Exception:
        checks["redis"] = "error"

    healthy = all(status == "ok" for status in checks.values())
    response.status_code = 200 if healthy else 503
    return {"status": "ok" if healthy else "error", **checks}
