"""Reporter levels: what each costs in points and what it unlocks.

One place for the whole ladder so the API and the app's "levels" screen
can never disagree. Perks are recognition only — they change how *your*
reports and name look, never how long a spot stays or where it's listed,
so a higher level can't make the map worse for anyone else (a spot kept
around longer is usually a spot that's already gone):

- bronze: bronze frame on your pins, bronze badge by your name
- silver: silver frame on your pins
- gold:   gold frame on your pins
- legend: a solid purple pin of your own
"""

from dataclasses import dataclass


@dataclass(frozen=True)
class Level:
    key: str
    min_points: int


LEVELS: tuple[Level, ...] = (
    Level("newbie", 0),
    Level("bronze", 100),
    Level("silver", 400),
    Level("gold", 1200),
    Level("legend", 3000),
)


def level_for(points: int) -> Level:
    current = LEVELS[0]
    for level in LEVELS:
        if points >= level.min_points:
            current = level
    return current


def next_level(points: int) -> Level | None:
    return next((level for level in LEVELS if level.min_points > points), None)
