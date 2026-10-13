# ParkIt error codes

Every error a user sees ends with a code, e.g. *"Check your connection and try again. (PK-100)"*.
Testers quote the code; the app also attaches the last 10 codes to feedback automatically.
Feedback and crash reports land in the tester inbox: `https://<server>/admin/feedback?token=<ADMIN_TOKEN>`.

| Code | What the user sees | What it means | What to do |
| --- | --- | --- | --- |
| PK-100 | Check your connection and try again | No internet / server unreachable | User: check connection. Us: if many report it, check the server is up (`/healthz`) |
| PK-101 | The server is taking too long | Request timed out | Us: check server load (CPU, DB), recent deploy |
| PK-400 | Something went wrong | The app sent a request the server didn't accept | Us: app/server version mismatch — check logs for the endpoint |
| PK-401 | Your session ended — sign in again | Token expired (30 min idle) or account deleted | Expected; user signs in again |
| PK-403 | Reporting is paused for a while | Several of the user's reports were flagged false | Expected (anti-abuse); lifts after an hour |
| PK-404 | That spot isn't there anymore | Spot expired, was taken or deleted | Expected |
| PK-409 | Someone else got there first | Another driver claimed / voted at the same time | Expected |
| PK-413 | That photo is too large | Upload over 8 MB | Us: shouldn't happen (app scales photos to 1600 px) — investigate |
| PK-422 | Some details aren't valid | Bad phone / email / form field | User fixes input |
| PK-429 | Too many attempts — wait a minute | Rate limit (240 req/min, SMS/email resend, feedback) | Expected; if many users hit it, raise `RATE_LIMIT_PER_MINUTE` |
| PK-500 | Something went wrong on our side | Server error (bug) | Us: check backend logs at that time — always a bug to fix |
| PK-503 | This service isn't available right now | SMS / email provider failed to send | Us: check provider credentials / quota |
| PK-900 | Something went wrong | Unexpected error inside the app | Us: look for a crash report in the inbox around that time |

**Crash reports** (kind `crash` in the inbox) are sent automatically on the next launch after the app crashes,
with the stack trace, app version, phone model and the last error codes.
