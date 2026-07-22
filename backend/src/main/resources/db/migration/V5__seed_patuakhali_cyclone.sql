-- Seed: the stable Patuakhali coastal cyclone world. Synthetic data only. A second, concurrent
-- disaster so the map and every later feature exercise a multi-disaster world from the start.
INSERT INTO disasters (code, type, status, name_en, name_bn) VALUES
    ('patuakhali-cyclone-2024', 'CYCLONE', 'STABLE', 'Patuakhali Coastal Cyclone', 'পটুয়াখালী উপকূলীয় ঘূর্ণিঝড়');

INSERT INTO affected_areas (disaster_id, name_en, name_bn, geometry)
SELECT d.id, v.name_en, v.name_bn, v.geometry
FROM disasters d
CROSS JOIN (VALUES
    ('Patuakhali Coastal Zone', 'পটুয়াখালী উপকূলীয় অঞ্চল',
     '{"type":"Polygon","coordinates":[[[90.20,22.55],[90.60,22.55],[90.58,21.90],[90.18,21.95],[90.20,22.55]]]}')
) AS v(name_en, name_bn, geometry)
WHERE d.code = 'patuakhali-cyclone-2024';

INSERT INTO camps (disaster_id, code, name_en, name_bn, lat, lng, capacity, population, status)
SELECT d.id, v.code, v.name_en, v.name_bn, v.lat, v.lng, v.capacity, v.population, v.status
FROM disasters d
CROSS JOIN (VALUES
    ('pat-sadar',     'Patuakhali Sadar Shelter',        'পটুয়াখালী সদর আশ্রয়কেন্দ্র',       22.359000, 90.329000, 900, 250, 'OPEN'),
    ('pat-kalapara',  'Kalapara Coastal Camp',           'কলাপাড়া উপকূলীয় আশ্রয়কেন্দ্র',      21.991000, 90.241000, 700, 180, 'OPEN'),
    ('pat-galachipa', 'Galachipa Union Shelter',         'গলাচিপা ইউনিয়ন আশ্রয়কেন্দ্র',       22.161000, 90.421000, 600, 120, 'OPEN'),
    ('pat-bauphal',   'Bauphal High School Camp',        'বাউফল উচ্চ বিদ্যালয় আশ্রয়কেন্দ্র',   22.481000, 90.531000, 550,  90, 'OPEN'),
    ('pat-rangabali', 'Rangabali Island Shelter',        'রাঙ্গাবালী দ্বীপ আশ্রয়কেন্দ্র',       22.171000, 90.301000, 500,  60, 'CLOSED')
) AS v(code, name_en, name_bn, lat, lng, capacity, population, status)
WHERE d.code = 'patuakhali-cyclone-2024';
