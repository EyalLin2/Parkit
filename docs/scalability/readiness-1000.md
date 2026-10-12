# Readiness for 1,000 users

_Load test and review of the current state — 2026-10-12. Re-run with `scripts/loadtest.py`._

## Bottom line

**Not ready yet, but close.** The current server peaks at about **85 requests/second** — roughly the
estimated peak demand of 1,000 users, with no safety margin. Past that point it does not slow down
gracefully: throughput drops, p95 latency reaches 7–10 s and connections are dropped.

Three things block a launch, all fixable in code at no cost except hosting:

1. **A fairness bug in penalties** — concurrent "already taken" flags can penalise (and block) a reporter several times for one report.
2. **Photos** — face-blurring blocks the whole server while it runs, and staged photos are held in Redis memory.
3. **Hosting** — the API runs on a laptop behind a temporary tunnel.

Addresses (our own geocoder, no rate limit) and maps (OpenFreeMap, no request limit) are already ready.

## How much load it handles

| Concurrent test users | Requests/s | p50 `GET /spots` | p95 `GET /spots` | Errors |
| ---: | ---: | ---: | ---: | --- |
| 10 | 23 | 15 ms | 97 ms | 0 |
| 25 | 55 | 14 ms | 263 ms | 0 |
| 50 | **87** (peak) | 89 ms | 606 ms | 0 |
| 100 | 77 | 672 ms | 2.3 s | 0 |
| 200 | 64 | 2.0 s | 6.9 s | 0.7% dropped connections |
| 300 | 67 | 3.9 s | 8.5 s | 0.2% dropped connections |

At 100 users the API process used ~350% CPU and Postgres ~250% CPU.

## Readiness by area

| Area | State | What we saw | Needed for 1,000 users |
| --- | --- | --- | --- |
| Server capacity | Partial | ~85 req/s peak, one process | Several workers, 2–3× headroom |
| Stability under load | Not ready | Throughput falls and connections drop past the peak | Overload should slow down, not collapse |
| Correctness | Not ready | Double penalties, duplicate spots under concurrency | Fix both + concurrency tests |
| Photos | Not ready | Blur blocks the server; staged photos in Redis (630 MB in the test) | Blur off the request path, smaller photos, disk/object storage |
| Hosting | Not ready | Laptop + free tunnel | Always-on cloud host |
| Database | Partial | Fast today, but every search runs full-table UPDATEs; expired spots are never removed | Background expiry, indexes, history cleanup |
| Security / sign-in | Not ready | Name-only sign-in; no rate limit on searches and uploads | Real sign-in, rate limits |
| Monitoring / backups | Not ready | No alerts, crash reports or backups | Uptime check, crash reports, daily backup |
| Payload size | Partial | Each map move downloads ~145 KB (every spot in the radius) | Cap results, gzip |
| Addresses | Ready | Self-hosted geocoder, 8–35 ms | Re-run the import after deploy |
| Maps | Ready | OpenFreeMap, no request limit | — |
| Cost | Ready | Everything is free so far | Stay on durable free tiers |

## Bugs found

| # | Bug | Severity |
| --- | --- | --- |
| 1 | Concurrent "already taken" flags apply the false-report penalty more than once; can block a reporter after a single report | Critical |
| 2 | Concurrent reports at the same place create duplicate spots (and award points for each) | Medium |
| 3 | Photo blurring runs on the request path and stalls every other request (2 ms → 543 ms with 6 uploads) | High |
| 4 | Past ~85 req/s throughput falls and connections are dropped instead of degrading gracefully | High |
| 5 | Staged photos are stored in Redis (~1.5 MB each, kept 10 min even if abandoned), with no memory cap | High |
| 6 | `GET /spots` returns every spot in the radius with no cap (~145 KB per map move) | Medium |
| 7 | Every search runs expiry UPDATEs over the whole table; expired spots are never deleted | Medium |

Verified correct under concurrency: 10 users claiming the same spot at once → exactly one wins.

## Fixes, in order (all free)

1. Row/location locking for feedback and reports → bugs 1, 2
2. Blur in a worker thread + downscale photos on the phone before upload → bugs 3, 5
3. Stage photos on disk, not Redis; drop the base64 preview from the response → bug 5
4. Background expiry job + index on `(status, reported_at)` → bug 7
5. Cap results to the nearest 50 + gzip → bug 6
6. Multiple server workers → bug 4
7. Per-user / per-IP rate limits
8. Concurrency tests and this load test in the repo

## Free hosting options (checked 2026-10-12)

| Option | Free allowance | What breaks |
| --- | --- | --- |
| [Render](https://render.com/docs/free) | 750 h/month | Sleeps after 15 min idle; ~1 min cold start |
| [Supabase](https://supabase.com/pricing) | 500 MB DB, 1 GB files, 5 GB egress | Paused after 1 week of inactivity (our DB is 88 MB) |
| [Upstash](https://upstash.com/pricing/redis) | 250 MB, 500K commands/month | 500K commands/month is likely too few for 1,000 users (estimate) |
| [Oracle Always Free](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm) | 2 ARM cores, 12 GB RAM, 200 GB disk, 10 TB egress | Idle instances can be reclaimed; probably needs a card to sign up |

## Method

Local machine (Docker, 7.6 GB RAM), one dev-mode server process, local Postgres + Redis, load generator on
the same machine. 1,500 active spots across Tel Aviv. Each virtual user: 85% map searches, 12% address
lookups, 3% photo uploads (1280×960), 0.2–0.6 s think time — deliberately aggressive. Steps of 10–300
concurrent users, 25 s each. Assumed 1,000-user peak: 10–15% active, one request every 2–3 s while
moving the map ≈ 35–75 req/s (to be measured once search events are logged). A free cloud host will be
weaker on CPU than this machine; repeat the test there after the fixes.
