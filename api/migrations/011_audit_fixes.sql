-- Retirement-plan assumptions shared by the pension and ISA bridge goals move
-- onto the profile, so the two goals can no longer disagree.
ALTER TABLE user_profile
  ADD COLUMN IF NOT EXISTS target_monthly_income         NUMERIC(15,2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS single_target_monthly_income  NUMERIC(15,2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS partner_date_of_birth         DATE,
  ADD COLUMN IF NOT EXISTS partner_state_pension_monthly NUMERIC(15,2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS partner_pension_end_age       INTEGER NOT NULL DEFAULT 0;

-- Carry the existing values across.  The pension goal wins where both goals
-- have a value; the ISA bridge fills in anything the pension goal left at 0.
UPDATE user_profile p SET
  retirement_age                = COALESCE(pg.stop_contribution_age, p.retirement_age),
  pension_access_age            = COALESCE(pg.draw_age, p.pension_access_age),
  target_monthly_income         = COALESCE(NULLIF(pg.target_monthly_income, 0), NULLIF(ib.target_monthly_income, 0), 0),
  partner_state_pension_monthly = COALESCE(NULLIF(pg.partner_state_pension_monthly, 0), NULLIF(ib.partner_state_pension_monthly, 0), 0)
FROM (SELECT 1 AS id) one
LEFT JOIN pension_goal pg ON pg.id = 1
LEFT JOIN isa_bridge_goal ib ON ib.id = 1
WHERE p.id = 1;

-- The superseded goal columns (pension_goal.target_monthly_income,
-- partner_state_pension_monthly, stop_contribution_age, draw_age,
-- safe_withdrawal_rate, target_value; isa_bridge_goal.target_monthly_income,
-- partner_state_pension_monthly, bridge_start_age, bridge_end_age) are left in
-- place, unused, so an older API image still runs against this schema.

-- Mortgage: follow-on rate after the fixed term, mortgage end date (payment is
-- recalculated at remortgage), a specific property for LTV, the penalty-free
-- overpayment allowance, and rates stored to 3 decimal places of a percent.
ALTER TABLE mortgage_goal
  ALTER COLUMN annual_interest_rate TYPE NUMERIC(8,6),
  ADD COLUMN IF NOT EXISTS follow_on_rate            NUMERIC(8,6),
  ADD COLUMN IF NOT EXISTS term_end                  DATE,
  ADD COLUMN IF NOT EXISTS property_account_id       INTEGER REFERENCES accounts(id),
  ADD COLUMN IF NOT EXISTS overpayment_allowance_pct NUMERIC(6,4) NOT NULL DEFAULT 0.10;

-- Accounts are archived rather than deleted so past net worth keeps them.
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ;
