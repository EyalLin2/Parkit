# Changelog

All notable changes to ParkIt. Each entry links to the pull request with the details.
Format loosely follows [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased] — open pull requests

### Added
- Professional redesign of the Android app: vector maps (MapLibre + OpenFreeMap) with a custom light/dark
  style, a nearby-spots sheet sorted by distance, a rebuilt profile and a dedicated settings screen (#32)
- Spot address stored on the server; redesigned report and spot sheets (#33)
- Report from the map pin: a "Report a spot here" bubble on the center pin (#33)
- Self-hosted reverse geocoder on OpenStreetMap data in PostGIS, replacing public Nominatim (#34)
- The reporter's photo leads the spot card; photo thumbnails in the nearby list; full-screen viewer (#35)
- Load-test script and a readiness review for 1,000 users (`docs/scalability/readiness-1000.md`)

### Fixed
- The pin address no longer flashes raw coordinates while loading (#33)

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
