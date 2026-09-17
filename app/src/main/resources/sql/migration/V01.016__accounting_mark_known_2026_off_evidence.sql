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
