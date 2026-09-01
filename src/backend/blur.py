"""Local stand-in for the AWS Rekognition blur step (ADR-0004).

This is explicitly NOT the real pipeline — it's a local OpenCV Haar
cascade face detector, used so the rest of the photo flow (staging,
preview, confirm-and-attach, lifecycle-tied deletion) can be built and
tested end-to-end without real AWS credentials. It only blurs faces:
unlike Rekognition's DetectFaces, there is no dedicated license-plate
API to stand in for either, so plate blurring is left out rather than
faked. Swapping this module for a real Rekognition call is a
single-module change — nothing else in the report flow needs to know
which one is running.
"""

import cv2
import numpy as np

_FACE_CASCADE = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_frontalface_default.xml")
_BLUR_KERNEL = (51, 51)


def blur_faces(image_bytes: bytes) -> tuple[bytes, int]:
    arr = np.frombuffer(image_bytes, dtype="uint8")
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("not a decodable image")

    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    faces = _FACE_CASCADE.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=5, minSize=(30, 30))

    for x, y, w, h in faces:
        region = img[y : y + h, x : x + w]
        img[y : y + h, x : x + w] = cv2.GaussianBlur(region, _BLUR_KERNEL, 0)

    ok, buf = cv2.imencode(".jpg", img)
    if not ok:
        raise ValueError("failed to encode image")
    return buf.tobytes(), len(faces)
