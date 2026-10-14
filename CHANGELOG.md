# Changelog

All notable changes to ParkIt. Each entry links to the pull request with the details.
Format loosely follows [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

### Added
- Android CI: debug build and Android lint on every app change, APK and lint report as artifacts
- Backend CI: migrations replayed on an empty database (with a downgrade round-trip), production
  Docker image build, pip caching, cancel outdated runs; Dependabot for pip, Gradle, Docker, Actions
- CI/CD guide (`docs/ci-cd.md`) and runbooks for deploy and backup/restore

### Fixed
- The first migration now enables PostGIS itself, so migrations work on a brand-new database
  (a restore or a new server), not only on the one the Docker image prepared

## [0.3.5-beta] — 2026-10-14 (versionCode 8)

### Added
- Fixed-code sign-in for the Google Play reviewer, set only in the server's environment (#86)
- Optional "blue-white curb" (paid) tick when reporting (#86)
- "I parked here" remembers your car: walk back to it, and report your spot in one tap when leaving (#86)
- Empty-map state with car parks and "invite friends"; an explanation screen before the location prompt (#86)
- Privacy policy and terms links in Settings and on sign-in (#86)
- Nightly database backup script for production (#86)
- Reports must come from near the spot (150 m plus GPS accuracy); "go to my location" hint (#85)
- Report an offensive photo or user: the photo comes down at once and lands in the admin feed (#85)

### Changed
- Navigation opens Waze, then Google Maps, then any map app (#86)
- Addresses follow the app language; phone numbers shown as 050-123-4567 (#86)
- Release builds point at production by default (#85)

### Fixed
- The trip card survives screen rotation; English filter chips no longer overflow; a failed photo shows
  an icon instead of an empty box (#86)

## [0.3.4-beta] — 2026-10-14 (versionCode 7)
- Play package id `com.parkit.israel`; rebuilt after the first build pointed at the dev server (#84)

## [0.3.3-beta] — 2026-10-13 (versionCode 6) — first Google Play upload

Everything below was built as a stacked series of pull requests (#32–#83) and squash-merged as #84.

### Added
- Professional redesign: vector maps (MapLibre + OpenFreeMap) with a custom light/dark style, nearby
  sheet, new profile and settings (#32)
- Server-side addresses from a self-hosted OpenStreetMap geocoder in PostGIS (#33, #34)
- Reporter's photo leads the spot card; thumbnails in the list (#35)
- Reporter levels, points, illustrated badges, weekly city league, avatars (#61, #69–#72, #78)
- "On your way" trip card after claiming; driver profile with car size from the plate and disabled
  permit (#62, #63)
- Hot areas, fading pins for older reports (#64)
- Licence-plate blurring in photos (#65)
- Public car parks layer from OpenStreetMap (#66); hand-off to Pango after parking (#67)
- Sign-in with a 6-digit code by SMS or email, with HMAC-stored codes and rate limits (#73–#75)
- Store readiness: account deletion, Android 16 target, signed release builds (#76)
- Production stack: Caddy HTTPS, several API workers, bounded Redis (#77)
- In-app feedback, crash reports, error codes, onboarding intro (#79)
- 800 m search radius; beta sign-in codes shown in the app until email delivery is live (#82)

### Fixed
- The map opens at your real location (#60); location on real phones (#83)

## [0.1.0-demo] — 2026-10-11

### Android app
- Native Kotlin/Compose app with the core loop: report, search, claim, feedback, cancel (#9)
- Visual redesign, brand fonts, one-tap reporting with a mandatory blurred photo (#10–#13, #17–#19, #26, #28–#31)
- Hebrew support with an in-app language picker (#20); explicit dark mode and splash screen (#26)
- "My reports" history and share (#21); configurable backend URL for device builds (#22, #25)
- Graceful handling of expired sessions (#14)

### Backend
- Domain model and report / nearby-search endpoints on PostGIS (#2)
- Claim, feedback, cancel, JWT dev sign-in, rate limits (#3)
- Filters on `GET /spots` (#5); profile and weekly leaderboard (#6)
- Photo upload with a local face-blur stand-in (#7); minimal web demo at `/demo` (#8)
- Seed script, optional vehicle size; test suite no longer touches the dev database (#15)
- Deployment prep for Render + Supabase + Upstash (#27)

### Quality
- pytest suite and GitHub Actions CI (lint, format, tests) (#4)

## [0.0.1] — 2026-08-27
- Repository structure, product spec, ADRs, local Docker + FastAPI skeleton (#1)
