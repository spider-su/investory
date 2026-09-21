CREATE TABLE investory.ryczalt_period (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    period_year INTEGER NOT NULL,
    period_month INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    calculated_at TIMESTAMPTZ,
    frozen_at TIMESTAMPTZ,
    CONSTRAINT uq_ryczalt_period_profile_month UNIQUE (profile_id, period_year, period_month),
    CONSTRAINT chk_ryczalt_period_month CHECK (period_month BETWEEN 1 AND 12),
    CONSTRAINT chk_ryczalt_period_status CHECK (status IN ('OPEN', 'DIRTY', 'CALCULATED', 'PAID', 'FROZEN'))
);

CREATE INDEX ix_ryczalt_period_profile_status
    ON investory.ryczalt_period(profile_id, status, period_year, period_month);

CREATE TABLE investory.ryczalt_invoice (
    id BIGSERIAL PRIMARY KEY,
    period_id BIGINT NOT NULL REFERENCES investory.ryczalt_period(id) ON DELETE CASCADE,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    direction VARCHAR(8) NOT NULL,
    reference VARCHAR(128) NOT NULL,
    issue_date DATE NOT NULL,
    accounting_date DATE NOT NULL,
    net_amount NUMERIC(19,4) NOT NULL,
    vat_amount NUMERIC(19,4) NOT NULL,
    gross_amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    booked_net_pln NUMERIC(19,4),
    ryczalt_rate NUMERIC(7,4),
    deductible_vat NUMERIC(19,4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ryczalt_invoice_source UNIQUE (profile_id, direction, reference),
    CONSTRAINT chk_ryczalt_invoice_direction CHECK (direction IN ('INCOME', 'COST')),
    CONSTRAINT chk_ryczalt_invoice_currency CHECK (length(btrim(currency)) = 3)
);

CREATE INDEX ix_ryczalt_invoice_period ON investory.ryczalt_invoice(profile_id, period_id, accounting_date, id);

CREATE TABLE investory.ryczalt_transaction (
    id BIGSERIAL PRIMARY KEY,
    period_id BIGINT NOT NULL REFERENCES investory.ryczalt_period(id) ON DELETE CASCADE,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    booking_date DATE NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    reference VARCHAR(256),
    counterparty VARCHAR(256),
    description VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ryczalt_transaction_source UNIQUE (profile_id, reference),
    CONSTRAINT chk_ryczalt_transaction_currency CHECK (length(btrim(currency)) = 3)
);

CREATE INDEX ix_ryczalt_transaction_period ON investory.ryczalt_transaction(profile_id, period_id, booking_date, id);

CREATE TABLE investory.ryczalt_calculation (
    id BIGSERIAL PRIMARY KEY,
    period_id BIGINT NOT NULL REFERENCES investory.ryczalt_period(id) ON DELETE CASCADE,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    calculation_type VARCHAR(8) NOT NULL,
    status VARCHAR(16) NOT NULL,
    result_json JSONB NOT NULL,
    input_fingerprint VARCHAR(128) NOT NULL,
    rule_version VARCHAR(64) NOT NULL,
    calculator_version VARCHAR(64) NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_ryczalt_calculation_period_type UNIQUE (profile_id, period_id, calculation_type),
    CONSTRAINT chk_ryczalt_calculation_type CHECK (calculation_type IN ('RYCZALT', 'VAT', 'ZUS')),
    CONSTRAINT chk_ryczalt_calculation_status CHECK (status IN ('CURRENT', 'STALE', 'FROZEN'))
);

CREATE INDEX ix_ryczalt_calculation_period ON investory.ryczalt_calculation(profile_id, period_id, calculation_type);

CREATE TABLE investory.ryczalt_obligation (
    id BIGSERIAL PRIMARY KEY,
    period_id BIGINT NOT NULL REFERENCES investory.ryczalt_period(id) ON DELETE CASCADE,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    obligation_type VARCHAR(8) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    due_date DATE,
    status VARCHAR(16) NOT NULL,
    calculation_id BIGINT REFERENCES investory.ryczalt_calculation(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ryczalt_obligation_period_type UNIQUE (profile_id, period_id, obligation_type),
    CONSTRAINT chk_ryczalt_obligation_type CHECK (obligation_type IN ('RYCZALT', 'VAT', 'ZUS')),
    CONSTRAINT chk_ryczalt_obligation_status CHECK (status IN ('OPEN', 'PAID', 'FROZEN'))
);

CREATE INDEX ix_ryczalt_obligation_period ON investory.ryczalt_obligation(profile_id, period_id, obligation_type);

CREATE TABLE investory.ryczalt_fx_rate (
    id BIGSERIAL PRIMARY KEY,
    currency CHAR(3) NOT NULL,
    effective_date DATE NOT NULL,
    rate NUMERIC(19,8) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    provider_reference VARCHAR(256),
    fetched_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_ryczalt_fx_identity UNIQUE (provider, currency, effective_date),
    CONSTRAINT chk_ryczalt_fx_rate_positive CHECK (rate > 0)
);

CREATE INDEX ix_ryczalt_fx_lookup ON investory.ryczalt_fx_rate(currency, effective_date);

CREATE TABLE investory.ryczalt_source_reference (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    entity_type VARCHAR(32) NOT NULL,
    entity_id BIGINT NOT NULL,
    source VARCHAR(64) NOT NULL,
    external_id VARCHAR(256) NOT NULL,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ryczalt_source_reference UNIQUE (profile_id, entity_type, source, external_id)
);

CREATE INDEX ix_ryczalt_source_entity ON investory.ryczalt_source_reference(profile_id, entity_type, entity_id);
