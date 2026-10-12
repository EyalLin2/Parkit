"""Load street names, addresses and towns from OpenStreetMap into PostGIS.

One-off (re-runnable) import that powers the self-hosted geocoder in
``geocoder.py``. Downloads the Geofabrik extract for Israel (~120MB, refreshed
daily, ODbL — attribution "© OpenStreetMap contributors" is shown in the app),
extracts:

- addresses: nodes/buildings with addr:housenumber (street from addr:street,
             an associatedStreet relation, or — at lookup — the nearest street),
             plus numbers expanded from addr:interpolation lines
- streets:   named drivable/pedestrian highways (not footpaths)
- squares:   named squares / pedestrian plazas (polygons)
- places:    city / town / village / suburb nodes
- parking lots: public off-street car parks (amenity=parking, not street-side
             parking and not private/customers-only) — the "Paid parking"
             layer and the "couldn't find a spot" fallback

and replaces the contents of the osm_* tables in a single transaction, so the
geocoder never sees a half-loaded state.

Usage (inside the backend container):
    python import_osm_geodata.py                 # download + import
    python import_osm_geodata.py --pbf file.pbf  # use a local extract
"""

import argparse
import asyncio
import tempfile
import time
import urllib.request
from pathlib import Path

import asyncpg
import osmium

from config import settings

EXTRACT_URL = "https://download.geofabrik.de/asia/israel-and-palestine-latest.osm.pbf"

STREET_HIGHWAYS = {
    "motorway",
    "trunk",
    "primary",
    "secondary",
    "tertiary",
    "unclassified",
    "residential",
    "living_street",
    "service",
    "pedestrian",
    "road",
    "motorway_link",
    "trunk_link",
    "primary_link",
    "secondary_link",
    "tertiary_link",
}
PLACE_KINDS = {"city", "town", "village", "suburb"}
# Street-side parking is what ParkIt users report themselves; these are lots.
NOT_A_LOT = {"street_side", "lane", "on_street", "on_kerb", "half_on_kerb", "layby"}
NOT_PUBLIC = {"private", "customers", "no", "permit", "residents", "delivery", "employees"}
_wkt = osmium.geom.WKTFactory()


def _is_named_square(tags) -> bool:
    if not tags.get("name"):
        return False
    return tags.get("place") == "square" or tags.get("highway") == "pedestrian"


class StreetRelations(osmium.SimpleHandler):
    """Pass 1: associatedStreet relations — "these houses belong to this
    street" — for buildings that carry a number but no addr:street."""

    def __init__(self) -> None:
        super().__init__()
        self.street_of: dict[tuple[str, int], str] = {}

    def relation(self, r) -> None:
        if r.tags.get("type") != "associatedStreet":
            return
        name = r.tags.get("name")
        if not name:
            return
        for m in r.members:
            if m.role == "house":
                self.street_of[(m.type, m.ref)] = name[:200]


class Extractor(osmium.SimpleHandler):
    def __init__(self, street_of: dict[tuple[str, int], str] | None = None) -> None:
        super().__init__()
        self.street_of = street_of or {}
        # node id -> (lon, lat, number, street) for interpolation endpoints
        self.numbered_nodes: dict[int, tuple[float, float, str, str | None]] = {}
        # (kind, street, [(node id, lon, lat)]) for addr:interpolation lines
        self.interpolations: list[tuple[str, str | None, list[tuple[int, float, float]]]] = []
        self.addresses: list[tuple[str, str, str, str | None]] = []
        self.streets: list[tuple[str, str, str | None, str | None]] = []
        self.places: list[tuple[str, str, str, str | None, str | None]] = []
        self.areas: list[tuple[str, str, str | None, str | None]] = []
        self.lots: list[tuple[str, ...]] = []

    def _lot(self, tags, lon: float, lat: float) -> None:
        if tags.get("amenity") != "parking":
            return
        if tags.get("parking") in NOT_A_LOT or tags.get("access") in NOT_PUBLIC:
            return
        capacity = tags.get("capacity", "")
        self.lots.append(
            (
                f"POINT({lon} {lat})",
                (tags.get("name") or "")[:200] or None,
                tags.get("name:he"),
                tags.get("name:en"),
                tags.get("parking"),
                tags.get("fee"),
                capacity if capacity.isdigit() else None,
                (tags.get("operator") or "")[:120] or None,
            )
        )

    @staticmethod
    def _address(tags) -> tuple[str | None, str, str | None] | None:
        # A house number alone is kept too: many Israeli buildings are mapped
        # with addr:housenumber but no addr:street, and the geocoder assigns
        # them to the nearest street (the same rule Nominatim uses).
        number = tags.get("addr:housenumber")
        if not number:
            return None
        street = tags.get("addr:street")
        return (street[:200] if street else None), number[:40], (tags.get("addr:city") or None)

    def _with_relation_street(self, addr, key):
        if addr and addr[0] is None and key in self.street_of:
            return (self.street_of[key], addr[1], addr[2])
        return addr

    def node(self, n) -> None:
        tags = n.tags
        addr = self._with_relation_street(self._address(tags), ("n", n.id))
        if addr and n.location.valid():
            self.addresses.append((f"POINT({n.location.lon} {n.location.lat})", *addr))
            self.numbered_nodes[n.id] = (n.location.lon, n.location.lat, addr[1], addr[0])
        if n.location.valid():
            self._lot(tags, n.location.lon, n.location.lat)
        kind = tags.get("place")
        if kind in PLACE_KINDS and tags.get("name") and n.location.valid():
            point = f"POINT({n.location.lon} {n.location.lat})"
            self.places.append((point, kind, tags["name"][:200], tags.get("name:he"), tags.get("name:en")))

    def way(self, w) -> None:
        tags = w.tags
        coords = [(nd.lon, nd.lat) for nd in w.nodes if nd.location.valid()]
        if len(coords) < 2:
            return
        kind = tags.get("addr:interpolation")
        if kind in ("odd", "even", "all"):
            nodes = [(nd.ref, nd.lon, nd.lat) for nd in w.nodes if nd.location.valid()]
            self.interpolations.append((kind, tags.get("addr:street"), nodes))
            return
        addr = self._with_relation_street(self._address(tags), ("w", w.id))
        if addr:
            # Buildings carry most Israeli addresses; their centroid is close
            # enough to the entrance for "which number is this" purposes.
            pts = coords[:-1] if coords[0] == coords[-1] else coords
            lon = sum(c[0] for c in pts) / len(pts)
            lat = sum(c[1] for c in pts) / len(pts)
            self.addresses.append((f"POINT({lon} {lat})", *addr))
        if tags.get("highway") in STREET_HIGHWAYS and tags.get("name"):
            wkt = "LINESTRING(" + ",".join(f"{lon} {lat}" for lon, lat in coords) + ")"
            self.streets.append((wkt, tags["name"][:200], tags.get("name:he"), tags.get("name:en")))

    def area(self, a) -> None:
        tags = a.tags
        addr = self._address(tags)
        addr = self._with_relation_street(addr, ("r", a.orig_id())) if not a.from_way() else addr
        if addr and not a.from_way():
            # Buildings mapped as multipolygon relations (plain closed ways
            # are already handled in way()).
            pts = [(n.lon, n.lat) for ring in a.outer_rings() for n in ring if n.location.valid()]
            if pts:
                lon = sum(p[0] for p in pts) / len(pts)
                lat = sum(p[1] for p in pts) / len(pts)
                self.addresses.append((f"POINT({lon} {lat})", *addr))
        if tags.get("amenity") == "parking":
            pts = [(n.lon, n.lat) for ring in a.outer_rings() for n in ring if n.location.valid()]
            if pts:
                self._lot(tags, sum(p[0] for p in pts) / len(pts), sum(p[1] for p in pts) / len(pts))
        if not _is_named_square(tags):
            return
        try:
            wkt = _wkt.create_multipolygon(a)
        except Exception:  # broken/incomplete OSM geometry — skip it
            return
        self.areas.append((wkt, tags["name"][:200], tags.get("name:he"), tags.get("name:en")))

    def expand_interpolations(self) -> int:
        """addr:interpolation lines ("numbers 2..40, even, along here"):
        place the missing numbers evenly between each pair of numbered
        endpoints on the line — the same thing Nominatim does."""
        added = 0
        for kind, line_street, nodes in self.interpolations:
            step = 1 if kind == "all" else 2
            # cumulative distance (degrees are fine for proportions on a short line)
            dist = [0.0]
            for (_, x0, y0), (_, x1, y1) in zip(nodes, nodes[1:]):
                dist.append(dist[-1] + ((x1 - x0) ** 2 + (y1 - y0) ** 2) ** 0.5)
            ends = [(i, self.numbered_nodes[ref]) for i, (ref, _, _) in enumerate(nodes) if ref in self.numbered_nodes]
            for (i, a), (j, b) in zip(ends, ends[1:]):
                try:
                    na, nb = int(a[2]), int(b[2])
                except ValueError:
                    continue  # "12א" style endpoints can't be interpolated
                if abs(nb - na) <= step or abs(nb - na) > 400 or dist[j] == dist[i]:
                    continue
                street = line_street or a[3] or b[3]
                direction = 1 if nb > na else -1
                for num in range(na + direction * step, nb, direction * step):
                    t = (num - na) / (nb - na)
                    target = dist[i] + t * (dist[j] - dist[i])
                    k = max(m for m in range(i, j + 1) if dist[m] <= target)
                    k = min(k, j - 1)
                    seg = dist[k + 1] - dist[k] or 1.0
                    f = (target - dist[k]) / seg
                    lon = nodes[k][1] + f * (nodes[k + 1][1] - nodes[k][1])
                    lat = nodes[k][2] + f * (nodes[k + 1][2] - nodes[k][2])
                    self.addresses.append((f"POINT({lon} {lat})", street, str(num), None))
                    added += 1
        return added


def _download(dest: Path) -> None:
    print(f"Downloading {EXTRACT_URL} …", flush=True)
    req = urllib.request.Request(EXTRACT_URL, headers={"User-Agent": "ParkIt-geodata-import/1.0"})
    with urllib.request.urlopen(req) as resp, open(dest, "wb") as out:
        while chunk := resp.read(1 << 20):
            out.write(chunk)
    print(f"  {dest.stat().st_size / 1e6:.0f} MB", flush=True)


async def _load(ex: Extractor) -> None:
    dsn = settings.database_url.replace("+asyncpg", "")
    conn = await asyncpg.connect(dsn, ssl="require" if settings.app_env != "local" else None)
    try:
        async with conn.transaction():
            for table, cols, rows in (
                ("osm_addresses", ("wkt", "street", "housenumber", "city"), ex.addresses),
                ("osm_streets", ("wkt", "name", "name_he", "name_en"), ex.streets),
                ("osm_areas", ("wkt", "name", "name_he", "name_en"), ex.areas),
                ("osm_places", ("wkt", "kind", "name", "name_he", "name_en"), ex.places),
                (
                    "osm_parking_lots",
                    ("wkt", "name", "name_he", "name_en", "kind", "fee", "capacity", "operator"),
                    ex.lots,
                ),
            ):
                stage = f"stage_{table}"
                await conn.execute(f"CREATE TEMP TABLE {stage} ({', '.join(c + ' text' for c in cols)}) ON COMMIT DROP")
                await conn.copy_records_to_table(stage, records=rows, columns=list(cols))
                rest = ", ".join(cols[1:])
                # Everything is staged as text; the one integer column is cast on the way in.
                select_rest = rest.replace("capacity", "capacity::int")
                await conn.execute(f"TRUNCATE {table} RESTART IDENTITY")
                await conn.execute(
                    f"INSERT INTO {table} (geom, {rest}) "
                    f"SELECT ST_GeogFromText('SRID=4326;' || wkt), {select_rest} FROM {stage}"
                )
                print(f"  {table}: {len(rows):,} rows", flush=True)
        for table in ("osm_addresses", "osm_streets", "osm_areas", "osm_places", "osm_parking_lots"):
            await conn.execute(f"ANALYZE {table}")
    finally:
        await conn.close()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--pbf", type=Path, help="local .osm.pbf extract (skips the download)")
    args = parser.parse_args()

    started = time.monotonic()
    with tempfile.TemporaryDirectory() as tmp:
        pbf = args.pbf
        if pbf is None:
            pbf = Path(tmp) / "extract.osm.pbf"
            _download(pbf)
        print("Extracting addresses, streets, squares, places and parking lots …", flush=True)
        rels = StreetRelations()
        rels.apply_file(str(pbf))
        ex = Extractor(rels.street_of)
        ex.apply_file(str(pbf), locations=True, idx="flex_mem")
        print(
            f"  {len(rels.street_of):,} houses tied to a street by relation, "
            f"{ex.expand_interpolations():,} numbers interpolated",
            flush=True,
        )
    print("Loading into PostGIS …", flush=True)
    asyncio.run(_load(ex))
    print(f"Done in {time.monotonic() - started:.0f}s", flush=True)


if __name__ == "__main__":
    main()
