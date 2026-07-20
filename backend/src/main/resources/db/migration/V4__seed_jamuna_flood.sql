-- Seed: the active Jamuna flood world (Kurigram / Gaibandha). Synthetic data only — no real
-- personal information. Delivered as a seed script, kept distinct from the schema (V3).
-- Coordinates are real-ish district/upazila points; camps are invented shelters.
INSERT INTO disasters (code, type, status, name_en, name_bn) VALUES
    ('jamuna-flood-2024', 'FLOOD', 'ACTIVE', 'Jamuna River Flood', 'যমুনা নদীর বন্যা');

-- Two affected river-char stretches, one per district, as GeoJSON polygons.
INSERT INTO affected_areas (disaster_id, name_en, name_bn, geometry)
SELECT d.id, v.name_en, v.name_bn, v.geometry
FROM disasters d
CROSS JOIN (VALUES
    ('Kurigram Char Belt', 'কুড়িগ্রাম চর অঞ্চল',
     '{"type":"Polygon","coordinates":[[[89.60,25.95],[89.80,25.90],[89.78,25.55],[89.58,25.60],[89.60,25.95]]]}'),
    ('Gaibandha Char Belt', 'গাইবান্ধা চর অঞ্চল',
     '{"type":"Polygon","coordinates":[[[89.55,25.45],[89.75,25.42],[89.72,25.15],[89.52,25.18],[89.55,25.45]]]}')
) AS v(name_en, name_bn, geometry)
WHERE d.code = 'jamuna-flood-2024';

INSERT INTO camps (disaster_id, code, name_en, name_bn, lat, lng, capacity, population, status)
SELECT d.id, v.code, v.name_en, v.name_bn, v.lat, v.lng, v.capacity, v.population, v.status
FROM disasters d
CROSS JOIN (VALUES
    ('jam-kurigram-sadar', 'Kurigram Sadar Govt College Shelter', 'কুড়িগ্রাম সদর সরকারি কলেজ আশ্রয়কেন্দ্র', 25.806000, 89.636000, 1200, 1080, 'OPEN'),
    ('jam-chilmari',       'Chilmari Riverside Camp',            'চিলমারী নদীতীর আশ্রয়কেন্দ্র',           25.553000, 89.683000,  800,  790, 'OPEN'),
    ('jam-ulipur',         'Ulipur Union Shelter',               'উলিপুর ইউনিয়ন আশ্রয়কেন্দ্র',            25.661000, 89.622000,  950,  610, 'OPEN'),
    ('jam-rajarhat',       'Rajarhat Primary School Camp',       'রাজারহাট প্রাথমিক বিদ্যালয় আশ্রয়কেন্দ্র', 25.831000, 89.552000,  600,  540, 'OPEN'),
    ('jam-nageshwari',     'Nageshwari Cyclone Shelter',         'নাগেশ্বরী ঘূর্ণিঝড় আশ্রয়কেন্দ্র',        25.951000, 89.731000,  700,  300, 'OPEN'),
    ('jam-roumari',        'Roumari Char Camp',                  'রৌমারী চর আশ্রয়কেন্দ্র',                25.751000, 89.861000,  500,  480, 'OPEN'),
    ('jam-fulchhari',      'Fulchhari Ghat Shelter',             'ফুলছড়ি ঘাট আশ্রয়কেন্দ্র',               25.352000, 89.681000, 1000,  940, 'OPEN'),
    ('jam-saghata',        'Saghata Union Camp',                 'সাঘাটা ইউনিয়ন আশ্রয়কেন্দ্র',            25.232000, 89.631000,  650,  420, 'OPEN'),
    ('jam-sundarganj',     'Sundarganj High School Shelter',     'সুন্দরগঞ্জ উচ্চ বিদ্যালয় আশ্রয়কেন্দ্র',   25.553000, 89.551000,  850,  700, 'OPEN'),
    ('jam-gaibandha-sadar','Gaibandha Sadar Stadium Camp',       'গাইবান্ধা সদর স্টেডিয়াম আশ্রয়কেন্দ্র',    25.331000, 89.531000, 1500, 1350, 'OPEN'),
    ('jam-char-relief',    'Kurigram Char Relief Point',         'কুড়িগ্রাম চর ত্রাণকেন্দ্র',              25.700000, 89.700000,  400,    0, 'CLOSED')
) AS v(code, name_en, name_bn, lat, lng, capacity, population, status)
WHERE d.code = 'jamuna-flood-2024';
