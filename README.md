# ParkIt

ParkIt is a DevOps-focused showcase project: a parking-management
system used as a vehicle to demonstrate infrastructure-as-code,
CI/CD, containerization, and observability practices end-to-end.

## Status

Early stage — see [ROADMAP.md](./ROADMAP.md) for the current phase.

## Repository structure

```
.github/workflows/   CI/CD pipelines
docs/adr/             Architecture Decision Records
docs/architecture/    Diagrams
docs/runbooks/        Operational runbooks
infra/terraform/      Infrastructure as code
infra/kubernetes/     Kubernetes manifests / Helm charts
src/                  Application source code
scripts/              Helper scripts
```

## Working agreement

Branching strategy and commit conventions are documented in
[CONTRIBUTING.md](./CONTRIBUTING.md). Architectural decisions are
recorded in [docs/adr/](./docs/adr/).
