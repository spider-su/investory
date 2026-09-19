CREATE TABLE investory.ryczalt_counterparty (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    tax_identifier VARCHAR(64),
    country CHAR(2) NOT NULL,
    legal_name VARCHAR(512) NOT NULL,
    alias VARCHAR(256),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_ryczalt_counterparty_tax ON investory.ryczalt_counterparty(profile_id, tax_identifier, country) WHERE tax_identifier IS NOT NULL;
CREATE INDEX ix_ryczalt_counterparty_profile ON investory.ryczalt_counterparty(profile_id, legal_name);

CREATE TABLE investory.ryczalt_counterparty_rule (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    counterparty_id BIGINT NOT NULL REFERENCES investory.ryczalt_counterparty(id) ON DELETE CASCADE,
    name VARCHAR(128) NOT NULL,
    source_type VARCHAR(32), document_type VARCHAR(64), service_key VARCHAR(256),
    classification VARCHAR(64), vat_treatment VARCHAR(64),
    vat_deduction_ratio NUMERIC(7,4), ryczalt_rate NUMERIC(7,4),
    auto_approve BOOLEAN NOT NULL DEFAULT FALSE,
    payment_verification_policy VARCHAR(16) NOT NULL DEFAULT 'REQUIRED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_ryczalt_rule_payment_policy CHECK (payment_verification_policy IN ('REQUIRED','NOT_REQUIRED'))
);
CREATE INDEX ix_ryczalt_rule_counterparty ON investory.ryczalt_counterparty_rule(profile_id, counterparty_id, name);

ALTER TABLE investory.ryczalt_invoice ADD COLUMN counterparty_id BIGINT REFERENCES investory.ryczalt_counterparty(id);
ALTER TABLE investory.ryczalt_invoice ADD COLUMN approval_status VARCHAR(16) NOT NULL DEFAULT 'NEEDS_REVIEW';
ALTER TABLE investory.ryczalt_invoice ADD COLUMN approval_method VARCHAR(32);
ALTER TABLE investory.ryczalt_invoice ADD COLUMN payment_verification_policy VARCHAR(16) NOT NULL DEFAULT 'REQUIRED';
ALTER TABLE investory.ryczalt_invoice ADD CONSTRAINT chk_ryczalt_invoice_approval CHECK (approval_status IN ('NEEDS_REVIEW','APPROVED'));
ALTER TABLE investory.ryczalt_invoice ADD CONSTRAINT chk_ryczalt_invoice_payment_policy CHECK (payment_verification_policy IN ('REQUIRED','NOT_REQUIRED'));
CREATE INDEX ix_ryczalt_invoice_counterparty ON investory.ryczalt_invoice(profile_id, counterparty_id, accounting_date, id);
