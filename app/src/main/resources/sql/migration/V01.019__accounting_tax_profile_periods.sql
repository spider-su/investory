CREATE TABLE investory.accounting_tax_profile_period (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    valid_from DATE NOT NULL,
    valid_to DATE,
    jdg_active BOOLEAN NOT NULL,
    ryczalt_rate NUMERIC(8, 5),
    vat_registered BOOLEAN NOT NULL,
    vat_eu_registered BOOLEAN NOT NULL,
    zus_regime VARCHAR(32),
    voluntary_sickness BOOLEAN NOT NULL,
    CONSTRAINT chk_accounting_tax_profile_period_dates CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE INDEX ix_accounting_tax_profile_period_profile_dates
    ON investory.accounting_tax_profile_period(profile_id, valid_from, valid_to);
