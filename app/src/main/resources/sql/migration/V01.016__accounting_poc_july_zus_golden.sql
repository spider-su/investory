-- Preserve the wFirma July ZUS obligation as the golden accounting value.
-- The bank fixture remains the observed cash payment and is intentionally not rewritten here.
UPDATE investory.accounting_poc_obligation
   SET expected_amount = 1495.0400,
       note = 'Golden July ZUS/health contribution from wFirma. Bank cash evidence remains recorded separately.'
 WHERE tax_period = DATE '2026-07-01'
   AND obligation_type = 'ZUS';
