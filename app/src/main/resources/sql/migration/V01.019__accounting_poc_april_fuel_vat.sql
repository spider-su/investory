-- April BP Europa fuel is an 8% VAT product purchase. For a mixed-use passenger car,
-- only 50% of the invoice VAT is deductible. This brings April document VAT in line
-- with the captured wFirma purchase-VAT golden without introducing a balancing value.
UPDATE investory.accounting_poc_expense_invoice
   SET net_amount = 313.8100,
       vat_amount = 25.1100,
       vat_deduction_ratio = 0.50,
       source_quality = 'WFIRMA_LIST_DERIVED_8',
       note = 'BP Europa fuel; reconstructed at 8% VAT from gross 338.92 PLN; 50% mixed-use vehicle VAT deduction.'
 WHERE tax_period = DATE '2026-04-01'
   AND reference = 'I26394B03005740';
