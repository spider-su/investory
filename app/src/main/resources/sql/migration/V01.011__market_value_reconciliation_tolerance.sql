INSERT INTO investory.reconciliation_parameters(parameter_name, numeric_value, description)
VALUES
    ('reconciliation_market_value_absolute_tolerance', 400,
     'Absolute tolerance for daily account market-value reconciliation.'),
    ('reconciliation_market_value_relative_tolerance', 0.02,
     'Relative tolerance for daily account market-value reconciliation.'),
    ('reconciliation_cash_absolute_tolerance', 0.99,
     'Absolute tolerance for daily account cash reconciliation.')
ON CONFLICT (parameter_name) DO UPDATE
SET numeric_value = EXCLUDED.numeric_value,
    description = EXCLUDED.description;

CREATE OR REPLACE VIEW investory.recon_v_account_daily_diagnostic AS
WITH raw_cash AS (
    SELECT
        ad.account_id,
        ad.snapshot_date AS valuation_date,
        ad.cash_balance AS reported_cash_balance_raw,
        SUM(rc.reconstructed_cash_component_base) AS reconstructed_cash_balance_raw
    FROM investory.account_daily ad
    LEFT JOIN investory.recon_v_reconstructed_cash_daily_mv rc
      ON rc.account_id = ad.account_id
     AND rc.valuation_date = ad.snapshot_date
    GROUP BY ad.account_id, ad.snapshot_date, ad.cash_balance
), classified AS (
    SELECT
        r.*,
        c.reported_cash_balance_raw,
        c.reconstructed_cash_balance_raw,
        ABS(r.market_value_difference) <= GREATEST(
            investory.reconciliation_parameter('reconciliation_market_value_absolute_tolerance'),
            investory.reconciliation_parameter('reconciliation_market_value_relative_tolerance')
                * GREATEST(ABS(r.reported_market_value), ABS(r.reconstructed_market_value))
        ) AS market_value_within_tolerance,
        c.reported_cash_balance_raw IS NOT NULL
        AND c.reconstructed_cash_balance_raw IS NOT NULL
        AND ABS(c.reported_cash_balance_raw - c.reconstructed_cash_balance_raw) <= GREATEST(
            investory.reconciliation_parameter('reconciliation_cash_absolute_tolerance'),
            investory.reconciliation_parameter('reconciliation_relative_tolerance')
                * GREATEST(ABS(c.reported_cash_balance_raw), ABS(c.reconstructed_cash_balance_raw))
        ) AS cash_within_tolerance,
        EXISTS (
            SELECT 1
            FROM investory.app_v_reconstructed_position_daily p
            WHERE p.account_id = r.account_id
              AND p.valuation_date = r.valuation_date
              AND p.reconstruction_status = 'WARN'
        ) AS has_lower_quality_valuation
    FROM investory.recon_v_account_daily r
    LEFT JOIN raw_cash c
      ON c.account_id = r.account_id
     AND c.valuation_date = r.valuation_date
)
SELECT
    r.account_id,
    r.valuation_date,
    r.reported_cash_balance,
    r.reconstructed_cash_balance,
    r.cash_difference,
    r.reported_market_value,
    r.reconstructed_market_value,
    r.market_value_difference,
    r.reported_cost_base,
    r.reconstructed_cost_base,
    r.cost_base_difference,
    r.reported_unrealized_profit,
    r.reconstructed_unrealized_profit,
    r.unrealized_difference,
    r.reported_equity,
    r.reconstructed_equity,
    r.equity_difference,
    r.reported_realized_profit,
    r.reconstructed_total_realized_result,
    r.realized_difference,
    r.market_value_effective_tolerance,
    r.cash_effective_tolerance,
    r.equity_effective_tolerance,
    r.cost_base_effective_tolerance,
    r.unrealized_effective_tolerance,
    r.realized_effective_tolerance,
    CASE
        WHEN r.validation_message = 'market value mismatch'
             AND r.market_value_within_tolerance
             AND r.has_lower_quality_valuation THEN 'WARN'
        WHEN r.validation_message = 'market value mismatch'
             AND r.market_value_within_tolerance THEN 'PASS'
        WHEN r.validation_message = 'cash mismatch'
             AND r.cash_within_tolerance
             AND r.has_lower_quality_valuation THEN 'WARN'
        WHEN r.validation_message = 'cash mismatch'
             AND r.cash_within_tolerance THEN 'PASS'
        ELSE r.status
    END::varchar(16) AS status,
    CASE
        WHEN r.validation_message = 'market value mismatch'
             AND r.market_value_within_tolerance
             AND r.has_lower_quality_valuation THEN 'WARN'
        WHEN r.validation_message = 'market value mismatch'
             AND r.market_value_within_tolerance THEN 'INFO'
        WHEN r.validation_message = 'cash mismatch'
             AND r.cash_within_tolerance
             AND r.has_lower_quality_valuation THEN 'WARN'
        WHEN r.validation_message = 'cash mismatch'
             AND r.cash_within_tolerance THEN 'INFO'
        ELSE r.severity
    END::varchar(16) AS severity,
    CASE
        WHEN r.validation_message = 'market value mismatch'
             AND r.market_value_within_tolerance
             AND r.has_lower_quality_valuation THEN 'valuation used lower-quality price source'
        WHEN r.validation_message = 'market value mismatch'
             AND r.market_value_within_tolerance THEN 'reconciliation passed'
        WHEN r.validation_message = 'cash mismatch'
             AND r.cash_within_tolerance
             AND r.has_lower_quality_valuation THEN 'valuation used lower-quality price source'
        WHEN r.validation_message = 'cash mismatch'
             AND r.cash_within_tolerance THEN 'reconciliation passed'
        ELSE r.validation_message
    END::text AS validation_message,
    CASE
        WHEN r.validation_message IN ('cash mismatch', 'cash reconstruction failed')
            THEN 'ACCOUNT_DAILY_CASH_RECONCILIATION'
        WHEN r.validation_message IN ('market value mismatch', 'position reconstruction failed',
                                      'valuation used lower-quality price source')
            THEN 'ACCOUNT_DAILY_MARKET_VALUE_RECONCILIATION'
        WHEN r.validation_message = 'cost base mismatch' THEN 'ACCOUNT_DAILY_COST_BASE_RECONCILIATION'
        WHEN r.validation_message = 'unrealized mismatch' THEN 'ACCOUNT_DAILY_UNREALIZED_RECONCILIATION'
        WHEN r.validation_message IN ('realized result mismatch', 'realized result reconstruction failed')
            THEN 'ACCOUNT_DAILY_REALIZED_RECONCILIATION'
        WHEN r.validation_message = 'equity mismatch' THEN 'ACCOUNT_DAILY_EQUITY_RECONCILIATION'
        ELSE 'UNKNOWN'
    END::varchar(96) AS diagnostic_code
FROM classified r;

COMMENT ON VIEW investory.recon_v_account_daily_diagnostic IS
    'Stable C4 diagnostic projection using dedicated daily market-value tolerance; cash and other component tolerances remain unchanged.';

CREATE OR REPLACE VIEW investory.recon_v_reporting_validation_summary AS
WITH price_summary AS (
    SELECT
        rpd.valuation_date,
        rpd.account_id,
        COUNT(*) AS total_positions,
        COUNT(*) FILTER (WHERE rpd.price_quality = 'EXACT_LISTING_MARKET_CLOSE') AS exact_prices,
        COUNT(*) FILTER (WHERE rpd.price_quality LIKE '%ALTERNATE%' OR rpd.price_quality LIKE '%PROXY%') AS alternate_listing_prices,
        COUNT(*) FILTER (WHERE rpd.price_quality LIKE '%PROXY%') AS proxy_prices,
        COUNT(*) FILTER (WHERE rpd.price_quality LIKE 'INTERPOLATED%') AS interpolated_prices,
        COUNT(*) FILTER (WHERE rpd.price_quality LIKE '%TRADE_OBSERVATION%') AS trade_observation_prices,
        COUNT(*) FILTER (WHERE rpd.selected_price IS NULL) AS missing_prices,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(rpd.fx_conversion_status)) AS missing_fx_rates,
        COUNT(*) FILTER (WHERE rpd.contract_multiplier IS NULL) AS missing_multipliers,
        COUNT(*) FILTER (WHERE rpd.open_quantity = 0 AND COALESCE(rpd.reconstructed_market_value_base, 0) <> 0) AS residual_positions
    FROM investory.app_v_reconstructed_position_daily rpd
    GROUP BY rpd.valuation_date, rpd.account_id
), validation_counts AS (
    SELECT
        vpv.valuation_date,
        vpv.account_id,
        COUNT(*) FILTER (WHERE vpv.validation_code IN ('PRICE_RATIO_100X')) AS price_anomalies
    FROM investory.recon_v_position_valuation_validation vpv
    GROUP BY vpv.valuation_date, vpv.account_id
), recon AS (
    SELECT
        adr.valuation_date,
        adr.account_id,
        COUNT(*) FILTER (WHERE adr.status = 'FAIL') AS reconciliation_failures,
        MAX(ABS(adr.market_value_difference)) AS maximum_market_value_difference,
        MAX(ABS(adr.equity_difference)) AS maximum_equity_difference,
        MAX(adr.status) AS status_hint
    FROM investory.recon_v_account_daily_diagnostic adr
    GROUP BY adr.valuation_date, adr.account_id
)
SELECT
    ps.valuation_date,
    ps.account_id,
    ps.total_positions,
    ps.exact_prices,
    ps.alternate_listing_prices,
    ps.proxy_prices,
    ps.interpolated_prices,
    ps.trade_observation_prices,
    ps.missing_prices,
    ps.missing_fx_rates,
    ps.missing_multipliers,
    ps.residual_positions,
    COALESCE(vc.price_anomalies, 0) AS price_anomalies,
    COALESCE(r.reconciliation_failures, 0) AS reconciliation_failures,
    COALESCE(r.maximum_market_value_difference, 0) AS maximum_market_value_difference,
    COALESCE(r.maximum_equity_difference, 0) AS maximum_equity_difference,
    CASE
        WHEN ps.missing_prices > 0
          OR ps.missing_fx_rates > 0
          OR ps.missing_multipliers > 0
          OR ps.residual_positions > 0
          OR COALESCE(r.reconciliation_failures, 0) > 0 THEN 'FAIL'
        WHEN ps.interpolated_prices > 0
          OR ps.trade_observation_prices > 0
          OR ps.alternate_listing_prices > 0
          OR COALESCE(vc.price_anomalies, 0) > 0 THEN 'WARN'
        ELSE 'PASS'
    END::varchar(16) AS status
FROM price_summary ps
LEFT JOIN validation_counts vc
    ON vc.valuation_date = ps.valuation_date
   AND vc.account_id = ps.account_id
LEFT JOIN recon r
    ON r.valuation_date = ps.valuation_date
   AND r.account_id = ps.account_id;

COMMENT ON VIEW investory.recon_v_reporting_validation_summary IS
    'High-level reporting validation using the effective C4 diagnostic status and dedicated market/cash tolerances.';
