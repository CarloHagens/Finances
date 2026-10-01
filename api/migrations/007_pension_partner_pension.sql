ALTER TABLE pension_goal
  ADD COLUMN IF NOT EXISTS partner_state_pension_monthly NUMERIC(15,2) NOT NULL DEFAULT 0;
