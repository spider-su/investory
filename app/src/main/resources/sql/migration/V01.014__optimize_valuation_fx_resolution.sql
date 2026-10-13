-- Same-currency valuations are identity conversions; avoid rate scans.
-- Bound mixed-currency lookups to the latest eligible direct and inverse observations.
-- Preserve resolver outputs, date precedence, and the direct-rate tie break.
-- Seek the latest observation for each currency pair instead of scanning rate history.
-- The existing unique valuation index continues to enforce one observation per pair/date.
CREATE INDEX ix_exchange_rates_valuation_pair_date
    ON investory.exchange_rates (base, to_currency, rate_date DESC)
    INCLUDE (rate, source, method, source_rate_date)
    WHERE purpose = 'VALUATION';

-- Only the latest direct and inverse observations can win the existing date/priority order.

CREATE OR REPLACE FUNCTION investory.resolve_fx_rate(p_valuation_date date, p_source_currency character varying, p_target_currency character varying) RETURNS TABLE(source_currency character varying, target_currency character varying, fx_rate_to_target numeric, source character varying, rate_method character varying, rate_source character varying, source_rate_date date, age_days integer, conversion_status character varying)
    LANGUAGE sql STABLE
    AS $$
WITH candidates AS (
    (SELECT er.rate, er.source, er.method,
           COALESCE(er.source_rate_date, er.rate_date) AS source_rate_date,
           er.rate_date, 0 AS direction_priority
    FROM investory.exchange_rates er
    WHERE p_source_currency IS DISTINCT FROM p_target_currency
      AND er.purpose = 'VALUATION'
      AND er.base = p_source_currency
      AND er.to_currency = p_target_currency
      AND er.rate_date <= p_valuation_date
    ORDER BY er.rate_date DESC
    LIMIT 1)
    UNION ALL
    (SELECT 1 / er.rate, er.source, er.method,
           COALESCE(er.source_rate_date, er.rate_date), er.rate_date, 1
    FROM investory.exchange_rates er
    WHERE p_source_currency IS DISTINCT FROM p_target_currency
      AND er.purpose = 'VALUATION'
      AND er.base = p_target_currency
      AND er.to_currency = p_source_currency
      AND er.rate_date <= p_valuation_date
    ORDER BY er.rate_date DESC
    LIMIT 1)
), selected AS (
    SELECT * FROM candidates
    ORDER BY rate_date DESC, direction_priority
    LIMIT 1
)
SELECT p_source_currency, p_target_currency,
       CASE WHEN p_source_currency = p_target_currency THEN 1 ELSE selected.rate END,
       CASE WHEN p_source_currency = p_target_currency THEN 'SAME_CURRENCY' ELSE selected.source END,
       CASE WHEN p_source_currency = p_target_currency THEN 'SAME_CURRENCY'
            WHEN selected.rate_date < p_valuation_date THEN 'CARRY_FORWARD'
            ELSE selected.method END,
       CASE WHEN p_source_currency = p_target_currency THEN 'SAME_CURRENCY' ELSE selected.source END,
       CASE WHEN p_source_currency = p_target_currency THEN p_valuation_date
            ELSE selected.source_rate_date END,
       CASE WHEN p_source_currency = p_target_currency THEN 0
            WHEN selected.source_rate_date IS NULL THEN NULL
            ELSE (p_valuation_date - selected.source_rate_date)::integer END,
       CASE WHEN p_source_currency = p_target_currency THEN 'SAME_CURRENCY'
            WHEN selected.rate IS NULL THEN 'MISSING_RATE'
            WHEN selected.rate_date < p_valuation_date THEN 'CARRY_FORWARD'
            WHEN selected.method = 'INTERPOLATED' THEN 'ESTIMATED'
            ELSE 'OK' END
FROM (SELECT 1) sentinel
LEFT JOIN selected ON true;
$$;
