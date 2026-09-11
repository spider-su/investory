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
    CONSTRAINT chk_accounting_poc_expense_ratio
      CHECK (vat_deduction_ratio IN (0.00, 0.50, 1.00))
);

CREATE INDEX idx_accounting_poc_expense_period
    ON investory.accounting_poc_expense_invoice (tax_period, invoice_date, id);

COMMENT ON TABLE investory.accounting_poc_expense_invoice IS
    'POC-only anonymized expense documents. Deductible VAT is calculated per document as VAT x configured ratio.';

-- Captured 2026 expense documents. Gross amounts come from the visible wFirma expense list.
-- Where net/VAT split was not captured directly, it is reconstructed from a visible/assumed 23% VAT rate
-- and explicitly marked DERIVED_FROM_GROSS rather than presented as source truth.
INSERT INTO investory.accounting_poc_expense_invoice
    (tax_period, invoice_date, reference, supplier_alias, category, currency,
     net_amount, vat_amount, gross_amount, vat_deduction_ratio, source_quality, note)
VALUES
    ('2026-01-01', NULL, 'EXP-2026-01-BP-01', 'SUPPLIER_FUEL_001', 'VEHICLE_FUEL', 'PLN',
     253.3400, 58.2700, 311.6100, 0.50, 'DERIVED_FROM_GROSS', 'BP fuel; mixed-use vehicle fixture uses 50% VAT deduction.'),
    ('2026-01-01', NULL, 'EXP-2026-01-ACCOUNTING-01', 'SUPPLIER_ACCOUNTING_001', 'ACCOUNTING_SERVICE', 'PLN',
     280.0000, 64.4000, 344.4000, 1.00, 'DERIVED_FROM_GROSS', 'Accounting service; 100% VAT deduction.'),

    ('2026-02-01', NULL, 'EXP-2026-02-ACCOUNTING-01', 'SUPPLIER_ACCOUNTING_001', 'ACCOUNTING_SERVICE', 'PLN',
     298.0000, 68.5400, 366.5400, 1.00, 'DERIVED_FROM_GROSS', 'Accounting service; 100% VAT deduction.'),
    ('2026-02-01', NULL, 'EXP-2026-02-BP-01', 'SUPPLIER_FUEL_001', 'VEHICLE_FUEL', 'PLN',
     278.3200, 64.0100, 342.3300, 0.50, 'DERIVED_FROM_GROSS', 'BP fuel; mixed-use vehicle fixture uses 50% VAT deduction.'),

    ('2026-03-01', NULL, 'EXP-2026-03-BP-01', 'SUPPLIER_FUEL_001', 'VEHICLE_FUEL', 'PLN',
     323.5800, 74.4200, 398.0000, 0.50, 'DERIVED_FROM_GROSS', 'BP fuel; mixed-use vehicle fixture uses 50% VAT deduction.'),
    ('2026-03-01', NULL, 'EXP-2026-03-ACCOUNTING-01', 'SUPPLIER_ACCOUNTING_001', 'ACCOUNTING_SERVICE', 'PLN',
     298.0000, 68.5400, 366.5400, 1.00, 'DERIVED_FROM_GROSS', 'Accounting service; 100% VAT deduction.'),
    ('2026-03-01', NULL, 'EXP-2026-03-OTHER-01', 'SUPPLIER_OTHER_001', 'OTHER', 'PLN',
     406.5000, 93.5000, 500.0000, 0.00, 'DERIVED_FROM_GROSS', 'Visible 500 PLN expense; VAT deductibility not established, so POC ratio is conservatively 0%.'),
    ('2026-03-01', NULL, 'EXP-2026-03-BP-02', 'SUPPLIER_FUEL_001', 'VEHICLE_FUEL', 'PLN',
     340.2000, 78.2500, 418.4500, 0.50, 'DERIVED_FROM_GROSS', 'BP fuel; mixed-use vehicle fixture uses 50% VAT deduction.'),

    ('2026-04-01', NULL, 'EXP-2026-04-ACCOUNTING-01', 'SUPPLIER_ACCOUNTING_001', 'ACCOUNTING_SERVICE', 'PLN',
     468.0000, 107.6400, 575.6400, 1.00, 'DERIVED_FROM_GROSS', 'Accounting service; 100% VAT deduction.'),
    ('2026-04-01', NULL, 'EXP-2026-04-BP-01', 'SUPPLIER_FUEL_001', 'VEHICLE_FUEL', 'PLN',
     275.5400, 63.3800, 338.9200, 0.50, 'DERIVED_FROM_GROSS', 'BP fuel; mixed-use vehicle fixture uses 50% VAT deduction.'),

    ('2026-05-01', NULL, 'EXP-2026-05-BP-01', 'SUPPLIER_FUEL_001', 'VEHICLE_FUEL', 'PLN',
     313.0700, 72.0100, 385.0800, 0.50, 'DERIVED_FROM_GROSS', 'BP fuel; mixed-use vehicle fixture uses 50% VAT deduction.'),
    ('2026-05-01', NULL, 'EXP-2026-05-BP-02', 'SUPPLIER_FUEL_001', 'VEHICLE_FUEL', 'PLN',
     315.5600, 72.5800, 388.1400, 0.50, 'DERIVED_FROM_GROSS', 'BP fuel; mixed-use vehicle fixture uses 50% VAT deduction.'),
    ('2026-05-01', '2026-05-29', '752/5/2026', 'SUPPLIER_ACCOUNTING_001', 'ACCOUNTING_SERVICE', 'PLN',
     298.0000, 68.5400, 366.5400, 1.00, 'SOURCE_DOCUMENT', 'Captured SalSoft accounting invoice: net 298.00, VAT 68.54, gross 366.54.'),
    ('2026-05-01', NULL, 'EXP-2026-05-ELECTRONICS-01', 'SUPPLIER_ELECTRONICS_001', 'EQUIPMENT', 'PLN',
     430.6800, 99.0600, 529.7400, 1.00, 'DERIVED_FROM_GROSS', 'Visible electronics expense; POC assumes business equipment and 100% VAT deduction.'),
    ('2026-05-01', NULL, 'EXP-2026-05-FUEL-03', 'SUPPLIER_FUEL_002', 'VEHICLE_FUEL', 'PLN',
     245.3400, 56.4300, 301.7700, 0.50, 'DERIVED_FROM_GROSS', 'Fuel expense; mixed-use vehicle fixture uses 50% VAT deduction.');

-- Remove normal-month monthly VAT balancing fixtures. From now on normal input VAT comes only
-- from document-level expenses. July keeps its explicit one-off correction adjustment.
DELETE FROM investory.accounting_poc_tax_input
 WHERE input_type = 'DEDUCTIBLE_INPUT_VAT';
