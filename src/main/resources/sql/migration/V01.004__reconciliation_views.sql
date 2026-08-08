SET search_path TO investory, public;

-- Read-only reconciliation and diagnostic objects. These verify production
-- reporting results but are not inputs to the production refresh contract.
COMMENT ON VIEW investory.v_portfolio_daily_fx_rate IS
    'Reconciliation FX layer. Production calculations use resolve_fx_rate directly; this view supports independent reconstruction checks.';

CREATE OR REPLACE FUNCTION investory.refresh_reconciliation_views()
RETURNS VOID AS $$
BEGIN
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
  AND price_currency IS DISTINCT FROM asset_currency;

COMMENT ON VIEW investory.reporting_price_history_contract_issues IS
    'Deterministic price-history contract diagnostics. Empty result is required before copying asset_price_history_tmp into asset_price_history.';

-- Before copying a production asset_price_history_tmp table, verify:
-- 1. asset_id is non-null for every row;
-- 2. every STOOQ row has a valid source_mapping_id;
-- 3. price_scale_factor and price_currency follow the mapping contract;
-- 4. no duplicate (asset_id, price_date, source) exists.
