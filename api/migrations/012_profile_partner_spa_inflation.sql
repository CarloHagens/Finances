-- Optional override for the partner's State Pension age; 0 = work it out from
-- their date of birth under current legislation.
ALTER TABLE user_profile
  ADD COLUMN IF NOT EXISTS partner_state_pension_age INTEGER NOT NULL DEFAULT 0;

-- Inflation becomes one shared assumption on the profile, so the pension and
-- ISA bridge goals can't use different rates.  The pension goal's rate wins;
-- the goal columns are left in place, unused.
ALTER TABLE user_profile
  ADD COLUMN IF NOT EXISTS inflation_rate NUMERIC(6,4) NOT NULL DEFAULT 0.03;

UPDATE user_profile p SET
  inflation_rate = COALESCE(pg.inflation_rate, ib.inflation_rate, p.inflation_rate)
FROM (SELECT 1 AS id) one
LEFT JOIN pension_goal pg ON pg.id = 1
LEFT JOIN isa_bridge_goal ib ON ib.id = 1
WHERE p.id = 1;
