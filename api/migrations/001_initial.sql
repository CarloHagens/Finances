CREATE TABLE IF NOT EXISTS user_profile (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    date_of_birth DATE NOT NULL,
    retirement_age INTEGER NOT NULL DEFAULT 53,
    pension_access_age INTEGER NOT NULL DEFAULT 58
);

CREATE TABLE IF NOT EXISTS accounts (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('asset', 'liability')),
    category TEXT NOT NULL CHECK (category IN ('checking', 'savings', 'isa', 'pension', 'property', 'mortgage', 'loan', 'credit_card', 'other')),
    balance NUMERIC(15, 2) NOT NULL DEFAULT 0,
    currency TEXT NOT NULL DEFAULT 'GBP'
);

CREATE TABLE IF NOT EXISTS mortgage_goal (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    account_id INTEGER REFERENCES accounts(id),
    monthly_payment NUMERIC(15, 2) NOT NULL DEFAULT 0,
    monthly_overpayment NUMERIC(15, 2) NOT NULL DEFAULT 0,
    annual_interest_rate NUMERIC(6, 4) NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pension_goal (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    account_id INTEGER REFERENCES accounts(id),
    monthly_contribution NUMERIC(15, 2) NOT NULL DEFAULT 0,
    annual_growth_rate NUMERIC(6, 4) NOT NULL DEFAULT 0.07,
    target_value NUMERIC(15, 2) NOT NULL DEFAULT 1000000,
    stop_contribution_age INTEGER NOT NULL DEFAULT 53,
    draw_age INTEGER NOT NULL DEFAULT 58
);

CREATE TABLE IF NOT EXISTS isa_bridge_goal (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    account_id INTEGER REFERENCES accounts(id),
    monthly_contribution NUMERIC(15, 2) NOT NULL DEFAULT 0,
    annual_growth_rate NUMERIC(6, 4) NOT NULL DEFAULT 0.07,
    target_monthly_income NUMERIC(15, 2) NOT NULL DEFAULT 0,
    partner_state_pension_monthly NUMERIC(15, 2) NOT NULL DEFAULT 0,
    bridge_start_age INTEGER NOT NULL DEFAULT 53,
    bridge_end_age INTEGER NOT NULL DEFAULT 58
);

CREATE TABLE IF NOT EXISTS trading212_config (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    api_key TEXT NOT NULL,
    last_synced_at TIMESTAMPTZ
);
