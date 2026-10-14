# Runbook: deploy the backend

Production is one Docker Compose stack in `~/Parkit/deployments/production` on the Oracle Cloud VM.

## Deploy

```bash
ssh ubuntu@<server>
cd ~/Parkit
git fetch origin && git switch main && git pull --ff-only
cd deployments/production
docker compose up -d --build backend        # rebuild and restart the API only
docker compose exec backend alembic upgrade head
```

## Verify

```bash
curl -fsS https://<domain>/healthz          # {"status":"ok","postgres":"ok","redis":"ok"}
docker compose ps                           # every service "healthy" / "running"
docker compose logs --since 5m backend | grep -iE "error|traceback" || echo "no errors"
```

Then open the app on a phone: the map loads spots and a report goes through.

## Roll back

```bash
cd ~/Parkit
git log --oneline -5                        # find the previous good commit
git switch --detach <commit>
cd deployments/production && docker compose up -d --build backend
```

If the bad release included a migration, run `alembic downgrade -1` **before** switching code back, and
take a fresh backup first (`./backup.sh`).

## Settings

Secrets and settings live in `deployments/production/.env` on the server, never in git
(`.env.production.example` lists them). After editing `.env`: `docker compose up -d backend`.
