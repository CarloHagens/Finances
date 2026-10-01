DROP TABLE IF EXISTS net_worth_snapshots;

CREATE TABLE IF NOT EXISTS account_balance_history (
    id SERIAL PRIMARY KEY,
    account_id INT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    balance NUMERIC(15,2) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_balance_history_account_time ON account_balance_history(account_id, recorded_at DESC);
