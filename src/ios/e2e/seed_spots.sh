#!/usr/bin/env bash
# Two other drivers report free spots near the test location, through the real API
# (dev-login exists only with APP_ENV=local). Usage: seed_spots.sh <run-label>
set -euo pipefail
api=http://localhost:8000
label="$1"

report() {
  local who="$1" lat="$2" lng="$3" extra="$4"
  local token
  token=$(curl -fsS "$api/auth/dev-login" -H 'Content-Type: application/json' \
    -d "{\"auth_provider\":\"dev\",\"external_id\":\"e2e-$label-$who\",\"display_name\":\"Seed $who\"}" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')
  curl -fsS "$api/spots" -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    -d "{\"lat\":$lat,\"lng\":$lng,\"spot_type\":\"street\",\"payment\":\"free\",\"vehicle_size\":\"regular\",\"reporter_lat\":$lat,\"reporter_lng\":$lng$extra}" >/dev/null
}

report a 32.08545 34.78190 ''
report b 32.08520 34.78160 ',"note":"Low branch at the back"'
report c 32.08510 34.78210 ''
echo "seeded spots for $label"
