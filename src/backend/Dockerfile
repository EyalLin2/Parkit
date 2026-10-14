FROM python:3.14-slim

WORKDIR /app

# libexpat is the one native library pyosmium (the OSM geodata importer,
# import_osm_geodata.py) needs that python:slim doesn't ship.
RUN apt-get update && apt-get install -y --no-install-recommends libexpat1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 8000

# Production: several worker processes (one per CPU core is the usual
# rule) — a single process topped out at ~85 req/s in the load test.
# --proxy-headers so rate limits see the real client IP behind Caddy.
# Shell form so $PORT / $WEB_CONCURRENCY expand. Local dev overrides this
# with --reload in docker-compose.
CMD uvicorn main:app --host 0.0.0.0 --port ${PORT:-8000} --workers ${WEB_CONCURRENCY:-4} \
    --proxy-headers --forwarded-allow-ips "*"
