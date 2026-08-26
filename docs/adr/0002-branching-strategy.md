# ADR-0002: Use trunk-based development with short-lived feature branches

## Status
Accepted

## Context
ParkIt is a solo project with no continuous production releases and
no multi-team release-train coordination needs. We still want a
disciplined, explainable branching model that reflects real-world
industry practice, since the workflow itself is part of what will be
discussed in interviews.

## Decision
We use trunk-based development:
- `main` is always stable and deployable; every commit on `main`
  arrived via a reviewed pull request.
- Work happens on short-lived branches: `feature/<topic>`,
  `fix/<topic>`, `docs/<topic>`.
- Branches are merged back into `main` via **squash merge**, so
  `main` history stays linear and each entry corresponds to one
  complete, reviewable unit of work.
- Commit messages follow the Conventional Commits format.

## Alternatives Considered
- **Git Flow (develop/release/hotfix branches)** — designed for
  projects with scheduled release trains and multiple environments
  needing independent stabilization. Overkill for a single-developer
  project with no versioned releases; would add ceremony without
  benefit, and it's less commonly used in modern CI/CD-first teams.
- **Committing directly to `main`** — no branch discipline. Rejected
  because it removes the PR checkpoint, which is exactly the
  mechanism used to keep `main` history clean and reviewable.

## Consequences
- History on `main` stays linear and readable — good for walking an
  interviewer through project evolution.
- Squash merging means in-branch commit hygiene (typos, WIP commits)
  doesn't matter — only the final PR title/description need to be
  clean.
- No support for maintaining multiple parallel release versions —
  acceptable, since ParkIt has no such requirement.

## Related
- Refs: CONTRIBUTING.md
