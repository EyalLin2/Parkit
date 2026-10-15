#!/usr/bin/env bash
# Weekly refresh of OpenStreetMap data: addresses, streets and the paid car
# parks layer (new lots appear, closed ones disappear). Installed by cron:
#   0 4 * * 0 /home/ubuntu/Parkit/deployments/production/osm-refresh.sh >> /home/ubuntu/backups/osm-refresh.log 2>&1
#
# Safe to run any time: the import replaces the tables inside one
# transaction, so the app keeps serving the old data until the new set is in.
set -euo pipefail
cd "$(dirname "$0")"
echo "$(date -u +%FT%TZ) OSM refresh starting"
docker compose exec -T backend python import_osm_geodata.py </dev/null
echo "$(date -u +%FT%TZ) OSM refresh done"
