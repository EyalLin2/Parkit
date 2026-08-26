# ADR-0004: MVP technology stack — Python/FastAPI, AWS, PostgreSQL+PostGIS

## Status
Accepted

## Context
`docs/PRODUCT_SPEC.md` and ADR-0003 (core domain model) are settled.
ROADMAP Phase 1 calls for a minimal API and containerized app; Phase
2 calls for Terraform modules; Phase 4 calls for Kubernetes. All of
that needs one concrete stack and one cloud target to build against,
chosen now so infra work doesn't start on an undecided foundation.

## Decision
- **Backend**: Python + FastAPI.
- **Database**: PostgreSQL with the **PostGIS** extension — the
  seeker search (`PRODUCT_SPEC.md` → Seeker Experience) is a live
  radius query around the user's GPS position; PostGIS gives that as
  a built-in `ST_DWithin` query instead of hand-rolled distance math,
  and the rest of the domain (User/Spot/SpotFeedback, ADR-0003) is
  relational by nature.
- **Cloud**: AWS — also the target for the Terraform/Kubernetes work
  already scaffolded in `infra/` per the roadmap.
- **Photo moderation** (plate/face blur from `PRODUCT_SPEC.md` →
  Reporting a Spot): a cloud vision API (AWS Rekognition) rather than
  a self-hosted model — automatic, no GPU infra to run for MVP.

## Alternatives Considered
- **Node.js + TypeScript** — arguably the fastest path to an MVP API
  with strong AWS support; not chosen because Python/FastAPI better
  serves what this project is for (practice + interview relevance),
  and pairs more naturally if AI/vision work ever gets pulled
  in-house later.
- **Go** — better raw performance and a strong DevOps-interview
  signal, but a slower path to a working MVP than FastAPI's batteries
  (Pydantic validation, auto-generated OpenAPI docs).
- **GCP** — stronger native Vision AI offering, but AWS carries more
  weight for the DevOps roles this project is meant to demonstrate
  for.
- **MongoDB** — faster to start with schema-less documents, but
  rejected: the domain is relational (ADR-0003) and the core seeker
  feature is a geospatial query, which Postgres/PostGIS handles
  natively.
- **Self-hosted blur model** (e.g. YOLO-based) — more infra to show
  off (GPU nodes on K8s), but too much upfront effort for an MVP;
  worth revisiting later as a stretch item once the app works
  end-to-end.

## Consequences
- FastAPI + Pydantic models map directly onto the ADR-0003 entities,
  and give a free OpenAPI spec for the native app to consume.
- Committing to AWS now means the Phase 2/4 Terraform and Kubernetes
  work has one clear target instead of staying provider-agnostic.
- Rekognition adds an external network dependency and a per-photo
  cost at report time — acceptable at MVP scale, and consistent with
  the "AI/automation removes steps" design principle.
- PostGIS requires the RDS Postgres instance to have the extension
  enabled — a one-line addition to the eventual Terraform module, not
  a blocker.

## Related
- Refs: `docs/PRODUCT_SPEC.md`, ADR-0003, ROADMAP Phase 1/2/4
