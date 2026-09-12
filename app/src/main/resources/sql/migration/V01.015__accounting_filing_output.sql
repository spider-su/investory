ALTER TABLE investory.accounting_poc_profile
    ADD COLUMN nip VARCHAR(10),
    ADD COLUMN full_name VARCHAR(240),
    ADD COLUMN tax_office_code VARCHAR(4),
    ADD COLUMN email VARCHAR(255),
    ADD COLUMN vat_payment_account VARCHAR(34),
    ADD COLUMN ryczalt_payment_account VARCHAR(34),
    ADD COLUMN zus_payment_account VARCHAR(34);

UPDATE investory.accounting_poc_profile
   SET nip = COALESCE(nip, '1010000000'),
       full_name = COALESCE(full_name, 'Investory Accounting POC'),
       tax_office_code = COALESCE(tax_office_code, '1215'),
       email = COALESCE(email, 'accounting@example.invalid');

CREATE TABLE investory.accounting_poc_period_state (
    tax_period DATE PRIMARY KEY,
    confirmed_at TIMESTAMPTZ,
    confirmed_calculation_hash VARCHAR(64)
);
