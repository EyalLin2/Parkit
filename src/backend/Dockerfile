FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 8000

# Shell form (not exec-array) so $PORT expands — docker-compose never sets
# it (falls back to 8000, unchanged), but Render's Docker runtime does.
CMD uvicorn main:app --host 0.0.0.0 --port ${PORT:-8000}
