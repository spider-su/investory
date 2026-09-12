ALTER TABLE investory.accounting_poc_invoice
    ADD COLUMN source_id BIGINT REFERENCES investory.accounting_source_evidence (id);

ALTER TABLE investory.accounting_poc_expense_invoice
    ADD COLUMN source_id BIGINT REFERENCES investory.accounting_source_evidence (id);

CREATE INDEX idx_accounting_poc_invoice_source_id
    ON investory.accounting_poc_invoice (source_id);

CREATE INDEX idx_accounting_poc_expense_source_id
    ON investory.accounting_poc_expense_invoice (source_id);
