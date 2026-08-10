SET search_path TO investory, public;

-- Read-only reconciliation and diagnostic objects. These verify production
-- reporting results but are not inputs to the production refresh contract.
COMMENT ON VIEW investory.v_portfolio_daily_fx_rate IS
    'Reconciliation FX layer. Production calculations use the centralized date-aware resolver.';

CREATE OR REPLACE VIEW investory.v_fx_reconciliation AS
WITH pairs AS (
    SELECT c1.id::varchar(3) AS source_currency, c2.id::varchar(3) AS target_currency
    FROM investory.currencies c1
    CROSS JOIN investory.currencies c2
    WHERE c1.id <> c2.id
), latest AS (
    SELECT DISTINCT ON (er.base, er.to_currency)
        er.base::varchar(3) AS source_currency,
        er.to_currency::varchar(3) AS target_currency,
        er.rate_date,
        er.source,
        er.method,
        er.rate
    FROM investory.exchange_rates er
    ORDER BY er.base, er.to_currency, er.rate_date DESC, er.imported_at DESC
), resolved AS (
    SELECT p.source_currency, p.target_currency,
           r.fx_rate_to_target, r.source_rate_date, r.rate_source,
           r.rate_method, r.age_days, r.conversion_status
    FROM pairs p
    CROSS JOIN LATERAL investory.resolve_fx_rate(
        CURRENT_DATE, p.source_currency, p.target_currency) r
)
SELECT r.source_currency,
       r.target_currency,
       r.fx_rate_to_target,
       r.source_rate_date,
       r.rate_source,
       r.rate_method,
       r.age_days,
       r.conversion_status,
       l.rate AS latest_stored_rate,
       l.rate_date AS latest_stored_date,
       l.source AS latest_stored_source,
       l.method AS latest_stored_method,
       (SELECT count(*) FROM investory.exchange_rates e
        WHERE e.method = 'INTERPOLATED'
          AND e.base::varchar(3) = r.source_currency
          AND e.to_currency::varchar(3) = r.target_currency) AS interpolated_observation_count,
       (SELECT count(*) FROM investory.exchange_rates e
        WHERE e.method = 'XTB_EXECUTION') AS xtb_execution_observation_count,
       (SELECT count(*) FROM investory.exchange_rates e
        WHERE e.method = 'IBKR_EXECUTION') AS ibkr_execution_observation_count
FROM resolved r
LEFT JOIN latest l
  ON l.source_currency = r.source_currency
 AND l.target_currency = r.target_currency;

COMMENT ON VIEW investory.v_fx_reconciliation IS
    'FX audit output: coverage, latest stored observation, resolver source/method/status, and broker observation counts.';

CREATE OR REPLACE VIEW investory.v_fx_data_quality AS
WITH jumps AS (
    SELECT base, to_currency, rate_date, rate,
           lag(rate) OVER (PARTITION BY base, to_currency ORDER BY rate_date) AS previous_rate
    FROM investory.exchange_rates
    WHERE method IN ('MARKET_DAILY', 'IBKR_DAILY_REFERENCE')
)
SELECT 'INVALID_RATE'::varchar(32) AS issue_code, base::varchar(3), to_currency::varchar(3), rate_date,
       rate::numeric, 'Rate must be positive'::text AS details
FROM investory.exchange_rates
WHERE rate <= 0
UNION ALL
SELECT 'FX_SPIKE', base::varchar(3), to_currency::varchar(3), rate_date, rate,
       'Daily/reference move exceeds 5%'::text
FROM jumps
WHERE previous_rate IS NOT NULL AND abs(rate / previous_rate - 1) > 0.05
UNION ALL
SELECT 'DAILY_COVERAGE_GAP', base::varchar(3), to_currency::varchar(3), CURRENT_DATE, NULL,
       'No recent market/reference observation after daily-history boundary'::text
FROM (VALUES ('USD'::varchar(3), 'EUR'::varchar(3)), ('USD', 'PLN'), ('EUR', 'USD'), ('EUR', 'PLN'), ('PLN', 'USD'), ('PLN', 'EUR')) pairs(base, to_currency)
WHERE CURRENT_DATE >= (SELECT config_value::date FROM investory.fx_configuration WHERE config_key = 'daily_history_start')
  AND NOT EXISTS (
      SELECT 1 FROM investory.exchange_rates er
      WHERE er.base = pairs.base AND er.to_currency = pairs.to_currency
        AND er.method IN ('MARKET_DAILY', 'IBKR_DAILY_REFERENCE')
        AND er.rate_date >= CURRENT_DATE - (SELECT config_value::integer FROM investory.fx_configuration WHERE config_key = 'max_age_days'));

CREATE OR REPLACE VIEW investory.v_fx_consistency_check AS
SELECT 'RECIPROCAL_MISMATCH'::varchar(32) AS issue_code,
       er.base::varchar(3) AS base,
       er.to_currency::varchar(3) AS to_currency,
       er.rate_date,
       abs(er.rate * inverse_rate.rate - 1) AS deviation,
       'Direct and reciprocal observations differ'::text AS details
FROM investory.exchange_rates er
JOIN investory.exchange_rates inverse_rate
  ON inverse_rate.rate_date = er.rate_date
 AND inverse_rate.base = er.to_currency
 AND inverse_rate.to_currency = er.base
 AND inverse_rate.source = er.source
 AND inverse_rate.method = er.method
WHERE abs(er.rate * inverse_rate.rate - 1) > 0.0001
UNION ALL
SELECT 'CROSS_RATE_MISMATCH', eur_usd.base, eur_usd.to_currency, eur_usd.rate_date,
       abs(eur_usd.rate * usd_pln.rate - eur_pln.rate),
       'EURUSD * USDPLN differs from EURPLN'::text
FROM investory.exchange_rates eur_usd
JOIN investory.exchange_rates usd_pln
  ON usd_pln.rate_date = eur_usd.rate_date
 AND usd_pln.source = eur_usd.source
 AND usd_pln.method = eur_usd.method
 AND usd_pln.base = 'USD' AND usd_pln.to_currency = 'PLN'
JOIN investory.exchange_rates eur_pln
  ON eur_pln.rate_date = eur_usd.rate_date
 AND eur_pln.source = eur_usd.source
 AND eur_pln.method = eur_usd.method
 AND eur_pln.base = 'EUR' AND eur_pln.to_currency = 'PLN'
WHERE eur_usd.base = 'EUR' AND eur_usd.to_currency = 'USD'
  AND abs(eur_usd.rate * usd_pln.rate - eur_pln.rate) > 0.01;

COMMENT ON VIEW investory.v_fx_consistency_check IS
    'Diagnostic reciprocal and cross-rate checks. Rows are flagged, never automatically rejected.';

CREATE OR REPLACE FUNCTION investory.refresh_reconciliation_views()
RETURNS VOID AS $$
BEGIN
    REFRESH MATERIALIZED VIEW investory.reporting_account_monthly_profit_reconciliation;
    REFRESH MATERIALIZED VIEW investory.reporting_account_statistics_vs_daily_reconciliation;
    REFRESH MATERIALIZED VIEW investory.reporting_account_daily_cashflow_reconciliation;
    REFRESH MATERIALIZED VIEW investory.v_account_daily_reconciliation;
    REFRESH MATERIALIZED VIEW investory.reporting_trade_settlement_reconciliation;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION investory.refresh_reconciliation_views() IS
    'Refreshes reconciliation materialized views on demand. Reconciliation objects are excluded from refresh_reporting_views().';

SELECT investory.refresh_reconciliation_views();

-- These objects were historical compatibility or diagnostic surfaces with no
-- live production consumer. Do not retain them for test-only references.
DROP VIEW IF EXISTS investory.v_activity_events;
DROP VIEW IF EXISTS investory.reporting_validation_issue;
DROP VIEW IF EXISTS investory.v_portfolio_data_quality;
DROP VIEW IF EXISTS investory.v_portfolio_data_quality_refresh;
DROP VIEW IF EXISTS investory.v_reporting_daily_fx_rate;
DROP VIEW IF EXISTS investory.account_monthly;
DROP VIEW IF EXISTS investory.portfolio_monthly;
DROP VIEW IF EXISTS investory.portfolio_daily;
DROP MATERIALIZED VIEW IF EXISTS investory.portfolio_daily_mv;

-- The generic discovery/history subsystem is intentionally removed. Production
-- refresh uses the explicit order in refresh_reporting_views().
DROP PROCEDURE IF EXISTS investory.refresh_reporting_materialized_views(boolean);
DROP VIEW IF EXISTS investory.reporting_materialized_view_refresh_status;
DROP VIEW IF EXISTS investory.reporting_materialized_view_dependencies;
DROP TABLE IF EXISTS investory.materialized_view_refresh_history;


CREATE OR REPLACE VIEW investory.reporting_price_history_contract_issues AS
WITH mapped_history AS (
    SELECT
        aph.asset_id,
        asset.symbol AS asset_symbol,
        aph.price_date,
        aph.source,
        aph.source_symbol,
        aph.price_origin,
        aph.estimated,
        aph.source_mapping_id,
        aph.price_currency,
        aph.price_scale_factor,
        asset.currency AS asset_currency,
        ass.id AS mapping_id,
        ass.asset_id AS mapping_asset_id,
        ass.source AS mapping_source,
        ass.source_symbol AS mapping_source_symbol,
        ass.price_currency AS mapping_price_currency,
        ass.price_scale_factor AS mapping_scale_factor
    FROM investory.asset_price_history aph
             JOIN investory.assets asset ON asset.id = aph.asset_id
             AND asset.exclude_from_import = false
             LEFT JOIN investory.asset_source_symbols ass ON ass.id = aph.source_mapping_id
)
SELECT
    asset_id,
    asset_symbol,
    price_date,
    source,
    source_symbol,
    'SOURCE_MAPPING_MISMATCH'::varchar(64) AS issue_code,
    'source_mapping_id does not match asset/source/source_symbol'::text AS issue_message
FROM mapped_history
WHERE source_mapping_id IS NOT NULL
  AND (
    mapping_id IS NULL
        OR mapping_asset_id IS DISTINCT FROM asset_id
        OR mapping_source IS DISTINCT FROM source
        OR lower(mapping_source_symbol) IS DISTINCT FROM lower(source_symbol)
    )
UNION ALL
SELECT
    asset_id,
    asset_symbol,
    price_date,
    source,
    source_symbol,
    'STOOQ_MAPPING_MISSING'::varchar(64),
    'STOOQ history row has no source mapping'::text
FROM mapped_history
WHERE source = 'STOOQ'
  AND source_mapping_id IS NULL
UNION ALL
SELECT
    asset_id,
    asset_symbol,
    price_date,
    source,
    source_symbol,
    'STOOQ_CURRENCY_MISMATCH'::varchar(64),
    'history price_currency differs from the bound provider mapping'::text
FROM mapped_history
WHERE source = 'STOOQ'
  AND source_mapping_id IS NOT NULL
  AND price_currency IS DISTINCT FROM mapping_price_currency
UNION ALL
SELECT
    asset_id,
    asset_symbol,
    price_date,
    source,
    source_symbol,
    'STOOQ_SCALE_MISMATCH'::varchar(64),
    'history price_scale_factor differs from the bound provider mapping'::text
FROM mapped_history
WHERE source = 'STOOQ'
  AND source_mapping_id IS NOT NULL
  AND price_scale_factor IS DISTINCT FROM mapping_scale_factor
UNION ALL
SELECT
    asset_id,
    asset_symbol,
    price_date,
    source,
    source_symbol,
    'XTB_QUOTE_CURRENCY_MISMATCH'::varchar(64),
    'XTB price observation currency differs from the canonical asset quote currency'::text
FROM mapped_history
WHERE source IN ('XTB_TRADE_OPEN', 'XTB_TRADE_CLOSE', 'INTERPOLATED_XTB')
  AND price_currency IS DISTINCT FROM asset_currency

UNION ALL
SELECT
    asset_id,
    asset_symbol,
    price_date,
    source,
    source_symbol,
    'GENERATED_PRICE_CURRENCY_MISMATCH'::varchar(64),
    'generated price currency differs from the canonical asset quote currency'::text
FROM mapped_history
WHERE (price_origin = 'STALE_CARRY_FORWARD' OR estimated)
  AND price_currency IS DISTINCT FROM asset_currency;

COMMENT ON VIEW investory.reporting_price_history_contract_issues IS
    'Deterministic price-history contract diagnostics. Empty result is required before copying asset_price_history_tmp into asset_price_history.';

-- Before copying a production asset_price_history_tmp table, verify:
CREATE OR REPLACE FUNCTION investory.fx_status_usable(p_status varchar)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT p_status IN ('OK', 'ESTIMATED', 'SAME_CURRENCY')
$$;

COMMENT ON FUNCTION investory.fx_status_usable(varchar) IS
    'Central FX usability contract. ESTIMATED is usable and retains its provenance; STALE, MISSING_RATE, and MISSING_CURRENCY are not usable.';

DO $$
DECLARE
    view_row record;
    view_sql text;
BEGIN
    FOR view_row IN
        SELECT schemaname, viewname
        FROM pg_views
        WHERE schemaname = 'investory'
    LOOP
        view_sql := pg_get_viewdef(format('%I.%I', view_row.schemaname, view_row.viewname)::regclass, true);
        view_sql := replace(view_sql, 'IN (''OK'', ''SAME_CURRENCY'')', 'IN (''OK'', ''ESTIMATED'', ''SAME_CURRENCY'')');
        view_sql := replace(view_sql, 'NOT IN (''OK'', ''SAME_CURRENCY'')', 'NOT IN (''OK'', ''ESTIMATED'', ''SAME_CURRENCY'')');
        view_sql := replace(view_sql, 'IN (''OK'', ''SAME_CURRENCY'')', 'IN (''OK'', ''ESTIMATED'', ''SAME_CURRENCY'')');
        EXECUTE format('CREATE OR REPLACE VIEW %I.%I AS %s', view_row.schemaname, view_row.viewname, view_sql);
    END LOOP;
END $$;

COMMENT ON COLUMN investory.fx_configuration.config_value IS
    'Runtime FX policy value. daily_history_start is the immutable migration boundary used by SQL and Java resolver calls.';

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

DO $$
DECLARE
    alias_row record;
    select_list text;
BEGIN
    FOR alias_row IN
        SELECT * FROM (VALUES
            ('app_v_current_asset_price', 'v_current_asset_price'),
            ('app_v_current_open_position_rows', 'v_current_open_position_rows'),
            ('app_v_normalized_cash_operations', 'normalized_cash_operations'),
            ('app_v_normalized_cash_operation_flows', 'normalized_cash_operation_flows'),
            ('app_v_reconstructed_position_daily', 'v_reconstructed_position_daily'),
            ('app_v_reconstructed_cash_daily', 'v_reconstructed_cash_daily'),
            ('app_v_portfolio_daily', 'v_portfolio_daily'),
            ('app_v_portfolio_daily_fx_rate', 'v_portfolio_daily_fx_rate'),
            ('app_v_account_monthly', 'account_monthly_mv'),
            ('app_v_portfolio_monthly', 'portfolio_monthly_mv'),
            ('app_v_account_statistics', 'account_statistics'),
            ('app_v_portfolio_kpi_summary', 'portfolio_kpi_summary'),
            ('app_v_portfolio_currency_breakdown', 'portfolio_currency_breakdown'),
            ('app_v_portfolio_asset_allocation', 'portfolio_asset_allocation'),
            ('app_v_symbol_performance', 'symbol_performance'),
            ('app_v_account_monthly_benchmark', 'account_monthly_benchmark'),
            ('recon_v_fx', 'v_fx_reconciliation'),
            ('recon_v_fx_data_quality', 'v_fx_data_quality'),
            ('recon_v_fx_consistency', 'v_fx_consistency_check'),
            ('recon_v_account_daily', 'v_account_daily_reconciliation'),
            ('recon_v_account_monthly_profit', 'reporting_account_monthly_profit_reconciliation'),
            ('recon_v_account_statistics_vs_daily', 'reporting_account_statistics_vs_daily_reconciliation'),
            ('recon_v_account_daily_cashflow', 'reporting_account_daily_cashflow_reconciliation'),
            ('recon_v_trade_settlement', 'reporting_trade_settlement_reconciliation'),
            ('recon_v_realized_result', 'v_realized_result_reconciliation'),
            ('recon_v_non_usd_closed_trade', 'v_non_usd_closed_trade_reconciliation'),
            ('recon_v_position_valuation_validation', 'v_position_valuation_validation'),
            ('recon_v_reporting_validation_summary', 'v_reporting_validation_summary'),
            ('recon_v_portfolio_service_fallback', 'v_portfolio_service_fallback_reconciliation'),
            ('recon_v_portfolio_data_quality', 'v_portfolio_data_quality'),
            ('recon_v_portfolio_data_quality_issue', 'v_portfolio_data_quality_issue'),
            ('recon_v_portfolio_data_quality_refresh', 'v_portfolio_data_quality_refresh'),
            ('recon_v_reporting_monthly_import_review', 'reporting_monthly_import_review'),
            ('recon_v_asset_identity_issues', 'reporting_asset_identity_issues'),
            ('recon_v_asset_price_quality_issues', 'reporting_asset_price_quality_issues'),
            ('recon_v_position_currency_validation', 'v_position_currency_validation'),
            ('recon_v_position_lot_duplicates', 'reporting_position_lot_duplicates'),
            ('recon_v_timezone_naive_columns', 'reporting_timezone_naive_columns'),
            ('recon_v_unsupported_transaction_states', 'reporting_unsupported_transaction_states'),
            ('recon_v_price_history_contract_issues', 'reporting_price_history_contract_issues')
        ) AS aliases(alias_name, source_name)
    LOOP
        IF to_regclass('investory.' || alias_row.source_name) IS NOT NULL THEN
            IF alias_row.alias_name IN (
                'app_v_account_monthly',
                'app_v_account_monthly_benchmark',
                'app_v_portfolio_daily',
                'app_v_portfolio_monthly',
                'app_v_account_statistics',
                'app_v_portfolio_kpi_summary',
                'app_v_portfolio_currency_breakdown',
                'app_v_portfolio_asset_allocation',
                'app_v_symbol_performance'
            ) THEN
                SELECT string_agg(
                    CASE
                        WHEN source_column.attname = ANY (ARRAY[
                            'cash_balance', 'market_value', 'equity', 'cost_base',
                            'unrealized_profit', 'realized_profit', 'dividends',
                            'interest', 'fees', 'taxes', 'deposits', 'withdrawals',
                            'daily_profit_amount', 'total_profit', 'opening_equity',
                            'closing_equity', 'total_deposit', 'total_withdrawal',
                            'net_deposit', 'account_net_deposit',
                            'converted_cash_subtotal', 'converted_equity_subtotal',
                            'total_deposits', 'total_withdrawals', 'net_deposits',
                            'total_cash', 'total_market_value', 'total_equity',
                            'total_realized_profit', 'total_unrealized_profit',
                            'total_dividends', 'total_interest', 'total_fees',
                            'total_taxes', 'amount_local', 'amount_in_base_currency',
                            'closed_profit', 'withholding_tax', 'cost_basis',
                            'cost_basis_in_base_currency', 'total_value_in_base_currency',
                            'unrealized_pl_in_base_currency'
                        ]) THEN format(
                            'investory.application_display_value(src.%I) AS %I',
                            source_column.attname,
                            source_column.attname)
                        ELSE format('src.%I', source_column.attname)
                    END,
                    ', ' ORDER BY source_column.attnum)
                INTO select_list
                FROM pg_attribute source_column
                JOIN pg_class source_relation
                    ON source_relation.oid = source_column.attrelid
                JOIN pg_namespace source_schema
                    ON source_schema.oid = source_relation.relnamespace
                WHERE source_schema.nspname = 'investory'
                  AND source_relation.relname = alias_row.source_name
                  AND source_column.attnum > 0
                  AND NOT source_column.attisdropped;

                EXECUTE format(
                    'CREATE OR REPLACE VIEW investory.%I AS SELECT %s FROM investory.%I src',
                    alias_row.alias_name,
                    select_list,
                    alias_row.source_name);
            ELSE
                EXECUTE format(
                    'CREATE OR REPLACE VIEW investory.%I AS SELECT * FROM investory.%I',
                    alias_row.alias_name,
                    alias_row.source_name);
            END IF;
            EXECUTE format(
                'COMMENT ON VIEW investory.%I IS %L',
                alias_row.alias_name,
                CASE WHEN alias_row.alias_name LIKE 'app_%'
                     THEN 'Application-facing derived view. Compatibility source: investory.' || alias_row.source_name
                     ELSE 'Reconciliation or diagnostic view. Not an application input. Compatibility source: investory.' || alias_row.source_name
                END);
        END IF;
    END LOOP;
END $$;

-- 1. asset_id is non-null for every row;
-- 2. every STOOQ row has a valid source_mapping_id;
-- 3. price_scale_factor and price_currency follow the mapping contract;
-- 4. no duplicate (asset_id, price_date, source) exists.
