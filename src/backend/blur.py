"""Local stand-in for the AWS Rekognition blur step (ADR-0004).

Blurs faces and licence plates before a photo is shown to anyone; the
original is never stored. It's a local OpenCV pipeline (no AWS needed), so
the whole photo flow can run and be tested end to end:

- Faces: OpenCV's frontal-face Haar cascade. Misses faces in profile.
- Plates: Israeli plates are yellow with dark characters, so the main
  detector looks for exactly that — a yellow region with plate proportions
  and dark marks inside — plus OpenCV's generic plate cascade as a backup
  for other plates. Over-blurring a yellow sign is acceptable; missing a
  plate is not, so the colour thresholds are deliberately loose.

The app shows the blurred preview before sending, so the reporter can see
what will be published. Swapping this module for Rekognition is a
single-module change.
"""

import cv2
import numpy as np

_FACE_CASCADE = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_frontalface_default.xml")
_PLATE_CASCADE = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_russian_plate_number.xml")
_BLUR_KERNEL = (51, 51)
MAX_SIDE_PX = 1280

# Israeli plate yellow in OpenCV HSV (H is 0–180).
_YELLOW_LO = np.array([15, 90, 90])
_YELLOW_HI = np.array([38, 255, 255])
_PLATE_ASPECT = (1.6, 6.5)  # 520×110 mm long plates ≈ 4.7; square rear plates ≈ 1.8
_MIN_AREA_FRACTION = 0.0004
_MAX_AREA_FRACTION = 0.12


def _yellow_plates(img: np.ndarray) -> list[tuple[int, int, int, int]]:
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    mask = cv2.inRange(hsv, _YELLOW_LO, _YELLOW_HI)
    # Close the gaps the dark characters cut into the yellow.
    mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_RECT, (15, 5)))
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

    total = img.shape[0] * img.shape[1]
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    found = []
    for c in contours:
        x, y, w, h = cv2.boundingRect(c)
        area = w * h
        if not (_MIN_AREA_FRACTION * total <= area <= _MAX_AREA_FRACTION * total):
            continue
        if not (_PLATE_ASPECT[0] <= w / max(h, 1) <= _PLATE_ASPECT[1]):
            continue
        if cv2.contourArea(c) / area < 0.5:  # plates are solid rectangles, not streaks
            continue
        dark = np.count_nonzero(gray[y : y + h, x : x + w] < 90) / area
        if not (0.03 <= dark <= 0.5):  # a plate has characters on it
            continue
        found.append((x, y, w, h))
    return found


def _overlap(a: tuple[int, int, int, int], b: tuple[int, int, int, int]) -> float:
    """Intersection as a share of the smaller box."""
    ix = max(0, min(a[0] + a[2], b[0] + b[2]) - max(a[0], b[0]))
    iy = max(0, min(a[1] + a[3], b[1] + b[3]) - max(a[1], b[1]))
    return ix * iy / max(1, min(a[2] * a[3], b[2] * b[3]))


def _blur(img: np.ndarray, x: int, y: int, w: int, h: int, pad: float = 0.0) -> None:
    px, py = int(w * pad), int(h * pad)
    x0, y0 = max(0, x - px), max(0, y - py)
    x1, y1 = min(img.shape[1], x + w + px), min(img.shape[0], y + h + py)
    img[y0:y1, x0:x1] = cv2.GaussianBlur(img[y0:y1, x0:x1], _BLUR_KERNEL, 0)


def blur_sensitive(image_bytes: bytes) -> tuple[bytes, int, int]:
    """Returns (blurred JPEG, faces blurred, plates blurred)."""
    arr = np.frombuffer(image_bytes, dtype="uint8")
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("not a decodable image")
    # A spot photo never needs more than this; shrinking first makes the blur
    # several times faster and keeps staged photos small.
    longest = max(img.shape[:2])
    if longest > MAX_SIDE_PX:
        scale = MAX_SIDE_PX / longest
        img = cv2.resize(img, (round(img.shape[1] * scale), round(img.shape[0] * scale)), interpolation=cv2.INTER_AREA)

    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    faces = _FACE_CASCADE.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=5, minSize=(30, 30))
    plates = _yellow_plates(img)
    for box in _PLATE_CASCADE.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=6, minSize=(40, 12)):
        if not any(_overlap(tuple(box), p) > 0.3 for p in plates):  # same plate, found twice
            plates.append(tuple(box))

    for x, y, w, h in faces:
        _blur(img, x, y, w, h)
    for x, y, w, h in plates:
        _blur(img, x, y, w, h, pad=0.15)

    ok, buf = cv2.imencode(".jpg", img, [cv2.IMWRITE_JPEG_QUALITY, 82])
    if not ok:
        raise ValueError("failed to encode image")
    return buf.tobytes(), len(faces), len(plates)
