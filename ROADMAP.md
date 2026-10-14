# ParkIt – Roadmap

ParkIt is a crowdsourced free-parking app (FastAPI + PostGIS backend,
native Android client) and a DevOps showcase: a real product used to
demonstrate infrastructure-as-code, CI/CD, containerization, load
testing and observability end-to-end. Day-to-day work is tracked in
[GitHub issues](https://github.com/EyalLin2/Parkit/issues) by milestone.

## Phase 0 — Foundations
- [x] Repository structure & branching strategy
- [x] ADR process established
- [x] CI pipeline skeleton (lint, format checks)

## Phase 1 — Application MVP
- [x] Define core domain (parking spots, reservations, users)
- [x] Minimal API implementation
- [x] Containerize application (Dockerfile)

## Phase 1.5 — Ready for 1,000 users
- [x] Load test + readiness review ([docs/scalability/readiness-1000.md](docs/scalability/readiness-1000.md))
- [x] Self-hosted geocoder (no third-party rate limits)
- [ ] Concurrency, photo-pipeline and database fixes (milestone "Ready for 1,000 users")
- [x] Always-on free hosting (Oracle Cloud VM, Caddy HTTPS)

## Phase 2 — Infrastructure as Code
- [ ] Terraform modules (network, compute, database)
- [ ] Separate environments (dev/staging)

## Phase 3 — CI/CD
- [x] Automated build & test pipeline
- [x] Migrations, Docker image and Android build checked in CI
- [x] Dependabot dependency updates
- [ ] Automated deployment after CI passes, with health check and rollback
- [ ] Signed release builds from tags

## Phase 4 — Orchestration
- [ ] Kubernetes manifests / Helm chart
- [ ] Local cluster (kind/minikube) for demo purposes

## Phase 5 — Observability
- [ ] Logging
- [ ] Metrics & dashboards
- [ ] Alerting basics

## Phase 6 — Polish for presentation
- [x] Architecture diagrams
- [x] CI/CD guide and runbooks
- [x] README walkthrough
- [ ] LinkedIn write-up
