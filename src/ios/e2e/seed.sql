-- A tiny slice of the OpenStreetMap tables around the test location, so the app has
-- real addresses and a car park to show. Production imports these from an OSM extract.
INSERT INTO osm_streets (geom, name, name_he, name_en) VALUES
  (ST_GeogFromText('SRID=4326;LINESTRING(34.7805 32.0853, 34.7830 32.0853)'), 'דיזנגוף', 'דיזנגוף', 'Dizengoff');
INSERT INTO osm_places (geom, kind, name, name_he, name_en) VALUES
  (ST_GeogFromText('SRID=4326;POINT(34.7818 32.0853)'), 'city', 'תל אביב-יפו', 'תל אביב-יפו', 'Tel Aviv-Yafo');
INSERT INTO osm_parking_lots (geom, name, name_he, name_en, kind, fee, capacity, operator) VALUES
  (ST_GeogFromText('SRID=4326;POINT(34.7830 32.0860)'), 'חניון דיזנגוף סנטר', 'חניון דיזנגוף סנטר', 'Dizengoff Center Parking', 'underground', 'yes', 400, 'Ahuzot HaHof');
