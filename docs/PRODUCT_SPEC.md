# ParkIt – Product Spec

This document defines the functional requirements and business logic for
ParkIt's MVP. It intentionally stays out of architecture, database, and
DevOps decisions — see [ROADMAP.md](../ROADMAP.md) for how those get
sequenced.

## Overview

ParkIt is a crowdsourced app for finding and sharing free parking spots.
Any user leaving a spot can report it; any user looking for a spot can
find it nearby. Reports decay over time and are self-policed by the
community, so the map stays trustworthy without manual moderation.

## User Roles & Access

There is a single user role. Every registered user can both **report**
a spot and **seek** a spot — these are actions, not separate account
types.

- Registration is lightweight (e.g. Google/Apple/phone) but mandatory
  for *any* use of the app, including just viewing the map.
- Anonymous/guest viewing is intentionally not supported: letting
  non-contributors see live spots for free removes the incentive to
  report, and the whole system depends on reciprocity.

## Spot Lifecycle & States

A reported spot moves through four states based on age:

| State   | Age            |
|---------|----------------|
| Hot     | 0–5 minutes    |
| Warm    | 5–15 minutes   |
| Cold    | 15–30 minutes  |
| Expired | auto-removed at 30 minutes |

**Claiming a spot.** When a seeker commits to a spot, it is temporarily
locked (~2 minutes) so other seekers aren't routed to the same spot at
the same time.

**Removal before natural expiry.**
- **"I took it"** — the seeker who parked confirms it, the spot is
  removed immediately, and the original reporter is rewarded.
- **"Spot is taken"** — a seeker who arrives and finds it unavailable
  flags it as a false report. **2 flags** are enough to remove the spot
  immediately, without waiting for the 30-minute expiry.

## Trust & Anti-Abuse

- A report can optionally include a photo. Photos are run through an
  automatic AI blur step (license plates and faces) before being shown
  to other users — no manual moderation queue for MVP.
- Reporter reputation is tracked implicitly: a user who racks up 2–3
  false/bad reports in a row is temporarily blocked from reporting, or
  prompted for additional identity verification before reporting again.

## Seeker Experience

- The map always centers on the seeker's **current GPS location**,
  filtered by radius (e.g. 500m/1km/2km) — there is no manual city
  search. A user physically in Netanya cannot browse spots in Tel
  Aviv; they'd need to actually be there. This keeps search simple
  (a distance query, no city concept to maintain) and matches how
  the feature is actually used — finding a spot *right now*, not
  planning one remotely.
- Also filterable by:
  - Spot type: street / lot / disabled / EV charging
  - Free vs. paid
- **Cold start.** If no spots are found nearby, the app shows an
  encouraging prompt to be the first to report in that area, with a
  bonus-points incentive attached.

## Gamification & Leaderboard

- Users earn points and badges for reporting spots and for reports that
  get confirmed as accurate.
- The leaderboard is **national** — one ranking across all users, not
  scoped per city. Keeps ranking logic simple and avoids needing to
  track each user's home city.
- The leaderboard **resets weekly**, so new users have a fair shot at
  ranking. Points and badges themselves are cumulative and never reset.
