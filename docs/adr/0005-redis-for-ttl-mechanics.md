# ADR-0005: Use Redis for TTL-based mechanics

## Status
Accepted

## Context
`docs/PRODUCT_SPEC.md` defines several short-lived, time-boxed rules
that ADR-0003's Postgres-backed domain model isn't a natural fit for:
the ~2-minute claim lock, the ~2-minute rate-limit cooldown between
reports, and the ~1-minute block on repeated same-location reports.
Implementing these as Postgres rows would mean polling/expiring rows
with a cron sweep just to model something that's inherently "this
key is valid for N seconds."

## Decision
Add **Redis** to the stack as a key-value store for exactly this
class of TTL-based state:
- `claim:{spot_id}` → seeker id, TTL ~2 min
- `report_cooldown:{user_id}` → TTL ~2 min
- `same_spot_block:{user_id}:{geohash}` → TTL ~1 min

Redis's native key expiry (`EX`) replaces what would otherwise be
manual expiry logic in application code or the database.

## Alternatives Considered
- **Postgres rows with an `expires_at` column** — works, but needs a
  background sweep (or checking `expires_at` on every read) instead
  of relying on the store to expire keys itself; more moving parts
  for the same outcome.
- **In-process memory (per-instance dict/cache)** — rejected: doesn't
  survive a restart and doesn't work once there's more than one
  backend instance, which defeats the purpose the moment this is
  containerized/scaled.

## Consequences
- One more container/managed service to run (`redis:alpine` locally,
  presumably ElastiCache on AWS later) — acceptable, standard pairing
  with a web API.
- Postgres stays focused on durable domain data (ADR-0003); Redis
  only ever holds disposable, TTL'd state — losing it entirely just
  means locks/cooldowns reset, not data loss.

## Related
- Refs: `docs/PRODUCT_SPEC.md` (Claiming a spot, Rate limiting), ADR-0004
