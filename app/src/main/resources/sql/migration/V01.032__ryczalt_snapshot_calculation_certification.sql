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
