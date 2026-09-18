-- Temporary POC/reference data and accounting data repairs squashed from V01.011 through V01.024.

-- Original V01.011 data injection.
INSERT INTO investory.accounting_poc_fact
    (fact_date, fact_type, reference, counterparty_alias, currency, amount, tax_rate, note, profile_id)
VALUES
    ('2026-05-29', 'EXPENSE_INVOICE', 'EXPENSE_001', 'SUPPLIER_ACCOUNTING_001', 'PLN', 366.5400, 0.2300,
     'Accounting services; net 298.00 PLN, VAT 68.54 PLN, fully paid, KSeF present.' , 1),
    ('2026-07-02', 'SALES_INVOICE', 'FV 4/2026', 'CUSTOMER_PL_001', 'PLN', 40048.8000, 0.1200,
     'Original gross invoice value; IT consulting; 23% VAT; ryczalt profile rate 12%.' , 1),
    (NULL, 'SALES_CORRECTION', 'FK 1/2026', 'CUSTOMER_PL_001', 'PLN', -184.5000, NULL,
     'Correction linked to FV 4/2026; corrected receivable becomes 39,864.30 PLN.' , 1),
    ('2026-07-03', 'BANK_RECEIPT', 'EU recurring payment', 'CUSTOMER_EU_001', 'EUR', 7636.0000, NULL,
     'Foreign customer payment received on EUR business account. FX conversion intentionally delegated to existing Investory FX facilities.' , 1),
    ('2026-07-16', 'BANK_RECEIPT', 'FV 4/2026', 'CUSTOMER_PL_001', 'PLN', 39864.3000, NULL,
     'Payment matches the corrected receivable for FV 4/2026.' , 1),
    (NULL, 'SALES_INVOICE', 'FV 5/2026', 'CUSTOMER_PL_002', 'PLN', 19987.5000, 0.1200,
     'Domestic sales invoice historical fixture.' , 1),
    ('2026-08-20', 'RYCZALT_DUE', '2026-07', 'TAX_OFFICE', 'PLN', 5809.0000, 0.1200,
     'Known wFirma result for July 2026 business ryczalt.' , 1),
    ('2026-08-20', 'ZUS_DUE', '2026-07', 'ZUS', 'PLN', 1495.0400, NULL,
     'Known monthly health contribution obligation for the visible 2026 periods.' , 1),
    (NULL, 'VAT_PAYMENT', '2026-07', 'TAX_OFFICE', 'PLN', 3592.0000, NULL,
     'Historical VAT payment from the PLN bank statement.' , 1),
    ('2026-08-25', 'VAT_UE_DECLARATION', '2026-07', 'TAX_OFFICE', 'PLN', 0.0000, NULL,
     'VAT-UE reporting obligation; reporting event only, not an additional tax amount.' , 1);


INSERT INTO investory.accounting_poc_invoice
    (tax_period, issue_date, sale_date, fx_rate_date, reference, customer_alias, invoice_kind, currency,
     net_amount, vat_amount, gross_amount, correction_gross_amount, correction_net_amount,
     correction_vat_amount, expected_receivable, booked_net_pln, ryczalt_rate, note, profile_id)
VALUES
    ('2026-06-01', '2026-07-02', '2026-06-30', NULL, 'FV 4/2026', 'CUSTOMER_PL_001', 'DOMESTIC_SERVICE', 'PLN', 32560.0000, 7488.8000, 40048.8000, -184.5000, -150.0000, -34.5000, 39864.3000, 32560.0000, 0.1200, 'KSeF FV 4/2026: issue 2026-07-02, sale/accounting period June, original net 32,560.00 PLN. July FK 1/2026 is a separate -150.00 net / -34.50 VAT correction.', 1),
    ('2026-07-01', NULL, NULL, NULL, 'FV 5/2026', 'CUSTOMER_PL_002', 'DOMESTIC_SERVICE', 'PLN', 16250.0000, 3737.5000, 19987.5000, 0.0000, 0.0000, 0.0000, 19987.5000, 16250.0000, 0.1200, 'Domestic service invoice. Exact issue/sale dates were not present in the captured source, so they remain null.', 1),
    ('2026-07-01', '2026-07-31', '2026-07-31', '2026-07-30', 'EU-SERVICE-2026-07', 'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR', 7636.0000, 0.0000, 7636.0000, 0.0000, 0.0000, 0.0000, 7636.0000, 32908.8700, 0.1200, 'Recurring EU service. Tax value uses Investory FX on 2026-07-30; 32,908.87 PLN remains the observed accounting golden value.' , 1),
    ('2026-01-01', '2026-01-31', '2026-01-31', NULL, 'PDC-V1650-11', 'CUSTOMER_PL_001', 'DOMESTIC_SERVICE', 'PLN', 29600.0000, 6808.0000, 36408.0000, 0.0000, 0.0000, 0.0000, 36408.0000, 29600.0000, 0.1200, 'January domestic service invoice.' , 1),
    ('2026-02-01', '2026-02-28', '2026-02-28', NULL, 'PDC-V1650-12', 'CUSTOMER_PL_001', 'DOMESTIC_SERVICE', 'PLN', 29600.0000, 6808.0000, 36408.0000, 0.0000, 0.0000, 0.0000, 36408.0000, 29600.0000, 0.1200, 'February domestic service invoice.' , 1),
    ('2026-03-01', '2026-03-31', '2026-03-31', NULL, 'FV 1/2026', 'CUSTOMER_PL_001', 'DOMESTIC_SERVICE', 'PLN', 32560.0000, 7488.8000, 40048.8000, 0.0000, 0.0000, 0.0000, 40048.8000, 32560.0000, 0.1200, 'March domestic service invoice.' , 1),
    ('2026-04-01', '2026-04-30', '2026-04-30', NULL, 'FV 2/2026', 'CUSTOMER_PL_001', 'DOMESTIC_SERVICE', 'PLN', 31080.0000, 7148.4000, 38228.4000, 0.0000, 0.0000, 0.0000, 38228.4000, 31080.0000, 0.1200, 'April domestic service invoice.' , 1),
    ('2026-05-01', '2026-05-29', '2026-05-29', NULL, 'FV 3/2026', 'CUSTOMER_PL_001', 'DOMESTIC_SERVICE', 'PLN', 29600.0000, 6808.0000, 36408.0000, 0.0000, 0.0000, 0.0000, 36408.0000, 29600.0000, 0.1200, 'May domestic service invoice.' , 1),
    ('2026-08-01', '2026-08-31', '2026-08-31', NULL, 'FV 6/2026', 'CUSTOMER_PL_002', 'DOMESTIC_SERVICE', 'PLN', 26250.0000, 6037.5000, 32287.5000, 0.0000, 0.0000, 0.0000, 32287.5000, 26250.0000, 0.1200, 'August domestic service invoice; tax outputs were not captured, therefore August remains partial.' , 1),
    ('2026-02-01', '2026-02-28', '2026-02-28', '2026-02-27', 'EU-SERVICE-2026-02', 'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR', 7636.0000, 0.0000, 7636.0000, 0.0000, 0.0000, 0.0000, 7636.0000, 32249.1200, 0.1200, 'Observed February foreign-service accounting value.' , 1),
    ('2026-03-01', '2026-03-31', '2026-03-31', '2026-03-30', 'EU-SERVICE-2026-03', 'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR', 7636.0000, 0.0000, 7636.0000, 0.0000, 0.0000, 0.0000, 7636.0000, 32706.5200, 0.1200, 'Observed March foreign-service accounting value.' , 1),
    ('2026-04-01', '2026-04-30', '2026-04-30', '2026-04-29', 'EU-SERVICE-2026-04', 'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR', 7636.0000, 0.0000, 7636.0000, 0.0000, 0.0000, 0.0000, 7636.0000, 32481.2500, 0.1200, 'Observed April foreign-service accounting value.' , 1),
    ('2026-05-01', '2026-05-29', '2026-05-29', '2026-05-29', 'EU-SERVICE-2026-05', 'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR', 7636.0000, 0.0000, 7636.0000, 0.0000, 0.0000, 0.0000, 7636.0000, 32317.0800, 0.1200, 'Observed May foreign-service accounting value. Source review maps it to the 2026-05-29 NBP table-A EUR rate.' , 1),
    ('2026-06-01', '2026-06-30', '2026-06-30', '2026-06-29', 'EU-SERVICE-2026-06', 'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR', 7636.0000, 0.0000, 7636.0000, 0.0000, 0.0000, 0.0000, 7636.0000, 32750.8000, 0.1200, 'Observed June foreign-service accounting value.' , 1),
    ('2026-01-01', '2026-01-31', '2026-01-31', '2026-01-30', 'EU-SERVICE-2026-01', 'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR', 7636.0000, 0.0000, 7636.0000, 0.0000, 0.0000, 0.0000, 7636.0000, 32171.2300, 0.1200, 'Source document 015 (Platform Developer): 7,636.00 EUR, sale 2026-01-31. NBP prior-business-day rate date 2026-01-30; booked wFirma value 32,171.23 PLN.' , 1);

INSERT INTO investory.accounting_poc_bank_transaction
    (booking_date, related_period, reference, counterparty_alias, currency, amount, transaction_type, scope, note,
     profile_id, provider, external_account_id, external_transaction_id, source_payload_hash)
VALUES
    ('2026-07-03', '2026-06-01', 'Service Agreements', 'CUSTOMER_EU_001', 'EUR', 7636.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'SEPA receipt for the prior monthly EU service; retained to prevent false July matching.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-01', NULL),
    ('2026-07-04', NULL, 'Transfer of funds', 'OWN_ACCOUNT', 'EUR', -7636.0000, 'INTERNAL_TRANSFER', 'EXCLUDED_INTERNAL', 'Transfer between own accounts; never revenue or expense.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-02', NULL),
    ('2026-07-16', '2026-06-01', 'FV 4/2026', 'CUSTOMER_PL_001', 'PLN', 39864.3000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Exactly matches the corrected FV 4/2026 receivable.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-03', NULL),
    ('2026-07-16', NULL, 'Transfer of funds', 'OWN_ACCOUNT', 'PLN', -20000.0000, 'INTERNAL_TRANSFER', 'EXCLUDED_INTERNAL', 'Own-account transfer.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-04', NULL),
    ('2026-08-05', '2026-07-01', 'Service Agreements', 'CUSTOMER_EU_001', 'EUR', 7636.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'SEPA receipt matched to the July EU service fixture.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-05', NULL),
    ('2026-08-13', '2026-07-01', 'FV 5/2026', 'CUSTOMER_PL_002', 'PLN', 19987.5000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Payment received for FV 5/2026.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-06', NULL),
    ('2026-08-18', '2026-07-01', '26M07 PPE business', 'TAX_OFFICE', 'PLN', -5809.0000, 'RYCZALT_PAYMENT', 'BUSINESS', 'Business ryczalt payment for 2026-07.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-07', NULL),
    ('2026-08-18', '2026-07-01', '26M07 VAT-7', 'TAX_OFFICE', 'PLN', -3592.0000, 'VAT_PAYMENT', 'BUSINESS', 'VAT payment for 2026-07.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-08', NULL),
    ('2026-08-18', '2026-07-01', '26M07 ZUS', 'ZUS', 'PLN', -1495.0000, 'ZUS_PAYMENT', 'BUSINESS', 'Bank payment for the 2026-07 ZUS obligation.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-09', NULL),
    ('2026-08-18', '2026-07-01', '26M07 PPE rental', 'TAX_OFFICE', 'PLN', -740.0000, 'RENTAL_TAX_PAYMENT', 'EXCLUDED_PRIVATE', 'Private rental ryczalt; deliberately outside the business POC.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-10', NULL),
    ('2026-08-18', NULL, 'Transfer of funds', 'OWN_ACCOUNT', 'PLN', -8000.0000, 'INTERNAL_TRANSFER', 'EXCLUDED_INTERNAL', 'Own-account transfer.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-11', NULL),
    ('2026-02-13', '2026-01-01', 'PDC-V1650-11', 'CUSTOMER_PL_001', 'PLN', 36408.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Payment of January domestic invoice.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-12', NULL),
    ('2026-03-13', '2026-02-01', 'PDC-V1650-12', 'CUSTOMER_PL_001', 'PLN', 36408.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Payment of February domestic invoice.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-13', NULL),
    ('2026-04-14', '2026-03-01', 'FV 1/2026', 'CUSTOMER_PL_001', 'PLN', 40048.8000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Payment of March domestic invoice.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-14', NULL),
    ('2026-05-14', '2026-04-01', 'FV 2/2026', 'CUSTOMER_PL_001', 'PLN', 38228.4000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Payment of April domestic invoice.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-15', NULL),
    ('2026-06-12', '2026-05-01', 'FV 3/2026', 'CUSTOMER_PL_001', 'PLN', 36408.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Payment of May domestic invoice.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-16', NULL),
    ('2026-03-05', '2026-02-01', 'Service Agreements', 'CUSTOMER_EU_001', 'EUR', 7636.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Receipt for February EU service.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-17', NULL),
    ('2026-04-07', '2026-03-01', 'Service Agreements', 'CUSTOMER_EU_001', 'EUR', 7636.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Receipt for March EU service.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-18', NULL),
    ('2026-05-06', '2026-04-01', 'Service Agreements', 'CUSTOMER_EU_001', 'EUR', 7636.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Receipt for April EU service.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-19', NULL),
    ('2026-06-05', '2026-05-01', 'Service Agreements', 'CUSTOMER_EU_001', 'EUR', 7636.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Receipt for May EU service.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-20', NULL);

INSERT INTO investory.accounting_poc_obligation
    (tax_period, obligation_type, due_date, expected_amount, paid_amount, payment_date, status, note, profile_id)
VALUES
    ('2026-07-01', 'RYCZALT', '2026-08-20', 5791.0000, 5791.0000, '2026-08-18', 'MATCHED', 'Golden wFirma/business-tax amount after the July cross-period correction.', 1),
    ('2026-07-01', 'VAT', NULL, 3557.0000, 3557.0000, '2026-08-18', 'MATCHED', 'Golden VAT amount after the July cross-period correction.', 1),
    ('2026-07-01', 'VAT_UE', '2026-08-25', 0.0000, 0.0000, NULL, 'REPORTING_ONLY', 'VAT-UE reporting obligation; no additional tax payment.' , 1),
    ('2026-01-01', 'RYCZALT', '2026-02-20', 7323.0000, 7323.0000, NULL, 'GOLDEN', 'January ryczałt recomputed after restoring source document 015.' , 1),
    ('2026-01-01', 'VAT', NULL, 6714.0000, 6714.0000, NULL, 'GOLDEN', 'Known January VAT payment.' , 1),
    ('2026-01-01', 'ZUS', '2026-02-20', 1495.0400, 1495.0400, NULL, 'GOLDEN', 'Known January health contribution.' , 1),
    ('2026-02-01', 'RYCZALT', '2026-03-20', 7332.0000, 7332.0000, NULL, 'GOLDEN', 'Known February ryczalt payment.' , 1),
    ('2026-02-01', 'VAT', NULL, 6707.0000, 6707.0000, NULL, 'GOLDEN', 'Known February VAT payment.' , 1),
    ('2026-02-01', 'ZUS', '2026-03-20', 1495.0400, 1495.0400, NULL, 'GOLDEN', 'Known February health contribution.' , 1),
    ('2026-03-01', 'RYCZALT', '2026-04-20', 7742.0000, 7742.0000, NULL, 'GOLDEN', 'Known March ryczalt payment.' , 1),
    ('2026-03-01', 'VAT', NULL, 7251.0000, 7251.0000, NULL, 'GOLDEN', 'Known March VAT payment.' , 1),
    ('2026-03-01', 'ZUS', '2026-04-20', 1495.0400, 1495.0400, NULL, 'GOLDEN', 'Known March health contribution.' , 1),
    ('2026-04-01', 'RYCZALT', '2026-05-20', 7538.0000, 7538.0000, NULL, 'GOLDEN', 'Known April ryczalt payment.' , 1),
    ('2026-04-01', 'VAT', NULL, 7028.0000, 7028.0000, NULL, 'GOLDEN', 'Known April VAT payment.' , 1),
    ('2026-04-01', 'ZUS', '2026-05-20', 1495.0400, 1495.0400, NULL, 'GOLDEN', 'Known April health contribution.' , 1),
    ('2026-05-01', 'RYCZALT', '2026-06-22', 7340.0000, 7340.0000, NULL, 'GOLDEN', 'Known May ryczalt payment.' , 1),
    ('2026-05-01', 'VAT', NULL, 6601.0000, 6601.0000, NULL, 'GOLDEN', 'Known May VAT payment.' , 1),
    ('2026-05-01', 'ZUS', '2026-06-22', 1495.0400, 1495.0400, NULL, 'GOLDEN', 'Known May health contribution.' , 1),
    ('2026-06-01', 'RYCZALT', '2026-07-20', 7748.0000, 7748.0000, NULL, 'GOLDEN', 'Known June ryczalt payment.' , 1),
    ('2026-06-01', 'VAT', NULL, 7293.0000, 7293.0000, NULL, 'GOLDEN', 'Known June VAT payment.' , 1),
    ('2026-06-01', 'ZUS', '2026-07-20', 1495.0400, 1495.0400, NULL, 'GOLDEN', 'Known June health contribution.' , 1),
    ('2026-07-01', 'ZUS', '2026-08-20', 1495.0400, 1495.0000, '2026-08-18', 'MATCHED', 'Golden July ZUS/health contribution from wFirma. Bank cash evidence remains recorded separately.' , 1);

INSERT INTO investory.accounting_poc_tax_input (tax_period, input_type, amount, note, profile_id)
VALUES
    ('2026-01-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400, 'Known monthly health contribution.' , 1),
    ('2026-02-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400, 'Known monthly health contribution.' , 1),
    ('2026-03-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400, 'Known monthly health contribution.' , 1),
    ('2026-04-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400, 'Known monthly health contribution.' , 1),
    ('2026-05-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400, 'Known monthly health contribution.' , 1),
    ('2026-06-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400, 'Known monthly health contribution.' , 1),
    ('2026-07-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400, 'Visible wFirma health contribution amount. Ryczałt deducts 50% of paid health contribution.' , 1),
    ('2026-01-01', 'EXPECTED_REVENUE_PLN', 61771.2300, 'wFirma analytics monthly revenue golden.' , 1),
    ('2026-02-01', 'EXPECTED_REVENUE_PLN', 61849.1200, 'wFirma analytics monthly revenue golden.' , 1),
    ('2026-03-01', 'EXPECTED_REVENUE_PLN', 65266.5200, 'wFirma analytics monthly revenue golden.' , 1),
    ('2026-04-01', 'EXPECTED_REVENUE_PLN', 63561.2500, 'wFirma analytics monthly revenue golden.' , 1),
    ('2026-05-01', 'EXPECTED_REVENUE_PLN', 61917.0800, 'wFirma analytics monthly revenue golden.' , 1),
    ('2026-06-01', 'EXPECTED_REVENUE_PLN', 65310.8000, 'wFirma analytics monthly revenue golden.' , 1),
    ('2026-07-01', 'EXPECTED_REVENUE_PLN', 49008.8700, 'Reference revenue includes the July cross-period correction represented by the canonical accounting calculation.' , 1),
    ('2026-08-01', 'EXPECTED_REVENUE_PLN', 26250.0000, 'wFirma analytics monthly revenue golden; no foreign revenue is booked in August.' , 1),
    ('2026-01-01', 'EXPECTED_INPUT_VAT', 93.5400, 'wFirma VAT analytics purchase VAT.' , 1),
    ('2026-02-01', 'EXPECTED_INPUT_VAT', 100.5500, 'wFirma VAT analytics purchase VAT.' , 1),
    ('2026-03-01', 'EXPECTED_INPUT_VAT', 238.3800, 'wFirma VAT analytics purchase VAT.' , 1),
    ('2026-04-01', 'EXPECTED_INPUT_VAT', 120.2000, 'wFirma VAT analytics purchase VAT.' , 1),
    ('2026-05-01', 'EXPECTED_INPUT_VAT', 207.4200, 'wFirma VAT analytics purchase VAT.' , 1),
    ('2026-06-01', 'EXPECTED_INPUT_VAT', 196.1000, 'wFirma VAT analytics purchase VAT.' , 1),
    ('2026-07-01', 'EXPECTED_INPUT_VAT', 145.9900, 'wFirma VAT analytics purchase VAT.' , 1),
    ('2026-08-01', 'EXPECTED_INPUT_VAT', 0.0000, 'wFirma VAT analytics purchase VAT.' , 1);

INSERT INTO investory.accounting_poc_expense_invoice
    (tax_period, invoice_date, reference, supplier_alias, category, currency, net_amount, vat_amount, gross_amount, vat_deduction_ratio, source_quality, note, profile_id)
VALUES
    ('2026-01-01', NULL, 'I26394B03000087', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 253.3400, 58.2700, 311.6100, 0.50, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; 50% mixed-use vehicle VAT deduction.' , 1),
    ('2026-01-01', NULL, '91/1/2026', 'SUPPLIER_SALSOFT_001', 'ACCOUNTING_SERVICE', 'PLN', 280.0000, 64.4000, 344.4000, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked SalSoft accounting expense.' , 1),
    ('2026-02-01', '2026-02-27', '1118/2/2026', 'SUPPLIER_SALSOFT_001', 'ACCOUNTING_SERVICE', 'PLN', 298.0000, 68.5400, 366.5400, 1.00, 'SOURCE_DOCUMENT', 'KSeF purchase invoice captured: net 298.00, VAT 68.54, gross 366.54.' , 1),
    ('2026-02-01', NULL, 'I26394B03002189', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 278.3200, 64.0100, 342.3300, 0.50, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; 50% mixed-use vehicle VAT deduction.' , 1),
    ('2026-03-01', NULL, 'I26394B01006279', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 323.5800, 74.4200, 398.0000, 0.50, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; 50% mixed-use vehicle VAT deduction.' , 1),
    ('2026-03-01', NULL, '2186/3/2026', 'SUPPLIER_SALSOFT_001', 'ACCOUNTING_SERVICE', 'PLN', 298.0000, 68.5400, 366.5400, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked SalSoft accounting expense.' , 1),
    ('2026-03-01', '2026-03-06', '5034146070', 'SUPPLIER_NOWA_ERA_001', 'BUSINESS_SERVICE', 'PLN', 406.5000, 93.5000, 500.0000, 1.00, 'SOURCE_DOCUMENT', 'KSeF purchase invoice captured: net 406.50, VAT 93.50, gross 500.00.' , 1),
    ('2026-03-01', NULL, 'I26394B03003487', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 340.2000, 78.2500, 418.4500, 0.50, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; 50% mixed-use vehicle VAT deduction.' , 1),
    ('2026-04-01', NULL, '538/4/2026', 'SUPPLIER_SALSOFT_001', 'ACCOUNTING_SERVICE', 'PLN', 468.0000, 107.6400, 575.6400, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked SalSoft expense; exact VAT composition still needs source-document verification.' , 1),
    ('2026-04-01', NULL, 'I26394B03005740', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 313.8100, 25.1100, 338.9200, 0.50, 'WFIRMA_LIST_DERIVED_8', 'BP Europa fuel; reconstructed at 8% VAT from gross 338.92 PLN; 50% mixed-use vehicle VAT deduction.' , 1),
    ('2026-05-01', '2026-05-30', 'I26394B03009405', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 356.5600, 28.5200, 385.0800, 0.50, 'SOURCE_DOCUMENT', 'Source BP invoice: 8% VAT, net 356.56, VAT 28.52, gross 385.08; mixed-use vehicle deducts 50% VAT.' , 1),
    ('2026-05-01', '2026-05-16', 'I26394801011115', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 359.3900, 28.7500, 388.1400, 0.50, 'SOURCE_DOCUMENT', 'Source BP invoice: 8% VAT, net 359.39, VAT 28.75, gross 388.14; mixed-use vehicle deducts 50% VAT.' , 1),
    ('2026-05-01', '2026-05-29', '752/5/2026', 'SUPPLIER_SALSOFT_001', 'ACCOUNTING_SERVICE', 'PLN', 298.0000, 68.5400, 366.5400, 1.00, 'SOURCE_DOCUMENT', 'Captured SalSoft invoice: net 298.00, VAT 68.54, gross 366.54.' , 1),
    ('2026-05-01', NULL, 'FS-652540/26/MEPL1', 'SUPPLIER_TERG_001', 'EQUIPMENT', 'PLN', 430.6800, 99.0600, 529.7400, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked TERG expense; exact VAT treatment still needs source-document verification.' , 1),
    ('2026-05-01', '2026-05-02', 'FVF/463/58/5/2026', 'SUPPLIER_ANIWIM_001', 'VEHICLE_FUEL', 'PLN', 279.4200, 22.3500, 301.7700, 0.50, 'SOURCE_DOCUMENT', 'Source Aniwim fuel invoice: 8% VAT, net 279.42, VAT 22.35, gross 301.77; mixed-use vehicle deducts 50% VAT.' , 1),
    ('2026-06-01', NULL, '1571/6/2026', 'SUPPLIER_SALSOFT_001', 'ACCOUNTING_SERVICE', 'PLN', 298.0000, 68.5400, 366.5400, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked SalSoft accounting expense.' , 1),
    ('2026-06-01', NULL, 'FA/1789/2026', 'SUPPLIER_SWIAT_DRUKU_001', 'BUSINESS_SERVICE', 'PLN', 185.3700, 42.6300, 228.0000, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; exact VAT treatment still needs source-document verification.' , 1),
    ('2026-06-01', NULL, 'I26394B03011055', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 336.3300, 26.9100, 363.2400, 0.50, 'WFIRMA_LIST_DERIVED_8', 'wFirma gross 363.24; fuel uses 8% VAT in this POC, derived net 336.33 / VAT 26.91; mixed-use vehicle deducts 50% VAT.' , 1),
    ('2026-06-01', NULL, 'I26394B03010191', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 323.5500, 25.8800, 349.4300, 0.50, 'WFIRMA_LIST_DERIVED_8', 'wFirma gross 349.43; fuel uses 8% VAT in this POC, derived net 323.55 / VAT 25.88; mixed-use vehicle deducts 50% VAT.' , 1),
    ('2026-06-01', NULL, 'FVS/xk/00000127858', 'SUPPLIER_XKOM_001', 'EQUIPMENT', 'PLN', 254.4600, 58.5300, 312.9900, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked X-KOM expense; exact VAT treatment still needs source-document verification.' , 1),
    ('2026-07-01', NULL, '1339/7/2026', 'SUPPLIER_SALSOFT_001', 'ACCOUNTING_SERVICE', 'PLN', 298.0000, 68.5400, 366.5400, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked SalSoft accounting expense.' , 1),
    ('2026-07-01', NULL, 'I26100B01009678', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 376.6200, 86.6200, 463.2400, 0.50, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; 50% mixed-use vehicle VAT deduction.' , 1),
    ('2026-07-01', NULL, 'I26394B01015705', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 296.8200, 68.2700, 365.0900, 0.50, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; 50% mixed-use vehicle VAT deduction.' , 1);


INSERT INTO investory.accounting_poc_profile (id, has_uop, profile_id)
VALUES (1, TRUE, 1);


-- Explicit POC calculation input for the normal-JDG branch. This is the 2026 minimum
-- compulsory social-side amount without voluntary sickness insurance. Keeping it as an
-- input avoids introducing statutory rate/base tables into this POC.
INSERT INTO investory.accounting_poc_tax_input (tax_period, input_type, amount, note, profile_id)
VALUES
    ('2026-01-01', 'JDG_COMPULSORY_SOCIAL_ZUS', 1788.2900, '2026 normal-JDG compulsory social-side ZUS input; excludes voluntary sickness insurance.' , 1),
    ('2026-02-01', 'JDG_COMPULSORY_SOCIAL_ZUS', 1788.2900, '2026 normal-JDG compulsory social-side ZUS input; excludes voluntary sickness insurance.' , 1),
    ('2026-03-01', 'JDG_COMPULSORY_SOCIAL_ZUS', 1788.2900, '2026 normal-JDG compulsory social-side ZUS input; excludes voluntary sickness insurance.' , 1),
    ('2026-04-01', 'JDG_COMPULSORY_SOCIAL_ZUS', 1788.2900, '2026 normal-JDG compulsory social-side ZUS input; excludes voluntary sickness insurance.' , 1),
    ('2026-05-01', 'JDG_COMPULSORY_SOCIAL_ZUS', 1788.2900, '2026 normal-JDG compulsory social-side ZUS input; excludes voluntary sickness insurance.' , 1),
    ('2026-06-01', 'JDG_COMPULSORY_SOCIAL_ZUS', 1788.2900, '2026 normal-JDG compulsory social-side ZUS input; excludes voluntary sickness insurance.' , 1),
    ('2026-07-01', 'JDG_COMPULSORY_SOCIAL_ZUS', 1788.2900, '2026 normal-JDG compulsory social-side ZUS input; excludes voluntary sickness insurance.' , 1),
    ('2026-08-01', 'JDG_COMPULSORY_SOCIAL_ZUS', 1788.2900, '2026 normal-JDG compulsory social-side ZUS input; excludes voluntary sickness insurance.' , 1);


UPDATE investory.accounting_poc_profile
   SET nip = COALESCE(nip, '1010000000'),
       full_name = COALESCE(full_name, 'Investory Accounting POC'),
       tax_office_code = COALESCE(tax_office_code, '1215'),
       email = COALESCE(email, 'accounting@example.invalid');


UPDATE investory.accounting_poc_bank_transaction
   SET provider = 'CSV',
       external_account_id = 'LEGACY_SOURCE',
       external_transaction_id = COALESCE(source_row_identity, 'legacy-' || id::varchar),
       source_payload_hash = NULL
 WHERE provider IS NULL;


UPDATE investory.portfolios p
   SET taxpayer_nip = COALESCE(p.taxpayer_nip, legacy.nip),
       taxpayer_full_name = COALESCE(p.taxpayer_full_name, legacy.full_name),
       taxpayer_first_name = COALESCE(p.taxpayer_first_name, legacy.first_name),
       taxpayer_surname = COALESCE(p.taxpayer_surname, legacy.surname),
       taxpayer_date_of_birth = COALESCE(p.taxpayer_date_of_birth, legacy.date_of_birth),
       taxpayer_tax_office_code = COALESCE(p.taxpayer_tax_office_code, legacy.tax_office_code),
       taxpayer_email = COALESCE(p.taxpayer_email, legacy.email),
       tax_micro_account = COALESCE(p.tax_micro_account, legacy.vat_payment_account, legacy.ryczalt_payment_account),
       zus_payment_account = COALESCE(p.zus_payment_account, legacy.zus_payment_account)
  FROM investory.accounting_poc_profile legacy
 WHERE p.id = 1 AND legacy.id = 1;


INSERT INTO investory.accounting_reference_invoice
    (id,profile_id,tax_period,issue_date,sale_date,fx_rate_date,reference,counterparty_alias,invoice_kind,currency,
     net_amount,vat_amount,gross_amount,correction_net_amount,correction_vat_amount,correction_gross_amount,
     expected_receivable,booked_net_pln,ryczalt_rate,note,source_id,counterparty_tax_identifier,
     counterparty_country,ksef_number,filing_evidence)
SELECT id,profile_id,tax_period,issue_date,sale_date,fx_rate_date,reference,customer_alias,invoice_kind,currency,
       net_amount,vat_amount,gross_amount,correction_net_amount,correction_vat_amount,correction_gross_amount,
       expected_receivable,booked_net_pln,ryczalt_rate,note,source_id,counterparty_tax_identifier,
       counterparty_country,ksef_number,filing_evidence
  FROM investory.accounting_poc_invoice
 WHERE tax_period >= DATE '2026-01-01' AND tax_period < DATE '2026-09-01';


INSERT INTO investory.accounting_reference_expense_invoice
    (id,profile_id,tax_period,invoice_date,reference,supplier_alias,category,currency,net_amount,vat_amount,
     gross_amount,vat_deduction_ratio,source_quality,note,source_id,counterparty_tax_identifier,
     counterparty_country,ksef_number,filing_evidence)
SELECT id,profile_id,tax_period,invoice_date,reference,supplier_alias,category,currency,net_amount,vat_amount,
       gross_amount,vat_deduction_ratio,source_quality,note,source_id,counterparty_tax_identifier,
       counterparty_country,ksef_number,filing_evidence
  FROM investory.accounting_poc_expense_invoice
 WHERE tax_period >= DATE '2026-01-01' AND tax_period < DATE '2026-09-01';


INSERT INTO investory.accounting_reference_bank_transaction
    (id,profile_id,booking_date,related_period,reference,counterparty_alias,currency,amount,transaction_type,
     scope,note,source_id,source_row_identity,provider,external_account_id,external_transaction_id,source_payload_hash)
SELECT id,profile_id,booking_date,related_period,reference,counterparty_alias,currency,amount,transaction_type,
       scope,note,source_id,source_row_identity,provider,external_account_id,external_transaction_id,source_payload_hash
  FROM investory.accounting_poc_bank_transaction
 WHERE booking_date >= DATE '2026-01-01' AND booking_date < DATE '2026-09-01';


INSERT INTO investory.accounting_reference_obligation
    (id,profile_id,tax_period,obligation_type,due_date,expected_amount,paid_amount,payment_date,status,note)
SELECT id,1,tax_period,obligation_type,due_date,expected_amount,paid_amount,payment_date,status,note
  FROM investory.accounting_poc_obligation
 WHERE tax_period >= DATE '2026-01-01' AND tax_period < DATE '2026-09-01';


INSERT INTO investory.accounting_reference_tax_input (id,profile_id,tax_period,input_type,amount,note)
SELECT id,1,tax_period,input_type,amount,note
  FROM investory.accounting_poc_tax_input
 WHERE tax_period >= DATE '2026-01-01' AND tax_period < DATE '2026-09-01';


INSERT INTO investory.accounting_reference_month
    (profile_id,tax_period,revenue,expenses,output_vat,deductible_input_vat,vat_payable,ryczalt,zus,
     document_count,bank_count,filing_status)
SELECT m.profile_id,m.tax_period,
       COALESCE((SELECT SUM(COALESCE(i.booked_net_pln,i.net_amount)) FROM investory.accounting_reference_invoice i
                  WHERE i.profile_id=m.profile_id AND i.tax_period=m.tax_period AND i.invoice_kind IN ('SALES_INVOICE','DOMESTIC_SERVICE','EU_SERVICE')),0),
       COALESCE((SELECT SUM(e.net_amount) FROM investory.accounting_reference_expense_invoice e WHERE e.profile_id=m.profile_id AND e.tax_period=m.tax_period),0),
       COALESCE((SELECT SUM(i.vat_amount) FROM investory.accounting_reference_invoice i WHERE i.profile_id=m.profile_id AND i.tax_period=m.tax_period),0),
       COALESCE((SELECT SUM(ROUND(e.vat_amount * e.vat_deduction_ratio, 2)) FROM investory.accounting_reference_expense_invoice e WHERE e.profile_id=m.profile_id AND e.tax_period=m.tax_period),0),
       0, COALESCE((SELECT SUM(o.expected_amount) FROM investory.accounting_reference_obligation o WHERE o.profile_id=m.profile_id AND o.tax_period=m.tax_period AND o.obligation_type='RYCZALT'),0),
       COALESCE((SELECT SUM(o.expected_amount) FROM investory.accounting_reference_obligation o WHERE o.profile_id=m.profile_id AND o.tax_period=m.tax_period AND o.obligation_type='ZUS'),0),
       (SELECT COUNT(*) FROM investory.accounting_reference_invoice i WHERE i.profile_id=m.profile_id AND i.tax_period=m.tax_period)
         + (SELECT COUNT(*) FROM investory.accounting_reference_expense_invoice e WHERE e.profile_id=m.profile_id AND e.tax_period=m.tax_period),
       (SELECT COUNT(*) FROM investory.accounting_reference_bank_transaction b WHERE b.profile_id=m.profile_id AND COALESCE(b.related_period, b.booking_date - (EXTRACT(DAY FROM b.booking_date)::integer - 1))=m.tax_period),
       NULL
  FROM (SELECT DISTINCT profile_id,tax_period FROM investory.accounting_reference_invoice
        UNION SELECT DISTINCT profile_id,tax_period FROM investory.accounting_reference_expense_invoice
        UNION SELECT 1,tax_period FROM investory.accounting_reference_obligation) m;


UPDATE investory.accounting_reference_month
   SET vat_payable = output_vat - deductible_input_vat;

INSERT INTO investory.accounting_reference_zus_branch
    (case_key,tax_period,has_uop,voluntary_sickness,ytd_revenue,paid_social,expected_health_band,
     expected_social,expected_deductible_social,expected_health,correction_sale_date,
     correction_issue_date,expected_correction_period,foreign_document_date,expected_fx_rate_date)
VALUES
 ('UOP','2026-01-01',TRUE,FALSE,0.00,0.00,'LOW',0.00,0.00,498.35,'2026-01-31','2026-02-02','2026-02-01','2026-01-31','2026-01-30'),
 ('JDG_PLAIN','2026-01-01',FALSE,FALSE,0.00,0.00,'LOW',1788.29,1649.82,498.35,'2026-01-31','2026-02-02','2026-02-01','2026-01-02','2025-12-31'),
 ('JDG_SICKNESS','2026-01-01',FALSE,TRUE,1649.82,1649.82,'LOW',1926.76,1788.29,498.35,'2026-01-31','2026-02-02','2026-02-01','2026-01-31','2026-01-30'),
 ('UOP','2026-02-01',TRUE,FALSE,60000.00,0.00,'LOW',0.00,0.00,498.35,'2026-02-28','2026-03-02','2026-03-01','2026-02-28','2026-02-27'),
 ('JDG_SICKNESS','2026-02-01',FALSE,TRUE,61649.82,1649.82,'LOW',1926.76,1788.29,498.35,'2026-02-28','2026-03-02','2026-03-01','2026-02-28','2026-02-27'),
 ('UOP','2026-03-01',TRUE,FALSE,60000.01,0.00,'MEDIUM',0.00,0.00,830.58,'2026-03-31','2026-04-02','2026-04-01','2026-03-31','2026-03-30'),
 ('JDG_SICKNESS','2026-03-01',FALSE,TRUE,61649.83,1649.82,'MEDIUM',1926.76,1788.29,830.58,'2026-03-31','2026-04-02','2026-04-01','2026-03-31','2026-03-30'),
 ('UOP','2026-04-01',TRUE,FALSE,300000.00,0.00,'MEDIUM',0.00,0.00,830.58,'2026-04-30','2026-05-02','2026-05-01','2026-04-30','2026-04-29'),
 ('JDG_SICKNESS','2026-04-01',FALSE,TRUE,301649.82,1649.82,'MEDIUM',1926.76,1788.29,830.58,'2026-04-30','2026-05-02','2026-05-01','2026-04-30','2026-04-29'),
 ('UOP','2026-05-01',TRUE,FALSE,300000.01,0.00,'HIGH',0.00,0.00,1495.04,'2026-05-31','2026-06-02','2026-06-01','2026-05-31','2026-05-29'),
 ('JDG_SICKNESS','2026-05-01',FALSE,TRUE,301649.83,1649.82,'HIGH',1926.76,1788.29,1495.04,'2026-05-31','2026-06-02','2026-06-01','2026-05-31','2026-05-29'),
 ('UOP','2026-06-01',TRUE,FALSE,500000.00,0.00,'HIGH',0.00,0.00,1495.04,'2026-06-30','2026-07-02','2026-07-01','2026-06-30','2026-06-29'),
 ('JDG_SICKNESS','2026-06-01',FALSE,TRUE,501649.82,1649.82,'HIGH',1926.76,1788.29,1495.04,'2026-06-30','2026-07-02','2026-07-01','2026-06-30','2026-06-29'),
 ('UOP','2026-07-01',TRUE,FALSE,59999.99,0.00,'LOW',0.00,0.00,498.35,'2026-07-31','2026-08-03','2026-08-01','2026-07-31','2026-07-30'),
 ('JDG_SICKNESS','2026-07-01',FALSE,TRUE,61649.81,1649.82,'LOW',1926.76,1788.29,498.35,'2026-07-31','2026-08-03','2026-08-01','2026-07-31','2026-07-30'),
 ('UOP','2026-08-01',TRUE,FALSE,300000.01,0.00,'HIGH',0.00,0.00,1495.04,'2026-08-31','2026-09-02','2026-09-01','2026-08-31','2026-08-28'),
 ('JDG_SICKNESS','2026-08-01',FALSE,TRUE,301649.83,1649.82,'HIGH',1926.76,1788.29,1495.04,'2026-08-31','2026-09-02','2026-09-01','2026-08-31','2026-08-28');


-- Historical rows are now reference-only. Operational acquisition starts empty.
DELETE FROM investory.accounting_poc_period_state;

DELETE FROM investory.accounting_filing_artifact;

DELETE FROM investory.accounting_authority_confirmation;

DELETE FROM investory.accounting_vat_transaction;

DELETE FROM investory.accounting_poc_obligation;

DELETE FROM investory.accounting_poc_tax_input;

DELETE FROM investory.accounting_poc_bank_transaction;

DELETE FROM investory.accounting_poc_expense_invoice;

DELETE FROM investory.accounting_poc_invoice;

DELETE FROM investory.accounting_poc_fact;

UPDATE investory.accounting_poc_profile SET profile_id = COALESCE(profile_id, 1);


UPDATE investory.accounting_poc_fact SET profile_id = COALESCE(profile_id, 1);

UPDATE investory.accounting_source_evidence SET profile_id = COALESCE(profile_id, 1);

UPDATE investory.accounting_poc_obligation SET profile_id = COALESCE(profile_id, 1);

UPDATE investory.accounting_poc_tax_input SET profile_id = COALESCE(profile_id, 1);

UPDATE investory.accounting_poc_period_state SET profile_id = COALESCE(profile_id, 1);

UPDATE investory.accounting_filing_artifact SET profile_id = COALESCE(profile_id, 1);

UPDATE investory.accounting_authority_confirmation SET profile_id = COALESCE(profile_id, 1);

UPDATE investory.accounting_vat_transaction SET profile_id = COALESCE(profile_id, 1);


INSERT INTO investory.accounting_known_counterparty
    (profile_id, tax_identifier, country, canonical_name)
SELECT DISTINCT profile_id,
       UPPER(REGEXP_REPLACE(counterparty_tax_identifier, '[^[:alnum:]]', '', 'g')),
       UPPER(counterparty_country),
       customer_alias
  FROM investory.accounting_poc_invoice
 WHERE counterparty_tax_identifier IS NOT NULL
   AND counterparty_country IS NOT NULL
   AND customer_alias IS NOT NULL
ON CONFLICT (profile_id, country, tax_identifier) DO NOTHING;


-- Backfill retained operational documents. Source evidence remains nullable during this expand phase:
-- historical fixture rows may predate immutable source ingestion.
INSERT INTO investory.accounting_document (
    profile_id, direction, document_kind, tax_period, issue_date, supply_date, due_date,
    reference, counterparty_name, counterparty_tax_identifier, counterparty_country, currency,
    net_amount, vat_amount, gross_amount, fx_rate_date, booked_net_pln, ryczalt_rate, source_id,
    ksef_number, filing_evidence, note)
SELECT profile_id,
       'SALE',
       CASE WHEN invoice_kind = 'CREDIT_NOTE' THEN 'CREDIT_NOTE' ELSE 'INVOICE' END,
       tax_period, issue_date, sale_date, due_date, reference, customer_alias,
       counterparty_tax_identifier, counterparty_country, currency,
       net_amount, vat_amount, gross_amount, fx_rate_date, booked_net_pln, ryczalt_rate, source_id,
       ksef_number, filing_evidence, note
  FROM investory.accounting_poc_invoice
ON CONFLICT (profile_id, direction, reference) DO NOTHING;

INSERT INTO investory.accounting_document (
    profile_id, direction, document_kind, tax_period, issue_date, supply_date, due_date,
    reference, counterparty_name, counterparty_tax_identifier, counterparty_country, currency,
    net_amount, vat_amount, gross_amount, category, vat_deduction_ratio, source_quality, source_id,
    ksef_number, filing_evidence, note)
SELECT profile_id, 'PURCHASE', 'INVOICE', tax_period, invoice_date, invoice_date, due_date,
       reference, supplier_alias, counterparty_tax_identifier, counterparty_country, currency,
       net_amount, vat_amount, gross_amount, category, vat_deduction_ratio, source_quality, source_id,
       ksef_number, filing_evidence, note
  FROM investory.accounting_poc_expense_invoice
ON CONFLICT (profile_id, direction, reference) DO NOTHING;

UPDATE investory.accounting_document document
   SET counterparty_id = counterpart.id
  FROM investory.accounting_known_counterparty counterpart
 WHERE document.profile_id = counterpart.profile_id
   AND document.counterparty_country = counterpart.country
   AND UPPER(REGEXP_REPLACE(document.counterparty_tax_identifier, '[^[:alnum:]]', '', 'g')) = counterpart.tax_identifier
   AND document.counterparty_id IS NULL;

-- Retained rows without an explicit VAT classification get the same default treatment used by
-- the existing filing projection. Explicit historical VAT classifications below take precedence.
INSERT INTO investory.accounting_document_vat_bucket
    (document_id, treatment, vat_rate, net_amount, vat_amount, deductible_vat)
SELECT document.id,
       CASE
           WHEN document.direction = 'PURCHASE' THEN 'DOMESTIC_PURCHASE'
           WHEN document.currency = 'PLN' THEN 'DOMESTIC_VAT'
           ELSE 'EU_B2B_REVERSE_CHARGE'
       END,
       CASE
           WHEN document.currency = 'PLN' AND document.net_amount <> 0
               THEN ROUND(document.vat_amount * 100 / document.net_amount, 2)
           ELSE NULL
       END,
       document.net_amount,
       document.vat_amount,
       CASE WHEN document.direction = 'PURCHASE'
           THEN ROUND(document.vat_amount * COALESCE(document.vat_deduction_ratio, 1), 2)
           ELSE 0
       END
  FROM investory.accounting_document document
 WHERE NOT EXISTS (
           SELECT 1
             FROM investory.accounting_vat_transaction vat
            WHERE vat.profile_id = document.profile_id
              AND vat.reference = document.reference
              AND vat.direction = document.direction)
ON CONFLICT (document_id, treatment, vat_rate) DO NOTHING;

INSERT INTO investory.accounting_document_vat_bucket
    (document_id, treatment, vat_rate, net_amount, vat_amount, deductible_vat)
SELECT document.id, vat.treatment, vat.vat_rate, vat.net_amount, vat.vat_amount, vat.deductible_vat
  FROM investory.accounting_vat_transaction vat
  JOIN investory.accounting_document document
    ON document.profile_id = vat.profile_id
   AND document.reference = vat.reference
   AND document.direction = vat.direction
ON CONFLICT (document_id, treatment, vat_rate)
    DO UPDATE SET net_amount = EXCLUDED.net_amount,
                  vat_amount = EXCLUDED.vat_amount,
                  deductible_vat = EXCLUDED.deductible_vat;


-- Data repair from V01.012__accounting_fuel_deduction_policy.sql.
-- Vehicle fuel is mixed-use in the accounting POC: only 50% of input VAT is deductible.
UPDATE investory.accounting_poc_expense_invoice
   SET vat_deduction_ratio = 0.50
 WHERE category = 'VEHICLE_FUEL'
   AND vat_deduction_ratio <> 0.50;

UPDATE investory.accounting_document
   SET vat_deduction_ratio = 0.50,
       updated_at = CURRENT_TIMESTAMP
 WHERE direction = 'PURCHASE'
   AND category = 'VEHICLE_FUEL'
   AND vat_deduction_ratio <> 0.50;

UPDATE investory.accounting_document_vat_bucket bucket
   SET deductible_vat = ROUND(bucket.vat_amount * 0.50, 2)
  FROM investory.accounting_document document
 WHERE bucket.document_id = document.id
   AND document.direction = 'PURCHASE'
   AND document.category = 'VEHICLE_FUEL';


-- Data injection from V01.014__accounting_2025_contribution_facts.sql.
-- Restore the 2025 external-system contribution facts used by historical
-- ryczałt reconstruction. These are source facts, not values derived from
-- the effective UoP/ZUS resolver.

INSERT INTO investory.accounting_tax_profile_period
    (profile_id, valid_from, valid_to, jdg_active, ryczalt_rate,
     vat_registered, vat_eu_registered, zus_regime, voluntary_sickness)
VALUES
    (1, DATE '2025-01-01', DATE '2025-12-01', TRUE, 0.12,
     TRUE, TRUE, 'JDG', FALSE)
ON CONFLICT DO NOTHING;

INSERT INTO investory.accounting_poc_tax_input
    (profile_id, tax_period, input_type, amount, note)
SELECT 1, period, 'SOCIAL_CONTRIBUTION_PAID', 1518.9800,
       'External-system paid deductible social contribution fact; retained independently from UoP/ZUS accrual resolution.'
  FROM generate_series(DATE '2025-03-01', DATE '2025-12-01', INTERVAL '1 month') period
ON CONFLICT (profile_id, tax_period, input_type) DO NOTHING;

INSERT INTO investory.accounting_poc_tax_input
    (profile_id, tax_period, input_type, amount, note)
SELECT 1, period, 'HEALTH_CONTRIBUTION_PAID', 1384.9700,
       'External-system paid health contribution fact; ryczałt uses the statutory 50% deductible portion.'
  FROM generate_series(DATE '2025-03-01', DATE '2025-12-01', INTERVAL '1 month') period
ON CONFLICT (profile_id, tax_period, input_type) DO NOTHING;

UPDATE investory.accounting_poc_tax_input
   SET note = CASE input_type
       WHEN 'SOCIAL_CONTRIBUTION_PAID' THEN
         'External-system paid deductible social contribution fact; retained independently from UoP/ZUS accrual resolution.'
       WHEN 'HEALTH_CONTRIBUTION_PAID' THEN
         'External-system paid health contribution fact; ryczałt uses the statutory 50% deductible portion.'
       ELSE note
       END
 WHERE profile_id = 1
   AND tax_period BETWEEN DATE '2025-03-01' AND DATE '2025-12-01'
   AND input_type IN ('SOCIAL_CONTRIBUTION_PAID', 'HEALTH_CONTRIBUTION_PAID');


-- Data repair from V01.015__accounting_2025_reference_months.sql.

WITH periods AS (
    SELECT profile_id, tax_period
      FROM investory.accounting_reference_obligation
     WHERE tax_period >= DATE '2025-01-01' AND tax_period < DATE '2026-01-01'
    UNION
    SELECT profile_id, tax_period
      FROM investory.accounting_tmp_bank_transaction
     WHERE tax_period >= DATE '2025-01-01' AND tax_period < DATE '2026-01-01'
    UNION
    SELECT profile_id, tax_period
      FROM investory.accounting_poc_invoice
     WHERE tax_period >= DATE '2025-01-01' AND tax_period < DATE '2026-01-01'
    UNION
    SELECT profile_id, tax_period
      FROM investory.accounting_poc_expense_invoice
     WHERE tax_period >= DATE '2025-01-01' AND tax_period < DATE '2026-01-01'
),
reference_documents AS (
    SELECT profile_id, tax_period,
           SUM(CASE WHEN invoice_kind IN ('SALES_INVOICE', 'DOMESTIC_SERVICE', 'EU_SERVICE')
                    THEN COALESCE(booked_net_pln, net_amount) ELSE 0 END) AS revenue,
           SUM(vat_amount) AS output_vat,
           COUNT(*) AS document_count
      FROM investory.accounting_reference_invoice
     GROUP BY profile_id, tax_period
),
reference_expenses AS (
    SELECT profile_id, tax_period,
           SUM(net_amount) AS expenses,
           SUM(ROUND(vat_amount * vat_deduction_ratio, 2)) AS deductible_input_vat,
           COUNT(*) AS document_count
      FROM investory.accounting_reference_expense_invoice
     GROUP BY profile_id, tax_period
),
operational_documents AS (
    SELECT profile_id, tax_period,
           SUM(CASE WHEN invoice_kind IN ('SALES_INVOICE', 'DOMESTIC_SERVICE', 'EU_SERVICE')
                    THEN COALESCE(booked_net_pln, net_amount) ELSE 0 END) AS revenue,
           SUM(vat_amount) AS output_vat,
           COUNT(*) AS document_count
      FROM investory.accounting_poc_invoice
     GROUP BY profile_id, tax_period
),
operational_expenses AS (
    SELECT profile_id, tax_period,
           SUM(net_amount) AS expenses,
           SUM(ROUND(vat_amount * vat_deduction_ratio, 2)) AS deductible_input_vat,
           COUNT(*) AS document_count
      FROM investory.accounting_poc_expense_invoice
     GROUP BY profile_id, tax_period
),
obligations AS (
    SELECT profile_id, tax_period,
           SUM(expected_amount) FILTER (WHERE obligation_type = 'VAT') AS vat_payable,
           SUM(expected_amount) FILTER (WHERE obligation_type = 'RYCZALT') AS ryczalt,
           SUM(expected_amount) FILTER (WHERE obligation_type = 'ZUS') AS zus
      FROM investory.accounting_reference_obligation
     GROUP BY profile_id, tax_period
),
operational_obligations AS (
    SELECT profile_id, tax_period,
           SUM(expected_amount) FILTER (WHERE obligation_type = 'VAT') AS vat_payable,
           SUM(expected_amount) FILTER (WHERE obligation_type = 'RYCZALT') AS ryczalt,
           SUM(expected_amount) FILTER (WHERE obligation_type = 'ZUS') AS zus
      FROM investory.accounting_poc_obligation
     GROUP BY profile_id, tax_period
),
bank_counts AS (
    SELECT profile_id, tax_period, COUNT(*) AS bank_count
      FROM investory.accounting_tmp_bank_transaction
     GROUP BY profile_id, tax_period
),
operational_bank_counts AS (
    SELECT profile_id,
           COALESCE(related_period, DATE_TRUNC('month', booking_date)::date) AS tax_period,
           COUNT(*) AS bank_count
      FROM investory.accounting_poc_bank_transaction
     GROUP BY profile_id, COALESCE(related_period, DATE_TRUNC('month', booking_date)::date)
)
INSERT INTO investory.accounting_reference_month
    (profile_id, tax_period, revenue, expenses, output_vat, deductible_input_vat,
     vat_payable, ryczalt, zus, document_count, bank_count, filing_status)
SELECT p.profile_id,
       p.tax_period,
       COALESCE(rd.revenue, od.revenue, 0),
       COALESCE(re.expenses, oe.expenses, 0),
       COALESCE(rd.output_vat, od.output_vat, 0),
       COALESCE(re.deductible_input_vat, oe.deductible_input_vat, 0),
       COALESCE(o.vat_payable, oo.vat_payable,
                GREATEST(COALESCE(rd.output_vat, od.output_vat, 0)
                         - COALESCE(re.deductible_input_vat, oe.deductible_input_vat, 0), 0)),
       COALESCE(o.ryczalt, oo.ryczalt, 0),
       COALESCE(o.zus, oo.zus, 0),
       COALESCE(rd.document_count, od.document_count, 0)
           + COALESCE(re.document_count, oe.document_count, 0),
       COALESCE(bc.bank_count, obc.bank_count, 0),
       NULL
  FROM periods p
  LEFT JOIN reference_documents rd USING (profile_id, tax_period)
  LEFT JOIN reference_expenses re USING (profile_id, tax_period)
  LEFT JOIN operational_documents od USING (profile_id, tax_period)
  LEFT JOIN operational_expenses oe USING (profile_id, tax_period)
  LEFT JOIN obligations o USING (profile_id, tax_period)
  LEFT JOIN operational_obligations oo USING (profile_id, tax_period)
  LEFT JOIN bank_counts bc USING (profile_id, tax_period)
  LEFT JOIN operational_bank_counts obc USING (profile_id, tax_period)
 ON CONFLICT (profile_id, tax_period) DO NOTHING;

UPDATE investory.accounting_reference_month
   SET vat_payable = output_vat - deductible_input_vat
 WHERE tax_period >= DATE '2026-01-01';


-- Data repair from V01.016__accounting_mark_known_2026_off_evidence.sql.
-- Uploaded legacy documents are known non-KSeF evidence for the 2026 JPK_V7M(3)
-- period.  Do not infer evidence for ambiguous or KSeF documents.
-- This is a provenance-only backfill; accounting amounts are unchanged.

UPDATE investory.accounting_document d
   SET filing_evidence = 'OFF'
 WHERE d.profile_id = 1
   AND d.tax_period >= DATE '2026-02-01'
   AND d.tax_period < DATE '2026-09-01'
   AND d.filing_evidence IS NULL
   AND d.ksef_number IS NULL
   AND EXISTS (
         SELECT 1
           FROM investory.accounting_source_evidence s
          WHERE s.id = d.source_id
            AND s.source_type = 'UPLOAD'
            AND s.original_filename IS NOT NULL
            AND s.original_filename ~* '\.pdf$'
       );

UPDATE investory.accounting_poc_invoice i
   SET filing_evidence = 'OFF'
 WHERE i.profile_id = 1
   AND i.tax_period >= DATE '2026-02-01'
   AND i.tax_period < DATE '2026-09-01'
   AND i.filing_evidence IS NULL
   AND i.ksef_number IS NULL
   AND EXISTS (
         SELECT 1
           FROM investory.accounting_source_evidence s
          WHERE s.id = i.source_id
            AND s.source_type = 'UPLOAD'
            AND s.original_filename IS NOT NULL
            AND s.original_filename ~* '\.pdf$'
       );

UPDATE investory.accounting_poc_expense_invoice i
   SET filing_evidence = 'OFF'
 WHERE i.profile_id = 1
   AND i.tax_period >= DATE '2026-02-01'
   AND i.tax_period < DATE '2026-09-01'
   AND i.filing_evidence IS NULL
   AND i.ksef_number IS NULL
   AND EXISTS (
         SELECT 1
           FROM investory.accounting_source_evidence s
          WHERE s.id = i.source_id
            AND s.source_type = 'UPLOAD'
            AND s.original_filename IS NOT NULL
            AND s.original_filename ~* '\.pdf$'
       );


-- Data repair from V01.018__accounting_link_ksef_counterparties.sql.
-- Link already-imported KSeF documents to their stable counterparty identity.
-- The application now sets this link for new canonical documents as well.
INSERT INTO investory.accounting_known_counterparty
    (profile_id, tax_identifier, country, canonical_name)
SELECT DISTINCT d.profile_id,
       UPPER(REGEXP_REPLACE(d.counterparty_tax_identifier, '[^[:alnum:]]', '', 'g')),
       UPPER(d.counterparty_country),
       d.counterparty_name
  FROM investory.accounting_document d
 WHERE d.ksef_number IS NOT NULL
   AND d.counterparty_tax_identifier IS NOT NULL
   AND d.counterparty_country IS NOT NULL
   AND NULLIF(TRIM(d.counterparty_name), '') IS NOT NULL
ON CONFLICT (profile_id, country, tax_identifier) DO NOTHING;

UPDATE investory.accounting_document d
   SET counterparty_id = k.id
  FROM investory.accounting_known_counterparty k
 WHERE d.ksef_number IS NOT NULL
   AND d.counterparty_id IS NULL
   AND k.profile_id = d.profile_id
   AND k.country = UPPER(d.counterparty_country)
   AND k.tax_identifier = UPPER(REGEXP_REPLACE(d.counterparty_tax_identifier, '[^[:alnum:]]', '', 'g'));


-- Data repair from V01.019__accounting_normalize_polish_counterparties.sql.
-- Polish NIP is the same identity with or without the PL VAT prefix.
CREATE TEMP TABLE accounting_counterparty_merge ON COMMIT DROP AS
SELECT duplicate.id AS duplicate_id, keeper.id AS keeper_id
  FROM investory.accounting_known_counterparty duplicate
  JOIN LATERAL (
        SELECT candidate.id
          FROM investory.accounting_known_counterparty candidate
         WHERE candidate.profile_id = duplicate.profile_id
           AND candidate.country = 'PL'
           AND REGEXP_REPLACE(UPPER(candidate.tax_identifier), '^PL', '') =
               REGEXP_REPLACE(UPPER(duplicate.tax_identifier), '^PL', '')
         ORDER BY (UPPER(candidate.tax_identifier) LIKE 'PL%'), candidate.id
         LIMIT 1
       ) keeper ON TRUE
 WHERE duplicate.country = 'PL'
   AND duplicate.id <> keeper.id;

UPDATE investory.accounting_known_counterparty keeper
   SET alias = COALESCE(NULLIF(keeper.alias, ''), duplicate.alias)
  FROM investory.accounting_known_counterparty duplicate
  JOIN accounting_counterparty_merge merge ON merge.duplicate_id = duplicate.id
 WHERE keeper.id = merge.keeper_id
   AND NULLIF(duplicate.alias, '') IS NOT NULL;

UPDATE investory.accounting_document document
   SET counterparty_id = merge.keeper_id
  FROM accounting_counterparty_merge merge
 WHERE document.counterparty_id = merge.duplicate_id;

UPDATE investory.accounting_trusted_counterparty_treatment treatment
   SET counterparty_id = merge.keeper_id
  FROM accounting_counterparty_merge merge
 WHERE treatment.counterparty_id = merge.duplicate_id;

DELETE FROM investory.accounting_known_counterparty duplicate
 USING accounting_counterparty_merge merge
 WHERE duplicate.id = merge.duplicate_id;

UPDATE investory.accounting_known_counterparty
   SET tax_identifier = REGEXP_REPLACE(UPPER(tax_identifier), '^PL', '')
 WHERE country = 'PL'
   AND UPPER(tax_identifier) LIKE 'PL%';


-- Data repair from V01.022__accounting_2025_partial_reference_months.sql.
-- Restore the 2025 months for which the database contains accounting evidence.
-- Only paid contribution facts are available for these periods. Do not present
-- missing documents, bank rows, or tax obligations as zero-valued evidence.

INSERT INTO investory.accounting_reference_month
    (profile_id, tax_period, revenue, expenses, output_vat, deductible_input_vat,
     vat_payable, ryczalt, zus, document_count, bank_count, filing_status)
SELECT profile_id,
       tax_period,
       0,
       0,
       0,
       0,
       0,
       0,
       0,
       0,
       0,
       'REVIEW_REQUIRED'
  FROM investory.accounting_poc_tax_input
 WHERE tax_period >= DATE '2025-01-01'
   AND tax_period < DATE '2026-01-01'
 GROUP BY profile_id, tax_period
 ON CONFLICT (profile_id, tax_period) DO NOTHING;

COMMENT ON TABLE investory.accounting_reference_month IS
    'Reference oracle: Jan-Aug 2026 complete; 2025 months are partial contribution evidence and require review.';


-- Data repair from V01.023__accounting_2025_legacy_calculation_mode.sql.
-- 2025 is reconstructed from confirmed historical facts. It must not opt into the
-- operational effective-profile path, which derives deductions from bank payment facts.
DELETE FROM investory.accounting_tax_profile_period
 WHERE profile_id = 1
   AND valid_from = DATE '2025-01-01'
   AND valid_to = DATE '2025-12-01';
