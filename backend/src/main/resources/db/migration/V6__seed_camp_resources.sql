-- Seed: a headline resource summary for every camp, scaled to its current population so the
-- numbers read plausibly. Closed/empty camps get zero, which is itself meaningful on the map.
INSERT INTO camp_resources (camp_id, resource_type, quantity, unit)
SELECT c.id, r.resource_type, r.per_person * c.population, r.unit
FROM camps c
CROSS JOIN (VALUES
    ('WATER',   3, 'liters/day'),
    ('FOOD',    2, 'meal packs'),
    ('MEDICAL', 1, 'aid kits')
) AS r(resource_type, per_person, unit);
