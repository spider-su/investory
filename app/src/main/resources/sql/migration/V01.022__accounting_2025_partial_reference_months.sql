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
