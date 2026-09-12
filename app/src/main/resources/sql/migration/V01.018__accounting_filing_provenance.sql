ALTER TABLE investory.accounting_poc_invoice
    ADD COLUMN counterparty_tax_identifier VARCHAR(32),
    ADD COLUMN counterparty_country VARCHAR(2),
    ADD COLUMN ksef_number VARCHAR(256),
    ADD COLUMN filing_evidence VARCHAR(8);

ALTER TABLE investory.accounting_poc_expense_invoice
    ADD COLUMN counterparty_tax_identifier VARCHAR(32),
    ADD COLUMN counterparty_country VARCHAR(2),
    ADD COLUMN ksef_number VARCHAR(256),
    ADD COLUMN filing_evidence VARCHAR(8);
