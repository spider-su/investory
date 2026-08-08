-- Price currency contract:
-- * assets.currency is the native currency of the explicit assets.market_price fallback.
-- * asset_price_history.price_currency is the currency of close_price after applying
--   price_scale_factor once.
-- * asset_source_symbols.price_currency is the expected provider quote currency.
-- Valuation converts the selected historical/current price directly to its target;
-- it never normalizes a price through assets.currency first.

-- Rebind provenance after a manual/bulk history load that did not provide the
-- optional mapping id. The trigger in V01.000 applies the same rule for new rows.
UPDATE investory.asset_price_history aph
SET source_mapping_id = ass.id
FROM investory.asset_source_symbols ass
WHERE aph.source_mapping_id IS NULL
  AND ass.asset_id = aph.asset_id
  AND ass.source = aph.source
  AND upper(ass.source_symbol) = upper(aph.source_symbol);

-- Legacy bulk imports already multiplied these close prices. Keep the scale at one
-- so v_current_asset_price and v_normalized_daily_price do not scale twice.
UPDATE investory.asset_price_history
SET price_scale_factor = 1
WHERE scale_reason = 'Normalized Nordic quote currency to USD from trade values'
  AND price_scale_factor IS DISTINCT FROM 1;

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
