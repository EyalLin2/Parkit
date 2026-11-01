-- A tiny slice of the OpenStreetMap tables around the test location, so the app has
-- real addresses and a car park to show. Production imports these from an OSM extract.
INSERT INTO osm_streets (geom, name, name_he, name_en) VALUES
  (ST_GeogFromText('SRID=4326;LINESTRING(34.7805 32.0853, 34.7830 32.0853)'), 'דיזנגוף', 'דיזנגוף', 'Dizengoff');
INSERT INTO osm_places (geom, kind, name, name_he, name_en) VALUES
  (ST_GeogFromText('SRID=4326;POINT(34.7818 32.0853)'), 'city', 'תל אביב-יפו', 'תל אביב-יפו', 'Tel Aviv-Yafo');
-- A sign already read at the test location, so the reader answers from the shared cache
-- without any AI call. The fingerprint is all ones: the simulator's stand-in photo is
-- a left-to-right gradient. Paid all day, so there's a change to set a reminder for.
INSERT INTO sign_rules (id, location, fingerprint, rules, reuse_count, created_at) VALUES
  (gen_random_uuid(), ST_GeogFromText('SRID=4326;POINT(34.7818 32.0853)'), repeat('f', 64),
   '{"readable": true, "windows": [{"days": [0,1,2,3,4,5,6], "from": "00:00", "to": "23:59", "state": "paid"}], "otherwise": "allowed", "holidays": false}',
   0, now());
INSERT INTO osm_parking_lots (geom, name, name_he, name_en, kind, fee, capacity, operator) VALUES
  (ST_GeogFromText('SRID=4326;POINT(34.7830 32.0860)'), 'חניון דיזנגוף סנטר', 'חניון דיזנגוף סנטר', 'Dizengoff Center Parking', 'underground', 'yes', 400, 'Ahuzot HaHof');
