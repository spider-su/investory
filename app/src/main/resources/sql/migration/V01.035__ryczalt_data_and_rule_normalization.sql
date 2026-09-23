-- Consolidated, repeatable Ryczalt rule seeding and historical data normalization.

UPDATE investory.ryczalt_invoice
SET classification = 'OFFICE_SERVICE'
WHERE direction = 'COST' AND classification = 'ACCOUNTING_SERVICE';

UPDATE investory.ryczalt_invoice
SET classification = 'OFFICE_COST'
WHERE direction = 'COST' AND classification = 'EQUIPMENT';

UPDATE investory.ryczalt_invoice
SET classification = 'VEHICLE'
WHERE direction = 'COST' AND classification IN ('VEHICLE_FUEL', 'VEHICLE_LEASING');

UPDATE investory.ryczalt_invoice
SET classification = 'EU_SERVICE'
WHERE direction = 'INCOME' AND classification = 'BUSINESS_SERVICE' AND vat_treatment = 'EU_SERVICE';

UPDATE investory.ryczalt_invoice
SET classification = 'PL_SERVICE'
WHERE direction = 'INCOME' AND classification = 'BUSINESS_SERVICE' AND vat_treatment IS NULL;

UPDATE investory.ryczalt_invoice_candidate
SET classification = 'VEHICLE'
WHERE direction = 'COST' AND classification IN ('VEHICLE_FUEL', 'VEHICLE_LEASING');

UPDATE investory.ryczalt_counterparty_rule
SET classification = 'VEHICLE'
WHERE classification IN ('VEHICLE_FUEL', 'VEHICLE_LEASING');

UPDATE investory.ryczalt_invoice
SET ryczalt_rate = NULL
WHERE direction = 'COST' OR classification NOT IN ('PL_SERVICE', 'EU_SERVICE');

UPDATE investory.ryczalt_invoice_candidate
SET ryczalt_rate = NULL
WHERE direction = 'COST' OR classification NOT IN ('PL_SERVICE', 'EU_SERVICE');

UPDATE investory.ryczalt_counterparty_rule
SET ryczalt_rate = NULL
WHERE document_type <> 'SALES_INVOICE'
   OR classification NOT IN ('PL_SERVICE', 'EU_SERVICE');

UPDATE investory.ryczalt_invoice i
SET classification = CASE
        WHEN cp.alias = 'SPRIBE' OR (cp.alias IS NULL AND cp.legal_name = 'SPRIBE') THEN 'EU_SERVICE'
        ELSE 'PL_SERVICE'
    END,
    ryczalt_rate = 12.0000
FROM investory.ryczalt_counterparty cp
WHERE cp.id = i.counterparty_id
  AND i.direction = 'INCOME'
  AND i.classification IS NULL
  AND COALESCE(cp.alias, cp.legal_name) IN ('Emagine', 'Coforge', 'SPRIBE');

-- Seed deterministic KSeF rules; NULL VAT treatments are explicitly text typed.
WITH desired(counterparty_name, classification, vat_treatment, vat_ratio, rate) AS (
    VALUES
        ('SPRIBE', 'EU_SERVICE', 'EU_SERVICE', NULL::numeric, 12.0000::numeric),
        ('Action S.A.', 'OFFICE_COST', NULL::varchar, 1.0000::numeric, NULL::numeric),
        ('mLeasing', 'VEHICLE', NULL::varchar, 0.5000::numeric, NULL::numeric),
        ('TERG S.A.', 'OFFICE_COST', NULL::varchar, 1.0000::numeric, NULL::numeric),
        ('Solidna Ksiegowa', 'OFFICE_SERVICE', NULL::varchar, NULL::numeric, NULL::numeric),
        ('DHL', 'EU_SERVICE', 'EU_SERVICE', NULL::numeric, 12.0000::numeric),
        ('NET POINT', 'OFFICE_COST', NULL::varchar, 1.0000::numeric, NULL::numeric),
        ('Sonepar', 'PL_SERVICE', NULL::varchar, NULL::numeric, 12.0000::numeric),
        ('Coforge', 'PL_SERVICE', NULL::varchar, NULL::numeric, 12.0000::numeric)
)
INSERT INTO investory.ryczalt_counterparty_rule (
    profile_id, counterparty_id, name, source_type, document_type, service_key,
    classification, vat_treatment, vat_deduction_ratio, ryczalt_rate,
    auto_approve, payment_verification_policy
)
SELECT cp.profile_id, cp.id,
       'KSeF current data - ' || desired.counterparty_name,
       'KSEF', NULL, NULL, desired.classification, desired.vat_treatment,
       desired.vat_ratio, desired.rate, TRUE, 'NOT_REQUIRED'
FROM desired
JOIN investory.ryczalt_counterparty cp
  ON COALESCE(cp.alias, cp.legal_name) = desired.counterparty_name
WHERE NOT EXISTS (
    SELECT 1
    FROM investory.ryczalt_counterparty_rule existing
    WHERE existing.profile_id = cp.profile_id
      AND existing.counterparty_id = cp.id
      AND existing.source_type = 'KSEF'
      AND existing.service_key IS NULL
      AND existing.classification = desired.classification
);

INSERT INTO investory.ryczalt_counterparty_rule (
    profile_id, counterparty_id, name, source_type, document_type, service_key,
    classification, vat_treatment, vat_deduction_ratio, ryczalt_rate,
    auto_approve, payment_verification_policy
)
SELECT cp.profile_id, cp.id, 'KSeF current data - Emagine', 'KSEF', NULL, NULL,
       'PL_SERVICE', NULL, NULL, 12.0000, TRUE, 'NOT_REQUIRED'
FROM investory.ryczalt_counterparty cp
WHERE COALESCE(cp.alias, cp.legal_name) = 'Emagine'
  AND NOT EXISTS (
      SELECT 1
      FROM investory.ryczalt_counterparty_rule existing
      WHERE existing.profile_id = cp.profile_id
        AND existing.counterparty_id = cp.id
        AND existing.source_type = 'KSEF'
        AND existing.service_key IS NULL
        AND existing.classification = 'PL_SERVICE'
  );

-- Copy immutable comparison obligations into Ryczalt-owned persistence.
INSERT INTO investory.ryczalt_obligation_reference (
    id, profile_id, tax_period, obligation_type, due_date,
    expected_amount, paid_amount, payment_date, status, note
)
SELECT id, profile_id, tax_period, obligation_type, due_date,
       expected_amount, paid_amount, payment_date, status, note
FROM investory.accounting_reference_obligation
ON CONFLICT (id) DO NOTHING;

UPDATE investory.ryczalt_invoice
SET ryczalt_rate = ryczalt_rate * 100
WHERE direction = 'INCOME' AND ryczalt_rate > 0 AND ryczalt_rate < 1;

UPDATE investory.ryczalt_invoice_candidate
SET ryczalt_rate = ryczalt_rate * 100
WHERE direction = 'INCOME' AND ryczalt_rate > 0 AND ryczalt_rate < 1;

UPDATE investory.ryczalt_invoice
SET vat_treatment = 'EU_SERVICE'
WHERE direction = 'INCOME' AND classification = 'EU_SERVICE';

UPDATE investory.ryczalt_invoice
SET vat_treatment = 'DOMESTIC_VAT'
WHERE direction = 'INCOME' AND classification = 'PL_SERVICE';

UPDATE investory.ryczalt_invoice_candidate
SET vat_treatment = 'EU_SERVICE'
WHERE direction = 'INCOME' AND classification = 'EU_SERVICE';

UPDATE investory.ryczalt_invoice_candidate
SET vat_treatment = 'DOMESTIC_VAT'
WHERE direction = 'INCOME' AND classification = 'PL_SERVICE';

UPDATE investory.ryczalt_counterparty_rule
SET vat_treatment = 'EU_SERVICE'
WHERE classification = 'EU_SERVICE';

UPDATE investory.ryczalt_counterparty_rule
SET vat_treatment = 'DOMESTIC_VAT'
WHERE classification = 'PL_SERVICE';

UPDATE investory.ryczalt_invoice i
SET classification = CASE
        WHEN COALESCE(cp.alias, cp.legal_name) = 'BP' THEN 'VEHICLE'
        WHEN COALESCE(cp.alias, cp.legal_name) IN ('TERG S.A.', 'X-COM', 'NET POINT') THEN 'OFFICE_COST'
        ELSE 'OFFICE_SERVICE'
    END,
    ryczalt_rate = NULL
FROM investory.ryczalt_counterparty cp
WHERE cp.id = i.counterparty_id
  AND i.direction = 'COST'
  AND i.classification IS NULL
  AND COALESCE(cp.alias, cp.legal_name) IN (
      'BP', 'Solidna Ksiegowa', 'Emagine', 'Nowa Era', 'TERG S.A.', 'X-COM', 'NET POINT'
  );

-- Store ryczalt rates as fractions (0.1200 means 12%).
UPDATE investory.ryczalt_invoice
SET ryczalt_rate = ryczalt_rate / 100
WHERE direction = 'INCOME' AND ryczalt_rate > 1;

UPDATE investory.ryczalt_invoice_candidate
SET ryczalt_rate = ryczalt_rate / 100
WHERE direction = 'INCOME' AND ryczalt_rate > 1;

UPDATE investory.ryczalt_counterparty_rule
SET ryczalt_rate = ryczalt_rate / 100
WHERE classification IN ('PL_SERVICE', 'EU_SERVICE') AND ryczalt_rate > 1;

-- Derive September 2026 input from August when it does not yet exist.
INSERT INTO investory.ryczalt_native_month_input (
    profile_id, tax_year, tax_month, jdg_active, qualifying_uop, zus_regime,
    voluntary_sickness, ytd_ryczalt_revenue, full_jdg_social,
    social_contribution_deduction, health_contribution_override,
    deductions_already_consumed, sales_corrections, explicit_vat_adjustments
)
SELECT previous.profile_id, 2026, 9, previous.jdg_active, previous.qualifying_uop,
       previous.zus_regime, previous.voluntary_sickness,
       COALESCE((
           SELECT SUM(invoice.booked_net_pln)
           FROM investory.ryczalt_invoice invoice
           WHERE invoice.profile_id = previous.profile_id
             AND invoice.direction = 'INCOME'
             AND invoice.approval_status = 'APPROVED'
             AND invoice.accounting_date >= DATE '2026-01-01'
             AND invoice.accounting_date < DATE '2026-10-01'
       ), 0),
       previous.full_jdg_social, previous.social_contribution_deduction,
       previous.health_contribution_override, previous.deductions_already_consumed,
       previous.sales_corrections, previous.explicit_vat_adjustments
FROM investory.ryczalt_native_month_input previous
WHERE previous.profile_id = 1 AND previous.tax_year = 2026 AND previous.tax_month = 8
ON CONFLICT (profile_id, tax_year, tax_month) DO NOTHING;

-- Correct deterministic period allocation from explicit bank payment descriptions.
DELETE FROM investory.ryczalt_payment_match match_row
USING investory.ryczalt_obligation obligation,
      investory.ryczalt_period period,
      investory.ryczalt_transaction transaction_row
WHERE match_row.obligation_id = obligation.id
  AND obligation.period_id = period.id
  AND match_row.transaction_id = transaction_row.id
  AND period.profile_id = 1
  AND period.period_year = 2025
  AND period.period_month = 12
  AND obligation.obligation_type = 'VAT'
  AND transaction_row.description LIKE '%/OKR/25M11/SFP/VAT-7/%';

UPDATE investory.ryczalt_payment_match match_row
SET matched_amount = 6334.0000
FROM investory.ryczalt_obligation obligation,
     investory.ryczalt_period period,
     investory.ryczalt_transaction transaction_row
WHERE match_row.obligation_id = obligation.id
  AND obligation.period_id = period.id
  AND match_row.transaction_id = transaction_row.id
  AND period.profile_id = 1
  AND period.period_year = 2025
  AND period.period_month = 11
  AND obligation.obligation_type = 'VAT'
  AND transaction_row.description LIKE '%/OKR/25M11/SFP/VAT-7/%';

UPDATE investory.ryczalt_payment_match match_row
SET obligation_id = may_obligation.id
FROM investory.ryczalt_transaction transaction_row,
     investory.ryczalt_obligation may_obligation,
     investory.ryczalt_period may_period
WHERE match_row.transaction_id = transaction_row.id
  AND may_obligation.id = match_row.obligation_id
  AND may_period.id = may_obligation.period_id
  AND may_period.profile_id = 1
  AND may_period.period_year = 2026
  AND may_period.period_month = 5
  AND may_obligation.obligation_type = 'RYCZALT'
  AND transaction_row.amount = -740.0000
  AND transaction_row.description LIKE '%/OKR/26M05/SFP/PPE%';

-- Exclude the specifically reviewed private-rent transaction from tax matching.
DELETE FROM investory.ryczalt_payment_match match_row
USING investory.ryczalt_transaction transaction_row
WHERE match_row.transaction_id = transaction_row.id
  AND transaction_row.id = 357;

UPDATE investory.ryczalt_transaction
SET excluded_from_payment_matching = TRUE
WHERE id = 357 AND profile_id = 1;

-- Recurring 740 PLN PPE-labelled transfers were confirmed as private rent.
DELETE FROM investory.ryczalt_payment_match match_row
USING investory.ryczalt_transaction transaction_row
WHERE match_row.transaction_id = transaction_row.id
  AND transaction_row.profile_id = 1
  AND transaction_row.amount = -740.0000
  AND transaction_row.description ILIKE '%PPE%';

UPDATE investory.ryczalt_transaction
SET excluded_from_payment_matching = TRUE
WHERE profile_id = 1
  AND amount = -740.0000
  AND description ILIKE '%PPE%';

-- accounting_reference_month is the source for expected comparison amounts.
WITH source AS (
    SELECT profile_id, tax_period, 'RYCZALT'::VARCHAR(32) AS obligation_type, ryczalt AS expected_amount
    FROM investory.accounting_reference_month WHERE ryczalt IS NOT NULL
    UNION ALL
    SELECT profile_id, tax_period, 'VAT'::VARCHAR(32), vat_payable
    FROM investory.accounting_reference_month WHERE vat_payable IS NOT NULL
    UNION ALL
    SELECT profile_id, tax_period, 'ZUS'::VARCHAR(32), zus
    FROM investory.accounting_reference_month WHERE zus IS NOT NULL
)
UPDATE investory.ryczalt_obligation_reference target
SET expected_amount = source.expected_amount
FROM source
WHERE target.profile_id = source.profile_id
  AND target.tax_period = source.tax_period
  AND target.obligation_type = source.obligation_type;

WITH source AS (
    SELECT profile_id, tax_period, 'RYCZALT'::VARCHAR(32) AS obligation_type, ryczalt AS expected_amount
    FROM investory.accounting_reference_month WHERE ryczalt IS NOT NULL
    UNION ALL
    SELECT profile_id, tax_period, 'VAT'::VARCHAR(32), vat_payable
    FROM investory.accounting_reference_month WHERE vat_payable IS NOT NULL
    UNION ALL
    SELECT profile_id, tax_period, 'ZUS'::VARCHAR(32), zus
    FROM investory.accounting_reference_month WHERE zus IS NOT NULL
), missing AS (
    SELECT source.*
    FROM source
    WHERE NOT EXISTS (
        SELECT 1 FROM investory.ryczalt_obligation_reference target
        WHERE target.profile_id = source.profile_id
          AND target.tax_period = source.tax_period
          AND target.obligation_type = source.obligation_type
    )
), numbered AS (
    SELECT missing.*, ROW_NUMBER() OVER (ORDER BY profile_id, tax_period, obligation_type) AS row_number
    FROM missing
), max_id AS (
    SELECT COALESCE(MAX(id), 0) AS value FROM investory.ryczalt_obligation_reference
)
INSERT INTO investory.ryczalt_obligation_reference (
    id, profile_id, tax_period, obligation_type, expected_amount, status, note
)
SELECT max_id.value + numbered.row_number, numbered.profile_id, numbered.tax_period,
       numbered.obligation_type, numbered.expected_amount, 'UNPAID',
       'Copied from accounting_reference_month'
FROM numbered CROSS JOIN max_id;

-- Authoritative August 2026 statement supplied by the accountant.
UPDATE investory.accounting_reference_month
SET ryczalt = 7044.00, vat_payable = 5935.00, zus = 1495.04
WHERE profile_id = 1 AND tax_period = DATE '2026-08-01';

UPDATE investory.ryczalt_obligation_reference target
SET expected_amount = source.expected_amount
FROM (
    SELECT profile_id, tax_period, 'RYCZALT'::VARCHAR(32) AS obligation_type, ryczalt AS expected_amount
    FROM investory.accounting_reference_month
    WHERE profile_id = 1 AND tax_period = DATE '2026-08-01'
    UNION ALL
    SELECT profile_id, tax_period, 'VAT'::VARCHAR(32), vat_payable
    FROM investory.accounting_reference_month
    WHERE profile_id = 1 AND tax_period = DATE '2026-08-01'
    UNION ALL
    SELECT profile_id, tax_period, 'ZUS'::VARCHAR(32), zus
    FROM investory.accounting_reference_month
    WHERE profile_id = 1 AND tax_period = DATE '2026-08-01'
) source
WHERE target.profile_id = source.profile_id
  AND target.tax_period = source.tax_period
  AND target.obligation_type = source.obligation_type;
