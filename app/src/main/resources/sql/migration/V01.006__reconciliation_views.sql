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

-- FX usability is defined once by investory.fx_status_usable() in
-- V01.005__portfolio_views.sql and called directly inside every production view
-- and materialized view. No post-hoc view-text rewriting is required here.

COMMENT ON COLUMN investory.fx_configuration.config_value IS
    'Runtime FX policy value. daily_history_start is the immutable migration boundary used by SQL and Java resolver calls.';

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

-- Diagnostic-only evidence for component gaps that occur alongside internal
-- transfer/bookkeeping rows. Imported facts and production cash reconstruction
-- remain unchanged.
CREATE MATERIALIZED VIEW investory.reporting_account_daily_cashflow_scope AS
WITH internal_daily AS (
    SELECT nco.account_id, nco.date::date AS snapshot_date,
           COALESCE(SUM(nco.amount_in_portfolio_base_currency) FILTER (
               WHERE nco.amount_in_portfolio_base_currency > 0
                 AND nco.normalized_category IN ('INTERNAL_TRANSFER_IN', 'INTERNAL_BOOKKEEPING')), 0) AS internal_inflow_base,
           COALESCE(SUM(-nco.amount_in_portfolio_base_currency) FILTER (
               WHERE nco.amount_in_portfolio_base_currency < 0
                 AND nco.normalized_category IN ('INTERNAL_TRANSFER_OUT', 'INTERNAL_BOOKKEEPING')), 0) AS internal_outflow_base,
           COUNT(*) FILTER (WHERE nco.normalized_category IN
               ('INTERNAL_TRANSFER_IN', 'INTERNAL_TRANSFER_OUT', 'INTERNAL_BOOKKEEPING')) AS internal_operation_count
    FROM investory.normalized_cash_operations nco
    GROUP BY nco.account_id, nco.date::date
), evidence AS (
    SELECT r.*, COALESCE(i.internal_inflow_base, 0) AS internal_inflow_base,
           COALESCE(i.internal_outflow_base, 0) AS internal_outflow_base,
           COALESCE(i.internal_operation_count, 0) AS internal_operation_count
    FROM investory.recon_v_account_daily_cashflow r
    LEFT JOIN internal_daily i ON i.account_id = r.account_id AND i.snapshot_date = r.snapshot_date
)
SELECT e.*,
       CASE WHEN e.internal_operation_count > 0 AND (
           (ABS(COALESCE(e.deposits_gap, 0)) > 20 AND ABS(e.deposits_gap) > 0.01 * GREATEST(ABS(COALESCE(e.account_daily_deposits, 0)), ABS(COALESCE(e.ledger_deposits, 0))))
           OR (ABS(COALESCE(e.withdrawals_gap, 0)) > 20 AND ABS(e.withdrawals_gap) > 0.01 * GREATEST(ABS(COALESCE(e.account_daily_withdrawals, 0)), ABS(COALESCE(e.ledger_withdrawals, 0))))
       ) THEN 'INTERNAL_TRANSFER_SCOPE_REVIEW' ELSE 'UNCLASSIFIED' END AS component_diagnostic_code
FROM evidence e;

CREATE UNIQUE INDEX ux_mv_reporting_account_daily_cashflow_scope_key
    ON investory.reporting_account_daily_cashflow_scope(account_id, snapshot_date);

CREATE OR REPLACE VIEW investory.recon_v_account_daily_cashflow_scope AS
SELECT * FROM investory.reporting_account_daily_cashflow_scope;

COMMENT ON MATERIALIZED VIEW investory.reporting_account_daily_cashflow_scope IS
    'Diagnostic-only cash-flow scope evidence. Internal transfer/bookkeeping rows are exposed for review; imported facts and production cash reconstruction are unchanged.';

-- Squashed semantic review views. These classify known reporting semantics while
-- leaving accounting facts, formulas, and materiality thresholds unchanged.
CREATE OR REPLACE VIEW investory.v_account_daily_market_value_semantic_review AS
SELECT r.account_id, a.name AS account_name, a.provider, r.valuation_date,
       r.reported_market_value, r.reconstructed_market_value, r.market_value_difference,
       r.reported_equity, r.reconstructed_equity, r.equity_difference,
       r.reported_unrealized_profit, r.reconstructed_unrealized_profit, r.unrealized_difference,
       'MARKET_VALUE_SEMANTIC_REVIEW'::varchar(64) AS diagnostic_code,
       'Imported broker market value differs from canonical position reconstruction; cash, cost base, and realized result are unchanged.'::text AS diagnostic_message
FROM investory.v_account_daily_reconciliation r
JOIN investory.accounts a ON a.id = r.account_id
WHERE r.status = 'FAIL'
  AND r.validation_message = 'market value mismatch'
  AND r.cash_difference = 0
  AND r.cost_base_difference = 0
  AND r.realized_difference = 0
  AND r.equity_difference = r.market_value_difference
  AND r.unrealized_difference = r.market_value_difference;

CREATE OR REPLACE VIEW investory.v_portfolio_account_quality AS
WITH latest_validation AS (
    SELECT DISTINCT ON (account_id) account_id, valuation_date, status,
           missing_prices, missing_fx_rates, missing_multipliers,
           residual_positions, reconciliation_failures
    FROM investory.v_reporting_validation_summary
    ORDER BY account_id, valuation_date DESC
), market_reviews AS (
    SELECT account_id, valuation_date, COUNT(*)::bigint AS review_count
    FROM investory.v_account_daily_market_value_semantic_review
    GROUP BY account_id, valuation_date
), statistics_status AS (
    SELECT account_id, reconciliation_status
    FROM investory.reporting_account_statistics_vs_daily_reconciliation
)
SELECT a.id AS account_id, a.name AS account_name, a.provider, lv.valuation_date,
       CASE
         WHEN lv.account_id IS NULL THEN 'REVIEW'
         WHEN COALESCE(lv.missing_prices, 0) > 0 OR COALESCE(lv.missing_fx_rates, 0) > 0
           OR COALESCE(lv.missing_multipliers, 0) > 0 OR COALESCE(lv.residual_positions, 0) > 0 THEN 'UNRECONCILED'
         WHEN COALESCE(lv.reconciliation_failures, 0) > 0
          AND COALESCE(mr.review_count, 0) <> lv.reconciliation_failures THEN 'UNRECONCILED'
         WHEN COALESCE(ss.reconciliation_status, 'OK') = 'VALUATION_ASOF_DIFFERENCE' THEN 'REVIEW'
         WHEN lv.status = 'PASS' THEN 'RECONCILED' ELSE 'REVIEW'
       END::varchar(16) AS quality_status,
       CASE
         WHEN lv.account_id IS NULL THEN 'NO_VALIDATION_SNAPSHOT'
         WHEN COALESCE(lv.missing_prices, 0) > 0 THEN 'MISSING_REQUIRED_PRICE'
         WHEN COALESCE(lv.missing_fx_rates, 0) > 0 THEN 'MISSING_REQUIRED_FX'
         WHEN COALESCE(lv.missing_multipliers, 0) > 0 THEN 'MISSING_REQUIRED_MULTIPLIER'
         WHEN COALESCE(lv.residual_positions, 0) > 0 THEN 'RESIDUAL_POSITION_VALUE'
         WHEN COALESCE(lv.reconciliation_failures, 0) > 0
          AND COALESCE(mr.review_count, 0) = lv.reconciliation_failures THEN 'MARKET_VALUE_SEMANTIC_REVIEW'
         WHEN COALESCE(ss.reconciliation_status, 'OK') = 'VALUATION_ASOF_DIFFERENCE' THEN 'VALUATION_ASOF_REVIEW'
         WHEN lv.status = 'PASS' THEN 'RECONCILED' ELSE 'VALIDATION_REVIEW'
       END::varchar(64) AS quality_reason
FROM investory.accounts a
LEFT JOIN latest_validation lv ON lv.account_id = a.id
LEFT JOIN market_reviews mr ON mr.account_id = lv.account_id AND mr.valuation_date = lv.valuation_date
LEFT JOIN statistics_status ss ON ss.account_id = a.id;

DROP VIEW IF EXISTS investory.v_portfolio_data_quality CASCADE;
CREATE VIEW investory.v_portfolio_data_quality AS
WITH active_accounts AS (SELECT COUNT(*)::bigint AS total_accounts FROM investory.accounts),
states AS (
    SELECT COUNT(*) FILTER (WHERE quality_status = 'RECONCILED')::bigint AS reconciled_accounts,
           COUNT(*) FILTER (WHERE quality_status = 'REVIEW')::bigint AS review_accounts,
           COUNT(*) FILTER (WHERE quality_status = 'UNRECONCILED')::bigint AS unreconciled_accounts
    FROM investory.v_portfolio_account_quality
), latest_positions AS (
    SELECT * FROM investory.v_reconstructed_position_daily
    WHERE valuation_date = (SELECT MAX(valuation_date) FROM investory.v_reconstructed_position_daily)
), pq AS (
    SELECT COUNT(*)::bigint AS total_open_positions,
           COUNT(*) FILTER (WHERE selected_price IS NOT NULL AND reconstruction_status <> 'FAIL')::bigint AS priced_open_positions,
           COUNT(*) FILTER (WHERE selected_price IS NULL)::bigint AS missing_price_count,
           COUNT(*) FILTER (WHERE price_age_days > 10)::bigint AS stale_price_count,
           COUNT(*) FILTER (WHERE selection_priority IN (3, 5) OR price_quality ILIKE '%PROXY%' OR price_quality ILIKE '%ALTERNATE%' OR price_quality ILIKE '%INTERPOLAT%')::bigint AS proxy_price_count,
           COUNT(*) FILTER (WHERE selection_priority = 5 OR price_quality ILIKE '%INTERPOLAT%')::bigint AS estimated_price_count,
           COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(fx_conversion_status))::bigint AS missing_fx_count,
           MAX(selected_price_date) AS latest_price_date
    FROM latest_positions WHERE open_quantity <> 0
), cq AS (
    SELECT COUNT(*)::bigint AS ambiguous_cost_basis_currency_count
    FROM investory.v_position_currency_validation
    WHERE anomaly_code IN ('MIXED_OPEN_POSITION_CURRENCIES', 'MISSING_POSITION_CURRENCY')
), cashq AS (
    SELECT COUNT(*)::bigint AS unclassified_cash_operation_count
    FROM investory.normalized_cash_operations WHERE normalized_category = 'UNCLASSIFIED'
), q AS (
    SELECT aa.total_accounts, s.reconciled_accounts, s.unreconciled_accounts,
           pq.total_open_positions, pq.priced_open_positions, pq.missing_price_count,
           pq.stale_price_count, pq.proxy_price_count, pq.estimated_price_count, pq.missing_fx_count,
           cq.ambiguous_cost_basis_currency_count, cq.ambiguous_cost_basis_currency_count AS excluded_position_count,
           cashq.unclassified_cash_operation_count,
           (SELECT MAX(finished_at) FROM investory.import_history WHERE status = 'COMPLETED') AS latest_broker_reconciliation_at,
           (SELECT MAX(finished_at) FROM investory.import_history WHERE status = 'COMPLETED') AS latest_import_at,
           pq.latest_price_date, (SELECT MAX(rate_date) FROM investory.exchange_rates) AS latest_fx_date,
           (SELECT MAX(updated_at) FROM investory.account_daily) AS latest_reporting_refresh_at,
           s.review_accounts
    FROM active_accounts aa CROSS JOIN states s CROSS JOIN pq CROSS JOIN cq CROSS JOIN cashq
)
SELECT q.total_accounts, q.reconciled_accounts, q.unreconciled_accounts, q.total_open_positions,
       q.priced_open_positions, q.missing_price_count, q.stale_price_count, q.proxy_price_count,
       q.estimated_price_count, q.missing_fx_count, q.ambiguous_cost_basis_currency_count,
       q.excluded_position_count, q.unclassified_cash_operation_count, q.latest_broker_reconciliation_at,
       q.latest_import_at, q.latest_price_date, q.latest_fx_date, q.latest_reporting_refresh_at,
       CASE WHEN q.unreconciled_accounts > 0 OR q.missing_price_count > 0 OR q.missing_fx_count > 0
                  OR q.ambiguous_cost_basis_currency_count > 0 OR q.priced_open_positions < q.total_open_positions THEN 'CRITICAL'
            WHEN q.review_accounts > 0 OR q.stale_price_count > 0 OR q.proxy_price_count > 0
                  OR q.estimated_price_count > 0 OR q.unclassified_cash_operation_count > 0 THEN 'REVIEW'
            ELSE 'HEALTHY' END::varchar(16) AS quality_state,
       q.review_accounts
FROM q;

COMMENT ON VIEW investory.v_portfolio_account_quality IS
    'Per-account quality state. Semantic review cases remain visible without being classified as unreconciled.';
COMMENT ON VIEW investory.v_portfolio_data_quality IS
    'Portfolio quality aggregation with explicit RECONCILED, REVIEW, and UNRECONCILED account states.';

CREATE OR REPLACE VIEW investory.recon_v_portfolio_data_quality AS
SELECT * FROM investory.v_portfolio_data_quality;

-- Squashed 01.012 monthly account-flow scope.
DROP MATERIALIZED VIEW IF EXISTS investory.reporting_account_monthly_profit_reconciliation CASCADE;
CREATE MATERIALIZED VIEW investory.reporting_account_monthly_profit_reconciliation AS
WITH daily AS (
    SELECT account_id, date_trunc('month', snapshot_date)::date AS month,
           SUM(COALESCE(daily_profit_amount, 0)) AS summed_daily_profit
    FROM investory.account_daily GROUP BY account_id, date_trunc('month', snapshot_date)::date
), flows AS (
    SELECT account_id, date_trunc('month', date)::date AS month,
           COALESCE(SUM(amount_in_portfolio_base_currency) FILTER (WHERE (is_external_flow OR is_internal_transfer) AND amount_in_portfolio_base_currency > 0), 0) AS inflows,
           COALESCE(SUM(-amount_in_portfolio_base_currency) FILTER (WHERE (is_external_flow OR is_internal_transfer) AND amount_in_portfolio_base_currency < 0), 0) AS outflows
    FROM investory.normalized_cash_operations GROUP BY account_id, date_trunc('month', date)::date
), values AS (
    SELECT am.*, COALESCE(d.summed_daily_profit, 0) AS profit,
           COALESCE(f.inflows, 0) AS inflows, COALESCE(f.outflows, 0) AS outflows
    FROM investory.account_monthly_mv am
    LEFT JOIN daily d ON d.account_id = am.account_id AND d.month = am.month
    LEFT JOIN flows f ON f.account_id = am.account_id AND f.month = am.month
), calc AS (
    SELECT v.*, v.closing_equity - v.opening_equity - v.inflows + v.outflows AS boundary,
           v.profit - v.closing_equity + v.opening_equity + v.inflows - v.outflows AS diff
    FROM values v
)
SELECT account_id, month, first_date, end_date, opening_equity, closing_equity, deposits, withdrawals,
       dividends, interest, fees, taxes, realized_profit, profit AS canonical_profit,
       profit AS summed_daily_profit, investory.reconciliation_display_value(boundary) AS expected_boundary_profit,
       investory.reconciliation_display_value(boundary) AS boundary_profit,
       investory.reconciliation_display_value(diff) AS difference,
       investory.reconciliation_display_value(diff) AS daily_sum_vs_boundary_difference,
       investory.reconciliation_display_value(investory.reconciliation_effective_tolerance(profit, boundary)) AS monthly_effective_tolerance,
       investory.reconciliation_display_value(investory.reconciliation_effective_tolerance(profit, boundary)) AS daily_sum_effective_tolerance,
       CASE WHEN NOT investory.reconciliation_values_match(profit, boundary) THEN 'MISMATCH' ELSE 'OK' END AS reconciliation_status
FROM calc WITH DATA;
CREATE UNIQUE INDEX ux_mv_reporting_account_monthly_profit_reconciliation_key
    ON investory.reporting_account_monthly_profit_reconciliation(account_id, month);
CREATE OR REPLACE VIEW investory.recon_v_account_monthly_profit AS
SELECT * FROM investory.reporting_account_monthly_profit_reconciliation;

-- Squashed 01.019 settlement scope correction. Rebuild the dependent objects
-- from their baseline definitions while excluding assets outside import scope.
DO $$
DECLARE settlement_definition text; account_definition text; alias_definition text;
BEGIN
    SELECT pg_get_viewdef('investory.reporting_trade_settlement_reconciliation'::regclass, true) INTO settlement_definition;
    SELECT pg_get_viewdef('investory.reporting_trade_settlement_reconciliation_by_account'::regclass, true) INTO account_definition;
    SELECT pg_get_viewdef('investory.recon_v_trade_settlement'::regclass, true) INTO alias_definition;
    DROP VIEW investory.recon_v_trade_settlement;
    DROP VIEW investory.reporting_trade_settlement_reconciliation_by_account;
    DROP MATERIALIZED VIEW investory.reporting_trade_settlement_reconciliation;
    settlement_definition := replace(settlement_definition, 'asset.id = p.asset_id', 'asset.id = p.asset_id AND asset.exclude_from_import = false');
    settlement_definition := regexp_replace(settlement_definition, ';[[:space:]]*$', '');
    account_definition := regexp_replace(account_definition, ';[[:space:]]*$', '');
    alias_definition := regexp_replace(alias_definition, ';[[:space:]]*$', '');
    EXECUTE 'CREATE MATERIALIZED VIEW investory.reporting_trade_settlement_reconciliation AS ' || settlement_definition || ' WITH DATA';
    CREATE UNIQUE INDEX uq_reporting_trade_settlement_reconciliation ON investory.reporting_trade_settlement_reconciliation(account_id, asset_id, valuation_date);
    CREATE INDEX idx_reporting_trade_settlement_reconciliation_status ON investory.reporting_trade_settlement_reconciliation(reconciliation_status, anomaly_code, valuation_date DESC);
    EXECUTE 'CREATE VIEW investory.reporting_trade_settlement_reconciliation_by_account AS ' || account_definition;
    EXECUTE 'CREATE VIEW investory.recon_v_trade_settlement AS ' || alias_definition;
END $$;

-- 1. asset_id is non-null for every row;
-- 2. every STOOQ row has a valid source_mapping_id;
-- 3. price_scale_factor and price_currency follow the mapping contract;
-- 4. no duplicate (asset_id, price_date, source) exists.

-- Stable diagnostic codes for the application reconciliation report.
CREATE OR REPLACE VIEW investory.recon_v_account_daily_diagnostic AS
SELECT
    r.*,
    CASE r.validation_message
        WHEN 'cash mismatch' THEN 'ACCOUNT_DAILY_CASH_RECONCILIATION'
        WHEN 'cash reconstruction failed' THEN 'ACCOUNT_DAILY_CASH_RECONCILIATION'
        WHEN 'market value mismatch' THEN 'ACCOUNT_DAILY_MARKET_VALUE_RECONCILIATION'
        WHEN 'position reconstruction failed' THEN 'ACCOUNT_DAILY_MARKET_VALUE_RECONCILIATION'
        WHEN 'valuation used lower-quality price source' THEN 'ACCOUNT_DAILY_MARKET_VALUE_RECONCILIATION'
        WHEN 'cost base mismatch' THEN 'ACCOUNT_DAILY_COST_BASE_RECONCILIATION'
        WHEN 'unrealized mismatch' THEN 'ACCOUNT_DAILY_UNREALIZED_RECONCILIATION'
        WHEN 'realized result mismatch' THEN 'ACCOUNT_DAILY_REALIZED_RECONCILIATION'
        WHEN 'realized result reconstruction failed' THEN 'ACCOUNT_DAILY_REALIZED_RECONCILIATION'
        WHEN 'equity mismatch' THEN 'ACCOUNT_DAILY_EQUITY_RECONCILIATION'
        ELSE 'UNKNOWN'
    END::varchar(96) AS diagnostic_code
FROM investory.v_account_daily_reconciliation r;

COMMENT ON VIEW investory.recon_v_account_daily_diagnostic IS
    'Stable diagnostic-code projection for reconciliation consumers; unknown source conditions remain UNKNOWN.';

-- Canonical C1 status-decision evidence. The existing
-- reporting_account_daily_cashflow_reconciliation materialized view remains a
-- rounded compatibility/presentation surface.
CREATE OR REPLACE VIEW investory.reconciliation_account_daily_cashflow_full_precision AS
WITH ledger_daily AS (
    SELECT nco.account_id,
           nco.date::date AS snapshot_date,
           nco.account_currency::varchar(3) AS account_currency,
           nco.base_currency::varchar(3) AS base_currency,
           SUM(nco.amount_in_portfolio_base_currency) FILTER (
               WHERE investory.fx_status_usable(nco.portfolio_conversion_status)) AS ledger_cash_base,
           SUM(nco.amount_in_portfolio_base_currency) FILTER (
               WHERE nco.normalized_category = 'EXTERNAL_DEPOSIT') AS ledger_deposits,
           SUM(ABS(nco.amount_in_portfolio_base_currency)) FILTER (
               WHERE nco.normalized_category = 'EXTERNAL_WITHDRAWAL') AS ledger_withdrawals,
           SUM(nco.amount_in_portfolio_base_currency) FILTER (
               WHERE nco.normalized_category IN ('DIVIDEND', 'DIVIDEND_REVERSAL')) AS ledger_dividends,
           SUM(nco.amount_in_portfolio_base_currency) FILTER (
               WHERE nco.normalized_category IN ('INTEREST', 'INTEREST_REVERSAL')) AS ledger_interest,
           SUM(-nco.amount_in_portfolio_base_currency) FILTER (
               WHERE nco.normalized_category = 'FEE') AS ledger_fees,
           SUM(-nco.amount_in_portfolio_base_currency) FILTER (
               WHERE nco.normalized_category IN ('WITHHOLDING_TAX', 'WITHHOLDING_TAX_REVERSAL', 'OTHER_TAX')) AS ledger_taxes,
           COUNT(*) FILTER (WHERE nco.normalized_category IN
               ('INTERNAL_TRANSFER_IN', 'INTERNAL_TRANSFER_OUT', 'INTERNAL_BOOKKEEPING')) AS internal_operation_count,
           COUNT(*) FILTER (
               WHERE NOT investory.fx_status_usable(nco.portfolio_conversion_status)) = 0 AS is_complete
    FROM investory.normalized_cash_operations nco
    GROUP BY nco.account_id, nco.date::date, nco.account_currency, nco.base_currency
), daily_with_prev AS (
    SELECT ad.account_id,
           ad.snapshot_date,
           ad.valuation_currency::varchar(3) AS valuation_currency,
           ad.cash_balance,
           LAG(ad.cash_balance) OVER (
               PARTITION BY ad.account_id ORDER BY ad.snapshot_date) AS previous_cash_balance,
           ad.deposits,
           ad.withdrawals,
           ad.dividends,
           ad.interest,
           ad.fees,
           ad.taxes
    FROM investory.account_daily ad
)
SELECT ad.account_id,
       ad.snapshot_date,
       COALESCE(ld.account_currency, ad.valuation_currency) AS account_currency,
       COALESCE(ld.base_currency, ad.valuation_currency) AS ledger_base_currency,
       COALESCE(ld.is_complete, true) AS is_complete,
       COALESCE(ld.internal_operation_count, 0) AS internal_operation_count,
       ad.cash_balance - COALESCE(ad.previous_cash_balance, 0) AS account_cash_delta,
       COALESCE(ld.ledger_cash_base, 0) AS ledger_cash_delta,
       ad.deposits,
       COALESCE(ld.ledger_deposits, 0) AS ledger_deposits,
       ad.withdrawals,
       COALESCE(ld.ledger_withdrawals, 0) AS ledger_withdrawals,
       ad.dividends,
       COALESCE(ld.ledger_dividends, 0) AS ledger_dividends,
       ad.interest,
       COALESCE(ld.ledger_interest, 0) AS ledger_interest,
       ad.fees,
       COALESCE(ld.ledger_fees, 0) AS ledger_fees,
       ad.taxes,
       COALESCE(ld.ledger_taxes, 0) AS ledger_taxes,
       CASE WHEN COALESCE(ld.account_currency, ad.valuation_currency)
                  = COALESCE(ld.base_currency, ad.valuation_currency)
            THEN (ad.cash_balance - COALESCE(ad.previous_cash_balance, 0))
                 - COALESCE(ld.ledger_cash_base, 0)
            ELSE NULL::numeric END AS same_currency_cash_delta_gap,
       ad.deposits - COALESCE(ld.ledger_deposits, 0) AS deposits_gap,
       ad.withdrawals - COALESCE(ld.ledger_withdrawals, 0) AS withdrawals_gap,
       ad.dividends - COALESCE(ld.ledger_dividends, 0) AS dividends_gap,
       ad.interest - COALESCE(ld.ledger_interest, 0) AS interest_gap,
       ad.fees - COALESCE(ld.ledger_fees, 0) AS fees_gap,
       ad.taxes - COALESCE(ld.ledger_taxes, 0) AS taxes_gap
FROM daily_with_prev ad
LEFT JOIN ledger_daily ld
  ON ld.account_id = ad.account_id
 AND ld.snapshot_date = ad.snapshot_date;

COMMENT ON VIEW investory.reconciliation_account_daily_cashflow_full_precision IS
    'Canonical full-precision C1 evidence. Status decisions use this view; rounded diagnostic columns remain in reporting_account_daily_cashflow_reconciliation.';
