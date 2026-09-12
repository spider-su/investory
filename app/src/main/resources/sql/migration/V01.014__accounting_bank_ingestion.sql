ALTER TABLE investory.accounting_poc_bank_transaction
    ADD COLUMN source_id BIGINT REFERENCES investory.accounting_source_evidence (id),
    ADD COLUMN source_row_identity VARCHAR(256);

CREATE UNIQUE INDEX uq_accounting_poc_bank_source_row
    ON investory.accounting_poc_bank_transaction (source_row_identity)
    WHERE source_row_identity IS NOT NULL;

ALTER TABLE investory.accounting_source_evidence
    DROP CONSTRAINT chk_accounting_source_type;

ALTER TABLE investory.accounting_source_evidence
    ADD CONSTRAINT chk_accounting_source_type
    CHECK (source_type IN ('KSEF', 'UPLOAD', 'BANK'));

CREATE INDEX idx_accounting_poc_bank_source_id
    ON investory.accounting_poc_bank_transaction (source_id);
