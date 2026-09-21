
-- Squashed source: app/src/main/resources/sql/migration/V01.024__accounting_staging_document_semantics.sql
-- Staging must retain every reviewed fact required for safe canonical promotion.
ALTER TABLE investory.accounting_tmp_invoice
    ADD COLUMN IF NOT EXISTS category VARCHAR(64),
    ADD COLUMN IF NOT EXISTS source_quality VARCHAR(32),
    ADD COLUMN IF NOT EXISTS corrects_document_reference VARCHAR(128);

-- Squashed source: app/src/main/resources/sql/migration/V01.025__accounting_calculation_snapshots.sql
CREATE TABLE investory.accounting_calculation_snapshot (
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    tax_period DATE NOT NULL,
    schema_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    calculation_hash VARCHAR(64) NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (profile_id, tax_period),
    CONSTRAINT chk_accounting_calculation_snapshot_period_month_start
        CHECK (EXTRACT(DAY FROM tax_period) = 1)
);

COMMENT ON TABLE investory.accounting_calculation_snapshot IS
    'Authoritative serialized calculation for locked accounting periods. Rebuilt after reopen.';

-- Squashed source: app/src/main/resources/sql/migration/V01.026__ryczalt_persistence.sql
CREATE TABLE investory.ryczalt_period (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    period_year INTEGER NOT NULL,
    period_month INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    calculated_at TIMESTAMPTZ,
    frozen_at TIMESTAMPTZ,
    reopened_at TIMESTAMPTZ,
    reopen_reason VARCHAR(1000),
    CONSTRAINT uq_ryczalt_period_profile_month UNIQUE (profile_id, period_year, period_month),
    CONSTRAINT chk_ryczalt_period_month CHECK (period_month BETWEEN 1 AND 12),
    CONSTRAINT chk_ryczalt_period_status CHECK (status IN ('OPEN', 'DIRTY', 'CALCULATED', 'PAID', 'FROZEN')),
    CONSTRAINT chk_ryczalt_period_reopen_reason
        CHECK (reopened_at IS NULL OR length(btrim(reopen_reason)) > 0)
);

CREATE INDEX ix_ryczalt_period_profile_status
    ON investory.ryczalt_period(profile_id, status, period_year, period_month);

CREATE TABLE investory.ryczalt_invoice (
    id BIGSERIAL PRIMARY KEY,
    period_id BIGINT NOT NULL REFERENCES investory.ryczalt_period(id) ON DELETE CASCADE,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    direction VARCHAR(8) NOT NULL,
    reference VARCHAR(128) NOT NULL,
    issue_date DATE NOT NULL,
    accounting_date DATE NOT NULL,
    net_amount NUMERIC(19,4) NOT NULL,
    vat_amount NUMERIC(19,4) NOT NULL,
    gross_amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    booked_net_pln NUMERIC(19,4),
    ryczalt_rate NUMERIC(7,4),
    deductible_vat NUMERIC(19,4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    counterparty_id BIGINT,
    approval_status VARCHAR(16) NOT NULL DEFAULT 'NEEDS_REVIEW',
    approval_method VARCHAR(32),
    payment_verification_policy VARCHAR(16) NOT NULL DEFAULT 'REQUIRED',
    classification VARCHAR(128),
    vat_treatment VARCHAR(128),
    vat_deduction_ratio NUMERIC(7,6),
    payment_status VARCHAR(16) NOT NULL DEFAULT 'UNMATCHED',
    CONSTRAINT chk_ryczalt_invoice_direction CHECK (direction IN ('INCOME', 'COST')),
    CONSTRAINT chk_ryczalt_invoice_currency CHECK (length(btrim(currency)) = 3),
    CONSTRAINT chk_ryczalt_invoice_approval CHECK (approval_status IN ('NEEDS_REVIEW','APPROVED')),
    CONSTRAINT chk_ryczalt_invoice_payment_policy CHECK (payment_verification_policy IN ('REQUIRED','NOT_REQUIRED')),
    CONSTRAINT chk_ryczalt_invoice_payment_status
        CHECK (payment_status IN ('MATCHED', 'PARTIALLY_MATCHED', 'UNMATCHED', 'NOT_REQUIRED'))
);

CREATE INDEX ix_ryczalt_invoice_period ON investory.ryczalt_invoice(profile_id, period_id, accounting_date, id);
CREATE INDEX ix_ryczalt_invoice_counterparty ON investory.ryczalt_invoice(profile_id, counterparty_id, accounting_date, id);
CREATE INDEX ix_ryczalt_invoice_profile_direction_reference
    ON investory.ryczalt_invoice(profile_id, direction, reference);

CREATE TABLE investory.ryczalt_transaction (
    id BIGSERIAL PRIMARY KEY,
    period_id BIGINT NOT NULL REFERENCES investory.ryczalt_period(id) ON DELETE CASCADE,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    booking_date DATE NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    reference VARCHAR(256),
    counterparty VARCHAR(256),
    description VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ryczalt_transaction_profile_id UNIQUE (profile_id, id),
    CONSTRAINT chk_ryczalt_transaction_currency CHECK (length(btrim(currency)) = 3)
);

CREATE INDEX ix_ryczalt_transaction_period ON investory.ryczalt_transaction(profile_id, period_id, booking_date, id);
CREATE INDEX ix_ryczalt_transaction_reference ON investory.ryczalt_transaction(profile_id, reference);

CREATE TABLE investory.ryczalt_calculation (
    id BIGSERIAL PRIMARY KEY,
    period_id BIGINT NOT NULL REFERENCES investory.ryczalt_period(id) ON DELETE CASCADE,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    calculation_type VARCHAR(8) NOT NULL,
    status VARCHAR(16) NOT NULL,
    revision INTEGER NOT NULL DEFAULT 1,
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    result_json JSONB NOT NULL,
    input_fingerprint VARCHAR(128) NOT NULL,
    rule_version VARCHAR(64) NOT NULL,
    calculator_version VARCHAR(64) NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_ryczalt_calculation_type CHECK (calculation_type IN ('RYCZALT', 'VAT', 'ZUS')),
    CONSTRAINT chk_ryczalt_calculation_status CHECK (status IN ('CURRENT', 'DIRTY', 'STALE', 'FROZEN', 'CALCULATED'))
);

CREATE INDEX ix_ryczalt_calculation_period ON investory.ryczalt_calculation(profile_id, period_id, calculation_type);
CREATE UNIQUE INDEX uq_ryczalt_calculation_current
    ON investory.ryczalt_calculation(profile_id, period_id, calculation_type)
    WHERE is_current;
CREATE UNIQUE INDEX uq_ryczalt_calculation_revision
    ON investory.ryczalt_calculation(profile_id, period_id, calculation_type, revision);

CREATE TABLE investory.ryczalt_obligation (
    id BIGSERIAL PRIMARY KEY,
    period_id BIGINT NOT NULL REFERENCES investory.ryczalt_period(id) ON DELETE CASCADE,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    obligation_type VARCHAR(8) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    due_date DATE,
    status VARCHAR(16) NOT NULL,
    calculation_id BIGINT REFERENCES investory.ryczalt_calculation(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ryczalt_obligation_period_type UNIQUE (profile_id, period_id, obligation_type),
    CONSTRAINT uq_ryczalt_obligation_profile_id UNIQUE (profile_id, id),
    CONSTRAINT chk_ryczalt_obligation_type CHECK (obligation_type IN ('RYCZALT', 'VAT', 'ZUS')),
    CONSTRAINT chk_ryczalt_obligation_status CHECK (status IN ('OPEN', 'PARTIALLY_PAID', 'PAID', 'OVERPAID', 'FROZEN'))
);

CREATE INDEX ix_ryczalt_obligation_period ON investory.ryczalt_obligation(profile_id, period_id, obligation_type);

CREATE TABLE investory.ryczalt_fx_rate (
    id BIGSERIAL PRIMARY KEY,
    currency VARCHAR(3) NOT NULL,
    effective_date DATE NOT NULL,
    rate NUMERIC(19,8) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    provider_reference VARCHAR(256),
    fetched_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_ryczalt_fx_identity UNIQUE (provider, currency, effective_date),
    CONSTRAINT chk_ryczalt_fx_rate_positive CHECK (rate > 0)
);

CREATE INDEX ix_ryczalt_fx_lookup ON investory.ryczalt_fx_rate(currency, effective_date);

CREATE TABLE investory.ryczalt_source_reference (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    entity_type VARCHAR(32) NOT NULL,
    entity_id BIGINT NOT NULL,
    source VARCHAR(64) NOT NULL,
    external_id VARCHAR(256) NOT NULL,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ryczalt_source_reference UNIQUE (profile_id, entity_type, source, external_id)
);

CREATE INDEX ix_ryczalt_source_entity ON investory.ryczalt_source_reference(profile_id, entity_type, entity_id);

-- Squashed source: app/src/main/resources/sql/migration/V01.027__ryczalt_lifecycle_history.sql
CREATE TABLE investory.ryczalt_correction (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    original_period_id BIGINT NOT NULL REFERENCES investory.ryczalt_period(id),
    affected_entity_type VARCHAR(32) NOT NULL,
    affected_entity_id BIGINT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correction_period_id BIGINT REFERENCES investory.ryczalt_period(id),
    CONSTRAINT chk_ryczalt_correction_reason CHECK (length(btrim(reason)) > 0)
);

CREATE INDEX ix_ryczalt_correction_original_period
    ON investory.ryczalt_correction(profile_id, original_period_id, requested_at);

CREATE TABLE investory.ryczalt_audit_event (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    period_id BIGINT REFERENCES investory.ryczalt_period(id),
    event_type VARCHAR(32) NOT NULL,
    reason VARCHAR(1000),
    actor VARCHAR(256),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB
);

CREATE INDEX ix_ryczalt_audit_period ON investory.ryczalt_audit_event(profile_id, period_id, occurred_at);

-- Squashed source: app/src/main/resources/sql/migration/V01.028__ryczalt_payment_matching.sql
CREATE TABLE investory.ryczalt_payment_match (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    obligation_id BIGINT NOT NULL,
    transaction_id BIGINT NOT NULL,
    matched_amount NUMERIC(19,4) NOT NULL,
    match_type VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ryczalt_payment_match_pair UNIQUE (profile_id, obligation_id, transaction_id),
    CONSTRAINT fk_ryczalt_payment_match_obligation
        FOREIGN KEY (profile_id, obligation_id)
        REFERENCES investory.ryczalt_obligation(profile_id, id),
    CONSTRAINT fk_ryczalt_payment_match_transaction
        FOREIGN KEY (profile_id, transaction_id)
        REFERENCES investory.ryczalt_transaction(profile_id, id),
    CONSTRAINT chk_ryczalt_payment_match_amount CHECK (matched_amount > 0),
    CONSTRAINT chk_ryczalt_payment_match_type CHECK (match_type IN ('AUTO', 'MANUAL'))
);

CREATE INDEX ix_ryczalt_payment_match_obligation
    ON investory.ryczalt_payment_match(profile_id, obligation_id, created_at);

CREATE INDEX ix_ryczalt_payment_match_transaction
    ON investory.ryczalt_payment_match(profile_id, transaction_id, created_at);

-- Squashed source: app/src/main/resources/sql/migration/V01.029__ryczalt_transaction_updated_at.sql
COMMENT ON COLUMN investory.ryczalt_transaction.updated_at IS
    'Last persistence update timestamp used by the canonical Ryczalt entity lifecycle.';

-- Squashed source: app/src/main/resources/sql/migration/V01.030__ryczalt_currency_enum_storage.sql
-- Squashed source: app/src/main/resources/sql/migration/V01.031__accounting_to_ryczalt_one_time_migration.sql
-- accounting_to_ryczalt_one_time_migration.sql
--
-- One-time additive migration of canonical Accounting POC facts into the native
-- Ryczalt persistence model.
--
-- Source schema: investory.accounting_poc_*
-- Target schema: investory.ryczalt_*
--
-- Deliberately NOT migrated:
--   * staging / temporary reconciliation tables
--   * source payload blobs
--   * filing / UPO state
--   * reference/golden tables
--   * accounting_calculation_snapshot (its payload/version semantics are not
--     equivalent to native ryczalt_calculation)
--   * payment matches (legacy obligations carry paid_amount/payment_date but do
--     not identify the canonical bank transaction that paid them)
--   * FX quotes (booked_net_pln is preserved; no historical FX quote is invented)
--
-- This migration is additive. Legacy tables are intentionally retained.
-- It is safe against accidental overwrite: if a native target row already owns
-- a natural key but is not the same previously migrated legacy fact, migration
-- fails instead of silently adopting/overwriting it.

-- ---------------------------------------------------------------------------
-- 0. Preflight: fail on unsupported obligation types.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    bad_types text;
BEGIN
    SELECT string_agg(DISTINCT obligation_type, ', ' ORDER BY obligation_type)
      INTO bad_types
      FROM investory.accounting_poc_obligation
     WHERE obligation_type NOT IN ('RYCZALT', 'VAT', 'ZUS');

    IF bad_types IS NOT NULL THEN
        RAISE EXCEPTION
            'Accounting -> Ryczalt migration: unsupported obligation type(s): %',
            bad_types;
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 1. Preflight: native-key conflicts must either be absent or already carry the
--    exact legacy provenance that this migration would create.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    conflicts bigint;
BEGIN
    SELECT count(*)
      INTO conflicts
      FROM investory.accounting_poc_invoice src
      JOIN investory.ryczalt_invoice dst
        ON dst.profile_id = src.profile_id
       AND dst.direction = 'INCOME'
       AND dst.reference = src.reference
     WHERE NOT EXISTS (
           SELECT 1
             FROM investory.ryczalt_source_reference sr
            WHERE sr.profile_id = src.profile_id
              AND sr.entity_type = 'INVOICE'
              AND sr.entity_id = dst.id
              AND sr.source = 'ACCOUNTING_POC_INVOICE'
              AND sr.external_id = src.id::text
     );

    IF conflicts > 0 THEN
        RAISE EXCEPTION
            'Accounting -> Ryczalt migration: % INCOME invoice natural-key conflict(s)',
            conflicts;
    END IF;

    SELECT count(*)
      INTO conflicts
      FROM investory.accounting_poc_expense_invoice src
      JOIN investory.ryczalt_invoice dst
        ON dst.profile_id = src.profile_id
       AND dst.direction = 'COST'
       AND dst.reference = src.reference
     WHERE NOT EXISTS (
           SELECT 1
             FROM investory.ryczalt_source_reference sr
            WHERE sr.profile_id = src.profile_id
              AND sr.entity_type = 'INVOICE'
              AND sr.entity_id = dst.id
              AND sr.source = 'ACCOUNTING_POC_EXPENSE_INVOICE'
              AND sr.external_id = src.id::text
     );

    IF conflicts > 0 THEN
        RAISE EXCEPTION
            'Accounting -> Ryczalt migration: % COST invoice natural-key conflict(s)',
            conflicts;
    END IF;

    SELECT count(*)
      INTO conflicts
      FROM investory.accounting_poc_bank_transaction src
      JOIN investory.ryczalt_transaction dst
        ON dst.profile_id = src.profile_id
       AND dst.reference = COALESCE(src.reference, 'legacy-bank-' || src.id::text)
     WHERE NOT EXISTS (
           SELECT 1
             FROM investory.ryczalt_source_reference sr
            WHERE sr.profile_id = src.profile_id
              AND sr.entity_type = 'TRANSACTION'
              AND sr.entity_id = dst.id
              AND sr.source = 'ACCOUNTING_POC_BANK_TRANSACTION'
              AND sr.external_id = src.id::text
     );

    IF conflicts > 0 THEN
        RAISE EXCEPTION
            'Accounting -> Ryczalt migration: % transaction natural-key conflict(s)',
            conflicts;
    END IF;

    SELECT count(*)
      INTO conflicts
      FROM investory.accounting_poc_obligation src
      JOIN investory.ryczalt_period p
        ON p.profile_id = src.profile_id
       AND p.period_year = EXTRACT(YEAR FROM src.tax_period)::int
       AND p.period_month = EXTRACT(MONTH FROM src.tax_period)::int
      JOIN investory.ryczalt_obligation dst
        ON dst.profile_id = src.profile_id
       AND dst.period_id = p.id
       AND dst.obligation_type = src.obligation_type
     WHERE NOT EXISTS (
           SELECT 1
             FROM investory.ryczalt_source_reference sr
            WHERE sr.profile_id = src.profile_id
              AND sr.entity_type = 'OBLIGATION'
              AND sr.entity_id = dst.id
              AND sr.source = 'ACCOUNTING_POC_OBLIGATION'
              AND sr.external_id = src.id::text
     );

    IF conflicts > 0 THEN
        RAISE EXCEPTION
            'Accounting -> Ryczalt migration: % obligation natural-key conflict(s)',
            conflicts;
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 2. Periods.
--
-- Create only periods referenced by canonical facts being migrated.
-- Legacy LOCKED maps to native FROZEN. All other/missing lifecycle states map
-- to OPEN. confirmed_at is preserved as frozen_at for LOCKED periods.
-- Existing native periods are never overwritten.
-- ---------------------------------------------------------------------------
WITH source_periods AS (
    SELECT profile_id, date_trunc('month', tax_period)::date AS tax_period
      FROM investory.accounting_poc_invoice
    UNION
    SELECT profile_id, date_trunc('month', tax_period)::date
      FROM investory.accounting_poc_expense_invoice
    UNION
    SELECT profile_id,
           date_trunc(
               'month',
               COALESCE(related_period, booking_date)
           )::date
      FROM investory.accounting_poc_bank_transaction
    UNION
    SELECT profile_id, date_trunc('month', tax_period)::date
      FROM investory.accounting_poc_obligation
),
period_data AS (
    SELECT sp.profile_id,
           EXTRACT(YEAR FROM sp.tax_period)::int AS period_year,
           EXTRACT(MONTH FROM sp.tax_period)::int AS period_month,
           CASE WHEN ps.lifecycle_status = 'LOCKED' THEN 'FROZEN' ELSE 'OPEN' END AS status,
           CASE WHEN ps.lifecycle_status = 'LOCKED' THEN ps.confirmed_at ELSE NULL END AS frozen_at
      FROM source_periods sp
      LEFT JOIN investory.accounting_poc_period_state ps
        ON ps.profile_id = sp.profile_id
       AND ps.tax_period = sp.tax_period
)
INSERT INTO investory.ryczalt_period
    (profile_id, period_year, period_month, status, frozen_at)
SELECT profile_id, period_year, period_month, status, frozen_at
  FROM period_data
ON CONFLICT (profile_id, period_year, period_month) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 3. Income invoices.
--
-- Historical booked_net_pln and ryczalt_rate are copied as facts.
-- issue_date follows the existing Java importer: tax_period when absent.
-- accounting_date intentionally equals that normalized issue_date to preserve
-- the current importer semantics.
-- ---------------------------------------------------------------------------
INSERT INTO investory.ryczalt_invoice
    (period_id,
     profile_id,
     direction,
     reference,
     issue_date,
     accounting_date,
     net_amount,
     vat_amount,
     gross_amount,
     currency,
     booked_net_pln,
     ryczalt_rate,
     deductible_vat)
SELECT p.id,
       src.profile_id,
       'INCOME',
       src.reference,
       COALESCE(src.issue_date, src.tax_period),
       COALESCE(src.issue_date, src.tax_period),
       src.net_amount,
       src.vat_amount,
       src.gross_amount,
       btrim(src.currency),
       src.booked_net_pln,
       src.ryczalt_rate,
       NULL
  FROM investory.accounting_poc_invoice src
  JOIN investory.ryczalt_period p
    ON p.profile_id = src.profile_id
   AND p.period_year = EXTRACT(YEAR FROM src.tax_period)::int
   AND p.period_month = EXTRACT(MONTH FROM src.tax_period)::int
 WHERE NOT EXISTS (
       SELECT 1
         FROM investory.ryczalt_source_reference sr
        WHERE sr.profile_id = src.profile_id
          AND sr.entity_type = 'INVOICE'
          AND sr.source = 'ACCOUNTING_POC_INVOICE'
          AND sr.external_id = src.id::text
 )
ON CONFLICT DO NOTHING;

INSERT INTO investory.ryczalt_source_reference
    (profile_id, entity_type, entity_id, source, external_id)
SELECT src.profile_id,
       'INVOICE',
       dst.id,
       'ACCOUNTING_POC_INVOICE',
       src.id::text
  FROM investory.accounting_poc_invoice src
  JOIN investory.ryczalt_invoice dst
    ON dst.profile_id = src.profile_id
   AND dst.direction = 'INCOME'
   AND dst.reference = src.reference
ON CONFLICT (profile_id, entity_type, source, external_id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 4. Cost invoices.
--
-- deductible_vat follows the existing Java importer exactly:
-- vat_amount * vat_deduction_ratio, with no new migration-time rounding.
-- ---------------------------------------------------------------------------
INSERT INTO investory.ryczalt_invoice
    (period_id,
     profile_id,
     direction,
     reference,
     issue_date,
     accounting_date,
     net_amount,
     vat_amount,
     gross_amount,
     currency,
     booked_net_pln,
     ryczalt_rate,
     deductible_vat)
SELECT p.id,
       src.profile_id,
       'COST',
       src.reference,
       COALESCE(src.invoice_date, src.tax_period),
       COALESCE(src.invoice_date, src.tax_period),
       src.net_amount,
       src.vat_amount,
       src.gross_amount,
       btrim(src.currency),
       NULL,
       NULL,
       src.vat_amount * src.vat_deduction_ratio
  FROM investory.accounting_poc_expense_invoice src
  JOIN investory.ryczalt_period p
    ON p.profile_id = src.profile_id
   AND p.period_year = EXTRACT(YEAR FROM src.tax_period)::int
   AND p.period_month = EXTRACT(MONTH FROM src.tax_period)::int
 WHERE NOT EXISTS (
       SELECT 1
         FROM investory.ryczalt_source_reference sr
        WHERE sr.profile_id = src.profile_id
          AND sr.entity_type = 'INVOICE'
          AND sr.source = 'ACCOUNTING_POC_EXPENSE_INVOICE'
          AND sr.external_id = src.id::text
 )
ON CONFLICT DO NOTHING;

INSERT INTO investory.ryczalt_source_reference
    (profile_id, entity_type, entity_id, source, external_id)
SELECT src.profile_id,
       'INVOICE',
       dst.id,
       'ACCOUNTING_POC_EXPENSE_INVOICE',
       src.id::text
  FROM investory.accounting_poc_expense_invoice src
  JOIN investory.ryczalt_invoice dst
    ON dst.profile_id = src.profile_id
   AND dst.direction = 'COST'
   AND dst.reference = src.reference
ON CONFLICT (profile_id, entity_type, source, external_id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 5. Transactions.
--
-- Preserve the existing importer identity rule:
-- reference when present, otherwise "legacy-bank-<legacy id>".
-- Provider/external transaction identity remains in the legacy schema; the
-- current native transaction table has no dedicated provider identity columns.
-- ---------------------------------------------------------------------------
INSERT INTO investory.ryczalt_transaction
    (period_id,
     profile_id,
     booking_date,
     amount,
     currency,
     reference,
     counterparty,
     description)
SELECT p.id,
       src.profile_id,
       src.booking_date,
       src.amount,
       btrim(src.currency),
       COALESCE(src.reference, 'legacy-bank-' || src.id::text),
       src.counterparty_alias,
       src.note
  FROM investory.accounting_poc_bank_transaction src
  JOIN investory.ryczalt_period p
    ON p.profile_id = src.profile_id
   AND p.period_year =
       EXTRACT(YEAR FROM COALESCE(src.related_period, date_trunc('month', src.booking_date)::date))::int
   AND p.period_month =
       EXTRACT(MONTH FROM COALESCE(src.related_period, date_trunc('month', src.booking_date)::date))::int
 WHERE NOT EXISTS (
       SELECT 1
         FROM investory.ryczalt_source_reference sr
        WHERE sr.profile_id = src.profile_id
          AND sr.entity_type = 'TRANSACTION'
          AND sr.source = 'ACCOUNTING_POC_BANK_TRANSACTION'
          AND sr.external_id = src.id::text
 )
ON CONFLICT DO NOTHING;

INSERT INTO investory.ryczalt_source_reference
    (profile_id, entity_type, entity_id, source, external_id,
     metadata)
SELECT src.profile_id,
       'TRANSACTION',
       dst.id,
       'ACCOUNTING_POC_BANK_TRANSACTION',
       src.id::text,
       jsonb_strip_nulls(
           jsonb_build_object(
               'provider', src.provider,
               'externalAccountId', src.external_account_id,
               'externalTransactionId', src.external_transaction_id,
               'sourceRowIdentity', src.source_row_identity,
               'sourcePayloadHash', src.source_payload_hash
           )
       )
  FROM investory.accounting_poc_bank_transaction src
  JOIN investory.ryczalt_transaction dst
    ON dst.profile_id = src.profile_id
   AND dst.reference = COALESCE(src.reference, 'legacy-bank-' || src.id::text)
ON CONFLICT (profile_id, entity_type, source, external_id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 6. Obligations.
--
-- Status mapping intentionally mirrors RyczaltMigrationService:
--   PAID / SETTLED   -> PAID
--   FROZEN / LOCKED  -> FROZEN
--   everything else  -> OPEN
--
-- Legacy paid_amount/payment_date are NOT converted into payment matches
-- because no authoritative obligation -> transaction identity is stored.
-- ---------------------------------------------------------------------------
INSERT INTO investory.ryczalt_obligation
    (period_id,
     profile_id,
     obligation_type,
     amount,
     currency,
     due_date,
     status)
SELECT p.id,
       src.profile_id,
       src.obligation_type,
       src.expected_amount,
       'PLN',
       src.due_date,
       CASE upper(src.status)
           WHEN 'PAID' THEN 'PAID'
           WHEN 'SETTLED' THEN 'PAID'
           WHEN 'FROZEN' THEN 'FROZEN'
           WHEN 'LOCKED' THEN 'FROZEN'
           ELSE 'OPEN'
       END
  FROM investory.accounting_poc_obligation src
  JOIN investory.ryczalt_period p
    ON p.profile_id = src.profile_id
   AND p.period_year = EXTRACT(YEAR FROM src.tax_period)::int
   AND p.period_month = EXTRACT(MONTH FROM src.tax_period)::int
 WHERE NOT EXISTS (
       SELECT 1
         FROM investory.ryczalt_source_reference sr
        WHERE sr.profile_id = src.profile_id
          AND sr.entity_type = 'OBLIGATION'
          AND sr.source = 'ACCOUNTING_POC_OBLIGATION'
          AND sr.external_id = src.id::text
 )
ON CONFLICT (profile_id, period_id, obligation_type) DO NOTHING;

INSERT INTO investory.ryczalt_source_reference
    (profile_id, entity_type, entity_id, source, external_id,
     metadata)
SELECT src.profile_id,
       'OBLIGATION',
       dst.id,
       'ACCOUNTING_POC_OBLIGATION',
       src.id::text,
       jsonb_strip_nulls(
           jsonb_build_object(
               'legacyPaidAmount', src.paid_amount,
               'legacyPaymentDate', src.payment_date,
               'legacyStatus', src.status
           )
       )
  FROM investory.accounting_poc_obligation src
  JOIN investory.ryczalt_period p
    ON p.profile_id = src.profile_id
   AND p.period_year = EXTRACT(YEAR FROM src.tax_period)::int
   AND p.period_month = EXTRACT(MONTH FROM src.tax_period)::int
  JOIN investory.ryczalt_obligation dst
    ON dst.profile_id = src.profile_id
   AND dst.period_id = p.id
   AND dst.obligation_type = src.obligation_type
ON CONFLICT (profile_id, entity_type, source, external_id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 7. Post-migration safety checks.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    expected bigint;
    actual bigint;
BEGIN
    SELECT count(*) INTO expected FROM investory.accounting_poc_invoice;
    SELECT count(*) INTO actual
      FROM investory.ryczalt_source_reference
     WHERE entity_type = 'INVOICE'
       AND source = 'ACCOUNTING_POC_INVOICE';
    IF actual <> expected THEN
        RAISE EXCEPTION
            'Accounting -> Ryczalt migration: income invoice provenance mismatch: expected %, got %',
            expected, actual;
    END IF;

    SELECT count(*) INTO expected FROM investory.accounting_poc_expense_invoice;
    SELECT count(*) INTO actual
      FROM investory.ryczalt_source_reference
     WHERE entity_type = 'INVOICE'
       AND source = 'ACCOUNTING_POC_EXPENSE_INVOICE';
    IF actual <> expected THEN
        RAISE EXCEPTION
            'Accounting -> Ryczalt migration: cost invoice provenance mismatch: expected %, got %',
            expected, actual;
    END IF;

    SELECT count(*) INTO expected FROM investory.accounting_poc_bank_transaction;
    SELECT count(*) INTO actual
      FROM investory.ryczalt_source_reference
     WHERE entity_type = 'TRANSACTION'
       AND source = 'ACCOUNTING_POC_BANK_TRANSACTION';
    IF actual <> expected THEN
        RAISE EXCEPTION
            'Accounting -> Ryczalt migration: transaction provenance mismatch: expected %, got %',
            expected, actual;
    END IF;

    SELECT count(*) INTO expected FROM investory.accounting_poc_obligation;
    SELECT count(*) INTO actual
      FROM investory.ryczalt_source_reference
     WHERE entity_type = 'OBLIGATION'
       AND source = 'ACCOUNTING_POC_OBLIGATION';
    IF actual <> expected THEN
        RAISE EXCEPTION
            'Accounting -> Ryczalt migration: obligation provenance mismatch: expected %, got %',
            expected, actual;
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 8. Verification queries.
-- These return result sets when the migration is run manually. Flyway ignores
-- them; they are useful for production dry-run/verification.
-- ---------------------------------------------------------------------------

-- Counts by source/target provenance.
SELECT 'income_invoices' AS fact,
       (SELECT count(*) FROM investory.accounting_poc_invoice) AS legacy_count,
       (SELECT count(*)
          FROM investory.ryczalt_source_reference
         WHERE entity_type = 'INVOICE'
           AND source = 'ACCOUNTING_POC_INVOICE') AS migrated_count
UNION ALL
SELECT 'cost_invoices',
       (SELECT count(*) FROM investory.accounting_poc_expense_invoice),
       (SELECT count(*)
          FROM investory.ryczalt_source_reference
         WHERE entity_type = 'INVOICE'
           AND source = 'ACCOUNTING_POC_EXPENSE_INVOICE')
UNION ALL
SELECT 'transactions',
       (SELECT count(*) FROM investory.accounting_poc_bank_transaction),
       (SELECT count(*)
          FROM investory.ryczalt_source_reference
         WHERE entity_type = 'TRANSACTION'
           AND source = 'ACCOUNTING_POC_BANK_TRANSACTION')
UNION ALL
SELECT 'obligations',
       (SELECT count(*) FROM investory.accounting_poc_obligation),
       (SELECT count(*)
          FROM investory.ryczalt_source_reference
         WHERE entity_type = 'OBLIGATION'
           AND source = 'ACCOUNTING_POC_OBLIGATION');

-- Income booked-PLN reconciliation by profile/month.
WITH legacy AS (
    SELECT profile_id,
           date_trunc('month', tax_period)::date AS period,
           count(*) AS row_count,
           sum(booked_net_pln) AS booked_net_pln
      FROM investory.accounting_poc_invoice
     GROUP BY profile_id, date_trunc('month', tax_period)::date
),
native AS (
    SELECT i.profile_id,
           make_date(p.period_year, p.period_month, 1) AS period,
           count(*) AS row_count,
           sum(i.booked_net_pln) AS booked_net_pln
      FROM investory.ryczalt_invoice i
      JOIN investory.ryczalt_period p ON p.id = i.period_id
      JOIN investory.ryczalt_source_reference sr
        ON sr.profile_id = i.profile_id
       AND sr.entity_type = 'INVOICE'
       AND sr.entity_id = i.id
       AND sr.source = 'ACCOUNTING_POC_INVOICE'
     GROUP BY i.profile_id, p.period_year, p.period_month
)
SELECT COALESCE(l.profile_id, n.profile_id) AS profile_id,
       COALESCE(l.period, n.period) AS period,
       l.row_count AS legacy_rows,
       n.row_count AS native_rows,
       l.booked_net_pln AS legacy_booked_net_pln,
       n.booked_net_pln AS native_booked_net_pln
  FROM legacy l
  FULL OUTER JOIN native n
    ON n.profile_id = l.profile_id
   AND n.period = l.period
 ORDER BY profile_id, period;

-- Obligation amount reconciliation by profile/month/type.
WITH legacy AS (
    SELECT profile_id,
           date_trunc('month', tax_period)::date AS period,
           obligation_type,
           sum(expected_amount) AS amount
      FROM investory.accounting_poc_obligation
     GROUP BY profile_id, date_trunc('month', tax_period)::date, obligation_type
),
native AS (
    SELECT o.profile_id,
           make_date(p.period_year, p.period_month, 1) AS period,
           o.obligation_type,
           sum(o.amount) AS amount
      FROM investory.ryczalt_obligation o
      JOIN investory.ryczalt_period p ON p.id = o.period_id
      JOIN investory.ryczalt_source_reference sr
        ON sr.profile_id = o.profile_id
       AND sr.entity_type = 'OBLIGATION'
       AND sr.entity_id = o.id
       AND sr.source = 'ACCOUNTING_POC_OBLIGATION'
     GROUP BY o.profile_id, p.period_year, p.period_month, o.obligation_type
)
SELECT COALESCE(l.profile_id, n.profile_id) AS profile_id,
       COALESCE(l.period, n.period) AS period,
       COALESCE(l.obligation_type, n.obligation_type) AS obligation_type,
       l.amount AS legacy_amount,
       n.amount AS native_amount
  FROM legacy l
  FULL OUTER JOIN native n
    ON n.profile_id = l.profile_id
   AND n.period = l.period
   AND n.obligation_type = l.obligation_type
 ORDER BY profile_id, period, obligation_type;

-- Squashed source: app/src/main/resources/sql/migration/V01.032__ryczalt_snapshot_calculation_certification.sql
-- Import authoritative persisted Accounting results into native Ryczalt storage.
-- These rows are historical evidence; no calculator is executed here.

DO $$
DECLARE conflicts bigint;
BEGIN
    SELECT count(*) INTO conflicts
      FROM investory.accounting_calculation_snapshot s
      JOIN investory.ryczalt_period p
        ON p.profile_id = s.profile_id
       AND p.period_year = EXTRACT(YEAR FROM s.tax_period)::int
       AND p.period_month = EXTRACT(MONTH FROM s.tax_period)::int
      JOIN LATERAL (VALUES
          ('RYCZALT', s.payload->'ryczalt'),
          ('VAT', s.payload->'vat'),
          ('ZUS', s.payload->'zus')) v(calculation_type, result_json) ON v.result_json IS NOT NULL
      JOIN investory.ryczalt_calculation c
        ON c.profile_id = s.profile_id
       AND c.period_id = p.id
       AND c.calculation_type = v.calculation_type
     WHERE c.input_fingerprint <> s.calculation_hash;
    IF conflicts > 0 THEN
        RAISE EXCEPTION
            'Accounting -> Ryczalt snapshot migration: % calculation fingerprint conflict(s)',
            conflicts;
    END IF;
END $$;

WITH snapshot_sections AS (
    SELECT s.profile_id, s.tax_period, s.calculation_hash, s.calculated_at,
           p.id AS period_id,
           CASE WHEN p.status = 'FROZEN' THEN 'FROZEN' ELSE 'CURRENT' END AS status,
           v.calculation_type, v.result_json
      FROM investory.accounting_calculation_snapshot s
      JOIN investory.ryczalt_period p
        ON p.profile_id = s.profile_id
       AND p.period_year = EXTRACT(YEAR FROM s.tax_period)::int
       AND p.period_month = EXTRACT(MONTH FROM s.tax_period)::int
      CROSS JOIN LATERAL (VALUES
          ('RYCZALT', s.payload->'ryczalt'),
          ('VAT', s.payload->'vat'),
          ('ZUS', s.payload->'zus')) v(calculation_type, result_json)
     WHERE v.result_json IS NOT NULL
)
INSERT INTO investory.ryczalt_calculation
    (period_id, profile_id, calculation_type, status, result_json, input_fingerprint,
     rule_version, calculator_version, calculated_at)
SELECT period_id, profile_id, calculation_type, status, result_json, calculation_hash,
       'LEGACY_ACCOUNTING_SNAPSHOT_V1', 'LEGACY_ACCOUNTING', calculated_at
  FROM snapshot_sections
ON CONFLICT (profile_id, period_id, calculation_type) WHERE is_current DO UPDATE SET
    status = EXCLUDED.status,
    result_json = EXCLUDED.result_json,
    input_fingerprint = EXCLUDED.input_fingerprint,
    rule_version = EXCLUDED.rule_version,
    calculator_version = EXCLUDED.calculator_version,
    calculated_at = EXCLUDED.calculated_at;

WITH snapshot_sections AS (
    SELECT s.profile_id, s.tax_period, s.calculation_hash, s.calculated_at,
           p.id AS period_id,
           CASE WHEN p.status = 'FROZEN' THEN 'FROZEN' ELSE 'OPEN' END AS status,
           v.calculation_type, v.result_json,
           CASE v.calculation_type
               WHEN 'RYCZALT' THEN (v.result_json->>'calculatedTax')::numeric
               WHEN 'VAT' THEN (v.result_json->>'calculatedVat')::numeric
               WHEN 'ZUS' THEN (v.result_json->>'totalZus')::numeric
           END AS amount
      FROM investory.accounting_calculation_snapshot s
      JOIN investory.ryczalt_period p
        ON p.profile_id = s.profile_id
       AND p.period_year = EXTRACT(YEAR FROM s.tax_period)::int
       AND p.period_month = EXTRACT(MONTH FROM s.tax_period)::int
      CROSS JOIN LATERAL (VALUES
          ('RYCZALT', s.payload->'ryczalt'),
          ('VAT', s.payload->'vat'),
          ('ZUS', s.payload->'zus')) v(calculation_type, result_json)
     WHERE v.result_json IS NOT NULL
)
INSERT INTO investory.ryczalt_obligation
    (period_id, profile_id, obligation_type, amount, currency, due_date, status, calculation_id)
SELECT x.period_id, x.profile_id, x.calculation_type, x.amount, 'PLN', NULL, x.status, c.id
  FROM snapshot_sections x
  JOIN investory.ryczalt_calculation c
    ON c.profile_id = x.profile_id
   AND c.period_id = x.period_id
   AND c.calculation_type = x.calculation_type
ON CONFLICT (profile_id, period_id, obligation_type) DO UPDATE SET
    amount = EXCLUDED.amount,
    currency = EXCLUDED.currency,
    status = EXCLUDED.status,
    calculation_id = EXCLUDED.calculation_id;

INSERT INTO investory.ryczalt_source_reference
    (profile_id, entity_type, entity_id, source, external_id)
SELECT c.profile_id, 'CALCULATION', c.id, 'ACCOUNTING_CALCULATION_SNAPSHOT',
       to_char(s.tax_period, 'YYYY-MM-DD') || '|' || c.calculation_type
  FROM investory.accounting_calculation_snapshot s
  JOIN investory.ryczalt_period p
    ON p.profile_id = s.profile_id
   AND p.period_year = EXTRACT(YEAR FROM s.tax_period)::int
   AND p.period_month = EXTRACT(MONTH FROM s.tax_period)::int
  JOIN investory.ryczalt_calculation c
    ON c.profile_id = s.profile_id AND c.period_id = p.id
  CROSS JOIN LATERAL (VALUES
      ('RYCZALT', s.payload->'ryczalt'),
      ('VAT', s.payload->'vat'),
      ('ZUS', s.payload->'zus')) v(calculation_type, result_json)
 WHERE c.calculation_type = v.calculation_type AND v.result_json IS NOT NULL
ON CONFLICT (profile_id, entity_type, source, external_id) DO NOTHING;

INSERT INTO investory.ryczalt_source_reference
    (profile_id, entity_type, entity_id, source, external_id)
SELECT o.profile_id, 'OBLIGATION', o.id, 'ACCOUNTING_CALCULATION_SNAPSHOT',
       to_char(s.tax_period, 'YYYY-MM-DD') || '|' || o.obligation_type
  FROM investory.accounting_calculation_snapshot s
  JOIN investory.ryczalt_period p
    ON p.profile_id = s.profile_id
   AND p.period_year = EXTRACT(YEAR FROM s.tax_period)::int
   AND p.period_month = EXTRACT(MONTH FROM s.tax_period)::int
  JOIN investory.ryczalt_obligation o
    ON o.profile_id = s.profile_id AND o.period_id = p.id
  CROSS JOIN LATERAL (VALUES
      ('RYCZALT', s.payload->'ryczalt'),
      ('VAT', s.payload->'vat'),
      ('ZUS', s.payload->'zus')) v(obligation_type, result_json)
 WHERE o.obligation_type = v.obligation_type AND v.result_json IS NOT NULL
ON CONFLICT (profile_id, entity_type, source, external_id) DO NOTHING;


-- ------------------------
-- ============================================================================
-- HISTORICAL CALCULATIONS + OBLIGATIONS
-- accounting_calculation_snapshot -> ryczalt_calculation / ryczalt_obligation
-- ============================================================================


-- ============================================================================
-- 1. CALCULATIONS
-- ============================================================================

WITH source_calculations AS (

    -- RYCZALT
    SELECT
        p.id AS period_id,
        s.profile_id,
        'RYCZALT'::varchar AS calculation_type,
        s.payload -> 'ryczalt' AS result_json,
        s.calculation_hash AS input_fingerprint,
        s.schema_version,
        s.calculated_at,
        p.status AS period_status
    FROM investory.accounting_calculation_snapshot s
             JOIN investory.ryczalt_period p
                  ON p.profile_id = s.profile_id
                      AND p.period_year = EXTRACT(YEAR FROM s.tax_period)::int
                      AND p.period_month = EXTRACT(MONTH FROM s.tax_period)::int
    WHERE s.payload #>> '{ryczalt,calculatedTax}' IS NOT NULL

    UNION ALL

    -- VAT
    SELECT
        p.id,
        s.profile_id,
        'VAT',
        s.payload -> 'vat',
        s.calculation_hash,
        s.schema_version,
        s.calculated_at,
        p.status
    FROM investory.accounting_calculation_snapshot s
             JOIN investory.ryczalt_period p
                  ON p.profile_id = s.profile_id
                      AND p.period_year = EXTRACT(YEAR FROM s.tax_period)::int
                      AND p.period_month = EXTRACT(MONTH FROM s.tax_period)::int
    WHERE s.payload #>> '{vat,calculatedVat}' IS NOT NULL

    UNION ALL

    -- ZUS
    SELECT
        p.id,
        s.profile_id,
        'ZUS',
        s.payload -> 'zus',
        s.calculation_hash,
        s.schema_version,
        s.calculated_at,
        p.status
    FROM investory.accounting_calculation_snapshot s
             JOIN investory.ryczalt_period p
                  ON p.profile_id = s.profile_id
                      AND p.period_year = EXTRACT(YEAR FROM s.tax_period)::int
                      AND p.period_month = EXTRACT(MONTH FROM s.tax_period)::int
    WHERE s.payload #>> '{zus,totalZus}' IS NOT NULL
)

INSERT INTO investory.ryczalt_calculation (
    period_id,
    profile_id,
    calculation_type,
    status,
    result_json,
    input_fingerprint,
    rule_version,
    calculator_version,
    calculated_at
)
SELECT
    src.period_id,
    src.profile_id,
    src.calculation_type,

    CASE
        WHEN src.period_status = 'FROZEN' THEN 'FROZEN'
        ELSE 'CURRENT'
        END,

    src.result_json,
    src.input_fingerprint,

    'LEGACY_ACCOUNTING_SNAPSHOT_V' || src.schema_version,
    'LEGACY_ACCOUNTING',
    src.calculated_at

FROM source_calculations src

WHERE NOT EXISTS (
    SELECT 1
    FROM investory.ryczalt_calculation existing
    WHERE existing.profile_id = src.profile_id
      AND existing.period_id = src.period_id
      AND existing.calculation_type = src.calculation_type
);


-- ============================================================================
-- 2. VERIFY CALCULATIONS
-- ============================================================================

SELECT
    calculation_type,
    count(*) AS count
FROM investory.ryczalt_calculation
GROUP BY calculation_type
ORDER BY calculation_type;

-- ============================================================================
-- 3. OBLIGATIONS
-- ============================================================================

WITH source_obligations AS (

    -- RYCZALT
    SELECT
        p.id AS period_id,
        s.profile_id,
        'RYCZALT'::varchar AS obligation_type,
        (s.payload #>> '{ryczalt,calculatedTax}')::numeric AS amount,
        s.calculated_at,
        p.status AS period_status
    FROM investory.accounting_calculation_snapshot s
             JOIN investory.ryczalt_period p
                  ON p.profile_id = s.profile_id
                      AND p.period_year = EXTRACT(YEAR FROM s.tax_period)::int
                      AND p.period_month = EXTRACT(MONTH FROM s.tax_period)::int
    WHERE s.payload #>> '{ryczalt,calculatedTax}' IS NOT NULL

    UNION ALL

    -- VAT
    SELECT
        p.id,
        s.profile_id,
        'VAT',
        (s.payload #>> '{vat,calculatedVat}')::numeric,
        s.calculated_at,
        p.status
    FROM investory.accounting_calculation_snapshot s
             JOIN investory.ryczalt_period p
                  ON p.profile_id = s.profile_id
                      AND p.period_year = EXTRACT(YEAR FROM s.tax_period)::int
                      AND p.period_month = EXTRACT(MONTH FROM s.tax_period)::int
    WHERE s.payload #>> '{vat,calculatedVat}' IS NOT NULL

    UNION ALL

    -- ZUS
    SELECT
        p.id,
        s.profile_id,
        'ZUS',
        (s.payload #>> '{zus,totalZus}')::numeric,
        s.calculated_at,
        p.status
    FROM investory.accounting_calculation_snapshot s
             JOIN investory.ryczalt_period p
                  ON p.profile_id = s.profile_id
                      AND p.period_year = EXTRACT(YEAR FROM s.tax_period)::int
                      AND p.period_month = EXTRACT(MONTH FROM s.tax_period)::int
    WHERE s.payload #>> '{zus,totalZus}' IS NOT NULL
)

INSERT INTO investory.ryczalt_obligation (
    period_id,
    profile_id,
    obligation_type,
    amount,
    currency,
    due_date,
    status,
    calculation_id,
    created_at,
    updated_at
)
SELECT
    src.period_id,
    src.profile_id,
    src.obligation_type,
    src.amount,
    'PLN',
    NULL,

    CASE
        WHEN src.period_status = 'FROZEN' THEN 'FROZEN'
        ELSE 'OPEN'
        END,

    calc.id,
    src.calculated_at,
    src.calculated_at

FROM source_obligations src

         JOIN investory.ryczalt_calculation calc
              ON calc.profile_id = src.profile_id
                  AND calc.period_id = src.period_id
                  AND calc.calculation_type = src.obligation_type

WHERE NOT EXISTS (
    SELECT 1
    FROM investory.ryczalt_obligation existing
    WHERE existing.profile_id = src.profile_id
      AND existing.period_id = src.period_id
      AND existing.obligation_type = src.obligation_type
);

-- Squashed source: app/src/main/resources/sql/migration/V01.033__ryczalt_transaction_source_reference_identity.sql
-- Native Ryczalt bank acquisition uses ryczalt_source_reference as the identity boundary;
-- the canonical table already contains the non-unique lookup index.

-- Squashed source: app/src/main/resources/sql/migration/V01.034__ryczalt_counterparties_and_invoice_approval.sql
CREATE TABLE investory.ryczalt_counterparty (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    tax_identifier VARCHAR(64),
    country VARCHAR(2) NOT NULL,
    legal_name VARCHAR(512) NOT NULL,
    alias VARCHAR(256),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ryczalt_counterparty_profile_id UNIQUE (profile_id, id)
);
CREATE UNIQUE INDEX uq_ryczalt_counterparty_tax ON investory.ryczalt_counterparty(profile_id, tax_identifier, country) WHERE tax_identifier IS NOT NULL;
CREATE INDEX ix_ryczalt_counterparty_profile ON investory.ryczalt_counterparty(profile_id, legal_name);

CREATE TABLE investory.ryczalt_counterparty_rule (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    counterparty_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    source_type VARCHAR(32), document_type VARCHAR(64), service_key VARCHAR(256),
    classification VARCHAR(64), vat_treatment VARCHAR(64),
    vat_deduction_ratio NUMERIC(7,4), ryczalt_rate NUMERIC(7,4),
    auto_approve BOOLEAN NOT NULL DEFAULT FALSE,
    payment_verification_policy VARCHAR(16) NOT NULL DEFAULT 'REQUIRED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_ryczalt_rule_payment_policy CHECK (payment_verification_policy IN ('REQUIRED','NOT_REQUIRED')),
    CONSTRAINT fk_ryczalt_rule_profile_counterparty
        FOREIGN KEY (profile_id, counterparty_id)
        REFERENCES investory.ryczalt_counterparty (profile_id, id)
);
CREATE INDEX ix_ryczalt_rule_counterparty ON investory.ryczalt_counterparty_rule(profile_id, counterparty_id, name);

ALTER TABLE investory.ryczalt_invoice
    ADD CONSTRAINT fk_ryczalt_invoice_profile_counterparty
        FOREIGN KEY (profile_id, counterparty_id)
        REFERENCES investory.ryczalt_counterparty (profile_id, id);

-- Squashed source: app/src/main/resources/sql/migration/V01.035__ryczalt_invoice_candidates.sql
CREATE TABLE investory.ryczalt_invoice_candidate (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    candidate_key UUID NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_external_id VARCHAR(256) NOT NULL,
    document_type VARCHAR(64) NOT NULL,
    direction VARCHAR(8) NOT NULL CHECK (direction IN ('INCOME', 'COST')),
    issue_date DATE NOT NULL,
    sale_date DATE,
    due_date DATE,
    reference VARCHAR(128) NOT NULL,
    seller_legal_name VARCHAR(512),
    seller_tax_identifier VARCHAR(64),
    seller_country VARCHAR(2),
    buyer_legal_name VARCHAR(512),
    buyer_tax_identifier VARCHAR(64),
    buyer_country VARCHAR(2),
    currency VARCHAR(3) NOT NULL,
    net_amount NUMERIC(19,4) NOT NULL,
    vat_amount NUMERIC(19,4) NOT NULL,
    gross_amount NUMERIC(19,4) NOT NULL,
    counterparty_id BIGINT,
    source_metadata JSONB,
    confidence NUMERIC(7,6),
    service_key VARCHAR(128),
    classification VARCHAR(128),
    vat_treatment VARCHAR(128),
    vat_deduction_ratio NUMERIC(7,6),
    ryczalt_rate NUMERIC(7,4),
    approval_status VARCHAR(16) NOT NULL DEFAULT 'NEEDS_REVIEW',
    approval_source VARCHAR(32),
    payment_verification_policy VARCHAR(16) NOT NULL DEFAULT 'REQUIRED',
    required_inputs JSONB NOT NULL DEFAULT '[]'::jsonb,
    duplicate BOOLEAN NOT NULL DEFAULT FALSE,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    rule_match_status VARCHAR(16) NOT NULL DEFAULT 'NO_MATCH',
    period_year INTEGER NOT NULL,
    period_month INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ryczalt_candidate_key UNIQUE (profile_id, candidate_key),
    CONSTRAINT uq_ryczalt_candidate_source UNIQUE (profile_id, source_type, source_external_id),
    CONSTRAINT chk_ryczalt_candidate_rule_match_status
        CHECK (rule_match_status IN ('MATCHED', 'NO_MATCH', 'AMBIGUOUS')),
    CONSTRAINT fk_ryczalt_candidate_profile_counterparty
        FOREIGN KEY (profile_id, counterparty_id)
        REFERENCES investory.ryczalt_counterparty (profile_id, id)
);

CREATE INDEX ix_ryczalt_candidate_profile_period
    ON investory.ryczalt_invoice_candidate(profile_id, period_year, period_month);

CREATE OR REPLACE FUNCTION investory.touch_ryczalt_invoice_candidate_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ryczalt_invoice_candidate_touch_updated_at
BEFORE UPDATE ON investory.ryczalt_invoice_candidate
FOR EACH ROW EXECUTE FUNCTION investory.touch_ryczalt_invoice_candidate_updated_at();

-- Squashed source: app/src/main/resources/sql/migration/V01.036__ryczalt_invoice_decisions.sql

-- Squashed source: app/src/main/resources/sql/migration/V01.037__ryczalt_profile_owned_counterparties.sql
-- Squashed source: app/src/main/resources/sql/migration/V01.038__ryczalt_invoice_candidate_integrity.sql
-- Squashed source: app/src/main/resources/sql/migration/V01.039__ryczalt_candidate_rule_state.sql
-- Squashed source: app/src/main/resources/sql/migration/V01.040__ryczalt_counterparty_country_varchar.sql
-- Squashed source: app/src/main/resources/sql/migration/V01.041__ryczalt_invoice_source_identity.sql
