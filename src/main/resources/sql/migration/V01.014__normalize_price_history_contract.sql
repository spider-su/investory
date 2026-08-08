-- Price-history contract after V01.003:
-- * asset_source_symbols stores raw provider quote currency and raw-unit scale.
-- * asset_price_history stores raw provider OHLC and the same scale factor.
-- * canonical views apply price_scale_factor exactly once.
--
-- V01.003 bulk rows were generated after applying source-unit normalization to
-- their OHLC values while retaining the mapping factor. Restore raw units for
-- every such scaled row without naming a symbol-specific exception.
UPDATE investory.asset_price_history aph
SET open_price = aph.open_price / NULLIF(aph.price_scale_factor, 0),
    high_price = aph.high_price / NULLIF(aph.price_scale_factor, 0),
    low_price = aph.low_price / NULLIF(aph.price_scale_factor, 0),
    close_price = aph.close_price / NULLIF(aph.price_scale_factor, 0),
    adjusted_close_price =
        aph.adjusted_close_price / NULLIF(aph.price_scale_factor, 0)
WHERE aph.source = 'STOOQ'
  AND aph.price_scale_factor IS DISTINCT FROM 1
  AND aph.scale_reason IS NOT NULL
  AND aph.source_mapping_id IS NOT NULL;

-- A scaled price is still denominated in the provider quote currency. Repair
-- generated history from its bound mapping rather than from account currency or
-- the canonical asset fallback.
UPDATE investory.asset_price_history aph
SET price_currency = ass.price_currency,
    price_scale_factor = ass.price_scale_factor
FROM investory.asset_source_symbols ass
WHERE aph.source_mapping_id = ass.id
  AND aph.source = 'STOOQ';

-- XTB trade observations are quote-price observations, not cash-ledger rows.
-- Older generated rows copied the account currency; use the canonical asset
-- quote currency for all XTB observations without changing broker ledger data.
UPDATE investory.asset_price_history aph
SET price_currency = asset.currency
FROM investory.assets asset
WHERE aph.asset_id = asset.id
  AND aph.source IN ('XTB_TRADE_OPEN', 'XTB_TRADE_CLOSE', 'INTERPOLATED_XTB');

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
