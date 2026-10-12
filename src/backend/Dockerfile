FROM python:3.12-slim

WORKDIR /app

# libexpat is the one native library pyosmium (the OSM geodata importer,
# import_osm_geodata.py) needs that python:slim doesn't ship.
RUN apt-get update && apt-get install -y --no-install-recommends libexpat1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 8000

# Shell form (not exec-array) so $PORT expands — docker-compose never sets
# it (falls back to 8000, unchanged), but Render's Docker runtime does.
CMD uvicorn main:app --host 0.0.0.0 --port ${PORT:-8000}
