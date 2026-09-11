-- May/June 2026 fuel VAT hardening.
-- May source invoices show fuel at 8% VAT; mixed-use vehicle deduction remains 50%.
-- June applies the same confirmed fuel treatment for this POC. Do not rewrite other months here:
-- their wFirma monthly VAT evidence must remain independently reconcilable until source invoices are captured.

-- May BP: source invoice I26394B03009405, gross 385.08 = net 356.56 + VAT 28.52 (8%).
UPDATE investory.accounting_poc_expense_invoice
   SET invoice_date = DATE '2026-05-30',
       net_amount = 356.5600,
       vat_amount = 28.5200,
       vat_deduction_ratio = 0.50,
       source_quality = 'SOURCE_DOCUMENT',
       note = 'Source BP invoice: 8% VAT, net 356.56, VAT 28.52, gross 385.08; mixed-use vehicle deducts 50% VAT.'
 WHERE tax_period = DATE '2026-05-01'
   AND reference = 'I26394B03009405';

-- May BP: source invoice I26394B01011115, gross 388.14 = net 359.39 + VAT 28.75 (8%).
UPDATE investory.accounting_poc_expense_invoice
   SET invoice_date = DATE '2026-05-16',
       net_amount = 359.3900,
       vat_amount = 28.7500,
       vat_deduction_ratio = 0.50,
       source_quality = 'SOURCE_DOCUMENT',
       note = 'Source BP invoice: 8% VAT, net 359.39, VAT 28.75, gross 388.14; mixed-use vehicle deducts 50% VAT.'
 WHERE tax_period = DATE '2026-05-01'
   AND reference = 'I26394801011115';

-- May Aniwim: source invoice FVF/463/58/5/2026, gross 301.77 = net 279.42 + VAT 22.35 (8%).
UPDATE investory.accounting_poc_expense_invoice
   SET invoice_date = DATE '2026-05-02',
       net_amount = 279.4200,
       vat_amount = 22.3500,
       vat_deduction_ratio = 0.50,
       source_quality = 'SOURCE_DOCUMENT',
       note = 'Source Aniwim fuel invoice: 8% VAT, net 279.42, VAT 22.35, gross 301.77; mixed-use vehicle deducts 50% VAT.'
 WHERE tax_period = DATE '2026-05-01'
   AND reference = 'FVF/463/58/5/2026';

-- June fuel rows currently have wFirma gross values only. Apply the confirmed 8% fuel VAT treatment
-- and preserve the 50% mixed-use vehicle deduction. Amounts are derived from gross and remain marked as such.
UPDATE investory.accounting_poc_expense_invoice
   SET net_amount = 336.3300,
       vat_amount = 26.9100,
       vat_deduction_ratio = 0.50,
       source_quality = 'WFIRMA_LIST_DERIVED_8',
       note = 'wFirma gross 363.24; fuel uses 8% VAT in this POC, derived net 336.33 / VAT 26.91; mixed-use vehicle deducts 50% VAT.'
 WHERE tax_period = DATE '2026-06-01'
   AND reference = 'I26394B03011055';

UPDATE investory.accounting_poc_expense_invoice
   SET net_amount = 323.5500,
       vat_amount = 25.8800,
       vat_deduction_ratio = 0.50,
       source_quality = 'WFIRMA_LIST_DERIVED_8',
       note = 'wFirma gross 349.43; fuel uses 8% VAT in this POC, derived net 323.55 / VAT 25.88; mixed-use vehicle deducts 50% VAT.'
 WHERE tax_period = DATE '2026-06-01'
   AND reference = 'I26394B03010191';
