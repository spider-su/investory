-- Restore the canonical listing currency used by AssetCatalogService for legacy rows.
-- Qualified symbols without a supported euro-area suffix use USD by application contract.
UPDATE investory.assets
SET currency = CASE
    WHEN upper(symbol) LIKE '%.PL' THEN 'PLN'
    WHEN right(upper(symbol), 3) IN ('.DE', '.FR', '.NL', '.IT', '.ES', '.FI', '.PT', '.IE', '.AT', '.BE') THEN 'EUR'
    ELSE 'USD'
END
WHERE symbol IS NOT NULL
  AND currency IS DISTINCT FROM CASE
    WHEN upper(symbol) LIKE '%.PL' THEN 'PLN'
    WHEN right(upper(symbol), 3) IN ('.DE', '.FR', '.NL', '.IT', '.ES', '.FI', '.PT', '.IE', '.AT', '.BE') THEN 'EUR'
    ELSE 'USD'
END;

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

-- A source marked as not requiring FX must use the asset's normalized currency.
UPDATE investory.asset_source_symbols ass
SET price_currency = a.currency,
    original_currency = a.currency,
    matched_currency = a.currency
FROM investory.assets a
WHERE ass.asset_id = a.id
  AND ass.requires_fx_conversion = false
  AND (
      ass.price_currency IS DISTINCT FROM a.currency
      OR ass.original_currency IS DISTINCT FROM a.currency
      OR ass.matched_currency IS DISTINCT FROM a.currency
  );

COMMENT ON VIEW investory.v_normalized_daily_price IS
    'Price rows are valued as quantity * (close_price * price_scale_factor) in price_currency, then converted once to portfolio base currency.';
