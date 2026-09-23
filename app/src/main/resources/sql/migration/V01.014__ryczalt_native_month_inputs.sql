CREATE TABLE investory.ryczalt_native_month_input (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    tax_year INTEGER NOT NULL,
    tax_month INTEGER NOT NULL CHECK (tax_month BETWEEN 1 AND 12),
    jdg_active BOOLEAN NOT NULL,
    qualifying_uop BOOLEAN NOT NULL,
    zus_regime VARCHAR(32),
    voluntary_sickness BOOLEAN NOT NULL,
    ytd_ryczalt_revenue NUMERIC(19,4) NOT NULL DEFAULT 0,
    full_jdg_social NUMERIC(19,4),
    deductions_already_consumed NUMERIC(19,4) NOT NULL DEFAULT 0,
    sales_corrections NUMERIC(19,4) NOT NULL DEFAULT 0,
    explicit_vat_adjustments NUMERIC(19,4) NOT NULL DEFAULT 0,
    CONSTRAINT uq_ryczalt_native_month_input_period UNIQUE (profile_id, tax_year, tax_month),
    CONSTRAINT chk_ryczalt_native_month_input_nonnegative CHECK (
        ytd_ryczalt_revenue >= 0
        AND COALESCE(full_jdg_social, 0) >= 0
        AND deductions_already_consumed >= 0
    )
);

CREATE INDEX ix_ryczalt_native_month_input_profile_period
    ON investory.ryczalt_native_month_input(profile_id, tax_year, tax_month);
