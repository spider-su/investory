CREATE TABLE investory.accounting_poc_invoice (
    id BIGSERIAL PRIMARY KEY,
    tax_period DATE NOT NULL,
    issue_date DATE,
    sale_date DATE,
    reference VARCHAR(128) NOT NULL UNIQUE,
    customer_alias VARCHAR(128) NOT NULL,
    invoice_kind VARCHAR(32) NOT NULL,
    currency CHAR(3) NOT NULL,
    net_amount NUMERIC(19, 4) NOT NULL,
    vat_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    gross_amount NUMERIC(19, 4) NOT NULL,
    correction_gross_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    expected_receivable NUMERIC(19, 4) NOT NULL,
    booked_net_pln NUMERIC(19, 4),
    ryczalt_rate NUMERIC(7, 4),
    note VARCHAR(512)
);

CREATE TABLE investory.accounting_poc_bank_transaction (
    id BIGSERIAL PRIMARY KEY,
    booking_date DATE NOT NULL,
    related_period DATE,
    reference VARCHAR(128),
    counterparty_alias VARCHAR(128),
    currency CHAR(3) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    scope VARCHAR(32) NOT NULL,
    note VARCHAR(512)
);

CREATE TABLE investory.accounting_poc_obligation (
    id BIGSERIAL PRIMARY KEY,
    tax_period DATE NOT NULL,
    obligation_type VARCHAR(32) NOT NULL,
    due_date DATE,
    expected_amount NUMERIC(19, 4) NOT NULL,
    paid_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    payment_date DATE,
    status VARCHAR(32) NOT NULL,
    note VARCHAR(512),
    UNIQUE (tax_period, obligation_type)
);

CREATE INDEX idx_accounting_poc_invoice_period
    ON investory.accounting_poc_invoice (tax_period, id);
CREATE INDEX idx_accounting_poc_bank_related_period
    ON investory.accounting_poc_bank_transaction (related_period, booking_date, id);
CREATE INDEX idx_accounting_poc_obligation_period
    ON investory.accounting_poc_obligation (tax_period, obligation_type);

COMMENT ON TABLE investory.accounting_poc_invoice IS
    'POC-only anonymized invoice fixtures used for deterministic reconciliation.';
COMMENT ON TABLE investory.accounting_poc_bank_transaction IS
    'POC-only anonymized bank fixtures. Private/internal movements are retained but explicitly scoped out.';
COMMENT ON TABLE investory.accounting_poc_obligation IS
    'POC-only golden tax/ZUS outputs reconstructed from wFirma and bank evidence; not tax advice.';

INSERT INTO investory.accounting_poc_invoice
    (tax_period, issue_date, sale_date, reference, customer_alias, invoice_kind, currency,
     net_amount, vat_amount, gross_amount, correction_gross_amount, expected_receivable,
     booked_net_pln, ryczalt_rate, note)
VALUES
    ('2026-06-01', '2026-07-02', '2026-06-30', 'FV 4/2026', 'CUSTOMER_PL_001', 'DOMESTIC_SERVICE', 'PLN',
     32560.0000, 7488.8000, 40048.8000, -184.5000, 39864.3000,
     32410.0000, 0.1200,
     'IT consulting. Correction FK 1/2026 reduces gross receivable by 184.50 PLN. Tax period follows the 2026-06-30 sale date.'),
    ('2026-07-01', NULL, NULL, 'FV 5/2026', 'CUSTOMER_PL_002', 'DOMESTIC_SERVICE', 'PLN',
     16250.0000, 3737.5000, 19987.5000, 0.0000, 19987.5000,
     16250.0000, 0.1200,
     'Domestic service invoice. Exact issue/sale dates were not present in the captured source, so they remain null.'),
    ('2026-07-01', NULL, NULL, 'EU-SERVICE-2026-07', 'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR',
     7636.0000, 0.0000, 7636.0000, 0.0000, 7636.0000,
     32908.8700, 0.1200,
     'Recurring EU service. 32,908.87 PLN is the observed booked accounting value. FX calculation remains delegated to Investory FX and is not implemented by this POC migration.');

INSERT INTO investory.accounting_poc_bank_transaction
    (booking_date, related_period, reference, counterparty_alias, currency, amount,
     transaction_type, scope, note)
VALUES
    ('2026-07-03', '2026-07-01', 'Service Agreements', 'CUSTOMER_EU_001', 'EUR', 7636.0000,
     'CUSTOMER_RECEIPT', 'BUSINESS', 'SEPA receipt on the EUR business account.'),
    ('2026-07-04', NULL, 'Transfer of funds', 'OWN_ACCOUNT', 'EUR', -7636.0000,
     'INTERNAL_TRANSFER', 'EXCLUDED_INTERNAL', 'Transfer between own accounts; never revenue or expense.'),
    ('2026-07-16', '2026-06-01', 'FV 4/2026', 'CUSTOMER_PL_001', 'PLN', 39864.3000,
     'CUSTOMER_RECEIPT', 'BUSINESS', 'Exactly matches the corrected FV 4/2026 receivable.'),
    ('2026-07-16', NULL, 'Transfer of funds', 'OWN_ACCOUNT', 'PLN', -20000.0000,
     'INTERNAL_TRANSFER', 'EXCLUDED_INTERNAL', 'Own-account transfer.'),
    ('2026-08-13', '2026-07-01', 'FV 5/2026', 'CUSTOMER_PL_002', 'PLN', 19987.5000,
     'CUSTOMER_RECEIPT', 'BUSINESS', 'Payment received for FV 5/2026.'),
    ('2026-08-18', '2026-07-01', '26M07 PPE business', 'TAX_OFFICE', 'PLN', -5791.0000,
     'RYCZALT_PAYMENT', 'BUSINESS', 'Business ryczalt payment for 2026-07.'),
    ('2026-08-18', '2026-07-01', '26M07 VAT-7', 'TAX_OFFICE', 'PLN', -3557.0000,
     'VAT_PAYMENT', 'BUSINESS', 'VAT payment for 2026-07.'),
    ('2026-08-18', '2026-07-01', '26M07 ZUS', 'ZUS', 'PLN', -1495.0000,
     'ZUS_PAYMENT', 'BUSINESS', 'Bank payment for the 2026-07 ZUS obligation.'),
    ('2026-08-18', '2026-07-01', '26M07 PPE rental', 'TAX_OFFICE', 'PLN', -740.0000,
     'RENTAL_TAX_PAYMENT', 'EXCLUDED_PRIVATE', 'Private rental ryczalt; deliberately outside the business POC.'),
    ('2026-08-18', NULL, 'Transfer of funds', 'OWN_ACCOUNT', 'PLN', -8000.0000,
     'INTERNAL_TRANSFER', 'EXCLUDED_INTERNAL', 'Own-account transfer.');

INSERT INTO investory.accounting_poc_obligation
    (tax_period, obligation_type, due_date, expected_amount, paid_amount, payment_date, status, note)
VALUES
    ('2026-07-01', 'RYCZALT', '2026-08-20', 5791.0000, 5791.0000, '2026-08-18', 'MATCHED',
     'Golden wFirma/business-tax amount confirmed by bank payment.'),
    ('2026-07-01', 'VAT', NULL, 3557.0000, 3557.0000, '2026-08-18', 'MATCHED',
     'Golden VAT amount confirmed by bank payment. Detailed JPK calculation is a later POC step.'),
    ('2026-07-01', 'ZUS', '2026-08-20', 1495.0000, 1495.0000, '2026-08-18', 'MATCHED',
     'Uses the actual bank payment as the July golden cash fact.'),
    ('2026-07-01', 'VAT_UE', '2026-08-25', 0.0000, 0.0000, NULL, 'REPORTING_ONLY',
     'VAT-UE reporting obligation; no additional tax payment.');