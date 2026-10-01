ALTER TABLE trading212_config ADD COLUMN IF NOT EXISTS account_id INT REFERENCES accounts(id);
