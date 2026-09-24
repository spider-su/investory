-- For PLN invoices there is no FX conversion: booked_net_pln must equal net_amount.
-- Non-PLN invoices retain their existing booked PLN conversion facts.
UPDATE investory.ryczalt_invoice
   SET booked_net_pln = net_amount,
       updated_at = CURRENT_TIMESTAMP
 WHERE currency = 'PLN'
   AND booked_net_pln IS DISTINCT FROM net_amount;

COMMENT ON COLUMN investory.ryczalt_invoice.booked_net_pln IS
    'Invoice net amount booked in PLN; for PLN invoices it equals net_amount, while foreign-currency invoices retain the imported conversion fact.';
