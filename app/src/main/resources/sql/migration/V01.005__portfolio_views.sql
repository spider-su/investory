SET search_path TO investory, public;

-- Currency semantics used by all reporting:
-- accounts.currency  = cash-account denomination.
-- assets.currency    = instrument listing/quote denomination.
-- positions uses explicit price, cost, profit, and commission currencies.
-- No position monetary currency is inferred from accounts.currency.
COMMENT ON COLUMN investory.accounts.currency IS
    'Cash-account currency: denomination of the account cash balance and account-native cash flows. Not the position trade or quote currency.';
COMMENT ON COLUMN investory.assets.currency IS
    'Instrument quote/listing currency: denomination of the asset market price and listing. Not necessarily the broker trade-value currency.';





CREATE MATERIALIZED VIEW IF NOT EXISTS investory.app_v_canonical_asset_daily_price_mv AS
SELECT DISTINCT ON (aph.asset_id, aph.price_date)
    aph.asset_id,
    aph.price_date,
    aph.source,
    aph.source_symbol,
    aph.price_origin,
    aph.price_currency,
    aph.source_mapping_id,
    aph.interpolation_method,
    aph.interpolation_left_date,
    aph.interpolation_right_date,
    aph.open_price,
    aph.high_price,
    aph.low_price,
    aph.close_price,
    aph.adjusted_close_price,
    aph.volume,
    aph.estimated,
    aph.quality_score,
    aph.quality_class,
    aph.is_observed,
    aph.is_proxy,
    aph.price_scale_factor,
    aph.scale_reason,
    aph.original_source_symbol,
    aph.source_date,
    aph.imported_at
FROM investory.asset_price_history aph
JOIN investory.assets asset
  ON asset.id = aph.asset_id
 AND asset.exclude_from_import = false
ORDER BY
    aph.asset_id,
    aph.price_date,
    aph.quality_score DESC,
    aph.is_observed DESC,
    aph.is_proxy ASC,
    CASE aph.price_origin
        WHEN 'XTB_TRADE_OPEN' THEN 0
        WHEN 'XTB_TRADE_CLOSE' THEN 2
        ELSE 1
    END,
    aph.imported_at DESC,
    aph.source
WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS ux_app_v_canonical_asset_daily_price_mv_key
    ON investory.app_v_canonical_asset_daily_price_mv(asset_id, price_date);

CREATE OR REPLACE VIEW investory.app_v_canonical_asset_daily_price AS
SELECT asset_id, price_date, source, source_symbol, price_origin, price_currency,
       source_mapping_id, interpolation_method, interpolation_left_date,
       interpolation_right_date, open_price, high_price, low_price, close_price,
       adjusted_close_price, volume, estimated, quality_score, quality_class,
       is_observed, is_proxy, price_scale_factor, scale_reason,
       original_source_symbol, source_date, imported_at
FROM investory.app_v_canonical_asset_daily_price_mv;

CREATE MATERIALIZED VIEW IF NOT EXISTS investory.app_v_canonical_asset_daily_price_ranked_mv AS
SELECT cp.*,
       CASE
           WHEN cp.estimated AND cp.interpolation_left_date IS NOT NULL
               THEN cp.interpolation_left_date
           ELSE COALESCE(cp.source_date, cp.price_date)
       END AS effective_observation_date,
       CASE
           WHEN cp.quality_class = 'EXACT_LISTING_MARKET_CLOSE' THEN 1
           WHEN cp.quality_class = 'EXACT_LISTING_SCALED' THEN 2
           WHEN cp.quality_class LIKE '%ALTERNATE%' OR cp.is_proxy THEN 3
           WHEN cp.price_origin = 'MANUAL' THEN 4
           WHEN cp.estimated OR cp.quality_class LIKE 'INTERPOLATED%' THEN 5
           WHEN cp.quality_class LIKE '%TRADE_OBSERVATION%' OR cp.price_origin LIKE '%TRADE%' THEN 6
           WHEN cp.quality_class LIKE '%STALE%' OR cp.price_origin = 'STALE_CARRY_FORWARD' THEN 7
           ELSE 9
       END AS selection_priority
FROM investory.app_v_canonical_asset_daily_price_mv cp
WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS ux_app_v_canonical_asset_daily_price_ranked_mv_key
    ON investory.app_v_canonical_asset_daily_price_ranked_mv(asset_id, price_date);
CREATE INDEX IF NOT EXISTS ix_app_v_canonical_asset_daily_price_ranked_mv_order
    ON investory.app_v_canonical_asset_daily_price_ranked_mv(
        asset_id, effective_observation_date DESC, selection_priority,
        quality_score DESC, price_date DESC, source, source_symbol);
ANALYZE investory.app_v_canonical_asset_daily_price_ranked_mv;

CREATE OR REPLACE VIEW investory.app_v_canonical_asset_daily_return AS
WITH canonical_prices AS (
    SELECT
        cp.asset_id,
        cp.price_date,
        COALESCE(cp.adjusted_close_price, cp.close_price)
            * COALESCE(cp.price_scale_factor, 1) AS return_price
    FROM investory.app_v_canonical_asset_daily_price cp
    WHERE COALESCE(cp.adjusted_close_price, cp.close_price) > 0
), with_previous AS (
    SELECT
        asset_id,
        price_date,
        return_price,
        LAG(return_price) OVER (
            PARTITION BY asset_id ORDER BY price_date
        ) AS previous_return_price
    FROM canonical_prices
)
SELECT
    asset_id,
    price_date,
    return_price,
    CASE
        WHEN previous_return_price > 0
            THEN return_price / previous_return_price - 1
        ELSE NULL::numeric
    END AS daily_return_pct
FROM with_previous;

COMMENT ON VIEW investory.app_v_canonical_asset_daily_return IS
    'Canonical return basis: provider adjusted_close_price when present, otherwise close_price, with the source price_scale_factor applied once. Position valuation uses raw close_price and broker-reported quantity; corporate-action quantity changes must be represented by the position source.';

CREATE MATERIALIZED VIEW IF NOT EXISTS investory.app_v_current_asset_price_mv AS
WITH latest_observed_price AS (
    SELECT DISTINCT ON (cp.asset_id)
        cp.asset_id, cp.price_date, cp.source, cp.source_symbol,
        cp.source_mapping_id, cp.price_origin, cp.price_currency,
        cp.close_price * cp.price_scale_factor AS selected_price,
        cp.quality_score, cp.quality_class, cp.is_proxy, cp.source_date,
        cp.imported_at
    FROM investory.app_v_canonical_asset_daily_price cp
    WHERE cp.is_observed AND cp.estimated = false AND cp.close_price > 0
      AND cp.price_origin <> 'STALE_CARRY_FORWARD'
      AND COALESCE(cp.source_date, cp.price_date) >= CURRENT_DATE - 10
      AND COALESCE(cp.source_date, cp.price_date) <= CURRENT_DATE
    ORDER BY cp.asset_id, COALESCE(cp.source_date, cp.price_date) DESC,
             CASE
                 WHEN cp.quality_class = 'EXACT_LISTING_MARKET_CLOSE' THEN 1
                 WHEN cp.quality_class = 'EXACT_LISTING_SCALED' THEN 2
                 WHEN cp.quality_class LIKE '%ALTERNATE%' OR cp.is_proxy THEN 3
                 WHEN cp.price_origin = 'MANUAL' THEN 4
                 WHEN cp.estimated OR cp.quality_class LIKE 'INTERPOLATED%' THEN 5
                 WHEN cp.quality_class LIKE '%TRADE_OBSERVATION%' OR cp.price_origin LIKE '%TRADE%' THEN 6
                 WHEN cp.quality_class LIKE '%STALE%' OR cp.price_origin = 'STALE_CARRY_FORWARD' THEN 7
                 ELSE 9
             END, cp.quality_score DESC, cp.price_date DESC,
             cp.imported_at DESC, cp.source
)
SELECT a.id AS asset_id,
       COALESCE(lp.selected_price, a.market_price) AS selected_price,
       CASE WHEN lp.asset_id IS NOT NULL THEN lp.price_currency ELSE a.currency::varchar(3) END AS price_currency,
       CASE WHEN lp.asset_id IS NOT NULL THEN 'HISTORICAL' ELSE 'ASSET_CURRENT_FALLBACK' END::varchar(32) AS price_selection_source,
       lp.price_date AS selected_price_date, lp.source_date AS underlying_observation_date,
       lp.source AS price_source, lp.source_symbol, lp.source_mapping_id,
       lp.price_origin, lp.quality_score, lp.quality_class, lp.is_proxy, lp.imported_at
FROM investory.assets a
LEFT JOIN latest_observed_price lp ON lp.asset_id = a.id
WHERE a.exclude_from_import = false
WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS ux_app_v_current_asset_price_mv_asset
    ON investory.app_v_current_asset_price_mv(asset_id);

CREATE OR REPLACE VIEW investory.app_v_current_asset_price AS
SELECT asset_id, selected_price, price_currency, price_selection_source,
       selected_price_date, underlying_observation_date, price_source,
       source_symbol, source_mapping_id, price_origin, quality_score,
       quality_class, is_proxy, imported_at
FROM investory.app_v_current_asset_price_mv;

COMMENT ON VIEW investory.app_v_current_asset_price IS
    'Authoritative current price selection: latest observed canonical historical price whose effective source observation is no older than ten days (scaled once), then assets.market_price in assets.currency, otherwise unavailable. Price and currency always come from the same source.';

CREATE MATERIALIZED VIEW IF NOT EXISTS investory.app_v_portfolio_daily_fx_rate_mv AS
WITH portfolio_dates AS (
    SELECT DISTINCT a.portfolio_id, ad.snapshot_date AS valuation_date
    FROM investory.account_daily ad JOIN investory.accounts a ON a.id = ad.account_id
    UNION
    SELECT DISTINCT a.portfolio_id, co.date::date
    FROM investory.cash_operations co JOIN investory.accounts a ON a.id = co.account_id
    UNION
    SELECT id, CURRENT_DATE FROM investory.portfolios
)
SELECT resolved.portfolio_id, resolved.valuation_date, resolved.source_currency,
       resolved.base_currency, resolved.fx_rate_to_base, resolved.source,
       resolved.rate_method, resolved.rate_source, resolved.source_rate_date,
       resolved.age_days, resolved.conversion_status
FROM portfolio_dates dates
CROSS JOIN investory.currencies currencies
CROSS JOIN LATERAL investory.resolve_portfolio_fx_rate(
    dates.portfolio_id, dates.valuation_date, currencies.id::varchar(3)) resolved
WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS ux_app_v_portfolio_daily_fx_rate_mv_key
    ON investory.app_v_portfolio_daily_fx_rate_mv(portfolio_id, valuation_date, source_currency);
CREATE INDEX IF NOT EXISTS ix_app_v_portfolio_daily_fx_rate_mv_date
    ON investory.app_v_portfolio_daily_fx_rate_mv(valuation_date);

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
JOIN investory.accounts a
    ON a.id = p.account_id
JOIN investory.portfolios pf
    ON pf.id = a.portfolio_id
JOIN investory.assets asset
    ON asset.id = p.asset_id
LEFT JOIN investory.app_v_current_asset_price_mv price
    ON price.asset_id = asset.id
LEFT JOIN investory.app_v_portfolio_daily_fx_rate_mv cost_fx
  ON cost_fx.portfolio_id = pf.id
 AND cost_fx.valuation_date = COALESCE(p.open_time::date, CURRENT_DATE)
 AND cost_fx.source_currency = p.cost_currency::varchar(3)
LEFT JOIN investory.app_v_portfolio_daily_fx_rate_mv market_fx
  ON market_fx.portfolio_id = pf.id
 AND market_fx.valuation_date = CURRENT_DATE
 AND market_fx.source_currency = price.price_currency::varchar(3)
WHERE p.close_time IS NULL
  AND asset.exclude_from_import = false
  AND COALESCE(p.volume, 0) > 0;

COMMENT ON VIEW investory.app_v_current_open_position_rows IS
    'Shared current open-position valuation rows. Uses app_v_current_asset_price and resolve_fx_rate(CURRENT_DATE, ...); stale or missing FX yields null converted values.';

CREATE MATERIALIZED VIEW IF NOT EXISTS investory.app_v_normalized_cash_operations AS
WITH classified AS (
    SELECT
        co.id AS operation_id,
        co.account_id,
        a.portfolio_id,
        a.currency::varchar(3) AS account_currency,
        co.currency::varchar(3) AS currency,
        p.base_currency::varchar(3) AS base_currency,
        co.operation::varchar(64) AS raw_operation,
        co.asset_id,
        co.amount,
        co.comment,
        co.date,
        co.execution_fx_base,
        co.execution_fx_to_currency,
        co.execution_fx_rate,
        co.execution_fx_observed_at,
        co.execution_fx_source,
        -- Imported timestamps are assigned to the household/reporting calendar in Europe/Warsaw.
        date_trunc('month', co.date AT TIME ZONE 'Europe/Warsaw')::date AS rate_month,
        CASE
            WHEN co.operation = 'DEPOSIT'
             AND lower(COALESCE(co.comment, '')) LIKE 'transfer out operation on account%'
                THEN 'INTERNAL_TRANSFER_OUT'
            WHEN co.operation = 'DEPOSIT'
             AND lower(COALESCE(co.comment, '')) LIKE 'transfer in operation on account%'
                THEN 'INTERNAL_TRANSFER_IN'
            WHEN co.operation = 'DEPOSIT'
             AND COALESCE(co.amount, 0) = 0
                THEN 'UNCLASSIFIED'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) ~* '(full call|early redemption|redemption)'
             AND lower(COALESCE(co.comment, '')) LIKE '%per bond%'
             AND co.asset_id IS NOT NULL
                THEN 'BOND_REDEMPTION'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) IN ('cash transfer')
             AND co.amount > 0
                THEN 'EXTERNAL_DEPOSIT'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) LIKE 'cash transfer |%'
             AND co.amount > 0
                THEN 'EXTERNAL_DEPOSIT'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) LIKE 'cash transfer|%'
             AND co.amount > 0
                THEN 'EXTERNAL_DEPOSIT'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) IN ('cash transfer')
             AND co.amount < 0
                THEN 'EXTERNAL_WITHDRAWAL'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) LIKE 'cash transfer |%'
             AND co.amount < 0
                THEN 'EXTERNAL_WITHDRAWAL'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) LIKE 'cash transfer|%'
             AND co.amount < 0
                THEN 'EXTERNAL_WITHDRAWAL'
            WHEN co.operation = 'TRANSFER'
             AND (
                 lower(COALESCE(co.comment, '')) LIKE 'net amount in base from forex trade:%'
                 OR lower(COALESCE(co.comment, '')) LIKE '%ibkrrawtype=forex trade component%'
             )
                THEN 'FX_CONVERSION'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) LIKE 'currency conversion,%from ta:%to:%'
             AND co.amount >= 0
                THEN 'INTERNAL_TRANSFER_IN'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) LIKE 'currency conversion,%from ta:%to:%'
             AND co.amount < 0
                THEN 'INTERNAL_TRANSFER_OUT'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) LIKE 'currency conversion,%'
                THEN 'FX_CONVERSION'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) LIKE 'transfer from % to %'
             AND co.amount >= 0
                THEN 'INTERNAL_TRANSFER_IN'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) LIKE 'transfer from % to %'
             AND co.amount < 0
                THEN 'INTERNAL_TRANSFER_OUT'
            WHEN co.operation = 'SUBACCOUNT_TRANSFER'
                THEN 'INTERNAL_BOOKKEEPING'
            WHEN co.operation = 'DIVIDEND' AND co.amount < 0
                THEN 'DIVIDEND_REVERSAL'
            WHEN co.operation = 'DIVIDEND'
                THEN 'DIVIDEND'
            WHEN co.operation = 'FREE_FUNDS_INTEREST' AND co.amount < 0
                THEN 'INTEREST_REVERSAL'
            WHEN co.operation = 'FREE_FUNDS_INTEREST'
                THEN 'INTEREST'
            WHEN co.operation = 'WITHHOLDING_TAX' AND co.amount > 0
                THEN 'WITHHOLDING_TAX_REVERSAL'
            WHEN co.operation = 'WITHHOLDING_TAX'
                THEN 'WITHHOLDING_TAX'
            WHEN co.operation IN ('FREE_FUNDS_INTEREST_TAX', 'TRANSACTION_TAX', 'STAMP_DUTY')
                THEN 'OTHER_TAX'
            WHEN co.operation IN ('COMMISSION', 'SEC_FEE', 'SWAP')
                THEN 'FEE'
            WHEN co.operation = 'STOCK_PURCHASE'
                THEN 'TRADE_PURCHASE'
            WHEN co.operation = 'STOCK_SELL'
                THEN 'TRADE_SALE'
            WHEN co.operation IN ('CLOSE_TRADE', 'ROLLOVER')
                THEN 'REALIZED_TRADE_RESULT'
            WHEN co.operation = 'CORRECTION'
                THEN 'CORRECTION'
            WHEN co.operation = 'DEPOSIT' AND co.amount > 0
                THEN 'EXTERNAL_DEPOSIT'
            WHEN co.operation = 'WITHDRAWAL' AND co.amount < 0
                THEN 'EXTERNAL_WITHDRAWAL'
            ELSE 'UNCLASSIFIED'
        END::varchar(64) AS normalized_category,
        CASE
            WHEN co.amount > 0 THEN 'INFLOW'
            WHEN co.amount < 0 THEN 'OUTFLOW'
            ELSE 'NEUTRAL'
        END::varchar(16) AS economic_direction,
        CASE
            WHEN co.operation = 'DEPOSIT'
             AND lower(COALESCE(co.comment, '')) LIKE 'transfer out operation on account%'
                THEN 'XTB_ACCOUNT_TRANSFER_OUT'
            WHEN co.operation = 'DEPOSIT'
             AND lower(COALESCE(co.comment, '')) LIKE 'transfer in operation on account%'
                THEN 'XTB_ACCOUNT_TRANSFER_IN'
            WHEN co.operation = 'DEPOSIT' AND COALESCE(co.amount, 0) = 0
                THEN 'ZERO_DEPOSIT'
            WHEN co.operation = 'DEPOSIT' AND co.amount < 0
                THEN 'RAW_DEPOSIT_NEGATIVE_REVIEW'
            WHEN co.operation = 'DEPOSIT' AND co.amount > 0
                THEN 'RAW_DEPOSIT'
            WHEN co.operation = 'WITHDRAWAL' AND co.amount < 0
                THEN 'RAW_WITHDRAWAL'
            WHEN co.operation = 'WITHDRAWAL' AND co.amount > 0
                THEN 'RAW_WITHDRAWAL_POSITIVE_REVIEW'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) ~* '(full call|early redemption|redemption)'
             AND lower(COALESCE(co.comment, '')) LIKE '%per bond%'
             AND co.asset_id IS NOT NULL
                THEN 'IBKR_BOND_REDEMPTION'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) LIKE 'cash transfer%'
             AND co.amount > 0
                THEN 'EXTERNAL_DEPOSIT'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) LIKE 'cash transfer%'
             AND co.amount < 0
                THEN 'EXTERNAL_WITHDRAWAL'
            WHEN co.operation = 'TRANSFER'
             AND (
                 lower(COALESCE(co.comment, '')) LIKE 'net amount in base from forex trade:%'
                 OR lower(COALESCE(co.comment, '')) LIKE '%ibkrrawtype=forex trade component%'
             )
                THEN 'FX_CONVERSION'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) LIKE 'currency conversion,%from ta:%to:%'
                THEN 'ACCOUNT_TRANSFER'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) LIKE 'currency conversion,%'
                THEN 'FX_CONVERSION'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) LIKE 'transfer from % to %'
                THEN 'ACCOUNT_TRANSFER'
            WHEN co.operation = 'SUBACCOUNT_TRANSFER'
                THEN 'SUBACCOUNT_TRANSFER'
            WHEN co.operation = 'DIVIDEND' AND co.amount < 0
                THEN 'DIVIDEND_REVERSAL'
            WHEN co.operation = 'DIVIDEND'
                THEN 'DIVIDEND'
            WHEN co.operation = 'FREE_FUNDS_INTEREST' AND co.amount < 0
                THEN 'INTEREST_REVERSAL'
            WHEN co.operation = 'FREE_FUNDS_INTEREST'
                THEN 'INTEREST'
            WHEN co.operation = 'WITHHOLDING_TAX' AND co.amount > 0
                THEN 'WITHHOLDING_TAX_REVERSAL'
            WHEN co.operation = 'WITHHOLDING_TAX'
                THEN 'WITHHOLDING_TAX'
            WHEN co.operation IN ('FREE_FUNDS_INTEREST_TAX', 'TRANSACTION_TAX', 'STAMP_DUTY')
                THEN 'TAX'
            WHEN co.operation IN ('COMMISSION', 'SEC_FEE', 'SWAP')
                THEN 'FEE'
            WHEN co.operation = 'STOCK_PURCHASE'
                THEN 'TRADE_PURCHASE'
            WHEN co.operation = 'STOCK_SELL'
                THEN 'TRADE_SALE'
            WHEN co.operation IN ('CLOSE_TRADE', 'ROLLOVER')
                THEN 'REALIZED_TRADE_RESULT'
            WHEN co.operation = 'CORRECTION'
                THEN 'UNCLASSIFIED_CORRECTION'
            ELSE 'UNCLASSIFIED'
        END::varchar(64) AS normalized_subtype,
        CASE
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) ~* '(full call|early redemption|redemption)'
             AND lower(COALESCE(co.comment, '')) LIKE '%per bond%'
             AND co.asset_id IS NOT NULL
                THEN 'IBKR fixed-income redemption settlement'
            WHEN co.operation = 'DEPOSIT'
             AND lower(COALESCE(co.comment, '')) LIKE 'transfer out operation on account%'
                THEN 'explicit transfer out comment'
            WHEN co.operation = 'DEPOSIT'
             AND lower(COALESCE(co.comment, '')) LIKE 'transfer in operation on account%'
                THEN 'explicit transfer in comment'
            WHEN co.operation = 'DEPOSIT' AND COALESCE(co.amount, 0) = 0
                THEN 'zero raw deposit requires review'
            WHEN co.operation = 'DEPOSIT' AND co.amount < 0
                THEN 'negative raw deposit requires review'
            WHEN co.operation = 'WITHDRAWAL' AND co.amount > 0
                THEN 'positive raw withdrawal requires review'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) = 'cash transfer'
                THEN 'explicit IBKR cash transfer comment'
            WHEN co.operation = 'TRANSFER'
             AND (
                 lower(COALESCE(co.comment, '')) LIKE 'net amount in base from forex trade:%'
                 OR lower(COALESCE(co.comment, '')) LIKE '%ibkrrawtype=forex trade component%'
             )
                THEN 'explicit IBKR forex trade component comment'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) LIKE 'currency conversion,%'
                THEN 'currency conversion comment'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) LIKE 'transfer from % to %'
                THEN 'transfer from X to Y comment'
            WHEN co.operation = 'SUBACCOUNT_TRANSFER'
                THEN 'raw subaccount transfer'
            WHEN co.operation = 'DIVIDEND' AND co.amount < 0
                THEN 'negative/correction dividend'
            WHEN co.operation = 'FREE_FUNDS_INTEREST' AND co.amount < 0
                THEN 'negative/correction interest'
            WHEN co.operation = 'WITHHOLDING_TAX' AND co.amount > 0
                THEN 'positive/correction withholding tax'
            WHEN co.operation = 'CORRECTION'
                THEN 'raw correction requires review'
            ELSE 'raw operation mapping'
        END::varchar(255) AS classification_reason,
        CASE
            WHEN co.operation = 'DEPOSIT'
             AND lower(COALESCE(co.comment, '')) LIKE 'transfer out operation on account%'
                THEN false
            WHEN co.operation = 'DEPOSIT'
             AND lower(COALESCE(co.comment, '')) LIKE 'transfer in operation on account%'
                THEN false
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) LIKE 'cash transfer%'
                THEN true
            WHEN co.operation = 'DEPOSIT' AND co.amount > 0
                THEN true
            WHEN co.operation = 'WITHDRAWAL' AND co.amount < 0
                THEN true
            ELSE false
        END AS is_external_flow,
        CASE
            WHEN co.operation = 'DEPOSIT'
             AND lower(COALESCE(co.comment, '')) LIKE 'transfer out operation on account%'
                THEN true
            WHEN co.operation = 'DEPOSIT'
             AND lower(COALESCE(co.comment, '')) LIKE 'transfer in operation on account%'
                THEN true
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) LIKE 'currency conversion,%from ta:%to:%'
                THEN true
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) LIKE 'transfer from % to %'
                THEN true
            WHEN co.operation = 'SUBACCOUNT_TRANSFER'
                THEN true
            ELSE false
        END AS is_internal_transfer,
        CASE
            WHEN co.operation = 'TRANSFER'
             AND (
                 lower(COALESCE(co.comment, '')) LIKE 'net amount in base from forex trade:%'
                 OR lower(COALESCE(co.comment, '')) LIKE '%ibkrrawtype=forex trade component%'
             )
                THEN true
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) LIKE 'currency conversion,%from ta:%to:%'
                THEN false
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) LIKE 'currency conversion,%'
                THEN true
            ELSE false
        END AS is_fx_conversion,
        CASE
            WHEN co.operation IN ('STOCK_PURCHASE', 'STOCK_SELL', 'CLOSE_TRADE', 'ROLLOVER')
                THEN true
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) ~* '(full call|early redemption|redemption)'
             AND lower(COALESCE(co.comment, '')) LIKE '%per bond%'
             AND co.asset_id IS NOT NULL
                THEN true
            ELSE false
        END AS is_trade_cash_flow,
        CASE WHEN co.operation = 'CORRECTION' THEN true ELSE false END AS is_correction,
        CASE
            WHEN co.operation = 'DIVIDEND' AND co.amount < 0 THEN true
            WHEN co.operation = 'FREE_FUNDS_INTEREST' AND co.amount < 0 THEN true
            WHEN co.operation = 'WITHHOLDING_TAX' AND co.amount > 0 THEN true
            WHEN co.operation = 'WITHDRAWAL' AND co.amount > 0 THEN true
            ELSE false
        END AS is_reversal
    FROM investory.cash_operations co
    JOIN investory.accounts a ON a.id = co.account_id
    JOIN investory.portfolios p ON p.id = a.portfolio_id
    LEFT JOIN investory.assets excluded_asset ON excluded_asset.id = co.asset_id
    WHERE co.asset_id IS NULL OR excluded_asset.exclude_from_import = false
), port_needed AS (
    SELECT DISTINCT
        portfolio_id,
        (date AT TIME ZONE 'Europe/Warsaw')::date AS vdate,
        currency
    FROM classified
), port_resolved AS (
    SELECT
        n.portfolio_id AS k_portfolio_id,
        n.vdate AS k_vdate,
        n.currency AS k_currency,
        r.fx_rate_to_base,
        r.source,
        r.source_rate_date,
        r.age_days,
        r.conversion_status
    FROM port_needed n
    CROSS JOIN LATERAL investory.resolve_portfolio_fx_rate(
        n.portfolio_id,
        n.vdate,
        n.currency
    ) AS r(
        portfolio_id,
        valuation_date,
        source_currency,
        base_currency,
        fx_rate_to_base,
        source,
        rate_method,
        rate_source,
        source_rate_date,
        age_days,
        conversion_status
    )
), acct_needed AS (
    SELECT DISTINCT
        (date AT TIME ZONE 'Europe/Warsaw')::date AS vdate,
        currency,
        account_currency
    FROM classified
), acct_resolved AS (
    SELECT
        n.vdate AS k_vdate,
        n.currency AS k_currency,
        n.account_currency AS k_account_currency,
        r.fx_rate_to_target,
        r.source,
        r.source_rate_date,
        r.age_days,
        r.conversion_status
    FROM acct_needed n
    CROSS JOIN LATERAL investory.resolve_fx_rate(
        n.vdate,
        n.currency,
        n.account_currency
    ) AS r(
        source_currency,
        target_currency,
        fx_rate_to_target,
        source,
        rate_method,
        rate_source,
        source_rate_date,
        age_days,
        conversion_status
    )
), txn_needed AS (
    SELECT DISTINCT
        date,
        currency,
        base_currency
    FROM classified
    WHERE is_fx_conversion
), txn_resolved AS (
    SELECT
        n.date AS k_date,
        n.currency AS k_currency,
        n.base_currency AS k_base_currency,
        r.fx_rate_to_target,
        r.source,
        r.source_rate_date,
        r.age_days,
        r.conversion_status
    FROM txn_needed n
    CROSS JOIN LATERAL investory.resolve_transaction_fx_rate(
        n.date,
        n.currency,
        n.base_currency,
        'TRANSACTION'::varchar
    ) AS r(
        source_currency,
        target_currency,
        fx_rate_to_target,
        source,
        rate_method,
        rate_source,
        source_rate_date,
        age_days,
        conversion_status
    )
), fx AS (
    SELECT
        c.*,
        CASE WHEN c.is_fx_conversion THEN tr.fx_rate_to_target ELSE pr.fx_rate_to_base END AS fx_rate_to_base,
        CASE WHEN c.is_fx_conversion THEN tr.source ELSE pr.source END AS portfolio_fx_source,
        CASE WHEN c.is_fx_conversion THEN tr.source_rate_date ELSE pr.source_rate_date END AS portfolio_source_rate_date,
        CASE WHEN c.is_fx_conversion THEN tr.age_days ELSE pr.age_days END AS portfolio_fx_age_days,
        CASE WHEN c.is_fx_conversion THEN tr.conversion_status ELSE pr.conversion_status END AS portfolio_conversion_status,
        ar.fx_rate_to_target AS fx_rate_to_account_currency,
        ar.source AS account_fx_source,
        ar.source_rate_date AS account_source_rate_date,
        ar.age_days AS account_fx_age_days,
        ar.conversion_status AS account_conversion_status
    FROM classified c
    LEFT JOIN port_resolved pr
        ON pr.k_portfolio_id = c.portfolio_id
       AND pr.k_vdate = (c.date AT TIME ZONE 'Europe/Warsaw')::date
       AND pr.k_currency = c.currency
    LEFT JOIN acct_resolved ar
        ON ar.k_vdate = (c.date AT TIME ZONE 'Europe/Warsaw')::date
       AND ar.k_currency = c.currency
       AND ar.k_account_currency = c.account_currency
    LEFT JOIN txn_resolved tr
        ON tr.k_date = c.date
       AND tr.k_currency = c.currency
       AND tr.k_base_currency = c.base_currency
)
SELECT
    operation_id,
    account_id,
    portfolio_id,
    account_currency,
    currency,
    base_currency AS portfolio_base_currency,
    base_currency,
    raw_operation,
    normalized_category,
    normalized_subtype,
    economic_direction,
    is_external_flow,
    is_internal_transfer,
    is_fx_conversion,
    is_trade_cash_flow,
    is_correction,
    is_reversal,
    asset_id,
    amount,
    CASE WHEN investory.fx_status_usable(portfolio_conversion_status)
        THEN amount * fx_rate_to_base END AS amount_in_portfolio_base_currency,
    CASE WHEN investory.fx_status_usable(portfolio_conversion_status)
        THEN amount * fx_rate_to_base END AS amount_in_base_currency,
    CASE WHEN investory.fx_status_usable(account_conversion_status)
        THEN amount * fx_rate_to_account_currency END AS amount_in_account_currency,
    comment,
    date,
    rate_month,
    fx_rate_to_base,
    portfolio_fx_source,
    portfolio_source_rate_date,
    portfolio_fx_age_days,
    portfolio_conversion_status,
    fx_rate_to_account_currency,
    account_fx_source,
    account_source_rate_date,
    account_fx_age_days,
    account_conversion_status,
    NULL::varchar(64) AS related_operation_id,
    NULL::varchar(64) AS transfer_group_id,
    CASE
        WHEN normalized_category IN ('INTERNAL_TRANSFER_IN', 'INTERNAL_TRANSFER_OUT', 'INTERNAL_BOOKKEEPING', 'FX_CONVERSION')
            THEN 'UNMATCHED'
        ELSE 'NOT_REQUIRED'
    END::varchar(32) AS pairing_status,
    classification_reason,
    'sql-v2026-08-09-ibkr-bond'::varchar(64) AS classification_version,
    portfolio_conversion_status AS conversion_status
FROM fx
WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS ux_normalized_cash_operations
    ON investory.app_v_normalized_cash_operations (operation_id);

COMMENT ON MATERIALIZED VIEW investory.app_v_normalized_cash_operations IS
    'Canonical classified cash ledger. IBKR fixed-income redemptions are settlement cash, not external funding.';

CREATE MATERIALIZED VIEW IF NOT EXISTS investory.app_v_account_monthly AS
WITH source_rows AS (
    SELECT
        ad.account_id,
        date_trunc('month', ad.snapshot_date)::date AS month,
        ad.snapshot_date,
        ad.equity,
        ad.deposits,
        ad.withdrawals,
        ad.dividends,
        ad.interest,
        ad.fees,
        ad.taxes,
        ad.realized_profit,
        ad.daily_profit_amount,
        ad.daily_return_pct,
        p.base_currency::varchar(3) AS base_currency,
        fx.fx_rate_to_target,
        fx.conversion_status,
        ROW_NUMBER() OVER (
            PARTITION BY ad.account_id, date_trunc('month', ad.snapshot_date)
            ORDER BY ad.snapshot_date DESC
        ) AS rn_last
    FROM investory.account_daily ad
    JOIN investory.accounts a ON a.id = ad.account_id
    JOIN investory.portfolios p ON p.id = a.portfolio_id
    CROSS JOIN LATERAL investory.resolve_fx_rate(
        ad.snapshot_date,
        ad.valuation_currency,
        p.base_currency::varchar(3)
    ) fx
),
month_rows AS (
    SELECT
        account_id,
        month,
        snapshot_date,
        CASE WHEN investory.fx_status_usable(conversion_status) THEN equity * fx_rate_to_target END AS equity,
        CASE WHEN investory.fx_status_usable(conversion_status) THEN deposits * fx_rate_to_target END AS deposits,
        CASE WHEN investory.fx_status_usable(conversion_status) THEN withdrawals * fx_rate_to_target END AS withdrawals,
        CASE WHEN investory.fx_status_usable(conversion_status) THEN dividends * fx_rate_to_target END AS dividends,
        CASE WHEN investory.fx_status_usable(conversion_status) THEN interest * fx_rate_to_target END AS interest,
        CASE WHEN investory.fx_status_usable(conversion_status) THEN fees * fx_rate_to_target END AS fees,
        CASE WHEN investory.fx_status_usable(conversion_status) THEN taxes * fx_rate_to_target END AS taxes,
        CASE WHEN investory.fx_status_usable(conversion_status) THEN realized_profit * fx_rate_to_target END AS realized_profit,
        CASE WHEN investory.fx_status_usable(conversion_status) THEN daily_profit_amount * fx_rate_to_target END AS daily_profit_amount,
        daily_return_pct,
        base_currency AS valuation_currency,
        rn_last
    FROM source_rows
),
monthly AS (
    -- One aggregated row per account/month. account_daily deposits, withdrawals,
    -- dividends, interest, fees, taxes and realized_profit are per-day amounts, so
    -- SUM yields the correct monthly total that reconciles with summed daily profit.
    SELECT
        mr.account_id,
        mr.month,
        MIN(mr.snapshot_date) AS first_date,
        MAX(mr.snapshot_date) AS end_date,
        COALESCE(MAX(CASE WHEN mr.rn_last = 1 THEN mr.equity END), 0) AS closing_equity,
        COALESCE(MAX(CASE WHEN mr.rn_last = 1 THEN mr.valuation_currency END), 'USD')::varchar(3)
            AS valuation_currency,
        COALESCE(SUM(mr.deposits), 0) AS deposits,
        COALESCE(SUM(mr.withdrawals), 0) AS withdrawals,
        COALESCE(SUM(mr.dividends), 0) AS dividends,
        COALESCE(SUM(mr.interest), 0) AS interest,
        COALESCE(SUM(mr.fees), 0) AS fees,
        COALESCE(SUM(mr.taxes), 0) AS taxes,
        COALESCE(SUM(mr.realized_profit), 0) AS realized_profit,
        COALESCE(SUM(mr.daily_profit_amount), 0) AS canonical_profit,
        CASE
            WHEN COUNT(mr.daily_return_pct) = 0 THEN NULL::numeric
            -- A day at or below -100% means account ruin for the month.
            WHEN BOOL_OR(mr.daily_return_pct <= -1) THEN -1::numeric
            -- Geometric linking of daily returns; NULL days are ignored and the
            -- ln domain is guarded so (1 + r) is always strictly positive.
            ELSE EXP(
                SUM(LN(1 + mr.daily_return_pct))
                    FILTER (WHERE mr.daily_return_pct IS NOT NULL AND mr.daily_return_pct > -1)
            ) - 1
        END AS compounded_monthly_return
    FROM month_rows mr
    GROUP BY mr.account_id, mr.month
)
SELECT
    m.account_id,
    m.month,
    m.first_date,
    m.end_date,
    -- Opening equity is the previous month's closing equity, carried across any
    -- calendar gaps between account_daily rows, and 0 at account inception.
    COALESCE(
        LAG(m.closing_equity) OVER (PARTITION BY m.account_id ORDER BY m.month),
        0
    ) AS opening_equity,
    m.closing_equity,
    m.valuation_currency,
    m.deposits,
    m.withdrawals,
    m.dividends,
    m.interest,
    m.fees,
    m.taxes,
    m.realized_profit,
        m.canonical_profit AS total_profit,
    m.compounded_monthly_return,
    NOW() AS updated_at
FROM monthly m
WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS ux_mv_account_monthly_account_month
    ON investory.app_v_account_monthly(account_id, month);

COMMENT ON MATERIALIZED VIEW investory.app_v_account_monthly IS
    'Monthly account performance in the owning portfolio base currency. Account-daily monetary facts are converted using the snapshot-date FX rate before aggregation; return percentages are currency invariant.';

CREATE OR REPLACE VIEW investory.app_v_normalized_cash_operation_flows AS
WITH parsed AS (
    SELECT
        nco.*,
        CASE
            WHEN nco.normalized_category = 'INTERNAL_BOOKKEEPING'
             AND nco.comment ~* 'transfer from [0-9]+ to [0-9]+'
                THEN substring(nco.comment from '(?i)transfer from ([0-9]+)')::bigint
        END AS transfer_source_account,
        CASE
            WHEN nco.normalized_category = 'INTERNAL_BOOKKEEPING'
             AND nco.comment ~* 'transfer from [0-9]+ to [0-9]+'
                THEN substring(nco.comment from '(?i)to ([0-9]+)')::bigint
        END AS transfer_target_account
    FROM investory.app_v_normalized_cash_operations nco
), effects AS (
    SELECT
        parsed.*,
        CASE
            WHEN normalized_category IN (
                'EXTERNAL_DEPOSIT', 'EXTERNAL_WITHDRAWAL',
                'INTERNAL_TRANSFER_IN', 'INTERNAL_TRANSFER_OUT'
            ) THEN amount
            WHEN normalized_category = 'INTERNAL_BOOKKEEPING'
             AND transfer_source_account = account_id
             AND amount < 0 THEN amount
            WHEN normalized_category = 'INTERNAL_BOOKKEEPING'
             AND transfer_target_account = account_id
             AND amount > 0 THEN amount
            ELSE 0::numeric
        END AS account_flow_amount,
        CASE
            WHEN normalized_category IN (
                'EXTERNAL_DEPOSIT', 'EXTERNAL_WITHDRAWAL',
                'INTERNAL_TRANSFER_IN', 'INTERNAL_TRANSFER_OUT'
            ) THEN amount
            ELSE 0::numeric
        END AS performance_flow_amount,
        CASE
            WHEN normalized_category IN ('EXTERNAL_DEPOSIT', 'EXTERNAL_WITHDRAWAL')
                THEN amount
            ELSE 0::numeric
        END AS portfolio_flow_amount
    FROM parsed
)
SELECT
    effects.*,
    CASE WHEN investory.fx_status_usable(portfolio_conversion_status)
        THEN account_flow_amount * fx_rate_to_base END
        AS account_flow_amount_in_portfolio_base_currency,
    CASE WHEN investory.fx_status_usable(account_conversion_status)
        THEN account_flow_amount * fx_rate_to_account_currency END
        AS account_flow_amount_in_account_currency,
    CASE WHEN investory.fx_status_usable(portfolio_conversion_status)
        THEN performance_flow_amount * fx_rate_to_base END
        AS performance_flow_amount_in_portfolio_base_currency,
    CASE WHEN investory.fx_status_usable(portfolio_conversion_status)
        THEN portfolio_flow_amount * fx_rate_to_base END
        AS portfolio_flow_amount_in_portfolio_base_currency
FROM effects;

CREATE OR REPLACE VIEW investory.app_v_portfolio_daily AS
WITH account_rows_with_fx AS (
    SELECT
        a.portfolio_id,
        p.base_currency::varchar(3) AS base_currency,
        ad.snapshot_date,
        ad.account_id,
        ad.cash_balance,
        ad.market_value,
        ad.equity,
        ad.dividends,
        ad.interest,
        ad.fees,
        ad.taxes,
        ad.realized_profit,
        ad.valuation_currency,
        fx.fx_rate_to_target AS valuation_to_base_rate,
        fx.conversion_status
    FROM investory.account_daily ad
    JOIN investory.accounts a ON a.id = ad.account_id
    JOIN investory.portfolios p ON p.id = a.portfolio_id
    CROSS JOIN LATERAL investory.resolve_fx_rate(
        ad.snapshot_date, ad.valuation_currency, p.base_currency::varchar(3)
    ) fx
), account_rows AS (
    SELECT
        portfolio_id, base_currency, snapshot_date, account_id, conversion_status,
        CASE WHEN investory.fx_status_usable(conversion_status) THEN cash_balance * valuation_to_base_rate END AS cash_balance,
        CASE WHEN investory.fx_status_usable(conversion_status) THEN market_value * valuation_to_base_rate END AS market_value,
        CASE WHEN investory.fx_status_usable(conversion_status) THEN equity * valuation_to_base_rate END AS equity,
        CASE WHEN investory.fx_status_usable(conversion_status) THEN dividends * valuation_to_base_rate END AS dividends,
        CASE WHEN investory.fx_status_usable(conversion_status) THEN interest * valuation_to_base_rate END AS interest,
        CASE WHEN investory.fx_status_usable(conversion_status) THEN fees * valuation_to_base_rate END AS fees,
        CASE WHEN investory.fx_status_usable(conversion_status) THEN taxes * valuation_to_base_rate END AS taxes,
        CASE WHEN investory.fx_status_usable(conversion_status) THEN realized_profit * valuation_to_base_rate END AS realized_profit
    FROM account_rows_with_fx
), external_flows AS (
    SELECT
        account_id,
        date::date AS snapshot_date,
        SUM(performance_flow_amount_in_portfolio_base_currency)
            FILTER (WHERE performance_flow_amount_in_portfolio_base_currency > 0) AS deposits,
        SUM(-performance_flow_amount_in_portfolio_base_currency)
            FILTER (WHERE performance_flow_amount_in_portfolio_base_currency < 0) AS withdrawals,
        COUNT(*) FILTER (
            WHERE NOT investory.fx_status_usable(portfolio_conversion_status)
        ) AS missing_flow_fx_count
    FROM investory.app_v_normalized_cash_operation_flows
    GROUP BY account_id, date::date
)
SELECT
    ar.portfolio_id,
    ar.snapshot_date,
    ar.base_currency,
    CASE WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(ar.conversion_status)) > 0 THEN NULL ELSE SUM(ar.cash_balance) END AS cash_balance,
    CASE WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(ar.conversion_status)) > 0 THEN NULL ELSE SUM(ar.market_value) END AS market_value,
    CASE WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(ar.conversion_status)) > 0 THEN NULL ELSE SUM(ar.equity) END AS equity,
    CASE WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(ar.conversion_status)) > 0 OR MAX(COALESCE(ef.missing_flow_fx_count, 0)) > 0 THEN NULL ELSE SUM(COALESCE(ef.deposits, 0)) END AS deposits,
    CASE WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(ar.conversion_status)) > 0 OR MAX(COALESCE(ef.missing_flow_fx_count, 0)) > 0 THEN NULL ELSE SUM(COALESCE(ef.withdrawals, 0)) END AS withdrawals,
    CASE WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(ar.conversion_status)) > 0 THEN NULL ELSE SUM(ar.dividends) END AS dividends,
    CASE WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(ar.conversion_status)) > 0 THEN NULL ELSE SUM(ar.interest) END AS interest,
    CASE WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(ar.conversion_status)) > 0 THEN NULL ELSE SUM(ar.fees) END AS fees,
    CASE WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(ar.conversion_status)) > 0 THEN NULL ELSE SUM(ar.taxes) END AS taxes,
    CASE WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(ar.conversion_status)) > 0 THEN NULL ELSE SUM(ar.realized_profit) END AS realized_profit,
    CASE WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(ar.conversion_status)) > 0 OR MAX(COALESCE(ef.missing_flow_fx_count, 0)) > 0 THEN NULL ELSE
        SUM(ar.equity) - LAG(SUM(ar.equity)) OVER (PARTITION BY ar.portfolio_id ORDER BY ar.snapshot_date)
        - SUM(COALESCE(ef.deposits, 0)) + SUM(COALESCE(ef.withdrawals, 0)) END AS total_profit,
    SUM(ar.equity) AS converted_equity_subtotal,
    COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(ar.conversion_status))::bigint AS missing_fx_count,
    COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(ar.conversion_status)) = 0
        AND MAX(COALESCE(ef.missing_flow_fx_count, 0)) = 0 AS is_complete,
    CASE
        WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(ar.conversion_status)) > 0 OR MAX(COALESCE(ef.missing_flow_fx_count, 0)) > 0 THEN NULL::numeric
        WHEN LAG(SUM(ar.equity)) OVER (PARTITION BY ar.portfolio_id ORDER BY ar.snapshot_date) IS NULL THEN NULL::numeric
        ELSE (SUM(ar.equity) - LAG(SUM(ar.equity)) OVER (PARTITION BY ar.portfolio_id ORDER BY ar.snapshot_date)
            - SUM(COALESCE(ef.deposits, 0)) + SUM(COALESCE(ef.withdrawals, 0)))
            / NULLIF(LAG(SUM(ar.equity)) OVER (PARTITION BY ar.portfolio_id ORDER BY ar.snapshot_date)
            + SUM(COALESCE(ef.deposits, 0)) - SUM(COALESCE(ef.withdrawals, 0)), 0)
    END AS daily_return_pct,
    NOW() AS updated_at
FROM account_rows ar
LEFT JOIN external_flows ef ON ef.account_id = ar.account_id AND ef.snapshot_date = ar.snapshot_date
GROUP BY ar.portfolio_id, ar.snapshot_date, ar.base_currency;

CREATE OR REPLACE VIEW investory.app_v_portfolio_performance_daily AS
WITH account_rows AS (
    SELECT
        a.portfolio_id,
        p.base_currency::varchar(3) AS base_currency,
        ad.snapshot_date,
        ad.equity,
        ad.deposits,
        ad.withdrawals,
        ad.dividends,
        ad.interest,
        ad.fees,
        ad.taxes,
        ad.realized_profit,
        ad.daily_profit_amount,
        fx.fx_rate_to_target AS valuation_to_base_rate,
        fx.conversion_status
    FROM investory.account_daily ad
    JOIN investory.accounts a ON a.id = ad.account_id
    JOIN investory.portfolios p ON p.id = a.portfolio_id
    CROSS JOIN LATERAL investory.resolve_fx_rate(
        ad.snapshot_date, ad.valuation_currency, p.base_currency::varchar(3)
    ) fx
    WHERE NOT a.cash_only
), converted AS (
    SELECT
        portfolio_id, base_currency, snapshot_date,
        CASE WHEN investory.fx_status_usable(conversion_status) THEN equity * valuation_to_base_rate END AS equity,
        CASE WHEN investory.fx_status_usable(conversion_status) THEN deposits * valuation_to_base_rate END AS deposits,
        CASE WHEN investory.fx_status_usable(conversion_status) THEN withdrawals * valuation_to_base_rate END AS withdrawals,
        CASE WHEN investory.fx_status_usable(conversion_status) THEN dividends * valuation_to_base_rate END AS dividends,
        CASE WHEN investory.fx_status_usable(conversion_status) THEN interest * valuation_to_base_rate END AS interest,
        CASE WHEN investory.fx_status_usable(conversion_status) THEN fees * valuation_to_base_rate END AS fees,
        CASE WHEN investory.fx_status_usable(conversion_status) THEN taxes * valuation_to_base_rate END AS taxes,
        CASE WHEN investory.fx_status_usable(conversion_status) THEN realized_profit * valuation_to_base_rate END AS realized_profit,
        CASE WHEN investory.fx_status_usable(conversion_status) THEN daily_profit_amount * valuation_to_base_rate END AS total_profit,
        conversion_status
    FROM account_rows
)
SELECT
    portfolio_id, snapshot_date, base_currency,
    CASE WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(conversion_status)) > 0 THEN NULL ELSE SUM(equity) END AS equity,
    CASE WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(conversion_status)) > 0 THEN NULL ELSE SUM(deposits) END AS deposits,
    CASE WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(conversion_status)) > 0 THEN NULL ELSE SUM(withdrawals) END AS withdrawals,
    CASE WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(conversion_status)) > 0 THEN NULL ELSE SUM(dividends) END AS dividends,
    CASE WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(conversion_status)) > 0 THEN NULL ELSE SUM(interest) END AS interest,
    CASE WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(conversion_status)) > 0 THEN NULL ELSE SUM(fees) END AS fees,
    CASE WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(conversion_status)) > 0 THEN NULL ELSE SUM(taxes) END AS taxes,
    CASE WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(conversion_status)) > 0 THEN NULL ELSE SUM(realized_profit) END AS realized_profit,
    CASE WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(conversion_status)) > 0 THEN NULL ELSE SUM(total_profit) END AS total_profit,
    CASE WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(conversion_status)) > 0 THEN NULL
         WHEN LAG(SUM(equity)) OVER (PARTITION BY portfolio_id ORDER BY snapshot_date) IS NULL THEN NULL
         ELSE SUM(total_profit) / NULLIF(LAG(SUM(equity)) OVER (PARTITION BY portfolio_id ORDER BY snapshot_date)
             + SUM(deposits) - SUM(withdrawals), 0) END AS daily_return_pct
FROM converted
GROUP BY portfolio_id, snapshot_date, base_currency;

COMMENT ON VIEW investory.app_v_portfolio_performance_daily IS
    'Investment-performance projection for non-cash-only accounts. Balance and cash fields in app_v_portfolio_daily remain whole-portfolio values.';

CREATE MATERIALIZED VIEW IF NOT EXISTS investory.app_v_portfolio_monthly AS
WITH month_rows AS (
    SELECT
        pd.portfolio_id,
        date_trunc('month', pd.snapshot_date)::date AS month,
        pd.snapshot_date,
        pd.base_currency,
        pd.equity,
        pd.deposits,
        pd.withdrawals,
        pd.dividends,
        pd.interest,
        pd.fees,
        pd.taxes,
        pd.realized_profit,
        pd.total_profit,
        pd.daily_return_pct,
        LAG(pd.equity) OVER (
            PARTITION BY pd.portfolio_id
            ORDER BY pd.snapshot_date
        ) AS previous_day_equity,
        ROW_NUMBER() OVER (
            PARTITION BY pd.portfolio_id, date_trunc('month', pd.snapshot_date)
            ORDER BY pd.snapshot_date
        ) AS rn_first,
        ROW_NUMBER() OVER (
            PARTITION BY pd.portfolio_id, date_trunc('month', pd.snapshot_date)
            ORDER BY pd.snapshot_date DESC
        ) AS rn_last
    FROM investory.app_v_portfolio_performance_daily pd
)
SELECT
    mr.portfolio_id,
    mr.month,
    MIN(mr.snapshot_date) AS first_date,
    MAX(mr.snapshot_date) AS end_date,
    MAX(CASE WHEN mr.rn_first = 1 THEN COALESCE(mr.previous_day_equity, mr.equity) END) AS opening_equity,
    MAX(CASE WHEN mr.rn_last = 1 THEN mr.equity END) AS closing_equity,
    MAX(mr.base_currency)::varchar(3) AS base_currency,
    SUM(mr.deposits) AS deposits,
    SUM(mr.withdrawals) AS withdrawals,
    SUM(mr.dividends) AS dividends,
    SUM(mr.interest) AS interest,
    SUM(mr.fees) AS fees,
    SUM(mr.taxes) AS taxes,
    SUM(mr.realized_profit) AS realized_profit,
    SUM(mr.total_profit) AS total_profit,
    CASE
        WHEN COUNT(mr.daily_return_pct) = 0 THEN NULL::numeric
        WHEN BOOL_OR(mr.daily_return_pct <= -1) THEN -1::numeric
        ELSE EXP(SUM(CASE
            WHEN mr.daily_return_pct IS NULL THEN NULL::numeric
            WHEN mr.daily_return_pct <= -1 THEN NULL::numeric
            ELSE LN(1 + mr.daily_return_pct)
        END)) - 1
    END AS compounded_monthly_return,
    NOW() AS updated_at
FROM month_rows mr
GROUP BY mr.portfolio_id, mr.month
WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS ux_mv_portfolio_monthly_portfolio_month
    ON investory.app_v_portfolio_monthly(portfolio_id, month);

CREATE MATERIALIZED VIEW IF NOT EXISTS investory.app_v_account_statistics AS
WITH latest_daily AS (
    SELECT DISTINCT ON (ad.account_id)
        ad.account_id,
        ad.snapshot_date,
        ad.valuation_currency,
        ad.cash_balance,
        ad.market_value,
        ad.equity,
        ad.cost_base,
        ad.unrealized_profit,
        ad.realized_profit,
        ad.daily_return_pct
    FROM investory.account_daily ad
    ORDER BY ad.account_id, ad.snapshot_date DESC, ad.id DESC
),
latest_daily_in_base AS (
    SELECT
        ld.account_id,
        ld.snapshot_date,
        pf.base_currency::varchar(3) AS valuation_currency,
        CASE WHEN investory.fx_status_usable(fx.conversion_status) THEN ld.cash_balance * fx.fx_rate_to_target END AS cash_balance,
        CASE WHEN investory.fx_status_usable(fx.conversion_status) THEN ld.market_value * fx.fx_rate_to_target END AS market_value,
        CASE WHEN investory.fx_status_usable(fx.conversion_status) THEN ld.equity * fx.fx_rate_to_target END AS equity,
        CASE WHEN investory.fx_status_usable(fx.conversion_status) THEN ld.cost_base * fx.fx_rate_to_target END AS cost_base,
        CASE WHEN investory.fx_status_usable(fx.conversion_status) THEN ld.unrealized_profit * fx.fx_rate_to_target END AS unrealized_profit,
        CASE WHEN investory.fx_status_usable(fx.conversion_status) THEN ld.realized_profit * fx.fx_rate_to_target END AS realized_profit,
        ld.daily_return_pct
    FROM latest_daily ld
    JOIN investory.accounts a ON a.id = ld.account_id
    JOIN investory.portfolios pf ON pf.id = a.portfolio_id
    LEFT JOIN LATERAL investory.resolve_fx_rate(
        ld.snapshot_date,
        ld.valuation_currency::varchar(3),
        pf.base_currency::varchar(3)
    ) fx ON true
),
open_position_totals AS (
    SELECT
        value.account_id,
        CASE WHEN COUNT(*) FILTER (WHERE value.cost_basis_in_base_currency IS NULL) > 0 THEN NULL::numeric
             ELSE SUM(value.cost_basis_in_base_currency) END AS cost_base,
        CASE WHEN COUNT(*) FILTER (WHERE value.market_value_in_base_currency IS NULL) > 0 THEN NULL::numeric
             ELSE SUM(value.market_value_in_base_currency) END AS market_value,
        CASE WHEN COUNT(*) FILTER (
            WHERE value.cost_basis_in_base_currency IS NULL OR value.market_value_in_base_currency IS NULL
        ) > 0 THEN NULL::numeric
             ELSE SUM(value.market_value_in_base_currency - value.cost_basis_in_base_currency) END AS unrealized_profit,
        COUNT(*) FILTER (
            WHERE value.cost_basis_in_base_currency IS NULL OR value.market_value_in_base_currency IS NULL
        )::bigint AS missing_fx_count
    FROM investory.app_v_current_open_position_rows value
    GROUP BY value.account_id
),
closed_position_components AS (
    SELECT
        a.id AS account_id,
        p.close_time::date AS valuation_date,
        p.profit_currency::varchar(3) AS source_currency,
        pf.base_currency::varchar(3) AS base_currency,
        CASE
            WHEN p.settlement_model = 'RESULT_ONLY' THEN COALESCE(p.profit, 0)
            ELSE COALESCE(p.profit, 0) + COALESCE(p.swap, 0)
        END AS amount_native
    FROM investory.positions p
    JOIN investory.accounts a
      ON a.id = p.account_id
    JOIN investory.portfolios pf
      ON pf.id = a.portfolio_id
    WHERE p.close_time IS NOT NULL
      AND p.asset_id IS NOT NULL
    UNION ALL
    SELECT
        a.id AS account_id,
        p.close_time::date AS valuation_date,
        p.commission_currency::varchar(3) AS source_currency,
        pf.base_currency::varchar(3) AS base_currency,
        CASE
            WHEN p.settlement_model = 'RESULT_ONLY' THEN 0
            ELSE COALESCE(p.commission, 0)
        END AS amount_native
    FROM investory.positions p
    JOIN investory.accounts a
      ON a.id = p.account_id
    JOIN investory.portfolios pf
      ON pf.id = a.portfolio_id
    WHERE p.close_time IS NOT NULL
      AND p.asset_id IS NOT NULL
),
closed_position_totals AS (
    SELECT
        c.account_id,
        CASE
            WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(fx.conversion_status)) > 0
                THEN NULL::numeric
            ELSE SUM(c.amount_native * fx.fx_rate_to_target)
        END AS realized_profit,
        SUM(c.amount_native * fx.fx_rate_to_target) FILTER (
            WHERE investory.fx_status_usable(fx.conversion_status)) AS converted_subtotal,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(fx.conversion_status))::bigint
            AS missing_fx_count
    FROM closed_position_components c
    LEFT JOIN LATERAL investory.resolve_fx_rate(
        c.valuation_date,
        c.source_currency,
        c.base_currency
    ) fx ON true
    GROUP BY c.account_id
), portfolio_flow_rows AS (
    SELECT
        nco.*,
        CASE
            WHEN nco.normalized_category IN ('EXTERNAL_DEPOSIT', 'EXTERNAL_WITHDRAWAL')
                THEN nco.amount_in_portfolio_base_currency
            WHEN nco.normalized_category = 'INTERNAL_BOOKKEEPING'
             AND nco.comment ~* 'transfer from [0-9]+ to [0-9]+'
             AND substring(nco.comment from '(?i)to ([0-9]+)')::bigint = nco.account_id
             AND nco.amount > 0
             AND NOT EXISTS (
                 SELECT 1
                 FROM investory.accounts counterparty
                 WHERE counterparty.id = substring(nco.comment from '(?i)transfer from ([0-9]+)')::bigint
             ) THEN nco.amount_in_portfolio_base_currency
            WHEN nco.normalized_category = 'INTERNAL_BOOKKEEPING'
             AND nco.comment ~* 'transfer from [0-9]+ to [0-9]+'
             AND substring(nco.comment from '(?i)transfer from ([0-9]+)')::bigint = nco.account_id
             AND nco.amount < 0
             AND NOT EXISTS (
                 SELECT 1
                 FROM investory.accounts counterparty
                 WHERE counterparty.id = substring(nco.comment from '(?i)to ([0-9]+)')::bigint
             ) THEN nco.amount_in_portfolio_base_currency
            ELSE 0::numeric
        END AS scoped_portfolio_flow_amount_in_portfolio_base_currency
    FROM investory.app_v_normalized_cash_operations nco
), flow_totals AS (
    SELECT
        nco.account_id,
        COUNT(*) FILTER (
            WHERE NOT investory.fx_status_usable(nco.portfolio_conversion_status)
               OR NOT investory.fx_status_usable(nco.account_conversion_status)
        )::bigint AS missing_fx_count,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(nco.account_conversion_status))::bigint
            AS account_missing_fx_count,
        SUM(nco.amount_in_portfolio_base_currency) FILTER (
            WHERE investory.fx_status_usable(nco.portfolio_conversion_status)) AS converted_subtotal,
        SUM(nco.scoped_portfolio_flow_amount_in_portfolio_base_currency)
            FILTER (WHERE nco.scoped_portfolio_flow_amount_in_portfolio_base_currency > 0)
            AS total_deposit,
        SUM(CASE
            WHEN nco.normalized_category = 'EXTERNAL_DEPOSIT' THEN nco.amount_in_account_currency
            ELSE NULL::numeric
        END) AS total_deposit_account_currency,
        SUM(-nco.scoped_portfolio_flow_amount_in_portfolio_base_currency)
            FILTER (WHERE nco.scoped_portfolio_flow_amount_in_portfolio_base_currency < 0)
            AS total_withdrawal,
        SUM(CASE
            WHEN nco.normalized_category = 'EXTERNAL_WITHDRAWAL' THEN ABS(nco.amount_in_account_currency)
            ELSE NULL::numeric
        END) AS total_withdrawal_account_currency,
        SUM(CASE
            WHEN nco.normalized_category IN ('DIVIDEND', 'DIVIDEND_REVERSAL') THEN nco.amount_in_base_currency
            ELSE NULL::numeric
        END) AS dividends,
        SUM(CASE
            WHEN nco.normalized_category IN ('INTEREST', 'INTEREST_REVERSAL') THEN nco.amount_in_base_currency
            ELSE NULL::numeric
        END) AS interest,
        SUM(CASE
            WHEN nco.normalized_category = 'FEE' THEN -nco.amount_in_base_currency
            ELSE NULL::numeric
        END) AS fees,
        SUM(CASE
            WHEN nco.normalized_category IN ('WITHHOLDING_TAX', 'WITHHOLDING_TAX_REVERSAL', 'OTHER_TAX') THEN -nco.amount_in_base_currency
            ELSE NULL::numeric
        END) AS taxes,
        SUM(CASE
            WHEN nco.normalized_category = 'REALIZED_TRADE_RESULT' THEN nco.amount_in_base_currency
            ELSE NULL::numeric
        END) AS realized_profit,
        SUM(CASE
            WHEN nco.normalized_category IN ('INTERNAL_TRANSFER_IN', 'INTERNAL_TRANSFER_OUT') THEN nco.amount_in_base_currency
            ELSE NULL::numeric
        END) AS internal_transfer_net,
        SUM(CASE
            WHEN nco.normalized_category = 'FX_CONVERSION' THEN nco.amount_in_base_currency
            ELSE NULL::numeric
        END) AS fx_conversion_net,
        SUM(CASE
            WHEN nco.normalized_category IN (
                'EXTERNAL_DEPOSIT',
                'EXTERNAL_WITHDRAWAL',
                'INTERNAL_TRANSFER_IN',
                'INTERNAL_TRANSFER_OUT',
                'INTERNAL_BOOKKEEPING',
                'FX_CONVERSION',
                'CORRECTION'
            ) THEN nco.amount_in_base_currency
            ELSE NULL::numeric
        END) AS total_cash_result,
        SUM(CASE
            WHEN nco.normalized_category IN (
                'EXTERNAL_DEPOSIT',
                'EXTERNAL_WITHDRAWAL',
                'INTERNAL_TRANSFER_IN',
                'INTERNAL_TRANSFER_OUT',
                'INTERNAL_BOOKKEEPING',
                'FX_CONVERSION',
                'CORRECTION'
            ) THEN nco.amount_in_account_currency
            ELSE NULL::numeric
        END) AS total_cash_result_account_currency
    FROM portfolio_flow_rows nco
    GROUP BY nco.account_id
),
activity_meta AS (
    SELECT
        ad.account_id,
        COUNT(*) FILTER (
            WHERE COALESCE(ad.deposits, 0) <> 0
               OR COALESCE(ad.withdrawals, 0) <> 0
               OR COALESCE(ad.dividends, 0) <> 0
               OR COALESCE(ad.interest, 0) <> 0
               OR COALESCE(ad.fees, 0) <> 0
               OR COALESCE(ad.taxes, 0) <> 0
               OR COALESCE(ad.realized_profit, 0) <> 0
        )::integer AS activity_count,
        MIN(ad.snapshot_date) FILTER (
            WHERE COALESCE(ad.deposits, 0) <> 0
               OR COALESCE(ad.withdrawals, 0) <> 0
               OR COALESCE(ad.dividends, 0) <> 0
               OR COALESCE(ad.interest, 0) <> 0
               OR COALESCE(ad.fees, 0) <> 0
               OR COALESCE(ad.taxes, 0) <> 0
               OR COALESCE(ad.realized_profit, 0) <> 0
        )::timestamptz AS first_activity_at,
        MAX(ad.snapshot_date) FILTER (
            WHERE COALESCE(ad.deposits, 0) <> 0
               OR COALESCE(ad.withdrawals, 0) <> 0
               OR COALESCE(ad.dividends, 0) <> 0
               OR COALESCE(ad.interest, 0) <> 0
               OR COALESCE(ad.fees, 0) <> 0
               OR COALESCE(ad.taxes, 0) <> 0
               OR COALESCE(ad.realized_profit, 0) <> 0
        )::timestamptz AS last_activity_at
    FROM investory.account_daily ad
    GROUP BY ad.account_id
)
SELECT
    a.id AS account_id,
    p.base_currency::varchar(3) AS valuation_currency,
    CASE WHEN COALESCE(ft.missing_fx_count, 0) > 0 THEN NULL ELSE COALESCE(ft.total_deposit, 0) END AS total_deposit,
    CASE WHEN COALESCE(ft.missing_fx_count, 0) > 0 THEN NULL ELSE COALESCE(ft.total_withdrawal, 0) END AS total_withdrawal,
    CASE WHEN COALESCE(ft.missing_fx_count, 0) > 0 THEN NULL
         ELSE COALESCE(ft.total_deposit, 0) - COALESCE(ft.total_withdrawal, 0) END AS net_deposit,
    CASE WHEN COALESCE(ft.account_missing_fx_count, 0) > 0 THEN NULL
         ELSE COALESCE(ft.total_deposit_account_currency, 0) - COALESCE(ft.total_withdrawal_account_currency, 0)
    END AS account_net_deposit,
    COALESCE(ld.cash_balance, 0) AS cash_balance,
    CASE WHEN COALESCE(opt.missing_fx_count, 0) > 0 THEN NULL
         ELSE COALESCE(opt.market_value, COALESCE(ld.market_value, 0)) END AS market_value,
    CASE WHEN COALESCE(opt.missing_fx_count, 0) > 0 THEN NULL
         ELSE COALESCE(ld.cash_balance, 0) + COALESCE(opt.market_value, COALESCE(ld.market_value, 0)) END AS equity,
    CASE WHEN COALESCE(opt.missing_fx_count, 0) > 0 THEN NULL
         ELSE COALESCE(opt.cost_base, COALESCE(ld.cost_base, 0)) END AS cost_base,
    CASE WHEN COALESCE(cpt.missing_fx_count, 0) > 0 THEN NULL
         ELSE COALESCE(cpt.realized_profit, 0) END AS realized_profit,
    CASE WHEN COALESCE(opt.missing_fx_count, 0) > 0 THEN NULL
         ELSE COALESCE(opt.unrealized_profit, COALESCE(ld.unrealized_profit, 0)) END AS unrealized_profit,
    CASE WHEN COALESCE(ft.missing_fx_count, 0) > 0 THEN NULL ELSE COALESCE(ft.dividends, 0) END AS dividends,
    CASE WHEN COALESCE(ft.missing_fx_count, 0) > 0 THEN NULL ELSE COALESCE(ft.interest, 0) END AS interest,
    CASE WHEN COALESCE(ft.missing_fx_count, 0) > 0 THEN NULL ELSE COALESCE(ft.fees, 0) END AS fees,
    CASE WHEN COALESCE(ft.missing_fx_count, 0) > 0 THEN NULL ELSE COALESCE(ft.taxes, 0) END AS taxes,
    COALESCE(ft.converted_subtotal, 0) AS converted_cash_subtotal,
    COALESCE(ft.missing_fx_count, 0) + COALESCE(cpt.missing_fx_count, 0)
        + COALESCE(opt.missing_fx_count, 0) AS missing_fx_count,
    COALESCE(ft.missing_fx_count, 0) = 0 AND COALESCE(cpt.missing_fx_count, 0) = 0
        AND COALESCE(opt.missing_fx_count, 0) = 0 AS is_complete,
    COALESCE(am.activity_count, 0) AS activity_count,
    am.first_activity_at,
    am.last_activity_at,
    ld.snapshot_date AS latest_snapshot_date,
    ld.daily_return_pct AS latest_return_pct,
    NOW() AS updated_at
FROM investory.accounts a
JOIN investory.portfolios p
    ON p.id = a.portfolio_id
LEFT JOIN latest_daily_in_base ld
    ON ld.account_id = a.id
LEFT JOIN open_position_totals opt
    ON opt.account_id = a.id
LEFT JOIN flow_totals ft
    ON ft.account_id = a.id
LEFT JOIN activity_meta am
    ON am.account_id = a.id
LEFT JOIN closed_position_totals cpt
    ON cpt.account_id = a.id
WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS ux_mv_account_statistics_account
    ON investory.app_v_account_statistics(account_id);


CREATE MATERIALIZED VIEW IF NOT EXISTS investory.app_v_portfolio_kpi_summary_mv AS
WITH latest_portfolio_daily AS (
    SELECT DISTINCT ON (pd.portfolio_id)
        pd.portfolio_id,
        pd.base_currency,
        pd.snapshot_date,
        pd.cash_balance,
        pd.market_value,
        pd.equity,
        pd.converted_equity_subtotal,
        pd.missing_fx_count,
        pd.is_complete
    FROM investory.app_v_portfolio_daily pd
    ORDER BY pd.portfolio_id, pd.snapshot_date DESC
),
latest_account_stats AS (
    SELECT
        a.portfolio_id,
        SUM(ast.missing_fx_count)::bigint AS missing_fx_count,
        SUM(ast.converted_cash_subtotal) AS converted_cash_subtotal,
        SUM(ast.cash_balance) AS total_cash,
        SUM(ast.market_value) AS total_market_value,
        SUM(ast.equity) AS total_equity,
        SUM(ast.total_deposit) AS total_deposits,
        SUM(ast.total_withdrawal) AS total_withdrawals,
        SUM(ast.total_deposit - ast.total_withdrawal) AS net_deposits,
        SUM(ast.realized_profit) AS total_realized_profit,
        SUM(ast.unrealized_profit) AS total_unrealized_profit,
        SUM(ast.dividends) AS total_dividends,
        SUM(ast.interest) AS total_interest,
        SUM(ast.fees) AS total_fees,
        SUM(ast.taxes) AS total_taxes,
        SUM(ast.activity_count) AS activity_count,
        MIN(ast.first_activity_at) AS first_activity_at,
        MAX(ast.last_activity_at) AS last_activity_at
    FROM investory.app_v_account_statistics ast
    JOIN investory.accounts a
        ON a.id = ast.account_id
    GROUP BY a.portfolio_id
)
SELECT
    p.id AS portfolio_id,
    p.name AS portfolio_name,
    p.base_currency::varchar(3) AS base_currency,
    CASE WHEN COALESCE(las.missing_fx_count, 0) > 0 THEN NULL ELSE COALESCE(las.total_deposits, 0) END AS total_deposits,
    CASE WHEN COALESCE(las.missing_fx_count, 0) > 0 THEN NULL ELSE COALESCE(las.total_withdrawals, 0) END AS total_withdrawals,
    CASE WHEN COALESCE(las.missing_fx_count, 0) > 0 THEN NULL ELSE COALESCE(las.net_deposits, 0) END AS net_deposits,
    CASE WHEN COALESCE(las.missing_fx_count, 0) = 0 THEN COALESCE(las.total_cash, 0) END AS total_cash,
    CASE WHEN COALESCE(las.missing_fx_count, 0) = 0 THEN COALESCE(las.total_market_value, 0) END AS total_market_value,
    CASE WHEN COALESCE(las.missing_fx_count, 0) = 0 THEN COALESCE(las.total_equity, 0) END AS total_equity,
    CASE WHEN COALESCE(las.missing_fx_count, 0) > 0 THEN NULL ELSE COALESCE(las.total_realized_profit, 0) END AS total_realized_profit,
    CASE WHEN COALESCE(las.missing_fx_count, 0) > 0 THEN NULL ELSE COALESCE(las.total_unrealized_profit, 0) END AS total_unrealized_profit,
    CASE WHEN COALESCE(las.missing_fx_count, 0) > 0 THEN NULL ELSE COALESCE(las.total_dividends, 0) END AS total_dividends,
    CASE WHEN COALESCE(las.missing_fx_count, 0) > 0 THEN NULL ELSE COALESCE(las.total_interest, 0) END AS total_interest,
    CASE WHEN COALESCE(las.missing_fx_count, 0) > 0 THEN NULL ELSE COALESCE(las.total_fees, 0) END AS total_fees,
    CASE WHEN COALESCE(las.missing_fx_count, 0) > 0 THEN NULL ELSE COALESCE(las.total_taxes, 0) END AS total_taxes,
    COALESCE(las.converted_cash_subtotal, 0) AS converted_cash_subtotal,
    COALESCE(lpd.converted_equity_subtotal, 0) AS converted_equity_subtotal,
    COALESCE(las.missing_fx_count, 0) AS missing_fx_count,
    COALESCE(las.missing_fx_count, 0) = 0 AS is_complete,
    COALESCE(las.activity_count, 0) AS activity_count,
    las.first_activity_at,
    las.last_activity_at,
    lpd.snapshot_date AS source_max_date,
    NOW() AS updated_at
FROM investory.portfolios p
LEFT JOIN latest_portfolio_daily lpd
    ON lpd.portfolio_id = p.id
LEFT JOIN latest_account_stats las
    ON las.portfolio_id = p.id
WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS ux_app_v_portfolio_kpi_summary_mv_portfolio
    ON investory.app_v_portfolio_kpi_summary_mv(portfolio_id);


CREATE MATERIALIZED VIEW IF NOT EXISTS investory.app_v_portfolio_currency_breakdown AS
WITH latest_account_daily AS (
    SELECT DISTINCT ON (ad.account_id)
        ad.account_id,
        ad.valuation_currency,
        ad.cash_balance,
        ad.market_value,
        ad.snapshot_date
    FROM investory.account_daily ad
    ORDER BY ad.account_id, ad.snapshot_date DESC, ad.id DESC
), latest_with_fx AS (
    SELECT
        lad.account_id,
        lad.valuation_currency,
        lad.cash_balance,
        lad.market_value,
        lad.snapshot_date,
        a.portfolio_id,
        p.base_currency,
        fx.fx_rate_to_target AS fx_rate_to_base,
        fx.conversion_status
    FROM latest_account_daily lad
    JOIN investory.accounts a
      ON a.id = lad.account_id
    JOIN investory.portfolios p
      ON p.id = a.portfolio_id
    LEFT JOIN LATERAL investory.resolve_fx_rate(
        lad.snapshot_date,
        lad.valuation_currency::varchar(3),
        p.base_currency::varchar(3)
    ) fx ON true
), account_latest AS (
    SELECT
        lwf.portfolio_id,
        lwf.base_currency::varchar(3) AS base_currency,
        'ACCOUNT_LATEST'::varchar(32) AS metric_type,
        lwf.valuation_currency::varchar(3) AS currency,
        SUM(lwf.cash_balance + lwf.market_value) AS amount_local,
        CASE
            WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(lwf.conversion_status)) > 0
                THEN NULL::numeric
            ELSE SUM((lwf.cash_balance + lwf.market_value) * lwf.fx_rate_to_base)
        END AS amount_in_base_currency,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(lwf.conversion_status))::bigint AS missing_fx_count,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(lwf.conversion_status)) = 0 AS is_complete
    FROM latest_with_fx lwf
    GROUP BY lwf.portfolio_id, lwf.base_currency, lwf.valuation_currency
), realized_components AS (
    SELECT
        a.portfolio_id,
        pf.base_currency::varchar(3) AS base_currency,
        p.close_time::date AS valuation_date,
        p.profit_currency::varchar(3) AS currency,
        COALESCE(p.profit, 0) + COALESCE(p.swap, 0) AS amount_local
    FROM investory.positions p
    JOIN investory.accounts a ON a.id = p.account_id
    JOIN investory.portfolios pf ON pf.id = a.portfolio_id
    WHERE p.close_time IS NOT NULL
    UNION ALL
    SELECT
        a.portfolio_id,
        pf.base_currency::varchar(3) AS base_currency,
        p.close_time::date AS valuation_date,
        p.commission_currency::varchar(3) AS currency,
        COALESCE(p.commission, 0) AS amount_local
    FROM investory.positions p
    JOIN investory.accounts a ON a.id = p.account_id
    JOIN investory.portfolios pf ON pf.id = a.portfolio_id
    WHERE p.close_time IS NOT NULL
), realized_with_fx AS (
    SELECT
        rc.*,
        fx.fx_rate_to_target AS fx_rate_to_base,
        fx.conversion_status
    FROM realized_components rc
    LEFT JOIN LATERAL investory.resolve_fx_rate(
        rc.valuation_date,
        rc.currency,
        rc.base_currency
    ) fx ON true
), realized AS (
    SELECT
        rwf.portfolio_id,
        rwf.base_currency,
        'REALIZED'::varchar(32) AS metric_type,
        rwf.currency,
        SUM(rwf.amount_local) AS amount_local,
        CASE
            WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(rwf.conversion_status)) > 0
                THEN NULL::numeric
            ELSE SUM(rwf.amount_local * rwf.fx_rate_to_base)
        END AS amount_in_base_currency,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(rwf.conversion_status))::bigint AS missing_fx_count,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(rwf.conversion_status)) = 0 AS is_complete
    FROM realized_with_fx rwf
    GROUP BY rwf.portfolio_id, rwf.base_currency, rwf.currency
), unrealized_components AS (
    SELECT
        a.portfolio_id,
        pf.base_currency::varchar(3) AS base_currency,
        p.profit_currency::varchar(3) AS currency,
        COALESCE(p.profit, 0) + COALESCE(p.swap, 0) AS amount_local
    FROM investory.positions p
    JOIN investory.accounts a ON a.id = p.account_id
    JOIN investory.portfolios pf ON pf.id = a.portfolio_id
    WHERE p.close_time IS NULL
    UNION ALL
    SELECT
        a.portfolio_id,
        pf.base_currency::varchar(3) AS base_currency,
        p.commission_currency::varchar(3) AS currency,
        COALESCE(p.commission, 0) AS amount_local
    FROM investory.positions p
    JOIN investory.accounts a ON a.id = p.account_id
    JOIN investory.portfolios pf ON pf.id = a.portfolio_id
    WHERE p.close_time IS NULL
), unrealized_with_fx AS (
    SELECT uc.*, fx.fx_rate_to_target AS fx_rate_to_base, fx.conversion_status
    FROM unrealized_components uc
    LEFT JOIN LATERAL investory.resolve_fx_rate(
        CURRENT_DATE, uc.currency, uc.base_currency
    ) fx ON true
), unrealized AS (
    SELECT
        uwf.portfolio_id,
        uwf.base_currency,
        'UNREALIZED'::varchar(32) AS metric_type,
        uwf.currency,
        SUM(uwf.amount_local) AS amount_local,
        CASE WHEN COUNT(*) FILTER (
            WHERE NOT investory.fx_status_usable(uwf.conversion_status)) > 0
            THEN NULL::numeric ELSE SUM(uwf.amount_local * uwf.fx_rate_to_base) END
            AS amount_in_base_currency,
        COUNT(*) FILTER (
            WHERE NOT investory.fx_status_usable(uwf.conversion_status))::bigint AS missing_fx_count,
        COUNT(*) FILTER (
            WHERE NOT investory.fx_status_usable(uwf.conversion_status)) = 0 AS is_complete
    FROM unrealized_with_fx uwf
    GROUP BY uwf.portfolio_id, uwf.base_currency, uwf.currency
), dividends AS (
    SELECT
        a.portfolio_id,
        pf.base_currency::varchar(3) AS base_currency,
        'DIVIDENDS'::varchar(32) AS metric_type,
        nco.currency::varchar(3) AS currency,
        SUM(nco.amount) AS amount_local,
        CASE
            WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(nco.portfolio_conversion_status)) > 0
                THEN NULL::numeric
            ELSE SUM(nco.amount_in_portfolio_base_currency)
        END AS amount_in_base_currency,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(nco.portfolio_conversion_status))::bigint AS missing_fx_count,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(nco.portfolio_conversion_status)) = 0 AS is_complete
    FROM investory.app_v_normalized_cash_operations nco
    JOIN investory.accounts a ON a.id = nco.account_id
    JOIN investory.portfolios pf ON pf.id = a.portfolio_id
    WHERE NOT a.cash_only
      AND nco.normalized_category IN ('DIVIDEND', 'DIVIDEND_REVERSAL')
    GROUP BY a.portfolio_id, pf.base_currency, nco.currency
)
SELECT portfolio_id, base_currency, metric_type, currency, amount_local,
       amount_in_base_currency, missing_fx_count, is_complete, NOW() AS updated_at
FROM account_latest
UNION ALL
SELECT portfolio_id, base_currency, metric_type, currency, amount_local,
       amount_in_base_currency, missing_fx_count, is_complete, NOW() AS updated_at
FROM realized
UNION ALL
SELECT portfolio_id, base_currency, metric_type, currency, amount_local,
       amount_in_base_currency, missing_fx_count, is_complete, NOW() AS updated_at
FROM unrealized
UNION ALL
SELECT portfolio_id, base_currency, metric_type, currency, amount_local,
       amount_in_base_currency, missing_fx_count, is_complete, NOW() AS updated_at
FROM dividends
WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS ux_mv_portfolio_currency_breakdown_key
    ON investory.app_v_portfolio_currency_breakdown(portfolio_id, metric_type, currency);


CREATE OR REPLACE VIEW investory.app_v_open_position_values AS
WITH position_rows AS (
         SELECT *
         FROM investory.app_v_current_open_position_rows
     ),
     position_rows_with_fx AS (
         SELECT *
         FROM position_rows
     ),
     row_values AS (
         SELECT
             pr.*,
             CASE
                 WHEN pr.market_price IS NULL THEN NULL::numeric
                 ELSE pr.volume * pr.market_price
                      * CASE WHEN price.quality_class LIKE '%PERCENT_OF_PAR%' THEN 0.01::numeric
                             ELSE 1::numeric END
                 END AS market_value_native
         FROM position_rows_with_fx pr
         LEFT JOIN investory.app_v_current_asset_price_mv price
           ON price.asset_id = pr.asset_id
     ),
     rollup AS (
         SELECT
             rv.portfolio_id,
             rv.base_currency,
             rv.account_id,
             rv.account_currency,
             rv.asset_id,
             MIN(rv.asset_symbol) AS asset_symbol,
             CASE
                 WHEN COUNT(DISTINCT rv.cost_basis_currency) = 1 THEN MIN(rv.cost_basis_currency)
                 ELSE 'MIXED'::varchar(8)
                 END AS cost_basis_currency,
             CASE
                 WHEN COUNT(DISTINCT rv.market_price_currency) = 1 THEN MIN(rv.market_price_currency)
                 ELSE 'MIXED'::varchar(8)
                 END AS market_price_currency,
             COUNT(*) AS position_row_count,
             COUNT(DISTINCT rv.cost_basis_currency) AS cost_basis_currency_count,
             COUNT(DISTINCT rv.market_price_currency) AS market_price_currency_count,
             SUM(rv.volume) AS volume,
             CASE
                 WHEN COUNT(DISTINCT rv.cost_basis_currency) = 1
                     THEN SUM(rv.cost_basis_native) / NULLIF(SUM(rv.volume), 0)
                 ELSE NULL::numeric
                 END AS average_open_price,
             CASE
                 WHEN COUNT(DISTINCT rv.cost_basis_currency) = 1
                     THEN SUM(rv.cost_basis_native)
                 ELSE NULL::numeric
                 END AS cost_basis,
             CASE
                 WHEN COUNT(DISTINCT rv.market_price_currency) = 1 THEN MIN(rv.market_price)
                 ELSE NULL::numeric
                 END AS market_price,
             CASE
                 WHEN COUNT(DISTINCT rv.market_price_currency) = 1
                     THEN SUM(rv.market_value_native)
                 ELSE NULL::numeric
                 END AS market_value,
             SUM(rv.cost_basis_in_base_currency) AS cost_basis_in_base_currency,
             SUM(rv.market_value_in_base_currency) AS market_value_in_base_currency
         FROM row_values rv
         GROUP BY rv.portfolio_id, rv.base_currency, rv.account_id, rv.account_currency, rv.asset_id
     )
SELECT
    r.portfolio_id,
    r.base_currency,
    r.account_id,
    r.account_currency,
    r.asset_id,
    r.asset_symbol,
    r.cost_basis_currency AS position_currency,
    r.market_price_currency AS market_currency,
    r.position_row_count,
    r.cost_basis_currency_count,
    r.market_price_currency_count,
    r.volume,
    r.average_open_price,
    r.cost_basis,
    r.market_price,
    r.market_value,
    CASE
        WHEN r.market_value IS NULL OR r.cost_basis IS NULL
          OR r.market_price_currency <> r.cost_basis_currency THEN NULL::numeric
        ELSE r.market_value - r.cost_basis
        END AS unrealized_pl,
    NULL::numeric AS position_to_base_rate,
    NULL::numeric AS market_to_base_rate,
    r.cost_basis_in_base_currency,
    r.market_value_in_base_currency,
    CASE
        WHEN r.market_value_in_base_currency IS NULL OR r.cost_basis_in_base_currency IS NULL THEN NULL::numeric
        ELSE r.market_value_in_base_currency - r.cost_basis_in_base_currency
        END AS unrealized_pl_in_base_currency,
    (
        r.cost_basis_in_base_currency IS NOT NULL
            AND r.market_value_in_base_currency IS NOT NULL
        ) AS fx_rate_available
FROM rollup r;


CREATE OR REPLACE VIEW investory.app_v_portfolio_daily_fx_rate AS
SELECT portfolio_id, valuation_date, source_currency, base_currency,
       fx_rate_to_base, source, rate_method, rate_source, source_rate_date,
       age_days, conversion_status
FROM investory.app_v_portfolio_daily_fx_rate_mv;
COMMENT ON VIEW investory.app_v_portfolio_daily_fx_rate IS
    'Canonical portfolio-aware and date-aware FX layer. Amount conversion exposes source, method, date, age, and explicit stale/missing status.';

CREATE OR REPLACE VIEW investory.app_v_normalized_daily_price AS
WITH position_dates AS (
    SELECT DISTINCT
        a.id AS asset_id,
        d.snapshot_date AS valuation_date
    FROM investory.positions p
    JOIN investory.assets a
        ON a.id = p.asset_id
       AND a.exclude_from_import = false
    JOIN (
        SELECT DISTINCT snapshot_date
        FROM investory.account_daily
    ) d
        ON d.snapshot_date >= COALESCE(p.open_time::date, d.snapshot_date)
       AND (
            p.close_time IS NULL
            OR d.snapshot_date < p.close_time::date
       )
),
price_candidates AS (
    SELECT
        pd.asset_id,
        pd.valuation_date,
        aph.price_date,
        aph.source_date,
        aph.source,
        aph.source_symbol,
        aph.original_source_symbol,
        aph.price_origin,
        aph.price_currency,
        aph.close_price,
        aph.price_scale_factor,
        aph.scale_reason,
        aph.quality_score,
        aph.quality_class,
        aph.estimated,
        aph.is_proxy,
        CASE
            WHEN aph.estimated AND aph.interpolation_left_date IS NOT NULL
                THEN aph.interpolation_left_date
            ELSE COALESCE(aph.source_date, aph.price_date)
        END AS effective_observation_date,
        CASE
            WHEN aph.estimated AND aph.interpolation_right_date IS NOT NULL
                THEN aph.interpolation_right_date
            ELSE NULL::date
        END AS interpolation_right_date,
        CASE
            WHEN aph.quality_class = 'EXACT_LISTING_MARKET_CLOSE' THEN 1
            WHEN aph.quality_class = 'EXACT_LISTING_SCALED' THEN 2
            WHEN aph.quality_class LIKE '%ALTERNATE%' OR aph.is_proxy THEN 3
            WHEN aph.price_origin = 'MANUAL' THEN 4
            WHEN aph.estimated OR aph.quality_class LIKE 'INTERPOLATED%' THEN 5
            WHEN aph.quality_class LIKE '%TRADE_OBSERVATION%' OR aph.price_origin LIKE '%TRADE%' THEN 6
            WHEN aph.quality_class LIKE '%STALE%' OR aph.price_origin = 'STALE_CARRY_FORWARD' THEN 7
            ELSE 9
        END AS selection_priority
    FROM position_dates pd
    JOIN investory.app_v_canonical_asset_daily_price_ranked_mv aph
        ON aph.asset_id = pd.asset_id
       AND aph.price_date <= pd.valuation_date
       AND (
            NOT aph.estimated
            OR aph.interpolation_right_date IS NULL
            OR aph.interpolation_right_date <= pd.valuation_date
       )
    WHERE CASE
              WHEN aph.estimated AND aph.interpolation_left_date IS NOT NULL
                  THEN aph.interpolation_left_date
              ELSE COALESCE(aph.source_date, aph.price_date)
          END <= pd.valuation_date
), candidates_with_latest AS (
    SELECT pc.*,
           MAX(pc.price_date) OVER (
               PARTITION BY pc.asset_id, pc.valuation_date
           ) AS latest_price_date
    FROM price_candidates pc
), ranked_prices AS (
    SELECT
        pc.*,
        ROW_NUMBER() OVER (
            PARTITION BY pc.asset_id, pc.valuation_date
            ORDER BY
                CASE
                    WHEN pc.effective_observation_date IS NULL THEN 1
                    ELSE 0
                END,
                pc.effective_observation_date DESC,
                pc.selection_priority,
                pc.quality_score DESC,
                pc.price_date DESC,
                CASE pc.price_origin
                    WHEN 'XTB_TRADE_OPEN' THEN 0
                    WHEN 'XTB_TRADE_CLOSE' THEN 2
                    ELSE 1
                END,
                pc.source,
                pc.source_symbol
        ) AS rn,
        COUNT(*) FILTER (WHERE pc.price_date = pc.latest_price_date)
            OVER (PARTITION BY pc.asset_id, pc.valuation_date) AS candidate_count_same_price_date
    FROM candidates_with_latest pc
)
SELECT
    rp.asset_id,
    rp.valuation_date,
    rp.close_price * COALESCE(rp.price_scale_factor, 1) AS selected_price,
    rp.price_date AS selected_price_date,
    rp.effective_observation_date AS underlying_observation_date,
    GREATEST(0, (rp.valuation_date - rp.effective_observation_date))::integer AS price_age_days,
    rp.price_currency,
    (rp.asset_id::varchar || ':' || rp.price_date::varchar || ':' || rp.source)::varchar(255) AS selected_price_history_id,
    rp.price_origin,
    rp.quality_class,
    rp.source_symbol,
    rp.original_source_symbol,
    rp.price_scale_factor,
    rp.scale_reason,
    rp.source AS source_name,
    NULL::bigint AS proxy_asset_id,
    (rp.estimated OR rp.quality_class LIKE 'INTERPOLATED%') AS is_interpolated,
    rp.selection_priority,
    CASE
        WHEN rp.rn IS NULL THEN 'FAIL'
        WHEN rp.close_price IS NULL OR rp.close_price <= 0 THEN 'FAIL'
        WHEN rp.price_currency IS NULL THEN 'FAIL'
        WHEN rp.selection_priority >= 7 THEN 'WARN'
        WHEN rp.selection_priority >= 3 THEN 'WARN'
        ELSE 'PASS'
    END::varchar(16) AS validation_status,
    CASE
        WHEN rp.selection_priority = 1 THEN 'exact listing market close'
        WHEN rp.selection_priority = 2 THEN 'exact listing scaled/normalized close'
        WHEN rp.selection_priority = 3 THEN 'verified alternate/proxy listing'
        WHEN rp.selection_priority = 4 THEN 'manual price'
        WHEN rp.selection_priority = 5 THEN 'interpolated price'
        WHEN rp.selection_priority = 6 THEN 'trade observation fallback'
        WHEN rp.selection_priority = 7 THEN 'stale carry-forward fallback'
        ELSE 'unclassified price source'
    END::text AS validation_message
FROM ranked_prices rp
WHERE rp.rn = 1;

COMMENT ON VIEW investory.app_v_normalized_daily_price IS
    'Independent deterministic valuation-price selector. Future observations are excluded; effective observation age uses source_date or interpolation_left_date, then freshness precedes quality/source priority. selected_price is close_price multiplied by price_scale_factor exactly once and carries the price_currency of that normalized number.';


CREATE OR REPLACE VIEW investory.app_v_normalized_daily_price AS
WITH position_dates AS (
    SELECT DISTINCT a.id AS asset_id, d.snapshot_date AS valuation_date
    FROM investory.positions p
    JOIN investory.assets a ON a.id = p.asset_id AND NOT a.exclude_from_import
    JOIN investory.account_daily d ON d.account_id = p.account_id
     AND d.snapshot_date >= COALESCE(p.open_time::date, d.snapshot_date)
     AND (p.close_time IS NULL OR d.snapshot_date < p.close_time::date)
)
SELECT w.asset_id, pd.valuation_date,
       w.close_price * COALESCE(w.price_scale_factor, 1) AS selected_price,
       w.price_date AS selected_price_date,
       w.effective_observation_date AS underlying_observation_date,
       GREATEST(0, pd.valuation_date - w.effective_observation_date)::integer AS price_age_days,
       w.price_currency,
       (w.asset_id::varchar || ':' || w.price_date::varchar || ':' || w.source)::varchar(255) AS selected_price_history_id,
       w.price_origin, w.quality_class, w.source_symbol, w.original_source_symbol,
       w.price_scale_factor, w.scale_reason, w.source AS source_name,
       NULL::bigint AS proxy_asset_id,
       (w.estimated OR w.quality_class LIKE 'INTERPOLATED%') AS is_interpolated,
       w.selection_priority,
       CASE WHEN w.close_price IS NULL OR w.close_price <= 0 THEN 'FAIL'
            WHEN w.price_currency IS NULL THEN 'FAIL'
            WHEN w.selection_priority >= 3 THEN 'WARN'
            ELSE 'PASS' END::varchar(16) AS validation_status,
       CASE w.selection_priority
            WHEN 1 THEN 'exact listing market close'
            WHEN 2 THEN 'exact listing scaled/normalized close'
            WHEN 3 THEN 'verified alternate/proxy listing'
            WHEN 4 THEN 'manual price'
            WHEN 5 THEN 'interpolated price'
            WHEN 6 THEN 'trade observation fallback'
            WHEN 7 THEN 'stale carry-forward fallback'
            ELSE 'unclassified price source' END::text AS validation_message
FROM position_dates pd
JOIN LATERAL (
    SELECT aph.*
    FROM investory.app_v_canonical_asset_daily_price_ranked_mv aph
    WHERE aph.asset_id = pd.asset_id
      AND aph.price_date <= pd.valuation_date
      AND (NOT aph.estimated OR aph.interpolation_right_date IS NULL
           OR aph.interpolation_right_date <= pd.valuation_date)
      AND aph.effective_observation_date <= pd.valuation_date
    ORDER BY CASE WHEN aph.effective_observation_date IS NULL THEN 1 ELSE 0 END,
             aph.effective_observation_date DESC, aph.selection_priority,
             aph.quality_score DESC, aph.price_date DESC,
             CASE aph.price_origin WHEN 'XTB_TRADE_OPEN' THEN 0 WHEN 'XTB_TRADE_CLOSE' THEN 2 ELSE 1 END,
             aph.source, aph.source_symbol
    LIMIT 1
) w ON true;

CREATE MATERIALIZED VIEW IF NOT EXISTS investory.app_v_normalized_daily_price_mv AS
WITH position_dates AS (
    SELECT DISTINCT a.id AS asset_id, d.snapshot_date AS valuation_date
    FROM investory.positions p
    JOIN investory.assets a ON a.id = p.asset_id AND NOT a.exclude_from_import
    JOIN investory.account_daily d ON d.account_id = p.account_id
     AND d.snapshot_date >= COALESCE(p.open_time::date, d.snapshot_date)
     AND (p.close_time IS NULL OR d.snapshot_date < p.close_time::date)
)
SELECT w.asset_id, pd.valuation_date,
       w.close_price * COALESCE(w.price_scale_factor, 1) AS selected_price,
       w.price_date AS selected_price_date,
       w.effective_observation_date AS underlying_observation_date,
       GREATEST(0, pd.valuation_date - w.effective_observation_date)::integer AS price_age_days,
       w.price_currency,
       (w.asset_id::varchar || ':' || w.price_date::varchar || ':' || w.source)::varchar(255) AS selected_price_history_id,
       w.price_origin, w.quality_class, w.source_symbol, w.original_source_symbol,
       w.price_scale_factor, w.scale_reason, w.source AS source_name,
       NULL::bigint AS proxy_asset_id,
       (w.estimated OR w.quality_class LIKE 'INTERPOLATED%') AS is_interpolated,
       w.selection_priority,
       CASE WHEN w.close_price IS NULL OR w.close_price <= 0 THEN 'FAIL'
            WHEN w.price_currency IS NULL THEN 'FAIL'
            WHEN w.selection_priority >= 3 THEN 'WARN'
            ELSE 'PASS' END::varchar(16) AS validation_status,
       CASE w.selection_priority
            WHEN 1 THEN 'exact listing market close'
            WHEN 2 THEN 'exact listing scaled/normalized close'
            WHEN 3 THEN 'verified alternate/proxy listing'
            WHEN 4 THEN 'manual price'
            WHEN 5 THEN 'interpolated price'
            WHEN 6 THEN 'trade observation fallback'
            WHEN 7 THEN 'stale carry-forward fallback'
            ELSE 'unclassified price source' END::text AS validation_message
FROM position_dates pd
JOIN LATERAL (
    SELECT aph.*
    FROM investory.app_v_canonical_asset_daily_price_ranked_mv aph
    WHERE aph.asset_id = pd.asset_id
      AND aph.price_date <= pd.valuation_date
      AND (NOT aph.estimated OR aph.interpolation_right_date IS NULL
           OR aph.interpolation_right_date <= pd.valuation_date)
      AND aph.effective_observation_date <= pd.valuation_date
    ORDER BY CASE WHEN aph.effective_observation_date IS NULL THEN 1 ELSE 0 END,
             aph.effective_observation_date DESC, aph.selection_priority,
             aph.quality_score DESC, aph.price_date DESC,
             CASE aph.price_origin WHEN 'XTB_TRADE_OPEN' THEN 0 WHEN 'XTB_TRADE_CLOSE' THEN 2 ELSE 1 END,
             aph.source, aph.source_symbol
    LIMIT 1
) w ON true
WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS ux_app_v_normalized_daily_price_mv_key
    ON investory.app_v_normalized_daily_price_mv(asset_id, valuation_date);
ANALYZE investory.app_v_normalized_daily_price_mv;

CREATE OR REPLACE VIEW investory.app_v_normalized_daily_price AS
SELECT * FROM investory.app_v_normalized_daily_price_mv;

-- Reconstructed positions expand each open lot across every account_daily date and then resolve
-- price and FX.  It is the shared expensive fact behind reconciliation and valuation diagnostics.











CREATE MATERIALIZED VIEW IF NOT EXISTS investory.app_v_portfolio_asset_allocation AS
SELECT
    v.portfolio_id,
    v.base_currency,
    v.asset_id,
    v.asset_symbol,
    SUM(v.volume) AS total_volume,
    CASE WHEN COUNT(DISTINCT v.market_currency) = 1 THEN MIN(v.market_price) END AS market_price,
    CASE WHEN COUNT(DISTINCT v.market_currency) = 1 THEN MIN(v.market_currency) END AS market_price_currency,
    CASE WHEN COUNT(*) FILTER (WHERE NOT v.fx_rate_available) > 0 THEN NULL::numeric
         ELSE SUM(v.cost_basis_in_base_currency) END AS cost_basis_in_base_currency,
    CASE WHEN COUNT(*) FILTER (WHERE NOT v.fx_rate_available) > 0 THEN NULL::numeric
         ELSE SUM(v.market_value_in_base_currency) END AS total_value_in_base_currency,
    CASE WHEN COUNT(*) FILTER (WHERE NOT v.fx_rate_available) > 0 THEN NULL::numeric
         ELSE SUM(v.unrealized_pl_in_base_currency) END AS unrealized_pl_in_base_currency,
    SUM(v.market_value_in_base_currency) FILTER (WHERE v.fx_rate_available)
        AS converted_value_subtotal,
    COUNT(*) FILTER (WHERE NOT v.fx_rate_available)::bigint AS missing_fx_count,
    COUNT(*) FILTER (WHERE NOT v.fx_rate_available) = 0 AS is_complete,
    NOW() AS updated_at
FROM investory.app_v_open_position_values v
GROUP BY v.portfolio_id, v.base_currency, v.asset_id, v.asset_symbol
WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS ux_mv_portfolio_asset_allocation_key
    ON investory.app_v_portfolio_asset_allocation(portfolio_id, asset_id);

CREATE MATERIALIZED VIEW IF NOT EXISTS investory.app_v_symbol_performance AS
WITH latest_positions AS (
    SELECT
        v.portfolio_id,
        v.asset_id,
        CASE WHEN COUNT(*) FILTER (WHERE NOT v.fx_rate_available) > 0 THEN NULL::numeric
             ELSE SUM(v.unrealized_pl_in_base_currency) END AS unrealized_profit,
        CASE WHEN COUNT(*) FILTER (WHERE NOT v.fx_rate_available) > 0 THEN NULL::numeric
             ELSE SUM(v.cost_basis_in_base_currency) END AS cost_basis,
        CASE WHEN COUNT(*) FILTER (WHERE NOT v.fx_rate_available) > 0 THEN NULL::numeric
             ELSE SUM(v.market_value_in_base_currency) END AS market_value,
        COUNT(*) FILTER (WHERE NOT v.fx_rate_available)::bigint AS missing_fx_count,
        COUNT(*) FILTER (WHERE NOT v.fx_rate_available) = 0 AS is_complete,
        SUM(v.volume) AS total_volume
    FROM investory.app_v_open_position_values v
    GROUP BY v.portfolio_id, v.asset_id
),
closed_position_components AS (
    SELECT
        a.portfolio_id,
        asset.id AS asset_id,
        p.profit_currency::varchar(3) AS source_currency,
        pf.base_currency::varchar(3) AS base_currency,
        p.close_time::date AS valuation_date,
        COALESCE(p.profit, 0) + COALESCE(p.swap, 0) AS amount_native
    FROM investory.positions p
    JOIN investory.accounts a
        ON a.id = p.account_id
    JOIN investory.portfolios pf
        ON pf.id = a.portfolio_id
    JOIN investory.assets asset
        ON asset.id = p.asset_id
       AND asset.exclude_from_import = false
    WHERE p.close_time IS NOT NULL
      AND p.asset_id IS NOT NULL
    UNION ALL
    SELECT
        a.portfolio_id,
        asset.id AS asset_id,
        p.commission_currency::varchar(3) AS source_currency,
        pf.base_currency::varchar(3) AS base_currency,
        p.close_time::date AS valuation_date,
        COALESCE(p.commission, 0) AS amount_native
    FROM investory.positions p
    JOIN investory.accounts a
        ON a.id = p.account_id
    JOIN investory.portfolios pf
        ON pf.id = a.portfolio_id
    JOIN investory.assets asset
        ON asset.id = p.asset_id
       AND asset.exclude_from_import = false
    WHERE p.close_time IS NOT NULL
      AND p.asset_id IS NOT NULL
),
closed_positions AS (
    SELECT
        cpr.portfolio_id,
        cpr.asset_id,
        SUM(
            CASE
                WHEN investory.fx_status_usable(fx.conversion_status)
                    THEN cpr.amount_native * fx.fx_rate_to_target
                ELSE NULL::numeric
            END
        ) AS closed_profit,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(fx.conversion_status))::bigint AS missing_fx_count,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(fx.conversion_status)) = 0 AS is_complete
    FROM closed_position_components cpr
    LEFT JOIN LATERAL investory.resolve_fx_rate(
        cpr.valuation_date,
        cpr.source_currency,
        cpr.base_currency
    ) fx ON true
    GROUP BY cpr.portfolio_id, cpr.asset_id
),
cash_dividends AS (
    SELECT
        a.portfolio_id,
        asset.id AS asset_id,
        SUM(nco.amount_in_portfolio_base_currency) FILTER (
            WHERE nco.normalized_category IN ('DIVIDEND', 'DIVIDEND_REVERSAL')) AS dividends,
        SUM(-nco.amount_in_portfolio_base_currency) FILTER (
            WHERE nco.normalized_category IN ('WITHHOLDING_TAX', 'WITHHOLDING_TAX_REVERSAL')) AS withholding_tax,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(nco.portfolio_conversion_status))::bigint AS missing_fx_count,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(nco.portfolio_conversion_status)) = 0 AS is_complete
    FROM investory.app_v_normalized_cash_operations nco
    JOIN investory.accounts a
      ON a.id = nco.account_id
    JOIN investory.assets asset
      ON asset.id = nco.asset_id
     AND asset.exclude_from_import = false
    GROUP BY a.portfolio_id, asset.id
)
SELECT
    COALESCE(lp.portfolio_id, cp.portfolio_id, cd.portfolio_id) AS portfolio_id,
    asset.symbol AS symbol,
    asset.id AS asset_id,
    CASE
        WHEN COALESCE(cp.is_complete, true) THEN COALESCE(cp.closed_profit, 0)
        ELSE NULL::numeric
    END AS closed_profit,
    CASE WHEN COALESCE(lp.is_complete, true) THEN COALESCE(lp.unrealized_profit, 0)
         ELSE NULL::numeric END AS unrealized_profit,
    CASE
        WHEN COALESCE(lp.is_complete, true) AND COALESCE(cp.is_complete, true)
          AND COALESCE(cd.is_complete, true)
            THEN COALESCE(cp.closed_profit, 0)
        + COALESCE(lp.unrealized_profit, 0)
        + COALESCE(cd.dividends, 0)
        - COALESCE(cd.withholding_tax, 0)
        ELSE NULL::numeric
    END AS total_profit,
    CASE
        WHEN COALESCE(cd.is_complete, true) THEN COALESCE(cd.dividends, 0)
        ELSE NULL::numeric
    END AS dividends,
    CASE
        WHEN COALESCE(cd.is_complete, true) THEN COALESCE(cd.withholding_tax, 0)
        ELSE NULL::numeric
    END AS withholding_tax,
    COALESCE(lp.total_volume, 0) AS total_volume,
    CASE WHEN COALESCE(lp.is_complete, true) THEN COALESCE(lp.cost_basis, 0)
         ELSE NULL::numeric END AS cost_basis,
    CASE WHEN COALESCE(lp.is_complete, true) THEN COALESCE(lp.market_value, 0)
         ELSE NULL::numeric END AS market_value,
    COALESCE(lp.missing_fx_count, 0) + COALESCE(cp.missing_fx_count, 0)
        + COALESCE(cd.missing_fx_count, 0) AS missing_fx_count,
    COALESCE(lp.is_complete, true) AND COALESCE(cp.is_complete, true)
        AND COALESCE(cd.is_complete, true) AS is_complete,
    NOW() AS updated_at
FROM latest_positions lp
FULL OUTER JOIN closed_positions cp
    ON cp.portfolio_id = lp.portfolio_id
   AND cp.asset_id = lp.asset_id
FULL OUTER JOIN cash_dividends cd
    ON cd.portfolio_id = COALESCE(lp.portfolio_id, cp.portfolio_id)
   AND cd.asset_id = COALESCE(lp.asset_id, cp.asset_id)
JOIN investory.assets asset
    ON asset.id = COALESCE(lp.asset_id, cp.asset_id, cd.asset_id)
WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS ux_mv_symbol_performance_key
    ON investory.app_v_symbol_performance(portfolio_id, asset_id);






CREATE OR REPLACE VIEW investory.app_v_account_monthly_benchmark AS
SELECT
    monthly.account_id,
    monthly.month,
    monthly.first_date,
    monthly.end_date,
    monthly.opening_equity,
    monthly.closing_equity,
    monthly.valuation_currency,
    monthly.deposits,
    monthly.withdrawals,
    monthly.dividends,
    monthly.interest,
    monthly.fees,
    monthly.taxes,
    monthly.realized_profit,
    monthly.total_profit,
    monthly.compounded_monthly_return,
    monthly.updated_at
FROM investory.app_v_account_monthly monthly;

COMMENT ON VIEW investory.app_v_account_monthly_benchmark IS
    'Benchmark monthly account performance projection. Portfolio P/L and return come from app_v_account_monthly, whose canonical source is account_daily; boundary equity and flows remain reconciliation data.';

COMMENT ON COLUMN investory.assets.asset_type IS
    'Current broad instrument classification. Sector allocation must not be introduced until a canonical sector taxonomy and an explicit asset-to-sector mapping are defined.';

SET search_path TO investory, public;

-- Keep the finalization audit from expanding the valuation reconstruction more
-- than necessary. The previous definition evaluated the same expensive view
-- once for errors and once for warnings, and found the latest date by scanning
-- that view a second time.









CREATE MATERIALIZED VIEW IF NOT EXISTS investory.app_v_portfolio_contribution_summary_mv AS
WITH contribution_rows AS (
    SELECT
        p.id AS portfolio_id,
        p.base_currency,
        nco.portfolio_conversion_status,
        CASE
            WHEN nco.normalized_category = 'EXTERNAL_DEPOSIT' THEN 'EXTERNAL_DEPOSIT'
            WHEN nco.normalized_category = 'EXTERNAL_WITHDRAWAL' THEN 'EXTERNAL_WITHDRAWAL'
            WHEN nco.normalized_category = 'INTERNAL_BOOKKEEPING'
             AND nco.comment ~* 'transfer from [0-9]+ to [0-9]+'
             AND substring(nco.comment from '(?i)to ([0-9]+)')::bigint = nco.account_id
             AND nco.amount > 0
             AND NOT EXISTS (
                 SELECT 1
                 FROM investory.accounts counterparty
                 WHERE counterparty.id = substring(
                     nco.comment from '(?i)transfer from ([0-9]+)')::bigint
             ) THEN 'BOUNDARY_TRANSFER'
            WHEN nco.normalized_category = 'INTERNAL_BOOKKEEPING'
             AND nco.comment ~* 'transfer from [0-9]+ to [0-9]+'
             AND substring(nco.comment from '(?i)transfer from ([0-9]+)')::bigint = nco.account_id
             AND nco.amount < 0
             AND NOT EXISTS (
                 SELECT 1
                 FROM investory.accounts counterparty
                 WHERE counterparty.id = substring(
                     nco.comment from '(?i)to ([0-9]+)')::bigint
             ) THEN 'BOUNDARY_TRANSFER'
            ELSE NULL
        END AS contribution_kind,
        nco.amount_in_portfolio_base_currency AS amount_in_base_currency
    FROM investory.app_v_normalized_cash_operations nco
    JOIN investory.accounts account ON account.id = nco.account_id
    JOIN investory.portfolios p ON p.id = account.portfolio_id
), contribution_totals AS (
    SELECT
        portfolio_id,
        SUM(amount_in_base_currency) FILTER (
            WHERE contribution_kind = 'EXTERNAL_DEPOSIT') AS external_deposits,
        SUM(-amount_in_base_currency) FILTER (
            WHERE contribution_kind = 'EXTERNAL_WITHDRAWAL') AS external_withdrawals,
        SUM(amount_in_base_currency) FILTER (
            WHERE contribution_kind = 'BOUNDARY_TRANSFER') AS boundary_transfer_net,
        COUNT(*) FILTER (
            WHERE contribution_kind IS NOT NULL
              AND NOT investory.fx_status_usable(portfolio_conversion_status))::bigint
            AS missing_fx_count
    FROM contribution_rows
    GROUP BY portfolio_id
)
SELECT
    p.id AS portfolio_id,
    p.base_currency,
    CASE WHEN COALESCE(t.missing_fx_count, 0) > 0 THEN NULL::numeric ELSE
        COALESCE(t.external_deposits, 0)
            + GREATEST(COALESCE(t.boundary_transfer_net, 0), 0)
    END AS total_deposits,
    CASE WHEN COALESCE(t.missing_fx_count, 0) > 0 THEN NULL::numeric ELSE
        COALESCE(t.external_withdrawals, 0)
            + GREATEST(-COALESCE(t.boundary_transfer_net, 0), 0)
    END AS total_withdrawals,
    CASE WHEN COALESCE(t.missing_fx_count, 0) > 0 THEN NULL::numeric ELSE
        COALESCE(t.external_deposits, 0)
            - COALESCE(t.external_withdrawals, 0)
            + COALESCE(t.boundary_transfer_net, 0)
    END AS net_deposits,
    COALESCE(t.external_deposits, 0) AS external_deposits,
    COALESCE(t.external_withdrawals, 0) AS external_withdrawals,
    COALESCE(t.boundary_transfer_net, 0) AS boundary_transfer_net,
    COALESCE(t.missing_fx_count, 0) AS missing_fx_count,
    COALESCE(t.missing_fx_count, 0) = 0 AS is_complete,
    NOW() AS updated_at
FROM investory.portfolios p
LEFT JOIN contribution_totals t ON t.portfolio_id = p.id;

CREATE UNIQUE INDEX IF NOT EXISTS ux_mv_app_v_portfolio_contribution_summary_mv_portfolio
    ON investory.app_v_portfolio_contribution_summary_mv(portfolio_id);

COMMENT ON MATERIALIZED VIEW investory.app_v_portfolio_contribution_summary_mv IS
    'Portfolio external contributions with boundary transfers netted before their signed net is assigned to deposits or withdrawals. Tracked-account transfers are excluded.';

CREATE OR REPLACE VIEW investory.app_v_portfolio_kpi_summary AS
SELECT
    src.portfolio_id,
    src.portfolio_name,
    src.base_currency,
    investory.application_display_value(contributions.total_deposits) AS total_deposits,
    investory.application_display_value(contributions.total_withdrawals) AS total_withdrawals,
    investory.application_display_value(contributions.net_deposits) AS net_deposits,
    investory.application_display_value(src.total_cash) AS total_cash,
    investory.application_display_value(src.total_market_value) AS total_market_value,
    investory.application_display_value(src.total_equity) AS total_equity,
    investory.application_display_value(src.total_realized_profit) AS total_realized_profit,
    investory.application_display_value(src.total_unrealized_profit) AS total_unrealized_profit,
    investory.application_display_value(src.total_dividends) AS total_dividends,
    investory.application_display_value(src.total_interest) AS total_interest,
    investory.application_display_value(src.total_fees) AS total_fees,
    investory.application_display_value(src.total_taxes) AS total_taxes,
    investory.application_display_value(src.converted_cash_subtotal) AS converted_cash_subtotal,
    investory.application_display_value(src.converted_equity_subtotal) AS converted_equity_subtotal,
    GREATEST(src.missing_fx_count, contributions.missing_fx_count) AS missing_fx_count,
    src.is_complete AND contributions.is_complete AS is_complete,
    src.activity_count,
    src.first_activity_at,
    src.last_activity_at,
    src.source_max_date,
    GREATEST(src.updated_at, contributions.updated_at) AS updated_at
FROM investory.app_v_portfolio_kpi_summary_mv src
JOIN investory.app_v_portfolio_contribution_summary_mv contributions
    ON contributions.portfolio_id = src.portfolio_id;

COMMENT ON VIEW investory.app_v_portfolio_kpi_summary IS
    'Application-facing KPI view. Contribution gross amounts use external flows plus the signed net of transfers crossing the tracked-account boundary.';

-- Consolidated from post-baseline reporting-read-model work.
SET search_path TO investory, public;

CREATE OR REPLACE VIEW investory.app_v_account_statistics_reporting AS
SELECT s.*,
       a.cash_only,
       (abs(COALESCE(s.cash_balance, 0) + COALESCE(s.market_value, 0)) >= 50
        OR abs(COALESCE(s.account_net_deposit, 0)) >= 50
        OR abs(COALESCE(s.net_deposit, 0)) >= 50) AS is_visible
FROM investory.app_v_account_statistics s
JOIN investory.accounts a ON a.id = s.account_id;

COMMENT ON VIEW investory.app_v_account_statistics_reporting IS
  'Authoritative account reporting boundary. cash_only comes from accounts and visibility uses one 50-unit threshold rule.';

-- Request-time reporting reads consume these projections. Current open-position values remain
-- owned by the live valuation views and are intentionally not copied here.
CREATE OR REPLACE VIEW investory.app_v_portfolio_monthly_summary AS
SELECT
    m.portfolio_id,
    m.portfolio_id::text || ':' || to_char(m.month, 'YYYY-MM') AS portfolio_month_key,
    m.month,
    m.opening_equity,
    m.closing_equity,
    m.deposits,
    m.withdrawals,
    m.deposits - m.withdrawals AS net_external_flow,
    m.total_profit,
    m.total_profit - m.realized_profit - m.dividends - m.interest - m.fees - m.taxes AS market_fx,
    m.realized_profit AS realized,
    m.dividends,
    m.interest,
    m.fees,
    m.taxes,
    COALESCE((
        SELECT COUNT(*)
        FROM investory.app_v_account_monthly am
        JOIN investory.accounts a ON a.id = am.account_id
        WHERE a.portfolio_id = m.portfolio_id
          AND NOT a.cash_only
          AND am.month = m.month
          AND (abs(am.closing_equity) >= 50
               OR abs(am.total_profit) >= 0.005
               OR abs(am.deposits - am.withdrawals) >= 0.005)
    ), 0)::bigint AS active_account_count
FROM investory.app_v_portfolio_monthly m;

CREATE OR REPLACE VIEW investory.app_v_account_monthly_attribution AS
SELECT
    m.account_id,
    m.account_id::text || ':' || to_char(m.month, 'YYYY-MM') AS account_month_key,
    a.portfolio_id,
    m.month,
    m.opening_equity,
    m.closing_equity,
    m.deposits - m.withdrawals AS net_cashflow,
    m.total_profit AS profit,
    CASE WHEN abs(s.total_profit) < 0.000001 THEN 0::numeric
         ELSE m.total_profit / s.total_profit * 100 END AS contribution_pct
FROM investory.app_v_account_monthly m
JOIN investory.accounts a ON a.id = m.account_id AND NOT a.cash_only
JOIN investory.app_v_portfolio_monthly_summary s
  ON s.portfolio_id = a.portfolio_id AND s.month = m.month;

-- One row per portfolio/year, with close-date historical FX applied once in the read model.
CREATE OR REPLACE VIEW investory.app_v_portfolio_tax_year_realized AS
SELECT
    a.portfolio_id,
    a.portfolio_id::text || ':' || extract(year FROM p.close_time)::text AS portfolio_tax_year_key,
    extract(year FROM p.close_time)::int AS tax_year,
    SUM(
      (COALESCE(p.profit, 0) + COALESCE(p.swap, 0)) * profit_fx.fx_rate_to_target
      + COALESCE(p.commission, 0) * commission_fx.fx_rate_to_target
    ) AS realized_result
FROM investory.positions p
JOIN investory.accounts a ON a.id = p.account_id
JOIN investory.portfolios portfolio ON portfolio.id = a.portfolio_id
CROSS JOIN LATERAL investory.resolve_fx_rate(
    p.close_time::date, p.profit_currency, portfolio.base_currency::varchar(3)
) profit_fx
CROSS JOIN LATERAL investory.resolve_fx_rate(
    p.close_time::date, p.commission_currency, portfolio.base_currency::varchar(3)
) commission_fx
WHERE p.close_time IS NOT NULL
  AND investory.fx_status_usable(profit_fx.conversion_status)
  AND investory.fx_status_usable(commission_fx.conversion_status)
GROUP BY a.portfolio_id, extract(year FROM p.close_time);

COMMENT ON VIEW investory.app_v_portfolio_monthly_summary IS
  'Authoritative stable portfolio/month attribution. Live current valuation is not persisted here.';
COMMENT ON VIEW investory.app_v_account_monthly_attribution IS
  'Authoritative non-cash-only account/month contribution rows; contribution percentage is database-derived.';
COMMENT ON VIEW investory.app_v_portfolio_tax_year_realized IS
  'Historical closed-position results by tax year, converted using close-date FX; tax carry-forward policy remains TaxCalculator-owned.';

-- The released KPI view predates roi_pct. PostgreSQL cannot add a column with
-- CREATE OR REPLACE VIEW when that would shift existing column positions.
DROP VIEW IF EXISTS investory.app_v_portfolio_kpi_summary;

CREATE VIEW investory.app_v_portfolio_kpi_summary AS
SELECT src.portfolio_id, src.portfolio_name, src.base_currency,
       investory.application_display_value(contributions.total_deposits) AS total_deposits,
       investory.application_display_value(contributions.total_withdrawals) AS total_withdrawals,
       investory.application_display_value(contributions.net_deposits) AS net_deposits,
       investory.application_display_value(src.total_cash) AS total_cash,
       investory.application_display_value(src.total_market_value) AS total_market_value,
       investory.application_display_value(src.total_equity) AS total_equity,
       investory.application_display_value(src.total_realized_profit) AS total_realized_profit,
       investory.application_display_value(src.total_unrealized_profit) AS total_unrealized_profit,
       investory.application_display_value(src.total_dividends) AS total_dividends,
       investory.application_display_value(src.total_interest) AS total_interest,
       investory.application_display_value(src.total_fees) AS total_fees,
       investory.application_display_value(src.total_taxes) AS total_taxes,
       CASE WHEN contributions.net_deposits > 0
            THEN investory.application_display_value(
                (src.total_equity - contributions.net_deposits) / contributions.net_deposits * 100)
            ELSE 0::numeric END AS roi_pct,
       investory.application_display_value(src.converted_cash_subtotal) AS converted_cash_subtotal,
       investory.application_display_value(src.converted_equity_subtotal) AS converted_equity_subtotal,
       GREATEST(src.missing_fx_count, contributions.missing_fx_count) AS missing_fx_count,
       src.is_complete AND contributions.is_complete AS is_complete,
       src.activity_count, src.first_activity_at, src.last_activity_at, src.source_max_date,
       GREATEST(src.updated_at, contributions.updated_at) AS updated_at
FROM investory.app_v_portfolio_kpi_summary_mv src
JOIN investory.app_v_portfolio_contribution_summary_mv contributions
  ON contributions.portfolio_id = src.portfolio_id;


-- Consolidated from post-baseline canonical-price ranking work.
-- Persist canonical-price ranking keys for the daily valuation selector.
CREATE MATERIALIZED VIEW IF NOT EXISTS investory.app_v_canonical_asset_daily_price_ranked_mv AS
SELECT cp.*,
       CASE WHEN cp.estimated AND cp.interpolation_left_date IS NOT NULL
            THEN cp.interpolation_left_date
            ELSE COALESCE(cp.source_date, cp.price_date) END AS effective_observation_date,
       CASE
           WHEN cp.quality_class = 'EXACT_LISTING_MARKET_CLOSE' THEN 1
           WHEN cp.quality_class = 'EXACT_LISTING_SCALED' THEN 2
           WHEN cp.quality_class LIKE '%ALTERNATE%' OR cp.is_proxy THEN 3
           WHEN cp.price_origin = 'MANUAL' THEN 4
           WHEN cp.estimated OR cp.quality_class LIKE 'INTERPOLATED%' THEN 5
           WHEN cp.quality_class LIKE '%TRADE_OBSERVATION%' OR cp.price_origin LIKE '%TRADE%' THEN 6
           WHEN cp.quality_class LIKE '%STALE%' OR cp.price_origin = 'STALE_CARRY_FORWARD' THEN 7
           ELSE 9
       END AS selection_priority
FROM investory.app_v_canonical_asset_daily_price_mv cp
WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS ux_app_v_canonical_asset_daily_price_ranked_mv_key
    ON investory.app_v_canonical_asset_daily_price_ranked_mv(asset_id, price_date);
CREATE INDEX IF NOT EXISTS ix_app_v_canonical_asset_daily_price_ranked_mv_order
    ON investory.app_v_canonical_asset_daily_price_ranked_mv(
        asset_id, effective_observation_date DESC, selection_priority,
        quality_score DESC, price_date DESC, source, source_symbol);
ANALYZE investory.app_v_canonical_asset_daily_price_ranked_mv;


-- Consolidated from post-baseline normalized-price lookup work.

-- Use the indexed canonical ranking keys and stop after the winner for each
-- position/date pair. No candidate-count columns are part of this public view.
CREATE OR REPLACE VIEW investory.app_v_normalized_daily_price AS
WITH position_dates AS (
    SELECT DISTINCT a.id AS asset_id, d.snapshot_date AS valuation_date
    FROM investory.positions p
    JOIN investory.assets a ON a.id = p.asset_id AND NOT a.exclude_from_import
    JOIN (SELECT DISTINCT snapshot_date FROM investory.account_daily) d
      ON d.snapshot_date >= COALESCE(p.open_time::date, d.snapshot_date)
     AND (p.close_time IS NULL OR d.snapshot_date < p.close_time::date)
)
SELECT w.asset_id, pd.valuation_date,
       w.close_price * COALESCE(w.price_scale_factor, 1) AS selected_price,
       w.price_date AS selected_price_date,
       w.effective_observation_date AS underlying_observation_date,
       GREATEST(0, pd.valuation_date - w.effective_observation_date)::integer AS price_age_days,
       w.price_currency,
       (w.asset_id::varchar || ':' || w.price_date::varchar || ':' || w.source)::varchar(255) AS selected_price_history_id,
       w.price_origin, w.quality_class, w.source_symbol, w.original_source_symbol,
       w.price_scale_factor, w.scale_reason, w.source AS source_name,
       NULL::bigint AS proxy_asset_id,
       (w.estimated OR w.quality_class LIKE 'INTERPOLATED%') AS is_interpolated,
       w.selection_priority,
       CASE WHEN w.close_price IS NULL OR w.close_price <= 0 THEN 'FAIL'
            WHEN w.price_currency IS NULL THEN 'FAIL'
            WHEN w.selection_priority >= 3 THEN 'WARN'
            ELSE 'PASS' END::varchar(16) AS validation_status,
       CASE w.selection_priority
            WHEN 1 THEN 'exact listing market close'
            WHEN 2 THEN 'exact listing scaled/normalized close'
            WHEN 3 THEN 'verified alternate/proxy listing'
            WHEN 4 THEN 'manual price'
            WHEN 5 THEN 'interpolated price'
            WHEN 6 THEN 'trade observation fallback'
            WHEN 7 THEN 'stale carry-forward fallback'
            ELSE 'unclassified price source' END::text AS validation_message
FROM position_dates pd
JOIN LATERAL (
    SELECT aph.*
    FROM investory.app_v_canonical_asset_daily_price_ranked_mv aph
    WHERE aph.asset_id = pd.asset_id
      AND aph.price_date <= pd.valuation_date
      AND (NOT aph.estimated OR aph.interpolation_right_date IS NULL
           OR aph.interpolation_right_date <= pd.valuation_date)
      AND aph.effective_observation_date <= pd.valuation_date
    ORDER BY CASE WHEN aph.effective_observation_date IS NULL THEN 1 ELSE 0 END,
             aph.effective_observation_date DESC, aph.selection_priority,
             aph.quality_score DESC, aph.price_date DESC,
             CASE aph.price_origin WHEN 'XTB_TRADE_OPEN' THEN 0 WHEN 'XTB_TRADE_CLOSE' THEN 2 ELSE 1 END,
             aph.source, aph.source_symbol
    LIMIT 1
) w ON true;


-- Consolidated from post-baseline normalized-price date-scope work.

-- Scope valuation dates to the position's account. This preserves the live
-- key set and avoids the global date relation used by the original view.
CREATE OR REPLACE VIEW investory.app_v_normalized_daily_price AS
WITH position_dates AS (
    SELECT DISTINCT a.id AS asset_id, d.snapshot_date AS valuation_date
    FROM investory.positions p
    JOIN investory.assets a ON a.id = p.asset_id AND NOT a.exclude_from_import
    JOIN investory.account_daily d ON d.account_id = p.account_id
     AND d.snapshot_date >= COALESCE(p.open_time::date, d.snapshot_date)
     AND (p.close_time IS NULL OR d.snapshot_date < p.close_time::date)
)
SELECT w.asset_id, pd.valuation_date,
       w.close_price * COALESCE(w.price_scale_factor, 1) AS selected_price,
       w.price_date AS selected_price_date,
       w.effective_observation_date AS underlying_observation_date,
       GREATEST(0, pd.valuation_date - w.effective_observation_date)::integer AS price_age_days,
       w.price_currency,
       (w.asset_id::varchar || ':' || w.price_date::varchar || ':' || w.source)::varchar(255) AS selected_price_history_id,
       w.price_origin, w.quality_class, w.source_symbol, w.original_source_symbol,
       w.price_scale_factor, w.scale_reason, w.source AS source_name,
       NULL::bigint AS proxy_asset_id,
       (w.estimated OR w.quality_class LIKE 'INTERPOLATED%') AS is_interpolated,
       w.selection_priority,
       CASE WHEN w.close_price IS NULL OR w.close_price <= 0 THEN 'FAIL'
            WHEN w.price_currency IS NULL THEN 'FAIL'
            WHEN w.selection_priority >= 3 THEN 'WARN'
            ELSE 'PASS' END::varchar(16) AS validation_status,
       CASE w.selection_priority
            WHEN 1 THEN 'exact listing market close'
            WHEN 2 THEN 'exact listing scaled/normalized close'
            WHEN 3 THEN 'verified alternate/proxy listing'
            WHEN 4 THEN 'manual price'
            WHEN 5 THEN 'interpolated price'
            WHEN 6 THEN 'trade observation fallback'
            WHEN 7 THEN 'stale carry-forward fallback'
            ELSE 'unclassified price source' END::text AS validation_message
FROM position_dates pd
JOIN LATERAL (
    SELECT aph.*
    FROM investory.app_v_canonical_asset_daily_price_ranked_mv aph
    WHERE aph.asset_id = pd.asset_id
      AND aph.price_date <= pd.valuation_date
      AND (NOT aph.estimated OR aph.interpolation_right_date IS NULL
           OR aph.interpolation_right_date <= pd.valuation_date)
      AND aph.effective_observation_date <= pd.valuation_date
    ORDER BY CASE WHEN aph.effective_observation_date IS NULL THEN 1 ELSE 0 END,
             aph.effective_observation_date DESC, aph.selection_priority,
             aph.quality_score DESC, aph.price_date DESC,
             CASE aph.price_origin WHEN 'XTB_TRADE_OPEN' THEN 0 WHEN 'XTB_TRADE_CLOSE' THEN 2 ELSE 1 END,
             aph.source, aph.source_symbol
    LIMIT 1
) w ON true;


-- Consolidated from post-baseline normalized-price materialization work.
-- Daily normalized prices are a shared read surface. Materialize the
-- expensive position/date price selection and keep the public view contract.
CREATE MATERIALIZED VIEW IF NOT EXISTS investory.app_v_normalized_daily_price_mv AS
WITH position_dates AS (
    SELECT DISTINCT a.id AS asset_id, d.snapshot_date AS valuation_date
    FROM investory.positions p
    JOIN investory.assets a ON a.id = p.asset_id AND NOT a.exclude_from_import
    JOIN investory.account_daily d ON d.account_id = p.account_id
     AND d.snapshot_date >= COALESCE(p.open_time::date, d.snapshot_date)
     AND (p.close_time IS NULL OR d.snapshot_date < p.close_time::date)
)
SELECT w.asset_id, pd.valuation_date,
       w.close_price * COALESCE(w.price_scale_factor, 1) AS selected_price,
       w.price_date AS selected_price_date,
       w.effective_observation_date AS underlying_observation_date,
       GREATEST(0, pd.valuation_date - w.effective_observation_date)::integer AS price_age_days,
       w.price_currency,
       (w.asset_id::varchar || ':' || w.price_date::varchar || ':' || w.source)::varchar(255) AS selected_price_history_id,
       w.price_origin, w.quality_class, w.source_symbol, w.original_source_symbol,
       w.price_scale_factor, w.scale_reason, w.source AS source_name,
       NULL::bigint AS proxy_asset_id,
       (w.estimated OR w.quality_class LIKE 'INTERPOLATED%') AS is_interpolated,
       w.selection_priority,
       CASE WHEN w.close_price IS NULL OR w.close_price <= 0 THEN 'FAIL'
            WHEN w.price_currency IS NULL THEN 'FAIL'
            WHEN w.selection_priority >= 3 THEN 'WARN'
            ELSE 'PASS' END::varchar(16) AS validation_status,
       CASE w.selection_priority
            WHEN 1 THEN 'exact listing market close'
            WHEN 2 THEN 'exact listing scaled/normalized close'
            WHEN 3 THEN 'verified alternate/proxy listing'
            WHEN 4 THEN 'manual price'
            WHEN 5 THEN 'interpolated price'
            WHEN 6 THEN 'trade observation fallback'
            WHEN 7 THEN 'stale carry-forward fallback'
            ELSE 'unclassified price source' END::text AS validation_message
FROM position_dates pd
JOIN LATERAL (
    SELECT aph.*
    FROM investory.app_v_canonical_asset_daily_price_ranked_mv aph
    WHERE aph.asset_id = pd.asset_id
      AND aph.price_date <= pd.valuation_date
      AND (NOT aph.estimated OR aph.interpolation_right_date IS NULL
           OR aph.interpolation_right_date <= pd.valuation_date)
      AND aph.effective_observation_date <= pd.valuation_date
    ORDER BY CASE WHEN aph.effective_observation_date IS NULL THEN 1 ELSE 0 END,
             aph.effective_observation_date DESC, aph.selection_priority,
             aph.quality_score DESC, aph.price_date DESC,
             CASE aph.price_origin WHEN 'XTB_TRADE_OPEN' THEN 0 WHEN 'XTB_TRADE_CLOSE' THEN 2 ELSE 1 END,
             aph.source, aph.source_symbol
    LIMIT 1
) w ON true
WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS ux_app_v_normalized_daily_price_mv_key
    ON investory.app_v_normalized_daily_price_mv(asset_id, valuation_date);
ANALYZE investory.app_v_normalized_daily_price_mv;

CREATE OR REPLACE VIEW investory.app_v_normalized_daily_price AS
SELECT * FROM investory.app_v_normalized_daily_price_mv;


-- Consolidated from post-baseline symbol-performance FX reuse work.
-- Rebuild the independent symbol-performance MV using the already refreshed portfolio FX map.
-- The old MV has no downstream materialized-view dependency.
DROP MATERIALIZED VIEW IF EXISTS investory.app_v_symbol_performance;

CREATE MATERIALIZED VIEW investory.app_v_symbol_performance AS
WITH latest_positions AS (
    SELECT v.portfolio_id, v.asset_id,
        CASE WHEN COUNT(*) FILTER (WHERE NOT v.fx_rate_available) > 0 THEN NULL::numeric ELSE SUM(v.unrealized_pl_in_base_currency) END AS unrealized_profit,
        CASE WHEN COUNT(*) FILTER (WHERE NOT v.fx_rate_available) > 0 THEN NULL::numeric ELSE SUM(v.cost_basis_in_base_currency) END AS cost_basis,
        CASE WHEN COUNT(*) FILTER (WHERE NOT v.fx_rate_available) > 0 THEN NULL::numeric ELSE SUM(v.market_value_in_base_currency) END AS market_value,
        COUNT(*) FILTER (WHERE NOT v.fx_rate_available)::bigint AS missing_fx_count,
        COUNT(*) FILTER (WHERE NOT v.fx_rate_available) = 0 AS is_complete,
        SUM(v.volume) AS total_volume
    FROM investory.app_v_open_position_values v
    GROUP BY v.portfolio_id, v.asset_id
), closed_position_components AS (
    SELECT a.portfolio_id, asset.id AS asset_id, p.profit_currency::varchar(3) AS source_currency,
        pf.base_currency::varchar(3) AS base_currency, p.close_time::date AS valuation_date,
        COALESCE(p.profit, 0) + COALESCE(p.swap, 0) AS amount_native
    FROM investory.positions p
    JOIN investory.accounts a ON a.id = p.account_id
    JOIN investory.portfolios pf ON pf.id = a.portfolio_id
    JOIN investory.assets asset ON asset.id = p.asset_id AND asset.exclude_from_import = false
    WHERE p.close_time IS NOT NULL AND p.asset_id IS NOT NULL
    UNION ALL
    SELECT a.portfolio_id, asset.id, p.commission_currency::varchar(3), pf.base_currency::varchar(3),
        p.close_time::date, COALESCE(p.commission, 0)
    FROM investory.positions p
    JOIN investory.accounts a ON a.id = p.account_id
    JOIN investory.portfolios pf ON pf.id = a.portfolio_id
    JOIN investory.assets asset ON asset.id = p.asset_id AND asset.exclude_from_import = false
    WHERE p.close_time IS NOT NULL AND p.asset_id IS NOT NULL
), closed_positions AS (
    SELECT cpr.portfolio_id, cpr.asset_id,
        SUM(CASE WHEN investory.fx_status_usable(fx.conversion_status)
            THEN cpr.amount_native * fx.fx_rate_to_base ELSE NULL::numeric END) AS closed_profit,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(fx.conversion_status))::bigint AS missing_fx_count,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(fx.conversion_status)) = 0 AS is_complete
    FROM closed_position_components cpr
    LEFT JOIN investory.app_v_portfolio_daily_fx_rate_mv fx
      ON fx.portfolio_id = cpr.portfolio_id
     AND fx.valuation_date = cpr.valuation_date
     AND fx.source_currency = cpr.source_currency
    GROUP BY cpr.portfolio_id, cpr.asset_id
), cash_dividends AS (
    SELECT a.portfolio_id, asset.id AS asset_id,
        SUM(nco.amount_in_portfolio_base_currency) FILTER (WHERE nco.normalized_category IN ('DIVIDEND', 'DIVIDEND_REVERSAL')) AS dividends,
        SUM(-nco.amount_in_portfolio_base_currency) FILTER (WHERE nco.normalized_category IN ('WITHHOLDING_TAX', 'WITHHOLDING_TAX_REVERSAL')) AS withholding_tax,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(nco.portfolio_conversion_status))::bigint AS missing_fx_count,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(nco.portfolio_conversion_status)) = 0 AS is_complete
    FROM investory.app_v_normalized_cash_operations nco
    JOIN investory.accounts a ON a.id = nco.account_id
    JOIN investory.assets asset ON asset.id = nco.asset_id AND asset.exclude_from_import = false
    GROUP BY a.portfolio_id, asset.id
)
SELECT COALESCE(lp.portfolio_id, cp.portfolio_id, cd.portfolio_id) AS portfolio_id,
    asset.symbol AS symbol, asset.id AS asset_id,
    CASE WHEN COALESCE(cp.is_complete, true) THEN COALESCE(cp.closed_profit, 0) ELSE NULL::numeric END AS closed_profit,
    CASE WHEN COALESCE(lp.is_complete, true) THEN COALESCE(lp.unrealized_profit, 0) ELSE NULL::numeric END AS unrealized_profit,
    CASE WHEN COALESCE(lp.is_complete, true) AND COALESCE(cp.is_complete, true) AND COALESCE(cd.is_complete, true)
        THEN COALESCE(cp.closed_profit, 0) + COALESCE(lp.unrealized_profit, 0) + COALESCE(cd.dividends, 0) - COALESCE(cd.withholding_tax, 0)
        ELSE NULL::numeric END AS total_profit,
    CASE WHEN COALESCE(cd.is_complete, true) THEN COALESCE(cd.dividends, 0) ELSE NULL::numeric END AS dividends,
    CASE WHEN COALESCE(cd.is_complete, true) THEN COALESCE(cd.withholding_tax, 0) ELSE NULL::numeric END AS withholding_tax,
    COALESCE(lp.total_volume, 0) AS total_volume,
    CASE WHEN COALESCE(lp.is_complete, true) THEN COALESCE(lp.cost_basis, 0) ELSE NULL::numeric END AS cost_basis,
    CASE WHEN COALESCE(lp.is_complete, true) THEN COALESCE(lp.market_value, 0) ELSE NULL::numeric END AS market_value,
    COALESCE(lp.missing_fx_count, 0) + COALESCE(cp.missing_fx_count, 0) + COALESCE(cd.missing_fx_count, 0) AS missing_fx_count,
    COALESCE(lp.is_complete, true) AND COALESCE(cp.is_complete, true) AND COALESCE(cd.is_complete, true) AS is_complete,
    NOW() AS updated_at
FROM latest_positions lp
FULL OUTER JOIN closed_positions cp ON cp.portfolio_id = lp.portfolio_id AND cp.asset_id = lp.asset_id
FULL OUTER JOIN cash_dividends cd ON cd.portfolio_id = COALESCE(lp.portfolio_id, cp.portfolio_id)
    AND cd.asset_id = COALESCE(lp.asset_id, cp.asset_id)
JOIN investory.assets asset ON asset.id = COALESCE(lp.asset_id, cp.asset_id, cd.asset_id)
WITH DATA;

CREATE UNIQUE INDEX ux_mv_symbol_performance_key
    ON investory.app_v_symbol_performance(portfolio_id, asset_id);


-- Consolidated from post-baseline currency-breakdown FX reuse work.
-- Rebuild the independent currency breakdown MV using the already refreshed portfolio FX map.
DROP MATERIALIZED VIEW IF EXISTS investory.app_v_portfolio_currency_breakdown;

CREATE MATERIALIZED VIEW investory.app_v_portfolio_currency_breakdown AS
WITH latest_account_daily AS (
    SELECT DISTINCT ON (ad.account_id) ad.account_id, ad.valuation_currency, ad.cash_balance,
        ad.market_value, ad.snapshot_date
    FROM investory.account_daily ad
    ORDER BY ad.account_id, ad.snapshot_date DESC, ad.id DESC
), latest_with_fx AS (
    SELECT lad.*, a.portfolio_id, p.base_currency,
        fx.fx_rate_to_base, fx.conversion_status
    FROM latest_account_daily lad
    JOIN investory.accounts a ON a.id = lad.account_id
    JOIN investory.portfolios p ON p.id = a.portfolio_id
    LEFT JOIN investory.app_v_portfolio_daily_fx_rate_mv fx
      ON fx.portfolio_id = a.portfolio_id
     AND fx.valuation_date = lad.snapshot_date
     AND fx.source_currency = lad.valuation_currency::varchar(3)
), account_latest AS (
    SELECT portfolio_id, base_currency::varchar(3) base_currency, 'ACCOUNT_LATEST'::varchar(32) metric_type,
        valuation_currency::varchar(3) currency, SUM(cash_balance + market_value) amount_local,
        CASE WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(conversion_status)) > 0 THEN NULL::numeric
             ELSE SUM((cash_balance + market_value) * fx_rate_to_base) END amount_in_base_currency,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(conversion_status))::bigint missing_fx_count,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(conversion_status)) = 0 is_complete
    FROM latest_with_fx GROUP BY portfolio_id, base_currency, valuation_currency
), realized_components AS (
    SELECT a.portfolio_id, pf.base_currency::varchar(3) base_currency, p.close_time::date valuation_date,
        p.profit_currency::varchar(3) currency, COALESCE(p.profit,0)+COALESCE(p.swap,0) amount_local
    FROM investory.positions p JOIN investory.accounts a ON a.id=p.account_id
    JOIN investory.portfolios pf ON pf.id=a.portfolio_id WHERE p.close_time IS NOT NULL
    UNION ALL
    SELECT a.portfolio_id, pf.base_currency::varchar(3), p.close_time::date,
        p.commission_currency::varchar(3), COALESCE(p.commission,0)
    FROM investory.positions p JOIN investory.accounts a ON a.id=p.account_id
    JOIN investory.portfolios pf ON pf.id=a.portfolio_id WHERE p.close_time IS NOT NULL
), realized_with_fx AS (
    SELECT rc.*, fx.fx_rate_to_target AS fx_rate_to_base, fx.conversion_status
    FROM realized_components rc
    LEFT JOIN LATERAL investory.resolve_fx_rate(
        rc.valuation_date, rc.currency, rc.base_currency
    ) fx ON true
), realized AS (
    SELECT portfolio_id,base_currency,'REALIZED'::varchar(32) metric_type,currency,SUM(amount_local) amount_local,
        CASE WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(conversion_status))>0 THEN NULL::numeric
             ELSE SUM(amount_local*fx_rate_to_base) END amount_in_base_currency,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(conversion_status))::bigint missing_fx_count,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(conversion_status))=0 is_complete
    FROM realized_with_fx GROUP BY portfolio_id,base_currency,currency
), unrealized_components AS (
    SELECT a.portfolio_id,pf.base_currency::varchar(3) base_currency,p.profit_currency::varchar(3) currency,
        COALESCE(p.profit,0)+COALESCE(p.swap,0) amount_local
    FROM investory.positions p JOIN investory.accounts a ON a.id=p.account_id
    JOIN investory.portfolios pf ON pf.id=a.portfolio_id WHERE p.close_time IS NULL
    UNION ALL
    SELECT a.portfolio_id,pf.base_currency::varchar(3),p.commission_currency::varchar(3),COALESCE(p.commission,0)
    FROM investory.positions p JOIN investory.accounts a ON a.id=p.account_id
    JOIN investory.portfolios pf ON pf.id=a.portfolio_id WHERE p.close_time IS NULL
), unrealized_with_fx AS (
    SELECT uc.*,fx.fx_rate_to_base,fx.conversion_status
    FROM unrealized_components uc
    LEFT JOIN investory.app_v_portfolio_daily_fx_rate_mv fx
      ON fx.portfolio_id=uc.portfolio_id AND fx.valuation_date=CURRENT_DATE AND fx.source_currency=uc.currency
), unrealized AS (
    SELECT portfolio_id,base_currency,'UNREALIZED'::varchar(32) metric_type,currency,SUM(amount_local) amount_local,
        CASE WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(conversion_status))>0 THEN NULL::numeric
             ELSE SUM(amount_local*fx_rate_to_base) END amount_in_base_currency,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(conversion_status))::bigint missing_fx_count,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(conversion_status))=0 is_complete
    FROM unrealized_with_fx GROUP BY portfolio_id,base_currency,currency
), dividends AS (
    SELECT a.portfolio_id,pf.base_currency::varchar(3) base_currency,'DIVIDENDS'::varchar(32) metric_type,
        nco.currency::varchar(3) currency,SUM(nco.amount) amount_local,
        CASE WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(nco.portfolio_conversion_status))>0 THEN NULL::numeric
             ELSE SUM(nco.amount_in_portfolio_base_currency) END amount_in_base_currency,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(nco.portfolio_conversion_status))::bigint missing_fx_count,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(nco.portfolio_conversion_status))=0 is_complete
    FROM investory.app_v_normalized_cash_operations nco JOIN investory.accounts a ON a.id=nco.account_id
    JOIN investory.portfolios pf ON pf.id=a.portfolio_id
    WHERE NOT a.cash_only AND nco.normalized_category IN ('DIVIDEND','DIVIDEND_REVERSAL')
    GROUP BY a.portfolio_id,pf.base_currency,nco.currency
)
SELECT portfolio_id,base_currency,metric_type,currency,amount_local,amount_in_base_currency,missing_fx_count,is_complete,NOW() updated_at FROM account_latest
UNION ALL SELECT portfolio_id,base_currency,metric_type,currency,amount_local,amount_in_base_currency,missing_fx_count,is_complete,NOW() FROM realized
UNION ALL SELECT portfolio_id,base_currency,metric_type,currency,amount_local,amount_in_base_currency,missing_fx_count,is_complete,NOW() FROM unrealized
UNION ALL SELECT portfolio_id,base_currency,metric_type,currency,amount_local,amount_in_base_currency,missing_fx_count,is_complete,NOW() FROM dividends
WITH DATA;

CREATE UNIQUE INDEX ux_mv_portfolio_currency_breakdown_key
    ON investory.app_v_portfolio_currency_breakdown(portfolio_id, metric_type, currency);


-- Consolidated from post-baseline normalized-cash FX reuse work.
SET search_path TO investory, public;

-- Normalized cash is a shared source for application and reconciliation read models.
-- Capture the complete dependent closure so the source MV can be rebuilt without CASCADE.
CREATE TEMP TABLE _nco_defs AS
WITH RECURSIVE deps(oid) AS (
    VALUES ('investory.app_v_normalized_cash_operations'::regclass)
    UNION
    SELECT w.ev_class
    FROM deps d
    JOIN pg_depend x ON x.refobjid = d.oid
    JOIN pg_rewrite w ON w.oid = x.objid
)
SELECT c.oid, c.relname AS object_name, c.relkind,
       pg_get_viewdef(c.oid, true) AS object_definition,
       obj_description(c.oid, 'pg_class') AS object_comment,
       false AS dropped
FROM deps d
JOIN pg_class c ON c.oid = d.oid
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'investory'
  AND c.relkind IN ('v', 'm')
  AND c.oid <> 'investory.app_v_normalized_cash_operations'::regclass;

CREATE TEMP TABLE _nco_indexes AS
SELECT DISTINCT i.tablename, i.indexname, i.indexdef
FROM pg_indexes i JOIN _nco_defs d ON d.object_name = i.tablename
WHERE i.schemaname = 'investory' AND d.relkind = 'm';

CREATE TEMP TABLE _nco_source AS
SELECT pg_get_viewdef('investory.app_v_normalized_cash_operations'::regclass, true) AS definition,
       obj_description('investory.app_v_normalized_cash_operations'::regclass, 'pg_class') AS view_comment;

-- Remove only captured dependents, one object at a time. A failed drop means another
-- captured object still depends on it; the next pass removes that blocker first.
DO $$
DECLARE v record; progress boolean;
BEGIN
    LOOP
        progress := false;
        FOR v IN SELECT * FROM _nco_defs WHERE NOT dropped ORDER BY relkind, object_name LOOP
            BEGIN
                IF v.relkind = 'm' THEN
                    EXECUTE 'DROP MATERIALIZED VIEW investory.' || quote_ident(v.object_name);
                ELSE
                    EXECUTE 'DROP VIEW investory.' || quote_ident(v.object_name);
                END IF;
                UPDATE _nco_defs SET dropped = true WHERE oid = v.oid;
                progress := true;
            EXCEPTION WHEN dependent_objects_still_exist THEN
                NULL;
            END;
        END LOOP;
        EXIT WHEN NOT EXISTS (SELECT 1 FROM _nco_defs WHERE NOT dropped);
        IF NOT progress THEN RAISE EXCEPTION 'Could not remove normalized-cash dependent objects'; END IF;
    END LOOP;
END
$$;

DROP MATERIALIZED VIEW investory.app_v_normalized_cash_operations;

DO $$
DECLARE d text; r text; p integer; q integer; c text;
BEGIN
    SELECT definition, view_comment INTO d, c FROM _nco_source;
    d := regexp_replace(d, ';[[:space:]]*$', '');
    p := strpos(d, 'port_resolved AS (');
    q := p + strpos(substr(d, p), '), acct_needed AS (') - 1;
    r := 'port_resolved AS ( SELECT n.portfolio_id AS k_portfolio_id, n.vdate AS k_vdate, n.currency AS k_currency, fx.fx_rate_to_base, fx.source, fx.source_rate_date, fx.age_days, fx.conversion_status FROM port_needed n CROSS JOIN LATERAL investory.resolve_portfolio_fx_rate(n.portfolio_id, n.vdate, n.currency) fx(portfolio_id, valuation_date, source_currency, base_currency, fx_rate_to_base, source, rate_method, rate_source, source_rate_date, age_days, conversion_status) )';
    d := left(d, p - 1) || r || substr(d, q + 1);
    EXECUTE 'CREATE MATERIALIZED VIEW investory.app_v_normalized_cash_operations AS ' || d || ' WITH DATA';
    CREATE UNIQUE INDEX ux_normalized_cash_operations ON investory.app_v_normalized_cash_operations(operation_id);
    IF c IS NOT NULL THEN EXECUTE 'COMMENT ON MATERIALIZED VIEW investory.app_v_normalized_cash_operations IS ' || quote_literal(c); END IF;
END
$$;

DO $$
DECLARE v record;
BEGIN
    LOOP
        FOR v IN SELECT * FROM _nco_defs WHERE dropped ORDER BY relkind, object_name LOOP
            BEGIN
                IF v.relkind = 'm' THEN
                    EXECUTE 'CREATE MATERIALIZED VIEW investory.' || quote_ident(v.object_name) || ' AS ' || regexp_replace(v.object_definition, ';[[:space:]]*$', '') || ' WITH DATA';
                    UPDATE _nco_defs SET dropped = false WHERE oid = v.oid;
                ELSE
                    EXECUTE 'CREATE VIEW investory.' || quote_ident(v.object_name) || ' AS ' || regexp_replace(v.object_definition, ';[[:space:]]*$', '');
                    UPDATE _nco_defs SET dropped = false WHERE oid = v.oid;
                END IF;
                IF v.object_comment IS NOT NULL THEN
                    EXECUTE CASE WHEN v.relkind = 'm' THEN 'COMMENT ON MATERIALIZED VIEW investory.' ELSE 'COMMENT ON VIEW investory.' END || quote_ident(v.object_name) || ' IS ' || quote_literal(v.object_comment);
                END IF;
            EXCEPTION WHEN undefined_table OR undefined_object OR dependent_objects_still_exist THEN
                NULL;
            END;
        END LOOP;
        EXIT WHEN NOT EXISTS (SELECT 1 FROM _nco_defs WHERE dropped);
    END LOOP;
END
$$;

DO $$
DECLARE v record;
BEGIN
    FOR v IN SELECT * FROM _nco_indexes LOOP EXECUTE v.indexdef; END LOOP;
END
$$;


-- Consolidated from post-baseline two-stage normalized-price lookup work.
SET search_path TO investory, public;

-- Keep the normalized-price dependent closure intact while changing only the price lookup.
CREATE TEMP TABLE _ndp_defs AS
WITH RECURSIVE deps(oid) AS (
    VALUES ('investory.app_v_normalized_daily_price_mv'::regclass)
    UNION
    SELECT w.ev_class
    FROM deps d
    JOIN pg_depend x ON x.refobjid = d.oid
    JOIN pg_rewrite w ON w.oid = x.objid
)
SELECT c.oid, c.relname AS object_name, c.relkind,
       pg_get_viewdef(c.oid, true) AS object_definition,
       obj_description(c.oid, 'pg_class') AS object_comment,
       false AS dropped
FROM deps d
JOIN pg_class c ON c.oid = d.oid
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'investory'
  AND c.relkind IN ('v', 'm')
  AND c.oid <> 'investory.app_v_normalized_daily_price_mv'::regclass;

CREATE TEMP TABLE _ndp_indexes AS
SELECT DISTINCT i.tablename, i.indexname, i.indexdef
FROM pg_indexes i JOIN _ndp_defs d ON d.object_name = i.tablename
WHERE i.schemaname = 'investory' AND d.relkind = 'm';

CREATE TEMP TABLE _ndp_source AS
SELECT pg_get_viewdef('investory.app_v_normalized_daily_price_mv'::regclass, true) AS definition,
       obj_description('investory.app_v_normalized_daily_price_mv'::regclass, 'pg_class') AS view_comment;

DO $$
DECLARE v record; progress boolean;
BEGIN
    LOOP
        progress := false;
        FOR v IN SELECT * FROM _ndp_defs WHERE NOT dropped ORDER BY relkind, object_name LOOP
            BEGIN
                IF v.relkind = 'm' THEN
                    EXECUTE 'DROP MATERIALIZED VIEW investory.' || quote_ident(v.object_name);
                ELSE
                    EXECUTE 'DROP VIEW investory.' || quote_ident(v.object_name);
                END IF;
                UPDATE _ndp_defs SET dropped = true WHERE oid = v.oid;
                progress := true;
            EXCEPTION WHEN dependent_objects_still_exist THEN NULL;
            END;
        END LOOP;
        EXIT WHEN NOT EXISTS (SELECT 1 FROM _ndp_defs WHERE NOT dropped);
        IF NOT progress THEN RAISE EXCEPTION 'Could not remove normalized-price dependent objects'; END IF;
    END LOOP;
END
$$;

DROP MATERIALIZED VIEW investory.app_v_normalized_daily_price_mv;

DO $$
DECLARE d text; p integer; q integer; replacement text; c text;
BEGIN
    SELECT definition, view_comment INTO d, c FROM _ndp_source;
    d := regexp_replace(d, ';[[:space:]]*$', '');
    p := strpos(d, 'JOIN LATERAL (');
    q := p + strpos(substr(d, p), ' w ON true') + length(' w ON true') - 1;
    IF p = 0 OR q < p THEN
        RAISE EXCEPTION 'Could not locate normalized-price lateral lookup';
    END IF;
    replacement := 'JOIN LATERAL ( SELECT aph.effective_observation_date FROM investory.app_v_canonical_asset_daily_price_ranked_mv aph'
        || ' WHERE aph.asset_id = pd.asset_id'
        || ' AND aph.price_date <= pd.valuation_date'
        || ' AND (NOT aph.estimated OR aph.interpolation_right_date IS NULL'
        || ' OR aph.interpolation_right_date <= pd.valuation_date)'
        || ' AND aph.effective_observation_date <= pd.valuation_date'
        || ' ORDER BY aph.effective_observation_date DESC LIMIT 1 ) latest ON true'
        || ' JOIN LATERAL ( SELECT aph.*'
        || ' FROM investory.app_v_canonical_asset_daily_price_ranked_mv aph'
        || ' WHERE aph.asset_id = pd.asset_id'
        || ' AND aph.effective_observation_date = latest.effective_observation_date'
        || ' AND aph.price_date <= pd.valuation_date'
        || ' AND (NOT aph.estimated OR aph.interpolation_right_date IS NULL'
        || ' OR aph.interpolation_right_date <= pd.valuation_date)'
        || ' ORDER BY CASE WHEN aph.effective_observation_date IS NULL THEN 1 ELSE 0 END,'
        || ' aph.effective_observation_date DESC, aph.selection_priority,'
        || ' aph.quality_score DESC, aph.price_date DESC,'
        || ' CASE aph.price_origin WHEN ''XTB_TRADE_OPEN'' THEN 0'
        || ' WHEN ''XTB_TRADE_CLOSE'' THEN 2 ELSE 1 END,'
        || ' aph.source, aph.source_symbol LIMIT 1 ) w ON true';
    d := left(d, p - 1) || replacement || substr(d, q + 1);
    EXECUTE 'CREATE MATERIALIZED VIEW investory.app_v_normalized_daily_price_mv AS ' || d || ' WITH DATA';
    CREATE UNIQUE INDEX ux_app_v_normalized_daily_price_mv_key
        ON investory.app_v_normalized_daily_price_mv(asset_id, valuation_date);
    IF c IS NOT NULL THEN
        EXECUTE 'COMMENT ON MATERIALIZED VIEW investory.app_v_normalized_daily_price_mv IS ' || quote_literal(c);
    END IF;
END
$$;

DO $$
DECLARE v record;
BEGIN
    LOOP
        FOR v IN SELECT * FROM _ndp_defs WHERE dropped ORDER BY relkind, object_name LOOP
            BEGIN
                IF v.relkind = 'm' THEN
                    EXECUTE 'CREATE MATERIALIZED VIEW investory.' || quote_ident(v.object_name)
                        || ' AS ' || regexp_replace(v.object_definition, ';[[:space:]]*$', '') || ' WITH DATA';
                    UPDATE _ndp_defs SET dropped = false WHERE oid = v.oid;
                ELSE
                    EXECUTE 'CREATE VIEW investory.' || quote_ident(v.object_name)
                        || ' AS ' || regexp_replace(v.object_definition, ';[[:space:]]*$', '');
                    UPDATE _ndp_defs SET dropped = false WHERE oid = v.oid;
                END IF;
                IF v.object_comment IS NOT NULL THEN
                    EXECUTE CASE WHEN v.relkind = 'm' THEN 'COMMENT ON MATERIALIZED VIEW investory.'
                        ELSE 'COMMENT ON VIEW investory.' END || quote_ident(v.object_name)
                        || ' IS ' || quote_literal(v.object_comment);
                END IF;
            EXCEPTION WHEN undefined_table OR undefined_object OR dependent_objects_still_exist THEN NULL;
            END;
        END LOOP;
        EXIT WHEN NOT EXISTS (SELECT 1 FROM _ndp_defs WHERE dropped);
    END LOOP;
END
$$;

DO $$
DECLARE v record;
BEGIN
    FOR v IN SELECT * FROM _ndp_indexes LOOP EXECUTE v.indexdef; END LOOP;
END
$$;


-- Consolidated from post-baseline current-position cost-FX work.
-- Current open-position values use current valuation FX for every monetary input.
-- The opening date is relevant to historical projections, not this current view.
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
