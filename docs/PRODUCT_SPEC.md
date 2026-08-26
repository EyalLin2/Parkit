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

## Design Principles

Every screen and interaction is judged against these:

- **Fast over feature-rich.** The core use cases (report a spot, find
  a spot) happen in seconds, often one-handed, often mid-errand —
  every added tap or field has to earn its place.
- **AI and automation remove steps, they don't add screens.** GPS
  auto-pin, AI photo blur, and the driving-safety gate all exist so
  the user does *less*, not so the app looks smarter. Automation
  should be invisible when it works.
- **Modern, not maximalist.** Visual polish (motion, typography,
  color) is welcome, but never at the cost of clarity — a first-time
  user should understand the map and the two core actions (report /
  find) with zero onboarding beyond the one permissions screen
  already defined.
- This isn't a new direction — it's already the tiebreaker behind
  every earlier decision in this doc: a cold-start nudge instead of
  an empty state, a reopen banner instead of push infrastructure,
  cancel-within-2-minutes instead of a confirmation dialog. Any
  future feature gets held to the same bar.

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
- Before requesting GPS/camera/phone permissions, a short one-screen
  explainer says why each is needed — kept brief, not a multi-step
  tutorial, just enough to raise permission opt-in and not feel
  invasive.

## Reporting a Spot

- Location is auto-captured from GPS and shown as a pin the reporter
  can drag to fine-tune before submitting — GPS in dense areas can be
  off by 10–20m.
- A report is created at the moment the reporter is actually leaving,
  not as a future prediction — this keeps the age-based decay
  (Hot/Warm/Cold, see below) accurate.
- **Reporting is blocked while the phone detects the car is moving**
  (speed/GPS-based) — the report screen only becomes available once
  the vehicle has actually stopped. This is a should-have, not a
  hard MVP blocker: if it proves too costly to build reliably, we
  drop it rather than delay the release. The block applies **only to
  creating a new report** — viewing the map, claiming a spot, and
  navigating stay usable while driving, since that's the seeker's
  primary use case.
- A reporter can **cancel their own report within 2 minutes** of
  submitting it, in case of a misclick or wrong pin — as long as no
  one has claimed it yet. A canceled report doesn't count toward the
  rate-limiting cooldown (below) — it's as if nothing was reported.
- Photo is optional. If attached, it's compressed/resized on-device
  before upload (keeps uploads fast and storage cheap), then run
  through automatic AI blurring (plates + faces); the reporter sees a
  **preview confirming what was blurred** (or that nothing needed
  blurring) before tapping **Confirm & Send** — no photo goes out
  without the reporter seeing the blurred result first.
- **Photo retention.** The photo is deleted automatically the moment
  its spot leaves the active state (taken / flagged-removed /
  expired) — nobody needs it after that, so storage never grows
  unbounded without a separate cleanup job. Where/how photos are
  physically stored is an architecture decision, not a product one.
- **Duplicate reports.** A new report within ~15 meters of an existing
  active spot is treated as a refresh of that spot (resets its decay
  clock) rather than a new pin. The refresh bonus points only apply
  if the refresher is a **different user** from the original
  reporter — a self-refresh just resets the clock, no extra points,
  so a user can't farm points by re-reporting their own spot.
  **Ownership stays with the original reporter**: whatever the spot's
  final outcome (taken / flagged / expired), the points and
  reputation consequences apply to them, not the refresher, who gets
  only the one-time refresh bonus.

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
- **Known limitation, accepted for MVP:** two colluding users could
  farm points by one reporting a fake spot and the other immediately
  confirming "I took it." Not solved now — pattern detection (e.g.
  the same pair repeatedly confirming each other) is deferred to a
  later version; it doesn't block MVP.

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
- **Navigate** on a chosen spot hands off to Waze/Google Maps for
  turn-by-turn directions — ParkIt doesn't build its own navigation.
- **No push notifications for MVP.** Instead, reopening the app shows
  a one-time banner if something relevant happened to your own spot
  (claimed, flagged as false) since you last had it open. Push may
  replace or supplement this later — revisit once there's a reason
  to justify the realtime plumbing.

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

## Profile — "My Activity"

A user's own screen, showing:
- Current points and earned badges.
- Past reports with their outcome (taken / expired / flagged as
  false) — this is where points and badges actually become visible
  and meaningful, not just abstract numbers on a leaderboard.
