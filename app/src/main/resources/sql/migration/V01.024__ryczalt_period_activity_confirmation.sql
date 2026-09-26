CREATE TABLE investory.ryczalt_period_activity_confirmation (
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    period_year INTEGER NOT NULL,
    period_month INTEGER NOT NULL CHECK (period_month BETWEEN 1 AND 12),
    confirmation_type VARCHAR(32) NOT NULL CHECK (confirmation_type IN ('NO_REVENUE')),
    confirmed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_by VARCHAR(256) NOT NULL,
    invoice_count_at_confirmation INTEGER NOT NULL CHECK (invoice_count_at_confirmation >= 0),
    transaction_count_at_confirmation INTEGER NOT NULL CHECK (transaction_count_at_confirmation >= 0),
    PRIMARY KEY (profile_id, period_year, period_month),
    CONSTRAINT ryczalt_activity_confirmation_period_ck CHECK (period_year BETWEEN 2000 AND 2200)
);

COMMENT ON TABLE investory.ryczalt_period_activity_confirmation IS
    'Authenticated, period-scoped user confirmation that no revenue was recorded at confirmation time.';
