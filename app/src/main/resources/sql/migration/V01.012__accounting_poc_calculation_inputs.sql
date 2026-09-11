ALTER TABLE investory.accounting_poc_invoice
    ADD COLUMN fx_rate_date DATE,
    ADD COLUMN correction_net_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    ADD COLUMN correction_vat_amount NUMERIC(19, 4) NOT NULL DEFAULT 0;

UPDATE investory.accounting_poc_invoice
   SET correction_net_amount = -150.0000,
       correction_vat_amount = -34.5000
 WHERE reference = 'FV 4/2026';

UPDATE investory.accounting_poc_invoice
   SET issue_date = '2026-07-31',
       sale_date = '2026-07-31',
       fx_rate_date = '2026-07-30',
       note = 'Recurring EU service. Tax value uses Investory FX on 2026-07-30; 32,908.87 PLN remains the observed accounting golden value.'
 WHERE reference = 'EU-SERVICE-2026-07';

UPDATE investory.accounting_poc_bank_transaction
   SET related_period = '2026-06-01',
       note = 'SEPA receipt for the prior monthly EU service; retained to prevent false July matching.'
 WHERE booking_date = '2026-07-03'
   AND counterparty_alias = 'CUSTOMER_EU_001'
   AND currency = 'EUR'
   AND amount = 7636.0000;

INSERT INTO investory.accounting_poc_bank_transaction
    (booking_date, related_period, reference, counterparty_alias, currency, amount,
     transaction_type, scope, note)
VALUES
    ('2026-08-05', '2026-07-01', 'Service Agreements', 'CUSTOMER_EU_001', 'EUR', 7636.0000,
     'CUSTOMER_RECEIPT', 'BUSINESS', 'SEPA receipt matched to the July EU service fixture.');

CREATE TABLE investory.accounting_poc_tax_input (
    id BIGSERIAL PRIMARY KEY,
    tax_period DATE NOT NULL,
    input_type VARCHAR(64) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    note VARCHAR(512),
    UNIQUE (tax_period, input_type)
);

COMMENT ON TABLE investory.accounting_poc_tax_input IS
    'POC-only explicit calculation inputs. Values remain traceable golden/source facts and are not hidden balancing adjustments.';

INSERT INTO investory.accounting_poc_tax_input (tax_period, input_type, amount, note)
VALUES
    ('2026-07-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400,
     'Visible wFirma health contribution amount. Ryczałt deducts 50% of paid health contribution.'),
    ('2026-07-01', 'JULY_ONLY_VAT_CORRECTION_ADJUSTMENT', 146.0000,
     'July-only historical invoice-correction adjustment needed to reconcile VAT to the observed 3,557 PLN result. Generic correction handling is explicitly parked for a later POC stage.');