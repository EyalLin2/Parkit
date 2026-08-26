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

ParkIt ships as a **native mobile app** (iOS + Android), not a web
app — full, reliable access to GPS, camera, and push notifications
without browser permission friction.

The app ships in **Hebrew only** for MVP; UI strings are structured
so English can be added later without rework.

## User Roles & Access

There is a single user role. Every registered user can both **report**
a spot and **seek** a spot — these are actions, not separate account
types.

- Registration is lightweight — Google/Apple sign-in **plus phone
  number verification** (SMS code) — but mandatory for *any* use of
  the app, including just viewing the map. The phone check is an
  extra anti-abuse layer, since it's harder to script than an OAuth
  login alone.
- Anonymous/guest viewing is intentionally not supported: letting
  non-contributors see live spots for free removes the incentive to
  report, and the whole system depends on reciprocity.

## Reporting a Spot

- Location is auto-captured from GPS and shown as a pin the reporter
  can drag to fine-tune before submitting — GPS in dense areas can be
  off by 10–20m.
- A report is created at the moment the reporter is actually leaving,
  not as a future prediction — this keeps the age-based decay below
  accurate.
- Photo is optional. If attached, it's run through automatic AI
  blurring (plates + faces), and the reporter sees a **preview
  confirming what was blurred** (or that nothing needed blurring)
  before tapping **Confirm & Send** — no photo goes out without the
  reporter seeing the blurred result first.
- **Duplicate reports.** A new report within ~15 meters of an existing
  active spot is treated as a refresh of that spot (resets its decay
  clock) rather than a new pin; the second reporter still earns
  partial points.

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
- **Rate limiting.** A short cooldown (~2 minutes) applies between any
  two reports from the same user, to blunt rapid-fire spam — not a
  hard cap of one active report at a time. If the same user repeatedly
  reports the *same* location, that's treated as a stronger signal:
  they're temporarily blocked from reporting for about a minute, on
  top of the general cooldown.

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
- **No push notifications for MVP** — the map refreshes when opened;
  real-time alerts for newly reported spots nearby are a post-MVP
  addition, once there's a reason to justify the realtime plumbing.

## Gamification & Leaderboard

- Users earn points and badges for reporting spots and for reports that
  get confirmed as accurate: **+10** for reporting a spot, a further
  **+5** bonus if it's confirmed via "I took it" within 5 minutes, and
  **-5** for each `flagged_false` against a report. Badges are simple
  milestone thresholds (10 / 50 / 200 successful reports), not
  compound rules — exact numbers can be tuned later.
- The leaderboard is **national** — one ranking across all users, not
  scoped per city. Keeps ranking logic simple and avoids needing to
  track each user's home city.
- The leaderboard **resets weekly**, so new users have a fair shot at
  ranking. Points and badges themselves are cumulative and never reset.
