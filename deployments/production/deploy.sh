#!/usr/bin/env bash
# One deploy, in the safe order: check settings, back up, build, migrate, start,
# verify. Stops at the first failure, with the old version still running until
# the migration step. Run on the server: ./deploy.sh [branch]   (default: main)
set -euo pipefail
cd "$(dirname "$0")"

for name in JWT_SECRET POSTGRES_PASSWORD; do
  grep -q "^$name=." .env || { echo "$name is missing from .env, nothing was changed"; exit 1; }
done
if grep -qE '^JWT_SECRET=dev-secret-change-me' .env; then
  echo "JWT_SECRET is still the default, nothing was changed"; exit 1
fi

git fetch origin
git switch "${1:-main}"
git pull --ff-only

bash ./backup.sh
docker compose build backend
docker compose run --rm --no-deps backend alembic upgrade head
docker compose up -d backend

for _ in $(seq 1 30); do
  if docker compose exec -T backend python -c "import urllib.request as u; u.urlopen('http://localhost:8000/healthz')" 2>/dev/null; then
    docker compose ps
    echo "deploy ok: $(git log --oneline -1)"
    exit 0
  fi
  sleep 2
done
echo "backend didn't become healthy, see: docker compose logs --since 5m backend"
exit 1
