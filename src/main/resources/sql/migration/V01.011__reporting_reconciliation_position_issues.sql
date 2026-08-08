CREATE OR REPLACE VIEW investory.reporting_reconciliation_position_issues AS
WITH reconstructed AS (
    SELECT
        account_id,
        asset_id,
        valuation_date,
        SUM(COALESCE(open_quantity, 0)) AS open_quantity,
        SUM(COALESCE(reconstructed_cost_base_base, 0)) AS reconstructed_cost_base_base,
        MAX(selected_price_date) AS selected_price_date,
        MAX(underlying_observation_date) AS underlying_observation_date,
        MAX(price_age_days) AS price_age_days,
        STRING_AGG(DISTINCT price_currency, ', ' ORDER BY price_currency) AS price_currency,
        MAX(fx_rate_to_base) AS fx_rate_to_base,
        STRING_AGG(DISTINCT fx_conversion_status, ', ' ORDER BY fx_conversion_status) AS fx_conversion_status,
        STRING_AGG(DISTINCT price_origin, ', ' ORDER BY price_origin) AS price_origin,
        STRING_AGG(DISTINCT price_quality, ', ' ORDER BY price_quality) AS price_quality,
        MAX(selection_priority) AS selection_priority,
        CASE
            WHEN BOOL_OR(reconstruction_status = 'FAIL') THEN 'FAIL'
            WHEN BOOL_OR(reconstruction_status = 'WARN') THEN 'WARN'
            ELSE 'PASS'
        END::varchar(16) AS reconstruction_status,
        STRING_AGG(DISTINCT reconstruction_message, ' | ' ORDER BY reconstruction_message)
            AS reconstruction_message
    FROM investory.v_reconstructed_position_daily
    GROUP BY account_id, asset_id, valuation_date
)
SELECT
    vpv.account_id,
    account.name AS account_name,
    account.provider,
    vpv.asset_id,
    asset.symbol,
    vpv.valuation_date,
    vpv.severity,
    vpv.validation_code,
    CASE
        WHEN vpv.validation_code IN ('MISSING_PRICE', 'MISSING_MULTIPLIER', 'MISSING_FX')
            THEN 'INCOMPLETE_INPUT'
        WHEN vpv.validation_code IN (
            'PRICE_RATIO_100X',
            'TRADE_OBSERVATION_SELECTED',
            'INTERPOLATED_PRICE_SELECTED',
            'ALTERNATE_LISTING_SELECTED'
        ) THEN 'LIKELY_PRICE_OR_SCALE'
        WHEN vpv.validation_code IN (
            'ZERO_QUANTITY_NONZERO_VALUE',
            'NONZERO_QUANTITY_ZERO_PRICE'
        ) THEN 'LIKELY_POSITION_OR_SETTLEMENT'
        ELSE 'REVIEW'
    END::varchar(64) AS suspected_source,
    vpv.expected_value AS selected_price,
    vpv.actual_value AS reconstructed_market_value_base,
    vpv.difference,
    vpv.relative_difference,
    vpv.message,
    rpd.open_quantity,
    rpd.reconstructed_cost_base_base,
    rpd.selected_price_date,
    rpd.underlying_observation_date,
    rpd.price_age_days,
    rpd.price_currency,
    rpd.fx_rate_to_base,
    rpd.fx_conversion_status,
    rpd.price_origin,
    rpd.price_quality,
    rpd.selection_priority,
    rpd.reconstruction_status,
    rpd.reconstruction_message,
    adr.status AS account_reconciliation_status,
    adr.market_value_difference AS account_market_value_difference,
    adr.cash_difference AS account_cash_difference,
    adr.equity_difference AS account_equity_difference
FROM investory.v_position_valuation_validation vpv
JOIN investory.accounts account
  ON account.id = vpv.account_id
JOIN investory.assets asset
  ON asset.id = vpv.asset_id
LEFT JOIN reconstructed rpd
  ON rpd.account_id = vpv.account_id
 AND rpd.asset_id = vpv.asset_id
 AND rpd.valuation_date = vpv.valuation_date
LEFT JOIN investory.v_account_daily_reconciliation adr
  ON adr.account_id = vpv.account_id
 AND adr.valuation_date = vpv.valuation_date;

COMMENT ON VIEW investory.reporting_reconciliation_position_issues IS
    'Read-only position reconciliation diagnostics grouped into the same input, price/scale, and position/settlement categories used by the fake-jump investigation script.';
