SET search_path TO investory, public;

-- V01.005 was already deployed before its final current-position definition was
-- consolidated. Re-apply the definition so upgraded databases keep holdings
-- visible when current price or FX is unavailable.
CREATE OR REPLACE VIEW investory.app_v_current_open_position_rows AS
SELECT
    pf.id AS portfolio_id,
    pf.base_currency::varchar(3) AS base_currency,
    a.id AS account_id,
    a.currency::varchar(3) AS account_currency,
    asset.id AS asset_id,
    asset.symbol AS asset_symbol,
    p.id AS position_id,
    p.cost_currency::varchar(3) AS cost_basis_currency,
    investory.signed_position_quantity(p.operation, p.volume) AS volume,
    COALESCE(p.purchase_value, p.volume * p.open_price, 0) AS cost_basis_native,
    price.selected_price AS market_price,
    price.price_currency::varchar(3) AS market_price_currency,
    price.price_selection_source,
    price.selected_price_date,
    price.price_source,
    price.source_symbol AS market_price_source_symbol,
    cost_fx.fx_rate_to_base AS cost_basis_to_base_rate,
    cost_fx.conversion_status AS cost_basis_fx_status,
    market_fx.fx_rate_to_base AS market_price_to_base_rate,
    market_fx.conversion_status AS market_price_fx_status,
    CASE
        WHEN investory.fx_status_usable(cost_fx.conversion_status)
            THEN COALESCE(p.purchase_value, p.volume * p.open_price, 0) * cost_fx.fx_rate_to_base
        ELSE NULL::numeric
    END AS cost_basis_in_base_currency,
    CASE
        WHEN price.selected_price IS NOT NULL
         AND investory.fx_status_usable(market_fx.conversion_status)
            THEN investory.signed_position_quantity(p.operation, p.volume)
                 * price.selected_price
                 * CASE WHEN price.quality_class LIKE '%PERCENT_OF_PAR%' THEN 0.01::numeric
                        ELSE 1::numeric END
                 * market_fx.fx_rate_to_base
        ELSE NULL::numeric
    END AS market_value_in_base_currency
FROM investory.positions p
JOIN investory.accounts a ON a.id = p.account_id
JOIN investory.portfolios pf ON pf.id = a.portfolio_id
JOIN investory.assets asset ON asset.id = p.asset_id
LEFT JOIN investory.app_v_current_asset_price_mv price ON price.asset_id = asset.id
LEFT JOIN investory.app_v_portfolio_daily_fx_rate_mv cost_fx
  ON cost_fx.portfolio_id = pf.id
 AND cost_fx.valuation_date = CURRENT_DATE
 AND cost_fx.source_currency = p.cost_currency::varchar(3)
LEFT JOIN investory.app_v_portfolio_daily_fx_rate_mv market_fx
  ON market_fx.portfolio_id = pf.id
 AND market_fx.valuation_date = CURRENT_DATE
 AND market_fx.source_currency = price.price_currency::varchar(3)
WHERE p.close_time IS NULL
  AND asset.exclude_from_import = false
  AND COALESCE(p.volume, 0) > 0;

COMMENT ON VIEW investory.app_v_current_open_position_rows IS
    'Shared current open-position valuation rows. Uses current valuation FX; stale or missing FX yields null converted values.';
