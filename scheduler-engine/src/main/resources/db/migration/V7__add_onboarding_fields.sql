-- Onboarding: collected right after signup, before the user reaches the
-- dashboard. Both nullable - a freshly registered account has neither yet;
-- occupation IS NOT NULL is what the frontend treats as "has completed
-- onboarding" (see AuthController.CurrentUserResponse).
ALTER TABLE users ADD COLUMN occupation TEXT;
ALTER TABLE users ADD COLUMN calendar_preference TEXT CHECK (calendar_preference IN ('google', 'manual'));
