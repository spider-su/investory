SET search_path TO investory, public;

-- Some databases migrated through V01.006 retain the legacy resolver body on
-- the public function OID. Keep both OIDs, but make both use canonical daily FX.
CREATE OR REPLACE FUNCTION investory.resolve_fx_rate(
    p_valuation_date date,
    p_source_currency varchar(3),
    p_target_currency varchar(3)
) RETURNS TABLE (
    source_currency varchar(3), target_currency varchar(3), fx_rate_to_target numeric,
    source varchar(64), rate_method varchar(32), rate_source varchar(32),
    source_rate_date date, age_days integer, conversion_status varchar(32)
) LANGUAGE sql STABLE AS $$
WITH cfg AS (
    SELECT max(config_value::integer) FILTER (WHERE config_key = 'max_age_days') AS max_age
    FROM investory.fx_configuration
), candidates AS (
    SELECT rate, source, method, source_rate_date, rate_date, 1 AS priority
    FROM investory.fx_daily_rates
    WHERE base = p_source_currency AND to_currency = p_target_currency
      AND rate_date <= p_valuation_date
      AND rate_date >= p_valuation_date - (SELECT max_age FROM cfg)
    UNION ALL
    SELECT 1 / rate, source, method, source_rate_date, rate_date, 1
    FROM investory.fx_daily_rates
    WHERE base = p_target_currency AND to_currency = p_source_currency
      AND rate_date <= p_valuation_date
      AND rate_date >= p_valuation_date - (SELECT max_age FROM cfg)
), selected AS (
    SELECT c.*
    FROM candidates c
    ORDER BY priority, rate_date DESC
    LIMIT 1
)
SELECT p_source_currency,
       p_target_currency,
       CASE WHEN p_source_currency = p_target_currency THEN 1 ELSE selected.rate END,
       CASE WHEN p_source_currency = p_target_currency THEN 'SAME_CURRENCY' ELSE selected.source END,
       CASE WHEN p_source_currency = p_target_currency THEN 'SAME_CURRENCY'
            WHEN selected.method = 'CARRY_FORWARD'
                 OR selected.rate_date < p_valuation_date THEN 'CARRY_FORWARD'
            ELSE selected.method END,
       CASE WHEN p_source_currency = p_target_currency THEN 'SAME_CURRENCY' ELSE selected.source END,
       CASE WHEN p_source_currency = p_target_currency THEN p_valuation_date ELSE selected.source_rate_date END,
       CASE WHEN p_source_currency = p_target_currency THEN 0
            WHEN selected.source_rate_date IS NULL THEN NULL
            ELSE (p_valuation_date - selected.source_rate_date)::integer END,
       CASE WHEN p_source_currency = p_target_currency THEN 'SAME_CURRENCY'
            WHEN selected.rate IS NULL THEN 'MISSING_RATE'
            WHEN selected.method = 'CARRY_FORWARD'
                 OR selected.rate_date < p_valuation_date THEN 'CARRY_FORWARD'
            WHEN selected.method = 'OBSERVED' THEN 'OK'
            WHEN selected.method = 'INTERPOLATED' THEN 'ESTIMATED'
            ELSE 'OK' END
FROM (SELECT 1) sentinel
LEFT JOIN selected ON true;
$$;

CREATE OR REPLACE FUNCTION investory.resolve_fx_rate_compat_oid(
    p_valuation_date date,
    p_source_currency varchar(3),
    p_target_currency varchar(3)
) RETURNS TABLE (
    source_currency varchar(3), target_currency varchar(3), fx_rate_to_target numeric,
    source varchar(64), rate_method varchar(32), rate_source varchar(32),
    source_rate_date date, age_days integer, conversion_status varchar(32)
) LANGUAGE sql STABLE AS $$
    SELECT * FROM investory.resolve_fx_rate(
        p_valuation_date, p_source_currency, p_target_currency)
$$;
