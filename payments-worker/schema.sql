CREATE TABLE IF NOT EXISTS transactions (id TEXT PRIMARY KEY, user_email TEXT, payment_id TEXT, receipt_no TEXT, amount INTEGER, tier_purchased TEXT, status TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS user_entitlements (email TEXT PRIMARY KEY, tier TEXT NOT NULL DEFAULT 'FREE', expires_at DATETIME);
