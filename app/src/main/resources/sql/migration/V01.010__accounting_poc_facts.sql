CREATE TABLE investory.accounting_poc_fact (
    id BIGSERIAL PRIMARY KEY,
    fact_date DATE,
    fact_type VARCHAR(64) NOT NULL,
    reference VARCHAR(128),
    counterparty_alias VARCHAR(128),
    currency CHAR(3) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    tax_rate NUMERIC(7, 4),
    note VARCHAR(512)
);

CREATE INDEX idx_accounting_poc_fact_date
    ON investory.accounting_poc_fact (fact_date DESC, id DESC);

COMMENT ON TABLE investory.accounting_poc_fact IS
    'POC-only anonymized accounting facts. Values are historical fixtures, not calculated tax advice.';

INSERT INTO investory.accounting_poc_fact
    (fact_date, fact_type, reference, counterparty_alias, currency, amount, tax_rate, note)
VALUES
    ('2026-05-29', 'EXPENSE_INVOICE', 'EXPENSE_001', 'SUPPLIER_ACCOUNTING_001', 'PLN', 366.5400, 0.2300,
     'Accounting services; net 298.00 PLN, VAT 68.54 PLN, fully paid, KSeF present.'),
    ('2026-07-02', 'SALES_INVOICE', 'FV 4/2026', 'CUSTOMER_PL_001', 'PLN', 40048.8000, 0.1200,
     'Original gross invoice value; IT consulting; 23% VAT; ryczalt profile rate 12%.'),
    (NULL, 'SALES_CORRECTION', 'FK 1/2026', 'CUSTOMER_PL_001', 'PLN', -184.5000, NULL,
     'Correction linked to FV 4/2026; corrected receivable becomes 39,864.30 PLN.'),
    ('2026-07-03', 'BANK_RECEIPT', 'EU recurring payment', 'CUSTOMER_EU_001', 'EUR', 7636.0000, NULL,
     'Foreign customer payment received on EUR business account. FX conversion intentionally delegated to existing Investory FX facilities.'),
    ('2026-07-16', 'BANK_RECEIPT', 'FV 4/2026', 'CUSTOMER_PL_001', 'PLN', 39864.3000, NULL,
     'Payment matches the corrected receivable for FV 4/2026.'),
    (NULL, 'SALES_INVOICE', 'FV 5/2026', 'CUSTOMER_PL_002', 'PLN', 19987.5000, 0.1200,
     'Domestic sales invoice historical fixture.'),
    ('2026-08-20', 'RYCZALT_DUE', '2026-07', 'TAX_OFFICE', 'PLN', 5791.0000, 0.1200,
     'Known wFirma result for July 2026 business ryczalt.'),
    ('2026-08-20', 'ZUS_DUE', '2026-07', 'ZUS', 'PLN', 1495.0400, NULL,
     'Known monthly health contribution obligation for the visible 2026 periods.'),
    (NULL, 'VAT_PAYMENT', '2026-07', 'TAX_OFFICE', 'PLN', 3557.0000, NULL,
     'Historical VAT payment from the PLN bank statement.'),
    ('2026-08-25', 'VAT_UE_DECLARATION', '2026-07', 'TAX_OFFICE', 'PLN', 0.0000, NULL,
     'VAT-UE reporting obligation; reporting event only, not an additional tax amount.');

CREATE TABLE investory.accounting_poc_invoice (
    id BIGSERIAL PRIMARY KEY,
    tax_period DATE NOT NULL,
    issue_date DATE,
    sale_date DATE,
    fx_rate_date DATE,
    reference VARCHAR(128) NOT NULL UNIQUE,
    customer_alias VARCHAR(128) NOT NULL,
    invoice_kind VARCHAR(32) NOT NULL,
    currency CHAR(3) NOT NULL,
    net_amount NUMERIC(19, 4) NOT NULL,
    vat_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    gross_amount NUMERIC(19, 4) NOT NULL,
    correction_gross_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    correction_net_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    correction_vat_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    expected_receivable NUMERIC(19, 4) NOT NULL,
    booked_net_pln NUMERIC(19, 4),
    ryczalt_rate NUMERIC(7, 4),
    note VARCHAR(512)
);

CREATE INDEX idx_accounting_poc_invoice_period
    ON investory.accounting_poc_invoice (tax_period, id);

COMMENT ON TABLE investory.accounting_poc_invoice IS
    'POC-only anonymized invoice fixtures used for deterministic reconciliation.';

INSERT INTO investory.accounting_poc_invoice
    (tax_period, issue_date, sale_date, fx_rate_date, reference, customer_alias, invoice_kind, currency,
     net_amount, vat_amount, gross_amount, correction_gross_amount, correction_net_amount,
     correction_vat_amount, expected_receivable, booked_net_pln, ryczalt_rate, note)
VALUES
    ('2026-06-01', '2026-07-02', '2026-06-30', NULL, 'FV 4/2026', 'CUSTOMER_PL_001', 'DOMESTIC_SERVICE', 'PLN', 32560.0000, 7488.8000, 40048.8000, -184.5000, -150.0000, -34.5000, 39864.3000, 32560.0000, 0.1200, 'KSeF FV 4/2026: issue 2026-07-02, sale/accounting period June, original net 32,560.00 PLN. July FK 1/2026 is a separate -150.00 net / -34.50 VAT correction.'),
    ('2026-07-01', NULL, NULL, NULL, 'FV 5/2026', 'CUSTOMER_PL_002', 'DOMESTIC_SERVICE', 'PLN', 16250.0000, 3737.5000, 19987.5000, 0.0000, 0.0000, 0.0000, 19987.5000, 16250.0000, 0.1200, 'Domestic service invoice. Exact issue/sale dates were not present in the captured source, so they remain null.'),
    ('2026-07-01', '2026-07-31', '2026-07-31', '2026-07-30', 'EU-SERVICE-2026-07', 'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR', 7636.0000, 0.0000, 7636.0000, 0.0000, 0.0000, 0.0000, 7636.0000, 32908.8700, 0.1200, 'Recurring EU service. Tax value uses Investory FX on 2026-07-30; 32,908.87 PLN remains the observed accounting golden value.'),
    ('2026-01-01', '2026-01-31', '2026-01-31', NULL, 'PDC-V1650-11', 'CUSTOMER_PL_001', 'DOMESTIC_SERVICE', 'PLN', 29600.0000, 6808.0000, 36408.0000, 0.0000, 0.0000, 0.0000, 36408.0000, 29600.0000, 0.1200, 'January domestic service invoice.'),
    ('2026-02-01', '2026-02-28', '2026-02-28', NULL, 'PDC-V1650-12', 'CUSTOMER_PL_001', 'DOMESTIC_SERVICE', 'PLN', 29600.0000, 6808.0000, 36408.0000, 0.0000, 0.0000, 0.0000, 36408.0000, 29600.0000, 0.1200, 'February domestic service invoice.'),
    ('2026-03-01', '2026-03-31', '2026-03-31', NULL, 'FV 1/2026', 'CUSTOMER_PL_001', 'DOMESTIC_SERVICE', 'PLN', 32560.0000, 7488.8000, 40048.8000, 0.0000, 0.0000, 0.0000, 40048.8000, 32560.0000, 0.1200, 'March domestic service invoice.'),
    ('2026-04-01', '2026-04-30', '2026-04-30', NULL, 'FV 2/2026', 'CUSTOMER_PL_001', 'DOMESTIC_SERVICE', 'PLN', 31080.0000, 7148.4000, 38228.4000, 0.0000, 0.0000, 0.0000, 38228.4000, 31080.0000, 0.1200, 'April domestic service invoice.'),
    ('2026-05-01', '2026-05-29', '2026-05-29', NULL, 'FV 3/2026', 'CUSTOMER_PL_001', 'DOMESTIC_SERVICE', 'PLN', 29600.0000, 6808.0000, 36408.0000, 0.0000, 0.0000, 0.0000, 36408.0000, 29600.0000, 0.1200, 'May domestic service invoice.'),
    ('2026-08-01', '2026-08-31', '2026-08-31', NULL, 'FV 6/2026', 'CUSTOMER_PL_002', 'DOMESTIC_SERVICE', 'PLN', 26250.0000, 6037.5000, 32287.5000, 0.0000, 0.0000, 0.0000, 32287.5000, 26250.0000, 0.1200, 'August domestic service invoice; tax outputs were not captured, therefore August remains partial.'),
    ('2026-02-01', '2026-02-28', '2026-02-28', '2026-02-27', 'EU-SERVICE-2026-02', 'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR', 7636.0000, 0.0000, 7636.0000, 0.0000, 0.0000, 0.0000, 7636.0000, 32249.1200, 0.1200, 'Observed February foreign-service accounting value.'),
    ('2026-03-01', '2026-03-31', '2026-03-31', '2026-03-30', 'EU-SERVICE-2026-03', 'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR', 7636.0000, 0.0000, 7636.0000, 0.0000, 0.0000, 0.0000, 7636.0000, 32706.5200, 0.1200, 'Observed March foreign-service accounting value.'),
    ('2026-04-01', '2026-04-30', '2026-04-30', '2026-04-29', 'EU-SERVICE-2026-04', 'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR', 7636.0000, 0.0000, 7636.0000, 0.0000, 0.0000, 0.0000, 7636.0000, 32481.2500, 0.1200, 'Observed April foreign-service accounting value.'),
    ('2026-05-01', '2026-05-29', '2026-05-29', '2026-05-29', 'EU-SERVICE-2026-05', 'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR', 7636.0000, 0.0000, 7636.0000, 0.0000, 0.0000, 0.0000, 7636.0000, 32317.0800, 0.1200, 'Observed May foreign-service accounting value. Source review maps it to the 2026-05-29 NBP table-A EUR rate.'),
    ('2026-06-01', '2026-06-30', '2026-06-30', '2026-06-29', 'EU-SERVICE-2026-06', 'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR', 7636.0000, 0.0000, 7636.0000, 0.0000, 0.0000, 0.0000, 7636.0000, 32750.8000, 0.1200, 'Observed June foreign-service accounting value.'),
    ('2026-01-01', '2026-01-31', '2026-01-31', '2026-01-30', 'EU-SERVICE-2026-01', 'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR', 7636.0000, 0.0000, 7636.0000, 0.0000, 0.0000, 0.0000, 7636.0000, 32171.2300, 0.1200, 'Source document 015 (Platform Developer): 7,636.00 EUR, sale 2026-01-31. NBP prior-business-day rate date 2026-01-30; booked wFirma value 32,171.23 PLN.');

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
CREATE INDEX idx_accounting_poc_bank_related_period ON investory.accounting_poc_bank_transaction (related_period, booking_date, id);
COMMENT ON TABLE investory.accounting_poc_bank_transaction IS 'POC-only anonymized bank fixtures. Private/internal movements are retained but explicitly scoped out.';
INSERT INTO investory.accounting_poc_bank_transaction
    (booking_date, related_period, reference, counterparty_alias, currency, amount, transaction_type, scope, note)
VALUES
    ('2026-07-03', '2026-06-01', 'Service Agreements', 'CUSTOMER_EU_001', 'EUR', 7636.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'SEPA receipt for the prior monthly EU service; retained to prevent false July matching.'),
    ('2026-07-04', NULL, 'Transfer of funds', 'OWN_ACCOUNT', 'EUR', -7636.0000, 'INTERNAL_TRANSFER', 'EXCLUDED_INTERNAL', 'Transfer between own accounts; never revenue or expense.'),
    ('2026-07-16', '2026-06-01', 'FV 4/2026', 'CUSTOMER_PL_001', 'PLN', 39864.3000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Exactly matches the corrected FV 4/2026 receivable.'),
    ('2026-07-16', NULL, 'Transfer of funds', 'OWN_ACCOUNT', 'PLN', -20000.0000, 'INTERNAL_TRANSFER', 'EXCLUDED_INTERNAL', 'Own-account transfer.'),
    ('2026-08-05', '2026-07-01', 'Service Agreements', 'CUSTOMER_EU_001', 'EUR', 7636.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'SEPA receipt matched to the July EU service fixture.'),
    ('2026-08-13', '2026-07-01', 'FV 5/2026', 'CUSTOMER_PL_002', 'PLN', 19987.5000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Payment received for FV 5/2026.'),
    ('2026-08-18', '2026-07-01', '26M07 PPE business', 'TAX_OFFICE', 'PLN', -5791.0000, 'RYCZALT_PAYMENT', 'BUSINESS', 'Business ryczalt payment for 2026-07.'),
    ('2026-08-18', '2026-07-01', '26M07 VAT-7', 'TAX_OFFICE', 'PLN', -3557.0000, 'VAT_PAYMENT', 'BUSINESS', 'VAT payment for 2026-07.'),
    ('2026-08-18', '2026-07-01', '26M07 ZUS', 'ZUS', 'PLN', -1495.0000, 'ZUS_PAYMENT', 'BUSINESS', 'Bank payment for the 2026-07 ZUS obligation.'),
    ('2026-08-18', '2026-07-01', '26M07 PPE rental', 'TAX_OFFICE', 'PLN', -740.0000, 'RENTAL_TAX_PAYMENT', 'EXCLUDED_PRIVATE', 'Private rental ryczalt; deliberately outside the business POC.'),
    ('2026-08-18', NULL, 'Transfer of funds', 'OWN_ACCOUNT', 'PLN', -8000.0000, 'INTERNAL_TRANSFER', 'EXCLUDED_INTERNAL', 'Own-account transfer.'),
    ('2026-02-13', '2026-01-01', 'PDC-V1650-11', 'CUSTOMER_PL_001', 'PLN', 36408.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Payment of January domestic invoice.'),
    ('2026-03-13', '2026-02-01', 'PDC-V1650-12', 'CUSTOMER_PL_001', 'PLN', 36408.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Payment of February domestic invoice.'),
    ('2026-04-14', '2026-03-01', 'FV 1/2026', 'CUSTOMER_PL_001', 'PLN', 40048.8000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Payment of March domestic invoice.'),
    ('2026-05-14', '2026-04-01', 'FV 2/2026', 'CUSTOMER_PL_001', 'PLN', 38228.4000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Payment of April domestic invoice.'),
    ('2026-06-12', '2026-05-01', 'FV 3/2026', 'CUSTOMER_PL_001', 'PLN', 36408.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Payment of May domestic invoice.'),
    ('2026-03-05', '2026-02-01', 'Service Agreements', 'CUSTOMER_EU_001', 'EUR', 7636.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Receipt for February EU service.'),
    ('2026-04-07', '2026-03-01', 'Service Agreements', 'CUSTOMER_EU_001', 'EUR', 7636.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Receipt for March EU service.'),
    ('2026-05-06', '2026-04-01', 'Service Agreements', 'CUSTOMER_EU_001', 'EUR', 7636.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Receipt for April EU service.'),
    ('2026-06-05', '2026-05-01', 'Service Agreements', 'CUSTOMER_EU_001', 'EUR', 7636.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Receipt for May EU service.');

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
CREATE INDEX idx_accounting_poc_obligation_period ON investory.accounting_poc_obligation (tax_period, obligation_type);
COMMENT ON TABLE investory.accounting_poc_obligation IS 'POC-only golden tax/ZUS outputs reconstructed from wFirma and bank evidence; not tax advice.';
INSERT INTO investory.accounting_poc_obligation
    (tax_period, obligation_type, due_date, expected_amount, paid_amount, payment_date, status, note)
VALUES
    ('2026-07-01', 'RYCZALT', '2026-08-20', 5791.0000, 5791.0000, '2026-08-18', 'MATCHED', 'Golden wFirma/business-tax amount confirmed by bank payment.'),
    ('2026-07-01', 'VAT', NULL, 3557.0000, 3557.0000, '2026-08-18', 'MATCHED', 'Golden VAT amount confirmed by bank payment. Detailed JPK calculation is a later POC step.'),
    ('2026-07-01', 'VAT_UE', '2026-08-25', 0.0000, 0.0000, NULL, 'REPORTING_ONLY', 'VAT-UE reporting obligation; no additional tax payment.'),
    ('2026-01-01', 'RYCZALT', '2026-02-20', 7323.0000, 7323.0000, NULL, 'GOLDEN', 'January ryczałt recomputed after restoring source document 015.'),
    ('2026-01-01', 'VAT', NULL, 6714.0000, 6714.0000, NULL, 'GOLDEN', 'Known January VAT payment.'),
    ('2026-01-01', 'ZUS', '2026-02-20', 1495.0400, 1495.0400, NULL, 'GOLDEN', 'Known January health contribution.'),
    ('2026-02-01', 'RYCZALT', '2026-03-20', 7332.0000, 7332.0000, NULL, 'GOLDEN', 'Known February ryczalt payment.'),
    ('2026-02-01', 'VAT', NULL, 6707.0000, 6707.0000, NULL, 'GOLDEN', 'Known February VAT payment.'),
    ('2026-02-01', 'ZUS', '2026-03-20', 1495.0400, 1495.0400, NULL, 'GOLDEN', 'Known February health contribution.'),
    ('2026-03-01', 'RYCZALT', '2026-04-20', 7742.0000, 7742.0000, NULL, 'GOLDEN', 'Known March ryczalt payment.'),
    ('2026-03-01', 'VAT', NULL, 7251.0000, 7251.0000, NULL, 'GOLDEN', 'Known March VAT payment.'),
    ('2026-03-01', 'ZUS', '2026-04-20', 1495.0400, 1495.0400, NULL, 'GOLDEN', 'Known March health contribution.'),
    ('2026-04-01', 'RYCZALT', '2026-05-20', 7538.0000, 7538.0000, NULL, 'GOLDEN', 'Known April ryczalt payment.'),
    ('2026-04-01', 'VAT', NULL, 7028.0000, 7028.0000, NULL, 'GOLDEN', 'Known April VAT payment.'),
    ('2026-04-01', 'ZUS', '2026-05-20', 1495.0400, 1495.0400, NULL, 'GOLDEN', 'Known April health contribution.'),
    ('2026-05-01', 'RYCZALT', '2026-06-22', 7340.0000, 7340.0000, NULL, 'GOLDEN', 'Known May ryczalt payment.'),
    ('2026-05-01', 'VAT', NULL, 6601.0000, 6601.0000, NULL, 'GOLDEN', 'Known May VAT payment.'),
    ('2026-05-01', 'ZUS', '2026-06-22', 1495.0400, 1495.0400, NULL, 'GOLDEN', 'Known May health contribution.'),
    ('2026-06-01', 'RYCZALT', '2026-07-20', 7748.0000, 7748.0000, NULL, 'GOLDEN', 'Known June ryczalt payment.'),
    ('2026-06-01', 'VAT', NULL, 7293.0000, 7293.0000, NULL, 'GOLDEN', 'Known June VAT payment.'),
    ('2026-06-01', 'ZUS', '2026-07-20', 1495.0400, 1495.0400, NULL, 'GOLDEN', 'Known June health contribution.'),
    ('2026-07-01', 'ZUS', '2026-08-20', 1495.0400, 1495.0000, '2026-08-18', 'MATCHED', 'Golden July ZUS/health contribution from wFirma. Bank cash evidence remains recorded separately.');

CREATE TABLE investory.accounting_poc_tax_input (
    id BIGSERIAL PRIMARY KEY,
    tax_period DATE NOT NULL,
    input_type VARCHAR(64) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    note VARCHAR(512),
    UNIQUE (tax_period, input_type)
);
COMMENT ON TABLE investory.accounting_poc_tax_input IS 'POC-only explicit calculation inputs. Values remain traceable golden/source facts and are not hidden balancing adjustments.';
INSERT INTO investory.accounting_poc_tax_input (tax_period, input_type, amount, note)
VALUES
    ('2026-01-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400, 'Known monthly health contribution.'),
    ('2026-02-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400, 'Known monthly health contribution.'),
    ('2026-03-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400, 'Known monthly health contribution.'),
    ('2026-04-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400, 'Known monthly health contribution.'),
    ('2026-05-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400, 'Known monthly health contribution.'),
    ('2026-06-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400, 'Known monthly health contribution.'),
    ('2026-07-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400, 'Visible wFirma health contribution amount. Ryczałt deducts 50% of paid health contribution.'),
    ('2026-01-01', 'EXPECTED_REVENUE_PLN', 61771.2300, 'wFirma analytics monthly revenue golden.'),
    ('2026-02-01', 'EXPECTED_REVENUE_PLN', 61849.1200, 'wFirma analytics monthly revenue golden.'),
    ('2026-03-01', 'EXPECTED_REVENUE_PLN', 65266.5200, 'wFirma analytics monthly revenue golden.'),
    ('2026-04-01', 'EXPECTED_REVENUE_PLN', 63561.2500, 'wFirma analytics monthly revenue golden.'),
    ('2026-05-01', 'EXPECTED_REVENUE_PLN', 61917.0800, 'wFirma analytics monthly revenue golden.'),
    ('2026-06-01', 'EXPECTED_REVENUE_PLN', 65310.8000, 'wFirma analytics monthly revenue golden.'),
    ('2026-07-01', 'EXPECTED_REVENUE_PLN', 49008.8700, 'wFirma analytics monthly revenue golden including July correction.'),
    ('2026-08-01', 'EXPECTED_REVENUE_PLN', 26250.0000, 'wFirma analytics monthly revenue golden; no foreign revenue is booked in August.'),
    ('2026-01-01', 'EXPECTED_INPUT_VAT', 93.5400, 'wFirma VAT analytics purchase VAT.'),
    ('2026-02-01', 'EXPECTED_INPUT_VAT', 100.5500, 'wFirma VAT analytics purchase VAT.'),
    ('2026-03-01', 'EXPECTED_INPUT_VAT', 238.3800, 'wFirma VAT analytics purchase VAT.'),
    ('2026-04-01', 'EXPECTED_INPUT_VAT', 120.2000, 'wFirma VAT analytics purchase VAT.'),
    ('2026-05-01', 'EXPECTED_INPUT_VAT', 207.4200, 'wFirma VAT analytics purchase VAT.'),
    ('2026-06-01', 'EXPECTED_INPUT_VAT', 196.1000, 'wFirma VAT analytics purchase VAT.'),
    ('2026-07-01', 'EXPECTED_INPUT_VAT', 145.9900, 'wFirma VAT analytics purchase VAT.'),
    ('2026-08-01', 'EXPECTED_INPUT_VAT', 0.0000, 'wFirma VAT analytics purchase VAT.');

CREATE TABLE investory.accounting_poc_expense_invoice (
    id BIGSERIAL PRIMARY KEY,
    tax_period DATE NOT NULL,
    invoice_date DATE,
    reference VARCHAR(128) NOT NULL UNIQUE,
    supplier_alias VARCHAR(128) NOT NULL,
    category VARCHAR(64) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'PLN',
    net_amount NUMERIC(19,4) NOT NULL,
    vat_amount NUMERIC(19,4) NOT NULL,
    gross_amount NUMERIC(19,4) NOT NULL,
    vat_deduction_ratio NUMERIC(3,2) NOT NULL,
    source_quality VARCHAR(32) NOT NULL,
    note VARCHAR(512),
    CONSTRAINT chk_accounting_poc_expense_ratio CHECK (vat_deduction_ratio IN (0.00, 0.50, 1.00))
);
CREATE INDEX idx_accounting_poc_expense_period ON investory.accounting_poc_expense_invoice (tax_period, invoice_date, id);
COMMENT ON TABLE investory.accounting_poc_expense_invoice IS 'POC-only anonymized expense documents. Deductible VAT is calculated per document as VAT x configured ratio.';
INSERT INTO investory.accounting_poc_expense_invoice
    (tax_period, invoice_date, reference, supplier_alias, category, currency, net_amount, vat_amount, gross_amount, vat_deduction_ratio, source_quality, note)
VALUES
    ('2026-01-01', NULL, 'I26394B03000087', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 253.3400, 58.2700, 311.6100, 0.50, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; 50% mixed-use vehicle VAT deduction.'),
    ('2026-01-01', NULL, '91/1/2026', 'SUPPLIER_SALSOFT_001', 'ACCOUNTING_SERVICE', 'PLN', 280.0000, 64.4000, 344.4000, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked SalSoft accounting expense.'),
    ('2026-02-01', '2026-02-27', '1118/2/2026', 'SUPPLIER_SALSOFT_001', 'ACCOUNTING_SERVICE', 'PLN', 298.0000, 68.5400, 366.5400, 1.00, 'SOURCE_DOCUMENT', 'KSeF purchase invoice captured: net 298.00, VAT 68.54, gross 366.54.'),
    ('2026-02-01', NULL, 'I26394B03002189', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 278.3200, 64.0100, 342.3300, 0.50, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; 50% mixed-use vehicle VAT deduction.'),
    ('2026-03-01', NULL, 'I26394B01006279', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 323.5800, 74.4200, 398.0000, 0.50, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; 50% mixed-use vehicle VAT deduction.'),
    ('2026-03-01', NULL, '2186/3/2026', 'SUPPLIER_SALSOFT_001', 'ACCOUNTING_SERVICE', 'PLN', 298.0000, 68.5400, 366.5400, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked SalSoft accounting expense.'),
    ('2026-03-01', '2026-03-06', '5034146070', 'SUPPLIER_NOWA_ERA_001', 'BUSINESS_SERVICE', 'PLN', 406.5000, 93.5000, 500.0000, 1.00, 'SOURCE_DOCUMENT', 'KSeF purchase invoice captured: net 406.50, VAT 93.50, gross 500.00.'),
    ('2026-03-01', NULL, 'I26394B03003487', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 340.2000, 78.2500, 418.4500, 0.50, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; 50% mixed-use vehicle VAT deduction.'),
    ('2026-04-01', NULL, '538/4/2026', 'SUPPLIER_SALSOFT_001', 'ACCOUNTING_SERVICE', 'PLN', 468.0000, 107.6400, 575.6400, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked SalSoft expense; exact VAT composition still needs source-document verification.'),
    ('2026-04-01', NULL, 'I26394B03005740', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 313.8100, 25.1100, 338.9200, 0.50, 'WFIRMA_LIST_DERIVED_8', 'BP Europa fuel; reconstructed at 8% VAT from gross 338.92 PLN; 50% mixed-use vehicle VAT deduction.'),
    ('2026-05-01', '2026-05-30', 'I26394B03009405', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 356.5600, 28.5200, 385.0800, 0.50, 'SOURCE_DOCUMENT', 'Source BP invoice: 8% VAT, net 356.56, VAT 28.52, gross 385.08; mixed-use vehicle deducts 50% VAT.'),
    ('2026-05-01', '2026-05-16', 'I26394801011115', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 359.3900, 28.7500, 388.1400, 0.50, 'SOURCE_DOCUMENT', 'Source BP invoice: 8% VAT, net 359.39, VAT 28.75, gross 388.14; mixed-use vehicle deducts 50% VAT.'),
    ('2026-05-01', '2026-05-29', '752/5/2026', 'SUPPLIER_SALSOFT_001', 'ACCOUNTING_SERVICE', 'PLN', 298.0000, 68.5400, 366.5400, 1.00, 'SOURCE_DOCUMENT', 'Captured SalSoft invoice: net 298.00, VAT 68.54, gross 366.54.'),
    ('2026-05-01', NULL, 'FS-652540/26/MEPL1', 'SUPPLIER_TERG_001', 'EQUIPMENT', 'PLN', 430.6800, 99.0600, 529.7400, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked TERG expense; exact VAT treatment still needs source-document verification.'),
    ('2026-05-01', '2026-05-02', 'FVF/463/58/5/2026', 'SUPPLIER_ANIWIM_001', 'VEHICLE_FUEL', 'PLN', 279.4200, 22.3500, 301.7700, 0.50, 'SOURCE_DOCUMENT', 'Source Aniwim fuel invoice: 8% VAT, net 279.42, VAT 22.35, gross 301.77; mixed-use vehicle deducts 50% VAT.'),
    ('2026-06-01', NULL, '1571/6/2026', 'SUPPLIER_SALSOFT_001', 'ACCOUNTING_SERVICE', 'PLN', 298.0000, 68.5400, 366.5400, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked SalSoft accounting expense.'),
    ('2026-06-01', NULL, 'FA/1789/2026', 'SUPPLIER_SWIAT_DRUKU_001', 'BUSINESS_SERVICE', 'PLN', 185.3700, 42.6300, 228.0000, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; exact VAT treatment still needs source-document verification.'),
    ('2026-06-01', NULL, 'I26394B03011055', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 336.3300, 26.9100, 363.2400, 0.50, 'WFIRMA_LIST_DERIVED_8', 'wFirma gross 363.24; fuel uses 8% VAT in this POC, derived net 336.33 / VAT 26.91; mixed-use vehicle deducts 50% VAT.'),
    ('2026-06-01', NULL, 'I26394B03010191', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 323.5500, 25.8800, 349.4300, 0.50, 'WFIRMA_LIST_DERIVED_8', 'wFirma gross 349.43; fuel uses 8% VAT in this POC, derived net 323.55 / VAT 25.88; mixed-use vehicle deducts 50% VAT.'),
    ('2026-06-01', NULL, 'FVS/xk/00000127858', 'SUPPLIER_XKOM_001', 'EQUIPMENT', 'PLN', 254.4600, 58.5300, 312.9900, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked X-KOM expense; exact VAT treatment still needs source-document verification.'),
    ('2026-07-01', NULL, '1339/7/2026', 'SUPPLIER_SALSOFT_001', 'ACCOUNTING_SERVICE', 'PLN', 298.0000, 68.5400, 366.5400, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked SalSoft accounting expense.'),
    ('2026-07-01', NULL, 'I26100B01009678', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 376.6200, 86.6200, 463.2400, 0.50, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; 50% mixed-use vehicle VAT deduction.'),
    ('2026-07-01', NULL, 'I26394B01015705', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 296.8200, 68.2700, 365.0900, 0.50, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; 50% mixed-use vehicle VAT deduction.');
