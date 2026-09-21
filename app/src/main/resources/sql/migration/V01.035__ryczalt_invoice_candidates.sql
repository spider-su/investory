CREATE TABLE investory.ryczalt_invoice_candidate (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    candidate_key UUID NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_external_id VARCHAR(256) NOT NULL,
    document_type VARCHAR(64) NOT NULL,
    direction VARCHAR(8) NOT NULL CHECK (direction IN ('INCOME', 'COST')),
    issue_date DATE NOT NULL,
    sale_date DATE,
    due_date DATE,
    reference VARCHAR(128) NOT NULL,
    seller_legal_name VARCHAR(512),
    seller_tax_identifier VARCHAR(64),
    seller_country VARCHAR(2),
    buyer_legal_name VARCHAR(512),
    buyer_tax_identifier VARCHAR(64),
    buyer_country VARCHAR(2),
    currency VARCHAR(3) NOT NULL,
    net_amount NUMERIC(19,4) NOT NULL,
    vat_amount NUMERIC(19,4) NOT NULL,
    gross_amount NUMERIC(19,4) NOT NULL,
    counterparty_id BIGINT REFERENCES investory.ryczalt_counterparty(id),
    source_metadata JSONB,
    confidence NUMERIC(7,6),
    service_key VARCHAR(128),
    classification VARCHAR(128),
    vat_treatment VARCHAR(128),
    vat_deduction_ratio NUMERIC(7,6),
    ryczalt_rate NUMERIC(7,4),
    approval_status VARCHAR(16) NOT NULL DEFAULT 'NEEDS_REVIEW',
    approval_source VARCHAR(32),
    payment_verification_policy VARCHAR(16) NOT NULL DEFAULT 'REQUIRED',
    required_inputs JSONB NOT NULL DEFAULT '[]'::jsonb,
    duplicate BOOLEAN NOT NULL DEFAULT FALSE,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    period_year INTEGER NOT NULL,
    period_month INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ryczalt_candidate_key UNIQUE (profile_id, candidate_key),
    CONSTRAINT uq_ryczalt_candidate_source UNIQUE (profile_id, source_type, source_external_id)
);

CREATE INDEX ix_ryczalt_candidate_profile_period
    ON investory.ryczalt_invoice_candidate(profile_id, period_year, period_month);

CREATE OR REPLACE FUNCTION investory.touch_ryczalt_invoice_candidate_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ryczalt_invoice_candidate_touch_updated_at
BEFORE UPDATE ON investory.ryczalt_invoice_candidate
FOR EACH ROW EXECUTE FUNCTION investory.touch_ryczalt_invoice_candidate_updated_at();
