ALTER TABLE investory.accounting_poc_profile
    ADD COLUMN auto_approve_known_counterparties BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE investory.accounting_trusted_counterparty_treatment (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    counterparty_id BIGINT NOT NULL REFERENCES investory.accounting_known_counterparty(id),
    source_document_id BIGINT NOT NULL REFERENCES investory.accounting_document(id),
    direction VARCHAR(16) NOT NULL,
    document_kind VARCHAR(32) NOT NULL,
    category VARCHAR(64),
    vat_treatment VARCHAR(40) NOT NULL,
    vat_deduction_ratio NUMERIC(3,2),
    vat_rate NUMERIC(5,2),
    jpk_evidence VARCHAR(32),
    confirmed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_accounting_trusted_treatment_source UNIQUE (profile_id, source_document_id),
    CONSTRAINT chk_accounting_trusted_treatment_direction CHECK (direction IN ('SALE', 'PURCHASE')),
    CONSTRAINT chk_accounting_trusted_treatment_kind CHECK (document_kind IN ('INVOICE', 'CREDIT_NOTE'))
);

CREATE INDEX ix_accounting_trusted_treatment_lookup
    ON investory.accounting_trusted_counterparty_treatment(profile_id, counterparty_id, direction);
