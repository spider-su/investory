-- Shared financial, FX, valuation, and reporting refresh functions.
-- Consolidated final schema; historical repair steps are not replayed.

SET search_path TO investory, public;
SET check_function_bodies = false;

CREATE FUNCTION investory.analyze_refresh_sources() RETURNS void
    LANGUAGE plpgsql
    AS $$
DECLARE
    started_at timestamptz := clock_timestamp();
    tbl text;
BEGIN
    FOREACH tbl IN ARRAY ARRAY[
        'investory.currencies',
        'investory.fx_configuration',
        'investory.portfolios',
        'investory.accounts',
        'investory.cash_operations',
        'investory.positions',
        'investory.account_daily',
        'investory.asset_price_history',
        'investory.exchange_rates',
        'investory.assets'
    ] LOOP
        BEGIN
            EXECUTE format('ANALYZE %s', tbl);
        EXCEPTION
            WHEN undefined_table THEN
                RAISE WARNING 'investory refresh source table % is missing; continuing', tbl;
        END;
    END LOOP;

    RAISE LOG 'investory refresh stage=analyze_sources elapsed_ms=%',
        EXTRACT(milliseconds FROM clock_timestamp() - started_at);
END;
$$;

CREATE FUNCTION investory.application_display_value(p_value numeric) RETURNS numeric
    LANGUAGE sql IMMUTABLE
    AS $$
    SELECT CASE
        WHEN p_value IS NULL THEN NULL::numeric
        ELSE ROUND(p_value, 2)
    END
$$;

CREATE FUNCTION investory.fx_daily_coverage_supported(p_start_date date) RETURNS boolean
    LANGUAGE sql STABLE
    AS $$
    SELECT NOT EXISTS (
        SELECT 1
        FROM investory.currencies c
        WHERE NOT EXISTS (
            SELECT 1
            FROM investory.exchange_rates er
            WHERE er.rate_date = p_start_date
              AND er.purpose = 'VALUATION'
              AND er.rate > 0
              AND (er.base = c.id OR er.to_currency = c.id)
        )
    );
$$;

CREATE FUNCTION investory.fx_status_usable(p_status character varying) RETURNS boolean
    LANGUAGE sql IMMUTABLE
    AS $$
    SELECT COALESCE(p_status IN ('OK', 'ESTIMATED', 'SAME_CURRENCY', 'CARRY_FORWARD'), false)
$$;

CREATE FUNCTION investory.investment_fn_bind_asset_price_history_source_mapping() RETURNS trigger
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

CREATE FUNCTION investory.reconciliation_display_value(p_value numeric) RETURNS numeric
    LANGUAGE sql STABLE
    AS $$
    SELECT CASE
        WHEN p_value IS NULL THEN NULL::numeric
        ELSE ROUND(
            p_value,
            investory.reconciliation_parameter('reconciliation_reporting_scale')::integer
        )
    END
$$;

CREATE FUNCTION investory.reconciliation_effective_tolerance(p_expected numeric, p_actual numeric) RETURNS numeric
    LANGUAGE sql STABLE
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

CREATE FUNCTION investory.reconciliation_parameter(p_parameter_name character varying) RETURNS numeric
    LANGUAGE sql STABLE
    AS $$
    SELECT numeric_value
    FROM investory.reconciliation_parameters
    WHERE parameter_name = p_parameter_name
$$;

CREATE FUNCTION investory.reconciliation_values_match(p_expected numeric, p_actual numeric) RETURNS boolean
    LANGUAGE sql STABLE
    AS $$
    SELECT p_expected IS NOT NULL
       AND p_actual IS NOT NULL
       AND ABS(p_actual - p_expected)
           <= investory.reconciliation_effective_tolerance(p_expected, p_actual)
$$;

CREATE FUNCTION investory.refresh_account_daily_reconciliation() RETURNS void
    LANGUAGE plpgsql
    AS $$
DECLARE started_at timestamptz := clock_timestamp();
BEGIN
    PERFORM investory.refresh_materialized_view('recon_v_account_daily_reconciliation_mv', false);
    RAISE LOG 'investory refresh stage=recon_v_account_daily_reconciliation_mv elapsed_ms=%',
        EXTRACT(milliseconds FROM clock_timestamp() - started_at);
END;
$$;

CREATE FUNCTION investory.refresh_app_views() RETURNS void
    LANGUAGE plpgsql
    AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(2147483647, 1001);
    PERFORM investory.analyze_refresh_sources();
    PERFORM investory.refresh_materialized_view('app_v_canonical_asset_daily_price_mv', false);
    PERFORM investory.refresh_materialized_view('app_v_canonical_asset_daily_price_ranked_mv', true);
    PERFORM investory.refresh_materialized_view('app_v_normalized_daily_price_mv', true);
    PERFORM investory.refresh_materialized_view('app_v_current_asset_price_mv', true);
    PERFORM investory.refresh_materialized_view('app_v_portfolio_daily_fx_rate_mv', true);
    REFRESH MATERIALIZED VIEW CONCURRENTLY investory.app_v_normalized_cash_operations;
    ANALYZE investory.app_v_normalized_cash_operations;
    PERFORM investory.refresh_materialized_view('app_v_account_monthly', true);
    PERFORM investory.refresh_materialized_view('app_v_portfolio_monthly', true);
    PERFORM investory.refresh_materialized_view('app_v_account_statistics', true);
    PERFORM investory.refresh_materialized_view('app_v_portfolio_contribution_summary_mv', true);
    PERFORM investory.refresh_materialized_view('app_v_portfolio_currency_breakdown', true);
    PERFORM investory.refresh_materialized_view('app_v_portfolio_asset_allocation', true);
    PERFORM investory.refresh_materialized_view('app_v_symbol_performance', true);
    PERFORM investory.refresh_materialized_view('app_v_portfolio_kpi_summary_mv', true);
END;
$$;

CREATE FUNCTION investory.refresh_materialized_view(p_view_name text, p_concurrently boolean) RETURNS void
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_matviews
        WHERE schemaname = 'investory'
          AND matviewname = p_view_name
    ) THEN
        RAISE EXCEPTION 'Unknown investory materialized view: %', p_view_name;
    END IF;
    IF p_concurrently THEN
        EXECUTE format('REFRESH MATERIALIZED VIEW CONCURRENTLY investory.%I', p_view_name);
    ELSE
        EXECUTE format('REFRESH MATERIALIZED VIEW investory.%I', p_view_name);
    END IF;
    EXECUTE format('ANALYZE investory.%I', p_view_name);
END;
$$;

CREATE FUNCTION investory.refresh_reconciliation_reporting_views() RETURNS void
    LANGUAGE plpgsql
    AS $$
DECLARE
    started_at timestamptz := clock_timestamp();
    step_started timestamptz;
BEGIN
    PERFORM pg_advisory_xact_lock(2147483647, 1001);
    PERFORM investory.analyze_refresh_sources();
    step_started := clock_timestamp();
    PERFORM investory.refresh_materialized_view('recon_v_account_monthly_profit', false);
    RAISE LOG 'investory refresh stage=recon_v_account_monthly_profit elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    step_started := clock_timestamp();
    PERFORM investory.refresh_materialized_view('recon_v_account_statistics_vs_daily', false);
    RAISE LOG 'investory refresh stage=recon_v_account_statistics_vs_daily elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    step_started := clock_timestamp();
    PERFORM investory.refresh_materialized_view('recon_v_account_daily_cashflow', false);
    RAISE LOG 'investory refresh stage=recon_v_account_daily_cashflow elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    step_started := clock_timestamp();
    PERFORM investory.refresh_materialized_view('recon_v_account_daily_cashflow_scope', false);
    RAISE LOG 'investory refresh stage=recon_v_account_daily_cashflow_scope elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    step_started := clock_timestamp();
    PERFORM investory.refresh_materialized_view('recon_v_trade_settlement', false);
    RAISE LOG 'investory refresh stage=recon_v_trade_settlement elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    RAISE LOG 'investory refresh stage=reconciliation_reporting_total elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - started_at);
END;
$$;

CREATE FUNCTION investory.refresh_reconstructed_account_market_daily() RETURNS void
    LANGUAGE plpgsql
    AS $$
DECLARE started_at timestamptz := clock_timestamp();
BEGIN
    PERFORM investory.refresh_materialized_view('recon_v_reconstructed_account_market_daily_mv', false);
    RAISE LOG 'investory refresh stage=recon_v_reconstructed_account_market_daily_mv elapsed_ms=%',
        EXTRACT(milliseconds FROM clock_timestamp() - started_at);
END;
$$;

CREATE FUNCTION investory.refresh_reconstructed_cash_daily() RETURNS void
    LANGUAGE plpgsql
    AS $$
DECLARE started_at timestamptz := clock_timestamp();
BEGIN
    PERFORM investory.refresh_materialized_view('recon_v_reconstructed_cash_daily_mv', false);
    RAISE LOG 'investory refresh stage=recon_v_reconstructed_cash_daily_mv elapsed_ms=%',
        EXTRACT(milliseconds FROM clock_timestamp() - started_at);
END;
$$;

CREATE FUNCTION investory.refresh_reconstructed_position_daily() RETURNS void
    LANGUAGE plpgsql
    AS $$
DECLARE
    started_at timestamptz := clock_timestamp();
BEGIN
    PERFORM pg_advisory_xact_lock(2147483647, 1001);
    PERFORM investory.analyze_refresh_sources();
    PERFORM investory.refresh_materialized_view('app_v_canonical_asset_daily_price_mv', false);
    PERFORM investory.refresh_materialized_view('app_v_canonical_asset_daily_price_ranked_mv', true);
    PERFORM investory.refresh_materialized_view('app_v_normalized_daily_price_mv', true);
    PERFORM investory.refresh_materialized_view('recon_v_reconstructed_position_daily_mv', false);
    RAISE LOG 'investory refresh stage=valuation_price_and_reconstruction elapsed_ms=%',
        EXTRACT(milliseconds FROM clock_timestamp() - started_at);
END;
$$;

CREATE FUNCTION investory.resolve_fx_rate(p_valuation_date date, p_source_currency character varying, p_target_currency character varying) RETURNS TABLE(source_currency character varying, target_currency character varying, fx_rate_to_target numeric, source character varying, rate_method character varying, rate_source character varying, source_rate_date date, age_days integer, conversion_status character varying)
    LANGUAGE sql STABLE
    AS $$
WITH candidates AS (
    SELECT er.rate, er.source, er.method,
           COALESCE(er.source_rate_date, er.rate_date) AS source_rate_date,
           er.rate_date, 0 AS direction_priority
    FROM investory.exchange_rates er
    WHERE er.purpose = 'VALUATION'
      AND er.base = p_source_currency
      AND er.to_currency = p_target_currency
      AND er.rate_date <= p_valuation_date
    UNION ALL
    SELECT 1 / er.rate, er.source, er.method,
           COALESCE(er.source_rate_date, er.rate_date), er.rate_date, 1
    FROM investory.exchange_rates er
    WHERE er.purpose = 'VALUATION'
      AND er.base = p_target_currency
      AND er.to_currency = p_source_currency
      AND er.rate_date <= p_valuation_date
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

CREATE FUNCTION investory.resolve_portfolio_fx_rate(p_portfolio_id bigint, p_valuation_date date, p_source_currency character varying) RETURNS TABLE(portfolio_id bigint, valuation_date date, source_currency character varying, base_currency character varying, fx_rate_to_base numeric, source character varying, rate_method character varying, rate_source character varying, source_rate_date date, age_days integer, conversion_status character varying)
    LANGUAGE sql STABLE
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

CREATE FUNCTION investory.resolve_transaction_fx_rate(p_transaction_time timestamp with time zone, p_source_currency character varying, p_target_currency character varying, p_purpose character varying) RETURNS TABLE(source_currency character varying, target_currency character varying, fx_rate_to_target numeric, source character varying, rate_method character varying, rate_source character varying, source_rate_date date, age_days integer, conversion_status character varying)
    LANGUAGE sql STABLE
    AS $$
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
      AND er.purpose = 'EXECUTION'
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
      AND er.purpose = 'EXECUTION'
      AND er.method IN ('XTB_EXECUTION', 'IBKR_EXECUTION')
      AND er.rate_date = (p_transaction_time AT TIME ZONE 'Europe/Warsaw')::date
      AND er.observed_at <= p_transaction_time
      AND er.base = p_target_currency
      AND er.to_currency = p_source_currency
    ORDER BY direction_priority, observed_at DESC NULLS LAST, source_reference ASC NULLS LAST
    LIMIT 1
), daily AS (
    SELECT d.*
    FROM investory.resolve_fx_rate(
        (p_transaction_time AT TIME ZONE 'Europe/Warsaw')::date,
        p_source_currency, p_target_currency) d
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
SELECT d.source_currency, d.target_currency, d.fx_rate_to_target, d.source, d.rate_method,
       d.rate_source, d.source_rate_date, d.age_days, d.conversion_status
FROM daily d
WHERE upper(p_purpose) = 'TRANSACTION'
  AND p_source_currency <> p_target_currency
  AND NOT EXISTS (SELECT 1 FROM selected);
$$;

CREATE FUNCTION investory.signed_position_quantity(operation investory.positions_operation_type, volume numeric) RETURNS numeric
    LANGUAGE sql IMMUTABLE STRICT
    AS $$
    SELECT CASE WHEN operation = 'SELL' THEN -ABS(volume) ELSE ABS(volume) END
$$;
