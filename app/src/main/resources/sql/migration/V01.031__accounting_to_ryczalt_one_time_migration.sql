-- V01.031__accounting_to_ryczalt_one_time_migration.sql
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
ON CONFLICT (profile_id, direction, reference) DO NOTHING;

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
ON CONFLICT (profile_id, direction, reference) DO NOTHING;

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
ON CONFLICT (profile_id, reference) DO NOTHING;

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
