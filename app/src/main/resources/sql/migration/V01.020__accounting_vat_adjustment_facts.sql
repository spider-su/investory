CREATE TABLE IF NOT EXISTS investory.accounting_vat_adjustment (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    tax_period DATE NOT NULL,
    adjustment_type TEXT NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    source_system TEXT NOT NULL,
    source_reference TEXT NOT NULL,
    affects TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_accounting_vat_adjustment_period_month_start
        CHECK (tax_period = date_trunc('month', tax_period)::date),
    CONSTRAINT chk_accounting_vat_adjustment_affects
        CHECK (affects IN ('OUTPUT_VAT', 'INPUT_VAT', 'PAYABLE_VAT')),
    CONSTRAINT uq_accounting_vat_adjustment_source
        UNIQUE (profile_id, tax_period, adjustment_type, source_system, source_reference)
);

COMMENT ON TABLE investory.accounting_vat_adjustment IS
    'Traceable historical VAT adjustment facts; amounts are signed and added to VAT payable.';
