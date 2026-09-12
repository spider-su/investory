CREATE TABLE investory.accounting_filing_artifact (
    id BIGSERIAL PRIMARY KEY,
    artifact_type VARCHAR(40) NOT NULL,
    tax_period DATE NOT NULL,
    schema_version VARCHAR(40) NOT NULL,
    payload BYTEA NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL,
    CONSTRAINT uq_accounting_filing_artifact_hash UNIQUE (artifact_type, tax_period, payload_hash)
);

CREATE TABLE investory.accounting_authority_confirmation (
    id BIGSERIAL PRIMARY KEY,
    authority VARCHAR(32) NOT NULL,
    obligation_or_artifact_type VARCHAR(40) NOT NULL,
    tax_period DATE NOT NULL,
    external_reference VARCHAR(256) NOT NULL,
    confirmation_type VARCHAR(40) NOT NULL,
    status VARCHAR(16) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    source_document_id BIGINT,
    note VARCHAR(1000)
);

CREATE INDEX ix_accounting_authority_confirmation_period
    ON investory.accounting_authority_confirmation(tax_period, obligation_or_artifact_type);
