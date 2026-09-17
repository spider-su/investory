-- Temporary POC/test data injection squashed from V01.010 through V01.019.


-- Squashed from app/src/main/resources/sql/migration/V01.027__accounting_staging_reconciliation.sql
CREATE TABLE investory.accounting_tmp_invoice (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    tax_period DATE NOT NULL,
    source_id BIGINT NOT NULL REFERENCES investory.accounting_source_evidence(id),
    source_type VARCHAR(16) NOT NULL,
    source_reference VARCHAR(256),
    source_hash BYTEA,
    document_kind VARCHAR(16) NOT NULL,
    document_date DATE,
    reference VARCHAR(128) NOT NULL,
    counterparty_name VARCHAR(256) NOT NULL,
    counterparty_tax_identifier VARCHAR(64),
    counterparty_country VARCHAR(2),
    currency CHAR(3) NOT NULL,
    net_amount NUMERIC(19,4) NOT NULL,
    vat_amount NUMERIC(19,4) NOT NULL,
    gross_amount NUMERIC(19,4) NOT NULL,
    vat_deduction_ratio NUMERIC(3,2),
    deductible_vat NUMERIC(19,4),
    vat_treatment VARCHAR(48),
    ksef_number VARCHAR(256),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reconciliation_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reconciliation_reason_codes VARCHAR(64)[] NOT NULL DEFAULT '{}',
    reconciliation_message VARCHAR(1000),
    promoted_at TIMESTAMPTZ,
    canonical_id BIGINT,
    canonical_type VARCHAR(32),
    CONSTRAINT chk_accounting_tmp_invoice_status CHECK (reconciliation_status IN ('PENDING','MATCH','NEW','MISMATCH','AMBIGUOUS','PROMOTED')),
    CONSTRAINT uq_accounting_tmp_invoice_source_reference UNIQUE (source_id, reference)
);


CREATE INDEX ix_accounting_tmp_invoice_period ON investory.accounting_tmp_invoice(profile_id, tax_period, reconciliation_status, id);


CREATE TABLE investory.accounting_tmp_bank_transaction (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    tax_period DATE NOT NULL,
    source_id BIGINT NOT NULL REFERENCES investory.accounting_source_evidence(id),
    source_type VARCHAR(16) NOT NULL,
    source_reference VARCHAR(256),
    source_hash BYTEA,
    provider VARCHAR(64) NOT NULL,
    external_account_id VARCHAR(256),
    external_transaction_id VARCHAR(256),
    booking_date DATE NOT NULL,
    value_date DATE,
    amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    counterparty_name VARCHAR(256),
    counterparty_account VARCHAR(256),
    remittance_information VARCHAR(1000),
    source_payload_hash VARCHAR(256),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reconciliation_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reconciliation_reason_codes VARCHAR(64)[] NOT NULL DEFAULT '{}',
    reconciliation_message VARCHAR(1000),
    promoted_at TIMESTAMPTZ,
    canonical_id BIGINT,
    CONSTRAINT chk_accounting_tmp_bank_status CHECK (reconciliation_status IN ('PENDING','MATCH','NEW','MISMATCH','AMBIGUOUS','PROMOTED')),
    CONSTRAINT uq_accounting_tmp_bank_identity UNIQUE (source_id, external_transaction_id)
);


CREATE INDEX ix_accounting_tmp_bank_period ON investory.accounting_tmp_bank_transaction(profile_id, tax_period, reconciliation_status, id);


CREATE TABLE investory.accounting_tmp_vat_transaction (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    tax_period DATE NOT NULL,
    source_id BIGINT NOT NULL REFERENCES investory.accounting_source_evidence(id),
    direction VARCHAR(16) NOT NULL,
    treatment VARCHAR(48) NOT NULL,
    tax_date DATE NOT NULL,
    counterparty_country VARCHAR(2),
    counterparty_tax_identifier VARCHAR(64),
    net_amount NUMERIC(19,4) NOT NULL,
    vat_amount NUMERIC(19,4) NOT NULL,
    deductible_vat NUMERIC(19,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reconciliation_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reconciliation_reason_codes VARCHAR(64)[] NOT NULL DEFAULT '{}',
    reconciliation_message VARCHAR(1000),
    promoted_at TIMESTAMPTZ,
    canonical_id BIGINT,
    CONSTRAINT chk_accounting_tmp_vat_status CHECK (reconciliation_status IN ('PENDING','MATCH','NEW','MISMATCH','AMBIGUOUS','PROMOTED'))
);


CREATE INDEX ix_accounting_tmp_vat_period ON investory.accounting_tmp_vat_transaction(profile_id, tax_period, reconciliation_status, id);
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


COMMENT ON TABLE investory.accounting_reference_month IS
    'Immutable Jan-Aug 2026 verification oracle. Never used as operational calculation input.';

-- Branch oracle: a second, non-UoP taxpayer case. This is intentionally
-- separate from accounting_poc_profile, whose singleton is the live POC
-- profile. Each month carries both policy variants and the date edge cases.
CREATE TABLE investory.accounting_reference_zus_branch (
    case_key VARCHAR(32) NOT NULL,
    tax_period DATE NOT NULL,
    has_uop BOOLEAN NOT NULL,
    voluntary_sickness BOOLEAN NOT NULL,
    ytd_revenue NUMERIC(19,4) NOT NULL,
    paid_social NUMERIC(19,4) NOT NULL,
    expected_health_band VARCHAR(16) NOT NULL,
    expected_social NUMERIC(19,4) NOT NULL,
    expected_deductible_social NUMERIC(19,4) NOT NULL,
    expected_health NUMERIC(19,4) NOT NULL,
    correction_sale_date DATE NOT NULL,
    correction_issue_date DATE NOT NULL,
    expected_correction_period DATE NOT NULL,
    foreign_document_date DATE NOT NULL,
    expected_fx_rate_date DATE NOT NULL,
    PRIMARY KEY (case_key, tax_period),
    CONSTRAINT chk_accounting_reference_zus_branch_period
        CHECK (tax_period >= DATE '2026-01-01' AND tax_period < DATE '2026-09-01'),
    CONSTRAINT chk_accounting_reference_zus_branch_band
        CHECK (expected_health_band IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT chk_accounting_reference_zus_branch_dates
        CHECK (expected_correction_period = DATE_TRUNC('month', correction_issue_date)::date)
);

COMMENT ON TABLE investory.accounting_reference_zus_branch IS
    'Immutable monthly ZUS branch oracle, including the non-UoP social path. Never used by calculations.';
-- A provider transaction is one operational staging row per profile, even when
-- the same transaction is present in overlapping exports.
CREATE UNIQUE INDEX uq_accounting_tmp_bank_profile_external_transaction
    ON investory.accounting_tmp_bank_transaction
       (profile_id, provider, external_account_id, external_transaction_id);
ALTER TABLE investory.accounting_tmp_invoice
    DROP CONSTRAINT IF EXISTS uq_accounting_tmp_invoice_source_reference;

CREATE UNIQUE INDEX IF NOT EXISTS uq_accounting_tmp_invoice_profile_source_reference
    ON investory.accounting_tmp_invoice(profile_id, source_id, reference);

ALTER TABLE investory.accounting_tmp_bank_transaction
    DROP CONSTRAINT IF EXISTS uq_accounting_tmp_bank_identity;

CREATE UNIQUE INDEX IF NOT EXISTS uq_accounting_tmp_bank_profile_identity
    ON investory.accounting_tmp_bank_transaction(profile_id, source_id, external_transaction_id);

ALTER TABLE investory.accounting_tmp_invoice
    ADD CONSTRAINT chk_accounting_tmp_invoice_period_month_start
    CHECK (EXTRACT(DAY FROM tax_period) = 1);

ALTER TABLE investory.accounting_tmp_bank_transaction
    ADD CONSTRAINT chk_accounting_tmp_bank_period_month_start
    CHECK (EXTRACT(DAY FROM tax_period) = 1);

ALTER TABLE investory.accounting_tmp_vat_transaction
    ADD CONSTRAINT chk_accounting_tmp_vat_period_month_start
    CHECK (EXTRACT(DAY FROM tax_period) = 1);

ALTER TABLE investory.accounting_reference_invoice
    ADD CONSTRAINT chk_accounting_reference_invoice_period_month_start
    CHECK (EXTRACT(DAY FROM tax_period) = 1);

ALTER TABLE investory.accounting_reference_expense_invoice
    ADD CONSTRAINT chk_accounting_reference_expense_period_month_start
    CHECK (EXTRACT(DAY FROM tax_period) = 1);

ALTER TABLE investory.accounting_reference_bank_transaction
    ADD CONSTRAINT chk_accounting_reference_bank_related_period_month_start
    CHECK (related_period IS NULL OR EXTRACT(DAY FROM related_period) = 1);

ALTER TABLE investory.accounting_reference_obligation
    ADD CONSTRAINT chk_accounting_reference_obligation_period_month_start
    CHECK (EXTRACT(DAY FROM tax_period) = 1);

ALTER TABLE investory.accounting_reference_tax_input
    ADD CONSTRAINT chk_accounting_reference_tax_input_period_month_start
    CHECK (EXTRACT(DAY FROM tax_period) = 1);

ALTER TABLE investory.accounting_reference_month
    ADD CONSTRAINT chk_accounting_reference_month_month_start
    CHECK (EXTRACT(DAY FROM tax_period) = 1);

ALTER TABLE investory.accounting_tmp_invoice
    DROP CONSTRAINT IF EXISTS accounting_tmp_invoice_source_id_fkey;

ALTER TABLE investory.accounting_tmp_invoice
    ADD CONSTRAINT fk_accounting_tmp_invoice_profile_source
    FOREIGN KEY (profile_id, source_id)
    REFERENCES investory.accounting_source_evidence (profile_id, id);

ALTER TABLE investory.accounting_tmp_bank_transaction
    DROP CONSTRAINT IF EXISTS accounting_tmp_bank_transaction_source_id_fkey;

ALTER TABLE investory.accounting_tmp_bank_transaction
    ADD CONSTRAINT fk_accounting_tmp_bank_profile_source
    FOREIGN KEY (profile_id, source_id)
    REFERENCES investory.accounting_source_evidence (profile_id, id);

ALTER TABLE investory.accounting_tmp_vat_transaction
    DROP CONSTRAINT IF EXISTS accounting_tmp_vat_transaction_source_id_fkey;

ALTER TABLE investory.accounting_tmp_vat_transaction
    ADD CONSTRAINT fk_accounting_tmp_vat_profile_source
    FOREIGN KEY (profile_id, source_id)
    REFERENCES investory.accounting_source_evidence (profile_id, id);


ALTER TABLE investory.accounting_tmp_invoice ALTER COLUMN profile_id DROP DEFAULT;

ALTER TABLE investory.accounting_tmp_bank_transaction ALTER COLUMN profile_id DROP DEFAULT;

ALTER TABLE investory.accounting_tmp_vat_transaction ALTER COLUMN profile_id DROP DEFAULT;

-- Preserve the source bank row's obligation period separately from the month used
-- to display and navigate the staging queue.
ALTER TABLE investory.accounting_tmp_bank_transaction
    ADD COLUMN IF NOT EXISTS related_period DATE;
ALTER TABLE investory.accounting_tmp_invoice
    ADD COLUMN IF NOT EXISTS due_date DATE;
ALTER TABLE investory.accounting_tmp_invoice
    ADD COLUMN vat_rate NUMERIC(5,2);



INSERT INTO investory.accounting_poc_fact
    (fact_date, fact_type, reference, counterparty_alias, currency, amount, tax_rate, note, profile_id)
VALUES
    ('2026-05-29', 'EXPENSE_INVOICE', 'EXPENSE_001', 'SUPPLIER_ACCOUNTING_001', 'PLN', 366.5400, 0.2300,
     'Accounting services; net 298.00 PLN, VAT 68.54 PLN, fully paid, KSeF present.' , 1),
    ('2026-07-02', 'SALES_INVOICE', 'FV 4/2026', 'CUSTOMER_PL_001', 'PLN', 40048.8000, 0.1200,
     'Original gross invoice value; IT consulting; 23% VAT; ryczalt profile rate 12%.' , 1),
    (NULL, 'SALES_CORRECTION', 'FK 1/2026', 'CUSTOMER_PL_001', 'PLN', -184.5000, NULL,
     'Correction linked to FV 4/2026; corrected receivable becomes 39,864.30 PLN.' , 1),
    ('2026-07-03', 'BANK_RECEIPT', 'EU recurring payment', 'CUSTOMER_EU_001', 'EUR', 7636.0000, NULL,
     'Foreign customer payment received on EUR business account. FX conversion intentionally delegated to existing Investory FX facilities.' , 1),
    ('2026-07-16', 'BANK_RECEIPT', 'FV 4/2026', 'CUSTOMER_PL_001', 'PLN', 39864.3000, NULL,
     'Payment matches the corrected receivable for FV 4/2026.' , 1),
    (NULL, 'SALES_INVOICE', 'FV 5/2026', 'CUSTOMER_PL_002', 'PLN', 19987.5000, 0.1200,
     'Domestic sales invoice historical fixture.' , 1),
    ('2026-08-20', 'RYCZALT_DUE', '2026-07', 'TAX_OFFICE', 'PLN', 5809.0000, 0.1200,
     'Known wFirma result for July 2026 business ryczalt.' , 1),
    ('2026-08-20', 'ZUS_DUE', '2026-07', 'ZUS', 'PLN', 1495.0400, NULL,
     'Known monthly health contribution obligation for the visible 2026 periods.' , 1),
    (NULL, 'VAT_PAYMENT', '2026-07', 'TAX_OFFICE', 'PLN', 3592.0000, NULL,
     'Historical VAT payment from the PLN bank statement.' , 1),
    ('2026-08-25', 'VAT_UE_DECLARATION', '2026-07', 'TAX_OFFICE', 'PLN', 0.0000, NULL,
     'VAT-UE reporting obligation; reporting event only, not an additional tax amount.' , 1);


INSERT INTO investory.accounting_poc_invoice
    (tax_period, issue_date, sale_date, fx_rate_date, reference, customer_alias, invoice_kind, currency,
     net_amount, vat_amount, gross_amount, correction_gross_amount, correction_net_amount,
     correction_vat_amount, expected_receivable, booked_net_pln, ryczalt_rate, note, profile_id)
VALUES
    ('2026-06-01', '2026-07-02', '2026-06-30', NULL, 'FV 4/2026', 'CUSTOMER_PL_001', 'DOMESTIC_SERVICE', 'PLN', 32560.0000, 7488.8000, 40048.8000, -184.5000, -150.0000, -34.5000, 39864.3000, 32560.0000, 0.1200, 'KSeF FV 4/2026: issue 2026-07-02, sale/accounting period June, original net 32,560.00 PLN. July FK 1/2026 is a separate -150.00 net / -34.50 VAT correction.', 1),
    ('2026-07-01', NULL, NULL, NULL, 'FV 5/2026', 'CUSTOMER_PL_002', 'DOMESTIC_SERVICE', 'PLN', 16250.0000, 3737.5000, 19987.5000, 0.0000, 0.0000, 0.0000, 19987.5000, 16250.0000, 0.1200, 'Domestic service invoice. Exact issue/sale dates were not present in the captured source, so they remain null.', 1),
    ('2026-07-01', '2026-07-31', '2026-07-31', '2026-07-30', 'EU-SERVICE-2026-07', 'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR', 7636.0000, 0.0000, 7636.0000, 0.0000, 0.0000, 0.0000, 7636.0000, 32908.8700, 0.1200, 'Recurring EU service. Tax value uses Investory FX on 2026-07-30; 32,908.87 PLN remains the observed accounting golden value.' , 1),
    ('2026-01-01', '2026-01-31', '2026-01-31', NULL, 'PDC-V1650-11', 'CUSTOMER_PL_001', 'DOMESTIC_SERVICE', 'PLN', 29600.0000, 6808.0000, 36408.0000, 0.0000, 0.0000, 0.0000, 36408.0000, 29600.0000, 0.1200, 'January domestic service invoice.' , 1),
    ('2026-02-01', '2026-02-28', '2026-02-28', NULL, 'PDC-V1650-12', 'CUSTOMER_PL_001', 'DOMESTIC_SERVICE', 'PLN', 29600.0000, 6808.0000, 36408.0000, 0.0000, 0.0000, 0.0000, 36408.0000, 29600.0000, 0.1200, 'February domestic service invoice.' , 1),
    ('2026-03-01', '2026-03-31', '2026-03-31', NULL, 'FV 1/2026', 'CUSTOMER_PL_001', 'DOMESTIC_SERVICE', 'PLN', 32560.0000, 7488.8000, 40048.8000, 0.0000, 0.0000, 0.0000, 40048.8000, 32560.0000, 0.1200, 'March domestic service invoice.' , 1),
    ('2026-04-01', '2026-04-30', '2026-04-30', NULL, 'FV 2/2026', 'CUSTOMER_PL_001', 'DOMESTIC_SERVICE', 'PLN', 31080.0000, 7148.4000, 38228.4000, 0.0000, 0.0000, 0.0000, 38228.4000, 31080.0000, 0.1200, 'April domestic service invoice.' , 1),
    ('2026-05-01', '2026-05-29', '2026-05-29', NULL, 'FV 3/2026', 'CUSTOMER_PL_001', 'DOMESTIC_SERVICE', 'PLN', 29600.0000, 6808.0000, 36408.0000, 0.0000, 0.0000, 0.0000, 36408.0000, 29600.0000, 0.1200, 'May domestic service invoice.' , 1),
    ('2026-08-01', '2026-08-31', '2026-08-31', NULL, 'FV 6/2026', 'CUSTOMER_PL_002', 'DOMESTIC_SERVICE', 'PLN', 26250.0000, 6037.5000, 32287.5000, 0.0000, 0.0000, 0.0000, 32287.5000, 26250.0000, 0.1200, 'August domestic service invoice; tax outputs were not captured, therefore August remains partial.' , 1),
    ('2026-02-01', '2026-02-28', '2026-02-28', '2026-02-27', 'EU-SERVICE-2026-02', 'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR', 7636.0000, 0.0000, 7636.0000, 0.0000, 0.0000, 0.0000, 7636.0000, 32249.1200, 0.1200, 'Observed February foreign-service accounting value.' , 1),
    ('2026-03-01', '2026-03-31', '2026-03-31', '2026-03-30', 'EU-SERVICE-2026-03', 'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR', 7636.0000, 0.0000, 7636.0000, 0.0000, 0.0000, 0.0000, 7636.0000, 32706.5200, 0.1200, 'Observed March foreign-service accounting value.' , 1),
    ('2026-04-01', '2026-04-30', '2026-04-30', '2026-04-29', 'EU-SERVICE-2026-04', 'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR', 7636.0000, 0.0000, 7636.0000, 0.0000, 0.0000, 0.0000, 7636.0000, 32481.2500, 0.1200, 'Observed April foreign-service accounting value.' , 1),
    ('2026-05-01', '2026-05-29', '2026-05-29', '2026-05-29', 'EU-SERVICE-2026-05', 'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR', 7636.0000, 0.0000, 7636.0000, 0.0000, 0.0000, 0.0000, 7636.0000, 32317.0800, 0.1200, 'Observed May foreign-service accounting value. Source review maps it to the 2026-05-29 NBP table-A EUR rate.' , 1),
    ('2026-06-01', '2026-06-30', '2026-06-30', '2026-06-29', 'EU-SERVICE-2026-06', 'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR', 7636.0000, 0.0000, 7636.0000, 0.0000, 0.0000, 0.0000, 7636.0000, 32750.8000, 0.1200, 'Observed June foreign-service accounting value.' , 1),
    ('2026-01-01', '2026-01-31', '2026-01-31', '2026-01-30', 'EU-SERVICE-2026-01', 'CUSTOMER_EU_001', 'EU_SERVICE', 'EUR', 7636.0000, 0.0000, 7636.0000, 0.0000, 0.0000, 0.0000, 7636.0000, 32171.2300, 0.1200, 'Source document 015 (Platform Developer): 7,636.00 EUR, sale 2026-01-31. NBP prior-business-day rate date 2026-01-30; booked wFirma value 32,171.23 PLN.' , 1);

INSERT INTO investory.accounting_poc_bank_transaction
    (booking_date, related_period, reference, counterparty_alias, currency, amount, transaction_type, scope, note,
     profile_id, provider, external_account_id, external_transaction_id, source_payload_hash)
VALUES
    ('2026-07-03', '2026-06-01', 'Service Agreements', 'CUSTOMER_EU_001', 'EUR', 7636.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'SEPA receipt for the prior monthly EU service; retained to prevent false July matching.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-01', NULL),
    ('2026-07-04', NULL, 'Transfer of funds', 'OWN_ACCOUNT', 'EUR', -7636.0000, 'INTERNAL_TRANSFER', 'EXCLUDED_INTERNAL', 'Transfer between own accounts; never revenue or expense.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-02', NULL),
    ('2026-07-16', '2026-06-01', 'FV 4/2026', 'CUSTOMER_PL_001', 'PLN', 39864.3000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Exactly matches the corrected FV 4/2026 receivable.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-03', NULL),
    ('2026-07-16', NULL, 'Transfer of funds', 'OWN_ACCOUNT', 'PLN', -20000.0000, 'INTERNAL_TRANSFER', 'EXCLUDED_INTERNAL', 'Own-account transfer.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-04', NULL),
    ('2026-08-05', '2026-07-01', 'Service Agreements', 'CUSTOMER_EU_001', 'EUR', 7636.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'SEPA receipt matched to the July EU service fixture.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-05', NULL),
    ('2026-08-13', '2026-07-01', 'FV 5/2026', 'CUSTOMER_PL_002', 'PLN', 19987.5000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Payment received for FV 5/2026.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-06', NULL),
    ('2026-08-18', '2026-07-01', '26M07 PPE business', 'TAX_OFFICE', 'PLN', -5809.0000, 'RYCZALT_PAYMENT', 'BUSINESS', 'Business ryczalt payment for 2026-07.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-07', NULL),
    ('2026-08-18', '2026-07-01', '26M07 VAT-7', 'TAX_OFFICE', 'PLN', -3592.0000, 'VAT_PAYMENT', 'BUSINESS', 'VAT payment for 2026-07.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-08', NULL),
    ('2026-08-18', '2026-07-01', '26M07 ZUS', 'ZUS', 'PLN', -1495.0000, 'ZUS_PAYMENT', 'BUSINESS', 'Bank payment for the 2026-07 ZUS obligation.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-09', NULL),
    ('2026-08-18', '2026-07-01', '26M07 PPE rental', 'TAX_OFFICE', 'PLN', -740.0000, 'RENTAL_TAX_PAYMENT', 'EXCLUDED_PRIVATE', 'Private rental ryczalt; deliberately outside the business POC.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-10', NULL),
    ('2026-08-18', NULL, 'Transfer of funds', 'OWN_ACCOUNT', 'PLN', -8000.0000, 'INTERNAL_TRANSFER', 'EXCLUDED_INTERNAL', 'Own-account transfer.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-11', NULL),
    ('2026-02-13', '2026-01-01', 'PDC-V1650-11', 'CUSTOMER_PL_001', 'PLN', 36408.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Payment of January domestic invoice.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-12', NULL),
    ('2026-03-13', '2026-02-01', 'PDC-V1650-12', 'CUSTOMER_PL_001', 'PLN', 36408.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Payment of February domestic invoice.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-13', NULL),
    ('2026-04-14', '2026-03-01', 'FV 1/2026', 'CUSTOMER_PL_001', 'PLN', 40048.8000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Payment of March domestic invoice.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-14', NULL),
    ('2026-05-14', '2026-04-01', 'FV 2/2026', 'CUSTOMER_PL_001', 'PLN', 38228.4000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Payment of April domestic invoice.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-15', NULL),
    ('2026-06-12', '2026-05-01', 'FV 3/2026', 'CUSTOMER_PL_001', 'PLN', 36408.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Payment of May domestic invoice.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-16', NULL),
    ('2026-03-05', '2026-02-01', 'Service Agreements', 'CUSTOMER_EU_001', 'EUR', 7636.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Receipt for February EU service.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-17', NULL),
    ('2026-04-07', '2026-03-01', 'Service Agreements', 'CUSTOMER_EU_001', 'EUR', 7636.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Receipt for March EU service.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-18', NULL),
    ('2026-05-06', '2026-04-01', 'Service Agreements', 'CUSTOMER_EU_001', 'EUR', 7636.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Receipt for April EU service.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-19', NULL),
    ('2026-06-05', '2026-05-01', 'Service Agreements', 'CUSTOMER_EU_001', 'EUR', 7636.0000, 'CUSTOMER_RECEIPT', 'BUSINESS', 'Receipt for May EU service.' , 1, 'CSV', 'LEGACY_SOURCE', 'legacy-20', NULL);

INSERT INTO investory.accounting_poc_obligation
    (tax_period, obligation_type, due_date, expected_amount, paid_amount, payment_date, status, note, profile_id)
VALUES
    ('2026-07-01', 'RYCZALT', '2026-08-20', 5791.0000, 5791.0000, '2026-08-18', 'MATCHED', 'Golden wFirma/business-tax amount after the July cross-period correction.', 1),
    ('2026-07-01', 'VAT', NULL, 3557.0000, 3557.0000, '2026-08-18', 'MATCHED', 'Golden VAT amount after the July cross-period correction.', 1),
    ('2026-07-01', 'VAT_UE', '2026-08-25', 0.0000, 0.0000, NULL, 'REPORTING_ONLY', 'VAT-UE reporting obligation; no additional tax payment.' , 1),
    ('2026-01-01', 'RYCZALT', '2026-02-20', 7323.0000, 7323.0000, NULL, 'GOLDEN', 'January ryczałt recomputed after restoring source document 015.' , 1),
    ('2026-01-01', 'VAT', NULL, 6714.0000, 6714.0000, NULL, 'GOLDEN', 'Known January VAT payment.' , 1),
    ('2026-01-01', 'ZUS', '2026-02-20', 1495.0400, 1495.0400, NULL, 'GOLDEN', 'Known January health contribution.' , 1),
    ('2026-02-01', 'RYCZALT', '2026-03-20', 7332.0000, 7332.0000, NULL, 'GOLDEN', 'Known February ryczalt payment.' , 1),
    ('2026-02-01', 'VAT', NULL, 6707.0000, 6707.0000, NULL, 'GOLDEN', 'Known February VAT payment.' , 1),
    ('2026-02-01', 'ZUS', '2026-03-20', 1495.0400, 1495.0400, NULL, 'GOLDEN', 'Known February health contribution.' , 1),
    ('2026-03-01', 'RYCZALT', '2026-04-20', 7742.0000, 7742.0000, NULL, 'GOLDEN', 'Known March ryczalt payment.' , 1),
    ('2026-03-01', 'VAT', NULL, 7251.0000, 7251.0000, NULL, 'GOLDEN', 'Known March VAT payment.' , 1),
    ('2026-03-01', 'ZUS', '2026-04-20', 1495.0400, 1495.0400, NULL, 'GOLDEN', 'Known March health contribution.' , 1),
    ('2026-04-01', 'RYCZALT', '2026-05-20', 7538.0000, 7538.0000, NULL, 'GOLDEN', 'Known April ryczalt payment.' , 1),
    ('2026-04-01', 'VAT', NULL, 7028.0000, 7028.0000, NULL, 'GOLDEN', 'Known April VAT payment.' , 1),
    ('2026-04-01', 'ZUS', '2026-05-20', 1495.0400, 1495.0400, NULL, 'GOLDEN', 'Known April health contribution.' , 1),
    ('2026-05-01', 'RYCZALT', '2026-06-22', 7340.0000, 7340.0000, NULL, 'GOLDEN', 'Known May ryczalt payment.' , 1),
    ('2026-05-01', 'VAT', NULL, 6601.0000, 6601.0000, NULL, 'GOLDEN', 'Known May VAT payment.' , 1),
    ('2026-05-01', 'ZUS', '2026-06-22', 1495.0400, 1495.0400, NULL, 'GOLDEN', 'Known May health contribution.' , 1),
    ('2026-06-01', 'RYCZALT', '2026-07-20', 7748.0000, 7748.0000, NULL, 'GOLDEN', 'Known June ryczalt payment.' , 1),
    ('2026-06-01', 'VAT', NULL, 7293.0000, 7293.0000, NULL, 'GOLDEN', 'Known June VAT payment.' , 1),
    ('2026-06-01', 'ZUS', '2026-07-20', 1495.0400, 1495.0400, NULL, 'GOLDEN', 'Known June health contribution.' , 1),
    ('2026-07-01', 'ZUS', '2026-08-20', 1495.0400, 1495.0000, '2026-08-18', 'MATCHED', 'Golden July ZUS/health contribution from wFirma. Bank cash evidence remains recorded separately.' , 1);

INSERT INTO investory.accounting_poc_tax_input (tax_period, input_type, amount, note, profile_id)
VALUES
    ('2026-01-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400, 'Known monthly health contribution.' , 1),
    ('2026-02-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400, 'Known monthly health contribution.' , 1),
    ('2026-03-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400, 'Known monthly health contribution.' , 1),
    ('2026-04-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400, 'Known monthly health contribution.' , 1),
    ('2026-05-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400, 'Known monthly health contribution.' , 1),
    ('2026-06-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400, 'Known monthly health contribution.' , 1),
    ('2026-07-01', 'HEALTH_CONTRIBUTION_PAID', 1495.0400, 'Visible wFirma health contribution amount. Ryczałt deducts 50% of paid health contribution.' , 1),
    ('2026-01-01', 'EXPECTED_REVENUE_PLN', 61771.2300, 'wFirma analytics monthly revenue golden.' , 1),
    ('2026-02-01', 'EXPECTED_REVENUE_PLN', 61849.1200, 'wFirma analytics monthly revenue golden.' , 1),
    ('2026-03-01', 'EXPECTED_REVENUE_PLN', 65266.5200, 'wFirma analytics monthly revenue golden.' , 1),
    ('2026-04-01', 'EXPECTED_REVENUE_PLN', 63561.2500, 'wFirma analytics monthly revenue golden.' , 1),
    ('2026-05-01', 'EXPECTED_REVENUE_PLN', 61917.0800, 'wFirma analytics monthly revenue golden.' , 1),
    ('2026-06-01', 'EXPECTED_REVENUE_PLN', 65310.8000, 'wFirma analytics monthly revenue golden.' , 1),
    ('2026-07-01', 'EXPECTED_REVENUE_PLN', 49008.8700, 'Reference revenue includes the July cross-period correction represented by the canonical accounting calculation.' , 1),
    ('2026-08-01', 'EXPECTED_REVENUE_PLN', 26250.0000, 'wFirma analytics monthly revenue golden; no foreign revenue is booked in August.' , 1),
    ('2026-01-01', 'EXPECTED_INPUT_VAT', 93.5400, 'wFirma VAT analytics purchase VAT.' , 1),
    ('2026-02-01', 'EXPECTED_INPUT_VAT', 100.5500, 'wFirma VAT analytics purchase VAT.' , 1),
    ('2026-03-01', 'EXPECTED_INPUT_VAT', 238.3800, 'wFirma VAT analytics purchase VAT.' , 1),
    ('2026-04-01', 'EXPECTED_INPUT_VAT', 120.2000, 'wFirma VAT analytics purchase VAT.' , 1),
    ('2026-05-01', 'EXPECTED_INPUT_VAT', 207.4200, 'wFirma VAT analytics purchase VAT.' , 1),
    ('2026-06-01', 'EXPECTED_INPUT_VAT', 196.1000, 'wFirma VAT analytics purchase VAT.' , 1),
    ('2026-07-01', 'EXPECTED_INPUT_VAT', 145.9900, 'wFirma VAT analytics purchase VAT.' , 1),
    ('2026-08-01', 'EXPECTED_INPUT_VAT', 0.0000, 'wFirma VAT analytics purchase VAT.' , 1);

INSERT INTO investory.accounting_poc_expense_invoice
    (tax_period, invoice_date, reference, supplier_alias, category, currency, net_amount, vat_amount, gross_amount, vat_deduction_ratio, source_quality, note, profile_id)
VALUES
    ('2026-01-01', NULL, 'I26394B03000087', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 253.3400, 58.2700, 311.6100, 0.50, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; 50% mixed-use vehicle VAT deduction.' , 1),
    ('2026-01-01', NULL, '91/1/2026', 'SUPPLIER_SALSOFT_001', 'ACCOUNTING_SERVICE', 'PLN', 280.0000, 64.4000, 344.4000, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked SalSoft accounting expense.' , 1),
    ('2026-02-01', '2026-02-27', '1118/2/2026', 'SUPPLIER_SALSOFT_001', 'ACCOUNTING_SERVICE', 'PLN', 298.0000, 68.5400, 366.5400, 1.00, 'SOURCE_DOCUMENT', 'KSeF purchase invoice captured: net 298.00, VAT 68.54, gross 366.54.' , 1),
    ('2026-02-01', NULL, 'I26394B03002189', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 278.3200, 64.0100, 342.3300, 0.50, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; 50% mixed-use vehicle VAT deduction.' , 1),
    ('2026-03-01', NULL, 'I26394B01006279', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 323.5800, 74.4200, 398.0000, 0.50, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; 50% mixed-use vehicle VAT deduction.' , 1),
    ('2026-03-01', NULL, '2186/3/2026', 'SUPPLIER_SALSOFT_001', 'ACCOUNTING_SERVICE', 'PLN', 298.0000, 68.5400, 366.5400, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked SalSoft accounting expense.' , 1),
    ('2026-03-01', '2026-03-06', '5034146070', 'SUPPLIER_NOWA_ERA_001', 'BUSINESS_SERVICE', 'PLN', 406.5000, 93.5000, 500.0000, 1.00, 'SOURCE_DOCUMENT', 'KSeF purchase invoice captured: net 406.50, VAT 93.50, gross 500.00.' , 1),
    ('2026-03-01', NULL, 'I26394B03003487', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 340.2000, 78.2500, 418.4500, 0.50, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; 50% mixed-use vehicle VAT deduction.' , 1),
    ('2026-04-01', NULL, '538/4/2026', 'SUPPLIER_SALSOFT_001', 'ACCOUNTING_SERVICE', 'PLN', 468.0000, 107.6400, 575.6400, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked SalSoft expense; exact VAT composition still needs source-document verification.' , 1),
    ('2026-04-01', NULL, 'I26394B03005740', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 313.8100, 25.1100, 338.9200, 0.50, 'WFIRMA_LIST_DERIVED_8', 'BP Europa fuel; reconstructed at 8% VAT from gross 338.92 PLN; 50% mixed-use vehicle VAT deduction.' , 1),
    ('2026-05-01', '2026-05-30', 'I26394B03009405', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 356.5600, 28.5200, 385.0800, 0.50, 'SOURCE_DOCUMENT', 'Source BP invoice: 8% VAT, net 356.56, VAT 28.52, gross 385.08; mixed-use vehicle deducts 50% VAT.' , 1),
    ('2026-05-01', '2026-05-16', 'I26394801011115', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 359.3900, 28.7500, 388.1400, 0.50, 'SOURCE_DOCUMENT', 'Source BP invoice: 8% VAT, net 359.39, VAT 28.75, gross 388.14; mixed-use vehicle deducts 50% VAT.' , 1),
    ('2026-05-01', '2026-05-29', '752/5/2026', 'SUPPLIER_SALSOFT_001', 'ACCOUNTING_SERVICE', 'PLN', 298.0000, 68.5400, 366.5400, 1.00, 'SOURCE_DOCUMENT', 'Captured SalSoft invoice: net 298.00, VAT 68.54, gross 366.54.' , 1),
    ('2026-05-01', NULL, 'FS-652540/26/MEPL1', 'SUPPLIER_TERG_001', 'EQUIPMENT', 'PLN', 430.6800, 99.0600, 529.7400, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked TERG expense; exact VAT treatment still needs source-document verification.' , 1),
    ('2026-05-01', '2026-05-02', 'FVF/463/58/5/2026', 'SUPPLIER_ANIWIM_001', 'VEHICLE_FUEL', 'PLN', 279.4200, 22.3500, 301.7700, 0.50, 'SOURCE_DOCUMENT', 'Source Aniwim fuel invoice: 8% VAT, net 279.42, VAT 22.35, gross 301.77; mixed-use vehicle deducts 50% VAT.' , 1),
    ('2026-06-01', NULL, '1571/6/2026', 'SUPPLIER_SALSOFT_001', 'ACCOUNTING_SERVICE', 'PLN', 298.0000, 68.5400, 366.5400, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked SalSoft accounting expense.' , 1),
    ('2026-06-01', NULL, 'FA/1789/2026', 'SUPPLIER_SWIAT_DRUKU_001', 'BUSINESS_SERVICE', 'PLN', 185.3700, 42.6300, 228.0000, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; exact VAT treatment still needs source-document verification.' , 1),
    ('2026-06-01', NULL, 'I26394B03011055', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 336.3300, 26.9100, 363.2400, 0.50, 'WFIRMA_LIST_DERIVED_8', 'wFirma gross 363.24; fuel uses 8% VAT in this POC, derived net 336.33 / VAT 26.91; mixed-use vehicle deducts 50% VAT.' , 1),
    ('2026-06-01', NULL, 'I26394B03010191', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 323.5500, 25.8800, 349.4300, 0.50, 'WFIRMA_LIST_DERIVED_8', 'wFirma gross 349.43; fuel uses 8% VAT in this POC, derived net 323.55 / VAT 25.88; mixed-use vehicle deducts 50% VAT.' , 1),
    ('2026-06-01', NULL, 'FVS/xk/00000127858', 'SUPPLIER_XKOM_001', 'EQUIPMENT', 'PLN', 254.4600, 58.5300, 312.9900, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked X-KOM expense; exact VAT treatment still needs source-document verification.' , 1),
    ('2026-07-01', NULL, '1339/7/2026', 'SUPPLIER_SALSOFT_001', 'ACCOUNTING_SERVICE', 'PLN', 298.0000, 68.5400, 366.5400, 1.00, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked SalSoft accounting expense.' , 1),
    ('2026-07-01', NULL, 'I26100B01009678', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 376.6200, 86.6200, 463.2400, 0.50, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; 50% mixed-use vehicle VAT deduction.' , 1),
    ('2026-07-01', NULL, 'I26394B01015705', 'SUPPLIER_BP_001', 'VEHICLE_FUEL', 'PLN', 296.8200, 68.2700, 365.0900, 0.50, 'WFIRMA_LIST_DERIVED_23', 'wFirma booked expense; 50% mixed-use vehicle VAT deduction.' , 1);


INSERT INTO investory.accounting_poc_profile (id, has_uop, profile_id)
VALUES (1, TRUE, 1);


-- Explicit POC calculation input for the normal-JDG branch. This is the 2026 minimum
-- compulsory social-side amount without voluntary sickness insurance. Keeping it as an
-- input avoids introducing statutory rate/base tables into this POC.
INSERT INTO investory.accounting_poc_tax_input (tax_period, input_type, amount, note, profile_id)
VALUES
    ('2026-01-01', 'JDG_COMPULSORY_SOCIAL_ZUS', 1788.2900, '2026 normal-JDG compulsory social-side ZUS input; excludes voluntary sickness insurance.' , 1),
    ('2026-02-01', 'JDG_COMPULSORY_SOCIAL_ZUS', 1788.2900, '2026 normal-JDG compulsory social-side ZUS input; excludes voluntary sickness insurance.' , 1),
    ('2026-03-01', 'JDG_COMPULSORY_SOCIAL_ZUS', 1788.2900, '2026 normal-JDG compulsory social-side ZUS input; excludes voluntary sickness insurance.' , 1),
    ('2026-04-01', 'JDG_COMPULSORY_SOCIAL_ZUS', 1788.2900, '2026 normal-JDG compulsory social-side ZUS input; excludes voluntary sickness insurance.' , 1),
    ('2026-05-01', 'JDG_COMPULSORY_SOCIAL_ZUS', 1788.2900, '2026 normal-JDG compulsory social-side ZUS input; excludes voluntary sickness insurance.' , 1),
    ('2026-06-01', 'JDG_COMPULSORY_SOCIAL_ZUS', 1788.2900, '2026 normal-JDG compulsory social-side ZUS input; excludes voluntary sickness insurance.' , 1),
    ('2026-07-01', 'JDG_COMPULSORY_SOCIAL_ZUS', 1788.2900, '2026 normal-JDG compulsory social-side ZUS input; excludes voluntary sickness insurance.' , 1),
    ('2026-08-01', 'JDG_COMPULSORY_SOCIAL_ZUS', 1788.2900, '2026 normal-JDG compulsory social-side ZUS input; excludes voluntary sickness insurance.' , 1);


UPDATE investory.accounting_poc_profile
   SET nip = COALESCE(nip, '1010000000'),
       full_name = COALESCE(full_name, 'Investory Accounting POC'),
       tax_office_code = COALESCE(tax_office_code, '1215'),
       email = COALESCE(email, 'accounting@example.invalid');


UPDATE investory.accounting_poc_bank_transaction
   SET provider = 'CSV',
       external_account_id = 'LEGACY_SOURCE',
       external_transaction_id = COALESCE(source_row_identity, 'legacy-' || id::varchar),
       source_payload_hash = NULL
 WHERE provider IS NULL;


UPDATE investory.portfolios p
   SET taxpayer_nip = COALESCE(p.taxpayer_nip, legacy.nip),
       taxpayer_full_name = COALESCE(p.taxpayer_full_name, legacy.full_name),
       taxpayer_first_name = COALESCE(p.taxpayer_first_name, legacy.first_name),
       taxpayer_surname = COALESCE(p.taxpayer_surname, legacy.surname),
       taxpayer_date_of_birth = COALESCE(p.taxpayer_date_of_birth, legacy.date_of_birth),
       taxpayer_tax_office_code = COALESCE(p.taxpayer_tax_office_code, legacy.tax_office_code),
       taxpayer_email = COALESCE(p.taxpayer_email, legacy.email),
       tax_micro_account = COALESCE(p.tax_micro_account, legacy.vat_payment_account, legacy.ryczalt_payment_account),
       zus_payment_account = COALESCE(p.zus_payment_account, legacy.zus_payment_account)
  FROM investory.accounting_poc_profile legacy
 WHERE p.id = 1 AND legacy.id = 1;


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
       COALESCE((SELECT SUM(i.vat_amount) FROM investory.accounting_reference_invoice i WHERE i.profile_id=m.profile_id AND i.tax_period=m.tax_period),0),
       COALESCE((SELECT SUM(ROUND(e.vat_amount * e.vat_deduction_ratio, 2)) FROM investory.accounting_reference_expense_invoice e WHERE e.profile_id=m.profile_id AND e.tax_period=m.tax_period),0),
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

INSERT INTO investory.accounting_reference_zus_branch
    (case_key,tax_period,has_uop,voluntary_sickness,ytd_revenue,paid_social,expected_health_band,
     expected_social,expected_deductible_social,expected_health,correction_sale_date,
     correction_issue_date,expected_correction_period,foreign_document_date,expected_fx_rate_date)
VALUES
 ('UOP','2026-01-01',TRUE,FALSE,0.00,0.00,'LOW',0.00,0.00,498.35,'2026-01-31','2026-02-02','2026-02-01','2026-01-31','2026-01-30'),
 ('JDG_PLAIN','2026-01-01',FALSE,FALSE,0.00,0.00,'LOW',1788.29,1649.82,498.35,'2026-01-31','2026-02-02','2026-02-01','2026-01-02','2025-12-31'),
 ('JDG_SICKNESS','2026-01-01',FALSE,TRUE,1649.82,1649.82,'LOW',1926.76,1788.29,498.35,'2026-01-31','2026-02-02','2026-02-01','2026-01-31','2026-01-30'),
 ('UOP','2026-02-01',TRUE,FALSE,60000.00,0.00,'LOW',0.00,0.00,498.35,'2026-02-28','2026-03-02','2026-03-01','2026-02-28','2026-02-27'),
 ('JDG_SICKNESS','2026-02-01',FALSE,TRUE,61649.82,1649.82,'LOW',1926.76,1788.29,498.35,'2026-02-28','2026-03-02','2026-03-01','2026-02-28','2026-02-27'),
 ('UOP','2026-03-01',TRUE,FALSE,60000.01,0.00,'MEDIUM',0.00,0.00,830.58,'2026-03-31','2026-04-02','2026-04-01','2026-03-31','2026-03-30'),
 ('JDG_SICKNESS','2026-03-01',FALSE,TRUE,61649.83,1649.82,'MEDIUM',1926.76,1788.29,830.58,'2026-03-31','2026-04-02','2026-04-01','2026-03-31','2026-03-30'),
 ('UOP','2026-04-01',TRUE,FALSE,300000.00,0.00,'MEDIUM',0.00,0.00,830.58,'2026-04-30','2026-05-02','2026-05-01','2026-04-30','2026-04-29'),
 ('JDG_SICKNESS','2026-04-01',FALSE,TRUE,301649.82,1649.82,'MEDIUM',1926.76,1788.29,830.58,'2026-04-30','2026-05-02','2026-05-01','2026-04-30','2026-04-29'),
 ('UOP','2026-05-01',TRUE,FALSE,300000.01,0.00,'HIGH',0.00,0.00,1495.04,'2026-05-31','2026-06-02','2026-06-01','2026-05-31','2026-05-29'),
 ('JDG_SICKNESS','2026-05-01',FALSE,TRUE,301649.83,1649.82,'HIGH',1926.76,1788.29,1495.04,'2026-05-31','2026-06-02','2026-06-01','2026-05-31','2026-05-29'),
 ('UOP','2026-06-01',TRUE,FALSE,500000.00,0.00,'HIGH',0.00,0.00,1495.04,'2026-06-30','2026-07-02','2026-07-01','2026-06-30','2026-06-29'),
 ('JDG_SICKNESS','2026-06-01',FALSE,TRUE,501649.82,1649.82,'HIGH',1926.76,1788.29,1495.04,'2026-06-30','2026-07-02','2026-07-01','2026-06-30','2026-06-29'),
 ('UOP','2026-07-01',TRUE,FALSE,59999.99,0.00,'LOW',0.00,0.00,498.35,'2026-07-31','2026-08-03','2026-08-01','2026-07-31','2026-07-30'),
 ('JDG_SICKNESS','2026-07-01',FALSE,TRUE,61649.81,1649.82,'LOW',1926.76,1788.29,498.35,'2026-07-31','2026-08-03','2026-08-01','2026-07-31','2026-07-30'),
 ('UOP','2026-08-01',TRUE,FALSE,300000.01,0.00,'HIGH',0.00,0.00,1495.04,'2026-08-31','2026-09-02','2026-09-01','2026-08-31','2026-08-28'),
 ('JDG_SICKNESS','2026-08-01',FALSE,TRUE,301649.83,1649.82,'HIGH',1926.76,1788.29,1495.04,'2026-08-31','2026-09-02','2026-09-01','2026-08-31','2026-08-28');


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

UPDATE investory.accounting_poc_profile SET profile_id = COALESCE(profile_id, 1);


UPDATE investory.accounting_poc_fact SET profile_id = COALESCE(profile_id, 1);

UPDATE investory.accounting_source_evidence SET profile_id = COALESCE(profile_id, 1);

UPDATE investory.accounting_poc_obligation SET profile_id = COALESCE(profile_id, 1);

UPDATE investory.accounting_poc_tax_input SET profile_id = COALESCE(profile_id, 1);

UPDATE investory.accounting_poc_period_state SET profile_id = COALESCE(profile_id, 1);

UPDATE investory.accounting_filing_artifact SET profile_id = COALESCE(profile_id, 1);

UPDATE investory.accounting_authority_confirmation SET profile_id = COALESCE(profile_id, 1);

UPDATE investory.accounting_vat_transaction SET profile_id = COALESCE(profile_id, 1);


INSERT INTO investory.accounting_known_counterparty
    (profile_id, tax_identifier, country, canonical_name)
SELECT DISTINCT profile_id,
       UPPER(REGEXP_REPLACE(counterparty_tax_identifier, '[^[:alnum:]]', '', 'g')),
       UPPER(counterparty_country),
       customer_alias
  FROM investory.accounting_poc_invoice
 WHERE counterparty_tax_identifier IS NOT NULL
   AND counterparty_country IS NOT NULL
   AND customer_alias IS NOT NULL
ON CONFLICT (profile_id, country, tax_identifier) DO NOTHING;


-- Backfill retained operational documents. Source evidence remains nullable during this expand phase:
-- historical fixture rows may predate immutable source ingestion.
INSERT INTO investory.accounting_document (
    profile_id, direction, document_kind, tax_period, issue_date, supply_date, due_date,
    reference, counterparty_name, counterparty_tax_identifier, counterparty_country, currency,
    net_amount, vat_amount, gross_amount, fx_rate_date, booked_net_pln, ryczalt_rate, source_id,
    ksef_number, filing_evidence, note)
SELECT profile_id,
       'SALE',
       CASE WHEN invoice_kind = 'CREDIT_NOTE' THEN 'CREDIT_NOTE' ELSE 'INVOICE' END,
       tax_period, issue_date, sale_date, due_date, reference, customer_alias,
       counterparty_tax_identifier, counterparty_country, currency,
       net_amount, vat_amount, gross_amount, fx_rate_date, booked_net_pln, ryczalt_rate, source_id,
       ksef_number, filing_evidence, note
  FROM investory.accounting_poc_invoice
ON CONFLICT (profile_id, direction, reference) DO NOTHING;

INSERT INTO investory.accounting_document (
    profile_id, direction, document_kind, tax_period, issue_date, supply_date, due_date,
    reference, counterparty_name, counterparty_tax_identifier, counterparty_country, currency,
    net_amount, vat_amount, gross_amount, category, vat_deduction_ratio, source_quality, source_id,
    ksef_number, filing_evidence, note)
SELECT profile_id, 'PURCHASE', 'INVOICE', tax_period, invoice_date, invoice_date, due_date,
       reference, supplier_alias, counterparty_tax_identifier, counterparty_country, currency,
       net_amount, vat_amount, gross_amount, category, vat_deduction_ratio, source_quality, source_id,
       ksef_number, filing_evidence, note
  FROM investory.accounting_poc_expense_invoice
ON CONFLICT (profile_id, direction, reference) DO NOTHING;

UPDATE investory.accounting_document document
   SET counterparty_id = counterpart.id
  FROM investory.accounting_known_counterparty counterpart
 WHERE document.profile_id = counterpart.profile_id
   AND document.counterparty_country = counterpart.country
   AND UPPER(REGEXP_REPLACE(document.counterparty_tax_identifier, '[^[:alnum:]]', '', 'g')) = counterpart.tax_identifier
   AND document.counterparty_id IS NULL;

-- Retained rows without an explicit VAT classification get the same default treatment used by
-- the existing filing projection. Explicit historical VAT classifications below take precedence.
INSERT INTO investory.accounting_document_vat_bucket
    (document_id, treatment, vat_rate, net_amount, vat_amount, deductible_vat)
SELECT document.id,
       CASE
           WHEN document.direction = 'PURCHASE' THEN 'DOMESTIC_PURCHASE'
           WHEN document.currency = 'PLN' THEN 'DOMESTIC_VAT'
           ELSE 'EU_B2B_REVERSE_CHARGE'
       END,
       CASE
           WHEN document.currency = 'PLN' AND document.net_amount <> 0
               THEN ROUND(document.vat_amount * 100 / document.net_amount, 2)
           ELSE NULL
       END,
       document.net_amount,
       document.vat_amount,
       CASE WHEN document.direction = 'PURCHASE'
           THEN ROUND(document.vat_amount * COALESCE(document.vat_deduction_ratio, 1), 2)
           ELSE 0
       END
  FROM investory.accounting_document document
 WHERE NOT EXISTS (
           SELECT 1
             FROM investory.accounting_vat_transaction vat
            WHERE vat.profile_id = document.profile_id
              AND vat.reference = document.reference
              AND vat.direction = document.direction)
ON CONFLICT (document_id, treatment, vat_rate) DO NOTHING;

INSERT INTO investory.accounting_document_vat_bucket
    (document_id, treatment, vat_rate, net_amount, vat_amount, deductible_vat)
SELECT document.id, vat.treatment, vat.vat_rate, vat.net_amount, vat.vat_amount, vat.deductible_vat
  FROM investory.accounting_vat_transaction vat
  JOIN investory.accounting_document document
    ON document.profile_id = vat.profile_id
   AND document.reference = vat.reference
   AND document.direction = vat.direction
ON CONFLICT (document_id, treatment, vat_rate)
    DO UPDATE SET net_amount = EXCLUDED.net_amount,
                  vat_amount = EXCLUDED.vat_amount,
                  deductible_vat = EXCLUDED.deductible_vat;
