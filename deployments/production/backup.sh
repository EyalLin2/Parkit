#!/usr/bin/env bash
# Nightly Postgres backup for the production stack. Installed by cron on the
# server (see README in this folder):
#   15 3 * * * /home/ubuntu/Parkit/deployments/production/backup.sh >> /home/ubuntu/backups/backup.log 2>&1
#
# Keeps the last 14 daily dumps in ~/backups. They live on the same machine,
# so this protects against bad migrations and mistakes, not against losing
# the server itself — copy them off the box for that.
set -euo pipefail

cd "$(dirname "$0")"

dest="$HOME/backups"
mkdir -p "$dest"
file="$dest/parkit-$(date -u +%Y-%m-%d).sql.gz"

# The db container has POSTGRES_USER/DB in its own environment, so .env isn't
# sourced here (a value like EMAIL_FROM=ParkIt <a@b> breaks a shell).
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner' | gzip > "$file.tmp"
mv "$file.tmp" "$file"

# Delete dumps older than 14 days.
find "$dest" -name 'parkit-*.sql.gz' -mtime +14 -delete

echo "$(date -u +%FT%TZ) backup ok: $file ($(du -h "$file" | cut -f1))"
