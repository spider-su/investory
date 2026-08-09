SET search_path TO investory, public;

CREATE TABLE IF NOT EXISTS investory.reconciliation_parameters (
    parameter_name varchar(96) PRIMARY KEY,
    numeric_value numeric(30,12) NOT NULL,
    description varchar(255) NOT NULL
);

INSERT INTO investory.reconciliation_parameters(parameter_name, numeric_value, description)
VALUES
    ('reconciliation_reporting_scale', 2, 'Decimal places used for displayed reconciliation values.'),
    ('reconciliation_absolute_tolerance', 0.05, 'Shared absolute monetary tolerance.'),
    ('reconciliation_relative_tolerance', 0.00001, 'Shared relative monetary tolerance.'),
    ('reconciliation_quantity_tolerance', 0.000001, 'Tolerance for signed quantity matching; not a monetary tolerance.'),
    ('reconciliation_trade_cash_absolute_tolerance', 5, 'Domain threshold for trade cash settlement differences.'),
    ('reconciliation_trade_cash_relative_tolerance', 0.05, 'Domain threshold for trade cash settlement differences.'),
    ('reconciliation_carrying_value_absolute_threshold', 250, 'Domain threshold for carrying-value outliers.'),
    ('reconciliation_carrying_value_relative_threshold', 0.50, 'Domain threshold for carrying-value outliers.'),
    ('reconciliation_reorganization_absolute_threshold', 250, 'Domain threshold for reorganization value jumps.'),
    ('reconciliation_reorganization_relative_threshold', 0.20, 'Domain threshold for reorganization value jumps.'),
    ('reconciliation_market_bridge_absolute_threshold', 250, 'Domain threshold for market-value bridge outliers.'),
    ('reconciliation_market_bridge_relative_threshold', 0.20, 'Domain threshold for market-value bridge outliers.'),
    ('reconciliation_position_input_absolute_tolerance', 0.05, 'Input validation tolerance for position notional reconstruction.'),
    ('reconciliation_position_input_relative_tolerance', 0.15, 'Input validation tolerance for position notional reconstruction.'),
    ('reconciliation_local_sale_price_relative_threshold', 0.05, 'Domain threshold for local sale-vs-carrying-value comparison.'),
    ('reconciliation_local_flat_base_jump_relative_threshold', 0.15, 'Domain threshold for local-flat/base-currency valuation jumps.'),
    ('reconciliation_price_jump_upper_ratio', 100, 'Domain threshold for extreme upward price jumps.'),
    ('reconciliation_price_jump_lower_ratio', 0.01, 'Domain threshold for extreme downward price jumps.'),
    ('reconciliation_valuation_jump_upper_ratio', 5, 'Domain threshold for valuation-jump detection.'),
    ('reconciliation_valuation_jump_lower_ratio', 0.20, 'Domain threshold for valuation-jump detection.'),
    ('reconciliation_valuation_jump_trade_multiplier', 1.25, 'Domain threshold for valuation-jump trade allowance.'),
    ('reconciliation_valuation_jump_absolute_threshold', 250, 'Domain threshold for valuation-jump detection.')
ON CONFLICT (parameter_name) DO NOTHING;

CREATE OR REPLACE FUNCTION investory.reconciliation_parameter(p_parameter_name varchar(96))
RETURNS numeric
LANGUAGE sql
STABLE
AS $$
    SELECT numeric_value
    FROM investory.reconciliation_parameters
    WHERE parameter_name = p_parameter_name
$$;

CREATE OR REPLACE FUNCTION investory.reconciliation_effective_tolerance(
    p_expected numeric,
    p_actual numeric
) RETURNS numeric
LANGUAGE sql
STABLE
AS $$
    SELECT CASE
        WHEN p_expected IS NULL OR p_actual IS NULL THEN NULL::numeric
        ELSE GREATEST(
            investory.reconciliation_parameter('reconciliation_absolute_tolerance'),
            investory.reconciliation_parameter('reconciliation_relative_tolerance')
                * GREATEST(ABS(p_expected), ABS(p_actual))
        )
    END
$$;

CREATE OR REPLACE FUNCTION investory.reconciliation_values_match(
    p_expected numeric,
    p_actual numeric
) RETURNS boolean
LANGUAGE sql
STABLE
AS $$
    SELECT p_expected IS NOT NULL
       AND p_actual IS NOT NULL
       AND ABS(p_actual - p_expected)
           <= investory.reconciliation_effective_tolerance(p_expected, p_actual)
$$;

CREATE OR REPLACE FUNCTION investory.reconciliation_display_value(p_value numeric)
RETURNS numeric
LANGUAGE sql
STABLE
AS $$
    SELECT CASE
        WHEN p_value IS NULL THEN NULL::numeric
        ELSE ROUND(
            p_value,
            investory.reconciliation_parameter('reconciliation_reporting_scale')::integer
        )
    END
$$;

COMMENT ON TABLE investory.reconciliation_parameters IS
    'Central reconciliation display precision, numeric tolerances, and explicitly named domain anomaly thresholds. Status uses full precision; reporting values are rounded only for presentation.';
COMMENT ON FUNCTION investory.reconciliation_effective_tolerance(numeric, numeric) IS
    'Shared numeric rule: max(absolute tolerance, relative tolerance * max(abs(expected), abs(actual))).';
COMMENT ON FUNCTION investory.reconciliation_values_match(numeric, numeric) IS
    'Full-precision reconciliation comparison. NULL values never match.';
COMMENT ON FUNCTION investory.reconciliation_display_value(numeric) IS
    'Rounds a reconciliation value for presentation only. It is never used for status decisions.';

-- Currency semantics used by all reporting:
-- accounts.currency  = cash-account denomination.
-- assets.currency    = instrument listing/quote denomination.
-- positions uses explicit price, cost, profit, and commission currencies.
-- No position monetary currency is inferred from accounts.currency.
COMMENT ON COLUMN investory.accounts.currency IS
    'Cash-account currency: denomination of the account cash balance and account-native cash flows. Not the position trade or quote currency.';
COMMENT ON COLUMN investory.assets.currency IS
    'Instrument quote/listing currency: denomination of the asset market price and listing. Not necessarily the broker trade-value currency.';

-- Canonical FX contract:
--   amount_in_target_currency = amount_in_source_currency * fx_rate_to_target
-- Stored exchange_rates follow the same mathematical direction: base -> to_currency.
-- A rate older than 45 days is exposed for diagnostics but is not authoritative.
CREATE OR REPLACE FUNCTION investory.resolve_fx_rate(
    p_valuation_date date,
    p_source_currency varchar(3),
    p_target_currency varchar(3)
) RETURNS TABLE (
    source_currency varchar(3),
    target_currency varchar(3),
    fx_rate_to_target numeric,
    source varchar(64),
    rate_method varchar(32),
    rate_source varchar(32),
    source_rate_date date,
    age_days integer,
    conversion_status varchar(32)
)
LANGUAGE sql
STABLE
AS $$
WITH edges AS (
    SELECT DISTINCT ON (edge_source, edge_target)
        edge_source,
        edge_target,
        edge_rate,
        edge_source_name,
        edge_method,
        edge_rate_source,
        rate_date
    FROM (
        SELECT
            er.base::varchar(3) AS edge_source,
            er.to_currency::varchar(3) AS edge_target,
            er.rate AS edge_rate,
            ('DIRECT:' || er.source)::varchar(64) AS edge_source_name,
            er.method AS edge_method,
            er.source AS edge_rate_source,
            er.rate_date AS rate_date,
            1 AS direction_priority,
            er.imported_at
        FROM investory.exchange_rates er
        WHERE er.rate_date <= p_valuation_date
          AND er.rate > 0
        UNION ALL
        SELECT
            er.to_currency::varchar(3) AS edge_source,
            er.base::varchar(3) AS edge_target,
            1::numeric / er.rate AS edge_rate,
            ('INVERSE:' || er.source)::varchar(64) AS edge_source_name,
            er.method AS edge_method,
            er.source AS edge_rate_source,
            er.rate_date AS rate_date,
            2 AS direction_priority,
            er.imported_at
        FROM investory.exchange_rates er
        WHERE er.rate_date <= p_valuation_date
          AND er.rate > 0
    ) available_edges
    ORDER BY edge_source, edge_target,
        CASE edge_method
            WHEN 'MARKET_DAILY' THEN 1
            WHEN 'IBKR_DAILY_REFERENCE' THEN 2
            WHEN 'HISTORICAL_MONTHLY' THEN 3
            ELSE 4
        END,
        rate_date DESC, direction_priority, imported_at DESC
), candidates AS (
    SELECT
        e.edge_rate AS candidate_rate,
        e.edge_source_name AS candidate_source,
        e.edge_method AS candidate_method,
        e.edge_rate_source AS candidate_rate_source,
        e.rate_date AS candidate_rate_date,
        1 AS candidate_priority
    FROM edges e
    WHERE e.edge_source = p_source_currency
      AND e.edge_target = p_target_currency
    UNION ALL
    SELECT
        first_leg.edge_rate * second_leg.edge_rate,
        ('TRIANGULATED:' || first_leg.edge_target)::varchar(64),
        CASE WHEN first_leg.edge_method = second_leg.edge_method THEN first_leg.edge_method ELSE 'TRIANGULATED' END,
        first_leg.edge_rate_source,
        LEAST(first_leg.rate_date, second_leg.rate_date),
        2
    FROM edges first_leg
    JOIN edges second_leg
      ON second_leg.edge_source = first_leg.edge_target
     AND second_leg.edge_target = p_target_currency
    WHERE first_leg.edge_source = p_source_currency
      AND first_leg.edge_target NOT IN (p_source_currency, p_target_currency)
), selected AS (
    SELECT *
    FROM candidates
    ORDER BY candidate_priority, candidate_rate_date DESC, candidate_source
    LIMIT 1
)
SELECT
    p_source_currency::varchar(3),
    p_target_currency::varchar(3),
    CASE
        WHEN p_source_currency = p_target_currency THEN 1::numeric
        ELSE selected.candidate_rate
    END,
    CASE
        WHEN p_source_currency = p_target_currency THEN 'SAME_CURRENCY'::varchar(64)
        ELSE selected.candidate_source
    END,
    CASE
        WHEN p_source_currency = p_target_currency THEN 'SAME_CURRENCY'::varchar(32)
        ELSE selected.candidate_method
    END,
    CASE
        WHEN p_source_currency = p_target_currency THEN 'SAME_CURRENCY'::varchar(32)
        ELSE selected.candidate_rate_source
    END,
    CASE
        WHEN p_source_currency = p_target_currency THEN p_valuation_date
        ELSE selected.candidate_rate_date
    END,
    CASE
        WHEN p_source_currency = p_target_currency THEN 0
        WHEN selected.candidate_rate_date IS NULL THEN NULL
        ELSE (p_valuation_date - selected.candidate_rate_date)::integer
    END,
    CASE
        WHEN p_source_currency IS NULL OR p_target_currency IS NULL THEN 'MISSING_CURRENCY'
        WHEN p_source_currency = p_target_currency THEN 'SAME_CURRENCY'
        WHEN selected.candidate_rate IS NULL THEN 'MISSING'
        WHEN selected.candidate_method = 'HISTORICAL_MONTHLY' AND p_valuation_date - selected.candidate_rate_date > 4 THEN 'ESTIMATED'
        WHEN p_valuation_date - selected.candidate_rate_date > 4 THEN 'STALE'
        ELSE 'OK'
    END::varchar(32)
FROM (SELECT 1) anchor
LEFT JOIN selected ON true
$$;

COMMENT ON FUNCTION investory.resolve_fx_rate(date, varchar, varchar) IS
    'Canonical date-aware FX resolver. amount_target = amount_source * fx_rate_to_target. Uses same-currency, direct, inverse, then one-currency triangulation. Rates older than 45 days are STALE.';

CREATE OR REPLACE FUNCTION investory.resolve_portfolio_fx_rate(
    p_portfolio_id bigint,
    p_valuation_date date,
    p_source_currency varchar(3)
) RETURNS TABLE (
    portfolio_id bigint,
    valuation_date date,
    source_currency varchar(3),
    base_currency varchar(3),
    fx_rate_to_base numeric,
    source varchar(64),
    rate_method varchar(32),
    rate_source varchar(32),
    source_rate_date date,
    age_days integer,
    conversion_status varchar(32)
)
LANGUAGE sql
STABLE
AS $$
SELECT
    p.id,
    p_valuation_date,
    resolved.source_currency,
    p.base_currency::varchar(3),
    resolved.fx_rate_to_target,
    resolved.source,
    resolved.rate_method,
    resolved.rate_source,
    resolved.source_rate_date,
    resolved.age_days,
    resolved.conversion_status
FROM investory.portfolios p
CROSS JOIN LATERAL investory.resolve_fx_rate(
    p_valuation_date,
    p_source_currency,
    p.base_currency::varchar(3)
) resolved
WHERE p.id = p_portfolio_id
$$;

COMMENT ON FUNCTION investory.resolve_portfolio_fx_rate(bigint, date, varchar) IS
    'Portfolio-aware canonical FX resolver. Portfolio base currency is derived from portfolios.id; callers cannot supply it.';


CREATE OR REPLACE VIEW investory.reporting_position_lot_duplicates AS
SELECT
    account_id,
    asset_id,
    source_position_id,
    source_row_occurrence,
    operation,
    settlement_model,
    open_time,
    close_time,
    volume,
    open_price,
    purchase_value,
    price_currency,
    cost_currency,
    profit_currency,
    commission_currency,
    count(*) AS duplicate_count,
    array_agg(id ORDER BY id) AS position_ids
FROM investory.positions
GROUP BY
    account_id,
    asset_id,
    source_position_id,
    source_row_occurrence,
    operation,
    settlement_model,
    open_time,
    close_time,
    volume,
    open_price,
    purchase_value,
    price_currency,
    cost_currency,
    profit_currency,
    commission_currency
HAVING count(*) > 1;
COMMENT ON VIEW investory.reporting_position_lot_duplicates IS
    'Diagnostic view for exact duplicate position lots imported under different IDs. Empty result is the expected healthy state.';

CREATE OR REPLACE VIEW investory.reporting_timezone_naive_columns AS
SELECT
    table_name,
    column_name,
    data_type
FROM information_schema.columns
WHERE table_schema = 'investory'
  AND data_type = 'timestamp without time zone';
COMMENT ON VIEW investory.reporting_timezone_naive_columns IS
    'Schema diagnostic. Empty result is expected; event and audit instants must use timestamp with time zone. Date-only business periods remain DATE.';


CREATE OR REPLACE VIEW investory.v_canonical_asset_daily_price AS
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
    aph.source;

CREATE OR REPLACE VIEW investory.v_canonical_asset_daily_return AS
WITH canonical_prices AS (
    SELECT
        cp.asset_id,
        cp.price_date,
        COALESCE(cp.adjusted_close_price, cp.close_price)
            * COALESCE(cp.price_scale_factor, 1) AS return_price
    FROM investory.v_canonical_asset_daily_price cp
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

COMMENT ON VIEW investory.v_canonical_asset_daily_return IS
    'Canonical return basis: provider adjusted_close_price when present, otherwise close_price, with the source price_scale_factor applied once. Position valuation uses raw close_price and broker-reported quantity; corporate-action quantity changes must be represented by the position source.';

CREATE OR REPLACE VIEW investory.v_current_asset_price AS
WITH latest_observed_price AS (
    SELECT DISTINCT ON (cp.asset_id)
        cp.asset_id,
        cp.price_date,
        cp.source,
        cp.source_symbol,
        cp.source_mapping_id,
        cp.price_origin,
        cp.price_currency,
        cp.close_price * cp.price_scale_factor AS selected_price,
        cp.quality_score,
        cp.quality_class,
        cp.is_proxy,
        cp.source_date,
        cp.imported_at
    FROM investory.v_canonical_asset_daily_price cp
    WHERE cp.is_observed
      AND cp.estimated = false
      AND cp.close_price > 0
      AND cp.price_origin <> 'STALE_CARRY_FORWARD'
      AND COALESCE(cp.source_date, cp.price_date) >= CURRENT_DATE - 10
      AND COALESCE(cp.source_date, cp.price_date) <= CURRENT_DATE
    ORDER BY cp.asset_id,
             COALESCE(cp.source_date, cp.price_date) DESC,
             CASE
                 WHEN cp.quality_class = 'EXACT_LISTING_MARKET_CLOSE' THEN 1
                 WHEN cp.quality_class = 'EXACT_LISTING_SCALED' THEN 2
                 WHEN cp.quality_class LIKE '%ALTERNATE%' OR cp.is_proxy THEN 3
                 WHEN cp.price_origin = 'MANUAL' THEN 4
                 WHEN cp.estimated OR cp.quality_class LIKE 'INTERPOLATED%' THEN 5
                 WHEN cp.quality_class LIKE '%TRADE_OBSERVATION%' OR cp.price_origin LIKE '%TRADE%' THEN 6
                 WHEN cp.quality_class LIKE '%STALE%' OR cp.price_origin = 'STALE_CARRY_FORWARD' THEN 7
                 ELSE 9
             END,
             cp.quality_score DESC,
             cp.price_date DESC,
             cp.imported_at DESC,
             cp.source
)
SELECT
    a.id AS asset_id,
    COALESCE(lp.selected_price, a.market_price) AS selected_price,
    CASE WHEN lp.asset_id IS NOT NULL THEN lp.price_currency ELSE a.currency::varchar(3) END AS price_currency,
    CASE WHEN lp.asset_id IS NOT NULL THEN 'HISTORICAL' ELSE 'ASSET_CURRENT_FALLBACK' END::varchar(32) AS price_selection_source,
    lp.price_date AS selected_price_date,
    lp.source_date AS underlying_observation_date,
    lp.source AS price_source,
    lp.source_symbol,
    lp.source_mapping_id,
    lp.price_origin,
    lp.quality_score,
    lp.quality_class,
    lp.is_proxy,
    lp.imported_at
FROM investory.assets a
LEFT JOIN latest_observed_price lp
    ON lp.asset_id = a.id
WHERE a.exclude_from_import = false;

COMMENT ON VIEW investory.v_current_asset_price IS
    'Authoritative current price selection: latest observed canonical historical price whose effective source observation is no older than ten days (scaled once), then assets.market_price in assets.currency, otherwise unavailable. Price and currency always come from the same source.';

CREATE OR REPLACE VIEW investory.v_current_open_position_rows AS
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
    cost_fx.fx_rate_to_target AS cost_basis_to_base_rate,
    cost_fx.conversion_status AS cost_basis_fx_status,
    market_fx.fx_rate_to_target AS market_price_to_base_rate,
    market_fx.conversion_status AS market_price_fx_status,
    CASE
        WHEN cost_fx.conversion_status IN ('OK', 'SAME_CURRENCY')
            THEN COALESCE(p.purchase_value, p.volume * p.open_price, 0) * cost_fx.fx_rate_to_target
        ELSE NULL::numeric
    END AS cost_basis_in_base_currency,
    CASE
        WHEN price.selected_price IS NOT NULL
         AND market_fx.conversion_status IN ('OK', 'SAME_CURRENCY')
            THEN investory.signed_position_quantity(p.operation, p.volume)
                 * price.selected_price * market_fx.fx_rate_to_target
        ELSE NULL::numeric
    END AS market_value_in_base_currency
FROM investory.positions p
JOIN investory.accounts a
    ON a.id = p.account_id
JOIN investory.portfolios pf
    ON pf.id = a.portfolio_id
JOIN investory.assets asset
    ON asset.id = p.asset_id
LEFT JOIN investory.v_current_asset_price price
    ON price.asset_id = asset.id
LEFT JOIN LATERAL investory.resolve_fx_rate(
    COALESCE(p.open_time::date, CURRENT_DATE),
    p.cost_currency::varchar(3),
    pf.base_currency::varchar(3)
) cost_fx ON true
LEFT JOIN LATERAL investory.resolve_fx_rate(
    CURRENT_DATE,
    price.price_currency::varchar(3),
    pf.base_currency::varchar(3)
) market_fx ON true
WHERE p.close_time IS NULL
  AND asset.exclude_from_import = false
  AND COALESCE(p.volume, 0) > 0;

COMMENT ON VIEW investory.v_current_open_position_rows IS
    'Shared current open-position valuation rows. Uses v_current_asset_price and resolve_fx_rate(CURRENT_DATE, ...); stale or missing FX yields null converted values.';

CREATE OR REPLACE VIEW investory.normalized_cash_operations AS
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
        date_trunc('month', co.date)::date AS rate_month,
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
             AND (
                 lower(COALESCE(co.comment, '')) = 'cash transfer'
                 OR lower(COALESCE(co.comment, '')) LIKE 'cash transfer |%'
                 OR lower(COALESCE(co.comment, '')) LIKE 'cash transfer|%'
             )
             AND co.amount > 0
                THEN 'EXTERNAL_DEPOSIT'
            WHEN co.operation = 'TRANSFER'
             AND (
                 lower(COALESCE(co.comment, '')) = 'cash transfer'
                 OR lower(COALESCE(co.comment, '')) LIKE 'cash transfer |%'
                 OR lower(COALESCE(co.comment, '')) LIKE 'cash transfer|%'
             )
             AND co.amount < 0
                THEN 'EXTERNAL_WITHDRAWAL'
            WHEN co.operation = 'TRANSFER'
             AND (
                 lower(COALESCE(co.comment, '')) LIKE 'net amount in base from forex trade:%'
                 OR lower(COALESCE(co.comment, '')) LIKE '%ibkrrawtype=forex trade component%'
             )
                THEN 'FX_CONVERSION'
            -- Cross-account currency conversion (XTB "... from TA: <src> to: <dst>")
            -- moves cash between the user's own accounts while switching currency. For
            -- each account it is a one-sided internal transfer, not a same-account FX
            -- swap, so classify by sign; otherwise the receiving account books the
            -- funding as fake profit.
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
            WHEN co.operation = 'DIVIDEND'
             AND co.amount < 0
                THEN 'DIVIDEND_REVERSAL'
            WHEN co.operation = 'DIVIDEND'
                THEN 'DIVIDEND'
            WHEN co.operation = 'FREE_FUNDS_INTEREST'
             AND co.amount < 0
                THEN 'INTEREST_REVERSAL'
            WHEN co.operation = 'FREE_FUNDS_INTEREST'
                THEN 'INTEREST'
            WHEN co.operation = 'WITHHOLDING_TAX'
             AND co.amount > 0
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
            WHEN co.operation = 'DEPOSIT'
             AND co.amount > 0
                THEN 'EXTERNAL_DEPOSIT'
            WHEN co.operation = 'WITHDRAWAL'
             AND co.amount < 0
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
            WHEN co.operation = 'DEPOSIT'
             AND COALESCE(co.amount, 0) = 0
                THEN 'ZERO_DEPOSIT'
            WHEN co.operation = 'DEPOSIT'
             AND co.amount < 0
                THEN 'RAW_DEPOSIT_NEGATIVE_REVIEW'
            WHEN co.operation = 'DEPOSIT'
             AND co.amount > 0
                THEN 'RAW_DEPOSIT'
            WHEN co.operation = 'WITHDRAWAL'
             AND co.amount < 0
                THEN 'RAW_WITHDRAWAL'
            WHEN co.operation = 'WITHDRAWAL'
             AND co.amount > 0
                THEN 'RAW_WITHDRAWAL_POSITIVE_REVIEW'
            WHEN co.operation = 'TRANSFER'
             AND (
                 lower(COALESCE(co.comment, '')) = 'cash transfer'
                 OR lower(COALESCE(co.comment, '')) LIKE 'cash transfer |%'
                 OR lower(COALESCE(co.comment, '')) LIKE 'cash transfer|%'
             )
             AND co.amount > 0
                THEN 'EXTERNAL_DEPOSIT'
            WHEN co.operation = 'TRANSFER'
             AND (
                 lower(COALESCE(co.comment, '')) = 'cash transfer'
                 OR lower(COALESCE(co.comment, '')) LIKE 'cash transfer |%'
                 OR lower(COALESCE(co.comment, '')) LIKE 'cash transfer|%'
             )
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
            WHEN co.operation = 'DIVIDEND'
             AND co.amount < 0
                THEN 'DIVIDEND_REVERSAL'
            WHEN co.operation = 'DIVIDEND'
                THEN 'DIVIDEND'
            WHEN co.operation = 'FREE_FUNDS_INTEREST'
             AND co.amount < 0
                THEN 'INTEREST_REVERSAL'
            WHEN co.operation = 'FREE_FUNDS_INTEREST'
                THEN 'INTEREST'
            WHEN co.operation = 'WITHHOLDING_TAX'
             AND co.amount > 0
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
            WHEN co.operation = 'DEPOSIT'
             AND lower(COALESCE(co.comment, '')) LIKE 'transfer out operation on account%'
                THEN 'explicit transfer out comment'
            WHEN co.operation = 'DEPOSIT'
             AND lower(COALESCE(co.comment, '')) LIKE 'transfer in operation on account%'
                THEN 'explicit transfer in comment'
            WHEN co.operation = 'DEPOSIT'
             AND COALESCE(co.amount, 0) = 0
                THEN 'zero raw deposit requires review'
            WHEN co.operation = 'DEPOSIT'
             AND co.amount < 0
                THEN 'negative raw deposit requires review'
            WHEN co.operation = 'WITHDRAWAL'
             AND co.amount > 0
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
            WHEN co.operation = 'DIVIDEND'
             AND co.amount < 0
                THEN 'negative/correction dividend'
            WHEN co.operation = 'FREE_FUNDS_INTEREST'
             AND co.amount < 0
                THEN 'negative/correction interest'
            WHEN co.operation = 'WITHHOLDING_TAX'
             AND co.amount > 0
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
             AND lower(COALESCE(co.comment, '')) = 'cash transfer'
                THEN true
            WHEN co.operation = 'DEPOSIT'
             AND co.amount > 0
                THEN true
            WHEN co.operation = 'WITHDRAWAL'
             AND co.amount < 0
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
            ELSE false
        END AS is_trade_cash_flow,
        CASE
            WHEN co.operation = 'CORRECTION'
                THEN true
            ELSE false
        END AS is_correction,
        CASE
            WHEN co.operation = 'DIVIDEND' AND co.amount < 0 THEN true
            WHEN co.operation = 'FREE_FUNDS_INTEREST' AND co.amount < 0 THEN true
            WHEN co.operation = 'WITHHOLDING_TAX' AND co.amount > 0 THEN true
            WHEN co.operation = 'WITHDRAWAL' AND co.amount > 0 THEN true
            ELSE false
        END AS is_reversal
    FROM investory.cash_operations co
    JOIN investory.accounts a
      ON a.id = co.account_id
    JOIN investory.portfolios p
      ON p.id = a.portfolio_id
),
fx AS (
    SELECT
        c.*,
        portfolio_fx.fx_rate_to_base,
        portfolio_fx.source AS portfolio_fx_source,
        portfolio_fx.source_rate_date AS portfolio_source_rate_date,
        portfolio_fx.age_days AS portfolio_fx_age_days,
        portfolio_fx.conversion_status AS portfolio_conversion_status,
        account_fx.fx_rate_to_target AS fx_rate_to_account_currency,
        account_fx.source AS account_fx_source,
        account_fx.source_rate_date AS account_source_rate_date,
        account_fx.age_days AS account_fx_age_days,
        account_fx.conversion_status AS account_conversion_status
    FROM classified c
    CROSS JOIN LATERAL investory.resolve_portfolio_fx_rate(
        c.portfolio_id,
        c.date::date,
        c.currency
    ) portfolio_fx
    CROSS JOIN LATERAL investory.resolve_fx_rate(
        c.date::date,
        c.currency,
        c.account_currency
    ) account_fx
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
    CASE
        WHEN portfolio_conversion_status IN ('OK', 'SAME_CURRENCY')
            THEN amount * fx_rate_to_base
    END AS amount_in_portfolio_base_currency,
    CASE
        WHEN portfolio_conversion_status IN ('OK', 'SAME_CURRENCY')
            THEN amount * fx_rate_to_base
    END AS amount_in_base_currency,
    CASE
        WHEN account_conversion_status IN ('OK', 'SAME_CURRENCY')
            THEN amount * fx_rate_to_account_currency
    END AS amount_in_account_currency,
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
    'sql-v2026-07-28-step2'::varchar(64) AS classification_version,
    portfolio_conversion_status AS conversion_status
FROM fx;

COMMENT ON VIEW investory.normalized_cash_operations IS
    'Canonical classified cash ledger. amount_in_portfolio_base_currency = amount * fx_rate_to_base. Missing or stale FX produces NULL converted amounts and explicit conversion statuses; raw amounts remain grouped by their own currency.';

CREATE MATERIALIZED VIEW investory.account_monthly_mv AS
WITH month_rows AS (
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
        ad.daily_return_pct,
        ad.valuation_currency,
        ROW_NUMBER() OVER (
            PARTITION BY ad.account_id, date_trunc('month', ad.snapshot_date)
            ORDER BY ad.snapshot_date DESC
        ) AS rn_last
    FROM investory.account_daily ad
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
    m.closing_equity
        - COALESCE(
            LAG(m.closing_equity) OVER (PARTITION BY m.account_id ORDER BY m.month),
            0
        )
        - m.deposits
        + m.withdrawals AS total_profit,
    m.compounded_monthly_return,
    NOW() AS updated_at
FROM monthly m
WITH DATA;

CREATE UNIQUE INDEX ux_mv_account_monthly_account_month
    ON investory.account_monthly_mv(account_id, month);

CREATE MATERIALIZED VIEW investory.reporting_account_monthly_profit_reconciliation AS
WITH monthly_daily_sums AS (
    SELECT
        ad.account_id,
        date_trunc('month', ad.snapshot_date)::date AS month,
        SUM(COALESCE(ad.daily_profit_amount, 0)) AS summed_daily_profit
    FROM investory.account_daily ad
    GROUP BY ad.account_id, date_trunc('month', ad.snapshot_date)::date
)
SELECT
    am.account_id,
    am.month,
    am.first_date,
    am.end_date,
    am.opening_equity,
    am.closing_equity,
    am.deposits,
    am.withdrawals,
    am.dividends,
    am.interest,
    am.fees,
    am.taxes,
    am.realized_profit,
    am.total_profit AS monthly_boundary_profit,
    COALESCE(mds.summed_daily_profit, 0) AS summed_daily_profit,
    investory.reconciliation_display_value(
        COALESCE(am.closing_equity, 0)
        - COALESCE(am.opening_equity, 0)
        - COALESCE(am.deposits, 0)
        + COALESCE(am.withdrawals, 0)
    ) AS expected_boundary_profit,
    investory.reconciliation_display_value(am.total_profit
        - (
            COALESCE(am.closing_equity, 0)
            - COALESCE(am.opening_equity, 0)
            - COALESCE(am.deposits, 0)
            + COALESCE(am.withdrawals, 0)
        )) AS monthly_vs_boundary_difference,
    investory.reconciliation_display_value(COALESCE(mds.summed_daily_profit, 0)
        - (
            COALESCE(am.closing_equity, 0)
            - COALESCE(am.opening_equity, 0)
            - COALESCE(am.deposits, 0)
            + COALESCE(am.withdrawals, 0)
        )) AS daily_sum_vs_boundary_difference,
    investory.reconciliation_display_value(investory.reconciliation_effective_tolerance(
        am.total_profit,
        (
            COALESCE(am.closing_equity, 0)
            - COALESCE(am.opening_equity, 0)
            - COALESCE(am.deposits, 0)
            + COALESCE(am.withdrawals, 0)
        )
    )) AS monthly_effective_tolerance,
    investory.reconciliation_display_value(investory.reconciliation_effective_tolerance(
        COALESCE(mds.summed_daily_profit, 0),
        (
            COALESCE(am.closing_equity, 0)
            - COALESCE(am.opening_equity, 0)
            - COALESCE(am.deposits, 0)
            + COALESCE(am.withdrawals, 0)
        )
    )) AS daily_sum_effective_tolerance,
    CASE
        WHEN NOT investory.reconciliation_values_match(
            am.total_profit,
            (
                COALESCE(am.closing_equity, 0)
                - COALESCE(am.opening_equity, 0)
                - COALESCE(am.deposits, 0)
                + COALESCE(am.withdrawals, 0)
            )
        ) THEN 'MONTHLY_VIEW_MISMATCH'
        WHEN NOT investory.reconciliation_values_match(
            COALESCE(mds.summed_daily_profit, 0),
            (
                COALESCE(am.closing_equity, 0)
                - COALESCE(am.opening_equity, 0)
                - COALESCE(am.deposits, 0)
                + COALESCE(am.withdrawals, 0)
            )
        ) THEN 'DAILY_SUM_MISMATCH'
        ELSE 'OK'
    END AS reconciliation_status
FROM investory.account_monthly_mv am
LEFT JOIN monthly_daily_sums mds
    ON mds.account_id = am.account_id
   AND mds.month = am.month;

CREATE UNIQUE INDEX ux_mv_reporting_account_monthly_profit_reconciliation_key
    ON investory.reporting_account_monthly_profit_reconciliation(account_id, month);

COMMENT ON MATERIALIZED VIEW investory.reporting_account_monthly_profit_reconciliation IS
    'Compares monthly profit from month-boundary formula vs summed daily profit for each account and month.';

CREATE OR REPLACE VIEW investory.v_portfolio_daily AS
WITH account_rows_with_fx AS (
    SELECT
        a.portfolio_id,
        p.base_currency::varchar(3) AS base_currency,
        ad.snapshot_date,
        ad.account_id,
        ad.cash_balance,
        ad.market_value,
        ad.equity,
        ad.deposits,
        ad.withdrawals,
        ad.dividends,
        ad.interest,
        ad.fees,
        ad.taxes,
        ad.realized_profit,
        ad.daily_profit_amount,
        ad.valuation_currency,
        fx.fx_rate_to_target AS valuation_to_base_rate,
        fx.conversion_status
    FROM investory.account_daily ad
    JOIN investory.accounts a
        ON a.id = ad.account_id
    JOIN investory.portfolios p
        ON p.id = a.portfolio_id
    CROSS JOIN LATERAL investory.resolve_fx_rate(
        ad.snapshot_date,
        ad.valuation_currency,
        p.base_currency::varchar(3)
    ) fx
),
account_rows AS (
    SELECT
        portfolio_id,
        base_currency,
        snapshot_date,
        account_id,
        conversion_status,
        CASE WHEN conversion_status IN ('OK', 'SAME_CURRENCY') THEN cash_balance * valuation_to_base_rate END AS cash_balance,
        CASE WHEN conversion_status IN ('OK', 'SAME_CURRENCY') THEN market_value * valuation_to_base_rate END AS market_value,
        CASE WHEN conversion_status IN ('OK', 'SAME_CURRENCY') THEN equity * valuation_to_base_rate END AS equity,
        CASE WHEN conversion_status IN ('OK', 'SAME_CURRENCY') THEN deposits * valuation_to_base_rate END AS deposits,
        CASE WHEN conversion_status IN ('OK', 'SAME_CURRENCY') THEN withdrawals * valuation_to_base_rate END AS withdrawals,
        CASE WHEN conversion_status IN ('OK', 'SAME_CURRENCY') THEN dividends * valuation_to_base_rate END AS dividends,
        CASE WHEN conversion_status IN ('OK', 'SAME_CURRENCY') THEN interest * valuation_to_base_rate END AS interest,
        CASE WHEN conversion_status IN ('OK', 'SAME_CURRENCY') THEN fees * valuation_to_base_rate END AS fees,
        CASE WHEN conversion_status IN ('OK', 'SAME_CURRENCY') THEN taxes * valuation_to_base_rate END AS taxes,
        CASE WHEN conversion_status IN ('OK', 'SAME_CURRENCY') THEN realized_profit * valuation_to_base_rate END AS realized_profit,
        CASE WHEN conversion_status IN ('OK', 'SAME_CURRENCY') THEN daily_profit_amount * valuation_to_base_rate END AS daily_profit_amount
    FROM account_rows_with_fx
)
SELECT
    ar.portfolio_id,
    ar.snapshot_date,
    ar.base_currency,
    CASE WHEN COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0 THEN NULL ELSE SUM(ar.cash_balance) END AS cash_balance,
    CASE WHEN COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0 THEN NULL ELSE SUM(ar.market_value) END AS market_value,
    CASE WHEN COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0 THEN NULL ELSE SUM(ar.equity) END AS equity,
    CASE WHEN COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0 THEN NULL ELSE SUM(ar.deposits) END AS deposits,
    CASE WHEN COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0 THEN NULL ELSE SUM(ar.withdrawals) END AS withdrawals,
    CASE WHEN COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0 THEN NULL ELSE SUM(ar.dividends) END AS dividends,
    CASE WHEN COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0 THEN NULL ELSE SUM(ar.interest) END AS interest,
    CASE WHEN COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0 THEN NULL ELSE SUM(ar.fees) END AS fees,
    CASE WHEN COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0 THEN NULL ELSE SUM(ar.taxes) END AS taxes,
    CASE WHEN COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0 THEN NULL ELSE SUM(ar.realized_profit) END AS realized_profit,
    CASE WHEN COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0 THEN NULL ELSE SUM(ar.daily_profit_amount) END AS total_profit,
    SUM(ar.equity) AS converted_equity_subtotal,
    COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY'))::bigint AS missing_fx_count,
    COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) = 0 AS is_complete,
    CASE
        WHEN COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0
            THEN NULL::numeric
        WHEN LAG(SUM(ar.equity)) OVER (PARTITION BY ar.portfolio_id ORDER BY ar.snapshot_date) IS NULL
            THEN NULL::numeric
        ELSE
            SUM(ar.daily_profit_amount)
            / NULLIF(
                LAG(SUM(ar.equity)) OVER (PARTITION BY ar.portfolio_id ORDER BY ar.snapshot_date)
                + SUM(ar.deposits)
                - SUM(ar.withdrawals),
                0
            )
    END AS daily_return_pct,
    NOW() AS updated_at
FROM account_rows ar
GROUP BY ar.portfolio_id, ar.snapshot_date, ar.base_currency;

CREATE MATERIALIZED VIEW investory.portfolio_monthly_mv AS
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
    FROM investory.v_portfolio_daily pd
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

CREATE UNIQUE INDEX ux_mv_portfolio_monthly_portfolio_month
    ON investory.portfolio_monthly_mv(portfolio_id, month);

CREATE MATERIALIZED VIEW investory.account_statistics AS
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
        CASE WHEN fx.conversion_status IN ('OK', 'SAME_CURRENCY') THEN ld.cash_balance * fx.fx_rate_to_target END AS cash_balance,
        CASE WHEN fx.conversion_status IN ('OK', 'SAME_CURRENCY') THEN ld.market_value * fx.fx_rate_to_target END AS market_value,
        CASE WHEN fx.conversion_status IN ('OK', 'SAME_CURRENCY') THEN ld.equity * fx.fx_rate_to_target END AS equity,
        CASE WHEN fx.conversion_status IN ('OK', 'SAME_CURRENCY') THEN ld.cost_base * fx.fx_rate_to_target END AS cost_base,
        CASE WHEN fx.conversion_status IN ('OK', 'SAME_CURRENCY') THEN ld.unrealized_profit * fx.fx_rate_to_target END AS unrealized_profit,
        CASE WHEN fx.conversion_status IN ('OK', 'SAME_CURRENCY') THEN ld.realized_profit * fx.fx_rate_to_target END AS realized_profit,
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
    FROM investory.v_current_open_position_rows value
    GROUP BY value.account_id
),
closed_position_components AS (
    SELECT
        a.id AS account_id,
        p.close_time::date AS valuation_date,
        p.profit_currency::varchar(3) AS source_currency,
        pf.base_currency::varchar(3) AS base_currency,
        COALESCE(p.profit, 0) + COALESCE(p.swap, 0) AS amount_native
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
        COALESCE(p.commission, 0) AS amount_native
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
            WHEN COUNT(*) FILTER (WHERE fx.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0
                THEN NULL::numeric
            ELSE SUM(c.amount_native * fx.fx_rate_to_target)
        END AS realized_profit,
        SUM(c.amount_native * fx.fx_rate_to_target) FILTER (
            WHERE fx.conversion_status IN ('OK', 'SAME_CURRENCY')) AS converted_subtotal,
        COUNT(*) FILTER (WHERE fx.conversion_status NOT IN ('OK', 'SAME_CURRENCY'))::bigint
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
    FROM investory.normalized_cash_operations nco
), flow_totals AS (
    SELECT
        nco.account_id,
        COUNT(*) FILTER (WHERE nco.portfolio_conversion_status NOT IN ('OK', 'SAME_CURRENCY'))::bigint
            AS missing_fx_count,
        COUNT(*) FILTER (WHERE nco.account_conversion_status NOT IN ('OK', 'SAME_CURRENCY'))::bigint
            AS account_missing_fx_count,
        SUM(nco.amount_in_portfolio_base_currency) FILTER (
            WHERE nco.portfolio_conversion_status IN ('OK', 'SAME_CURRENCY')) AS converted_subtotal,
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

CREATE UNIQUE INDEX ux_mv_account_statistics_account
    ON investory.account_statistics(account_id);

CREATE MATERIALIZED VIEW investory.reporting_account_statistics_vs_daily_reconciliation AS
WITH latest_daily AS (
    SELECT DISTINCT ON (ad.account_id)
        ad.account_id,
        ad.snapshot_date,
        ad.valuation_currency,
        ad.cash_balance,
        ad.market_value,
        ad.equity,
        ad.cost_base,
        ad.unrealized_profit
    FROM investory.account_daily ad
    ORDER BY ad.account_id, ad.snapshot_date DESC, ad.id DESC
),
daily_flow_totals AS (
    SELECT
        ad.account_id,
        SUM(COALESCE(ad.realized_profit, 0)) AS realized_profit,
        SUM(COALESCE(ad.dividends, 0)) AS dividends,
        SUM(COALESCE(ad.interest, 0)) AS interest,
        SUM(COALESCE(ad.fees, 0)) AS fees,
        SUM(COALESCE(ad.taxes, 0)) AS taxes
    FROM investory.account_daily ad
    GROUP BY ad.account_id
)
SELECT
    a.id AS account_id,
    a.name AS account_name,
    a.currency::varchar(3) AS account_currency,
    CURRENT_DATE AS statistics_valuation_date,
    ast.valuation_currency AS statistics_currency,
    ld.valuation_currency AS latest_daily_currency,
    ast.latest_snapshot_date AS statistics_snapshot_date,
    ld.snapshot_date AS latest_daily_snapshot_date,
    ROUND(COALESCE(ast.cash_balance, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS statistics_cash_balance,
    ROUND(COALESCE(ld.cash_balance, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS latest_daily_cash_balance,
    ROUND(COALESCE(ast.cash_balance, 0) - COALESCE(ld.cash_balance, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS cash_balance_difference,
    ROUND(COALESCE(ast.market_value, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS statistics_market_value,
    ROUND(COALESCE(ld.market_value, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS latest_daily_market_value,
    ROUND(COALESCE(ast.market_value, 0) - COALESCE(ld.market_value, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS market_value_difference,
    ROUND(COALESCE(ast.equity, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS statistics_equity,
    ROUND(COALESCE(ld.equity, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS latest_daily_equity,
    ROUND(COALESCE(ast.equity, 0) - COALESCE(ld.equity, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS equity_difference,
    ROUND(COALESCE(ast.cost_base, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS statistics_cost_base,
    ROUND(COALESCE(ld.cost_base, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS latest_daily_cost_base,
    ROUND(COALESCE(ast.cost_base, 0) - COALESCE(ld.cost_base, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS cost_base_difference,
    ROUND(COALESCE(ast.realized_profit, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS statistics_realized_profit,
    ROUND(COALESCE(dft.realized_profit, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS cumulative_daily_realized_profit,
    ROUND(COALESCE(ast.realized_profit, 0) - COALESCE(dft.realized_profit, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS realized_profit_difference,
    ROUND(COALESCE(ast.unrealized_profit, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS statistics_unrealized_profit,
    ROUND(COALESCE(ld.unrealized_profit, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS latest_daily_unrealized_profit,
    ROUND(COALESCE(ast.unrealized_profit, 0) - COALESCE(ld.unrealized_profit, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS unrealized_profit_difference,
    ROUND(COALESCE(ast.dividends, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS statistics_dividends,
    ROUND(COALESCE(dft.dividends, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS cumulative_daily_dividends,
    ROUND(COALESCE(ast.dividends, 0) - COALESCE(dft.dividends, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS dividends_difference,
    ROUND(COALESCE(ast.interest, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS statistics_interest,
    ROUND(COALESCE(dft.interest, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS cumulative_daily_interest,
    ROUND(COALESCE(ast.interest, 0) - COALESCE(dft.interest, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS interest_difference,
    ROUND(COALESCE(ast.fees, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS statistics_fees,
    ROUND(COALESCE(dft.fees, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS cumulative_daily_fees,
    ROUND(COALESCE(ast.fees, 0) - COALESCE(dft.fees, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS fees_difference,
    ROUND(COALESCE(ast.taxes, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS statistics_taxes,
    ROUND(COALESCE(dft.taxes, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS cumulative_daily_taxes,
    ROUND(COALESCE(ast.taxes, 0) - COALESCE(dft.taxes, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS taxes_difference,
    ROUND(COALESCE(ast.net_deposit, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS statistics_net_deposit,
    ROUND(COALESCE(ast.account_net_deposit, 0), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS statistics_account_net_deposit,
    ROUND(GREATEST(
        investory.reconciliation_effective_tolerance(ast.cash_balance, ld.cash_balance),
        investory.reconciliation_effective_tolerance(ast.market_value, ld.market_value),
        investory.reconciliation_effective_tolerance(ast.equity, ld.equity),
        investory.reconciliation_effective_tolerance(ast.cost_base, ld.cost_base),
        investory.reconciliation_effective_tolerance(ast.realized_profit, dft.realized_profit),
        investory.reconciliation_effective_tolerance(ast.unrealized_profit, ld.unrealized_profit),
        investory.reconciliation_effective_tolerance(ast.dividends, dft.dividends),
        investory.reconciliation_effective_tolerance(ast.interest, dft.interest),
        investory.reconciliation_effective_tolerance(ast.fees, dft.fees),
        investory.reconciliation_effective_tolerance(ast.taxes, dft.taxes)
    ), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS effective_tolerance,
    CASE
        WHEN ast.latest_snapshot_date IS NULL AND ld.snapshot_date IS NULL THEN 'NO_DATA'
        WHEN ast.latest_snapshot_date IS NULL OR ld.snapshot_date IS NULL THEN 'MISSING_SIDE'
        WHEN ast.valuation_currency IS DISTINCT FROM ld.valuation_currency THEN 'CURRENCY_MISMATCH'
        WHEN ast.latest_snapshot_date IS DISTINCT FROM ld.snapshot_date THEN 'VALUATION_ASOF_DIFFERENCE'
        WHEN CURRENT_DATE <> ld.snapshot_date THEN 'VALUATION_ASOF_DIFFERENCE'
        WHEN NOT investory.reconciliation_values_match(ast.cash_balance, ld.cash_balance)
          OR NOT investory.reconciliation_values_match(ast.market_value, ld.market_value)
          OR NOT investory.reconciliation_values_match(ast.equity, ld.equity)
          OR NOT investory.reconciliation_values_match(ast.cost_base, ld.cost_base)
          OR NOT investory.reconciliation_values_match(ast.realized_profit, dft.realized_profit)
          OR NOT investory.reconciliation_values_match(ast.unrealized_profit, ld.unrealized_profit)
          OR NOT investory.reconciliation_values_match(ast.dividends, dft.dividends)
          OR NOT investory.reconciliation_values_match(ast.interest, dft.interest)
          OR NOT investory.reconciliation_values_match(ast.fees, dft.fees)
          OR NOT investory.reconciliation_values_match(ast.taxes, dft.taxes)
        THEN 'VALUE_MISMATCH'
        ELSE 'OK'
    END AS reconciliation_status
FROM investory.accounts a
LEFT JOIN investory.account_statistics ast
    ON ast.account_id = a.id
LEFT JOIN latest_daily ld
    ON ld.account_id = a.id
LEFT JOIN daily_flow_totals dft
    ON dft.account_id = a.id;

CREATE UNIQUE INDEX ux_mv_recon_account_stats_account
    ON investory.reporting_account_statistics_vs_daily_reconciliation(account_id);

COMMENT ON MATERIALIZED VIEW investory.reporting_account_statistics_vs_daily_reconciliation IS
    'Current per-account reconciliation between account_statistics and the latest account_daily snapshot; monetary values use reconciliation_reporting_scale, status uses full-precision shared tolerance, and currency/as-of differences are reported explicitly.';

CREATE MATERIALIZED VIEW investory.portfolio_kpi_summary AS
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
    FROM investory.v_portfolio_daily pd
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
    FROM investory.account_statistics ast
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

CREATE UNIQUE INDEX ux_mv_portfolio_kpi_summary_portfolio
    ON investory.portfolio_kpi_summary(portfolio_id);

CREATE OR REPLACE VIEW investory.reporting_validation_issue AS
WITH equity_check AS (
    SELECT
        ad.account_id,
        ad.snapshot_date,
        'EQUITY_MISMATCH'::varchar(64) AS issue_type,
        'ERROR'::varchar(16) AS severity,
        NULL::bigint AS asset_id,
        NULL::bigint AS operation_id,
        (ad.cash_balance + ad.market_value)::numeric AS expected_value,
        ad.equity::numeric AS actual_value,
        ('equity=' || ad.equity || ', cash+market=' || (ad.cash_balance + ad.market_value))::text AS details
    FROM investory.account_daily ad
    WHERE NOT investory.reconciliation_values_match(
        ad.cash_balance + ad.market_value,
        ad.equity
    )
),
unrealized_check AS (
    SELECT
        ad.account_id,
        ad.snapshot_date,
        'UNREALIZED_MISMATCH'::varchar(64) AS issue_type,
        'ERROR'::varchar(16) AS severity,
        NULL::bigint AS asset_id,
        NULL::bigint AS operation_id,
        (ad.market_value - ad.cost_base)::numeric AS expected_value,
        ad.unrealized_profit::numeric AS actual_value,
        ('unrealized=' || ad.unrealized_profit || ', market-cost=' || (ad.market_value - ad.cost_base))::text AS details
    FROM investory.account_daily ad
    WHERE NOT investory.reconciliation_values_match(
        ad.market_value - ad.cost_base,
        ad.unrealized_profit
    )
),
duplicate_prices AS (
    SELECT
        NULL::bigint AS account_id,
        aph.price_date AS snapshot_date,
        'DUPLICATE_PRICE'::varchar(64) AS issue_type,
        'ERROR'::varchar(16) AS severity,
        aph.asset_id,
        NULL::bigint AS operation_id,
        1::numeric AS expected_value,
        COUNT(*)::numeric AS actual_value,
        ('price rows=' || COUNT(*) || ' for source=' || aph.source)::text AS details
    FROM investory.asset_price_history aph
    JOIN investory.assets a
      ON a.id = aph.asset_id
     AND a.exclude_from_import = false
    GROUP BY aph.asset_id, a.symbol, aph.price_date, aph.source
    HAVING COUNT(*) > 1
),
duplicate_positions AS (
    SELECT
        p.account_id,
        COALESCE(
            COALESCE(p.close_time, p.open_time)::date,
            CURRENT_DATE
        ) AS snapshot_date,
        'DUPLICATE_POSITION'::varchar(64) AS issue_type,
        'ERROR'::varchar(16) AS severity,
        p.asset_id,
        NULL::bigint AS operation_id,
        1::numeric AS expected_value,
        COUNT(*)::numeric AS actual_value,
        ('duplicate business-key positions=' || COUNT(*))::text AS details
    FROM investory.positions p
    JOIN investory.assets asset
      ON asset.id = p.asset_id
     AND asset.exclude_from_import = false
    GROUP BY
        p.account_id,
        p.asset_id,
        COALESCE(
            COALESCE(p.close_time, p.open_time)::date,
            CURRENT_DATE
        ),
        p.operation,
        COALESCE(p.volume, 0),
        p.price_currency,
        p.cost_currency,
        p.profit_currency,
        p.commission_currency,
        COALESCE(p.open_time, '-infinity'::timestamptz),
        COALESCE(p.open_price, 0),
        COALESCE(p.close_time, 'infinity'::timestamptz),
        COALESCE(p.close_price, 0),
        COALESCE(p.base_value, 0),
        COALESCE(p.purchase_value, 0),
        COALESCE(p.sale_value, 0),
        COALESCE(p.margin, 0),
        COALESCE(p.commission, 0),
        COALESCE(p.swap, 0),
        COALESCE(p.profit, 0)
    HAVING COUNT(*) > 1
),
missing_price AS (
    SELECT DISTINCT
        p.account_id,
        d.snapshot_date,
        'MISSING_PRICE'::varchar(64) AS issue_type,
        'WARN'::varchar(16) AS severity,
        p.asset_id,
        NULL::bigint AS operation_id,
        NULL::numeric AS expected_value,
        NULL::numeric AS actual_value,
        'no canonical price row for open position date'::text AS details
    FROM investory.positions p
    JOIN investory.assets a
      ON a.id = p.asset_id
     AND a.exclude_from_import = false
    JOIN (
        SELECT DISTINCT account_id, snapshot_date
        FROM investory.account_daily
    ) d ON d.account_id = p.account_id
        AND d.snapshot_date >= p.open_time::date
        AND (p.close_time IS NULL OR d.snapshot_date <= p.close_time::date)
    LEFT JOIN LATERAL (
        SELECT cap.price_date
        FROM investory.v_canonical_asset_daily_price cap
        WHERE cap.asset_id = a.id
          AND cap.price_date <= d.snapshot_date
        ORDER BY cap.price_date DESC
        LIMIT 1
    ) cap ON TRUE
    WHERE p.close_time IS NULL
      AND (
          cap.price_date IS NULL
          OR cap.price_date < d.snapshot_date - INTERVAL '10 days'
      )
),
valuation_jump AS (
    SELECT
        x.account_id,
        x.snapshot_date,
        'VALUATION_JUMP'::varchar(64) AS issue_type,
        'WARN'::varchar(16) AS severity,
        NULL::bigint AS asset_id,
        NULL::bigint AS operation_id,
        x.prev_market_value::numeric AS expected_value,
        x.market_value::numeric AS actual_value,
        (
            'market jump from ' || x.prev_market_value
            || ' to ' || x.market_value
            || ', trade_notional=' || COALESCE(t.trade_notional, 0)
        )::text AS details
    FROM (
        SELECT
            ad.account_id,
            ad.snapshot_date,
            ad.market_value,
            LAG(ad.market_value) OVER (PARTITION BY ad.account_id ORDER BY ad.snapshot_date) AS prev_market_value
        FROM investory.account_daily ad
    ) x
    LEFT JOIN (
        SELECT
            p.account_id,
            d.snapshot_date,
            SUM(
                CASE
                    WHEN p.open_time::date = d.snapshot_date
                        THEN COALESCE(p.purchase_value, ABS(COALESCE(p.volume, 0)) * COALESCE(p.open_price, 0), 0)
                    ELSE 0
                END
                +
                CASE
                    WHEN p.close_time::date = d.snapshot_date
                        THEN COALESCE(p.sale_value, ABS(COALESCE(p.volume, 0)) * COALESCE(p.close_price, 0), 0)
                    ELSE 0
                END
            ) AS trade_notional
        FROM investory.positions p
        JOIN (
            SELECT DISTINCT account_id, snapshot_date
            FROM investory.account_daily
        ) d ON d.account_id = p.account_id
        WHERE (p.open_time IS NOT NULL AND p.open_time::date = d.snapshot_date)
           OR (p.close_time IS NOT NULL AND p.close_time::date = d.snapshot_date)
        GROUP BY p.account_id, d.snapshot_date
    ) t
      ON t.account_id = x.account_id
     AND t.snapshot_date = x.snapshot_date
    WHERE x.prev_market_value IS NOT NULL
      AND x.prev_market_value > 0
      AND (
          x.market_value / x.prev_market_value
              > investory.reconciliation_parameter('reconciliation_valuation_jump_upper_ratio')
          OR x.market_value / x.prev_market_value
              < investory.reconciliation_parameter('reconciliation_valuation_jump_lower_ratio')
      )
      AND ABS(x.market_value - x.prev_market_value) > COALESCE(t.trade_notional, 0)
          * investory.reconciliation_parameter('reconciliation_valuation_jump_trade_multiplier')
          + investory.reconciliation_parameter('reconciliation_valuation_jump_absolute_threshold')
),
unclassified_cash AS (
    SELECT
        co.account_id,
        co.date::date AS snapshot_date,
        'UNCLASSIFIED_OPERATION'::varchar(64) AS issue_type,
        'WARN'::varchar(16) AS severity,
        co.asset_id,
        co.id AS operation_id,
        NULL::numeric AS expected_value,
        co.amount::numeric AS actual_value,
        ('raw operation=' || co.operation || ', comment=' || COALESCE(co.comment, ''))::text AS details
    FROM investory.cash_operations co
    WHERE co.operation = 'UNKNOWN'
      AND ABS(co.amount) > 0
)
SELECT *, NOW() AS created_at
FROM (
    SELECT * FROM equity_check
    UNION ALL
    SELECT * FROM unrealized_check
    UNION ALL
    SELECT * FROM duplicate_prices
    UNION ALL
    SELECT * FROM duplicate_positions
    UNION ALL
    SELECT * FROM missing_price
    UNION ALL
    SELECT * FROM valuation_jump
    UNION ALL
    SELECT * FROM unclassified_cash
) issues;

CREATE MATERIALIZED VIEW investory.portfolio_currency_breakdown AS
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
            WHEN COUNT(*) FILTER (WHERE lwf.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0
                THEN NULL::numeric
            ELSE SUM((lwf.cash_balance + lwf.market_value) * lwf.fx_rate_to_base)
        END AS amount_in_base_currency,
        COUNT(*) FILTER (WHERE lwf.conversion_status NOT IN ('OK', 'SAME_CURRENCY'))::bigint AS missing_fx_count,
        COUNT(*) FILTER (WHERE lwf.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) = 0 AS is_complete
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
            WHEN COUNT(*) FILTER (WHERE rwf.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0
                THEN NULL::numeric
            ELSE SUM(rwf.amount_local * rwf.fx_rate_to_base)
        END AS amount_in_base_currency,
        COUNT(*) FILTER (WHERE rwf.conversion_status NOT IN ('OK', 'SAME_CURRENCY'))::bigint AS missing_fx_count,
        COUNT(*) FILTER (WHERE rwf.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) = 0 AS is_complete
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
            WHERE uwf.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0
            THEN NULL::numeric ELSE SUM(uwf.amount_local * uwf.fx_rate_to_base) END
            AS amount_in_base_currency,
        COUNT(*) FILTER (
            WHERE uwf.conversion_status NOT IN ('OK', 'SAME_CURRENCY'))::bigint AS missing_fx_count,
        COUNT(*) FILTER (
            WHERE uwf.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) = 0 AS is_complete
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
            WHEN COUNT(*) FILTER (WHERE nco.portfolio_conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0
                THEN NULL::numeric
            ELSE SUM(nco.amount_in_portfolio_base_currency)
        END AS amount_in_base_currency,
        COUNT(*) FILTER (WHERE nco.portfolio_conversion_status NOT IN ('OK', 'SAME_CURRENCY'))::bigint AS missing_fx_count,
        COUNT(*) FILTER (WHERE nco.portfolio_conversion_status NOT IN ('OK', 'SAME_CURRENCY')) = 0 AS is_complete
    FROM investory.normalized_cash_operations nco
    JOIN investory.accounts a ON a.id = nco.account_id
    JOIN investory.portfolios pf ON pf.id = a.portfolio_id
    WHERE NOT a.cash_only
      AND nco.normalized_category IN ('DIVIDEND', 'DIVIDEND_REVERSAL')
    GROUP BY a.portfolio_id, pf.base_currency, nco.currency
)
SELECT *, NOW() AS updated_at FROM account_latest
UNION ALL
SELECT *, NOW() AS updated_at FROM realized
UNION ALL
SELECT *, NOW() AS updated_at FROM unrealized
UNION ALL
SELECT *, NOW() AS updated_at FROM dividends
WITH DATA;

CREATE UNIQUE INDEX ux_mv_portfolio_currency_breakdown_key
    ON investory.portfolio_currency_breakdown(portfolio_id, metric_type, currency);

CREATE OR REPLACE VIEW investory.v_position_currency_validation AS
WITH per_row AS (
    SELECT
        p.id AS position_id,
        p.account_id,
        a.portfolio_id,
        portfolio.base_currency::varchar(3) AS base_currency,
        a.name AS account_name,
        a.provider,
        a.currency::varchar(3) AS account_currency,
        p.asset_id,
        asset.currency::varchar(3) AS asset_currency,
        p.cost_currency::varchar(3) AS position_currency,
        p.volume,
        p.open_price,
        p.purchase_value,
        p.close_time,
        p.close_price,
        p.sale_value,
        CASE
            WHEN p.cost_currency IS NULL OR p.price_currency IS NULL
              OR p.profit_currency IS NULL OR p.commission_currency IS NULL
                THEN 'MISSING_POSITION_CURRENCY'
            WHEN asset.currency IS NULL THEN 'MISSING_ASSET_CURRENCY'
            WHEN a.provider = 'XTB'
             AND p.price_currency IS DISTINCT FROM asset.currency
             AND (
                 (
                     p.volume > 0 AND p.open_price > 0 AND p.purchase_value > 0
                     AND ABS(ABS(p.purchase_value / NULLIF(p.volume, 0)) - p.open_price)
                         <= GREATEST(
                             investory.reconciliation_parameter('reconciliation_position_input_absolute_tolerance'),
                             ABS(p.open_price)
                                 * investory.reconciliation_parameter('reconciliation_position_input_relative_tolerance')
                         )
                 )
                 OR
                 (
                     p.close_time IS NOT NULL AND p.volume > 0 AND p.close_price > 0
                     AND p.sale_value > 0
                     AND ABS(ABS(p.sale_value / NULLIF(p.volume, 0)) - p.close_price)
                         <= GREATEST(
                             investory.reconciliation_parameter('reconciliation_position_input_absolute_tolerance'),
                             ABS(p.close_price)
                                 * investory.reconciliation_parameter('reconciliation_position_input_relative_tolerance')
                         )
                 )
             ) THEN 'POSITION_ASSET_CURRENCY_MISMATCH'
            ELSE 'OK'
            END AS anomaly_code,
        CASE
            WHEN p.cost_currency IS NULL OR p.price_currency IS NULL
              OR p.profit_currency IS NULL OR p.commission_currency IS NULL
                THEN 'One or more explicit position currencies are null.'
            WHEN asset.currency IS NULL THEN 'Asset currency is null.'
            WHEN a.provider = 'XTB'
             AND p.price_currency IS DISTINCT FROM asset.currency THEN
                'XTB price currency differs from the asset quote currency.'
            WHEN p.price_currency IS DISTINCT FROM asset.currency THEN
                'Broker price currency differs from asset listing currency; review the source mapping.'
            ELSE 'No issue detected.'
            END AS details
    FROM investory.positions p
             JOIN investory.accounts a
                  ON a.id = p.account_id
             JOIN investory.portfolios portfolio
                  ON portfolio.id = a.portfolio_id
             LEFT JOIN investory.assets asset
                       ON asset.id = p.asset_id
                      AND asset.exclude_from_import = false
    WHERE p.asset_id IS NOT NULL
),
     mixed_groups AS (
         SELECT
             p.account_id,
             p.asset_id,
             COUNT(DISTINCT p.cost_currency) AS distinct_position_currencies
         FROM investory.positions p
         WHERE p.close_time IS NULL
           AND p.asset_id IS NOT NULL
           AND EXISTS (
               SELECT 1
               FROM investory.assets asset
               WHERE asset.id = p.asset_id
                 AND asset.exclude_from_import = false
           )
           AND COALESCE(p.volume, 0) > 0
         GROUP BY p.account_id, p.asset_id
         HAVING COUNT(DISTINCT p.cost_currency) > 1
     )
SELECT
    pr.position_id,
    pr.account_id,
    pr.account_name,
    pr.provider,
    pr.account_currency,
    pr.asset_id,
    pr.asset_currency,
    pr.position_currency,
    pr.volume,
    pr.open_price,
    pr.purchase_value,
    pr.close_time,
    pr.close_price,
    pr.sale_value,
    CASE
        WHEN mg.distinct_position_currencies IS NOT NULL THEN 'MIXED_OPEN_POSITION_CURRENCIES'
        ELSE pr.anomaly_code
        END AS anomaly_code,
    CASE
        WHEN mg.distinct_position_currencies IS NOT NULL THEN
            'Open positions for this account/asset have conflicting position currencies.'
        ELSE pr.details
        END AS details
FROM per_row pr
         LEFT JOIN mixed_groups mg
                   ON mg.account_id = pr.account_id
                       AND mg.asset_id = pr.asset_id
WHERE pr.anomaly_code <> 'OK'
   OR mg.distinct_position_currencies IS NOT NULL;

DROP VIEW IF EXISTS investory.v_open_position_values;
CREATE VIEW investory.v_open_position_values AS
WITH position_rows AS (
         SELECT *
         FROM investory.v_current_open_position_rows
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
                 END AS market_value_native
         FROM position_rows_with_fx pr
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

CREATE MATERIALIZED VIEW investory.reporting_account_daily_cashflow_reconciliation AS
WITH ledger_daily AS (
    SELECT
        nco.account_id,
        nco.date::date AS snapshot_date,
        nco.account_currency::varchar(3) AS account_currency,
        nco.base_currency::varchar(3) AS base_currency,
        CASE
            WHEN COUNT(DISTINCT nco.currency::varchar(3)) = 1 THEN SUM(nco.amount)
            ELSE NULL::numeric
        END AS ledger_cash_native,
        SUM(nco.amount_in_portfolio_base_currency) FILTER (
            WHERE nco.portfolio_conversion_status IN ('OK', 'SAME_CURRENCY')) AS ledger_cash_base,
        SUM(nco.amount_in_portfolio_base_currency) FILTER (
            WHERE nco.normalized_category = 'EXTERNAL_DEPOSIT') AS ledger_deposits,
        SUM(ABS(nco.amount_in_portfolio_base_currency)) FILTER (
            WHERE nco.normalized_category = 'EXTERNAL_WITHDRAWAL') AS ledger_withdrawals,
        SUM(nco.amount_in_portfolio_base_currency) FILTER (
            WHERE nco.normalized_category IN ('DIVIDEND', 'DIVIDEND_REVERSAL')) AS ledger_dividends,
        SUM(nco.amount_in_portfolio_base_currency) FILTER (
            WHERE nco.normalized_category IN ('INTEREST', 'INTEREST_REVERSAL')) AS ledger_interest,
        SUM(-nco.amount_in_portfolio_base_currency) FILTER (
            WHERE nco.normalized_category = 'FEE') AS ledger_fees,
        SUM(-nco.amount_in_portfolio_base_currency) FILTER (
            WHERE nco.normalized_category IN ('WITHHOLDING_TAX', 'WITHHOLDING_TAX_REVERSAL', 'OTHER_TAX')) AS ledger_taxes,
        COUNT(*) FILTER (WHERE nco.portfolio_conversion_status NOT IN ('OK', 'SAME_CURRENCY'))::bigint AS missing_fx_count,
        COUNT(*) FILTER (WHERE nco.portfolio_conversion_status NOT IN ('OK', 'SAME_CURRENCY')) = 0 AS is_complete
    FROM investory.normalized_cash_operations nco
    GROUP BY nco.account_id, nco.date::date, nco.account_currency, nco.base_currency
),
daily_with_prev AS (
    SELECT
        ad.account_id,
        ad.snapshot_date,
        ad.valuation_currency::varchar(3) AS valuation_currency,
        ad.cash_balance,
        LAG(ad.cash_balance) OVER (
            PARTITION BY ad.account_id
            ORDER BY ad.snapshot_date
        ) AS previous_cash_balance,
        ad.deposits,
        ad.withdrawals,
        ad.dividends,
        ad.interest,
        ad.fees,
        ad.taxes
    FROM investory.account_daily ad
)
SELECT
    ad.account_id,
    ad.snapshot_date,
    ld.account_currency,
    COALESCE(ld.base_currency, ad.valuation_currency) AS ledger_base_currency,
    ad.valuation_currency,
    investory.reconciliation_display_value(ad.previous_cash_balance) AS previous_cash_balance,
    investory.reconciliation_display_value(ad.cash_balance) AS cash_balance,
    investory.reconciliation_display_value(ad.cash_balance - COALESCE(ad.previous_cash_balance, 0)) AS account_daily_cash_delta,
    ld.ledger_cash_native,
    ld.ledger_cash_base,
    ld.missing_fx_count,
    ld.is_complete,
    CASE
        WHEN ld.account_currency = COALESCE(ld.base_currency, ad.valuation_currency)
            THEN investory.reconciliation_display_value(ld.ledger_cash_base)
        ELSE NULL::numeric
    END AS expected_cash_delta_base_for_same_currency_account,
    CASE
        WHEN ld.account_currency = COALESCE(ld.base_currency, ad.valuation_currency)
            THEN investory.reconciliation_display_value((ad.cash_balance - COALESCE(ad.previous_cash_balance, 0)) - COALESCE(ld.ledger_cash_base, 0))
        ELSE NULL::numeric
    END AS same_currency_cash_delta_gap,
    investory.reconciliation_display_value(ad.deposits) AS account_daily_deposits,
    investory.reconciliation_display_value(ld.ledger_deposits) AS ledger_deposits,
    investory.reconciliation_display_value(ad.deposits - COALESCE(ld.ledger_deposits, 0)) AS deposits_gap,
    investory.reconciliation_display_value(ad.withdrawals) AS account_daily_withdrawals,
    investory.reconciliation_display_value(ld.ledger_withdrawals) AS ledger_withdrawals,
    investory.reconciliation_display_value(ad.withdrawals - COALESCE(ld.ledger_withdrawals, 0)) AS withdrawals_gap,
    investory.reconciliation_display_value(ad.dividends) AS account_daily_dividends,
    investory.reconciliation_display_value(ld.ledger_dividends) AS ledger_dividends,
    investory.reconciliation_display_value(ad.dividends - COALESCE(ld.ledger_dividends, 0)) AS dividends_gap,
    investory.reconciliation_display_value(ad.interest) AS account_daily_interest,
    investory.reconciliation_display_value(ld.ledger_interest) AS ledger_interest,
    investory.reconciliation_display_value(ad.interest - COALESCE(ld.ledger_interest, 0)) AS interest_gap,
    investory.reconciliation_display_value(ad.fees) AS account_daily_fees,
    investory.reconciliation_display_value(ld.ledger_fees) AS ledger_fees,
    investory.reconciliation_display_value(ad.fees - COALESCE(ld.ledger_fees, 0)) AS fees_gap,
    investory.reconciliation_display_value(ad.taxes) AS account_daily_taxes,
    investory.reconciliation_display_value(ld.ledger_taxes) AS ledger_taxes,
    investory.reconciliation_display_value(ad.taxes - COALESCE(ld.ledger_taxes, 0)) AS taxes_gap,
    investory.reconciliation_display_value(investory.reconciliation_effective_tolerance(
        ad.cash_balance - COALESCE(ad.previous_cash_balance, 0), ld.ledger_cash_base
    )) AS cash_delta_effective_tolerance,
    investory.reconciliation_display_value(investory.reconciliation_effective_tolerance(ad.deposits, ld.ledger_deposits)) AS deposits_effective_tolerance,
    investory.reconciliation_display_value(investory.reconciliation_effective_tolerance(ad.withdrawals, ld.ledger_withdrawals)) AS withdrawals_effective_tolerance,
    investory.reconciliation_display_value(investory.reconciliation_effective_tolerance(ad.dividends, ld.ledger_dividends)) AS dividends_effective_tolerance,
    investory.reconciliation_display_value(investory.reconciliation_effective_tolerance(ad.interest, ld.ledger_interest)) AS interest_effective_tolerance,
    investory.reconciliation_display_value(investory.reconciliation_effective_tolerance(ad.fees, ld.ledger_fees)) AS fees_effective_tolerance,
    investory.reconciliation_display_value(investory.reconciliation_effective_tolerance(ad.taxes, ld.ledger_taxes)) AS taxes_effective_tolerance
FROM daily_with_prev ad
LEFT JOIN ledger_daily ld
    ON ld.account_id = ad.account_id
   AND ld.snapshot_date = ad.snapshot_date;

CREATE UNIQUE INDEX ux_mv_reporting_account_daily_cashflow_reconciliation_key
    ON investory.reporting_account_daily_cashflow_reconciliation(account_id, snapshot_date);

COMMENT ON MATERIALIZED VIEW investory.reporting_account_daily_cashflow_reconciliation IS
    'DB-side reconciliation of account_daily daily flow fields against canonical normalized_cash_operations. For same-currency/base-currency accounts, account_daily cash delta should equal ledger cash delta. For non-base-currency accounts, stored base-currency cash delta also includes FX revaluation of the opening native cash balance.';

CREATE OR REPLACE VIEW investory.v_portfolio_daily_fx_rate AS
WITH portfolio_dates AS (
    SELECT DISTINCT a.portfolio_id, ad.snapshot_date AS valuation_date
    FROM investory.account_daily ad
    JOIN investory.accounts a ON a.id = ad.account_id
    UNION
    SELECT DISTINCT a.portfolio_id, co.date::date AS valuation_date
    FROM investory.cash_operations co
    JOIN investory.accounts a ON a.id = co.account_id
)
SELECT
    resolved.portfolio_id,
    resolved.valuation_date,
    resolved.source_currency,
    resolved.base_currency,
    resolved.fx_rate_to_base,
    resolved.source,
    resolved.rate_method,
    resolved.rate_source,
    resolved.source_rate_date,
    resolved.age_days,
    resolved.conversion_status
FROM portfolio_dates dates
CROSS JOIN investory.currencies currencies
CROSS JOIN LATERAL investory.resolve_portfolio_fx_rate(
    dates.portfolio_id,
    dates.valuation_date,
    currencies.id::varchar(3)
) resolved;

COMMENT ON VIEW investory.v_portfolio_daily_fx_rate IS
    'Canonical portfolio-aware and date-aware FX layer. amount_in_base = amount_in_source_currency * fx_rate_to_base. STALE and MISSING rows are diagnostic only.';

CREATE OR REPLACE VIEW investory.v_normalized_daily_price AS
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
    JOIN investory.v_canonical_asset_daily_price aph
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
),
ranked_prices AS (
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
        COUNT(*) FILTER (
            WHERE pc.price_date = (
                SELECT MAX(pc2.price_date)
                FROM price_candidates pc2
                WHERE pc2.asset_id = pc.asset_id
                  AND pc2.valuation_date = pc.valuation_date
            )
        ) OVER (PARTITION BY pc.asset_id, pc.valuation_date) AS candidate_count_same_price_date
    FROM price_candidates pc
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

COMMENT ON VIEW investory.v_normalized_daily_price IS
    'Independent deterministic valuation-price selector. Future observations are excluded; effective observation age uses source_date or interpolation_left_date, then freshness precedes quality/source priority. selected_price is close_price multiplied by price_scale_factor exactly once and carries the price_currency of that normalized number.';

CREATE OR REPLACE VIEW investory.v_reconstructed_position_daily AS
WITH active_position_dates AS (
    SELECT
        p.account_id,
        account.portfolio_id,
        a.id AS asset_id,
        d.snapshot_date AS valuation_date,
        p.cost_currency::varchar(3) AS acquisition_currency,
        a.asset_type,
        SUM(investory.signed_position_quantity(p.operation, p.volume)) AS open_quantity,
        SUM(COALESCE(p.purchase_value, COALESCE(p.volume, 0) * COALESCE(p.open_price, 0), 0)) AS reconstructed_cost_base_local,
        SUM(
            CASE
                WHEN acq.conversion_status NOT IN ('OK', 'SAME_CURRENCY') THEN NULL::numeric
                ELSE COALESCE(p.purchase_value, COALESCE(p.volume, 0) * COALESCE(p.open_price, 0), 0) * acq.fx_rate_to_base
            END
        ) AS reconstructed_cost_base_base
    FROM investory.positions p
    JOIN investory.assets a
        ON a.id = p.asset_id
       AND a.exclude_from_import = false
    JOIN investory.accounts account
        ON account.id = p.account_id
    JOIN investory.account_daily d
        ON d.account_id = p.account_id
       AND d.snapshot_date >= COALESCE(p.open_time::date, d.snapshot_date)
       AND (
            p.close_time IS NULL
            OR d.snapshot_date < p.close_time::date
       )
    LEFT JOIN investory.v_portfolio_daily_fx_rate acq
        ON acq.portfolio_id = account.portfolio_id
       AND acq.valuation_date = COALESCE(p.open_time::date, d.snapshot_date)
       AND acq.source_currency = p.cost_currency::varchar(3)
    GROUP BY
        p.account_id,
        account.portfolio_id,
        a.id,
        d.snapshot_date,
        p.cost_currency,
        a.asset_type
),
priced AS (
    SELECT
        apd.account_id,
        apd.portfolio_id,
        apd.asset_id,
        apd.valuation_date,
        apd.open_quantity,
        apd.reconstructed_cost_base_local,
        apd.reconstructed_cost_base_base,
        apd.acquisition_currency,
        apd.asset_type,
        ndp.selected_price,
        ndp.selected_price_date,
        ndp.underlying_observation_date,
        ndp.price_age_days,
        ndp.price_currency,
        ndp.selected_price_history_id,
        ndp.price_origin,
        ndp.quality_class AS price_quality,
        ndp.selection_priority,
        ndp.validation_status AS price_validation_status,
        ndp.validation_message AS price_validation_message,
        ndp.price_scale_factor,
        CASE
            WHEN ndp.quality_class LIKE '%PERCENT_OF_PAR%' THEN 0.01::numeric
            WHEN apd.asset_type IN ('EQUITY', 'ETF', 'INDEX', 'CRYPTOCURRENCY', 'COMMODITY') THEN 1::numeric
            WHEN apd.asset_type = 'BOND' THEN 1::numeric
            ELSE 1::numeric
        END AS contract_multiplier
    FROM active_position_dates apd
    LEFT JOIN investory.v_normalized_daily_price ndp
        ON ndp.asset_id = apd.asset_id
       AND ndp.valuation_date = apd.valuation_date
)
SELECT
    p.account_id,
    p.asset_id,
    p.valuation_date,
    p.open_quantity,
    p.reconstructed_cost_base_local,
    p.reconstructed_cost_base_base,
    p.acquisition_currency,
    p.selected_price,
    p.selected_price_date,
    p.underlying_observation_date,
    p.price_age_days,
    p.price_currency,
    p.contract_multiplier,
    p.selection_priority,
    val.base_currency,
    val.fx_rate_to_base,
    val.conversion_status AS fx_conversion_status,
    CASE
        WHEN p.open_quantity = 0 THEN 0::numeric
        WHEN p.selected_price IS NULL THEN NULL::numeric
        WHEN p.contract_multiplier IS NULL THEN NULL::numeric
        WHEN val.conversion_status NOT IN ('OK', 'SAME_CURRENCY') THEN NULL::numeric
        ELSE p.open_quantity * p.selected_price * p.contract_multiplier * val.fx_rate_to_base
    END AS reconstructed_market_value_base,
    CASE
        WHEN p.open_quantity = 0 THEN 0::numeric
        WHEN p.selected_price IS NULL THEN NULL::numeric
        WHEN p.contract_multiplier IS NULL THEN NULL::numeric
        WHEN val.conversion_status NOT IN ('OK', 'SAME_CURRENCY') THEN NULL::numeric
        WHEN p.reconstructed_cost_base_base IS NULL THEN NULL::numeric
        ELSE p.open_quantity * p.selected_price * p.contract_multiplier * val.fx_rate_to_base
             - p.reconstructed_cost_base_base
    END AS reconstructed_unrealized_profit_base,
    p.price_origin,
    p.price_quality,
    CASE
        WHEN p.open_quantity = 0 THEN 'PASS'
        WHEN p.selected_price IS NULL THEN 'FAIL'
        WHEN p.contract_multiplier IS NULL THEN 'FAIL'
        WHEN val.conversion_status NOT IN ('OK', 'SAME_CURRENCY') THEN 'FAIL'
        WHEN p.reconstructed_cost_base_base IS NULL THEN 'FAIL'
        WHEN p.selection_priority >= 5 THEN 'WARN'
        ELSE 'PASS'
    END::varchar(16) AS reconstruction_status,
    CASE
        WHEN p.open_quantity = 0 THEN 'zero quantity -> zero valuation'
        WHEN p.selected_price IS NULL THEN 'missing valuation price'
        WHEN p.contract_multiplier IS NULL THEN 'missing explicit multiplier metadata'
        WHEN val.conversion_status NOT IN ('OK', 'SAME_CURRENCY') THEN 'missing or stale valuation FX'
        WHEN p.reconstructed_cost_base_base IS NULL THEN 'missing acquisition FX'
        WHEN p.selection_priority >= 5 THEN p.price_validation_message
        ELSE 'reconstructed from positions + selected daily price + FX'
    END::text AS reconstruction_message
FROM priced p
LEFT JOIN investory.v_portfolio_daily_fx_rate val
    ON val.portfolio_id = p.portfolio_id
   AND val.valuation_date = p.valuation_date
   AND val.source_currency = p.price_currency;

COMMENT ON VIEW investory.v_reconstructed_position_daily IS
    'Independent end-of-day position valuation reconstruction from canonical positions, selected prices, and FX. It does not read account_daily market or cost values.';

CREATE OR REPLACE VIEW investory.v_position_valuation_validation AS
WITH neighboring AS (
    SELECT
        rpd.*,
        LAG(rpd.selected_price) OVER (PARTITION BY rpd.asset_id ORDER BY rpd.valuation_date) AS prev_selected_price
    FROM investory.v_reconstructed_position_daily rpd
)
SELECT
    n.account_id,
    n.asset_id,
    n.valuation_date,
    CASE
        WHEN n.reconstruction_status = 'FAIL' THEN 'ERROR'
        WHEN n.reconstruction_status = 'WARN' THEN 'WARN'
        WHEN n.open_quantity = 0 AND COALESCE(n.reconstructed_market_value_base, 0) <> 0 THEN 'ERROR'
        WHEN n.open_quantity <> 0 AND COALESCE(n.selected_price, 0) = 0 THEN 'ERROR'
        WHEN n.prev_selected_price IS NOT NULL
         AND n.prev_selected_price > 0
         AND (
            n.selected_price / n.prev_selected_price
                >= investory.reconciliation_parameter('reconciliation_price_jump_upper_ratio')
            OR n.selected_price / n.prev_selected_price
                <= investory.reconciliation_parameter('reconciliation_price_jump_lower_ratio')
         ) THEN 'WARN'
        ELSE 'INFO'
    END::varchar(16) AS severity,
    CASE
        WHEN n.reconstruction_status = 'FAIL' AND n.selected_price IS NULL THEN 'MISSING_PRICE'
        WHEN n.reconstruction_status = 'FAIL' AND n.contract_multiplier IS NULL THEN 'MISSING_MULTIPLIER'
        WHEN n.reconstruction_status = 'FAIL' AND COALESCE(n.fx_rate_to_base, 0) = 0 THEN 'MISSING_FX'
        WHEN n.selection_priority = 6 THEN 'TRADE_OBSERVATION_SELECTED'
        WHEN n.selection_priority = 5 THEN 'INTERPOLATED_PRICE_SELECTED'
        WHEN n.selection_priority = 3 THEN 'ALTERNATE_LISTING_SELECTED'
        WHEN n.open_quantity = 0 AND COALESCE(n.reconstructed_market_value_base, 0) <> 0 THEN 'ZERO_QUANTITY_NONZERO_VALUE'
        WHEN n.open_quantity <> 0 AND COALESCE(n.selected_price, 0) = 0 THEN 'NONZERO_QUANTITY_ZERO_PRICE'
        WHEN n.prev_selected_price IS NOT NULL
         AND n.prev_selected_price > 0
         AND (
            n.selected_price / n.prev_selected_price
                >= investory.reconciliation_parameter('reconciliation_price_jump_upper_ratio')
            OR n.selected_price / n.prev_selected_price
                <= investory.reconciliation_parameter('reconciliation_price_jump_lower_ratio')
         ) THEN 'PRICE_RATIO_100X'
        ELSE 'OK'
    END::varchar(64) AS validation_code,
    comparison.expected_value,
    n.reconstructed_market_value_base AS actual_value,
    n.reconstructed_market_value_base - comparison.expected_value AS difference,
    (n.reconstructed_market_value_base - comparison.expected_value)
        / NULLIF(ABS(comparison.expected_value), 0) AS relative_difference,
    n.reconstruction_message AS message
FROM neighboring n
CROSS JOIN LATERAL (
    SELECT CASE
        WHEN n.prev_selected_price IS NULL
          OR n.open_quantity IS NULL
          OR n.contract_multiplier IS NULL
          OR n.fx_conversion_status NOT IN ('OK', 'SAME_CURRENCY')
          OR n.fx_rate_to_base IS NULL
            THEN NULL::numeric
        ELSE n.open_quantity
            * n.prev_selected_price
            * n.contract_multiplier
            * n.fx_rate_to_base
    END AS expected_value
) comparison
WHERE
    n.reconstruction_status <> 'PASS'
    OR n.selection_priority >= 3
    OR (n.prev_selected_price IS NOT NULL
        AND n.prev_selected_price > 0
        AND (
            n.selected_price / n.prev_selected_price
                >= investory.reconciliation_parameter('reconciliation_price_jump_upper_ratio')
            OR n.selected_price / n.prev_selected_price
                <= investory.reconciliation_parameter('reconciliation_price_jump_lower_ratio')
        ));

COMMENT ON VIEW investory.v_position_valuation_validation IS
    'Position-level valuation diagnostics independent from account_daily. It reports missing price/FX/multiplier, low-quality price selection, and obvious 100x scale anomalies.';

CREATE OR REPLACE VIEW investory.v_reconstructed_cash_daily AS
WITH valuation_dates AS (
    SELECT DISTINCT ad.account_id, a.portfolio_id, ad.snapshot_date AS valuation_date
    FROM investory.account_daily ad
    JOIN investory.accounts a ON a.id = ad.account_id
),
cash_legs AS (
    SELECT
        vd.account_id,
        vd.portfolio_id,
        vd.valuation_date,
        nco.currency::varchar(3) AS operation_currency,
        SUM(
            CASE
                WHEN nco.date::date <= vd.valuation_date THEN COALESCE(nco.amount, 0)
                ELSE NULL::numeric
            END
        ) AS ending_native_cash,
        SUM(
            CASE
                WHEN nco.date::date = vd.valuation_date THEN COALESCE(nco.amount, 0)
                ELSE 0
            END
        ) AS daily_native_cash_movement
    FROM valuation_dates vd
    JOIN investory.normalized_cash_operations nco
        ON nco.account_id = vd.account_id
    GROUP BY vd.account_id, vd.portfolio_id, vd.valuation_date, nco.currency
)
SELECT
    cl.account_id,
    cl.valuation_date,
    cl.operation_currency,
    cl.ending_native_cash,
    fx.fx_rate_to_base,
    cl.daily_native_cash_movement,
    CASE
        WHEN fx.conversion_status NOT IN ('OK', 'SAME_CURRENCY') THEN NULL::numeric
        ELSE cl.daily_native_cash_movement * fx.fx_rate_to_base
    END AS transaction_cash_movement_base,
    CASE
        WHEN fx.conversion_status NOT IN ('OK', 'SAME_CURRENCY') THEN NULL::numeric
        ELSE cl.ending_native_cash * fx.fx_rate_to_base
    END AS reconstructed_cash_component_base,
    CASE
        WHEN fx.conversion_status NOT IN ('OK', 'SAME_CURRENCY') THEN 'FAIL'
        ELSE 'PASS'
    END::varchar(16) AS status,
    CASE
        WHEN fx.conversion_status NOT IN ('OK', 'SAME_CURRENCY') THEN 'missing or stale FX for cash balance revaluation'
        ELSE 'cash reconstructed from normalized cash ledger'
    END::text AS validation_message
FROM cash_legs cl
LEFT JOIN investory.v_portfolio_daily_fx_rate fx
    ON fx.portfolio_id = cl.portfolio_id
   AND fx.valuation_date = cl.valuation_date
   AND fx.source_currency = cl.operation_currency;

COMMENT ON VIEW investory.v_reconstructed_cash_daily IS
    'Independent cash reconstruction from normalized_cash_operations. Foreign-currency cash is revalued to base currency using canonical FX for each valuation date.';

CREATE OR REPLACE VIEW investory.v_realized_result_reconciliation AS
WITH trade_components AS (
    SELECT
        p.account_id,
        p.close_time::date AS valuation_date,
        pf.base_currency::varchar(3) AS base_currency,
        p.profit_currency::varchar(3) AS source_currency,
        'PROFIT'::varchar(16) AS component_type,
        COALESCE(p.profit, 0) AS amount_native
    FROM investory.positions p
    JOIN investory.accounts a ON a.id = p.account_id
    JOIN investory.portfolios pf ON pf.id = a.portfolio_id
    JOIN investory.assets asset ON asset.id = p.asset_id
                               AND asset.exclude_from_import = false
    WHERE p.close_time IS NOT NULL
    UNION ALL
    SELECT p.account_id, p.close_time::date, pf.base_currency::varchar(3),
           p.profit_currency::varchar(3), 'SWAP'::varchar(16), COALESCE(p.swap, 0)
    FROM investory.positions p
    JOIN investory.accounts a ON a.id = p.account_id
    JOIN investory.portfolios pf ON pf.id = a.portfolio_id
    JOIN investory.assets asset ON asset.id = p.asset_id
                              AND asset.exclude_from_import = false
    WHERE p.close_time IS NOT NULL
    UNION ALL
    SELECT p.account_id, p.close_time::date, pf.base_currency::varchar(3),
           p.commission_currency::varchar(3), 'COMMISSION'::varchar(16), COALESCE(p.commission, 0)
    FROM investory.positions p
    JOIN investory.accounts a ON a.id = p.account_id
    JOIN investory.portfolios pf ON pf.id = a.portfolio_id
    JOIN investory.assets asset ON asset.id = p.asset_id
                              AND asset.exclude_from_import = false
    WHERE p.close_time IS NOT NULL
), converted_trade_components AS (
    SELECT tc.*, fx.fx_rate_to_target, fx.conversion_status
    FROM trade_components tc
    LEFT JOIN LATERAL investory.resolve_fx_rate(
        tc.valuation_date, tc.source_currency, tc.base_currency
    ) fx ON true
), trade_result AS (
    SELECT
        ctc.account_id,
        ctc.valuation_date,
        SUM(ctc.amount_native * ctc.fx_rate_to_target) FILTER (
            WHERE ctc.component_type = 'PROFIT'
              AND ctc.conversion_status IN ('OK', 'SAME_CURRENCY')) AS realized_trade_profit,
        SUM(ctc.amount_native * ctc.fx_rate_to_target) FILTER (
            WHERE ctc.component_type = 'SWAP'
              AND ctc.conversion_status IN ('OK', 'SAME_CURRENCY')) AS swap_cost,
        SUM(ctc.amount_native * ctc.fx_rate_to_target) FILTER (
            WHERE ctc.component_type = 'COMMISSION'
              AND ctc.conversion_status IN ('OK', 'SAME_CURRENCY')) AS commission_cost,
        COUNT(*) FILTER (
            WHERE ctc.conversion_status NOT IN ('OK', 'SAME_CURRENCY'))::bigint AS missing_fx_count,
        COUNT(*) FILTER (
            WHERE ctc.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) = 0 AS is_complete
    FROM converted_trade_components ctc
    GROUP BY ctc.account_id, ctc.valuation_date
),
cash_components AS (
    SELECT
        nco.account_id,
        nco.date::date AS valuation_date,
        SUM(-nco.amount_in_portfolio_base_currency) FILTER (
            WHERE nco.normalized_category = 'FEE') AS fee_component,
        SUM(-nco.amount_in_portfolio_base_currency) FILTER (
            WHERE nco.raw_operation = 'SWAP') AS swap_component,
        SUM(nco.amount_in_portfolio_base_currency) FILTER (
            WHERE nco.raw_operation = 'ROLLOVER') AS rollover_component,
        SUM(nco.amount_in_portfolio_base_currency) FILTER (
            WHERE nco.normalized_category = 'CORRECTION') AS correction_component,
        COUNT(*) FILTER (WHERE nco.portfolio_conversion_status NOT IN ('OK', 'SAME_CURRENCY'))::bigint AS missing_fx_count,
        COUNT(*) FILTER (WHERE nco.portfolio_conversion_status NOT IN ('OK', 'SAME_CURRENCY')) = 0 AS is_complete
    FROM investory.normalized_cash_operations nco
    GROUP BY nco.account_id, nco.date::date
)
SELECT
    COALESCE(tr.account_id, cc.account_id) AS account_id,
    COALESCE(tr.valuation_date, cc.valuation_date) AS valuation_date,
    CASE WHEN COALESCE(tr.is_complete, true) THEN COALESCE(tr.realized_trade_profit, 0) ELSE NULL::numeric END AS realized_trade_profit,
    CASE WHEN COALESCE(tr.is_complete, true) THEN COALESCE(tr.swap_cost, 0) ELSE NULL::numeric END AS swap_cost,
    CASE WHEN COALESCE(tr.is_complete, true) THEN COALESCE(tr.commission_cost, 0) ELSE NULL::numeric END AS commission_cost,
    CASE WHEN COALESCE(cc.is_complete, true) THEN COALESCE(cc.fee_component, 0) ELSE NULL::numeric END AS fee_component,
    CASE WHEN COALESCE(cc.is_complete, true) THEN COALESCE(cc.swap_component, 0) ELSE NULL::numeric END AS cash_swap_component,
    CASE WHEN COALESCE(cc.is_complete, true) THEN COALESCE(cc.rollover_component, 0) ELSE NULL::numeric END AS rollover_component,
    CASE WHEN COALESCE(cc.is_complete, true) THEN COALESCE(cc.correction_component, 0) ELSE NULL::numeric END AS correction_component,
    COALESCE(tr.missing_fx_count, 0) + COALESCE(cc.missing_fx_count, 0) AS missing_fx_count,
    COALESCE(tr.is_complete, true) AND COALESCE(cc.is_complete, true) AS is_complete,
    CASE WHEN COALESCE(tr.is_complete, true) AND COALESCE(cc.is_complete, true)
        THEN COALESCE(tr.realized_trade_profit, 0)
            + COALESCE(tr.swap_cost, 0)
            + COALESCE(tr.commission_cost, 0)
        ELSE NULL::numeric END AS reconstructed_total_realized_result
FROM trade_result tr
FULL OUTER JOIN cash_components cc
    ON cc.account_id = tr.account_id
   AND cc.valuation_date = tr.valuation_date;

COMMENT ON VIEW investory.v_realized_result_reconciliation IS
    'Independent decomposition of realized result into trade profit, swap, commission, fee-like cash rows, rollover, and corrections.';

CREATE MATERIALIZED VIEW investory.v_account_daily_reconciliation AS
WITH market_side AS (
    SELECT
        rpd.account_id,
        rpd.valuation_date,
        SUM(COALESCE(rpd.reconstructed_market_value_base, 0)) AS reconstructed_market_value,
        SUM(COALESCE(rpd.reconstructed_cost_base_base, 0)) AS reconstructed_cost_base,
        SUM(COALESCE(rpd.reconstructed_unrealized_profit_base, 0)) AS reconstructed_unrealized_profit,
        MAX(CASE WHEN rpd.reconstruction_status = 'FAIL' THEN 1 ELSE 0 END) AS has_market_fail,
        MAX(CASE WHEN rpd.reconstruction_status = 'WARN' THEN 1 ELSE 0 END) AS has_market_warn
    FROM investory.v_reconstructed_position_daily rpd
    GROUP BY rpd.account_id, rpd.valuation_date
),
cash_side AS (
    SELECT
        rcd.account_id,
        rcd.valuation_date,
        SUM(COALESCE(rcd.reconstructed_cash_component_base, 0)) AS reconstructed_cash_balance,
        MAX(CASE WHEN rcd.status = 'FAIL' THEN 1 ELSE 0 END) AS has_cash_fail
    FROM investory.v_reconstructed_cash_daily rcd
    GROUP BY rcd.account_id, rcd.valuation_date
),
realized_side AS (
    SELECT
        r.account_id,
        r.valuation_date,
        CASE
            WHEN COUNT(*) FILTER (
                WHERE NOT COALESCE(r.is_complete, false)
                   OR r.reconstructed_total_realized_result IS NULL
            ) > 0 THEN NULL::numeric
            ELSE SUM(r.reconstructed_total_realized_result)
        END AS reconstructed_total_realized_result,
        MAX(CASE WHEN NOT COALESCE(r.is_complete, false) THEN 1 ELSE 0 END) AS has_realized_fail
    FROM investory.v_realized_result_reconciliation r
    GROUP BY r.account_id, r.valuation_date
)
SELECT
    ad.account_id,
    ad.snapshot_date AS valuation_date,
    investory.reconciliation_display_value(ad.cash_balance) AS reported_cash_balance,
    investory.reconciliation_display_value(COALESCE(cs.reconstructed_cash_balance, 0)) AS reconstructed_cash_balance,
    investory.reconciliation_display_value(ad.cash_balance - COALESCE(cs.reconstructed_cash_balance, 0)) AS cash_difference,
    investory.reconciliation_display_value(ad.market_value) AS reported_market_value,
    investory.reconciliation_display_value(COALESCE(ms.reconstructed_market_value, 0)) AS reconstructed_market_value,
    investory.reconciliation_display_value(ad.market_value - COALESCE(ms.reconstructed_market_value, 0)) AS market_value_difference,
    investory.reconciliation_display_value(ad.cost_base) AS reported_cost_base,
    investory.reconciliation_display_value(COALESCE(ms.reconstructed_cost_base, 0)) AS reconstructed_cost_base,
    investory.reconciliation_display_value(ad.cost_base - COALESCE(ms.reconstructed_cost_base, 0)) AS cost_base_difference,
    investory.reconciliation_display_value(ad.unrealized_profit) AS reported_unrealized_profit,
    investory.reconciliation_display_value(COALESCE(ms.reconstructed_unrealized_profit, 0)) AS reconstructed_unrealized_profit,
    investory.reconciliation_display_value(ad.unrealized_profit - COALESCE(ms.reconstructed_unrealized_profit, 0)) AS unrealized_difference,
    investory.reconciliation_display_value(ad.equity) AS reported_equity,
    investory.reconciliation_display_value(COALESCE(cs.reconstructed_cash_balance, 0) + COALESCE(ms.reconstructed_market_value, 0)) AS reconstructed_equity,
    investory.reconciliation_display_value(ad.equity - (COALESCE(cs.reconstructed_cash_balance, 0) + COALESCE(ms.reconstructed_market_value, 0))) AS equity_difference,
    investory.reconciliation_display_value(ad.realized_profit) AS reported_realized_profit,
    CASE
        WHEN COALESCE(rs.has_realized_fail, 0) = 1 THEN NULL::numeric
        ELSE investory.reconciliation_display_value(COALESCE(rs.reconstructed_total_realized_result, 0))
    END AS reconstructed_total_realized_result,
    CASE
        WHEN COALESCE(rs.has_realized_fail, 0) = 1
          OR (rs.reconstructed_total_realized_result IS NULL AND rs.account_id IS NOT NULL)
            THEN NULL::numeric
        ELSE investory.reconciliation_display_value(ad.realized_profit - COALESCE(rs.reconstructed_total_realized_result, 0))
    END AS realized_difference,
    ROUND(investory.reconciliation_effective_tolerance(
        ad.market_value, COALESCE(ms.reconstructed_market_value, 0)
    ), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS market_value_effective_tolerance,
    ROUND(investory.reconciliation_effective_tolerance(
        ad.cash_balance, COALESCE(cs.reconstructed_cash_balance, 0)
    ), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS cash_effective_tolerance,
    ROUND(investory.reconciliation_effective_tolerance(
        ad.equity, COALESCE(cs.reconstructed_cash_balance, 0) + COALESCE(ms.reconstructed_market_value, 0)
    ), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS equity_effective_tolerance,
    ROUND(investory.reconciliation_effective_tolerance(
        ad.cost_base, COALESCE(ms.reconstructed_cost_base, 0)
    ), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS cost_base_effective_tolerance,
    ROUND(investory.reconciliation_effective_tolerance(
        ad.unrealized_profit, COALESCE(ms.reconstructed_unrealized_profit, 0)
    ), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS unrealized_effective_tolerance,
    ROUND(investory.reconciliation_effective_tolerance(
        ad.realized_profit, COALESCE(rs.reconstructed_total_realized_result, 0)
    ), investory.reconciliation_parameter('reconciliation_reporting_scale')::integer) AS realized_effective_tolerance,
    CASE
        WHEN COALESCE(ms.has_market_fail, 0) = 1
          OR COALESCE(cs.has_cash_fail, 0) = 1
          OR COALESCE(rs.has_realized_fail, 0) = 1 THEN 'FAIL'
        WHEN NOT investory.reconciliation_values_match(ad.market_value, COALESCE(ms.reconstructed_market_value, 0))
          OR NOT investory.reconciliation_values_match(ad.cash_balance, COALESCE(cs.reconstructed_cash_balance, 0))
          OR NOT investory.reconciliation_values_match(ad.equity, COALESCE(cs.reconstructed_cash_balance, 0) + COALESCE(ms.reconstructed_market_value, 0))
          OR NOT investory.reconciliation_values_match(ad.cost_base, COALESCE(ms.reconstructed_cost_base, 0))
          OR NOT investory.reconciliation_values_match(ad.unrealized_profit, COALESCE(ms.reconstructed_unrealized_profit, 0))
          OR NOT investory.reconciliation_values_match(ad.realized_profit, COALESCE(rs.reconstructed_total_realized_result, 0))
          THEN 'FAIL'
        WHEN COALESCE(ms.has_market_warn, 0) = 1 THEN 'WARN'
        ELSE 'PASS'
    END::varchar(16) AS status,
    CASE
        WHEN COALESCE(ms.has_market_fail, 0) = 1
          OR COALESCE(cs.has_cash_fail, 0) = 1
          OR COALESCE(rs.has_realized_fail, 0) = 1 THEN 'ERROR'
        WHEN COALESCE(ms.has_market_warn, 0) = 1 THEN 'WARN'
        ELSE 'INFO'
    END::varchar(16) AS severity,
    CASE
        WHEN COALESCE(ms.has_market_fail, 0) = 1 THEN 'position reconstruction failed'
        WHEN COALESCE(cs.has_cash_fail, 0) = 1 THEN 'cash reconstruction failed'
        WHEN COALESCE(rs.has_realized_fail, 0) = 1 THEN 'realized result reconstruction failed'
        WHEN NOT investory.reconciliation_values_match(ad.market_value, COALESCE(ms.reconstructed_market_value, 0)) THEN 'market value mismatch'
        WHEN NOT investory.reconciliation_values_match(ad.cash_balance, COALESCE(cs.reconstructed_cash_balance, 0)) THEN 'cash mismatch'
        WHEN NOT investory.reconciliation_values_match(ad.equity, COALESCE(cs.reconstructed_cash_balance, 0) + COALESCE(ms.reconstructed_market_value, 0)) THEN 'equity mismatch'
        WHEN NOT investory.reconciliation_values_match(ad.cost_base, COALESCE(ms.reconstructed_cost_base, 0)) THEN 'cost base mismatch'
        WHEN NOT investory.reconciliation_values_match(ad.unrealized_profit, COALESCE(ms.reconstructed_unrealized_profit, 0)) THEN 'unrealized mismatch'
        WHEN NOT investory.reconciliation_values_match(ad.realized_profit, COALESCE(rs.reconstructed_total_realized_result, 0)) THEN 'realized result mismatch'
        WHEN COALESCE(ms.has_market_warn, 0) = 1 THEN 'valuation used lower-quality price source'
        ELSE 'reconciliation passed'
    END::text AS validation_message
FROM investory.account_daily ad
LEFT JOIN market_side ms
    ON ms.account_id = ad.account_id
   AND ms.valuation_date = ad.snapshot_date
LEFT JOIN cash_side cs
    ON cs.account_id = ad.account_id
   AND cs.valuation_date = ad.snapshot_date
LEFT JOIN realized_side rs
    ON rs.account_id = ad.account_id
   AND rs.valuation_date = ad.snapshot_date;

CREATE UNIQUE INDEX ux_mv_v_account_daily_reconciliation_key
    ON investory.v_account_daily_reconciliation(account_id, valuation_date);

COMMENT ON MATERIALIZED VIEW investory.v_account_daily_reconciliation IS
    'Independent account-level reconciliation of account_daily against reconstructed cash, reconstructed market value, reconstructed cost base, and reconstructed total realized result including signed swap and commission.';

CREATE OR REPLACE VIEW investory.v_non_usd_closed_trade_reconciliation AS
WITH closed_positions AS (
    SELECT
        p.id AS position_id,
        p.account_id,
        account.portfolio_id,
        p.asset_id,
        a.id AS asset_numeric_id,
        p.profit_currency::varchar(3) AS trade_currency,
        p.commission_currency::varchar(3) AS commission_currency,
        portfolio.base_currency::varchar(3) AS portfolio_base_currency,
        p.open_time::date AS open_date,
        p.close_time::date AS close_date,
        COALESCE(p.volume, 0) AS close_quantity,
        COALESCE(p.purchase_value, COALESCE(p.volume, 0) * COALESCE(p.open_price, 0), 0) AS purchase_value_local,
        COALESCE(p.sale_value, COALESCE(p.volume, 0) * COALESCE(p.close_price, 0), 0) AS sale_value_local,
        COALESCE(p.profit, 0) AS realized_profit_local,
        COALESCE(p.commission, 0) AS commission_local,
        COALESCE(p.swap, 0) AS swap_local,
        COALESCE(p.close_price, 0) AS close_price_local
    FROM investory.positions p
    JOIN investory.assets a
        ON a.id = p.asset_id
    JOIN investory.accounts account
        ON account.id = p.account_id
    JOIN investory.portfolios portfolio
        ON portfolio.id = account.portfolio_id
    WHERE p.close_time IS NOT NULL
      AND (p.profit_currency::varchar(3) <> portfolio.base_currency::varchar(3)
        OR p.commission_currency::varchar(3) <> portfolio.base_currency::varchar(3))
),
previous_trade_day AS (
    SELECT
        cp.position_id,
        MAX(ad.snapshot_date) AS previous_valuation_date
    FROM closed_positions cp
    JOIN investory.account_daily ad
        ON ad.account_id = cp.account_id
       AND ad.snapshot_date < cp.close_date
    GROUP BY cp.position_id
),
close_day_account AS (
    SELECT
        cp.position_id,
        ad.snapshot_date AS valuation_date,
        ad.cash_balance,
        ad.market_value,
        ad.equity,
        ad.daily_profit_amount,
        LAG(ad.cash_balance) OVER (PARTITION BY ad.account_id ORDER BY ad.snapshot_date) AS previous_cash_balance,
        LAG(ad.market_value) OVER (PARTITION BY ad.account_id ORDER BY ad.snapshot_date) AS previous_market_value,
        LAG(ad.equity) OVER (PARTITION BY ad.account_id ORDER BY ad.snapshot_date) AS previous_equity
    FROM closed_positions cp
    JOIN investory.account_daily ad
        ON ad.account_id = cp.account_id
       AND ad.snapshot_date = cp.close_date
),
previous_symbol_value AS (
    SELECT
        cp.position_id,
        ptd.previous_valuation_date,
        rpd.open_quantity AS previous_open_quantity,
        rpd.selected_price AS previous_selected_price_local,
        rpd.price_currency AS previous_price_currency,
        rpd.reconstructed_market_value_base AS previous_symbol_market_value_base,
        rpd.reconstructed_cost_base_base AS previous_symbol_cost_base_base
    FROM closed_positions cp
    LEFT JOIN previous_trade_day ptd
        ON ptd.position_id = cp.position_id
    LEFT JOIN investory.v_reconstructed_position_daily rpd
        ON rpd.account_id = cp.account_id
       AND rpd.asset_id = cp.asset_numeric_id
       AND rpd.valuation_date = ptd.previous_valuation_date
),
fx_at_close AS (
    SELECT
        cp.position_id,
        profit_fx.fx_rate_to_target AS close_fx_rate_to_base,
        profit_fx.conversion_status AS profit_conversion_status,
        commission_fx.fx_rate_to_target AS commission_fx_rate_to_base,
        commission_fx.conversion_status AS commission_conversion_status
    FROM closed_positions cp
    LEFT JOIN LATERAL investory.resolve_fx_rate(
        cp.close_date, cp.trade_currency, cp.portfolio_base_currency
    ) profit_fx ON true
    LEFT JOIN LATERAL investory.resolve_fx_rate(
        cp.close_date, cp.commission_currency, cp.portfolio_base_currency
    ) commission_fx ON true
),
day_cash_other_flows AS (
    SELECT
        cp.position_id,
        SUM(
            CASE
                WHEN nco.normalized_category IN ('TRADE_PURCHASE', 'TRADE_SALE', 'REALIZED_TRADE_RESULT')
                    THEN 0
                ELSE nco.amount_in_portfolio_base_currency
            END
        ) AS non_trade_cash_flow_base
    FROM closed_positions cp
    LEFT JOIN investory.normalized_cash_operations nco
        ON nco.account_id = cp.account_id
       AND nco.date::date = cp.close_date
    GROUP BY cp.position_id
)
SELECT
    cp.position_id,
    cp.account_id,
    cp.asset_id,
    cp.trade_currency,
    cp.commission_currency,
    cp.open_date,
    cp.close_date AS valuation_date,
    psv.previous_valuation_date,
    cp.close_quantity,
    cp.purchase_value_local,
    cp.sale_value_local,
    cp.realized_profit_local,
    cp.commission_local,
    cp.swap_local,
    cp.close_price_local,
    psv.previous_open_quantity,
    psv.previous_selected_price_local,
    psv.previous_price_currency,
    psv.previous_symbol_market_value_base,
    psv.previous_symbol_cost_base_base,
    fx.close_fx_rate_to_base,
    fx.commission_fx_rate_to_base,
    CASE
        WHEN fx.close_fx_rate_to_base IS NULL THEN NULL::numeric
        ELSE cp.sale_value_local * fx.close_fx_rate_to_base
    END AS sale_value_base_at_close_fx,
    CASE
        WHEN fx.profit_conversion_status NOT IN ('OK', 'SAME_CURRENCY')
          OR fx.commission_conversion_status NOT IN ('OK', 'SAME_CURRENCY') THEN NULL::numeric
        ELSE (cp.realized_profit_local + cp.swap_local) * fx.close_fx_rate_to_base
            + cp.commission_local * fx.commission_fx_rate_to_base
    END AS net_realized_result_base_at_close_fx,
    CASE
        WHEN COALESCE(psv.previous_open_quantity, 0) = 0 THEN NULL::numeric
        ELSE COALESCE(psv.previous_symbol_market_value_base, 0) * cp.close_quantity / psv.previous_open_quantity
    END AS allocated_previous_market_value_base,
    COALESCE(cda.cash_balance, 0) - COALESCE(cda.previous_cash_balance, 0) AS account_cash_delta_base,
    COALESCE(cda.market_value, 0) - COALESCE(cda.previous_market_value, 0) AS account_market_delta_base,
    COALESCE(cda.equity, 0) - COALESCE(cda.previous_equity, 0) AS account_equity_delta_base,
    COALESCE(cda.daily_profit_amount, 0) AS reported_daily_profit_base,
    COALESCE(dcof.non_trade_cash_flow_base, 0) AS non_trade_cash_flow_base,
    adr.status AS account_reconciliation_status,
    adr.validation_message AS account_reconciliation_message,
    CASE
        WHEN adr.status = 'FAIL' THEN 'ACCOUNT_RECON_FAIL'
        WHEN fx.close_fx_rate_to_base IS NULL OR fx.commission_fx_rate_to_base IS NULL
            THEN 'MISSING_CLOSE_FX'
        WHEN psv.previous_valuation_date IS NULL THEN 'MISSING_PREVIOUS_DAY'
        WHEN COALESCE(psv.previous_open_quantity, 0) = 0 THEN 'MISSING_PREVIOUS_POSITION'
        WHEN psv.previous_selected_price_local IS NULL THEN 'MISSING_PREVIOUS_PRICE'
        WHEN ABS(cp.sale_value_local - (cp.close_quantity * COALESCE(psv.previous_selected_price_local, 0)))
             <= investory.reconciliation_parameter('reconciliation_local_sale_price_relative_threshold')
                * GREATEST(1, ABS(cp.sale_value_local))
             AND ABS(
                 COALESCE(
                     CASE
                         WHEN fx.close_fx_rate_to_base IS NULL THEN NULL::numeric
                         ELSE cp.sale_value_local * fx.close_fx_rate_to_base
                     END,
                     0
                 )
                 - COALESCE(
                     CASE
                         WHEN COALESCE(psv.previous_open_quantity, 0) = 0 THEN NULL::numeric
                         ELSE COALESCE(psv.previous_symbol_market_value_base, 0) * cp.close_quantity / psv.previous_open_quantity
                     END,
                     0
                 )
             ) > investory.reconciliation_parameter('reconciliation_local_flat_base_jump_relative_threshold')
                 * GREATEST(
                 1,
                 ABS(
                     COALESCE(
                         CASE
                             WHEN COALESCE(psv.previous_open_quantity, 0) = 0 THEN NULL::numeric
                             ELSE COALESCE(psv.previous_symbol_market_value_base, 0) * cp.close_quantity / psv.previous_open_quantity
                         END,
                         0
                     )
                 )
             )
            THEN 'LOCAL_FLAT_BASE_JUMP'
        ELSE 'OK'
    END::varchar(64) AS anomaly_code
FROM closed_positions cp
LEFT JOIN previous_symbol_value psv
    ON psv.position_id = cp.position_id
LEFT JOIN fx_at_close fx
    ON fx.position_id = cp.position_id
LEFT JOIN close_day_account cda
    ON cda.position_id = cp.position_id
LEFT JOIN day_cash_other_flows dcof
    ON dcof.position_id = cp.position_id
LEFT JOIN investory.v_account_daily_reconciliation adr
    ON adr.account_id = cp.account_id
   AND adr.valuation_date = cp.close_date;

COMMENT ON VIEW investory.v_non_usd_closed_trade_reconciliation IS
    'Diagnostic view for non-USD closed trades. It lines up prior carried market value, close-day sale value, account cash/market deltas, and reconciliation status to spot fake balance jumps caused by valuation mistakes.';

CREATE MATERIALIZED VIEW investory.reporting_trade_settlement_reconciliation AS
WITH closed_lots AS (
    SELECT
        account.portfolio_id,
        p.account_id,
        p.asset_id,
        asset.symbol,
        p.close_time::date AS valuation_date,
        p.settlement_model::varchar(32) AS position_settlement_model,
        ABS(COALESCE(p.volume, 0)) AS closed_quantity,
        CASE WHEN p.settlement_model = 'CASH_SETTLED' THEN
            COALESCE(p.sale_value,
                ABS(COALESCE(p.volume, 0)) * COALESCE(p.close_price, 0), 0)
        END AS close_notional_native,
        CASE WHEN p.settlement_model = 'RESULT_ONLY'
              AND profit_fx.conversion_status IN ('OK', 'SAME_CURRENCY')
              AND (COALESCE(p.commission, 0) = 0
                   OR commission_fx.conversion_status IN ('OK', 'SAME_CURRENCY'))
            THEN (COALESCE(p.profit, 0) - COALESCE(p.swap, 0))
                    * profit_fx.fx_rate_to_base
                - COALESCE(p.commission, 0)
                    * COALESCE(commission_fx.fx_rate_to_base, 0)
        END AS close_result_base,
        cost_fx.fx_rate_to_base AS cost_fx_rate_to_base,
        cost_fx.conversion_status AS cost_conversion_status,
        CASE
            WHEN p.settlement_model = 'CASH_SETTLED'
             AND cost_fx.conversion_status NOT IN ('OK', 'SAME_CURRENCY') THEN 1
            WHEN p.settlement_model = 'RESULT_ONLY'
             AND (profit_fx.conversion_status NOT IN ('OK', 'SAME_CURRENCY')
                  OR (COALESCE(p.commission, 0) <> 0
                      AND commission_fx.conversion_status NOT IN ('OK', 'SAME_CURRENCY'))) THEN 1
            ELSE 0
        END AS missing_fx_count
    FROM investory.positions p
    JOIN investory.accounts account ON account.id = p.account_id
    JOIN investory.assets asset ON asset.id = p.asset_id
    LEFT JOIN investory.v_portfolio_daily_fx_rate cost_fx
      ON cost_fx.portfolio_id = account.portfolio_id
     AND cost_fx.valuation_date = p.close_time::date
     AND cost_fx.source_currency = p.cost_currency::varchar(3)
    LEFT JOIN investory.v_portfolio_daily_fx_rate profit_fx
      ON profit_fx.portfolio_id = account.portfolio_id
     AND profit_fx.valuation_date = p.close_time::date
     AND profit_fx.source_currency = p.profit_currency::varchar(3)
    LEFT JOIN investory.v_portfolio_daily_fx_rate commission_fx
      ON commission_fx.portfolio_id = account.portfolio_id
     AND commission_fx.valuation_date = p.close_time::date
     AND commission_fx.source_currency = p.commission_currency::varchar(3)
    WHERE p.close_time IS NOT NULL
), closed_group AS (
    SELECT
        cl.portfolio_id,
        cl.account_id,
        cl.asset_id,
        cl.symbol,
        cl.valuation_date,
        COUNT(*)::bigint AS closed_lot_count,
        COUNT(*) FILTER (WHERE cl.position_settlement_model = 'CASH_SETTLED')::bigint
            AS cash_settled_lot_count,
        COUNT(*) FILTER (WHERE cl.position_settlement_model = 'RESULT_ONLY')::bigint
            AS result_only_lot_count,
        COUNT(*) FILTER (WHERE cl.position_settlement_model = 'UNCLASSIFIED')::bigint
            AS unclassified_lot_count,
        SUM(cl.closed_quantity) AS closed_quantity,
        SUM(cl.closed_quantity) FILTER (
            WHERE cl.position_settlement_model = 'CASH_SETTLED')
            AS cash_settled_closed_quantity,
        SUM(cl.close_notional_native) FILTER (
            WHERE cl.position_settlement_model = 'CASH_SETTLED')
            AS position_close_notional_native,
        CASE WHEN COUNT(*) FILTER (
            WHERE cl.position_settlement_model = 'CASH_SETTLED'
              AND cl.cost_conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0
            THEN NULL::numeric
            ELSE SUM(cl.close_notional_native * cl.cost_fx_rate_to_base) FILTER (
                WHERE cl.position_settlement_model = 'CASH_SETTLED')
        END AS position_close_notional_base,
        CASE WHEN COUNT(*) FILTER (
            WHERE cl.position_settlement_model = 'RESULT_ONLY'
              AND cl.close_result_base IS NULL) > 0
            THEN NULL::numeric
            ELSE SUM(cl.close_result_base) FILTER (
                WHERE cl.position_settlement_model = 'RESULT_ONLY')
        END AS position_close_result_base,
        SUM(cl.missing_fx_count)::bigint
            AS close_missing_fx_count
    FROM closed_lots cl
    GROUP BY cl.portfolio_id, cl.account_id, cl.asset_id, cl.symbol, cl.valuation_date
), opened_lots AS (
    SELECT
        p.account_id,
        p.asset_id,
        p.open_time::date AS valuation_date,
        p.settlement_model::varchar(32) AS position_settlement_model,
        ABS(COALESCE(p.volume, 0)) AS opened_quantity,
        CASE WHEN p.settlement_model = 'CASH_SETTLED' THEN
            COALESCE(p.purchase_value,
                ABS(COALESCE(p.volume, 0)) * COALESCE(p.open_price, 0), 0)
        END AS open_notional_native,
        fx.fx_rate_to_base,
        fx.conversion_status
    FROM investory.positions p
    JOIN investory.accounts account ON account.id = p.account_id
    JOIN closed_group target
      ON target.account_id = p.account_id
     AND target.asset_id = p.asset_id
     AND target.valuation_date = p.open_time::date
    LEFT JOIN investory.v_portfolio_daily_fx_rate fx
      ON fx.portfolio_id = account.portfolio_id
     AND fx.valuation_date = p.open_time::date
     AND fx.source_currency = p.cost_currency::varchar(3)
), opened_group AS (
    SELECT
        ol.account_id,
        ol.asset_id,
        ol.valuation_date,
        COUNT(*)::bigint AS opened_lot_count,
        SUM(ol.opened_quantity) AS opened_quantity,
        SUM(ol.opened_quantity) FILTER (
            WHERE ol.position_settlement_model = 'CASH_SETTLED')
            AS cash_settled_opened_quantity,
        CASE WHEN COUNT(*) FILTER (
            WHERE ol.position_settlement_model = 'CASH_SETTLED'
              AND ol.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0
            THEN NULL::numeric
            ELSE SUM(ol.open_notional_native * ol.fx_rate_to_base) FILTER (
                WHERE ol.position_settlement_model = 'CASH_SETTLED')
        END AS position_open_notional_base,
        COUNT(*) FILTER (
            WHERE ol.position_settlement_model = 'CASH_SETTLED'
              AND ol.conversion_status NOT IN ('OK', 'SAME_CURRENCY'))::bigint
            AS open_missing_fx_count
    FROM opened_lots ol
    GROUP BY ol.account_id, ol.asset_id, ol.valuation_date
), ledger_rows AS (
    SELECT
        target.account_id,
        target.asset_id,
        target.valuation_date,
        co.id AS operation_id,
        co.operation::varchar(64) AS raw_operation,
        co.amount,
        fx.fx_rate_to_base,
        fx.conversion_status
    FROM closed_group target
    LEFT JOIN investory.cash_operations co
      ON co.account_id = target.account_id
     AND co.asset_id = target.asset_id
     AND co.date::date = target.valuation_date
     AND co.operation IN ('STOCK_PURCHASE', 'STOCK_SELL', 'CLOSE_TRADE')
    LEFT JOIN investory.v_portfolio_daily_fx_rate fx
      ON fx.portfolio_id = target.portfolio_id
     AND fx.valuation_date = target.valuation_date
     AND fx.source_currency = co.currency::varchar(3)
), ledger_group AS (
    SELECT
        lr.account_id,
        lr.asset_id,
        lr.valuation_date,
        COUNT(lr.operation_id) FILTER (
            WHERE lr.raw_operation = 'STOCK_SELL')::bigint AS ledger_sale_row_count,
        COUNT(lr.operation_id) FILTER (
            WHERE lr.raw_operation = 'CLOSE_TRADE')::bigint AS ledger_close_result_row_count,
        CASE WHEN COUNT(lr.operation_id) FILTER (
            WHERE lr.raw_operation = 'STOCK_SELL'
              AND lr.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0
            THEN NULL::numeric
            ELSE SUM(lr.amount * lr.fx_rate_to_base) FILTER (
                WHERE lr.raw_operation = 'STOCK_SELL')
        END AS ledger_sale_cash_base,
        CASE WHEN COUNT(lr.operation_id) FILTER (
            WHERE lr.raw_operation = 'CLOSE_TRADE'
              AND lr.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0
            THEN NULL::numeric
            ELSE SUM(lr.amount * lr.fx_rate_to_base) FILTER (
                WHERE lr.raw_operation = 'CLOSE_TRADE')
        END AS ledger_close_result_base,
        CASE WHEN COUNT(lr.operation_id) FILTER (
            WHERE lr.raw_operation = 'STOCK_PURCHASE'
              AND lr.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0
            THEN NULL::numeric
            ELSE -SUM(lr.amount * lr.fx_rate_to_base) FILTER (
                WHERE lr.raw_operation = 'STOCK_PURCHASE')
        END AS ledger_purchase_cash_base,
        COUNT(lr.operation_id) FILTER (
            WHERE lr.conversion_status NOT IN ('OK', 'SAME_CURRENCY'))::bigint
            AS ledger_missing_fx_count
    FROM ledger_rows lr
    GROUP BY lr.account_id, lr.asset_id, lr.valuation_date
), previous_dates AS (
    SELECT
        target.account_id,
        target.asset_id,
        target.valuation_date,
        MAX(ad.snapshot_date) AS previous_valuation_date
    FROM closed_group target
    LEFT JOIN investory.account_daily ad
      ON ad.account_id = target.account_id
     AND ad.snapshot_date < target.valuation_date
    GROUP BY target.account_id, target.asset_id, target.valuation_date
), symbol_values AS (
    SELECT
        target.account_id,
        target.asset_id,
        target.valuation_date,
        pd.previous_valuation_date,
        COALESCE(previous_quantity.open_quantity, 0) AS previous_open_quantity,
        CASE
            WHEN COALESCE(previous_quantity.open_quantity, 0) = 0 THEN 0::numeric
            WHEN previous_price.close_price IS NULL
              OR previous_fx.conversion_status NOT IN ('OK', 'SAME_CURRENCY') THEN NULL::numeric
            ELSE previous_quantity.open_quantity
                * previous_price.close_price
                * COALESCE(previous_price.price_scale_factor, 1)
                * CASE WHEN previous_price.quality_class LIKE '%PERCENT_OF_PAR%'
                    THEN 0.01::numeric ELSE 1::numeric END
                * previous_fx.fx_rate_to_base
        END AS previous_symbol_market_value_base,
        CASE
            WHEN COALESCE(previous_quantity.open_quantity, 0) = 0 THEN 'PASS'
            WHEN previous_price.close_price IS NULL
              OR previous_fx.conversion_status NOT IN ('OK', 'SAME_CURRENCY') THEN 'FAIL'
            ELSE 'PASS'
        END::varchar(16) AS previous_reconstruction_status,
        COALESCE(current_quantity.open_quantity, 0) AS current_open_quantity,
        CASE
            WHEN COALESCE(current_quantity.open_quantity, 0) = 0 THEN 0::numeric
            WHEN current_price.close_price IS NULL
              OR current_fx.conversion_status NOT IN ('OK', 'SAME_CURRENCY') THEN NULL::numeric
            ELSE current_quantity.open_quantity
                * current_price.close_price
                * COALESCE(current_price.price_scale_factor, 1)
                * CASE WHEN current_price.quality_class LIKE '%PERCENT_OF_PAR%'
                    THEN 0.01::numeric ELSE 1::numeric END
                * current_fx.fx_rate_to_base
        END AS current_symbol_market_value_base,
        CASE
            WHEN COALESCE(current_quantity.open_quantity, 0) = 0 THEN 'PASS'
            WHEN current_price.close_price IS NULL
              OR current_fx.conversion_status NOT IN ('OK', 'SAME_CURRENCY') THEN 'FAIL'
            ELSE 'PASS'
        END::varchar(16) AS current_reconstruction_status
    FROM closed_group target
    LEFT JOIN previous_dates pd
      ON pd.account_id = target.account_id
     AND pd.asset_id = target.asset_id
     AND pd.valuation_date = target.valuation_date
    LEFT JOIN LATERAL (
        SELECT SUM(investory.signed_position_quantity(p.operation, p.volume)) AS open_quantity
        FROM investory.positions p
        WHERE p.account_id = target.account_id
          AND p.asset_id = target.asset_id
          AND p.settlement_model = 'CASH_SETTLED'
          AND p.open_time::date <= pd.previous_valuation_date
          AND (p.close_time IS NULL OR pd.previous_valuation_date < p.close_time::date)
    ) previous_quantity ON true
    LEFT JOIN LATERAL (
        SELECT aph.close_price, aph.price_scale_factor, aph.price_currency, aph.quality_class
        FROM investory.v_canonical_asset_daily_price aph
        WHERE aph.asset_id = target.asset_id
          AND aph.price_date <= pd.previous_valuation_date
        ORDER BY
            aph.price_date DESC,
            CASE
                WHEN aph.quality_class = 'EXACT_LISTING_MARKET_CLOSE' THEN 1
                WHEN aph.quality_class = 'EXACT_LISTING_SCALED' THEN 2
                WHEN aph.quality_class LIKE '%ALTERNATE%' OR aph.is_proxy THEN 3
                WHEN aph.price_origin = 'MANUAL' THEN 4
                WHEN aph.estimated OR aph.quality_class LIKE 'INTERPOLATED%' THEN 5
                WHEN aph.quality_class LIKE '%TRADE_OBSERVATION%' OR aph.price_origin LIKE '%TRADE%' THEN 6
                WHEN aph.quality_class LIKE '%STALE_CARRY_FORWARD%' THEN 7
                ELSE 9
            END,
            aph.quality_score DESC,
            CASE aph.price_origin
                WHEN 'XTB_TRADE_OPEN' THEN 0
                WHEN 'XTB_TRADE_CLOSE' THEN 2
                ELSE 1
            END,
            aph.imported_at DESC
        LIMIT 1
    ) previous_price ON true
    LEFT JOIN investory.v_portfolio_daily_fx_rate previous_fx
      ON previous_fx.portfolio_id = target.portfolio_id
     AND previous_fx.valuation_date = pd.previous_valuation_date
     AND previous_fx.source_currency = previous_price.price_currency::varchar(3)
    LEFT JOIN LATERAL (
        SELECT SUM(investory.signed_position_quantity(p.operation, p.volume)) AS open_quantity
        FROM investory.positions p
        WHERE p.account_id = target.account_id
          AND p.asset_id = target.asset_id
          AND p.settlement_model = 'CASH_SETTLED'
          AND p.open_time::date <= target.valuation_date
          AND (p.close_time IS NULL OR target.valuation_date < p.close_time::date)
    ) current_quantity ON true
    LEFT JOIN LATERAL (
        SELECT aph.close_price, aph.price_scale_factor, aph.price_currency, aph.quality_class
        FROM investory.v_canonical_asset_daily_price aph
        WHERE aph.asset_id = target.asset_id
          AND aph.price_date <= target.valuation_date
        ORDER BY
            aph.price_date DESC,
            CASE
                WHEN aph.quality_class = 'EXACT_LISTING_MARKET_CLOSE' THEN 1
                WHEN aph.quality_class = 'EXACT_LISTING_SCALED' THEN 2
                WHEN aph.quality_class LIKE '%ALTERNATE%' OR aph.is_proxy THEN 3
                WHEN aph.price_origin = 'MANUAL' THEN 4
                WHEN aph.estimated OR aph.quality_class LIKE 'INTERPOLATED%' THEN 5
                WHEN aph.quality_class LIKE '%TRADE_OBSERVATION%' OR aph.price_origin LIKE '%TRADE%' THEN 6
                WHEN aph.quality_class LIKE '%STALE_CARRY_FORWARD%' THEN 7
                ELSE 9
            END,
            aph.quality_score DESC,
            CASE aph.price_origin
                WHEN 'XTB_TRADE_OPEN' THEN 0
                WHEN 'XTB_TRADE_CLOSE' THEN 2
                ELSE 1
            END,
            aph.imported_at DESC
        LIMIT 1
    ) current_price ON true
    LEFT JOIN investory.v_portfolio_daily_fx_rate current_fx
      ON current_fx.portfolio_id = target.portfolio_id
     AND current_fx.valuation_date = target.valuation_date
     AND current_fx.source_currency = current_price.price_currency::varchar(3)
), account_daily_with_previous AS (
    SELECT
        ad.*,
        LAG(ad.cash_balance) OVER (
            PARTITION BY ad.account_id ORDER BY ad.snapshot_date) AS previous_cash_balance,
        LAG(ad.market_value) OVER (
            PARTITION BY ad.account_id ORDER BY ad.snapshot_date) AS previous_market_value,
        LAG(ad.equity) OVER (
            PARTITION BY ad.account_id ORDER BY ad.snapshot_date) AS previous_equity
    FROM investory.account_daily ad
), combined AS (
    SELECT
        cg.*,
        portfolio.base_currency::varchar(3) AS base_currency,
        COALESCE(og.opened_lot_count, 0) AS opened_lot_count,
        COALESCE(og.opened_quantity, 0) AS opened_quantity,
        COALESCE(og.cash_settled_opened_quantity, 0) AS cash_settled_opened_quantity,
        og.position_open_notional_base,
        COALESCE(og.open_missing_fx_count, 0) AS open_missing_fx_count,
        COALESCE(lg.ledger_sale_row_count, 0) AS ledger_sale_row_count,
        COALESCE(lg.ledger_close_result_row_count, 0) AS ledger_close_result_row_count,
        lg.ledger_sale_cash_base,
        lg.ledger_close_result_base,
        lg.ledger_purchase_cash_base,
        COALESCE(lg.ledger_missing_fx_count, 0) AS ledger_missing_fx_count,
        sv.previous_valuation_date,
        sv.previous_open_quantity,
        sv.previous_symbol_market_value_base,
        sv.previous_reconstruction_status,
        COALESCE(sv.current_open_quantity, 0) AS current_open_quantity,
        COALESCE(sv.current_symbol_market_value_base, 0) AS current_symbol_market_value_base,
        COALESCE(sv.current_reconstruction_status, 'PASS') AS current_reconstruction_status,
        ad.previous_cash_balance,
        ad.cash_balance,
        ad.previous_market_value,
        ad.market_value,
        ad.previous_equity,
        ad.equity,
        ad.daily_profit_amount,
        CASE
            WHEN cg.result_only_lot_count > 0 AND cg.cash_settled_lot_count > 0 THEN 'MIXED'
            WHEN cg.result_only_lot_count > 0 THEN 'RESULT_ONLY'
            WHEN cg.unclassified_lot_count > 0 THEN 'UNCLASSIFIED'
            WHEN COALESCE(lg.ledger_sale_row_count, 0) > 0 THEN 'CASH_SETTLED'
            WHEN COALESCE(og.opened_quantity, 0) > 0
             AND ABS(COALESCE(og.position_open_notional_base, 0)
                     - COALESCE(cg.position_close_notional_base, 0))
                 <= investory.reconciliation_parameter('reconciliation_reorganization_relative_threshold')
                    * GREATEST(1, ABS(COALESCE(cg.position_close_notional_base, 0)))
                THEN 'REORGANIZATION'
            ELSE 'UNCLASSIFIED'
        END::varchar(32) AS settlement_model
    FROM closed_group cg
    JOIN investory.portfolios portfolio ON portfolio.id = cg.portfolio_id
    LEFT JOIN opened_group og
      ON og.account_id = cg.account_id
     AND og.asset_id = cg.asset_id
     AND og.valuation_date = cg.valuation_date
    LEFT JOIN ledger_group lg
      ON lg.account_id = cg.account_id
     AND lg.asset_id = cg.asset_id
     AND lg.valuation_date = cg.valuation_date
    LEFT JOIN symbol_values sv
      ON sv.account_id = cg.account_id
     AND sv.asset_id = cg.asset_id
     AND sv.valuation_date = cg.valuation_date
    LEFT JOIN account_daily_with_previous ad
      ON ad.account_id = cg.account_id
     AND ad.snapshot_date = cg.valuation_date
), quantities AS (
    SELECT
        c.*,
        LEAST(
            COALESCE(c.cash_settled_closed_quantity, 0),
            ABS(COALESCE(c.previous_open_quantity, 0)))
            AS carried_close_quantity,
        LEAST(
            GREATEST(
                COALESCE(c.cash_settled_closed_quantity, 0)
                    - ABS(COALESCE(c.previous_open_quantity, 0)),
                0::numeric),
            COALESCE(c.cash_settled_opened_quantity, 0))
            AS same_day_round_trip_quantity
    FROM combined c
), metrics AS (
    SELECT
        q.*,
        GREATEST(
            COALESCE(q.cash_settled_closed_quantity, 0)
                - q.carried_close_quantity
                - q.same_day_round_trip_quantity,
            0::numeric) AS unmatched_close_quantity,
        CASE
            WHEN q.carried_close_quantity = 0 THEN 0::numeric
            WHEN q.previous_symbol_market_value_base IS NULL
              OR ABS(COALESCE(q.previous_open_quantity, 0)) = 0 THEN NULL::numeric
            ELSE q.previous_symbol_market_value_base
                * q.carried_close_quantity / ABS(q.previous_open_quantity)
        END AS allocated_previous_market_value_base,
        CASE
            WHEN q.carried_close_quantity = 0 THEN 0::numeric
            WHEN q.ledger_sale_cash_base IS NULL
              OR COALESCE(q.cash_settled_closed_quantity, 0) = 0 THEN NULL::numeric
            ELSE q.ledger_sale_cash_base
                * q.carried_close_quantity / q.cash_settled_closed_quantity
        END AS carried_sale_cash_base,
        CASE
            WHEN q.same_day_round_trip_quantity = 0 THEN 0::numeric
            WHEN q.ledger_sale_cash_base IS NULL
              OR COALESCE(q.cash_settled_closed_quantity, 0) = 0 THEN NULL::numeric
            ELSE q.ledger_sale_cash_base
                * q.same_day_round_trip_quantity / q.cash_settled_closed_quantity
        END AS same_day_sale_cash_base,
        CASE
            WHEN q.same_day_round_trip_quantity = 0 THEN 0::numeric
            WHEN q.position_open_notional_base IS NULL
              OR q.cash_settled_opened_quantity = 0 THEN NULL::numeric
            ELSE q.position_open_notional_base
                * q.same_day_round_trip_quantity / q.cash_settled_opened_quantity
        END AS same_day_closed_open_notional_base,
        q.current_symbol_market_value_base - COALESCE(q.previous_symbol_market_value_base, 0)
            AS symbol_market_value_delta_base,
        COALESCE(q.cash_balance, 0) - COALESCE(q.previous_cash_balance, 0)
            AS account_cash_delta_base,
        COALESCE(q.market_value, 0) - COALESCE(q.previous_market_value, 0)
            AS account_market_value_delta_base,
        COALESCE(q.equity, 0) - COALESCE(q.previous_equity, 0)
            AS account_equity_delta_base,
        q.close_missing_fx_count + q.open_missing_fx_count + q.ledger_missing_fx_count
            AS missing_fx_count
    FROM quantities q
), reconciled AS (
    SELECT
        m.*,
        CASE
            WHEN m.ledger_sale_cash_base IS NULL OR m.position_close_notional_base IS NULL
                THEN NULL::numeric
            ELSE m.ledger_sale_cash_base - m.position_close_notional_base
        END AS settlement_cash_difference_base,
        CASE
            WHEN m.carried_sale_cash_base IS NULL
              OR m.allocated_previous_market_value_base IS NULL THEN NULL::numeric
            ELSE m.carried_sale_cash_base - m.allocated_previous_market_value_base
        END AS sale_vs_previous_market_value_difference_base,
        CASE
            WHEN m.allocated_previous_market_value_base IS NULL
              OR m.position_open_notional_base IS NULL
              OR m.same_day_closed_open_notional_base IS NULL THEN NULL::numeric
            ELSE m.symbol_market_value_delta_base
                - ((m.position_open_notional_base - m.same_day_closed_open_notional_base)
                   - m.allocated_previous_market_value_base)
        END AS symbol_market_bridge_difference_base,
        CASE
            WHEN m.position_close_result_base IS NULL
              OR m.ledger_close_result_base IS NULL THEN NULL::numeric
            ELSE m.ledger_close_result_base - m.position_close_result_base
        END AS result_settlement_difference_base,
        CASE
            WHEN m.settlement_model = 'RESULT_ONLY' THEN
                m.missing_fx_count = 0
                AND m.position_close_result_base IS NOT NULL
                AND m.ledger_close_result_base IS NOT NULL
            WHEN m.settlement_model = 'MIXED' THEN
                m.missing_fx_count = 0
                AND m.previous_valuation_date IS NOT NULL
                AND COALESCE(m.previous_reconstruction_status, 'FAIL') <> 'FAIL'
                AND m.current_reconstruction_status <> 'FAIL'
                AND m.position_close_result_base IS NOT NULL
                AND m.ledger_close_result_base IS NOT NULL
            ELSE
                m.missing_fx_count = 0
                AND m.previous_valuation_date IS NOT NULL
                AND COALESCE(m.previous_reconstruction_status, 'FAIL') <> 'FAIL'
                AND m.current_reconstruction_status <> 'FAIL'
        END AS is_complete
    FROM metrics m
)
SELECT
    r.portfolio_id,
    r.account_id,
    r.asset_id,
    r.symbol,
    r.valuation_date,
    r.previous_valuation_date,
    r.base_currency,
    r.settlement_model,
    r.closed_lot_count,
    r.opened_lot_count,
    r.previous_open_quantity,
    r.closed_quantity,
    r.cash_settled_closed_quantity,
    r.opened_quantity,
    r.cash_settled_opened_quantity,
    r.carried_close_quantity,
    r.same_day_round_trip_quantity,
    r.unmatched_close_quantity,
    r.current_open_quantity,
    r.position_close_notional_native,
    r.position_close_notional_base,
    r.position_close_result_base,
    r.position_open_notional_base,
    r.ledger_sale_cash_base,
    r.carried_sale_cash_base,
    r.same_day_sale_cash_base,
    r.same_day_closed_open_notional_base,
    r.ledger_purchase_cash_base,
    r.ledger_close_result_base,
    r.previous_symbol_market_value_base,
    r.allocated_previous_market_value_base,
    r.current_symbol_market_value_base,
    r.symbol_market_value_delta_base,
    r.account_cash_delta_base,
    r.account_market_value_delta_base,
    r.account_equity_delta_base,
    r.daily_profit_amount AS reported_daily_profit_base,
    investory.reconciliation_display_value(r.settlement_cash_difference_base) AS settlement_cash_difference_base,
    investory.reconciliation_display_value(r.result_settlement_difference_base) AS result_settlement_difference_base,
    investory.reconciliation_display_value(r.sale_vs_previous_market_value_difference_base) AS sale_vs_previous_market_value_difference_base,
    investory.reconciliation_display_value(r.symbol_market_bridge_difference_base) AS symbol_market_bridge_difference_base,
    investory.reconciliation_display_value(
        investory.reconciliation_effective_tolerance(
            r.position_close_result_base,
            r.ledger_close_result_base
        )
    ) AS result_effective_tolerance,
    investory.reconciliation_display_value(GREATEST(
        investory.reconciliation_parameter('reconciliation_trade_cash_absolute_tolerance'),
        investory.reconciliation_parameter('reconciliation_trade_cash_relative_tolerance')
            * ABS(COALESCE(r.position_close_notional_base, 0))
    )) AS settlement_cash_effective_tolerance,
    investory.reconciliation_display_value(GREATEST(
        investory.reconciliation_parameter('reconciliation_carrying_value_absolute_threshold'),
        investory.reconciliation_parameter('reconciliation_carrying_value_relative_threshold')
            * ABS(COALESCE(r.allocated_previous_market_value_base, 0))
    )) AS carrying_value_effective_threshold,
    investory.reconciliation_display_value(GREATEST(
        investory.reconciliation_parameter('reconciliation_market_bridge_absolute_threshold'),
        investory.reconciliation_parameter('reconciliation_market_bridge_relative_threshold')
            * ABS(COALESCE(r.previous_symbol_market_value_base, 0))
    )) AS market_bridge_effective_threshold,
    investory.reconciliation_display_value(GREATEST(
        investory.reconciliation_parameter('reconciliation_reorganization_absolute_threshold'),
        investory.reconciliation_parameter('reconciliation_reorganization_relative_threshold')
            * ABS(COALESCE(r.previous_symbol_market_value_base, 0))
    )) AS reorganization_effective_threshold,
    r.missing_fx_count,
    r.is_complete,
    CASE
        WHEN NOT r.is_complete THEN 'INCOMPLETE'
        WHEN r.settlement_model = 'RESULT_ONLY'
         AND investory.reconciliation_values_match(
             r.position_close_result_base,
             r.ledger_close_result_base
         )
            THEN 'PASS'
        WHEN r.settlement_model = 'REORGANIZATION'
         AND ABS(r.symbol_market_value_delta_base)
             <= GREATEST(
                 investory.reconciliation_parameter('reconciliation_reorganization_absolute_threshold'),
                 investory.reconciliation_parameter('reconciliation_reorganization_relative_threshold')
                     * ABS(COALESCE(r.previous_symbol_market_value_base, 0))
             )
            THEN 'PASS'
        WHEN r.settlement_model = 'CASH_SETTLED'
         AND ABS(COALESCE(r.settlement_cash_difference_base, 0))
             <= GREATEST(
                 investory.reconciliation_parameter('reconciliation_trade_cash_absolute_tolerance'),
                 investory.reconciliation_parameter('reconciliation_trade_cash_relative_tolerance')
                     * ABS(COALESCE(r.position_close_notional_base, 0))
             )
         AND ABS(COALESCE(r.sale_vs_previous_market_value_difference_base, 0))
             <= GREATEST(
                 investory.reconciliation_parameter('reconciliation_carrying_value_absolute_threshold'),
                 investory.reconciliation_parameter('reconciliation_carrying_value_relative_threshold')
                     * ABS(COALESCE(r.allocated_previous_market_value_base, 0))
             )
         AND ABS(COALESCE(r.symbol_market_bridge_difference_base, 0))
             <= GREATEST(
                 investory.reconciliation_parameter('reconciliation_market_bridge_absolute_threshold'),
                 investory.reconciliation_parameter('reconciliation_market_bridge_relative_threshold')
                     * ABS(COALESCE(r.previous_symbol_market_value_base, 0))
             )
            THEN 'PASS'
        ELSE 'REVIEW'
    END::varchar(16) AS reconciliation_status,
    CASE
        WHEN NOT r.is_complete AND r.missing_fx_count > 0 THEN 'MISSING_FX'
        WHEN NOT r.is_complete AND r.previous_valuation_date IS NULL THEN 'MISSING_PREVIOUS_VALUATION'
        WHEN NOT r.is_complete THEN 'VALUATION_RECONSTRUCTION_FAILED'
        WHEN r.settlement_model = 'RESULT_ONLY'
         AND NOT investory.reconciliation_values_match(
             r.position_close_result_base,
             r.ledger_close_result_base
         )
            THEN 'RESULT_ONLY_CASH_MISMATCH'
        WHEN r.settlement_model = 'RESULT_ONLY' THEN 'OK'
        WHEN r.settlement_model = 'MIXED' THEN 'MIXED_SETTLEMENT_MODEL'
        WHEN r.settlement_model = 'UNCLASSIFIED' THEN 'UNCLASSIFIED_SETTLEMENT_MODEL'
        WHEN r.settlement_model = 'REORGANIZATION'
         AND ABS(r.symbol_market_value_delta_base)
             > GREATEST(
                 investory.reconciliation_parameter('reconciliation_reorganization_absolute_threshold'),
                 investory.reconciliation_parameter('reconciliation_reorganization_relative_threshold')
                     * ABS(COALESCE(r.previous_symbol_market_value_base, 0))
             )
            THEN 'REORGANIZATION_VALUE_JUMP'
        WHEN r.settlement_model = 'REORGANIZATION' THEN 'OK'
        WHEN r.unmatched_close_quantity > investory.reconciliation_parameter('reconciliation_quantity_tolerance')
            THEN 'UNMATCHED_CLOSE_QUANTITY'
        WHEN ABS(COALESCE(r.settlement_cash_difference_base, 0))
             > GREATEST(
                 investory.reconciliation_parameter('reconciliation_trade_cash_absolute_tolerance'),
                 investory.reconciliation_parameter('reconciliation_trade_cash_relative_tolerance')
                     * ABS(COALESCE(r.position_close_notional_base, 0))
             )
            THEN 'SALE_CASH_MISMATCH'
        WHEN ABS(COALESCE(r.sale_vs_previous_market_value_difference_base, 0))
             > GREATEST(
                 investory.reconciliation_parameter('reconciliation_carrying_value_absolute_threshold'),
                 investory.reconciliation_parameter('reconciliation_carrying_value_relative_threshold')
                     * ABS(COALESCE(r.allocated_previous_market_value_base, 0))
             )
            THEN 'SALE_VS_CARRYING_VALUE_OUTLIER'
        WHEN ABS(COALESCE(r.symbol_market_bridge_difference_base, 0))
             > GREATEST(
                 investory.reconciliation_parameter('reconciliation_market_bridge_absolute_threshold'),
                 investory.reconciliation_parameter('reconciliation_market_bridge_relative_threshold')
                     * ABS(COALESCE(r.previous_symbol_market_value_base, 0))
             )
            THEN 'MARKET_VALUE_BRIDGE_OUTLIER'
        ELSE 'OK'
    END::varchar(64) AS anomaly_code
FROM reconciled r
WITH NO DATA;

CREATE UNIQUE INDEX uq_reporting_trade_settlement_reconciliation
    ON investory.reporting_trade_settlement_reconciliation
        (account_id, asset_id, valuation_date);

CREATE INDEX idx_reporting_trade_settlement_reconciliation_status
    ON investory.reporting_trade_settlement_reconciliation
        (reconciliation_status, anomaly_code, valuation_date DESC);

COMMENT ON MATERIALIZED VIEW investory.reporting_trade_settlement_reconciliation IS
    'Grouped account/date/symbol reconciliation of position closes, canonical sale cash, prior carrying value, same-day opens, and market-value movement. Cash-settled sales, result-only contracts, and reorganizations are classified separately; authoritative comparisons fail closed when FX or valuation inputs are unavailable.';

CREATE VIEW investory.reporting_trade_settlement_reconciliation_by_account AS
SELECT
    r.portfolio_id,
    r.account_id,
    account.name AS account_name,
    account.provider,
    account.currency AS account_currency,
    r.base_currency,
    r.settlement_model,
    r.anomaly_code,
    COUNT(*)::bigint AS reconciliation_row_count,
    COUNT(DISTINCT r.asset_id)::bigint AS asset_count,
    COUNT(DISTINCT r.symbol)::bigint AS symbol_count,
    MIN(r.valuation_date) AS first_valuation_date,
    MAX(r.valuation_date) AS last_valuation_date,
    CASE WHEN COUNT(*) FILTER (WHERE r.position_close_notional_base IS NULL) > 0
        THEN NULL::numeric ELSE SUM(r.position_close_notional_base) END
        AS position_close_notional_base,
    SUM(r.position_close_notional_base) AS position_close_notional_converted_subtotal_base,
    CASE WHEN COUNT(*) FILTER (
        WHERE r.settlement_model = 'RESULT_ONLY' AND r.ledger_close_result_base IS NULL) > 0
        THEN NULL::numeric ELSE SUM(r.ledger_close_result_base) END
        AS ledger_close_result_base,
    SUM(r.ledger_close_result_base) AS ledger_close_result_converted_subtotal_base,
    SUM(r.missing_fx_count)::bigint AS missing_fx_count,
    BOOL_AND(r.is_complete) AS is_complete
FROM investory.reporting_trade_settlement_reconciliation r
JOIN investory.accounts account ON account.id = r.account_id
GROUP BY
    r.portfolio_id,
    r.account_id,
    account.name,
    account.provider,
    account.currency,
    r.base_currency,
    r.settlement_model,
    r.anomaly_code;

COMMENT ON VIEW investory.reporting_trade_settlement_reconciliation_by_account IS
    'Account grouping over trade settlement reconciliation. Authoritative sums become NULL when any required converted value is unavailable; converted subtotals remain diagnostic only.';

CREATE OR REPLACE VIEW investory.v_reporting_validation_summary AS
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
        COUNT(*) FILTER (WHERE rpd.fx_conversion_status NOT IN ('OK', 'SAME_CURRENCY')) AS missing_fx_rates,
        COUNT(*) FILTER (WHERE rpd.contract_multiplier IS NULL) AS missing_multipliers,
        COUNT(*) FILTER (WHERE rpd.open_quantity = 0 AND COALESCE(rpd.reconstructed_market_value_base, 0) <> 0) AS residual_positions
    FROM investory.v_reconstructed_position_daily rpd
    GROUP BY rpd.valuation_date, rpd.account_id
),
validation_counts AS (
    SELECT
        vpv.valuation_date,
        vpv.account_id,
        COUNT(*) FILTER (WHERE vpv.validation_code IN ('PRICE_RATIO_100X')) AS price_anomalies
    FROM investory.v_position_valuation_validation vpv
    GROUP BY vpv.valuation_date, vpv.account_id
),
recon AS (
    SELECT
        adr.valuation_date,
        adr.account_id,
        COUNT(*) FILTER (WHERE adr.status = 'FAIL') AS reconciliation_failures,
        MAX(ABS(adr.market_value_difference)) AS maximum_market_value_difference,
        MAX(ABS(adr.equity_difference)) AS maximum_equity_difference,
        MAX(adr.status) AS status_hint
    FROM investory.v_account_daily_reconciliation adr
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

COMMENT ON VIEW investory.v_reporting_validation_summary IS
    'High-level PASS/WARN/FAIL reporting summary by valuation date and account, built from independent reconstruction and validation views.';

CREATE OR REPLACE FUNCTION investory.repair_position_trade_currency()
RETURNS integer
LANGUAGE plpgsql
AS $$
DECLARE
    repaired_count integer;
BEGIN
    -- Baseline imports must provide explicit currency metadata. No inference or repair is allowed.
    repaired_count := 0;
    RETURN repaired_count;
END;
$$;

SELECT investory.repair_position_trade_currency();


DROP MATERIALIZED VIEW IF EXISTS investory.portfolio_asset_allocation;
CREATE MATERIALIZED VIEW investory.portfolio_asset_allocation AS
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
FROM investory.v_open_position_values v
GROUP BY v.portfolio_id, v.base_currency, v.asset_id, v.asset_symbol
WITH DATA;

CREATE UNIQUE INDEX ux_mv_portfolio_asset_allocation_key
    ON investory.portfolio_asset_allocation(portfolio_id, asset_id);

DROP MATERIALIZED VIEW IF EXISTS investory.symbol_performance;
CREATE MATERIALIZED VIEW investory.symbol_performance AS
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
    FROM investory.v_open_position_values v
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
                WHEN fx.conversion_status IN ('OK', 'SAME_CURRENCY')
                    THEN cpr.amount_native * fx.fx_rate_to_target
                ELSE NULL::numeric
            END
        ) AS closed_profit,
        COUNT(*) FILTER (WHERE fx.conversion_status NOT IN ('OK', 'SAME_CURRENCY'))::bigint AS missing_fx_count,
        COUNT(*) FILTER (WHERE fx.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) = 0 AS is_complete
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
        COUNT(*) FILTER (WHERE nco.portfolio_conversion_status NOT IN ('OK', 'SAME_CURRENCY'))::bigint AS missing_fx_count,
        COUNT(*) FILTER (WHERE nco.portfolio_conversion_status NOT IN ('OK', 'SAME_CURRENCY')) = 0 AS is_complete
    FROM investory.normalized_cash_operations nco
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

CREATE UNIQUE INDEX ux_mv_symbol_performance_key
    ON investory.symbol_performance(portfolio_id, asset_id);

CREATE OR REPLACE VIEW investory.v_portfolio_service_fallback_reconciliation AS
WITH position_components AS (
    SELECT
        pf.id AS portfolio_id,
        CASE WHEN pos.close_time IS NULL THEN 'UNREALIZED' ELSE 'REALIZED' END::varchar(16) AS metric_type,
        COALESCE(pos.close_time::date, CURRENT_DATE) AS valuation_date,
        pos.profit_currency::varchar(3) AS source_currency,
        pf.base_currency::varchar(3) AS base_currency,
        COALESCE(pos.profit, 0) + COALESCE(pos.swap, 0) AS amount_native
    FROM investory.positions pos
    JOIN investory.accounts acc ON acc.id = pos.account_id
    JOIN investory.portfolios pf ON pf.id = acc.portfolio_id
    JOIN investory.assets asset ON asset.id = pos.asset_id
                               AND asset.exclude_from_import = false
    UNION ALL
    SELECT
        pf.id AS portfolio_id,
        CASE WHEN pos.close_time IS NULL THEN 'UNREALIZED' ELSE 'REALIZED' END::varchar(16) AS metric_type,
        COALESCE(pos.close_time::date, CURRENT_DATE) AS valuation_date,
        pos.commission_currency::varchar(3) AS source_currency,
        pf.base_currency::varchar(3) AS base_currency,
        COALESCE(pos.commission, 0) AS amount_native
    FROM investory.positions pos
    JOIN investory.accounts acc ON acc.id = pos.account_id
    JOIN investory.portfolios pf ON pf.id = acc.portfolio_id
    JOIN investory.assets asset ON asset.id = pos.asset_id
                               AND asset.exclude_from_import = false
), converted_position_components AS (
    SELECT pc.*, fx.fx_rate_to_target, fx.conversion_status
    FROM position_components pc
    LEFT JOIN LATERAL investory.resolve_fx_rate(
        pc.valuation_date, pc.source_currency, pc.base_currency
    ) fx ON true
), raw_position_totals AS (
    SELECT
        cpc.portfolio_id,
        CASE WHEN COUNT(*) FILTER (
            WHERE cpc.metric_type = 'REALIZED'
              AND cpc.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0
            THEN NULL::numeric
            ELSE SUM(cpc.amount_native * cpc.fx_rate_to_target) FILTER (
                WHERE cpc.metric_type = 'REALIZED') END AS fallback_realized_profit,
        CASE WHEN COUNT(*) FILTER (
            WHERE cpc.metric_type = 'UNREALIZED'
              AND cpc.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0
            THEN NULL::numeric
            ELSE SUM(cpc.amount_native * cpc.fx_rate_to_target) FILTER (
                WHERE cpc.metric_type = 'UNREALIZED') END AS fallback_unrealized_profit,
        COUNT(*) FILTER (
            WHERE cpc.conversion_status NOT IN ('OK', 'SAME_CURRENCY'))::bigint
            AS fallback_position_fx_missing_count
    FROM converted_position_components cpc
    GROUP BY cpc.portfolio_id
), raw_cash_totals AS (
    SELECT
        pr.id AS portfolio_id,
        SUM(nco.amount_in_portfolio_base_currency) FILTER (
            WHERE nco.normalized_category IN ('DIVIDEND', 'DIVIDEND_REVERSAL')) AS fallback_dividends,
        SUM(nco.amount_in_portfolio_base_currency) FILTER (
            WHERE nco.normalized_category IN ('INTEREST', 'INTEREST_REVERSAL')) AS fallback_interest,
        COUNT(*) FILTER (WHERE nco.portfolio_conversion_status NOT IN ('OK', 'SAME_CURRENCY'))::bigint AS fallback_cash_fx_missing_count,
        COUNT(*) FILTER (WHERE nco.portfolio_conversion_status NOT IN ('OK', 'SAME_CURRENCY')) = 0 AS fallback_cash_is_complete
    FROM investory.normalized_cash_operations nco
    JOIN investory.accounts acc ON acc.id = nco.account_id
    JOIN investory.portfolios pr ON pr.id = acc.portfolio_id
    GROUP BY pr.id
)
SELECT
    pk.portfolio_id,
    pk.base_currency,
    pk.total_realized_profit AS canonical_realized_profit,
    CASE WHEN rp.portfolio_id IS NULL THEN 0 ELSE rp.fallback_realized_profit END AS fallback_realized_profit,
    pk.total_unrealized_profit AS canonical_unrealized_profit,
    CASE WHEN rp.portfolio_id IS NULL THEN 0 ELSE rp.fallback_unrealized_profit END AS fallback_unrealized_profit,
    COALESCE(rp.fallback_position_fx_missing_count, 0) AS fallback_position_fx_missing_count,
    pk.total_dividends AS canonical_dividends,
    CASE WHEN COALESCE(rc.fallback_cash_is_complete, true) THEN COALESCE(rc.fallback_dividends, 0) ELSE NULL::numeric END AS fallback_dividends,
    pk.total_interest AS canonical_interest,
    CASE WHEN COALESCE(rc.fallback_cash_is_complete, true) THEN COALESCE(rc.fallback_interest, 0) ELSE NULL::numeric END AS fallback_interest,
    (CASE WHEN rp.portfolio_id IS NULL THEN 0 ELSE rp.fallback_realized_profit END)
        - pk.total_realized_profit AS realized_profit_difference,
    (CASE WHEN rp.portfolio_id IS NULL THEN 0 ELSE rp.fallback_unrealized_profit END)
        - pk.total_unrealized_profit AS unrealized_profit_difference,
    CASE WHEN COALESCE(rc.fallback_cash_is_complete, true) THEN COALESCE(rc.fallback_dividends, 0) - pk.total_dividends ELSE NULL::numeric END AS dividends_difference,
    CASE WHEN COALESCE(rc.fallback_cash_is_complete, true) THEN COALESCE(rc.fallback_interest, 0) - pk.total_interest ELSE NULL::numeric END AS interest_difference,
    COALESCE(rp.fallback_position_fx_missing_count, 0) + COALESCE(rc.fallback_cash_fx_missing_count, 0) AS missing_fx_count,
    COALESCE(rp.fallback_position_fx_missing_count, 0) = 0 AND COALESCE(rc.fallback_cash_is_complete, true) AS is_complete,
    CASE WHEN COALESCE(rp.fallback_position_fx_missing_count, 0) = 0
           AND COALESCE(rc.fallback_cash_is_complete, true)
           AND investory.reconciliation_values_match(
               pk.total_realized_profit,
               CASE WHEN rp.portfolio_id IS NULL THEN 0 ELSE rp.fallback_realized_profit END
           )
           AND investory.reconciliation_values_match(
               pk.total_unrealized_profit,
               CASE WHEN rp.portfolio_id IS NULL THEN 0 ELSE rp.fallback_unrealized_profit END
           )
           AND investory.reconciliation_values_match(
               pk.total_dividends,
               COALESCE(rc.fallback_dividends, 0)
           )
           AND investory.reconciliation_values_match(
               pk.total_interest,
               COALESCE(rc.fallback_interest, 0)
           )
         THEN 'MATCH' ELSE 'REVIEW' END AS fallback_reconciliation_status
FROM investory.portfolio_kpi_summary pk
LEFT JOIN raw_position_totals rp ON rp.portfolio_id = pk.portfolio_id
LEFT JOIN raw_cash_totals rc ON rc.portfolio_id = pk.portfolio_id;

COMMENT ON VIEW investory.v_portfolio_service_fallback_reconciliation IS
    'Compares canonical KPI values with raw-position and normalized-cash fallback values. Values are expected in portfolio base currency; raw position fields are intentionally shown for later review.';

CREATE OR REPLACE FUNCTION investory.refresh_reporting_views()
RETURNS VOID AS $$
BEGIN
    REFRESH MATERIALIZED VIEW investory.account_monthly_mv;
    REFRESH MATERIALIZED VIEW investory.portfolio_monthly_mv;
    REFRESH MATERIALIZED VIEW investory.account_statistics;
    REFRESH MATERIALIZED VIEW investory.portfolio_currency_breakdown;
    REFRESH MATERIALIZED VIEW investory.portfolio_asset_allocation;
    REFRESH MATERIALIZED VIEW investory.symbol_performance;
    REFRESH MATERIALIZED VIEW investory.portfolio_kpi_summary;
END;
$$ LANGUAGE plpgsql;

SELECT investory.refresh_reporting_views();

-- Portfolio data quality and valuation confidence.
CREATE OR REPLACE VIEW investory.v_portfolio_data_quality AS
WITH active_accounts AS (SELECT COUNT(*)::bigint AS total_accounts FROM investory.accounts),
account_recon AS (
    SELECT COUNT(*) FILTER (WHERE s.status = 'PASS')::bigint AS reconciled_accounts
    FROM (SELECT DISTINCT ON (account_id) account_id, status
          FROM investory.v_reporting_validation_summary
          ORDER BY account_id, valuation_date DESC) s
),
latest_positions AS (
    SELECT * FROM investory.v_reconstructed_position_daily
    WHERE valuation_date = (SELECT MAX(valuation_date) FROM investory.v_reconstructed_position_daily)
),
position_quality AS (
    SELECT COUNT(*)::bigint AS total_open_positions,
           COUNT(*) FILTER (WHERE selected_price IS NOT NULL AND reconstruction_status <> 'FAIL')::bigint AS priced_open_positions,
           COUNT(*) FILTER (WHERE selected_price IS NULL)::bigint AS missing_price_count,
           COUNT(*) FILTER (WHERE price_age_days > 10)::bigint AS stale_price_count,
           COUNT(*) FILTER (WHERE selection_priority = 3 OR price_quality ILIKE '%PROXY%' OR price_quality ILIKE '%ALTERNATE%')::bigint AS proxy_price_count,
           COUNT(*) FILTER (WHERE selection_priority = 5 OR price_quality ILIKE '%INTERPOLAT%')::bigint AS estimated_price_count,
           COUNT(*) FILTER (WHERE fx_conversion_status NOT IN ('OK', 'SAME_CURRENCY'))::bigint AS missing_fx_count,
           MAX(selected_price_date) AS latest_price_date
    FROM latest_positions WHERE open_quantity <> 0
),
currency_quality AS (
    SELECT COUNT(*)::bigint AS ambiguous_cost_basis_currency_count
    FROM investory.v_position_currency_validation
    WHERE anomaly_code IN ('MIXED_OPEN_POSITION_CURRENCIES', 'MISSING_POSITION_CURRENCY')
),
cash_quality AS (
    SELECT COUNT(*)::bigint AS unclassified_cash_operation_count
    FROM investory.normalized_cash_operations WHERE normalized_category = 'UNCLASSIFIED'
),
quality AS (
    SELECT aa.total_accounts, COALESCE(ar.reconciled_accounts, 0) AS reconciled_accounts,
           GREATEST(aa.total_accounts - COALESCE(ar.reconciled_accounts, 0), 0) AS unreconciled_accounts,
           COALESCE(pq.total_open_positions, 0) AS total_open_positions,
           COALESCE(pq.priced_open_positions, 0) AS priced_open_positions,
           COALESCE(pq.missing_price_count, 0) AS missing_price_count,
           COALESCE(pq.stale_price_count, 0) AS stale_price_count,
           COALESCE(pq.proxy_price_count, 0) AS proxy_price_count,
           COALESCE(pq.estimated_price_count, 0) AS estimated_price_count,
           COALESCE(pq.missing_fx_count, 0) AS missing_fx_count,
           COALESCE(cq.ambiguous_cost_basis_currency_count, 0) AS ambiguous_cost_basis_currency_count,
           COALESCE(cq.ambiguous_cost_basis_currency_count, 0) AS excluded_position_count,
           COALESCE(cqo.unclassified_cash_operation_count, 0) AS unclassified_cash_operation_count,
           (SELECT MAX(finished_at) FROM investory.import_history WHERE status = 'COMPLETED') AS latest_broker_reconciliation_at,
           (SELECT MAX(finished_at) FROM investory.import_history WHERE status = 'COMPLETED') AS latest_import_at,
           pq.latest_price_date,
           (SELECT MAX(rate_date) FROM investory.exchange_rates) AS latest_fx_month,
           (SELECT MAX(updated_at) FROM investory.account_daily) AS latest_reporting_refresh_at
    FROM active_accounts aa CROSS JOIN account_recon ar CROSS JOIN position_quality pq CROSS JOIN currency_quality cq CROSS JOIN cash_quality cqo
)
SELECT q.*,
       CASE WHEN q.unreconciled_accounts > 0 OR q.missing_price_count > 0 OR q.missing_fx_count > 0
                  OR q.ambiguous_cost_basis_currency_count > 0 OR q.priced_open_positions < q.total_open_positions THEN 'CRITICAL'
            WHEN q.stale_price_count > 0 OR q.proxy_price_count > 0 OR q.estimated_price_count > 0
                  OR q.unclassified_cash_operation_count > 0 OR q.reconciled_accounts < q.total_accounts THEN 'REVIEW'
            ELSE 'HEALTHY' END::varchar(16) AS quality_state
FROM quality q;

COMMENT ON VIEW investory.v_portfolio_data_quality IS
    'Portfolio-level data quality and valuation coverage.';

CREATE OR REPLACE VIEW investory.v_portfolio_data_quality_issue AS
SELECT 'POSITION'::varchar(32) AS issue_type, r.account_id::varchar(64) AS account_id,
       r.asset_id::varchar(64) AS asset_id,
       CASE WHEN r.selected_price IS NULL THEN 'MISSING_PRICE'
            WHEN r.price_age_days > 10 THEN 'STALE_PRICE'
            WHEN r.fx_conversion_status NOT IN ('OK', 'SAME_CURRENCY') THEN 'MISSING_FX'
            WHEN r.selection_priority = 3 OR r.price_quality ILIKE '%PROXY%' OR r.price_quality ILIKE '%ALTERNATE%' THEN 'PROXY_PRICE'
            WHEN r.selection_priority = 5 OR r.price_quality ILIKE '%INTERPOLAT%' THEN 'ESTIMATED_PRICE' END::varchar(64) AS issue_code,
       r.price_age_days, r.selected_price_date, r.price_currency, r.price_quality, r.price_origin,
       r.reconstruction_status, NULL::bigint AS selected_price_history_id
FROM investory.v_reconstructed_position_daily r
WHERE r.valuation_date = (SELECT MAX(valuation_date) FROM investory.v_reconstructed_position_daily)
  AND r.open_quantity <> 0
  AND (r.selected_price IS NULL OR r.price_age_days > 10 OR r.fx_conversion_status NOT IN ('OK', 'SAME_CURRENCY')
       OR r.selection_priority IN (3, 5) OR r.price_quality ILIKE '%PROXY%' OR r.price_quality ILIKE '%ALTERNATE%' OR r.price_quality ILIKE '%INTERPOLAT%');

COMMENT ON VIEW investory.v_portfolio_data_quality_issue IS
    'Actionable asset-level data-quality issues with price provenance for open positions.';

CREATE OR REPLACE VIEW investory.v_portfolio_data_quality_refresh AS
SELECT (SELECT MAX(finished_at) FROM investory.import_history WHERE status = 'COMPLETED') AS broker_imported_at,
       (SELECT MAX(price_updated_at) FROM investory.assets) AS prices_updated_at,
       (SELECT MAX(imported_at) FROM investory.exchange_rates) AS fx_updated_at,
       (SELECT MAX(updated_at) FROM investory.account_daily) AS projections_rebuilt_at,
       (SELECT MAX(updated_at) FROM investory.account_daily) AS reporting_refreshed_at;

COMMENT ON VIEW investory.v_portfolio_data_quality_refresh IS
    'Separate timestamps for import, price, FX, projection, and reporting stages.';

CREATE OR REPLACE VIEW investory.reporting_unsupported_transaction_states AS
SELECT
    'cash_operations'::varchar(32) AS source_table,
    co.id AS row_id,
    co.account_id,
    co.asset_id,
    co.date AS occurred_at,
    'UNKNOWN_CASH_OPERATION'::varchar(64) AS issue_code,
    co.operation::text AS raw_state,
    co.comment AS evidence
FROM investory.cash_operations co
WHERE co.operation = 'UNKNOWN'
UNION ALL
SELECT
    'cash_operations'::varchar(32),
    nco.operation_id,
    nco.account_id,
    nco.asset_id,
    nco.date,
    'UNCLASSIFIED_CASH_OPERATION'::varchar(64),
    nco.raw_operation::text,
    nco.comment
FROM investory.normalized_cash_operations nco
WHERE nco.normalized_category = 'UNCLASSIFIED'
UNION ALL
SELECT
    'positions'::varchar(32),
    p.id,
    p.account_id,
    p.asset_id,
    p.open_time,
    'UNCLASSIFIED_SETTLEMENT_MODEL'::varchar(64),
    p.settlement_model::text,
    p.broker_product
FROM investory.positions p
WHERE p.settlement_model = 'UNCLASSIFIED';

COMMENT ON VIEW investory.reporting_unsupported_transaction_states IS
    'Required review queue for preserved but unsupported or unresolved ledger states. Empty result is expected before trusted reporting; unknown enum labels are rejected directly by PostgreSQL.';

CREATE OR REPLACE VIEW investory.account_monthly_benchmark AS
WITH portfolio_base_flows AS (
    SELECT
        nco.account_id,
        date_trunc('month', nco.date)::date AS month,
        SUM(nco.amount_in_portfolio_base_currency) AS net_external_flow
    FROM investory.normalized_cash_operations nco
    WHERE nco.normalized_category IN (
        'EXTERNAL_DEPOSIT',
        'EXTERNAL_WITHDRAWAL',
        'INTERNAL_TRANSFER_IN',
        'INTERNAL_TRANSFER_OUT',
        'INTERNAL_BOOKKEEPING',
        'FX_CONVERSION',
        'CORRECTION'
    )
    GROUP BY nco.account_id, date_trunc('month', nco.date)::date
)
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
    monthly.closing_equity
        - monthly.opening_equity
        - COALESCE(flows.net_external_flow, 0) AS total_profit,
    CASE
        WHEN monthly.opening_equity = 0 THEN NULL::numeric
        ELSE (
            monthly.closing_equity
            - monthly.opening_equity
            - COALESCE(flows.net_external_flow, 0)
        ) / monthly.opening_equity
    END AS compounded_monthly_return,
    monthly.updated_at
FROM investory.account_monthly_mv monthly
LEFT JOIN portfolio_base_flows flows
    ON flows.account_id = monthly.account_id
   AND flows.month = monthly.month;

COMMENT ON VIEW investory.account_monthly_benchmark IS
    'Benchmark-safe monthly account performance. Monthly P/L uses portfolio-base equity minus portfolio-base normalized cash flows, preventing PLN or other account-native amounts from leaking into USD benchmark returns.';

CREATE TABLE investory.materialized_view_refresh_history (
    id                bigserial PRIMARY KEY,
    refresh_run_id    uuid NOT NULL,
    materialized_view varchar(128) NOT NULL,
    dependency_level  integer NOT NULL,
    started_at        timestamptz NOT NULL,
    finished_at       timestamptz,
    status            varchar(16) NOT NULL,
    error_message     text,
    CONSTRAINT chk_mv_refresh_status
        CHECK (status IN ('STARTED', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_mv_refresh_dependency_level_non_negative
        CHECK (dependency_level >= 0),
    CONSTRAINT chk_mv_refresh_finished_after_started
        CHECK (finished_at IS NULL OR finished_at >= started_at)
);

CREATE INDEX ix_mv_refresh_history_view_finished
    ON investory.materialized_view_refresh_history(materialized_view, finished_at DESC);

COMMENT ON TABLE investory.materialized_view_refresh_history IS
    'Audit history for ordered reporting materialized-view refreshes. A row is written for every view in every refresh run.';

CREATE OR REPLACE VIEW investory.reporting_materialized_view_dependencies AS
WITH RECURSIVE materialized_views AS (
    SELECT c.oid, c.relname
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'investory'
      AND c.relkind = 'm'
), dependency_edges AS (
    SELECT DISTINCT
        dependent.oid AS dependent_oid,
        dependent.relname AS dependent_view,
        referenced.oid AS referenced_oid,
        referenced.relname AS referenced_view
    FROM pg_depend d
    JOIN pg_rewrite rw ON rw.oid = d.objid
    JOIN materialized_views dependent ON dependent.oid = rw.ev_class
    JOIN materialized_views referenced ON referenced.oid = d.refobjid
    WHERE dependent.oid <> referenced.oid
), dependency_walk AS (
    SELECT mv.oid, mv.relname, 0 AS dependency_level
    FROM materialized_views mv
    WHERE NOT EXISTS (
        SELECT 1
        FROM dependency_edges edge
        WHERE edge.dependent_oid = mv.oid
    )
    UNION ALL
    SELECT edge.dependent_oid, edge.dependent_view, walk.dependency_level + 1
    FROM dependency_walk walk
    JOIN dependency_edges edge ON edge.referenced_oid = walk.oid
), levels AS (
    SELECT mv.oid, mv.relname, COALESCE(max(walk.dependency_level), 0)::integer AS dependency_level
    FROM materialized_views mv
    LEFT JOIN dependency_walk walk ON walk.oid = mv.oid
    GROUP BY mv.oid, mv.relname
), concurrent_eligibility AS (
    SELECT
        mv.oid,
        EXISTS (
            SELECT 1
            FROM pg_index idx
            WHERE idx.indrelid = mv.oid
              AND idx.indisunique
              AND idx.indisvalid
              AND idx.indpred IS NULL
              AND idx.indexprs IS NULL
        ) AS concurrent_refresh_eligible
    FROM materialized_views mv
)
SELECT
    levels.relname::varchar(128) AS materialized_view,
    levels.dependency_level,
    eligibility.concurrent_refresh_eligible,
    array_remove(array_agg(edge.referenced_view ORDER BY edge.referenced_view), NULL)::varchar[] AS depends_on
FROM levels
JOIN concurrent_eligibility eligibility ON eligibility.oid = levels.oid
LEFT JOIN dependency_edges edge ON edge.dependent_oid = levels.oid
GROUP BY levels.relname, levels.dependency_level, eligibility.concurrent_refresh_eligible;

COMMENT ON VIEW investory.reporting_materialized_view_dependencies IS
    'Dependency order and concurrent-refresh eligibility for Investory materialized views. Concurrent eligibility requires a valid, unconditional, non-expression unique index.';

CREATE OR REPLACE VIEW investory.reporting_materialized_view_refresh_status AS
SELECT
    dependencies.materialized_view,
    dependencies.dependency_level,
    dependencies.depends_on,
    dependencies.concurrent_refresh_eligible,
    latest.refresh_run_id,
    latest.started_at AS last_refresh_started_at,
    latest.finished_at AS last_refresh_finished_at,
    latest.status AS last_refresh_status,
    latest.error_message AS last_refresh_error
FROM investory.reporting_materialized_view_dependencies dependencies
LEFT JOIN LATERAL (
    SELECT history.*
    FROM investory.materialized_view_refresh_history history
    WHERE history.materialized_view = dependencies.materialized_view
    ORDER BY history.started_at DESC, history.id DESC
    LIMIT 1
) latest ON true;

COMMENT ON VIEW investory.reporting_materialized_view_refresh_status IS
    'Current refresh status, last successful or failed refresh timestamp, dependency level, and concurrent-refresh eligibility for every reporting materialized view.';

COMMENT ON COLUMN investory.assets.asset_type IS
    'Current broad instrument classification. Sector allocation must not be introduced until a canonical sector taxonomy and an explicit asset-to-sector mapping are defined.';

-- Consolidated actionable queue for valuation inputs. Existing valuation views
-- remain fail-closed: missing or stale inputs produce NULL totals and explicit
-- status fields rather than silently dropping rows or substituting zero.
CREATE OR REPLACE VIEW investory.reporting_valuation_input_issues AS
WITH latest_position_date AS (
    SELECT max(valuation_date) AS valuation_date
    FROM investory.v_reconstructed_position_daily
), position_issues AS (
    SELECT
        CASE
            WHEN rpd.selected_price IS NULL THEN 'MISSING_PRICE'
            WHEN rpd.price_age_days > 10 THEN 'STALE_PRICE'
            WHEN rpd.fx_conversion_status IN ('MISSING', 'MISSING_CURRENCY') THEN 'MISSING_FX'
            WHEN rpd.fx_conversion_status = 'STALE' THEN 'STALE_FX'
        END::varchar(64) AS issue_code,
        CASE
            WHEN rpd.selected_price IS NULL
              OR rpd.fx_conversion_status IN ('MISSING', 'MISSING_CURRENCY') THEN 'ERROR'
            ELSE 'WARN'
        END::varchar(16) AS severity,
        account.portfolio_id,
        rpd.account_id,
        rpd.asset_id,
        rpd.valuation_date,
        rpd.price_currency::varchar(3) AS source_currency,
        portfolio.base_currency::varchar(3) AS target_currency,
        rpd.selected_price_date AS input_date,
        CASE
            WHEN rpd.selected_price IS NULL THEN NULL
            ELSE rpd.price_age_days
        END::integer AS age_days,
        concat_ws(
            '; ',
            'symbol=' || asset.symbol,
            'price_quality=' || coalesce(rpd.price_quality, 'NULL'),
            'fx_status=' || coalesce(rpd.fx_conversion_status, 'NULL')
        )::text AS details,
        CASE
            WHEN rpd.selected_price IS NULL
                THEN 'Import a canonical price on or before the valuation date, then rebuild reporting.'
            WHEN rpd.price_age_days > 10
                THEN 'Review whether the last available price is valid for this instrument and valuation date.'
            WHEN rpd.fx_conversion_status IN ('MISSING', 'MISSING_CURRENCY')
                THEN 'Import the required FX rate, then rebuild reporting.'
            ELSE 'Review the stale FX rate before accepting reporting.'
        END::varchar(255) AS required_action
    FROM investory.v_reconstructed_position_daily rpd
    JOIN investory.accounts account ON account.id = rpd.account_id
    JOIN investory.portfolios portfolio ON portfolio.id = account.portfolio_id
    JOIN investory.assets asset ON asset.id = rpd.asset_id
    CROSS JOIN latest_position_date latest
    WHERE rpd.valuation_date = latest.valuation_date
      AND rpd.open_quantity <> 0
      AND (
          rpd.selected_price IS NULL
          OR rpd.price_age_days > 10
          OR rpd.fx_conversion_status NOT IN ('OK', 'SAME_CURRENCY')
      )
), cash_fx_issues AS (
    SELECT
        CASE
            WHEN nco.portfolio_conversion_status IN ('MISSING', 'MISSING_CURRENCY') THEN 'MISSING_FX'
            ELSE 'STALE_FX'
        END::varchar(64) AS issue_code,
        CASE
            WHEN nco.portfolio_conversion_status IN ('MISSING', 'MISSING_CURRENCY') THEN 'ERROR'
            ELSE 'WARN'
        END::varchar(16) AS severity,
        nco.portfolio_id,
        nco.account_id,
        nco.asset_id,
        nco.date::date AS valuation_date,
        nco.currency::varchar(3) AS source_currency,
        nco.base_currency::varchar(3) AS target_currency,
        nco.portfolio_source_rate_date AS input_date,
        nco.portfolio_fx_age_days::integer AS age_days,
        concat_ws(
            '; ',
            'operation_id=' || nco.operation_id,
            'operation=' || nco.raw_operation,
            'category=' || nco.normalized_category,
            'fx_status=' || nco.portfolio_conversion_status
        )::text AS details,
        CASE
            WHEN nco.portfolio_conversion_status IN ('MISSING', 'MISSING_CURRENCY')
                THEN 'Import the required FX rate, then rebuild reporting.'
            ELSE 'Review the stale FX rate before accepting reporting.'
        END::varchar(255) AS required_action
    FROM investory.normalized_cash_operations nco
    WHERE nco.portfolio_conversion_status NOT IN ('OK', 'SAME_CURRENCY')
)
SELECT * FROM position_issues WHERE issue_code IS NOT NULL
UNION ALL
SELECT * FROM cash_fx_issues;

COMMENT ON VIEW investory.reporting_valuation_input_issues IS
    'Actionable queue for missing or stale prices and FX. ERROR rows are truly missing required inputs; WARN rows use an as-of input that exceeded the accepted age threshold.';

CREATE OR REPLACE VIEW investory.reporting_monthly_import_review AS
SELECT
    'UNSUPPORTED_TRANSACTION_STATE'::varchar(64) AS check_code,
    count(*)::bigint AS issue_count,
    'Review UNKNOWN or UNCLASSIFIED imported ledger states.'::varchar(255) AS required_action
FROM investory.reporting_unsupported_transaction_states
UNION ALL
SELECT
    'DUPLICATE_POSITION_LOT'::varchar(64),
    count(*)::bigint,
    'Review duplicate source lots before trusting position and P/L totals.'::varchar(255)
FROM investory.reporting_position_lot_duplicates
UNION ALL
SELECT
    'TIMEZONE_NAIVE_COLUMN'::varchar(64),
    count(*)::bigint,
    'Convert event or audit timestamps to timestamptz.'::varchar(255)
FROM investory.reporting_timezone_naive_columns
UNION ALL
SELECT
    'VALUATION_INPUT_ERROR'::varchar(64),
    count(*)::bigint,
    'Import missing prices or FX rates and rebuild reporting.'::varchar(255)
FROM investory.reporting_valuation_input_issues
WHERE severity = 'ERROR'
UNION ALL
SELECT
    'VALUATION_INPUT_WARNING'::varchar(64),
    count(*)::bigint,
    'Review stale price or FX inputs before accepting month-end reporting.'::varchar(255)
FROM investory.reporting_valuation_input_issues
WHERE severity = 'WARN';

COMMENT ON VIEW investory.reporting_monthly_import_review IS
    'Monthly import-review checklist. Error counts must be zero before reporting is accepted; warnings require review. Corporate actions remain a manual broker-statement review.';


CREATE OR REPLACE PROCEDURE investory.refresh_reporting_materialized_views(
    p_fail_on_missing_inputs boolean DEFAULT false
)
LANGUAGE plpgsql
AS $$
DECLARE
    target record;
    run_id uuid := gen_random_uuid();
    history_id bigint;
    missing_input_count bigint;
BEGIN
    IF p_fail_on_missing_inputs THEN
        SELECT count(*)
        INTO missing_input_count
        FROM investory.reporting_valuation_input_issues
        WHERE severity = 'ERROR';

        IF missing_input_count > 0 THEN
            RAISE EXCEPTION
                'Reporting refresh blocked: % missing required valuation inputs. Review investory.reporting_valuation_input_issues.',
                missing_input_count;
        END IF;
    END IF;

    FOR target IN
        SELECT materialized_view, dependency_level
        FROM investory.reporting_materialized_view_dependencies
        ORDER BY dependency_level, materialized_view
    LOOP
        INSERT INTO investory.materialized_view_refresh_history (
            refresh_run_id,
            materialized_view,
            dependency_level,
            started_at,
            status
        ) VALUES (
            run_id,
            target.materialized_view,
            target.dependency_level,
            clock_timestamp(),
            'STARTED'
        ) RETURNING id INTO history_id;

        BEGIN
            EXECUTE format('REFRESH MATERIALIZED VIEW investory.%I', target.materialized_view);

            UPDATE investory.materialized_view_refresh_history
            SET finished_at = clock_timestamp(),
                status = 'COMPLETED'
            WHERE id = history_id;
        EXCEPTION WHEN OTHERS THEN
            UPDATE investory.materialized_view_refresh_history
            SET finished_at = clock_timestamp(),
                status = 'FAILED',
                error_message = SQLERRM
            WHERE id = history_id;
            RAISE WARNING 'Materialized-view refresh stopped at %: %', target.materialized_view, SQLERRM;
            RETURN;
        END;
    END LOOP;
END;
$$;

CREATE OR REPLACE VIEW investory.reporting_reconciliation_position_issues AS
WITH reconstructed AS (
    SELECT
        account_id,
        asset_id,
        valuation_date,
        SUM(COALESCE(open_quantity, 0)) AS open_quantity,
        SUM(COALESCE(reconstructed_cost_base_base, 0)) AS reconstructed_cost_base_base,
        MAX(selected_price) AS selected_price,
        MAX(reconstructed_market_value_base) AS reconstructed_market_value_base,
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
    rpd.selected_price,
    rpd.reconstructed_market_value_base,
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

COMMENT ON PROCEDURE investory.refresh_reporting_materialized_views(boolean) IS
    'Refreshes reporting MVs in dependency order. When fail_on_missing_inputs is true, truly missing prices or FX block refresh; stale as-of inputs remain warnings.';

CREATE OR REPLACE VIEW investory.normalized_cash_operation_flows AS
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
    FROM investory.normalized_cash_operations nco
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
            WHEN normalized_category = 'INTERNAL_BOOKKEEPING'
             AND transfer_target_account = account_id
             AND amount > 0
             AND NOT EXISTS (
                 SELECT 1
                 FROM investory.accounts counterparty
                 WHERE counterparty.id = transfer_source_account
             ) THEN amount
            WHEN normalized_category = 'INTERNAL_BOOKKEEPING'
             AND transfer_source_account = account_id
             AND amount < 0
             AND NOT EXISTS (
                 SELECT 1
                 FROM investory.accounts counterparty
                 WHERE counterparty.id = transfer_target_account
             ) THEN amount
            ELSE 0::numeric
        END AS portfolio_flow_amount
    FROM parsed
)
SELECT
    effects.*,
    CASE WHEN portfolio_conversion_status IN ('OK', 'SAME_CURRENCY')
        THEN account_flow_amount * fx_rate_to_base END
        AS account_flow_amount_in_portfolio_base_currency,
    CASE WHEN account_conversion_status IN ('OK', 'SAME_CURRENCY')
        THEN account_flow_amount * fx_rate_to_account_currency END
        AS account_flow_amount_in_account_currency,
    CASE WHEN portfolio_conversion_status IN ('OK', 'SAME_CURRENCY')
        THEN performance_flow_amount * fx_rate_to_base END
        AS performance_flow_amount_in_portfolio_base_currency,
    CASE WHEN portfolio_conversion_status IN ('OK', 'SAME_CURRENCY')
        THEN portfolio_flow_amount * fx_rate_to_base END
        AS portfolio_flow_amount_in_portfolio_base_currency
FROM effects;

COMMENT ON VIEW investory.normalized_cash_operation_flows IS
    'Flow contract with separate account funding, performance-neutralizing, and portfolio-scoped values. INTERNAL_BOOKKEEPING remains cash/account-funding diagnostic data but is not performance flow; external and genuine tracked-account transfers are performance flow at account level and cancel when aggregated within a portfolio.';

CREATE OR REPLACE VIEW investory.v_portfolio_daily AS
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
        CASE WHEN conversion_status IN ('OK', 'SAME_CURRENCY') THEN cash_balance * valuation_to_base_rate END AS cash_balance,
        CASE WHEN conversion_status IN ('OK', 'SAME_CURRENCY') THEN market_value * valuation_to_base_rate END AS market_value,
        CASE WHEN conversion_status IN ('OK', 'SAME_CURRENCY') THEN equity * valuation_to_base_rate END AS equity,
        CASE WHEN conversion_status IN ('OK', 'SAME_CURRENCY') THEN dividends * valuation_to_base_rate END AS dividends,
        CASE WHEN conversion_status IN ('OK', 'SAME_CURRENCY') THEN interest * valuation_to_base_rate END AS interest,
        CASE WHEN conversion_status IN ('OK', 'SAME_CURRENCY') THEN fees * valuation_to_base_rate END AS fees,
        CASE WHEN conversion_status IN ('OK', 'SAME_CURRENCY') THEN taxes * valuation_to_base_rate END AS taxes,
        CASE WHEN conversion_status IN ('OK', 'SAME_CURRENCY') THEN realized_profit * valuation_to_base_rate END AS realized_profit
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
            WHERE portfolio_conversion_status NOT IN ('OK', 'SAME_CURRENCY')
        ) AS missing_flow_fx_count
    FROM investory.normalized_cash_operation_flows
    GROUP BY account_id, date::date
)
SELECT
    ar.portfolio_id,
    ar.snapshot_date,
    ar.base_currency,
    CASE WHEN COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0 THEN NULL ELSE SUM(ar.cash_balance) END AS cash_balance,
    CASE WHEN COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0 THEN NULL ELSE SUM(ar.market_value) END AS market_value,
    CASE WHEN COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0 THEN NULL ELSE SUM(ar.equity) END AS equity,
    CASE WHEN COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0 OR MAX(COALESCE(ef.missing_flow_fx_count, 0)) > 0 THEN NULL ELSE SUM(COALESCE(ef.deposits, 0)) END AS deposits,
    CASE WHEN COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0 OR MAX(COALESCE(ef.missing_flow_fx_count, 0)) > 0 THEN NULL ELSE SUM(COALESCE(ef.withdrawals, 0)) END AS withdrawals,
    CASE WHEN COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0 THEN NULL ELSE SUM(ar.dividends) END AS dividends,
    CASE WHEN COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0 THEN NULL ELSE SUM(ar.interest) END AS interest,
    CASE WHEN COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0 THEN NULL ELSE SUM(ar.fees) END AS fees,
    CASE WHEN COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0 THEN NULL ELSE SUM(ar.taxes) END AS taxes,
    CASE WHEN COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0 THEN NULL ELSE SUM(ar.realized_profit) END AS realized_profit,
    CASE WHEN COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0 OR MAX(COALESCE(ef.missing_flow_fx_count, 0)) > 0 THEN NULL ELSE
        SUM(ar.equity) - LAG(SUM(ar.equity)) OVER (PARTITION BY ar.portfolio_id ORDER BY ar.snapshot_date)
        - SUM(COALESCE(ef.deposits, 0)) + SUM(COALESCE(ef.withdrawals, 0)) END AS total_profit,
    SUM(ar.equity) AS converted_equity_subtotal,
    COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY'))::bigint AS missing_fx_count,
    COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) = 0
        AND MAX(COALESCE(ef.missing_flow_fx_count, 0)) = 0 AS is_complete,
    CASE
        WHEN COUNT(*) FILTER (WHERE ar.conversion_status NOT IN ('OK', 'SAME_CURRENCY')) > 0 OR MAX(COALESCE(ef.missing_flow_fx_count, 0)) > 0 THEN NULL::numeric
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

CREATE OR REPLACE VIEW investory.reporting_account_daily_performance_flow AS
WITH account_days AS (
    SELECT
        ad.account_id,
        ad.snapshot_date,
        LAG(ad.equity, 1, 0::numeric) OVER (
            PARTITION BY ad.account_id
            ORDER BY ad.snapshot_date
        ) AS previous_equity,
        ad.equity,
        ad.daily_profit_amount
    FROM investory.account_daily ad
), flows AS (
    SELECT
        nco.account_id,
        nco.date::date AS snapshot_date,
        CASE
            WHEN COUNT(*) FILTER (
                WHERE nco.portfolio_conversion_status NOT IN ('OK', 'SAME_CURRENCY')
            ) > 0 THEN NULL::numeric
            ELSE SUM(COALESCE(nco.account_flow_amount_in_portfolio_base_currency, 0))
        END AS account_funding_flow,
        CASE
            WHEN COUNT(*) FILTER (
                WHERE nco.portfolio_conversion_status NOT IN ('OK', 'SAME_CURRENCY')
            ) > 0 THEN NULL::numeric
            ELSE SUM(COALESCE(nco.performance_flow_amount_in_portfolio_base_currency, 0))
        END AS performance_flow,
        CASE
            WHEN COUNT(*) FILTER (
                WHERE nco.portfolio_conversion_status NOT IN ('OK', 'SAME_CURRENCY')
            ) > 0 THEN NULL::numeric
            ELSE SUM(COALESCE(nco.portfolio_flow_amount_in_portfolio_base_currency, 0))
        END AS portfolio_flow
    FROM investory.normalized_cash_operation_flows nco
    GROUP BY nco.account_id, nco.date::date
)
SELECT
    ad.account_id,
    ad.snapshot_date,
    ad.previous_equity,
    ad.equity,
    ad.equity - ad.previous_equity AS daily_equity_change,
    f.account_funding_flow,
    f.performance_flow,
    f.portfolio_flow,
    ad.daily_profit_amount AS reported_daily_profit,
    ad.equity - ad.previous_equity - COALESCE(f.performance_flow, 0)
        AS derived_daily_profit,
    ad.daily_profit_amount
        - (ad.equity - ad.previous_equity - COALESCE(f.performance_flow, 0))
        AS daily_profit_difference
FROM account_days ad
LEFT JOIN flows f
  ON f.account_id = ad.account_id
 AND f.snapshot_date = ad.snapshot_date;

COMMENT ON VIEW investory.reporting_account_daily_performance_flow IS
    'Explains account_daily performance using separate account funding, performance, and portfolio flow semantics. Performance flow excludes INTERNAL_BOOKKEEPING, FX_CONVERSION, and ambiguous CORRECTION rows.';

CREATE OR REPLACE VIEW investory.reporting_asset_identity_issues AS
SELECT
    'SOURCE_MAPPING_CURRENCY_MISMATCH'::varchar(64) AS issue_code,
    'ERROR'::varchar(16) AS severity,
    a.id AS asset_id,
    a.symbol AS asset_symbol,
    ass.source,
    ass.source_symbol,
    ('mapping currency ' || ass.price_currency || ' differs from asset currency ' || a.currency)::text AS details
FROM investory.asset_source_symbols ass
JOIN investory.assets a ON a.id = ass.asset_id
WHERE a.exclude_from_import = false
  AND ass.active
  AND ass.price_currency IS DISTINCT FROM a.currency
  AND ass.requires_fx_conversion = false
UNION ALL
SELECT
    'ALTERNATE_LISTING_NOT_MARKED',
    'WARN',
    a.id,
    a.symbol,
    ass.source,
    ass.source_symbol,
    ('mapping market ' || COALESCE(ass.matched_exchange, ass.source_market)
        || ' differs from asset country ' || a.country)::text
FROM investory.asset_source_symbols ass
JOIN investory.assets a ON a.id = ass.asset_id
WHERE a.exclude_from_import = false
  AND ass.active
  AND COALESCE(ass.matched_exchange, ass.source_market) IS NOT NULL
  AND upper(COALESCE(ass.matched_exchange, ass.source_market)) <> upper(a.country)
  AND ass.is_alternate_listing = false
UNION ALL
SELECT
    'TICKER_COLLISION_WITHOUT_STABLE_IDENTIFIER',
    'WARN',
    a.id,
    a.symbol,
    NULL::varchar(32),
    NULL::varchar(64),
    ('ticker ' || a.ticker || ' is shared by multiple included assets without ISIN/FIGI')::text
FROM investory.assets a
WHERE a.exclude_from_import = false
  AND a.isin IS NULL
  AND a.figi IS NULL
  AND EXISTS (
      SELECT 1
      FROM investory.assets other
      WHERE other.exclude_from_import = false
        AND other.id <> a.id
        AND upper(other.ticker) = upper(a.ticker)
        AND other.currency IS DISTINCT FROM a.currency
  );

COMMENT ON VIEW investory.reporting_asset_identity_issues IS
    'Included-asset identity diagnostics. ISIN/FIGI are optional, but supplied identifiers are format-checked; mapping currency and suspicious multi-listing/share-class collisions require review.';

INSERT INTO investory.reconciliation_parameters(parameter_name, numeric_value, description)
VALUES
    ('reconciliation_source_disagreement_ratio', 1.25, 'Price-source disagreement ratio that requires review.'),
    ('reconciliation_interpolation_deviation_ratio', 0.25, 'Interpolation deviation ratio that requires review.')
ON CONFLICT (parameter_name) DO NOTHING;

CREATE OR REPLACE VIEW investory.reporting_asset_price_quality_issues AS
WITH included_history AS (
    SELECT aph.asset_id, a.symbol AS asset_symbol, aph.price_date, aph.source,
           aph.source_symbol, aph.price_currency,
           aph.close_price * aph.price_scale_factor AS normalized_price,
           aph.estimated, aph.interpolation_left_date, aph.interpolation_right_date,
           ass.requires_fx_conversion, a.currency AS asset_currency
    FROM investory.asset_price_history aph
    JOIN investory.assets a ON a.id = aph.asset_id
    LEFT JOIN investory.asset_source_symbols ass ON ass.id = aph.source_mapping_id
    WHERE a.exclude_from_import = false
), source_disagreement AS (
    SELECT asset_id, asset_symbol, price_date, MIN(normalized_price) AS minimum_price,
           MAX(normalized_price) AS maximum_price, COUNT(*) AS observation_count
    FROM included_history
    GROUP BY asset_id, asset_symbol, price_date
    HAVING COUNT(*) > 1 AND MIN(normalized_price) > 0
       AND MAX(normalized_price) / MIN(normalized_price)
           >= investory.reconciliation_parameter('reconciliation_source_disagreement_ratio')
), canonical_prices AS (
    SELECT cp.asset_id, cp.price_date, cp.source, cp.source_symbol,
           cp.close_price * cp.price_scale_factor AS normalized_price,
           LAG(cp.close_price * cp.price_scale_factor) OVER (
               PARTITION BY cp.asset_id ORDER BY cp.price_date) AS previous_normalized_price
    FROM investory.v_canonical_asset_daily_price cp
), interpolation_checks AS (
    SELECT current.asset_id, current.asset_symbol, current.price_date, current.source,
           current.source_symbol, current.normalized_price,
           left_price.normalized_price AS left_normalized_price,
           right_price.normalized_price AS right_normalized_price,
           left_price.price_date AS left_date, right_price.price_date AS right_date
    FROM included_history current
    LEFT JOIN LATERAL (
        SELECT cp.price_date, cp.close_price * cp.price_scale_factor AS normalized_price
        FROM investory.v_canonical_asset_daily_price cp
        WHERE cp.asset_id = current.asset_id AND cp.price_date = current.interpolation_left_date
    ) left_price ON true
    LEFT JOIN LATERAL (
        SELECT cp.price_date, cp.close_price * cp.price_scale_factor AS normalized_price
        FROM investory.v_canonical_asset_daily_price cp
        WHERE cp.asset_id = current.asset_id AND cp.price_date = current.interpolation_right_date
    ) right_price ON true
    WHERE current.estimated
), interpolation_deviation AS (
    SELECT *, left_normalized_price
        + (right_normalized_price - left_normalized_price)
          * (price_date - left_date)::numeric / NULLIF(right_date - left_date, 0)
        AS expected_normalized_price
    FROM interpolation_checks
    WHERE left_normalized_price > 0 AND right_normalized_price > 0
)
SELECT 'PRICE_CURRENCY_MISMATCH'::varchar(64) AS issue_code,
       'ERROR'::varchar(16) AS severity,
       asset_id, asset_symbol, price_date, source, source_symbol,
       ('price currency ' || price_currency || ' differs from asset currency ' || asset_currency)::text
FROM included_history
WHERE price_currency IS DISTINCT FROM asset_currency
  AND COALESCE(requires_fx_conversion, false) = false
UNION ALL
SELECT 'EXTREME_SAME_DATE_SOURCE_DISAGREEMENT', 'WARN', asset_id, asset_symbol, price_date,
       NULL, NULL, ('source prices range from ' || minimum_price || ' to ' || maximum_price
       || ' across ' || observation_count || ' rows')::text
FROM source_disagreement
UNION ALL
SELECT 'SUSPICIOUS_INTERPOLATION', 'WARN', asset_id, asset_symbol, price_date,
       source, source_symbol,
       ('interpolated price ' || normalized_price || ' differs from expected ' || expected_normalized_price)::text
FROM interpolation_deviation
WHERE ABS(normalized_price - expected_normalized_price) / NULLIF(expected_normalized_price, 0)
      >= investory.reconciliation_parameter('reconciliation_interpolation_deviation_ratio')
UNION ALL
SELECT 'EXTREME_DAILY_MOVE', 'WARN', asset_id,
       (SELECT symbol FROM investory.assets a WHERE a.id = canonical_prices.asset_id),
       price_date, source, source_symbol,
       ('canonical price moved from ' || previous_normalized_price || ' to ' || normalized_price)::text
FROM canonical_prices
WHERE previous_normalized_price > 0
  AND (normalized_price / previous_normalized_price
       >= investory.reconciliation_parameter('reconciliation_price_jump_upper_ratio')
       OR normalized_price / previous_normalized_price
       <= investory.reconciliation_parameter('reconciliation_price_jump_lower_ratio'))
UNION ALL
SELECT 'PRICE_SCALE_MAPPING_MISMATCH', 'ERROR', h.asset_id, h.asset_symbol, h.price_date,
       h.source, h.source_symbol,
       'source price scale or currency does not match its explicit mapping'::text
FROM included_history h
JOIN investory.asset_price_history aph
  ON aph.asset_id = h.asset_id AND aph.price_date = h.price_date
 AND aph.source = h.source AND aph.source_symbol = h.source_symbol
JOIN investory.asset_source_symbols ass ON ass.id = aph.source_mapping_id
WHERE aph.price_scale_factor IS DISTINCT FROM ass.price_scale_factor
   OR (ass.requires_fx_conversion = false AND aph.price_currency IS DISTINCT FROM ass.price_currency);

COMMENT ON VIEW investory.reporting_asset_price_quality_issues IS
    'Non-destructive included-asset price-quality checks. Invalid price/OHLC/scale rows are rejected by table constraints; this view flags currency, source disagreement, interpolation, and extreme-move anomalies for review.';
