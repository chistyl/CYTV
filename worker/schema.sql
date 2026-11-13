CREATE TABLE IF NOT EXISTS activation_codes (
  code_hash TEXT PRIMARY KEY,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  used_at INTEGER,
  installation_id TEXT,
  activation_id TEXT UNIQUE
);

CREATE INDEX IF NOT EXISTS idx_activation_codes_expiry
  ON activation_codes(expires_at);

CREATE TABLE IF NOT EXISTS activations (
  activation_id TEXT PRIMARY KEY,
  installation_id TEXT NOT NULL UNIQUE,
  activated_at INTEGER NOT NULL
);
