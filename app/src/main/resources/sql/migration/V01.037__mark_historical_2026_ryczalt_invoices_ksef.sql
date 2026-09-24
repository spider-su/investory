-- Historical 2026 invoice provenance repair.
--
-- The legacy accounting import retained the invoice facts but often lost the
-- acquisition label when the document was copied into the native Ryczalt
-- tables.  The supplied 2026 set is KSeF-backed or can be confirmed in KSeF.
-- Keep explicit uploaded/off-evidence rows out of this repair.
--
-- This is provenance-only: it does not change invoice amounts, approvals,
-- payment state, periods, or calculation inputs.

WITH historical_invoice AS (
    SELECT
        source.profile_id,
        source.entity_id,
        source.source AS legacy_source,
        source.external_id AS legacy_external_id,
        COALESCE(sales.ksef_number, purchase.ksef_number) AS ksef_number,
        COALESCE(sales.filing_evidence, purchase.filing_evidence) AS filing_evidence,
        COALESCE(sales.source_id, purchase.source_id) AS source_id,
        invoice.accounting_date
    FROM investory.ryczalt_source_reference source
    JOIN investory.ryczalt_invoice invoice
      ON invoice.profile_id = source.profile_id
     AND invoice.id = source.entity_id
    LEFT JOIN investory.accounting_poc_invoice sales
      ON source.source = 'ACCOUNTING_POC_INVOICE'
     AND sales.profile_id = source.profile_id
     AND sales.id::text = source.external_id
    LEFT JOIN investory.accounting_poc_expense_invoice purchase
      ON source.source = 'ACCOUNTING_POC_EXPENSE_INVOICE'
     AND purchase.profile_id = source.profile_id
     AND purchase.id::text = source.external_id
    WHERE source.entity_type = 'INVOICE'
      AND source.source IN ('ACCOUNTING_POC_INVOICE', 'ACCOUNTING_POC_EXPENSE_INVOICE')
      AND invoice.accounting_date >= DATE '2026-01-01'
      AND invoice.accounting_date < DATE '2027-01-01'
),
eligible AS (
    SELECT historical_invoice.*
    FROM historical_invoice
    WHERE historical_invoice.filing_evidence IS DISTINCT FROM 'OFF'
      AND NOT EXISTS (
          SELECT 1
          FROM investory.accounting_source_evidence evidence
          WHERE evidence.id = historical_invoice.source_id
            AND evidence.source_type = 'UPLOAD'
            AND evidence.original_filename IS NOT NULL
            AND evidence.original_filename ~* '\.pdf$'
            AND historical_invoice.ksef_number IS NULL
      )
)
UPDATE investory.ryczalt_source_reference source
SET source = 'KSEF',
    external_id = COALESCE(
        eligible.ksef_number,
        'HISTORICAL-2026-' || lower(eligible.legacy_source) || '-' || eligible.legacy_external_id
    )
FROM eligible
WHERE source.profile_id = eligible.profile_id
  AND source.entity_type = 'INVOICE'
  AND source.entity_id = eligible.entity_id
  AND source.source = eligible.legacy_source
  AND source.external_id = eligible.legacy_external_id;
