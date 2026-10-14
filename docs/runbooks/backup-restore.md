# Runbook: backup and restore

## What runs

`deployments/production/backup.sh`, from cron every night at 03:15 UTC:

```
15 3 * * * /home/ubuntu/Parkit/deployments/production/backup.sh >> /home/ubuntu/backups/backup.log 2>&1
```

It writes `~/backups/parkit-YYYY-MM-DD.sql.gz` (a full `pg_dump`) and deletes dumps older than 14 days.
Photos are not backed up on purpose: they are temporary and deleted when a spot closes.

**Limitation:** the dumps sit on the same VM. They protect against a bad migration or a mistake, not
against losing the server. Copying them to object storage is the next step.

## Check that backups work

```bash
tail -3 ~/backups/backup.log                # "backup ok" with today's date
ls -lh ~/backups | tail -3                  # sizes should be similar day to day
```

## Restore

Into a scratch database first, to inspect it without touching production:

```bash
cd ~/Parkit/deployments/production
docker compose exec db createdb -U "$POSTGRES_USER" restore_check
gunzip -c ~/backups/parkit-<date>.sql.gz | docker compose exec -T db psql -U "$POSTGRES_USER" -d restore_check
docker compose exec db psql -U "$POSTGRES_USER" -d restore_check -c "select count(*) from users"
```

Over production (stop the API so nothing writes during the restore):

```bash
./backup.sh                                 # snapshot of the current state, just in case
docker compose stop backend
docker compose exec db dropdb -U "$POSTGRES_USER" "$POSTGRES_DB"
docker compose exec db createdb -U "$POSTGRES_USER" "$POSTGRES_DB"
gunzip -c ~/backups/parkit-<date>.sql.gz | docker compose exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"
docker compose start backend
curl -fsS https://<domain>/healthz
```

The dump includes the PostGIS extension and the OpenStreetMap address tables, so nothing needs to be
re-imported.
