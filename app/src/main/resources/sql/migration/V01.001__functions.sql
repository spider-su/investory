SET search_path TO investory, public;

CREATE OR REPLACE FUNCTION investory.bind_asset_price_history_source_mapping()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    resolved_mapping_id bigint;
BEGIN
    SELECT ass.id
    INTO resolved_mapping_id
    FROM investory.asset_source_symbols ass
    WHERE ass.asset_id = NEW.asset_id
      AND ass.source = NEW.source
      AND upper(ass.source_symbol) = upper(NEW.source_symbol);

    IF resolved_mapping_id IS NOT NULL THEN
        IF NEW.source_mapping_id IS NULL THEN
            NEW.source_mapping_id := resolved_mapping_id;
        ELSIF NEW.source_mapping_id <> resolved_mapping_id THEN
            RAISE EXCEPTION 'asset price source mapping % does not match asset %, source %, symbol %',
                NEW.source_mapping_id, NEW.asset_id, NEW.source, NEW.source_symbol;
        END IF;
    ELSIF NEW.source_mapping_id IS NOT NULL AND NOT EXISTS (
        SELECT 1
        FROM investory.asset_source_symbols ass
        WHERE ass.id = NEW.source_mapping_id
          AND ass.asset_id = NEW.asset_id
          AND ass.source = NEW.source
          AND upper(ass.source_symbol) = upper(NEW.source_symbol)
    ) THEN
        RAISE EXCEPTION 'asset price source mapping % does not match asset %, source %, symbol %',
            NEW.source_mapping_id, NEW.asset_id, NEW.source, NEW.source_symbol;
    END IF;

    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION investory.signed_position_quantity(
    operation investory.positions_operation_type,
    volume numeric
) RETURNS numeric
LANGUAGE sql
IMMUTABLE
RETURNS NULL ON NULL INPUT
AS $$
    SELECT CASE WHEN operation = 'SELL' THEN -ABS(volume) ELSE ABS(volume) END
$$;

COMMENT ON FUNCTION investory.signed_position_quantity(investory.positions_operation_type, numeric) IS
    'Canonical position quantity: BUY is positive and SELL is negative while stored positions.volume remains non-negative.';

-- Canonical FX usability contract. Authoritative monetary conversion treats
-- OK, ESTIMATED, and SAME_CURRENCY as usable; STALE, MISSING_RATE, and
-- MISSING_CURRENCY are not usable. Defined once here and reused everywhere so
-- the usable-status list is never duplicated or patched after the fact.
CREATE OR REPLACE FUNCTION investory.fx_status_usable(p_status varchar)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT p_status IN ('OK', 'ESTIMATED', 'SAME_CURRENCY')
$$;

COMMENT ON FUNCTION investory.fx_status_usable(varchar) IS
    'Central FX usability contract. ESTIMATED is usable and retains its provenance; STALE, MISSING_RATE, and MISSING_CURRENCY are not usable.';

CREATE OR REPLACE FUNCTION investory.reconciliation_parameter(p_parameter_name varchar(96))
RETURNS numeric
LANGUAGE sql
STABLE
AS $$
    SELECT numeric_value
    FROM investory.reconciliation_parameters
    WHERE parameter_name = p_parameter_name
$$;

CREATE OR REPLACE FUNCTION investory.reconciliation_effective_tolerance(
    p_expected numeric,
    p_actual numeric
) RETURNS numeric
LANGUAGE sql
STABLE
AS $$
    SELECT CASE
        WHEN p_expected IS NULL OR p_actual IS NULL THEN NULL::numeric
        ELSE GREATEST(
            investory.reconciliation_parameter('reconciliation_absolute_tolerance'),
            investory.reconciliation_parameter('reconciliation_relative_tolerance')
                * GREATEST(ABS(p_expected), ABS(p_actual))
        )
    END
$$;

CREATE OR REPLACE FUNCTION investory.reconciliation_values_match(
    p_expected numeric,
    p_actual numeric
) RETURNS boolean
LANGUAGE sql
STABLE
AS $$
    SELECT p_expected IS NOT NULL
       AND p_actual IS NOT NULL
       AND ABS(p_actual - p_expected)
           <= investory.reconciliation_effective_tolerance(p_expected, p_actual)
$$;

CREATE OR REPLACE FUNCTION investory.reconciliation_display_value(p_value numeric)
RETURNS numeric
LANGUAGE sql
STABLE
AS $$
    SELECT CASE
        WHEN p_value IS NULL THEN NULL::numeric
        ELSE ROUND(
            p_value,
            investory.reconciliation_parameter('reconciliation_reporting_scale')::integer
        )
    END
$$;

COMMENT ON FUNCTION investory.reconciliation_effective_tolerance(numeric, numeric) IS
    'Shared numeric rule: max(absolute tolerance, relative tolerance * max(abs(expected), abs(actual))).';

COMMENT ON FUNCTION investory.reconciliation_values_match(numeric, numeric) IS
    'Full-precision reconciliation comparison. NULL values never match.';

COMMENT ON FUNCTION investory.reconciliation_display_value(numeric) IS
    'Rounds a reconciliation value for presentation only. It is never used for status decisions.';

-- Canonical FX contract:
--   amount_in_target_currency = amount_in_source_currency * fx_rate_to_target
-- Stored exchange_rates follow the same mathematical direction: base -> to_currency.
-- Daily FX starts at the deployment boundary. Older gaps may be reconstructed, but
-- reconstructed values never become observed source rows.
CREATE OR REPLACE FUNCTION investory.resolve_fx_rate(
    p_valuation_date date,
    p_source_currency varchar(3),
    p_target_currency varchar(3)
) RETURNS TABLE (
    source_currency varchar(3),
    target_currency varchar(3),
    fx_rate_to_target numeric,
    source varchar(64),
    rate_method varchar(32),
    rate_source varchar(32),
    source_rate_date date,
    age_days integer,
    conversion_status varchar(32)
)
LANGUAGE sql
STABLE
AS $$
WITH edges AS (
    SELECT DISTINCT ON (edge_source, edge_target)
        edge_source,
        edge_target,
        edge_rate,
        edge_source_name,
        edge_method,
        edge_rate_source,
        rate_date
    FROM (
        SELECT
            er.base::varchar(3) AS edge_source,
            er.to_currency::varchar(3) AS edge_target,
            er.rate AS edge_rate,
            ('DIRECT:' || er.source)::varchar(64) AS edge_source_name,
            er.method AS edge_method,
            er.source AS edge_rate_source,
            er.rate_date AS rate_date,
            1 AS direction_priority,
            er.imported_at
        FROM investory.exchange_rates er
        WHERE er.rate_date <= p_valuation_date
          AND er.rate > 0
        UNION ALL
        SELECT
            er.to_currency::varchar(3) AS edge_source,
            er.base::varchar(3) AS edge_target,
            1::numeric / er.rate AS edge_rate,
            ('INVERSE:' || er.source)::varchar(64) AS edge_source_name,
            er.method AS edge_method,
            er.source AS edge_rate_source,
            er.rate_date AS rate_date,
            2 AS direction_priority,
            er.imported_at
        FROM investory.exchange_rates er
        WHERE er.rate_date <= p_valuation_date
          AND er.rate > 0
    ) available_edges
    ORDER BY edge_source, edge_target,
        CASE edge_method
            WHEN 'MARKET_DAILY' THEN 1
            WHEN 'IBKR_DAILY_REFERENCE' THEN 2
            WHEN 'HISTORICAL_MONTHLY' THEN 3
            ELSE 4
        END,
        rate_date DESC, direction_priority, imported_at DESC
), candidates AS (
    SELECT
        e.edge_rate AS candidate_rate,
        e.edge_source_name AS candidate_source,
        e.edge_method AS candidate_method,
        e.edge_rate_source AS candidate_rate_source,
        e.rate_date AS candidate_rate_date,
        1 AS candidate_priority
    FROM edges e
    WHERE e.edge_source = p_source_currency
      AND e.edge_target = p_target_currency
    UNION ALL
    SELECT
        first_leg.edge_rate * second_leg.edge_rate,
        ('TRIANGULATED:' || first_leg.edge_target)::varchar(64),
        CASE WHEN first_leg.edge_method = second_leg.edge_method THEN first_leg.edge_method ELSE 'TRIANGULATED' END,
        first_leg.edge_rate_source,
        LEAST(first_leg.rate_date, second_leg.rate_date),
        2
    FROM edges first_leg
    JOIN edges second_leg
      ON second_leg.edge_source = first_leg.edge_target
     AND second_leg.edge_target = p_target_currency
    WHERE first_leg.edge_source = p_source_currency
      AND first_leg.edge_target NOT IN (p_source_currency, p_target_currency)
    UNION ALL
    -- Deterministic historical interpolation: bracket strictly by the nearest
    -- observation before and the nearest observation after the valuation date,
    -- within one source/method series. No arbitrary lower/upper pair.
    SELECT
        lo.rate + (up.rate - lo.rate)
            * ((p_valuation_date - lo.rate_date)::numeric
            / (up.rate_date - lo.rate_date)::numeric),
        'INTERPOLATED'::varchar(64),
        'INTERPOLATED'::varchar(32),
        lo.source::varchar(32),
        p_valuation_date,
        1
    FROM (
        SELECT er.rate, er.rate_date, er.source
        FROM investory.exchange_rates er
        WHERE er.base = p_source_currency
          AND er.to_currency = p_target_currency
          AND er.method = 'HISTORICAL_MONTHLY'
          AND er.rate > 0
          AND er.rate_date < p_valuation_date
        ORDER BY er.rate_date DESC, er.imported_at DESC
        LIMIT 1
    ) lo
    JOIN LATERAL (
        SELECT er.rate, er.rate_date
        FROM investory.exchange_rates er
        WHERE er.base = p_source_currency
          AND er.to_currency = p_target_currency
          AND er.method = 'HISTORICAL_MONTHLY'
          AND er.source = lo.source
          AND er.rate > 0
          AND er.rate_date > p_valuation_date
        ORDER BY er.rate_date ASC, er.imported_at DESC
        LIMIT 1
    ) up ON true
    WHERE p_valuation_date < (SELECT config_value::date FROM investory.fx_configuration WHERE config_key = 'daily_history_start')
    UNION ALL
    -- Inverse direction uses the identical nearest-bracket rule, then inverts.
    SELECT
        1::numeric / (lo.rate + (up.rate - lo.rate)
            * ((p_valuation_date - lo.rate_date)::numeric
            / (up.rate_date - lo.rate_date)::numeric)),
        'INTERPOLATED'::varchar(64),
        'INTERPOLATED'::varchar(32),
        lo.source::varchar(32),
        p_valuation_date,
        1
    FROM (
        SELECT er.rate, er.rate_date, er.source
        FROM investory.exchange_rates er
        WHERE er.base = p_target_currency
          AND er.to_currency = p_source_currency
          AND er.method = 'HISTORICAL_MONTHLY'
          AND er.rate > 0
          AND er.rate_date < p_valuation_date
        ORDER BY er.rate_date DESC, er.imported_at DESC
        LIMIT 1
    ) lo
    JOIN LATERAL (
        SELECT er.rate, er.rate_date
        FROM investory.exchange_rates er
        WHERE er.base = p_target_currency
          AND er.to_currency = p_source_currency
          AND er.method = 'HISTORICAL_MONTHLY'
          AND er.source = lo.source
          AND er.rate > 0
          AND er.rate_date > p_valuation_date
        ORDER BY er.rate_date ASC, er.imported_at DESC
        LIMIT 1
    ) up ON true
    WHERE p_valuation_date < (SELECT config_value::date FROM investory.fx_configuration WHERE config_key = 'daily_history_start')
), selected AS (
    SELECT *
    FROM candidates
    ORDER BY candidate_priority, candidate_rate_date DESC, candidate_source
    LIMIT 1
)
SELECT
    p_source_currency::varchar(3),
    p_target_currency::varchar(3),
    CASE
        WHEN p_source_currency = p_target_currency THEN 1::numeric
        ELSE selected.candidate_rate
    END,
    CASE
        WHEN p_source_currency = p_target_currency THEN 'SAME_CURRENCY'::varchar(64)
        ELSE selected.candidate_source
    END,
    CASE
        WHEN p_source_currency = p_target_currency THEN 'SAME_CURRENCY'::varchar(32)
        WHEN selected.candidate_method IN ('MARKET_DAILY', 'IBKR_DAILY_REFERENCE')
             AND selected.candidate_rate_date < p_valuation_date
             AND EXTRACT(ISODOW FROM p_valuation_date) IN (6, 7)
          THEN 'CARRY_FORWARD'::varchar(32)
        ELSE selected.candidate_method
    END,
    CASE
        WHEN p_source_currency = p_target_currency THEN 'SAME_CURRENCY'::varchar(32)
        ELSE selected.candidate_rate_source
    END,
    CASE
        WHEN p_source_currency = p_target_currency THEN p_valuation_date
        WHEN selected.candidate_method = 'INTERPOLATED' THEN NULL
        ELSE selected.candidate_rate_date
    END,
    CASE
        WHEN p_source_currency = p_target_currency THEN 0
        WHEN selected.candidate_method = 'INTERPOLATED' THEN NULL
        WHEN selected.candidate_rate_date IS NULL THEN NULL
        ELSE (p_valuation_date - selected.candidate_rate_date)::integer
    END,
    CASE
        WHEN p_source_currency IS NULL OR p_target_currency IS NULL THEN 'MISSING_CURRENCY'
        WHEN p_source_currency = p_target_currency THEN 'SAME_CURRENCY'
        WHEN selected.candidate_rate IS NULL THEN 'MISSING_RATE'
        WHEN selected.candidate_method = 'INTERPOLATED' THEN 'ESTIMATED'
        WHEN selected.candidate_method = 'HISTORICAL_MONTHLY'
             AND selected.candidate_rate_source IN ('NBP', 'STATIC_BOOTSTRAP')
             AND p_valuation_date < (SELECT config_value::date FROM investory.fx_configuration WHERE config_key = 'daily_history_start')
             AND p_valuation_date - selected.candidate_rate_date > (
                 SELECT config_value::integer
                 FROM investory.fx_configuration
                 WHERE config_key = 'max_age_days'
             ) THEN 'ESTIMATED'
        WHEN p_valuation_date - selected.candidate_rate_date > (
            SELECT config_value::integer
            FROM investory.fx_configuration
            WHERE config_key = 'max_age_days'
        ) THEN 'STALE'
        ELSE 'OK'
    END::varchar(32)
FROM (SELECT 1) anchor
LEFT JOIN selected ON true
$$;

CREATE OR REPLACE FUNCTION investory.fx_daily_coverage_supported(p_start_date date)
RETURNS boolean
LANGUAGE sql
STABLE
AS $$
    SELECT NOT EXISTS (
        SELECT 1
        FROM investory.currencies c
        WHERE NOT EXISTS (
            SELECT 1
            FROM investory.exchange_rates er
            WHERE er.rate_date = p_start_date
              AND er.method IN ('MARKET_DAILY', 'IBKR_DAILY_REFERENCE')
              AND er.rate > 0
              AND (er.base = c.id OR er.to_currency = c.id)
        )
    );
$$;

CREATE OR REPLACE FUNCTION investory.validate_daily_history_start()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.config_key = 'daily_history_start'
       AND NEW.config_value::date < DATE '9999-12-31'
       AND NOT investory.fx_daily_coverage_supported(NEW.config_value::date) THEN
        RAISE EXCEPTION 'daily_history_start % precedes supported neutral daily FX coverage', NEW.config_value;
    END IF;
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION investory.fx_daily_coverage_supported(date) IS
    'True only when every configured currency participates in neutral daily/reference FX on the proposed boundary date.';

COMMENT ON FUNCTION investory.resolve_fx_rate(date, varchar, varchar) IS
    'Canonical valuation FX resolver. Market daily and IBKR reference rates outrank broker execution rates. Historical gaps are explicitly estimated.';

CREATE OR REPLACE FUNCTION investory.resolve_fx_rate(
    p_valuation_date date,
    p_source_currency varchar(3),
    p_target_currency varchar(3),
    p_purpose varchar(16)
) RETURNS TABLE (
    source_currency varchar(3),
    target_currency varchar(3),
    fx_rate_to_target numeric,
    source varchar(64),
    rate_method varchar(32),
    rate_source varchar(32),
    source_rate_date date,
    age_days integer,
    conversion_status varchar(32)
)
LANGUAGE sql
STABLE
AS $$
WITH execution_candidates AS (
    SELECT er.base::varchar(3) AS source_currency,
           er.to_currency::varchar(3) AS target_currency,
           er.rate AS fx_rate_to_target,
           er.method::varchar(32) AS rate_method,
           er.source::varchar(32) AS rate_source,
           er.rate_date AS source_rate_date,
           er.observed_at,
           1 AS direction_priority
    FROM investory.exchange_rates er
    WHERE er.rate_date = p_valuation_date
      AND er.method IN ('XTB_EXECUTION', 'IBKR_EXECUTION')
      AND er.base = p_source_currency
      AND er.to_currency = p_target_currency
    UNION ALL
    SELECT er.to_currency::varchar(3), er.base::varchar(3), 1 / er.rate,
           er.method::varchar(32), er.source::varchar(32), er.rate_date,
           er.observed_at, 2
    FROM investory.exchange_rates er
    WHERE er.rate_date = p_valuation_date
      AND er.method IN ('XTB_EXECUTION', 'IBKR_EXECUTION')
      AND er.base = p_target_currency
      AND er.to_currency = p_source_currency
), selected_execution AS (
    SELECT * FROM execution_candidates
    WHERE source_currency = p_source_currency
      AND target_currency = p_target_currency
    ORDER BY observed_at DESC NULLS LAST, direction_priority
    LIMIT 1
)
SELECT r.*
FROM investory.resolve_fx_rate(p_valuation_date, p_source_currency, p_target_currency) r
WHERE upper(p_purpose) = 'VALUATION'
UNION ALL
SELECT p_source_currency, p_target_currency, 1, 'SAME_CURRENCY', 'SAME_CURRENCY', 'SAME_CURRENCY', p_valuation_date, 0, 'SAME_CURRENCY'
WHERE upper(p_purpose) = 'TRANSACTION' AND p_source_currency = p_target_currency
UNION ALL
SELECT e.source_currency, e.target_currency, e.fx_rate_to_target,
       ('EXECUTION:' || e.rate_source)::varchar(64), e.rate_method, e.rate_source,
       e.source_rate_date, 0, 'OK'
FROM selected_execution e
WHERE upper(p_purpose) = 'TRANSACTION'
UNION ALL
SELECT p_source_currency, p_target_currency, NULL, 'MISSING', NULL, NULL, NULL, NULL, 'MISSING_RATE'
WHERE upper(p_purpose) = 'TRANSACTION'
  AND p_source_currency <> p_target_currency
  AND NOT EXISTS (SELECT 1 FROM selected_execution);
$$;

CREATE OR REPLACE FUNCTION investory.resolve_portfolio_fx_rate(
    p_portfolio_id bigint,
    p_valuation_date date,
    p_source_currency varchar(3)
) RETURNS TABLE (
    portfolio_id bigint,
    valuation_date date,
    source_currency varchar(3),
    base_currency varchar(3),
    fx_rate_to_base numeric,
    source varchar(64),
    rate_method varchar(32),
    rate_source varchar(32),
    source_rate_date date,
    age_days integer,
    conversion_status varchar(32)
)
LANGUAGE sql
STABLE
AS $$
SELECT
    p.id,
    p_valuation_date,
    resolved.source_currency,
    p.base_currency::varchar(3),
    resolved.fx_rate_to_target,
    resolved.source,
    resolved.rate_method,
    resolved.rate_source,
    resolved.source_rate_date,
    resolved.age_days,
    resolved.conversion_status
FROM investory.portfolios p
CROSS JOIN LATERAL investory.resolve_fx_rate(
    p_valuation_date,
    p_source_currency,
    p.base_currency::varchar(3)
) resolved
WHERE p.id = p_portfolio_id
$$;

COMMENT ON FUNCTION investory.resolve_portfolio_fx_rate(bigint, date, varchar) IS
    'Portfolio-aware canonical FX resolver. Portfolio base currency is derived from portfolios.id; callers cannot supply it.';

CREATE OR REPLACE FUNCTION investory.repair_position_trade_currency()
RETURNS integer
LANGUAGE plpgsql
AS $$
DECLARE
    repaired_count integer;
BEGIN
    -- Baseline imports must provide explicit currency metadata. No inference or repair is allowed.
    repaired_count := 0;
    RETURN repaired_count;
END;
$$;

CREATE OR REPLACE FUNCTION investory.refresh_reporting_views()
RETURNS VOID AS $$
BEGIN
    REFRESH MATERIALIZED VIEW investory.account_monthly_mv;
    REFRESH MATERIALIZED VIEW investory.portfolio_monthly_mv;
    REFRESH MATERIALIZED VIEW investory.account_statistics;
    REFRESH MATERIALIZED VIEW investory.portfolio_currency_breakdown;
    REFRESH MATERIALIZED VIEW investory.portfolio_asset_allocation;
    REFRESH MATERIALIZED VIEW investory.symbol_performance;
    REFRESH MATERIALIZED VIEW investory.portfolio_kpi_summary;
END;
$$ LANGUAGE plpgsql;

-- Final canonical valuation resolver. Java calls this function directly; no Java-side
-- candidate selection is allowed. Daily/reference freshness outranks stale observations.
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
), edges AS (
    SELECT er.base::varchar(3) AS edge_source, er.to_currency::varchar(3) AS edge_target,
           er.rate AS edge_rate, er.source::varchar(32) AS edge_rate_source,
           er.method::varchar(32) AS edge_method, er.rate_date,
           CASE WHEN er.rate_date = p_valuation_date AND er.method = 'MARKET_DAILY' THEN 1
                WHEN er.rate_date = p_valuation_date AND er.method = 'IBKR_DAILY_REFERENCE' THEN 2
                WHEN er.method IN ('MARKET_DAILY','IBKR_DAILY_REFERENCE')
                     AND p_valuation_date - er.rate_date <= cfg.max_age THEN 5
                WHEN er.method = 'HISTORICAL_MONTHLY' THEN 9 ELSE 9 END AS rank
    FROM investory.exchange_rates er CROSS JOIN cfg
    WHERE er.base <> er.to_currency AND er.rate > 0
      AND er.rate_date <= p_valuation_date
      AND er.method NOT IN ('XTB_EXECUTION','IBKR_EXECUTION','INTERPOLATED')
    UNION ALL
    SELECT er.to_currency, er.base, 1 / er.rate, er.source, er.method, er.rate_date,
           CASE WHEN er.rate_date = p_valuation_date AND er.method = 'MARKET_DAILY' THEN 1
                WHEN er.rate_date = p_valuation_date AND er.method = 'IBKR_DAILY_REFERENCE' THEN 2
                WHEN er.method IN ('MARKET_DAILY','IBKR_DAILY_REFERENCE')
                     AND p_valuation_date - er.rate_date <= cfg.max_age THEN 5
                WHEN er.method = 'HISTORICAL_MONTHLY' THEN 9 ELSE 9 END
    FROM investory.exchange_rates er CROSS JOIN cfg
    WHERE er.base <> er.to_currency AND er.rate > 0
      AND er.rate_date <= p_valuation_date
      AND er.method NOT IN ('XTB_EXECUTION','IBKR_EXECUTION','INTERPOLATED')
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
), historical_edges AS (
    SELECT er.base::varchar(3) AS edge_source,
           er.to_currency::varchar(3) AS edge_target,
           er.rate AS edge_rate,
           er.source::varchar(32) AS edge_rate_source,
           er.method::varchar(32) AS edge_method,
           er.rate_date,
           false AS is_inverse
    FROM investory.exchange_rates er
    WHERE er.method = 'HISTORICAL_MONTHLY' AND er.rate > 0
    UNION ALL
    SELECT er.to_currency::varchar(3),
           er.base::varchar(3),
           1 / er.rate,
           er.source::varchar(32),
           er.method::varchar(32),
           er.rate_date,
           true AS is_inverse
    FROM investory.exchange_rates er
    WHERE er.method = 'HISTORICAL_MONTHLY' AND er.rate > 0
), historical AS (
    SELECT CASE
             WHEN lower.is_inverse THEN 1 / NULLIF(
                 (1 / lower.edge_rate) + ((1 / upper.edge_rate) - (1 / lower.edge_rate))
                     * ((p_valuation_date - lower.rate_date)::numeric
                     / (upper.rate_date - lower.rate_date)::numeric),
                 0)
             ELSE lower.edge_rate + (upper.edge_rate - lower.edge_rate)
                 * ((p_valuation_date - lower.rate_date)::numeric
                 / (upper.rate_date - lower.rate_date)::numeric)
           END AS rate,
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

CREATE OR REPLACE FUNCTION investory.resolve_transaction_fx_rate(
    p_transaction_time timestamptz,
    p_source_currency varchar(3),
    p_target_currency varchar(3),
    p_purpose varchar(16)
) RETURNS TABLE (
    source_currency varchar(3), target_currency varchar(3), fx_rate_to_target numeric,
    source varchar(64), rate_method varchar(32), rate_source varchar(32),
    source_rate_date date, age_days integer, conversion_status varchar(32)
) LANGUAGE sql STABLE AS $$
WITH selected AS (
    SELECT er.base::varchar(3) AS source_currency,
           er.to_currency::varchar(3) AS target_currency,
           er.rate AS fx_rate_to_target,
           er.method::varchar(32) AS rate_method,
           er.source::varchar(32) AS rate_source,
           er.rate_date AS source_rate_date,
           0::integer AS age_days,
           0 AS direction_priority,
           er.observed_at,
           er.source_reference
    FROM investory.exchange_rates er
    WHERE upper(p_purpose) = 'TRANSACTION'
      AND er.method IN ('XTB_EXECUTION', 'IBKR_EXECUTION')
      AND er.rate_date = (p_transaction_time AT TIME ZONE 'Europe/Warsaw')::date
      AND er.observed_at <= p_transaction_time
      AND er.base = p_source_currency
      AND er.to_currency = p_target_currency
    UNION ALL
    SELECT er.to_currency::varchar(3), er.base::varchar(3), 1 / er.rate,
           er.method::varchar(32), er.source::varchar(32), er.rate_date,
           0::integer, 1, er.observed_at, er.source_reference
    FROM investory.exchange_rates er
    WHERE upper(p_purpose) = 'TRANSACTION'
      AND er.method IN ('XTB_EXECUTION', 'IBKR_EXECUTION')
      AND er.rate_date = (p_transaction_time AT TIME ZONE 'Europe/Warsaw')::date
      AND er.observed_at <= p_transaction_time
      AND er.base = p_target_currency
      AND er.to_currency = p_source_currency
    ORDER BY direction_priority, observed_at DESC NULLS LAST, source_reference ASC NULLS LAST
    LIMIT 1
)
SELECT p_source_currency, p_target_currency, 1, 'SAME_CURRENCY', 'SAME_CURRENCY',
       'SAME_CURRENCY', (p_transaction_time AT TIME ZONE 'Europe/Warsaw')::date, 0, 'SAME_CURRENCY'
WHERE upper(p_purpose) = 'TRANSACTION' AND p_source_currency = p_target_currency
UNION ALL
SELECT s.source_currency, s.target_currency, s.fx_rate_to_target,
       ('EXECUTION:' || s.rate_source)::varchar(64), s.rate_method, s.rate_source,
       s.source_rate_date, s.age_days, 'OK'
FROM selected s
WHERE upper(p_purpose) = 'TRANSACTION'
UNION ALL
SELECT p_source_currency, p_target_currency, NULL, 'MISSING', NULL, NULL, NULL, NULL, 'MISSING_RATE'
WHERE upper(p_purpose) = 'TRANSACTION'
  AND p_source_currency <> p_target_currency
  AND NOT EXISTS (SELECT 1 FROM selected);
$$;

CREATE OR REPLACE FUNCTION investory.refresh_reconstructed_position_daily()
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE started_at timestamptz := clock_timestamp();
BEGIN
    REFRESH MATERIALIZED VIEW investory.mv_reconstructed_position_daily;
    ANALYZE investory.mv_reconstructed_position_daily;
    RAISE LOG 'investory refresh stage=mv_reconstructed_position_daily elapsed_ms=%',
        EXTRACT(milliseconds FROM clock_timestamp() - started_at);
END;
$$;
CREATE OR REPLACE FUNCTION investory.refresh_reconstructed_account_market_daily()
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE started_at timestamptz := clock_timestamp();
BEGIN
    REFRESH MATERIALIZED VIEW investory.mv_reconstructed_account_market_daily;
    ANALYZE investory.mv_reconstructed_account_market_daily;
    RAISE LOG 'investory refresh stage=mv_reconstructed_account_market_daily elapsed_ms=%',
        EXTRACT(milliseconds FROM clock_timestamp() - started_at);
END;
$$;

CREATE OR REPLACE FUNCTION investory.refresh_reconstructed_cash_daily()
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE started_at timestamptz := clock_timestamp();
BEGIN
    REFRESH MATERIALIZED VIEW investory.mv_reconstructed_cash_daily;
    ANALYZE investory.mv_reconstructed_cash_daily;
    RAISE LOG 'investory refresh stage=mv_reconstructed_cash_daily elapsed_ms=%',
        EXTRACT(milliseconds FROM clock_timestamp() - started_at);
END;
$$;

CREATE OR REPLACE FUNCTION investory.refresh_account_daily_reconciliation()
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE started_at timestamptz := clock_timestamp();
BEGIN
    REFRESH MATERIALIZED VIEW investory.mv_account_daily_reconciliation;
    ANALYZE investory.mv_account_daily_reconciliation;
    RAISE LOG 'investory refresh stage=mv_account_daily_reconciliation elapsed_ms=%',
        EXTRACT(milliseconds FROM clock_timestamp() - started_at);
END;
$$;

CREATE OR REPLACE FUNCTION investory.refresh_reconciliation_views()
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM investory.refresh_reconstructed_position_daily();
    PERFORM investory.refresh_reconstructed_account_market_daily();
    PERFORM investory.refresh_reconstructed_cash_daily();
    PERFORM investory.refresh_account_daily_reconciliation();
    PERFORM investory.refresh_reconciliation_reporting_views();
END;
$$;

CREATE OR REPLACE FUNCTION investory.refresh_reconciliation_reporting_views()
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE started_at timestamptz := clock_timestamp();
BEGIN
    REFRESH MATERIALIZED VIEW investory.reporting_account_monthly_profit_reconciliation;
    ANALYZE investory.reporting_account_monthly_profit_reconciliation;
    REFRESH MATERIALIZED VIEW investory.reporting_account_statistics_vs_daily_reconciliation;
    ANALYZE investory.reporting_account_statistics_vs_daily_reconciliation;
    REFRESH MATERIALIZED VIEW investory.reporting_account_daily_cashflow_reconciliation;
    ANALYZE investory.reporting_account_daily_cashflow_reconciliation;
    REFRESH MATERIALIZED VIEW investory.reporting_trade_settlement_reconciliation;
    ANALYZE investory.reporting_trade_settlement_reconciliation;
    RAISE LOG 'investory refresh stage=reconciliation_reporting elapsed_ms=%',
        EXTRACT(milliseconds FROM clock_timestamp() - started_at);
END;
$$;

-- Squashed release observability: keep refresh-stage timing in the baseline
-- function definitions so clean databases get the same operational telemetry.
CREATE OR REPLACE FUNCTION investory.refresh_reporting_views()
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    started_at timestamptz := clock_timestamp();
    step_started timestamptz;
BEGIN
    step_started := clock_timestamp();
    REFRESH MATERIALIZED VIEW investory.account_monthly_mv;
    RAISE LOG 'investory refresh stage=account_monthly_mv elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    step_started := clock_timestamp();
    REFRESH MATERIALIZED VIEW investory.portfolio_monthly_mv;
    RAISE LOG 'investory refresh stage=portfolio_monthly_mv elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    step_started := clock_timestamp();
    REFRESH MATERIALIZED VIEW investory.account_statistics;
    RAISE LOG 'investory refresh stage=account_statistics elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    step_started := clock_timestamp();
    REFRESH MATERIALIZED VIEW investory.portfolio_contribution_summary;
    RAISE LOG 'investory refresh stage=portfolio_contribution_summary elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    step_started := clock_timestamp();
    REFRESH MATERIALIZED VIEW investory.portfolio_currency_breakdown;
    RAISE LOG 'investory refresh stage=portfolio_currency_breakdown elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    step_started := clock_timestamp();
    REFRESH MATERIALIZED VIEW investory.portfolio_asset_allocation;
    RAISE LOG 'investory refresh stage=portfolio_asset_allocation elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    step_started := clock_timestamp();
    REFRESH MATERIALIZED VIEW investory.symbol_performance;
    RAISE LOG 'investory refresh stage=symbol_performance elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    step_started := clock_timestamp();
    REFRESH MATERIALIZED VIEW investory.portfolio_kpi_summary;
    RAISE LOG 'investory refresh stage=portfolio_kpi_summary elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    RAISE LOG 'investory refresh stage=app_reporting_total elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - started_at);
END;
$$;

CREATE OR REPLACE FUNCTION investory.refresh_reconciliation_reporting_views()
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    started_at timestamptz := clock_timestamp();
    step_started timestamptz;
BEGIN
    step_started := clock_timestamp();
    REFRESH MATERIALIZED VIEW investory.reporting_account_monthly_profit_reconciliation;
    ANALYZE investory.reporting_account_monthly_profit_reconciliation;
    RAISE LOG 'investory refresh stage=reporting_account_monthly_profit_reconciliation elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    step_started := clock_timestamp();
    REFRESH MATERIALIZED VIEW investory.reporting_account_statistics_vs_daily_reconciliation;
    ANALYZE investory.reporting_account_statistics_vs_daily_reconciliation;
    RAISE LOG 'investory refresh stage=reporting_account_statistics_vs_daily_reconciliation elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    step_started := clock_timestamp();
    REFRESH MATERIALIZED VIEW investory.reporting_account_daily_cashflow_reconciliation;
    ANALYZE investory.reporting_account_daily_cashflow_reconciliation;
    RAISE LOG 'investory refresh stage=reporting_account_daily_cashflow_reconciliation elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    step_started := clock_timestamp();
    REFRESH MATERIALIZED VIEW investory.reporting_account_daily_cashflow_scope;
    ANALYZE investory.reporting_account_daily_cashflow_scope;
    RAISE LOG 'investory refresh stage=reporting_account_daily_cashflow_scope elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    step_started := clock_timestamp();
    REFRESH MATERIALIZED VIEW investory.reporting_trade_settlement_reconciliation;
    ANALYZE investory.reporting_trade_settlement_reconciliation;
    RAISE LOG 'investory refresh stage=reporting_trade_settlement_reconciliation elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    RAISE LOG 'investory refresh stage=reconciliation_reporting_total elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - started_at);
END;
$$;

COMMENT ON FUNCTION investory.refresh_reconciliation_views() IS
    'Refreshes disposable reconstruction facts in dependency order, then their reconciliation reports. Java invokes the stage functions separately after import so each stage can commit and be timed independently.';


-- Public naming contract: app_v_* is consumed by production application code;
-- recon_v_* is independent validation or reconciliation output. The original
-- names remain as compatibility surfaces for existing SQL clients.
CREATE OR REPLACE FUNCTION investory.refresh_app_views()
RETURNS VOID AS $$
BEGIN
    PERFORM investory.refresh_reporting_views();
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION investory.refresh_recon_views()
RETURNS VOID AS $$
BEGIN
    PERFORM investory.refresh_reconciliation_views();
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION investory.application_display_value(p_value numeric)
RETURNS numeric
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT CASE
        WHEN p_value IS NULL THEN NULL::numeric
        ELSE ROUND(p_value, 2)
    END
$$;

COMMENT ON FUNCTION investory.application_display_value(numeric) IS
    'Rounds final application-facing monetary values to cents. Canonical tables and derived calculations retain full precision.';

CREATE OR REPLACE FUNCTION investory.run_system_audit(
    p_import_history_id bigint DEFAULT NULL,
    p_trigger_source varchar(32) DEFAULT 'MANUAL'
) RETURNS uuid
LANGUAGE plpgsql
AS $$
DECLARE
    audit_id uuid := gen_random_uuid();
    import_row investory.import_history%ROWTYPE;
    errors bigint;
    warnings bigint;
    error_checks bigint;
    warning_checks bigint;
    final_status varchar(16);
    notification varchar(32);
    payload jsonb;
BEGIN
    IF p_import_history_id IS NOT NULL THEN
        SELECT * INTO STRICT import_row
        FROM investory.import_history
        WHERE id = p_import_history_id;

    END IF;

    INSERT INTO investory.system_audit_runs(
        id, import_history_id, trigger_source, status, notification_status
    ) VALUES (
        audit_id, p_import_history_id, p_trigger_source, 'STARTED', 'NONE'
    );

    INSERT INTO investory.system_audit_issues(
        audit_run_id, check_code, severity, issue_count, required_action, details
    )
    SELECT
        audit_id,
        review.check_code,
        CASE
            WHEN review.check_code IN (
                'UNSUPPORTED_TRANSACTION_STATE',
                'DUPLICATE_POSITION_LOT',
                'TIMEZONE_NAIVE_COLUMN',
                'VALUATION_INPUT_ERROR'
            ) THEN 'ERROR'
            WHEN review.check_code = 'VALUATION_INPUT_WARNING' THEN 'WARN'
            ELSE 'ERROR'
        END,
        review.issue_count,
        review.required_action,
        jsonb_build_object('source', 'reporting_monthly_import_review')
    FROM investory.reporting_monthly_import_review review
    WHERE review.issue_count > 0;

    IF p_import_history_id IS NOT NULL AND import_row.status IN ('FAILED', 'NOT_READY') THEN
        INSERT INTO investory.system_audit_issues(
            audit_run_id, check_code, severity, issue_count, required_action, details
        ) VALUES (
            audit_id,
            'IMPORT_FAILED',
            'ERROR',
            1,
            'Review the import error and rerun the source file after correction.',
            jsonb_build_object(
                'provider', import_row.provider,
                'file_name', import_row.file_name,
                'error_message', import_row.error_message
            )
        ) ON CONFLICT (audit_run_id, check_code) DO NOTHING;
    ELSIF p_import_history_id IS NOT NULL AND import_row.status = 'PARTIAL' THEN
        INSERT INTO investory.system_audit_issues(
            audit_run_id, check_code, severity, issue_count, required_action, details
        ) VALUES (
            audit_id,
            'IMPORT_PARTIAL',
            'WARN',
            GREATEST(COALESCE(import_row.rows_failed, 1), 1),
            'Review rejected rows before accepting reporting as complete.',
            jsonb_build_object(
                'provider', import_row.provider,
                'file_name', import_row.file_name,
                'rows_total', import_row.rows_total,
                'rows_applied', import_row.rows_applied,
                'rows_failed', import_row.rows_failed
            )
        ) ON CONFLICT (audit_run_id, check_code) DO NOTHING;
    END IF;

    SELECT
        COALESCE(sum(issue_count) FILTER (WHERE severity = 'ERROR'), 0)::bigint,
        COALESCE(sum(issue_count) FILTER (WHERE severity = 'WARN'), 0)::bigint,
        count(*) FILTER (WHERE severity = 'ERROR'),
        count(*) FILTER (WHERE severity = 'WARN')
    INTO errors, warnings, error_checks, warning_checks
    FROM investory.system_audit_issues
    WHERE audit_run_id = audit_id;

    final_status := CASE
        WHEN errors > 0 THEN 'ERROR'
        WHEN warnings > 0 THEN 'WARN'
        ELSE 'HEALTHY'
    END;
    notification := CASE
        WHEN errors > 0 THEN 'READY_ERROR'
        WHEN warnings > 0 THEN 'READY_WARNING'
        ELSE 'NONE'
    END;

    SELECT jsonb_build_object(
        'audit_run_id', audit_id,
        'import_history_id', p_import_history_id,
        'trigger_source', p_trigger_source,
        'status', final_status,
        'notification_status', notification,
        'error_count', errors,
        'warning_count', warnings,
        'error_checks', error_checks,
        'warning_checks', warning_checks,
        'issues', COALESCE(jsonb_agg(
            jsonb_build_object(
                'check_code', issue.check_code,
                'severity', issue.severity,
                'issue_count', issue.issue_count,
                'required_action', issue.required_action,
                'details', issue.details
            ) ORDER BY issue.severity DESC, issue.check_code
        ) FILTER (WHERE issue.id IS NOT NULL), '[]'::jsonb)
    )
    INTO payload
    FROM investory.system_audit_issues issue
    WHERE issue.audit_run_id = audit_id;

    UPDATE investory.system_audit_runs
    SET finished_at = clock_timestamp(),
        status = final_status,
        error_count = errors,
        warning_count = warnings,
        notification_status = notification,
        summary = payload
    WHERE id = audit_id;

    RAISE LOG 'investory_audit %', payload::text;
    RETURN audit_id;
END;
$$;

COMMENT ON FUNCTION investory.run_system_audit(bigint, varchar) IS
    'Runs and persists the canonical reporting audit. The JSON summary is suitable for structured application logs and notification adapters.';

-- Final clean-baseline refresh functions. Refreshes are serialized across
-- application instances before materialized-view relation locks are taken.
CREATE OR REPLACE FUNCTION investory.refresh_reporting_views()
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(2147483647, 1001);
    REFRESH MATERIALIZED VIEW investory.account_monthly_mv;
    REFRESH MATERIALIZED VIEW investory.portfolio_monthly_mv;
    REFRESH MATERIALIZED VIEW investory.account_statistics;
    REFRESH MATERIALIZED VIEW investory.portfolio_contribution_summary;
    REFRESH MATERIALIZED VIEW investory.portfolio_currency_breakdown;
    REFRESH MATERIALIZED VIEW investory.portfolio_asset_allocation;
    REFRESH MATERIALIZED VIEW investory.symbol_performance;
    REFRESH MATERIALIZED VIEW investory.portfolio_kpi_summary;
END;
$$;

CREATE OR REPLACE FUNCTION investory.refresh_reconciliation_views()
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(2147483647, 1001);
    REFRESH MATERIALIZED VIEW investory.mv_reconstructed_position_daily;
    ANALYZE investory.mv_reconstructed_position_daily;
    REFRESH MATERIALIZED VIEW investory.mv_reconstructed_account_market_daily;
    ANALYZE investory.mv_reconstructed_account_market_daily;
    REFRESH MATERIALIZED VIEW investory.mv_reconstructed_cash_daily;
    ANALYZE investory.mv_reconstructed_cash_daily;
    REFRESH MATERIALIZED VIEW investory.mv_account_daily_reconciliation;
    ANALYZE investory.mv_account_daily_reconciliation;
    REFRESH MATERIALIZED VIEW investory.reporting_account_monthly_profit_reconciliation;
    ANALYZE investory.reporting_account_monthly_profit_reconciliation;
    REFRESH MATERIALIZED VIEW investory.reporting_account_statistics_vs_daily_reconciliation;
    ANALYZE investory.reporting_account_statistics_vs_daily_reconciliation;
    REFRESH MATERIALIZED VIEW investory.reporting_account_daily_cashflow_reconciliation;
    ANALYZE investory.reporting_account_daily_cashflow_reconciliation;
    REFRESH MATERIALIZED VIEW investory.reporting_account_daily_cashflow_scope;
    ANALYZE investory.reporting_account_daily_cashflow_scope;
    REFRESH MATERIALIZED VIEW investory.reporting_trade_settlement_reconciliation;
    ANALYZE investory.reporting_trade_settlement_reconciliation;
END;
$$;