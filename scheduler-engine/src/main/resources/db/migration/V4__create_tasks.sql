-- The extracted action item, persisted end-to-end for the first time - today
-- it only ever lives inside one request/response cycle (TaskBoard is
-- in-memory, wiped on restart).
--
-- status is TEXT with a CHECK rather than a native Postgres ENUM type
-- deliberately: Hibernate's @Enumerated(STRING) sends enum values as a
-- VARCHAR-typed JDBC bind parameter, which Postgres does not implicitly
-- cast to a custom enum column (ERROR: column "status" is of type
-- task_status but expression is of type character varying) - a CHECK
-- constraint gives the same "exactly these three values" guarantee
-- without that friction.
CREATE TABLE tasks (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id                    UUID REFERENCES users(id) ON DELETE SET NULL, -- null only for UNRESOLVED
    owner_name_raw              TEXT,      -- preserved verbatim for UNRESOLVED rows (what the pipeline actually sent)
    description                 TEXT NOT NULL,
    deadline                    TIMESTAMPTZ,
    priority                    INT NOT NULL CHECK (priority BETWEEN 1 AND 10),
    estimated_duration_minutes  INT NOT NULL CHECK (estimated_duration_minutes > 0),
    status                      TEXT NOT NULL CHECK (status IN ('PLACED', 'REJECTED', 'UNRESOLVED')),
    reason                      TEXT,      -- rejection/unresolved reason; null for PLACED
    scheduled_start             TIMESTAMPTZ,
    scheduled_end               TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (status <> 'PLACED' OR (owner_id IS NOT NULL AND scheduled_start IS NOT NULL AND scheduled_end IS NOT NULL))
);
CREATE INDEX idx_tasks_owner_id ON tasks (owner_id);
CREATE INDEX idx_tasks_status ON tasks (status);
