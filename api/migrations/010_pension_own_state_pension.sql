-- Add the user's own state pension to the pension goal.  Drawn from a later age
-- (default 68) at an amount the user enters in today's money (their own haircut).
ALTER TABLE pension_goal
  ADD COLUMN IF NOT EXISTS own_state_pension_monthly NUMERIC(15,2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS own_state_pension_age INTEGER NOT NULL DEFAULT 68;
