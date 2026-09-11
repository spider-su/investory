CREATE TABLE investory.accounting_poc_fact (
    id BIGSERIAL PRIMARY KEY,
    fact_date DATE,
    fact_type VARCHAR(64) NOT NULL,
    reference VARCHAR(128),
    counterparty_alias VARCHAR(128),
    currency CHAR(3) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    tax_rate NUMERIC(7, 4),
    note VARCHAR(512)
);

CREATE INDEX idx_accounting_poc_fact_date
    ON investory.accounting_poc_fact (fact_date DESC, id DESC);

COMMENT ON TABLE investory.accounting_poc_fact IS
    'POC-only anonymized accounting facts. Values are historical fixtures, not calculated tax advice.';

INSERT INTO investory.accounting_poc_fact
    (fact_date, fact_type, reference, counterparty_alias, currency, amount, tax_rate, note)
VALUES
    ('2026-05-29', 'EXPENSE_INVOICE', 'EXPENSE_001', 'SUPPLIER_ACCOUNTING_001', 'PLN', 366.5400, 0.2300,
     'Accounting services; net 298.00 PLN, VAT 68.54 PLN, fully paid, KSeF present.'),
    ('2026-07-02', 'SALES_INVOICE', 'FV 4/2026', 'CUSTOMER_PL_001', 'PLN', 40048.8000, 0.1200,
     'Original gross invoice value; IT consulting; 23% VAT; ryczalt profile rate 12%.'),
    (NULL, 'SALES_CORRECTION', 'FK 1/2026', 'CUSTOMER_PL_001', 'PLN', -184.5000, NULL,
     'Correction linked to FV 4/2026; corrected receivable becomes 39,864.30 PLN.'),
    ('2026-07-03', 'BANK_RECEIPT', 'EU recurring payment', 'CUSTOMER_EU_001', 'EUR', 7636.0000, NULL,
     'Foreign customer payment received on EUR business account. FX conversion intentionally delegated to existing Investory FX facilities.'),
    ('2026-07-16', 'BANK_RECEIPT', 'FV 4/2026', 'CUSTOMER_PL_001', 'PLN', 39864.3000, NULL,
     'Payment matches the corrected receivable for FV 4/2026.'),
    (NULL, 'SALES_INVOICE', 'FV 5/2026', 'CUSTOMER_PL_002', 'PLN', 19987.5000, 0.1200,
     'Domestic sales invoice historical fixture.'),
    ('2026-08-20', 'RYCZALT_DUE', '2026-07', 'TAX_OFFICE', 'PLN', 5791.0000, 0.1200,
     'Known wFirma result for July 2026 business ryczalt.'),
    ('2026-08-20', 'ZUS_DUE', '2026-07', 'ZUS', 'PLN', 1495.0400, NULL,
     'Known monthly health contribution obligation for the visible 2026 periods.'),
    (NULL, 'VAT_PAYMENT', '2026-07', 'TAX_OFFICE', 'PLN', 3557.0000, NULL,
     'Historical VAT payment from the PLN bank statement.'),
    ('2026-08-25', 'VAT_UE_DECLARATION', '2026-07', 'TAX_OFFICE', 'PLN', 0.0000, NULL,
     'VAT-UE reporting obligation; reporting event only, not an additional tax amount.');