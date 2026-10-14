import pytest
from sqlalchemy import text

from database import engine
from geocoder import same_street

# A tiny synthetic map: Dizengoff runs north-south along lng 34.7740,
# Frishman crosses it east-west at lat 32.0740; a square sits to the west.
STREET_LNG = 34.7740


DIZENGOFF = "LINESTRING(34.7740 32.0700, 34.7740 32.0760)"
FRISHMAN = "LINESTRING(34.7720 32.0740, 34.7760 32.0740)"
SQUARE = "MULTIPOLYGON(((34.7650 32.0700, 34.7670 32.0700, 34.7670 32.0720, 34.7650 32.0720, 34.7650 32.0700)))"

FIXTURE_SQL = [
    f"""INSERT INTO osm_streets (geom, name, name_he, name_en) VALUES
          (ST_GeogFromText('SRID=4326;{DIZENGOFF}'), 'דיזנגוף', NULL, 'Dizengoff'),
          (ST_GeogFromText('SRID=4326;{FRISHMAN}'), 'פרישמן', NULL, 'Frishman')""",
    """INSERT INTO osm_addresses (geom, street, housenumber, city) VALUES
          (ST_GeogFromText('SRID=4326;POINT(34.77415 32.0705)'), 'דיזנגוף', '50', NULL),
          (ST_GeogFromText('SRID=4326;POINT(34.77415 32.0712)'), NULL, '52', NULL),
          (ST_GeogFromText('SRID=4326;POINT(34.77415 32.0718)'), 'דיזינגוף', '54', NULL),
          (ST_GeogFromText('SRID=4326;POINT(34.77250 32.07415)'), 'פרישמן', '7', NULL)""",
    """INSERT INTO osm_places (geom, kind, name, name_he, name_en) VALUES
          (ST_GeogFromText('SRID=4326;POINT(34.7800 32.0800)'), 'city', 'תל אביב-יפו', NULL, 'Tel Aviv-Yafo')""",
    f"""INSERT INTO osm_areas (geom, name, name_he, name_en) VALUES
          (ST_GeogFromText('SRID=4326;{SQUARE}'), 'כיכר רבין', NULL, 'Rabin Square')""",
]


async def _load_fixture_map() -> None:
    async with engine.begin() as conn:
        for statement in FIXTURE_SQL:
            await conn.execute(text(statement))


async def _reverse(client, lat, lng, lang="he"):
    resp = await client.get("/geocode/reverse", params={"lat": lat, "lng": lng, "lang": lang})
    assert resp.status_code == 200
    return resp.json()


async def test_point_on_a_street_gets_street_number_and_city(client):
    await _load_fixture_map()
    body = await _reverse(client, 32.0705, STREET_LNG)
    assert body == {"label": "דיזנגוף 50, תל אביב-יפו", "street": "דיזנגוף 50"}


async def test_number_only_building_is_assigned_to_the_nearest_street(client):
    await _load_fixture_map()
    body = await _reverse(client, 32.0712, STREET_LNG)
    assert body["street"] == "דיזנגוף 52"


async def test_spelling_variants_of_the_street_name_still_match(client):
    await _load_fixture_map()
    body = await _reverse(client, 32.0718, STREET_LNG)
    assert body["street"] == "דיזנגוף 54"


async def test_a_number_from_the_cross_street_is_never_borrowed(client):
    await _load_fixture_map()
    # On Dizengoff right at the corner; the nearest building is Frishman 7,
    # which must not be reported as a Dizengoff address.
    body = await _reverse(client, 32.0740, STREET_LNG)
    assert body["street"].startswith("דיזנגוף") or body["street"].startswith("פרישמן")
    assert body["street"] != "דיזנגוף 7"


async def test_point_inside_a_square_is_named_after_the_square(client):
    await _load_fixture_map()
    body = await _reverse(client, 32.0710, 34.7660)
    assert body["street"] == "כיכר רבין"


async def test_english_labels_use_english_names(client):
    await _load_fixture_map()
    body = await _reverse(client, 32.0705, STREET_LNG, lang="en")
    assert body == {"label": "Dizengoff 50, Tel Aviv-Yafo", "street": "Dizengoff 50"}


async def test_nowhere_returns_nulls_not_an_error(client):
    body = await _reverse(client, 31.0, 35.0)
    assert body == {"label": None, "street": None}


async def test_report_without_an_address_gets_one_from_the_server(client, make_user):
    await _load_fixture_map()
    _, headers = await make_user("reporter")
    resp = await client.post(
        "/spots",
        json={"lat": 32.0705, "lng": STREET_LNG, "spot_type": "street", "payment": "free"},
        headers=headers,
    )
    assert resp.status_code == 201
    assert resp.json()["address"] == "דיזנגוף 50"


async def test_server_address_wins_over_the_clients_label(client, make_user):
    await _load_fixture_map()
    _, headers = await make_user("reporter")
    resp = await client.post(
        "/spots",
        json={"lat": 32.0705, "lng": STREET_LNG, "spot_type": "street", "payment": "free", "address": "Somewhere 1"},
        headers=headers,
    )
    assert resp.json()["address"] == "דיזנגוף 50"


@pytest.mark.parametrize(
    ("a", "b", "same"),
    [
        ("בגין מנחם", "מנחם בגין", True),
        ("ברנדיס", "ברנדייס", True),
        ("הגבור האלמוני", "הגיבור האלמוני", True),
        ('ד"ר בודנהיימר', "בודנהיימר", True),
        ("בן גוריון", "שדרות דוד בן גוריון", True),
        ("שד' רוטשילד", "שדרות רוטשילד", True),
        ("דיזנגוף", "פרישמן", False),
        ("הרצל", "ז'בוטינסקי", False),
        ("", "דיזנגוף", False),
    ],
)
def test_same_street(a, b, same):
    assert same_street(a, b) is same
