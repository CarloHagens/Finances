-- Add LTV remortgage targeting to the mortgage goal:
--   target_ltv      — desired loan-to-value band at the end of the fixed term (e.g. 0.60 = 60%)
--   fixed_term_end  — date the current fixed-rate deal ends (nullable)
ALTER TABLE mortgage_goal
  ADD COLUMN IF NOT EXISTS target_ltv NUMERIC(6,4) NOT NULL DEFAULT 0.60,
  ADD COLUMN IF NOT EXISTS fixed_term_end DATE;
