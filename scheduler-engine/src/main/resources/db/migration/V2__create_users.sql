-- Real accounts. Also the assignment target for tasks: task-owners are real
-- user accounts (Phase 10 design decision), not a separate directory concept.
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         TEXT NOT NULL UNIQUE,
    display_name  TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Replaces UserDirectoryService.NAME_VARIANTS: "sarah" and "sarah kim" are
-- two rows pointing at the same user_id. Every user is seeded with a
-- self-alias (normalized display_name) at registration; more can be added
-- later without a schema change.
CREATE TABLE user_name_aliases (
    id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    alias   TEXT NOT NULL -- stored pre-normalized: trim + lowercase, same rule OwnerResolver already applies
);
CREATE UNIQUE INDEX idx_user_name_aliases_alias ON user_name_aliases (alias);
CREATE INDEX idx_user_name_aliases_user_id ON user_name_aliases (user_id);
