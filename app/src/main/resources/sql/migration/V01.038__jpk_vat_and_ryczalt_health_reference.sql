-- JPK_V7M reference corrections for March through August 2026.

ALTER TABLE investory.ryczalt_native_month_input
    ADD COLUMN IF NOT EXISTS health_contribution_paid_override NUMERIC(19,4);

ALTER TABLE investory.ryczalt_native_month_input
    DROP CONSTRAINT IF EXISTS chk_ryczalt_native_month_input_nonnegative;

ALTER TABLE investory.ryczalt_native_month_input
    ADD CONSTRAINT chk_ryczalt_native_month_input_nonnegative CHECK (
        ytd_ryczalt_revenue >= 0
        AND COALESCE(full_jdg_social, 0) >= 0
        AND COALESCE(social_contribution_deduction, 0) >= 0
        AND COALESCE(health_contribution_override, 0) >= 0
        AND COALESCE(health_contribution_paid_override, 0) >= 0
        AND deductions_already_consumed >= 0
    );

-- JPK_V7M is authoritative for VAT payable. January and February remain parked;
-- the supplied March-August filings replace stale internal reference cents.
UPDATE investory.accounting_reference_month
   SET vat_payable = CASE tax_period
       WHEN DATE '2026-03-01' THEN 7251.00
       WHEN DATE '2026-05-01' THEN 6601.00
       WHEN DATE '2026-06-01' THEN 7293.00
       WHEN DATE '2026-07-01' THEN 3557.00
       WHEN DATE '2026-08-01' THEN 5935.00
       ELSE vat_payable
   END,
       output_vat = CASE tax_period
       WHEN DATE '2026-03-01' THEN 7489.00
       WHEN DATE '2026-05-01' THEN 6808.00
       WHEN DATE '2026-06-01' THEN 7489.00
       WHEN DATE '2026-07-01' THEN 3703.00
       WHEN DATE '2026-08-01' THEN 6038.00
       ELSE output_vat
   END,
       deductible_input_vat = CASE tax_period
       WHEN DATE '2026-03-01' THEN 238.00
       WHEN DATE '2026-05-01' THEN 207.00
       WHEN DATE '2026-06-01' THEN 196.00
       WHEN DATE '2026-07-01' THEN 146.00
       WHEN DATE '2026-08-01' THEN 103.00
       ELSE deductible_input_vat
   END
 WHERE profile_id = 1
   AND tax_period IN (
       DATE '2026-03-01', DATE '2026-05-01', DATE '2026-06-01',
       DATE '2026-07-01', DATE '2026-08-01');

-- wFirma Ryczalt calculations are authoritative for the 12% advance.
UPDATE investory.accounting_reference_month
   SET revenue = CASE tax_period
       WHEN DATE '2026-03-01' THEN 65266.52
       WHEN DATE '2026-05-01' THEN 61917.08
       WHEN DATE '2026-06-01' THEN 65310.80
       WHEN DATE '2026-07-01' THEN 49008.87
       WHEN DATE '2026-08-01' THEN 59443.58
       ELSE revenue
   END,
       ryczalt = CASE tax_period
       WHEN DATE '2026-03-01' THEN 7742.00
       WHEN DATE '2026-05-01' THEN 7340.00
       WHEN DATE '2026-06-01' THEN 7748.00
       WHEN DATE '2026-07-01' THEN 5791.00
       WHEN DATE '2026-08-01' THEN 7044.00
       ELSE ryczalt
   END
 WHERE profile_id = 1
   AND tax_period IN (
       DATE '2026-03-01', DATE '2026-05-01', DATE '2026-06-01',
       DATE '2026-07-01', DATE '2026-08-01');

UPDATE investory.accounting_reference_obligation
   SET expected_amount = CASE tax_period
       WHEN DATE '2026-03-01' THEN 7251.00
       WHEN DATE '2026-05-01' THEN 6601.00
       WHEN DATE '2026-06-01' THEN 7293.00
       WHEN DATE '2026-07-01' THEN 3557.00
       WHEN DATE '2026-08-01' THEN 5935.00
       ELSE expected_amount
   END
 WHERE profile_id = 1
   AND obligation_type = 'VAT'
   AND tax_period IN (
       DATE '2026-03-01', DATE '2026-05-01', DATE '2026-06-01',
       DATE '2026-07-01', DATE '2026-08-01');

UPDATE investory.ryczalt_obligation_reference
   SET expected_amount = CASE tax_period
       WHEN DATE '2026-03-01' THEN 7251.00
       WHEN DATE '2026-05-01' THEN 6601.00
       WHEN DATE '2026-06-01' THEN 7293.00
       WHEN DATE '2026-07-01' THEN 3557.00
       WHEN DATE '2026-08-01' THEN 5935.00
       ELSE expected_amount
   END
 WHERE profile_id = 1
   AND obligation_type = 'VAT'
   AND tax_period IN (
       DATE '2026-03-01', DATE '2026-05-01', DATE '2026-06-01',
       DATE '2026-07-01', DATE '2026-08-01');

UPDATE investory.accounting_reference_obligation
   SET expected_amount = CASE tax_period
       WHEN DATE '2026-03-01' THEN 7742.00
       WHEN DATE '2026-05-01' THEN 7340.00
       WHEN DATE '2026-06-01' THEN 7748.00
       WHEN DATE '2026-07-01' THEN 5791.00
       ELSE expected_amount
   END
 WHERE profile_id = 1
   AND obligation_type = 'RYCZALT'
   AND tax_period IN (
       DATE '2026-03-01', DATE '2026-05-01', DATE '2026-06-01',
       DATE '2026-07-01');

INSERT INTO investory.accounting_reference_obligation (
    id, profile_id, tax_period, obligation_type, due_date,
    expected_amount, paid_amount, payment_date, status, note)
VALUES (
    23, 1, DATE '2026-08-01', 'RYCZALT', DATE '2026-09-20',
    7044.00, NULL, NULL, 'GOLDEN',
    'wFirma Ryczalt calculation for August 2026.')
ON CONFLICT (id) DO UPDATE
    SET expected_amount = EXCLUDED.expected_amount,
        note = EXCLUDED.note;

INSERT INTO investory.ryczalt_obligation_reference (
    id, profile_id, tax_period, obligation_type, due_date,
    expected_amount, paid_amount, payment_date, status, note)
SELECT id, profile_id, tax_period, obligation_type, due_date,
       expected_amount, paid_amount, payment_date, status, note
  FROM investory.accounting_reference_obligation
 WHERE id = 23
ON CONFLICT (id) DO UPDATE
    SET expected_amount = EXCLUDED.expected_amount,
        note = EXCLUDED.note;

UPDATE investory.ryczalt_obligation_reference
   SET expected_amount = CASE tax_period
       WHEN DATE '2026-03-01' THEN 7742.00
       WHEN DATE '2026-05-01' THEN 7340.00
       WHEN DATE '2026-06-01' THEN 7748.00
       WHEN DATE '2026-07-01' THEN 5791.00
       ELSE expected_amount
   END
 WHERE profile_id = 1
   AND obligation_type = 'RYCZALT'
   AND tax_period IN (
       DATE '2026-03-01', DATE '2026-05-01', DATE '2026-06-01',
       DATE '2026-07-01');
