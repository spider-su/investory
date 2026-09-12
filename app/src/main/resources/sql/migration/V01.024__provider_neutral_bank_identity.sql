ALTER TABLE investory.accounting_poc_bank_transaction
    ADD COLUMN provider VARCHAR(32),
    ADD COLUMN external_account_id VARCHAR(256),
    ADD COLUMN external_transaction_id VARCHAR(256),
    ADD COLUMN source_payload_hash VARCHAR(128);

UPDATE investory.accounting_poc_bank_transaction
   SET provider = 'CSV',
       external_account_id = 'LEGACY_SOURCE',
       external_transaction_id = COALESCE(source_row_identity, 'legacy-' || id::varchar),
       source_payload_hash = NULL
 WHERE provider IS NULL;

ALTER TABLE investory.accounting_poc_bank_transaction
    ALTER COLUMN provider SET NOT NULL,
    ALTER COLUMN external_account_id SET NOT NULL,
    ALTER COLUMN external_transaction_id SET NOT NULL;

CREATE UNIQUE INDEX uq_accounting_poc_bank_external_transaction
    ON investory.accounting_poc_bank_transaction
       (provider, external_account_id, external_transaction_id);

COMMENT ON COLUMN investory.accounting_poc_bank_transaction.provider IS
    'Neutral bank data provider identity; classification remains Accounting-owned.';
COMMENT ON COLUMN investory.accounting_poc_bank_transaction.external_transaction_id IS
    'Provider or deterministic adapter transaction identity used for idempotent ingestion.';
