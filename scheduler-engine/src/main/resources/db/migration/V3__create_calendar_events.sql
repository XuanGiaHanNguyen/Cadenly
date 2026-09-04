-- Fixed, immovable pre-existing commitments (the CalendarEvent model - real
-- now; not yet wired into scheduling, which still reads busy time from
-- bookings, but the table exists as a genuine target for that later).
--
-- The EXCLUDE constraint is expressed over an inline tstzrange(...)
-- expression rather than a stored range column, so the entity stays two
-- plain TIMESTAMPTZ columns - no custom Hibernate range type needed.
CREATE TABLE calendar_events (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    start_at   TIMESTAMPTZ NOT NULL,
    end_at     TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (start_at < end_at),
    CONSTRAINT calendar_events_no_overlap EXCLUDE USING gist (
        user_id WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
    )
);
CREATE INDEX idx_calendar_events_user_id ON calendar_events (user_id);
