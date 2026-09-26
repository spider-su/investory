CREATE TABLE investory.ryczalt_ksef_sync_status (
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    period_year INTEGER NOT NULL,
    period_month INTEGER NOT NULL CHECK (period_month BETWEEN 1 AND 12),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','SUCCEEDED','FAILED')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    error_code VARCHAR(64),
    PRIMARY KEY (profile_id, period_year, period_month)
);
