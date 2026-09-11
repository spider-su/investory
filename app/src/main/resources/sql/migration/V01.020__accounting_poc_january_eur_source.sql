-- January recurring EU service source is now captured directly from wFirma/invoice evidence.
-- Document 015: Platform Developer, 7,636.00 EUR, 0 VAT, sale/invoice date 2026-01-31.
-- PIT conversion uses the NBP table-A rate from the prior business day, 2026-01-30: 4.2131 PLN/EUR,
-- which yields the booked wFirma value 32,171.23 PLN.
INSERT INTO investory.accounting_poc_invoice
    (tax_period, issue_date, sale_date, fx_rate_date, reference, customer_alias, invoice_kind, currency,
     net_amount, vat_amount, gross_amount, correction_gross_amount, expected_receivable,
     booked_net_pln, ryczalt_rate, note)
VALUES
    ('2026-01-01', '2026-01-31', '2026-01-31', '2026-01-30', 'EU-SERVICE-2026-01',
     'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR',
     7636.0000, 0.0000, 7636.0000, 0.0000, 7636.0000,
     32171.2300, 0.1200,
     'Source document 015 (Platform Developer): 7,636.00 EUR, sale 2026-01-31. NBP prior-business-day rate date 2026-01-30; booked wFirma value 32,171.23 PLN.')
ON CONFLICT (reference) DO UPDATE
  SET issue_date = EXCLUDED.issue_date,
      sale_date = EXCLUDED.sale_date,
      fx_rate_date = EXCLUDED.fx_rate_date,
      net_amount = EXCLUDED.net_amount,
      vat_amount = EXCLUDED.vat_amount,
      gross_amount = EXCLUDED.gross_amount,
      expected_receivable = EXCLUDED.expected_receivable,
      booked_net_pln = EXCLUDED.booked_net_pln,
      ryczalt_rate = EXCLUDED.ryczalt_rate,
      note = EXCLUDED.note;
