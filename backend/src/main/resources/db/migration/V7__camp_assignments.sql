-- Which camp(s) a user manages. Camp Managers are entitled to their own camp's realtime topic
-- and nothing else, so the STOMP layer reads this to authorize per-camp subscriptions. Portable
-- DDL (Postgres + H2). Rows are seeded by CampAssignmentSeeder at startup, since user ids are
-- assigned by the demo seeder at runtime rather than baked into SQL.
CREATE TABLE camp_assignments (
    user_id BIGINT NOT NULL REFERENCES users (id),
    camp_id BIGINT NOT NULL REFERENCES camps (id),
    PRIMARY KEY (user_id, camp_id)
);

CREATE INDEX idx_camp_assignments_user ON camp_assignments (user_id);
