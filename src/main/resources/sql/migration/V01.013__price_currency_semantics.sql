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

COMMENT ON VIEW investory.v_normalized_daily_price IS
    'Price rows are valued as quantity * (close_price * price_scale_factor) in price_currency, then converted once to portfolio base currency.';
