SET search_path TO investory, public;

CREATE OR REPLACE FUNCTION investory.investment_fn_bind_asset_price_history_source_mapping()
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
    SELECT COALESCE(p_status IN ('OK', 'ESTIMATED', 'SAME_CURRENCY'), false)
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

COMMENT ON FUNCTION investory.fx_daily_coverage_supported(date) IS
    'True only when every configured currency participates in neutral daily/reference FX on the proposed boundary date.';

COMMENT ON FUNCTION investory.resolve_fx_rate(date, varchar, varchar) IS
    'Canonical valuation FX resolver. Market daily and IBKR reference rates outrank broker execution rates. Historical gaps are explicitly estimated.';



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

CREATE OR REPLACE FUNCTION investory.analyze_refresh_sources()
RETURNS void
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

CREATE OR REPLACE FUNCTION investory.refresh_app_views()
RETURNS void LANGUAGE plpgsql AS $$
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

CREATE OR REPLACE FUNCTION investory.refresh_materialized_view(
    p_view_name text,
    p_concurrently boolean
)
RETURNS void
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

CREATE OR REPLACE FUNCTION investory.refresh_reconstructed_position_daily()
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE started_at timestamptz := clock_timestamp();
BEGIN
    PERFORM investory.refresh_materialized_view('recon_v_reconstructed_position_daily_mv', false);
    RAISE LOG 'investory refresh stage=recon_v_reconstructed_position_daily_mv elapsed_ms=%',
        EXTRACT(milliseconds FROM clock_timestamp() - started_at);
END;
$$;

CREATE OR REPLACE FUNCTION investory.refresh_reconstructed_account_market_daily()
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE started_at timestamptz := clock_timestamp();
BEGIN
    PERFORM investory.refresh_materialized_view('recon_v_reconstructed_account_market_daily_mv', false);
    RAISE LOG 'investory refresh stage=recon_v_reconstructed_account_market_daily_mv elapsed_ms=%',
        EXTRACT(milliseconds FROM clock_timestamp() - started_at);
END;
$$;

CREATE OR REPLACE FUNCTION investory.refresh_reconstructed_cash_daily()
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE started_at timestamptz := clock_timestamp();
BEGIN
    PERFORM investory.refresh_materialized_view('recon_v_reconstructed_cash_daily_mv', false);
    RAISE LOG 'investory refresh stage=recon_v_reconstructed_cash_daily_mv elapsed_ms=%',
        EXTRACT(milliseconds FROM clock_timestamp() - started_at);
END;
$$;

CREATE OR REPLACE FUNCTION investory.refresh_account_daily_reconciliation()
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE started_at timestamptz := clock_timestamp();
BEGIN
    PERFORM investory.refresh_materialized_view('recon_v_account_daily_reconciliation_mv', false);
    RAISE LOG 'investory refresh stage=recon_v_account_daily_reconciliation_mv elapsed_ms=%',
        EXTRACT(milliseconds FROM clock_timestamp() - started_at);
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
        jsonb_build_object('source', 'recon_v_reporting_monthly_import_review')
    FROM investory.recon_v_reporting_monthly_import_review review
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
