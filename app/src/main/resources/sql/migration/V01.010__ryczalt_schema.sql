SET search_path TO investory, public;

-- Operational Ryczalt schema. Keep the complete current shape here: later
-- corrections belong in this definition, not in follow-up ALTER migrations.
CREATE TABLE investory.ryczalt_period (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    period_year INTEGER NOT NULL,
    period_month INTEGER NOT NULL CHECK (period_month BETWEEN 1 AND 12),
    status VARCHAR(16) NOT NULL CHECK (status IN ('OPEN','DIRTY','CALCULATED','PAID','FROZEN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    calculated_at TIMESTAMPTZ,
    frozen_at TIMESTAMPTZ,
    reopened_at TIMESTAMPTZ,
    reopen_reason VARCHAR(1000),
    CONSTRAINT uq_ryczalt_period_profile_month UNIQUE (profile_id, period_year, period_month),
    CONSTRAINT chk_ryczalt_period_reopen_reason CHECK (reopened_at IS NULL OR length(btrim(reopen_reason)) > 0)
);
CREATE INDEX ix_ryczalt_period_profile_status ON investory.ryczalt_period(profile_id, status, period_year, period_month);

CREATE TABLE investory.ryczalt_counterparty (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    tax_identifier VARCHAR(64), country VARCHAR(2) NOT NULL, legal_name VARCHAR(512) NOT NULL,
    alias VARCHAR(256), bank_account VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ryczalt_counterparty_profile_id UNIQUE (profile_id, id)
);
CREATE UNIQUE INDEX uq_ryczalt_counterparty_tax ON investory.ryczalt_counterparty(profile_id, tax_identifier, country) WHERE tax_identifier IS NOT NULL;
CREATE INDEX ix_ryczalt_counterparty_profile ON investory.ryczalt_counterparty(profile_id, legal_name);
CREATE INDEX ix_ryczalt_counterparty_bank_account ON investory.ryczalt_counterparty(profile_id, bank_account) WHERE bank_account IS NOT NULL;

CREATE TABLE investory.ryczalt_invoice (
    id BIGSERIAL PRIMARY KEY,
    period_id BIGINT NOT NULL REFERENCES investory.ryczalt_period(id) ON DELETE CASCADE,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    direction VARCHAR(8) NOT NULL CHECK (direction IN ('INCOME','COST')),
    reference VARCHAR(128) NOT NULL, issue_date DATE NOT NULL, accounting_date DATE NOT NULL,
    net_amount NUMERIC(19,4) NOT NULL, vat_amount NUMERIC(19,4) NOT NULL, gross_amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL CHECK (length(btrim(currency)) = 3), booked_net_pln NUMERIC(19,4),
    ryczalt_rate NUMERIC(7,4), deductible_vat NUMERIC(19,4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    counterparty_id BIGINT, approval_status VARCHAR(16) NOT NULL DEFAULT 'NEEDS_REVIEW' CHECK (approval_status IN ('NEEDS_REVIEW','APPROVED')),
    approval_method VARCHAR(32), payment_verification_policy VARCHAR(16) NOT NULL DEFAULT 'REQUIRED' CHECK (payment_verification_policy IN ('REQUIRED','NOT_REQUIRED')),
    classification VARCHAR(128), vat_treatment VARCHAR(128), vat_deduction_ratio NUMERIC(7,6),
    payment_status VARCHAR(20) NOT NULL DEFAULT 'UNMATCHED' CHECK (payment_status IN ('MATCHED','PARTIALLY_MATCHED','UNMATCHED','MANUALLY_CONFIRMED','NOT_REQUIRED')),
    manual_paid_date DATE, manual_paid_note VARCHAR(1000),
    CONSTRAINT chk_ryczalt_invoice_manual_paid_data CHECK ((payment_status = 'MANUALLY_CONFIRMED' AND manual_paid_date IS NOT NULL) OR payment_status <> 'MANUALLY_CONFIRMED'),
    CONSTRAINT fk_ryczalt_invoice_profile_counterparty FOREIGN KEY (profile_id, counterparty_id) REFERENCES investory.ryczalt_counterparty(profile_id, id)
);
CREATE INDEX ix_ryczalt_invoice_period ON investory.ryczalt_invoice(profile_id, period_id, accounting_date, id);
CREATE INDEX ix_ryczalt_invoice_counterparty ON investory.ryczalt_invoice(profile_id, counterparty_id, accounting_date, id);
CREATE INDEX ix_ryczalt_invoice_profile_direction_reference ON investory.ryczalt_invoice(profile_id, direction, reference);

CREATE TABLE investory.ryczalt_transaction (
    id BIGSERIAL PRIMARY KEY, period_id BIGINT NOT NULL REFERENCES investory.ryczalt_period(id) ON DELETE CASCADE,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    booking_date DATE NOT NULL, amount NUMERIC(19,4) NOT NULL, currency VARCHAR(3) NOT NULL CHECK (length(btrim(currency)) = 3),
    reference VARCHAR(256), counterparty VARCHAR(256), counterparty_account VARCHAR(64), description VARCHAR(1000),
    excluded_from_payment_matching BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ryczalt_transaction_profile_id UNIQUE (profile_id, id)
);
CREATE INDEX ix_ryczalt_transaction_period ON investory.ryczalt_transaction(profile_id, period_id, booking_date, id);
CREATE INDEX ix_ryczalt_transaction_reference ON investory.ryczalt_transaction(profile_id, reference);
CREATE INDEX ix_ryczalt_transaction_counterparty_account ON investory.ryczalt_transaction(profile_id, counterparty_account) WHERE counterparty_account IS NOT NULL;

CREATE TABLE investory.ryczalt_calculation (
    id BIGSERIAL PRIMARY KEY, period_id BIGINT NOT NULL REFERENCES investory.ryczalt_period(id) ON DELETE CASCADE,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    calculation_type VARCHAR(8) NOT NULL CHECK (calculation_type IN ('RYCZALT','VAT','ZUS')),
    status VARCHAR(16) NOT NULL CHECK (status IN ('CURRENT','DIRTY','STALE','FROZEN','CALCULATED')),
    revision INTEGER NOT NULL DEFAULT 1, is_current BOOLEAN NOT NULL DEFAULT TRUE, result_json JSONB NOT NULL,
    input_fingerprint VARCHAR(128) NOT NULL, rule_version VARCHAR(64) NOT NULL, calculator_version VARCHAR(64) NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_ryczalt_calculation_period ON investory.ryczalt_calculation(profile_id, period_id, calculation_type);
CREATE UNIQUE INDEX uq_ryczalt_calculation_current ON investory.ryczalt_calculation(profile_id, period_id, calculation_type) WHERE is_current;
CREATE UNIQUE INDEX uq_ryczalt_calculation_revision ON investory.ryczalt_calculation(profile_id, period_id, calculation_type, revision);

CREATE TABLE investory.ryczalt_obligation (
    id BIGSERIAL PRIMARY KEY, period_id BIGINT NOT NULL REFERENCES investory.ryczalt_period(id) ON DELETE CASCADE,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    obligation_type VARCHAR(8) NOT NULL CHECK (obligation_type IN ('RYCZALT','VAT','ZUS')),
    amount NUMERIC(19,4) NOT NULL, currency VARCHAR(3) NOT NULL, due_date DATE, status VARCHAR(16) NOT NULL CHECK (status IN ('OPEN','PARTIALLY_PAID','PAID','OVERPAID','FROZEN')),
    calculation_id BIGINT REFERENCES investory.ryczalt_calculation(id), manual_paid_date DATE, manual_paid_note VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ryczalt_obligation_period_type UNIQUE (profile_id, period_id, obligation_type),
    CONSTRAINT uq_ryczalt_obligation_profile_id UNIQUE (profile_id, id)
);
CREATE INDEX ix_ryczalt_obligation_period ON investory.ryczalt_obligation(profile_id, period_id, obligation_type);

CREATE TABLE investory.ryczalt_fx_rate (
    id BIGSERIAL PRIMARY KEY, currency VARCHAR(3) NOT NULL, effective_date DATE NOT NULL, rate NUMERIC(19,8) NOT NULL CHECK (rate > 0),
    provider VARCHAR(64) NOT NULL, provider_reference VARCHAR(256), fetched_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_ryczalt_fx_identity UNIQUE (provider, currency, effective_date)
);
CREATE INDEX ix_ryczalt_fx_lookup ON investory.ryczalt_fx_rate(currency, effective_date);

CREATE TABLE investory.ryczalt_source_reference (
    id BIGSERIAL PRIMARY KEY, profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    entity_type VARCHAR(32) NOT NULL, entity_id BIGINT NOT NULL, source VARCHAR(64) NOT NULL, external_id VARCHAR(256) NOT NULL,
    metadata JSONB, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ryczalt_source_reference UNIQUE (profile_id, entity_type, source, external_id)
);
CREATE INDEX ix_ryczalt_source_entity ON investory.ryczalt_source_reference(profile_id, entity_type, entity_id);

CREATE TABLE investory.ryczalt_correction (
    id BIGSERIAL PRIMARY KEY, profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    original_period_id BIGINT NOT NULL REFERENCES investory.ryczalt_period(id), affected_entity_type VARCHAR(32) NOT NULL,
    affected_entity_id BIGINT NOT NULL, reason VARCHAR(1000) NOT NULL CHECK (length(btrim(reason)) > 0),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, correction_period_id BIGINT REFERENCES investory.ryczalt_period(id)
);
CREATE INDEX ix_ryczalt_correction_original_period ON investory.ryczalt_correction(profile_id, original_period_id, requested_at);

CREATE TABLE investory.ryczalt_audit_event (
    id BIGSERIAL PRIMARY KEY, profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    period_id BIGINT REFERENCES investory.ryczalt_period(id), event_type VARCHAR(32) NOT NULL, reason VARCHAR(1000), actor VARCHAR(256),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, metadata JSONB
);
CREATE INDEX ix_ryczalt_audit_period ON investory.ryczalt_audit_event(profile_id, period_id, occurred_at);

CREATE TABLE investory.ryczalt_payment_match (
    id BIGSERIAL PRIMARY KEY, profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    obligation_id BIGINT NOT NULL, transaction_id BIGINT NOT NULL, matched_amount NUMERIC(19,4) NOT NULL CHECK (matched_amount > 0),
    match_type VARCHAR(16) NOT NULL CHECK (match_type IN ('AUTO','MANUAL')), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ryczalt_payment_match_pair UNIQUE (profile_id, obligation_id, transaction_id),
    CONSTRAINT fk_ryczalt_payment_match_obligation FOREIGN KEY (profile_id, obligation_id) REFERENCES investory.ryczalt_obligation(profile_id, id),
    CONSTRAINT fk_ryczalt_payment_match_transaction FOREIGN KEY (profile_id, transaction_id) REFERENCES investory.ryczalt_transaction(profile_id, id)
);
CREATE INDEX ix_ryczalt_payment_match_obligation ON investory.ryczalt_payment_match(profile_id, obligation_id, created_at);
CREATE INDEX ix_ryczalt_payment_match_transaction ON investory.ryczalt_payment_match(profile_id, transaction_id, created_at);

CREATE TABLE investory.ryczalt_payment_account_rule (
    id BIGSERIAL PRIMARY KEY, profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    obligation_type VARCHAR(32) NOT NULL CHECK (obligation_type IN ('RYCZALT','VAT','ZUS')), account_number VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ryczalt_payment_account_rule UNIQUE (profile_id, obligation_type, account_number),
    CONSTRAINT chk_ryczalt_payment_account_rule_account CHECK (length(btrim(account_number)) > 0)
);
CREATE INDEX ix_ryczalt_payment_account_rule_profile ON investory.ryczalt_payment_account_rule(profile_id, obligation_type);

CREATE TABLE investory.ryczalt_native_month_input (
    id BIGSERIAL PRIMARY KEY, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE, tax_year INTEGER NOT NULL, tax_month INTEGER NOT NULL CHECK (tax_month BETWEEN 1 AND 12),
    jdg_active BOOLEAN NOT NULL, qualifying_uop BOOLEAN NOT NULL, zus_regime VARCHAR(32), voluntary_sickness BOOLEAN NOT NULL,
    ytd_ryczalt_revenue NUMERIC(19,4) NOT NULL DEFAULT 0, full_jdg_social NUMERIC(19,4), social_contribution_deduction NUMERIC(19,4),
    health_contribution_override NUMERIC(19,4), health_contribution_paid_override NUMERIC(19,4), deductions_already_consumed NUMERIC(19,4) NOT NULL DEFAULT 0,
    sales_corrections NUMERIC(19,4) NOT NULL DEFAULT 0, explicit_vat_adjustments NUMERIC(19,4) NOT NULL DEFAULT 0,
    CONSTRAINT uq_ryczalt_native_month_input_period UNIQUE (profile_id, tax_year, tax_month),
    CONSTRAINT chk_ryczalt_native_month_input_nonnegative CHECK (ytd_ryczalt_revenue >= 0 AND COALESCE(full_jdg_social,0) >= 0 AND COALESCE(social_contribution_deduction,0) >= 0 AND COALESCE(health_contribution_override,0) >= 0 AND COALESCE(health_contribution_paid_override,0) >= 0 AND deductions_already_consumed >= 0)
);
CREATE INDEX ix_ryczalt_native_month_input_profile_period ON investory.ryczalt_native_month_input(profile_id, tax_year, tax_month);

CREATE TABLE investory.ryczalt_counterparty_rule (
    id BIGSERIAL PRIMARY KEY, profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE, counterparty_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL, source_type VARCHAR(32), document_type VARCHAR(64), service_key VARCHAR(256), classification VARCHAR(64), vat_treatment VARCHAR(64),
    vat_deduction_ratio NUMERIC(7,4), ryczalt_rate NUMERIC(7,4), auto_approve BOOLEAN NOT NULL DEFAULT FALSE,
    payment_verification_policy VARCHAR(16) NOT NULL DEFAULT 'REQUIRED' CHECK (payment_verification_policy IN ('REQUIRED','NOT_REQUIRED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ryczalt_rule_profile_counterparty FOREIGN KEY (profile_id, counterparty_id) REFERENCES investory.ryczalt_counterparty(profile_id, id)
);
CREATE INDEX ix_ryczalt_rule_counterparty ON investory.ryczalt_counterparty_rule(profile_id, counterparty_id, name);

CREATE TABLE investory.ryczalt_invoice_candidate (
    id BIGSERIAL PRIMARY KEY, profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id), candidate_key UUID NOT NULL, source_type VARCHAR(32) NOT NULL,
    source_external_id VARCHAR(256) NOT NULL, document_type VARCHAR(64) NOT NULL, direction VARCHAR(8) NOT NULL CHECK (direction IN ('INCOME','COST')),
    issue_date DATE NOT NULL, sale_date DATE, due_date DATE, reference VARCHAR(128) NOT NULL, seller_legal_name VARCHAR(512), seller_tax_identifier VARCHAR(64), seller_country VARCHAR(2), buyer_legal_name VARCHAR(512), buyer_tax_identifier VARCHAR(64), buyer_country VARCHAR(2), currency VARCHAR(3) NOT NULL, net_amount NUMERIC(19,4) NOT NULL, vat_amount NUMERIC(19,4) NOT NULL, gross_amount NUMERIC(19,4) NOT NULL, counterparty_id BIGINT, source_metadata JSONB, confidence NUMERIC(7,6), service_key VARCHAR(128), classification VARCHAR(128), vat_treatment VARCHAR(128), vat_deduction_ratio NUMERIC(7,6), ryczalt_rate NUMERIC(7,4), approval_status VARCHAR(16) NOT NULL DEFAULT 'NEEDS_REVIEW', approval_source VARCHAR(32), payment_verification_policy VARCHAR(16) NOT NULL DEFAULT 'REQUIRED', required_inputs JSONB NOT NULL DEFAULT '[]'::jsonb, duplicate BOOLEAN NOT NULL DEFAULT FALSE, consumed BOOLEAN NOT NULL DEFAULT FALSE, version BIGINT NOT NULL DEFAULT 0, rule_match_status VARCHAR(16) NOT NULL DEFAULT 'NO_MATCH' CHECK (rule_match_status IN ('MATCHED','NO_MATCH','AMBIGUOUS')), period_year INTEGER NOT NULL, period_month INTEGER NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ryczalt_candidate_key UNIQUE (profile_id, candidate_key), CONSTRAINT uq_ryczalt_candidate_source UNIQUE (profile_id, source_type, source_external_id),
    CONSTRAINT fk_ryczalt_candidate_profile_counterparty FOREIGN KEY (profile_id, counterparty_id) REFERENCES investory.ryczalt_counterparty(profile_id, id)
);
CREATE INDEX ix_ryczalt_candidate_profile_period ON investory.ryczalt_invoice_candidate(profile_id, period_year, period_month);

CREATE TABLE investory.ryczalt_obligation_reference (
    id BIGINT PRIMARY KEY, profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id), tax_period DATE NOT NULL,
    obligation_type VARCHAR(32) NOT NULL, due_date DATE, expected_amount NUMERIC(19,4) NOT NULL, paid_amount NUMERIC(19,4), payment_date DATE, status VARCHAR(32) NOT NULL, note VARCHAR(512),
    CONSTRAINT chk_ryczalt_obligation_reference_period_month_start CHECK (EXTRACT(DAY FROM tax_period) = 1)
);
CREATE INDEX idx_ryczalt_obligation_reference_profile_period ON investory.ryczalt_obligation_reference(profile_id, tax_period, id);
COMMENT ON TABLE investory.ryczalt_obligation_reference IS 'Persisted reference obligations used to compare native Ryczalt calculations with supplied reference values.';

CREATE OR REPLACE FUNCTION investory.touch_ryczalt_invoice_candidate_updated_at() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN NEW.updated_at = now(); RETURN NEW; END; $$;
CREATE TRIGGER trg_ryczalt_invoice_candidate_touch_updated_at BEFORE UPDATE ON investory.ryczalt_invoice_candidate FOR EACH ROW EXECUTE FUNCTION investory.touch_ryczalt_invoice_candidate_updated_at();
