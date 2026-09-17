-- Vehicle fuel is mixed-use in the accounting POC: only 50% of input VAT is deductible.
UPDATE investory.accounting_poc_expense_invoice
   SET vat_deduction_ratio = 0.50
 WHERE category = 'VEHICLE_FUEL'
   AND vat_deduction_ratio <> 0.50;

UPDATE investory.accounting_document
   SET vat_deduction_ratio = 0.50,
       updated_at = CURRENT_TIMESTAMP
 WHERE direction = 'PURCHASE'
   AND category = 'VEHICLE_FUEL'
   AND vat_deduction_ratio <> 0.50;

UPDATE investory.accounting_document_vat_bucket bucket
   SET deductible_vat = ROUND(bucket.vat_amount * 0.50, 2)
  FROM investory.accounting_document document
 WHERE bucket.document_id = document.id
   AND document.direction = 'PURCHASE'
   AND document.category = 'VEHICLE_FUEL';
