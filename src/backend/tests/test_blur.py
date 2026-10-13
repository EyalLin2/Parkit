import cv2
import numpy as np

from blur import blur_sensitive

YELLOW = (0, 205, 245)  # BGR — Israeli plate yellow


def _street(with_plate: bool = True, with_line: bool = False) -> np.ndarray:
    """A grey street scene with a car-coloured block and, optionally, an
    Israeli-style plate (yellow, black digits) and a yellow road line."""
    img = np.full((480, 640, 3), (120, 120, 120), np.uint8)
    cv2.rectangle(img, (150, 180), (490, 380), (60, 40, 30), -1)  # car body
    if with_plate:
        cv2.rectangle(img, (240, 320), (400, 356), YELLOW, -1)
        cv2.putText(img, "12-345-67", (248, 348), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (10, 10, 10), 2)
    if with_line:
        cv2.rectangle(img, (0, 440), (640, 446), YELLOW, -1)  # long thin painted line
    return img


def _jpeg(img: np.ndarray) -> bytes:
    return cv2.imencode(".jpg", img)[1].tobytes()


def _decode(data: bytes) -> np.ndarray:
    return cv2.imdecode(np.frombuffer(data, np.uint8), cv2.IMREAD_COLOR)


def test_israeli_plate_is_found_and_unreadable():
    original = _street()
    out, faces, plates = blur_sensitive(_jpeg(original))
    assert (faces, plates) == (0, 1)
    region = (slice(320, 356), slice(240, 400))
    before = cv2.cvtColor(original[region], cv2.COLOR_BGR2GRAY)
    after = cv2.cvtColor(_decode(out)[region], cv2.COLOR_BGR2GRAY)
    # The digits' sharp edges are gone: far less high-frequency detail.
    assert cv2.Laplacian(after, cv2.CV_64F).var() < cv2.Laplacian(before, cv2.CV_64F).var() * 0.2


def test_no_plate_no_blur():
    _, faces, plates = blur_sensitive(_jpeg(_street(with_plate=False)))
    assert (faces, plates) == (0, 0)


def test_yellow_road_line_is_not_a_plate():
    _, _, plates = blur_sensitive(_jpeg(_street(with_plate=False, with_line=True)))
    assert plates == 0


def test_big_photos_are_shrunk_before_storing():
    big = np.full((3000, 4000, 3), (120, 120, 120), np.uint8)
    out, _, _ = blur_sensitive(_jpeg(big))
    assert max(_decode(out).shape[:2]) == 1280
