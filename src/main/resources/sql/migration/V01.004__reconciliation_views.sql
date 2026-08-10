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
-- 1. asset_id is non-null for every row;
-- 2. every STOOQ row has a valid source_mapping_id;
-- 3. price_scale_factor and price_currency follow the mapping contract;
-- 4. no duplicate (asset_id, price_date, source) exists.
