-- Baseline migration: proves Flyway is wired end-to-end and fails startup if it cannot run.
-- Kept to portable DDL so the identical migration applies on Postgres (runtime) and H2 (tests).
CREATE TABLE app_info (
    id         INTEGER      NOT NULL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO app_info (id, name) VALUES (1, 'disaster-management-system');
