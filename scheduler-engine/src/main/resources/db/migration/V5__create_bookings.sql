-- Replaces SharedResourceCalendar's in-memory ConcurrentHashMap<UUID, List<TimeSlot>>.
-- resource_id stays dual-purpose exactly as before the migration: an
-- owner's personal calendar OR an arbitrary shared resource (e.g. a room) -
-- deliberately no FK here, since a resource is not necessarily a user.
--
-- bookings_no_overlap is the single source of truth for "no double booking":
-- see Phase 10 design notes for why this replaces the in-memory
-- ReentrantLock-per-resource approach (SharedResourceCalendar, now a test
-- fixture only - see com.cadenly.scheduler.concurrency).
CREATE TABLE bookings (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resource_id UUID NOT NULL,
    start_at    TIMESTAMPTZ NOT NULL,
    end_at      TIMESTAMPTZ NOT NULL,
    task_id     UUID REFERENCES tasks(id) ON DELETE SET NULL, -- null: raw BookingController bookings aren't tied to a task
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (start_at < end_at),
    CONSTRAINT bookings_no_overlap EXCLUDE USING gist (
        resource_id WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
    )
);
CREATE INDEX idx_bookings_resource_id ON bookings (resource_id);
CREATE INDEX idx_bookings_task_id ON bookings (task_id);
