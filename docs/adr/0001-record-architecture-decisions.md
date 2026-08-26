# ADR-0001: Record architecture decisions with ADRs

## Status
Accepted

## Context
ParkIt is built as a personal DevOps showcase project intended to be
discussed in depth during job interviews. Without a written record,
the reasoning behind technical choices (why a tool, pattern, or
provider was chosen over alternatives) fades over time and becomes
hard to reconstruct months later.

## Decision
We will record every non-trivial architectural or technical decision
as an Architecture Decision Record (ADR) in `docs/adr/`, using the
template in `docs/adr/template.md`. Files are numbered sequentially
(`0001-...`, `0002-...`) and are never deleted — superseded decisions
are marked as such and linked to their replacement.

## Alternatives Considered
- **No formal record, rely on commit messages** — commit messages
  explain *what* changed, but rarely capture the *alternatives* that
  were rejected and why. Insufficient for interview-depth explanations.
- **A single running design-notes document** — harder to navigate
  once decisions accumulate, and doesn't map cleanly to a single
  linkable unit that a commit or PR can reference.

## Consequences
- Every decision has a stable, linkable identifier (e.g. `ADR-0003`)
  that commit messages and PRs can reference.
- Slight overhead per decision (writing the ADR), which is acceptable
  given the project's explicit goal of interview-readiness.

## Related
- Refs: ROADMAP.md Phase 0
