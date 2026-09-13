-- Canonical Accounting facts are owned by the portfolio that may reconcile them.
-- Existing POC fixtures belong to the original singleton portfolio (1).
ALTER TABLE investory.accounting_poc_invoice
    ADD COLUMN profile_id BIGINT NOT NULL DEFAULT 1
        REFERENCES investory.portfolios (id);

ALTER TABLE investory.accounting_poc_expense_invoice
    ADD COLUMN profile_id BIGINT NOT NULL DEFAULT 1
        REFERENCES investory.portfolios (id);

ALTER TABLE investory.accounting_poc_bank_transaction
    ADD COLUMN profile_id BIGINT NOT NULL DEFAULT 1
        REFERENCES investory.portfolios (id);

ALTER TABLE investory.accounting_poc_invoice
    DROP CONSTRAINT accounting_poc_invoice_reference_key;

ALTER TABLE investory.accounting_poc_expense_invoice
    DROP CONSTRAINT accounting_poc_expense_invoice_reference_key;

DROP INDEX investory.uq_accounting_poc_bank_source_row;
DROP INDEX investory.uq_accounting_poc_bank_external_transaction;

CREATE UNIQUE INDEX uq_accounting_poc_invoice_profile_reference
    ON investory.accounting_poc_invoice (profile_id, reference);

CREATE UNIQUE INDEX uq_accounting_poc_invoice_profile_ksef
    ON investory.accounting_poc_invoice (profile_id, ksef_number)
    WHERE ksef_number IS NOT NULL;

CREATE UNIQUE INDEX uq_accounting_poc_expense_profile_reference
    ON investory.accounting_poc_expense_invoice (profile_id, reference);

CREATE UNIQUE INDEX uq_accounting_poc_expense_profile_ksef
    ON investory.accounting_poc_expense_invoice (profile_id, ksef_number)
    WHERE ksef_number IS NOT NULL;

CREATE UNIQUE INDEX uq_accounting_poc_bank_profile_source_row
    ON investory.accounting_poc_bank_transaction (profile_id, source_row_identity)
    WHERE source_row_identity IS NOT NULL;

CREATE UNIQUE INDEX uq_accounting_poc_bank_profile_external_transaction
    ON investory.accounting_poc_bank_transaction
       (profile_id, provider, external_account_id, external_transaction_id);

CREATE INDEX ix_accounting_poc_invoice_profile_reference
    ON investory.accounting_poc_invoice (profile_id, reference);

CREATE INDEX ix_accounting_poc_expense_profile_reference
    ON investory.accounting_poc_expense_invoice (profile_id, reference);

CREATE INDEX ix_accounting_poc_bank_profile_match
    ON investory.accounting_poc_bank_transaction
       (profile_id, booking_date, amount, currency);
