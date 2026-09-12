CREATE TABLE investory.accounting_vat_transaction (
    id BIGSERIAL PRIMARY KEY,
    tax_period DATE NOT NULL,
    tax_date DATE NOT NULL,
    source_document_id VARCHAR(256) NOT NULL,
    reference VARCHAR(256) NOT NULL,
    direction VARCHAR(16) NOT NULL,
    treatment VARCHAR(48) NOT NULL,
    counterparty_country VARCHAR(2),
    counterparty_tax_identifier VARCHAR(64),
    identifier_type VARCHAR(16),
    vat_eu_number VARCHAR(64),
    vies_verified_at DATE,
    vies_status VARCHAR(24),
    net_amount NUMERIC(18, 2) NOT NULL,
    vat_amount NUMERIC(18, 2) NOT NULL,
    deductible_vat NUMERIC(18, 2) NOT NULL,
    evidence VARCHAR(256) NOT NULL
);

CREATE INDEX ix_accounting_vat_transaction_period
    ON investory.accounting_vat_transaction(tax_period, id);
