ALTER TABLE investory.accounting_vat_transaction
    ADD COLUMN vat_rate NUMERIC(5,2);

ALTER TABLE investory.accounting_tmp_invoice
    ADD COLUMN vat_rate NUMERIC(5,2);

COMMENT ON COLUMN investory.accounting_vat_transaction.vat_rate IS
    'Explicit normalized domestic VAT rate; never reconstructed from net and VAT amounts.';
