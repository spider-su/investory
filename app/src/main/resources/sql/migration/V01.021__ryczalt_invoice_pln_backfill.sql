SET search_path TO investory, public;

UPDATE investory.ryczalt_invoice
SET booked_vat_pln = vat_amount
WHERE currency = 'PLN'
  AND booked_vat_pln IS NULL;

UPDATE investory.ryczalt_invoice
SET booked_net_pln = net_amount
WHERE currency = 'PLN'
  AND booked_net_pln IS NULL;
