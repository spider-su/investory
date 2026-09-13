CREATE TABLE investory.accounting_known_counterparty (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    tax_identifier VARCHAR(64) NOT NULL,
    country VARCHAR(2) NOT NULL,
    canonical_name VARCHAR(256) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT accounting_known_counterparty_key UNIQUE (profile_id, country, tax_identifier)
);

INSERT INTO investory.accounting_known_counterparty
    (profile_id, tax_identifier, country, canonical_name)
SELECT DISTINCT profile_id,
       UPPER(REGEXP_REPLACE(counterparty_tax_identifier, '[^[:alnum:]]', '', 'g')),
       UPPER(counterparty_country),
       customer_alias
  FROM investory.accounting_poc_invoice
 WHERE counterparty_tax_identifier IS NOT NULL
   AND counterparty_country IS NOT NULL
   AND customer_alias IS NOT NULL
ON CONFLICT (profile_id, country, tax_identifier) DO NOTHING;

CREATE INDEX ix_accounting_known_counterparty_lookup
    ON investory.accounting_known_counterparty (profile_id, country, tax_identifier);
