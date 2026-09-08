-- Weekly work schedule, collected during onboarding right before the
-- calendar-connect step. Used by the dashboard to gray out non-working
-- days/hours on the calendar. Nullable - existing users who onboarded
-- before this migration simply have no schedule (frontend treats that as
-- "don't gray anything out").
ALTER TABLE users ADD COLUMN work_days TEXT;
ALTER TABLE users ADD COLUMN work_start_time TEXT;
ALTER TABLE users ADD COLUMN work_end_time TEXT;
