-- XTB trade observations, their interpolations, stale carry-forwards, and reviewed
-- alternate listings must carry the currency of the normalized asset price.
UPDATE investory.asset_price_history aph
SET price_currency = a.currency
FROM investory.assets a
WHERE aph.asset_id = a.id
  AND (
      aph.price_origin IN ('XTB_TRADE_OPEN', 'XTB_TRADE_CLOSE', 'INTERPOLATED_XTB', 'STALE_CARRY_FORWARD')
      OR aph.quality_class IN ('XTB_TRADE_OBSERVATION', 'INTERPOLATED_XTB', 'STALE_CARRY_FORWARD')
      OR (aph.quality_class = 'VERIFIED_ALTERNATE_LISTING' AND aph.is_proxy)
      OR EXISTS (
          SELECT 1
          FROM investory.asset_source_symbols ass
          WHERE ass.asset_id = aph.asset_id
            AND ass.source = aph.source
            AND ass.source_symbol = aph.source_symbol
            AND ass.requires_fx_conversion = false
      )
  )
  AND aph.price_currency IS DISTINCT FROM a.currency;

-- Legacy bulk imports already multiplied these close prices. Keep the scale
-- factor at one so reporting does not apply that conversion a second time.
UPDATE investory.asset_price_history
SET price_scale_factor = 1
WHERE scale_reason = 'Normalized Nordic quote currency to USD from trade values'
  AND price_scale_factor IS DISTINCT FROM 1;

COMMENT ON VIEW investory.v_normalized_daily_price IS
    'Price rows are valued as quantity * (close_price * price_scale_factor) in price_currency, then converted once to portfolio base currency.';
