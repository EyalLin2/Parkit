# Contributing / Working Agreement

This is a solo showcase project, but it follows real team conventions
on purpose — see `docs/adr/0002-branching-strategy.md` for the reasoning.

## Branching

- `main` — always stable, deployable. Protected: changes land via PR.
- `feature/<topic>` — new functionality (e.g. `feature/terraform-vpc-module`).
- `fix/<topic>` — bug fixes.
- `docs/<topic>` — documentation / ADR-only changes.

Branches are merged into `main` via **squash merge**.

## Commit Messages — Conventional Commits

```
<type>(<scope>): <short description>

[optional body — explain WHY, not WHAT]

[optional footer — e.g. Refs: ADR-0003]
```

Common types: `feat`, `fix`, `docs`, `refactor`, `chore`, `ci`, `infra`.

Example:

```
feat(terraform): add VPC module with public/private subnets

Chose a 2-AZ setup over single-AZ to demonstrate HA awareness
without over-provisioning cost for a demo project.

Refs: ADR-0002
```

## Architecture Decisions

Any non-trivial technical or architectural choice gets an ADR in
`docs/adr/`, using `docs/adr/template.md`. Reference the relevant
ADR number in the commit/PR that implements it.
