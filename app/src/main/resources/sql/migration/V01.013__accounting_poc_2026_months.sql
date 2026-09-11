-- Expand the accounting POC from the July golden month to every 2026 month for which
-- captured evidence is currently available. January and August remain intentionally partial.

-- Domestic invoices with known monthly accounting periods.
INSERT INTO investory.accounting_poc_invoice
    (tax_period, issue_date, sale_date, reference, customer_alias, invoice_kind, currency,
     net_amount, vat_amount, gross_amount, correction_gross_amount, expected_receivable,
     booked_net_pln, ryczalt_rate, note)
VALUES
    ('2026-01-01', '2026-01-31', '2026-01-31', 'PDC-V1650-11', 'CUSTOMER_PL_001', 'DOMESTIC_SERVICE', 'PLN',
     29600.0000, 6808.0000, 36408.0000, 0.0000, 36408.0000, 29600.0000, 0.1200,
     'January domestic service invoice.'),
    ('2026-02-01', '2026-02-28', '2026-02-28', 'PDC-V1650-12', 'CUSTOMER_PL_001', 'DOMESTIC_SERVICE', 'PLN',
     29600.0000, 6808.0000, 36408.0000, 0.0000, 36408.0000, 29600.0000, 0.1200,
     'February domestic service invoice.'),
    ('2026-03-01', '2026-03-31', '2026-03-31', 'FV 1/2026', 'CUSTOMER_PL_001', 'DOMESTIC_SERVICE', 'PLN',
     32560.0000, 7488.8000, 40048.8000, 0.0000, 40048.8000, 32560.0000, 0.1200,
     'March domestic service invoice.'),
    ('2026-04-01', '2026-04-30', '2026-04-30', 'FV 2/2026', 'CUSTOMER_PL_001', 'DOMESTIC_SERVICE', 'PLN',
     31080.0000, 7148.4000, 38228.4000, 0.0000, 38228.4000, 31080.0000, 0.1200,
     'April domestic service invoice.'),
    ('2026-05-01', '2026-05-29', '2026-05-29', 'FV 3/2026', 'CUSTOMER_PL_001', 'DOMESTIC_SERVICE', 'PLN',
     29600.0000, 6808.0000, 36408.0000, 0.0000, 36408.0000, 29600.0000, 0.1200,
     'May domestic service invoice.'),
    ('2026-08-01', '2026-08-31', '2026-08-31', 'FV 6/2026', 'CUSTOMER_PL_002', 'DOMESTIC_SERVICE', 'PLN',
     26250.0000, 6037.5000, 32287.5000, 0.0000, 32287.5000, 26250.0000, 0.1200,
     'August domestic service invoice; tax outputs were not captured, therefore August remains partial.')
ON CONFLICT (reference) DO NOTHING;

-- Foreign recurring service invoices. Exact source amount and booked PLN value are captured
-- for February through July. FX date is the previous business day used by the POC comparison.
INSERT INTO investory.accounting_poc_invoice
    (tax_period, issue_date, sale_date, fx_rate_date, reference, customer_alias, invoice_kind, currency,
     net_amount, vat_amount, gross_amount, correction_gross_amount, expected_receivable,
     booked_net_pln, ryczalt_rate, note)
VALUES
    ('2026-02-01', '2026-02-28', '2026-02-28', '2026-02-27', 'EU-SERVICE-2026-02', 'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR',
     7636.0000, 0.0000, 7636.0000, 0.0000, 7636.0000, 32249.1200, 0.1200,
     'Observed February foreign-service accounting value.'),
    ('2026-03-01', '2026-03-31', '2026-03-31', '2026-03-30', 'EU-SERVICE-2026-03', 'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR',
     7636.0000, 0.0000, 7636.0000, 0.0000, 7636.0000, 32706.5200, 0.1200,
     'Observed March foreign-service accounting value.'),
    ('2026-04-01', '2026-04-30', '2026-04-30', '2026-04-29', 'EU-SERVICE-2026-04', 'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR',
     7636.0000, 0.0000, 7636.0000, 0.0000, 7636.0000, 32481.2500, 0.1200,
     'Observed April foreign-service accounting value.'),
    ('2026-05-01', '2026-05-29', '2026-05-29', '2026-05-28', 'EU-SERVICE-2026-05', 'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR',
     7636.0000, 0.0000, 7636.0000, 0.0000, 7636.0000, 32317.0800, 0.1200,
     'Observed May foreign-service accounting value.'),
    ('2026-06-01', '2026-06-30', '2026-06-30', '2026-06-29', 'EU-SERVICE-2026-06', 'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR',
     7636.0000, 0.0000, 7636.0000, 0.0000, 7636.0000, 32750.8000, 0.1200,
     'Observed June foreign-service accounting value.')
ON CONFLICT (reference) DO NOTHING;

-- Known business tax outputs January through July. January is retained as a golden result even
-- though the exact source EUR invoice is still missing, so its calculation status is intentionally partial.
INSERT INTO investory.accounting_poc_obligation
    (tax_period, obligation_type, due_date, expected_amount, paid_amount, payment_date, status, note)
VALUES
    ('2026-01-01', 'RYCZALT', '2026-02-20', 7329.0000, 7329.0000, NULL, 'GOLDEN', 'Known January ryczalt payment.'),
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
    ('2026-06-01', 'ZUS', '2026-07-20', 1495.0400, 1495.0400, NULL, 'GOLDEN', 'Known June health contribution.')
ON CONFLICT (tax_period, obligation_type) DO NOTHING;

-- Calculation inputs for clean months. These are explicit monthly summarized source/golden inputs;
-- document-level expense reconstruction remains a separate hardening step.
INSERT INTO investory.accounting_poc_tax_input (tax_period, input_type, amount, note)
VALUES
    ('2026-01-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400, 'Known monthly health contribution.'),
    ('2026-01-01', 'DEDUCTIBLE_INPUT_VAT', 94.0000, 'January summarized deductible input VAT.'),
    ('2026-02-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400, 'Known monthly health contribution.'),
    ('2026-02-01', 'DEDUCTIBLE_INPUT_VAT', 101.0000, 'February summarized deductible input VAT.'),
    ('2026-03-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400, 'Known monthly health contribution.'),
    ('2026-03-01', 'DEDUCTIBLE_INPUT_VAT', 237.8000, 'March summarized deductible input VAT.'),
    ('2026-04-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400, 'Known monthly health contribution.'),
    ('2026-04-01', 'DEDUCTIBLE_INPUT_VAT', 120.4000, 'April summarized deductible input VAT.'),
    ('2026-05-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400, 'Known monthly health contribution.'),
    ('2026-05-01', 'DEDUCTIBLE_INPUT_VAT', 207.0000, 'May summarized deductible input VAT.'),
    ('2026-06-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400, 'Known monthly health contribution.'),
    ('2026-06-01', 'DEDUCTIBLE_INPUT_VAT', 195.8000, 'June summarized deductible input VAT.')
ON CONFLICT (tax_period, input_type) DO NOTHING;

-- Known customer receipts used by reconciliation. Related period is the accounting month of the invoice.
INSERT INTO investory.accounting_poc_bank_transaction
    (booking_date, related_period, reference, counterparty_alias, currency, amount, transaction_type, scope, note)
VALUES
    ('2026-02-13', '2026-01-01', 'PDC-V1650-11', 'CUSTOMER_PL_001', 'PLN', 36408.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Payment of January domestic invoice.'),
    ('2026-03-13', '2026-02-01', 'PDC-V1650-12', 'CUSTOMER_PL_001', 'PLN', 36408.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Payment of February domestic invoice.'),
    ('2026-04-14', '2026-03-01', 'FV 1/2026', 'CUSTOMER_PL_001', 'PLN', 40048.8000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Payment of March domestic invoice.'),
    ('2026-05-14', '2026-04-01', 'FV 2/2026', 'CUSTOMER_PL_001', 'PLN', 38228.4000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Payment of April domestic invoice.'),
    ('2026-06-12', '2026-05-01', 'FV 3/2026', 'CUSTOMER_PL_001', 'PLN', 36408.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Payment of May domestic invoice.'),
    ('2026-03-05', '2026-02-01', 'Service Agreements', 'CUSTOMER_EU_001', 'EUR', 7636.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Receipt for February EU service.'),
    ('2026-04-07', '2026-03-01', 'Service Agreements', 'CUSTOMER_EU_001', 'EUR', 7636.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Receipt for March EU service.'),
    ('2026-05-06', '2026-04-01', 'Service Agreements', 'CUSTOMER_EU_001', 'EUR', 7636.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Receipt for April EU service.'),
    ('2026-06-05', '2026-05-01', 'Service Agreements', 'CUSTOMER_EU_001', 'EUR', 7636.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Receipt for May EU service.'),
    ('2026-07-03', '2026-06-01', 'Service Agreements', 'CUSTOMER_EU_001', 'EUR', 7636.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Receipt for June EU service.')
;
