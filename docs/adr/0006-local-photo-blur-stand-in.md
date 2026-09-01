# ADR-0006: Local stand-in for the photo blur pipeline

## Status
Accepted

## Context
`docs/PRODUCT_SPEC.md` requires that an attached report photo be run
through automatic AI blurring (faces + license plates) before being
shown to other users, with the reporter seeing a confirm-before-send
preview of the blurred result. ADR-0004 named AWS Rekognition + S3 as
the intended MVP implementation, but building against them requires
real AWS credentials and a bucket, which aren't available yet — the
same blocker as real Google/Apple/SMS auth (see `routers/auth.py`'s
`dev-login`).

Rather than leave the whole photo flow unbuilt until those credentials
exist, this ADR follows the same precedent as `dev-login`: build the
full flow — staging upload, detection, blur, preview, confirm-and-attach,
lifecycle-tied deletion — against a local stand-in, clearly flagged as
such, with a single, well-defined swap point for the real thing later.

## Decision
- **Detection/blur (`blur.py`)**: OpenCV's bundled Haar cascade face
  detector (`opencv-python-headless`), Gaussian-blurred over each
  detected face region. **Faces only** — Rekognition has no dedicated
  license-plate API either (it would need `DetectText` + heuristics,
  per the plate caveat already documented to the user), so faking a
  plate detector here would misrepresent what the stand-in actually
  does. Swapping `blur_faces()` for a Rekognition `DetectFaces` call
  is a single-module change; every caller only sees "bytes in, blurred
  bytes + a face count out."
- **Storage (`media.py`)**: local disk under `src/backend/media/`
  (gitignored), served back via a FastAPI `StaticFiles` mount at
  `/media`. Stands in for S3 the same way — one module to swap later.
- **Upload flow**: `POST /photos/stage` runs the blur immediately and
  returns a base64 preview — matching the spec's "reporter sees the
  blurred result before Confirm & Send" requirement, which only makes
  sense if blurring happens *before* the report is created, not as
  part of it. The blurred bytes are held in **Redis** (not on disk) as
  the value of `photo_staging:{staging_id}`, `EX 600` (10 min). `POST
  /spots` consumes and deletes that key when attaching a
  `photo_staging_id` to a report; an expired or unknown id is a 400,
  not a silent no-op.
  - Storing the bytes as the Redis value (not a filesystem path) means
    an abandoned upload — staged but never attached — is cleaned up by
    Redis's own TTL expiry with nothing left on disk. No sweep job
    needed, consistent with ADR-0005.
- **Retention**: `media.delete_photo()` is called at every point a
  spot's photo should no longer exist — confirmed-taken, flagged-false
  removal, expiry (checked before the existing bulk-expire `UPDATE` in
  `_reconcile_spot_states`), and self-cancel — matching the
  Trust & Anti-Abuse photo-retention rule already in the spec.

## Alternatives Considered
- **Wait for real AWS credentials before building this at all** —
  rejected: blocks a fully-specced feature indefinitely on something
  outside this project's control, the same reasoning that motivated
  `dev-login`.
- **Fake plate blurring too** (e.g. blur a fixed region) — rejected:
  would misrepresent the stand-in as more capable than the real
  Rekognition-based pipeline actually would be without extra work.
- **Store staged photos on disk with a Redis key only as a marker** —
  rejected: leaves orphaned files needing a sweep for the abandoned-
  upload case, which storing the bytes *in* Redis avoids entirely.

## Consequences
- `requirements.txt` gains `opencv-python-headless` + `numpy` — a real
  runtime dependency of the stand-in, not a dev-only tool.
- The stand-in is prominently flagged (code docstrings, PR
  description, changelog) as not the real Rekognition pipeline, same
  as `dev-login` is flagged as not real provider auth.
- Swapping in Rekognition + S3 later touches `blur.py` and `media.py`
  only; `routers/photos.py` and the retention hooks in `routers/spots.py`
  are unaffected.

## Related
- Refs: `docs/PRODUCT_SPEC.md` (Reporting a Spot, Trust & Anti-Abuse),
  ADR-0004 (names Rekognition/S3), ADR-0005 (TTL-without-cron precedent)
