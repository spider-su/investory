-- Reconcile the January ryczałt golden after restoring the captured EUR source invoice.
-- The source raises January revenue to 61,771.23 PLN; after the health deduction,
-- the 12% calculation rounds to 7,323 PLN.
UPDATE investory.accounting_poc_obligation
   SET expected_amount = 7323.0000,
       paid_amount = 7323.0000,
       note = 'January ryczałt recomputed after restoring source document 015.'
 WHERE tax_period = DATE '2026-01-01'
   AND obligation_type = 'RYCZALT';
