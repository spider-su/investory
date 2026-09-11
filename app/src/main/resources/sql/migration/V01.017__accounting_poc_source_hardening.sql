-- Hardening from September 2026 source review: wFirma/KSeF revenue, expense and VAT evidence.
-- Keep calculations document-driven; monthly golden values are stored only for comparison/diagnostics.

-- FV4 belongs to the June accounting period at its original value. The separate July correction
-- remains represented by correction_net_amount / correction_vat_amount and corrected receivable.
UPDATE investory.accounting_poc_invoice
   SET booked_net_pln = 32560.0000,
       note = 'KSeF FV 4/2026: issue 2026-07-02, sale/accounting period June, original net 32,560.00 PLN. July FK 1/2026 is a separate -150.00 net / -34.50 VAT correction.'
 WHERE reference = 'FV 4/2026';

-- wFirma May revenue equals 7,636 EUR at the NBP table-A rate published on 2026-05-29 (4.2322).
UPDATE investory.accounting_poc_invoice
   SET fx_rate_date = DATE '2026-05-29',
       note = 'Observed May foreign-service accounting value 32,317.08 PLN. Source review maps it to the 2026-05-29 NBP table-A EUR rate.'
 WHERE reference = 'EU-SERVICE-2026-05';

-- Authoritative monthly revenue totals from wFirma analytics. These are comparison goldens only;
-- calculations still use sales invoices and CurrencyConversion.
INSERT INTO investory.accounting_poc_tax_input (tax_period, input_type, amount, note)
VALUES
    ('2026-01-01', 'EXPECTED_REVENUE_PLN', 61771.2300, 'wFirma analytics monthly revenue golden.'),
    ('2026-02-01', 'EXPECTED_REVENUE_PLN', 61849.1200, 'wFirma analytics monthly revenue golden.'),
    ('2026-03-01', 'EXPECTED_REVENUE_PLN', 65266.5200, 'wFirma analytics monthly revenue golden.'),
    ('2026-04-01', 'EXPECTED_REVENUE_PLN', 63561.2500, 'wFirma analytics monthly revenue golden.'),
    ('2026-05-01', 'EXPECTED_REVENUE_PLN', 61917.0800, 'wFirma analytics monthly revenue golden.'),
    ('2026-06-01', 'EXPECTED_REVENUE_PLN', 65310.8000, 'wFirma analytics monthly revenue golden.'),
    ('2026-07-01', 'EXPECTED_REVENUE_PLN', 49008.8700, 'wFirma analytics monthly revenue golden including July correction.'),
    ('2026-08-01', 'EXPECTED_REVENUE_PLN', 26250.0000, 'wFirma analytics monthly revenue golden; no foreign revenue is booked in August.')
ON CONFLICT (tax_period, input_type) DO UPDATE
  SET amount = EXCLUDED.amount,
      note = EXCLUDED.note;

-- wFirma VAT analytics purchase-VAT totals. They are retained as evidence only and are NOT used
-- to calculate VAT payable; document-level expense rows remain the calculation source.
INSERT INTO investory.accounting_poc_tax_input (tax_period, input_type, amount, note)
VALUES
    ('2026-01-01', 'EXPECTED_INPUT_VAT', 93.5400, 'wFirma VAT analytics purchase VAT.'),
    ('2026-02-01', 'EXPECTED_INPUT_VAT', 100.5500, 'wFirma VAT analytics purchase VAT.'),
    ('2026-03-01', 'EXPECTED_INPUT_VAT', 238.3800, 'wFirma VAT analytics purchase VAT.'),
    ('2026-04-01', 'EXPECTED_INPUT_VAT', 120.2000, 'wFirma VAT analytics purchase VAT.'),
    ('2026-05-01', 'EXPECTED_INPUT_VAT', 207.4200, 'wFirma VAT analytics purchase VAT.'),
    ('2026-06-01', 'EXPECTED_INPUT_VAT', 196.1000, 'wFirma VAT analytics purchase VAT.'),
    ('2026-07-01', 'EXPECTED_INPUT_VAT', 145.9900, 'wFirma VAT analytics purchase VAT.'),
    ('2026-08-01', 'EXPECTED_INPUT_VAT', 0.0000, 'wFirma VAT analytics purchase VAT.')
ON CONFLICT (tax_period, input_type) DO UPDATE
  SET amount = EXCLUDED.amount,
      note = EXCLUDED.note;

-- July's ~146 PLN is now proven to be ordinary purchase/input VAT in wFirma analytics, not a
-- correction-only balancing adjustment. The separate sales correction remains unchanged.
DELETE FROM investory.accounting_poc_tax_input
 WHERE tax_period = DATE '2026-07-01'
   AND input_type = 'JULY_ONLY_VAT_CORRECTION_ADJUSTMENT';

-- Replace earlier synthetic/partial expense fixtures with the full Jan-Jul wFirma expense inventory.
-- Exact source net/VAT is used where captured from KSeF. For list-only rows, a 23% split is derived
-- transparently and source_quality says so; no balancing values are introduced.
DELETE FROM investory.accounting_poc_expense_invoice
 WHERE tax_period >= DATE '2026-01-01'
   AND tax_period < DATE '2026-08-01';

INSERT INTO investory.accounting_poc_expense_invoice
    (tax_period, invoice_date, reference, supplier_alias, category, currency,
     net_amount, vat_amount, gross_amount, vat_deduction_ratio, source_quality, note)
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
    ('2026-04-01', NULL, 'I26394B03005740', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 275.5400, 63.3800, 338.9200, 0.50, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; 50% mixed-use vehicle VAT deduction.'),

    ('2026-05-01', NULL, 'I26394B03009405', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 313.0700, 72.0100, 385.0800, 0.50, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; 50% mixed-use vehicle VAT deduction.'),
    ('2026-05-01', NULL, 'I26394801011115', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 315.5600, 72.5800, 388.1400, 0.50, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; 50% mixed-use vehicle VAT deduction.'),
    ('2026-05-01', '2026-05-29', '752/5/2026', 'SUPPLIER_SALSOFT_001', 'ACCOUNTING_SERVICE', 'PLN', 298.0000, 68.5400, 366.5400, 1.00, 'SOURCE_DOCUMENT', 'Captured SalSoft invoice: net 298.00, VAT 68.54, gross 366.54.'),
    ('2026-05-01', NULL, 'FS-652540/26/MEPL1', 'SUPPLIER_TERG_001', 'EQUIPMENT', 'PLN', 430.6800, 99.0600, 529.7400, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked TERG expense; exact VAT treatment still needs source-document verification.'),
    ('2026-05-01', NULL, 'FVF/463/58/5/2026', 'SUPPLIER_ANIWIM_001', 'VEHICLE_FUEL', 'PLN', 245.3400, 56.4300, 301.7700, 0.50, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked fuel expense; 50% mixed-use vehicle VAT deduction.'),

    ('2026-06-01', NULL, '1571/6/2026', 'SUPPLIER_SALSOFT_001', 'ACCOUNTING_SERVICE', 'PLN', 298.0000, 68.5400, 366.5400, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked SalSoft accounting expense.'),
    ('2026-06-01', NULL, 'FA/1789/2026', 'SUPPLIER_SWIAT_DRUKU_001', 'BUSINESS_SERVICE', 'PLN', 185.3700, 42.6300, 228.0000, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; exact VAT treatment still needs source-document verification.'),
    ('2026-06-01', NULL, 'I26394B03011055', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 295.3200, 67.9200, 363.2400, 0.50, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; 50% mixed-use vehicle VAT deduction.'),
    ('2026-06-01', NULL, 'I26394B03010191', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 284.0900, 65.3400, 349.4300, 0.50, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; 50% mixed-use vehicle VAT deduction.'),
    ('2026-06-01', NULL, 'FVS/xk/00000127858', 'SUPPLIER_XKOM_001', 'EQUIPMENT', 'PLN', 254.4600, 58.5300, 312.9900, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked X-KOM expense; exact VAT treatment still needs source-document verification.'),

    ('2026-07-01', NULL, '1339/7/2026', 'SUPPLIER_SALSOFT_001', 'ACCOUNTING_SERVICE', 'PLN', 298.0000, 68.5400, 366.5400, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked SalSoft accounting expense.'),
    ('2026-07-01', NULL, 'I26100B01009678', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 376.6200, 86.6200, 463.2400, 0.50, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; 50% mixed-use vehicle VAT deduction.'),
    ('2026-07-01', NULL, 'I26394B01015705', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 296.8200, 68.2700, 365.0900, 0.50, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; 50% mixed-use vehicle VAT deduction.');
