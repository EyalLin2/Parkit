"""Reporter levels: what each costs in points and what it unlocks.

One place for the whole ladder so the API, the perks and the app's
"benefits" screen can never disagree. Perks are real behaviour, earned by
a track record (false reports cost points, so a level can be lost):

- bronze: a bronze badge next to your name and on your reports
- silver: your reports stay on the map 45 min instead of 30
- gold:   1 min between reports instead of 2
- legend: a distinct pin on the map, listed first in "nearby"
"""

from dataclasses import dataclass


@dataclass(frozen=True)
class Level:
    key: str
    min_points: int
    spot_ttl_minutes: int
    report_cooldown_seconds: int


LEVELS: tuple[Level, ...] = (
    Level("newbie", 0, 30, 120),
    Level("bronze", 100, 30, 120),
    Level("silver", 400, 45, 120),
    Level("gold", 1200, 45, 60),
    Level("legend", 3000, 45, 60),
)


def level_for(points: int) -> Level:
    current = LEVELS[0]
    for level in LEVELS:
        if points >= level.min_points:
            current = level
    return current


def next_level(points: int) -> Level | None:
    return next((level for level in LEVELS if level.min_points > points), None)
