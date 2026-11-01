"""Where ParkIt is open. Launch starts small, in Tel Aviv-Yafo.

A plain box around the city, so its eastern and southern edges also catch a
little of Ramat Gan, Givatayim and Holon. Good enough while starting small.
"""

from config import settings

SOUTH, NORTH = 32.03, 32.15
WEST, EAST = 34.73, 34.85


def in_service_area(lat: float, lng: float) -> bool:
    if not settings.tel_aviv_only:
        return True
    return SOUTH <= lat <= NORTH and WEST <= lng <= EAST
