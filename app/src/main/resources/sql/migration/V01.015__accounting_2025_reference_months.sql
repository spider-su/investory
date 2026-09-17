-- Reconstruct the missing 2025 reference-month rows from the immutable
-- obligation evidence and staged bank evidence, falling back to operational
-- facts where a reference component is not available.

ALTER TABLE investory.accounting_reference_month
    DROP CONSTRAINT IF EXISTS chk_accounting_reference_month_range;

ALTER TABLE investory.accounting_reference_month
    ADD CONSTRAINT chk_accounting_reference_month_range
    CHECK (tax_period >= DATE '2025-01-01' AND tax_period < DATE '2026-09-01');

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
