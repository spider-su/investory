CREATE TABLE investory.accounting_calculation_snapshot (
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    tax_period DATE NOT NULL,
    schema_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    calculation_hash VARCHAR(64) NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (profile_id, tax_period),
    CONSTRAINT chk_accounting_calculation_snapshot_period_month_start
        CHECK (EXTRACT(DAY FROM tax_period) = 1)
);

COMMENT ON TABLE investory.accounting_calculation_snapshot IS
    'Authoritative serialized calculation for locked accounting periods. Rebuilt after reopen.';
