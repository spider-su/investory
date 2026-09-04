SET search_path TO investory, public;

-- Preserve the Flyway history of V01.001 while correcting historical cross-rate
-- provenance for databases that have already applied the original function.
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
    SELECT max(config_value::integer) FILTER (WHERE config_key = 'max_age_days') AS max_age,
           max(config_value::date) FILTER (WHERE config_key = 'daily_history_start') AS daily_start
    FROM investory.fx_configuration
), source_edges AS MATERIALIZED (
    SELECT er.base::varchar(3) AS edge_source, er.to_currency::varchar(3) AS edge_target,
           er.rate AS edge_rate, er.source::varchar(32) AS edge_rate_source,
           er.method::varchar(32) AS edge_method, er.rate_date,
            CASE WHEN er.rate_date = p_valuation_date AND er.method = 'MARKET_DAILY' THEN 1
                WHEN er.rate_date = p_valuation_date AND er.method = 'IBKR_DAILY_REFERENCE' THEN 2
                -- A direct observed rate for the exact pair must outrank triangulation (rank 6),
                -- even when older than max_age. Staleness is reported via conversion_status, not
                -- by demoting the rate below a triangulated value derived from ancient legs.
                WHEN er.method IN ('MARKET_DAILY','IBKR_DAILY_REFERENCE') THEN 5
                WHEN er.method = 'HISTORICAL_MONTHLY' THEN 9 ELSE 9 END AS rank
    FROM investory.exchange_rates er CROSS JOIN cfg
    WHERE er.base <> er.to_currency AND er.rate > 0
      AND er.rate_date <= p_valuation_date
      AND er.method NOT IN ('XTB_EXECUTION','IBKR_EXECUTION','INTERPOLATED')
), edges AS (
    SELECT edge_source, edge_target, edge_rate, edge_rate_source, edge_method, rate_date, rank
    FROM source_edges
    UNION ALL
    SELECT edge_target, edge_source, 1 / edge_rate, edge_rate_source, edge_method, rate_date, rank
    FROM source_edges
), candidates AS (
    SELECT edge_rate AS rate, ('DIRECT:' || edge_rate_source)::varchar(64) AS chosen_source,
           edge_method AS chosen_method, edge_rate_source AS chosen_rate_source,
           rate_date AS chosen_date, rank AS chosen_rank
    FROM edges WHERE edge_source = p_source_currency AND edge_target = p_target_currency
    UNION ALL
    SELECT a.edge_rate * b.edge_rate, ('TRIANGULATED:' || a.edge_target)::varchar(64),
           CASE WHEN a.edge_method = b.edge_method THEN a.edge_method ELSE 'TRIANGULATED' END,
           a.edge_rate_source, LEAST(a.rate_date, b.rate_date), 6
    FROM edges a JOIN edges b ON b.edge_source = a.edge_target
                              AND b.edge_target = p_target_currency
    WHERE a.edge_source = p_source_currency
      AND a.edge_target NOT IN (p_source_currency, p_target_currency)
), historical_source_edges AS MATERIALIZED (
    SELECT er.base::varchar(3) AS edge_source,
           er.to_currency::varchar(3) AS edge_target,
           er.rate AS edge_rate,
           er.source::varchar(32) AS edge_rate_source,
           er.method::varchar(32) AS edge_method,
           er.rate_date,
           false AS is_inverse
    FROM investory.exchange_rates er
    WHERE er.method = 'HISTORICAL_MONTHLY' AND er.rate > 0
), historical_edges AS (
    SELECT edge_source, edge_target, edge_rate, edge_rate_source, edge_method, rate_date, false AS is_inverse
    FROM historical_source_edges
    UNION ALL
    SELECT edge_target, edge_source, 1 / edge_rate, edge_rate_source, edge_method, rate_date, true AS is_inverse
    FROM historical_source_edges
), historical AS (
    SELECT lower.edge_rate + (upper.edge_rate - lower.edge_rate)
                 * ((p_valuation_date - lower.rate_date)::numeric
                 / (upper.rate_date - lower.rate_date)::numeric) AS rate,
           ('INTERPOLATED:' || lower.edge_rate_source)::varchar(64) AS chosen_source,
           'INTERPOLATED'::varchar(32) AS chosen_method,
           lower.edge_rate_source::varchar(32) AS chosen_rate_source,
           NULL::date AS chosen_date, 7 AS chosen_rank
    FROM cfg
    CROSS JOIN (
      SELECT DISTINCT edge_rate_source, edge_method
      FROM historical_edges
      WHERE edge_source = p_source_currency
        AND edge_target = p_target_currency
    ) method_group
    CROSS JOIN LATERAL (
      SELECT h.* FROM historical_edges h
      WHERE h.edge_source = p_source_currency
        AND h.edge_target = p_target_currency
        AND h.edge_rate_source = method_group.edge_rate_source
        AND h.edge_method = method_group.edge_method
        AND h.rate_date < p_valuation_date
      ORDER BY h.rate_date DESC
      LIMIT 1) lower
    CROSS JOIN LATERAL (
      SELECT h.* FROM historical_edges h
      WHERE h.edge_source = p_source_currency
        AND h.edge_target = p_target_currency
        AND h.edge_rate_source = lower.edge_rate_source
        AND h.edge_method = lower.edge_method
        AND h.rate_date > p_valuation_date
      ORDER BY h.rate_date
      LIMIT 1) upper
    WHERE p_valuation_date < cfg.daily_start
), selected AS (
    SELECT * FROM candidates
    UNION ALL SELECT * FROM historical
    ORDER BY chosen_rank, chosen_date DESC NULLS LAST, chosen_source
    LIMIT 1
)
SELECT p_source_currency, p_target_currency,
       CASE WHEN p_source_currency = p_target_currency THEN 1 ELSE selected.rate END,
       CASE WHEN p_source_currency = p_target_currency THEN 'SAME_CURRENCY' ELSE selected.chosen_source END,
       CASE WHEN p_source_currency = p_target_currency THEN 'SAME_CURRENCY'
            WHEN selected.chosen_method IN ('MARKET_DAILY','IBKR_DAILY_REFERENCE')
                 AND selected.chosen_date < p_valuation_date
                 THEN 'CARRY_FORWARD'
            ELSE selected.chosen_method END,
       CASE WHEN p_source_currency = p_target_currency THEN 'SAME_CURRENCY' ELSE selected.chosen_rate_source END,
       CASE WHEN p_source_currency = p_target_currency THEN p_valuation_date
            ELSE selected.chosen_date END,
       CASE WHEN p_source_currency = p_target_currency THEN 0
            WHEN selected.chosen_date IS NULL THEN NULL
            ELSE (p_valuation_date - selected.chosen_date)::integer END,
       CASE WHEN p_source_currency = p_target_currency THEN 'SAME_CURRENCY'
           WHEN selected.rate IS NULL THEN 'MISSING_RATE'
           WHEN selected.chosen_method = 'INTERPOLATED' THEN 'ESTIMATED'
           WHEN selected.chosen_method = 'HISTORICAL_MONTHLY'
                AND p_valuation_date >= cfg.daily_start THEN 'STALE'
           WHEN selected.chosen_method = 'HISTORICAL_MONTHLY'
                 AND selected.chosen_rate_source <> 'TEST' THEN 'ESTIMATED'
            WHEN p_valuation_date - selected.chosen_date > cfg.max_age THEN 'STALE'
            ELSE 'OK' END
FROM cfg LEFT JOIN selected ON true;
$$;
