-- V01.012 already reassigned the 2026-07-03 EUR receipt to the June accounting period.
-- V01.013 also inserted the captured June receipt while expanding all months; keep one canonical row.
DELETE FROM investory.accounting_poc_bank_transaction a
USING investory.accounting_poc_bank_transaction b
WHERE a.id > b.id
  AND a.booking_date = b.booking_date
  AND a.related_period IS NOT DISTINCT FROM b.related_period
  AND a.reference IS NOT DISTINCT FROM b.reference
  AND a.counterparty_alias IS NOT DISTINCT FROM b.counterparty_alias
  AND a.currency = b.currency
  AND a.amount = b.amount
  AND a.transaction_type = b.transaction_type
  AND a.scope = b.scope;
