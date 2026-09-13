-- Immutable Jan-Aug 2026 verification oracle. It is deliberately separate from
-- operational Accounting tables and is never read by the calculation path.
CREATE TABLE investory.accounting_reference_invoice (
    id BIGINT PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    tax_period DATE NOT NULL,
    issue_date DATE,
    sale_date DATE,
    fx_rate_date DATE,
    reference VARCHAR(128) NOT NULL,
    counterparty_alias VARCHAR(128) NOT NULL,
    invoice_kind VARCHAR(32) NOT NULL,
    currency CHAR(3) NOT NULL,
    net_amount NUMERIC(19,4) NOT NULL,
    vat_amount NUMERIC(19,4) NOT NULL,
    gross_amount NUMERIC(19,4) NOT NULL,
    correction_net_amount NUMERIC(19,4) NOT NULL,
    correction_vat_amount NUMERIC(19,4) NOT NULL,
    correction_gross_amount NUMERIC(19,4) NOT NULL,
    expected_receivable NUMERIC(19,4) NOT NULL,
    booked_net_pln NUMERIC(19,4),
    ryczalt_rate NUMERIC(7,4),
    note VARCHAR(512),
    source_id BIGINT,
    counterparty_tax_identifier VARCHAR(32),
    counterparty_country VARCHAR(2),
    ksef_number VARCHAR(256),
    filing_evidence VARCHAR(8),
    UNIQUE (profile_id, reference)
);

CREATE TABLE investory.accounting_reference_expense_invoice (
    id BIGINT PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    tax_period DATE NOT NULL,
    invoice_date DATE,
    reference VARCHAR(128) NOT NULL,
    supplier_alias VARCHAR(128) NOT NULL,
    category VARCHAR(64) NOT NULL,
    currency CHAR(3) NOT NULL,
    net_amount NUMERIC(19,4) NOT NULL,
    vat_amount NUMERIC(19,4) NOT NULL,
    gross_amount NUMERIC(19,4) NOT NULL,
    vat_deduction_ratio NUMERIC(3,2) NOT NULL,
    source_quality VARCHAR(32) NOT NULL,
    note VARCHAR(512),
    source_id BIGINT,
    counterparty_tax_identifier VARCHAR(32),
    counterparty_country VARCHAR(2),
    ksef_number VARCHAR(256),
    filing_evidence VARCHAR(8),
    UNIQUE (profile_id, reference)
);

CREATE TABLE investory.accounting_reference_bank_transaction (
    id BIGINT PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    booking_date DATE NOT NULL,
    related_period DATE,
    reference VARCHAR(128),
    counterparty_alias VARCHAR(128),
    currency CHAR(3) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    scope VARCHAR(32) NOT NULL,
    note VARCHAR(512),
    source_id BIGINT,
    source_row_identity VARCHAR(256),
    provider VARCHAR(32) NOT NULL,
    external_account_id VARCHAR(256) NOT NULL,
    external_transaction_id VARCHAR(256) NOT NULL,
    source_payload_hash VARCHAR(128)
);

CREATE TABLE investory.accounting_reference_obligation (
    id BIGINT PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    tax_period DATE NOT NULL,
    obligation_type VARCHAR(32) NOT NULL,
    due_date DATE,
    expected_amount NUMERIC(19,4) NOT NULL,
    paid_amount NUMERIC(19,4),
    payment_date DATE,
    status VARCHAR(32) NOT NULL,
    note VARCHAR(512)
);

CREATE TABLE investory.accounting_reference_tax_input (
    id BIGINT PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    tax_period DATE NOT NULL,
    input_type VARCHAR(64) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    note VARCHAR(512)
);

CREATE TABLE investory.accounting_reference_month (
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    tax_period DATE NOT NULL,
    revenue NUMERIC(19,4) NOT NULL,
    expenses NUMERIC(19,4) NOT NULL,
    output_vat NUMERIC(19,4) NOT NULL,
    deductible_input_vat NUMERIC(19,4) NOT NULL,
    vat_payable NUMERIC(19,4) NOT NULL,
    ryczalt NUMERIC(19,4),
    zus NUMERIC(19,4),
    document_count INTEGER NOT NULL,
    bank_count INTEGER NOT NULL,
    filing_status VARCHAR(32),
    PRIMARY KEY (profile_id, tax_period),
    CONSTRAINT chk_accounting_reference_month_range
        CHECK (tax_period >= DATE '2026-01-01' AND tax_period < DATE '2026-09-01')
);

INSERT INTO investory.accounting_reference_invoice
    (id,profile_id,tax_period,issue_date,sale_date,fx_rate_date,reference,counterparty_alias,invoice_kind,currency,
     net_amount,vat_amount,gross_amount,correction_net_amount,correction_vat_amount,correction_gross_amount,
     expected_receivable,booked_net_pln,ryczalt_rate,note,source_id,counterparty_tax_identifier,
     counterparty_country,ksef_number,filing_evidence)
SELECT id,profile_id,tax_period,issue_date,sale_date,fx_rate_date,reference,customer_alias,invoice_kind,currency,
       net_amount,vat_amount,gross_amount,correction_net_amount,correction_vat_amount,correction_gross_amount,
       expected_receivable,booked_net_pln,ryczalt_rate,note,source_id,counterparty_tax_identifier,
       counterparty_country,ksef_number,filing_evidence
  FROM investory.accounting_poc_invoice
 WHERE tax_period >= DATE '2026-01-01' AND tax_period < DATE '2026-09-01';

INSERT INTO investory.accounting_reference_expense_invoice
    (id,profile_id,tax_period,invoice_date,reference,supplier_alias,category,currency,net_amount,vat_amount,
     gross_amount,vat_deduction_ratio,source_quality,note,source_id,counterparty_tax_identifier,
     counterparty_country,ksef_number,filing_evidence)
SELECT id,profile_id,tax_period,invoice_date,reference,supplier_alias,category,currency,net_amount,vat_amount,
       gross_amount,vat_deduction_ratio,source_quality,note,source_id,counterparty_tax_identifier,
       counterparty_country,ksef_number,filing_evidence
  FROM investory.accounting_poc_expense_invoice
 WHERE tax_period >= DATE '2026-01-01' AND tax_period < DATE '2026-09-01';

INSERT INTO investory.accounting_reference_bank_transaction
    (id,profile_id,booking_date,related_period,reference,counterparty_alias,currency,amount,transaction_type,
     scope,note,source_id,source_row_identity,provider,external_account_id,external_transaction_id,source_payload_hash)
SELECT id,profile_id,booking_date,related_period,reference,counterparty_alias,currency,amount,transaction_type,
       scope,note,source_id,source_row_identity,provider,external_account_id,external_transaction_id,source_payload_hash
  FROM investory.accounting_poc_bank_transaction
 WHERE booking_date >= DATE '2026-01-01' AND booking_date < DATE '2026-09-01';

INSERT INTO investory.accounting_reference_obligation
    (id,profile_id,tax_period,obligation_type,due_date,expected_amount,paid_amount,payment_date,status,note)
SELECT id,1,tax_period,obligation_type,due_date,expected_amount,paid_amount,payment_date,status,note
  FROM investory.accounting_poc_obligation
 WHERE tax_period >= DATE '2026-01-01' AND tax_period < DATE '2026-09-01';

INSERT INTO investory.accounting_reference_tax_input (id,profile_id,tax_period,input_type,amount,note)
SELECT id,1,tax_period,input_type,amount,note
  FROM investory.accounting_poc_tax_input
 WHERE tax_period >= DATE '2026-01-01' AND tax_period < DATE '2026-09-01';

INSERT INTO investory.accounting_reference_month
    (profile_id,tax_period,revenue,expenses,output_vat,deductible_input_vat,vat_payable,ryczalt,zus,
     document_count,bank_count,filing_status)
SELECT m.profile_id,m.tax_period,
       COALESCE((SELECT SUM(COALESCE(i.booked_net_pln,i.net_amount)) FROM investory.accounting_reference_invoice i
                  WHERE i.profile_id=m.profile_id AND i.tax_period=m.tax_period AND i.invoice_kind IN ('SALES_INVOICE','DOMESTIC_SERVICE','EU_SERVICE')),0),
       COALESCE((SELECT SUM(e.net_amount) FROM investory.accounting_reference_expense_invoice e WHERE e.profile_id=m.profile_id AND e.tax_period=m.tax_period),0),
       COALESCE((SELECT SUM(i.vat_amount + i.correction_vat_amount) FROM investory.accounting_reference_invoice i WHERE i.profile_id=m.profile_id AND i.tax_period=m.tax_period),0),
       COALESCE((SELECT SUM(e.vat_amount * e.vat_deduction_ratio) FROM investory.accounting_reference_expense_invoice e WHERE e.profile_id=m.profile_id AND e.tax_period=m.tax_period),0),
       0, COALESCE((SELECT SUM(o.expected_amount) FROM investory.accounting_reference_obligation o WHERE o.profile_id=m.profile_id AND o.tax_period=m.tax_period AND o.obligation_type='RYCZALT'),0),
       COALESCE((SELECT SUM(o.expected_amount) FROM investory.accounting_reference_obligation o WHERE o.profile_id=m.profile_id AND o.tax_period=m.tax_period AND o.obligation_type='ZUS'),0),
       (SELECT COUNT(*) FROM investory.accounting_reference_invoice i WHERE i.profile_id=m.profile_id AND i.tax_period=m.tax_period)
         + (SELECT COUNT(*) FROM investory.accounting_reference_expense_invoice e WHERE e.profile_id=m.profile_id AND e.tax_period=m.tax_period),
       (SELECT COUNT(*) FROM investory.accounting_reference_bank_transaction b WHERE b.profile_id=m.profile_id AND COALESCE(b.related_period, b.booking_date - (EXTRACT(DAY FROM b.booking_date)::integer - 1))=m.tax_period),
       NULL
  FROM (SELECT DISTINCT profile_id,tax_period FROM investory.accounting_reference_invoice
        UNION SELECT DISTINCT profile_id,tax_period FROM investory.accounting_reference_expense_invoice
        UNION SELECT 1,tax_period FROM investory.accounting_reference_obligation) m;

UPDATE investory.accounting_reference_month
   SET vat_payable = output_vat - deductible_input_vat;

-- Historical rows are now reference-only. Operational acquisition starts empty.
DELETE FROM investory.accounting_poc_period_state;
DELETE FROM investory.accounting_filing_artifact;
DELETE FROM investory.accounting_authority_confirmation;
DELETE FROM investory.accounting_vat_transaction;
DELETE FROM investory.accounting_poc_obligation;
DELETE FROM investory.accounting_poc_tax_input;
DELETE FROM investory.accounting_poc_bank_transaction;
DELETE FROM investory.accounting_poc_expense_invoice;
DELETE FROM investory.accounting_poc_invoice;
DELETE FROM investory.accounting_poc_fact;

COMMENT ON TABLE investory.accounting_reference_month IS
    'Immutable Jan-Aug 2026 verification oracle. Never used as operational calculation input.';

-- A provider transaction is one operational staging row per profile, even when
-- the same transaction is present in overlapping exports.
CREATE UNIQUE INDEX uq_accounting_tmp_bank_profile_external_transaction
    ON investory.accounting_tmp_bank_transaction
       (profile_id, provider, external_account_id, external_transaction_id);
