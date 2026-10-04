-- Rebuild trade settlement reconciliation using normalized prices for carrying values.
-- Normalized prices exclude future interpolation endpoints and retain the selected currency.
-- Result-only settlement includes CLOSE_TRADE plus ROLLOVER. IBKR bond redemptions
-- are normalized as BOND_REDEMPTION settlement cash and reconcile to closed bond lots.
DROP VIEW investory.recon_v_trade_settlement_by_account;
DROP MATERIALIZED VIEW investory.recon_v_trade_settlement;

CREATE MATERIALIZED VIEW investory.recon_v_trade_settlement AS
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
              AND investory.fx_status_usable(profit_fx.conversion_status)
              AND (COALESCE(p.commission, 0) = 0
                   OR investory.fx_status_usable(commission_fx.conversion_status))
            THEN (COALESCE(p.profit, 0) - COALESCE(p.swap, 0))
                    * profit_fx.fx_rate_to_base
                - COALESCE(p.commission, 0)
                    * COALESCE(commission_fx.fx_rate_to_base, 0)
        END AS close_result_base,
        cost_fx.fx_rate_to_base AS cost_fx_rate_to_base,
        cost_fx.conversion_status AS cost_conversion_status,
        CASE
            WHEN p.settlement_model = 'CASH_SETTLED'
             AND NOT investory.fx_status_usable(cost_fx.conversion_status) THEN 1
            WHEN p.settlement_model = 'RESULT_ONLY'
             AND (NOT investory.fx_status_usable(profit_fx.conversion_status)
                  OR (COALESCE(p.commission, 0) <> 0
                      AND NOT investory.fx_status_usable(commission_fx.conversion_status))) THEN 1
            ELSE 0
        END AS missing_fx_count
    FROM investory.positions p
    JOIN investory.accounts account ON account.id = p.account_id
    JOIN investory.assets asset
      ON asset.id = p.asset_id
     AND asset.exclude_from_import = false
    LEFT JOIN investory.app_v_portfolio_daily_fx_rate cost_fx
      ON cost_fx.portfolio_id = account.portfolio_id
     AND cost_fx.valuation_date = p.close_time::date
     AND cost_fx.source_currency = p.cost_currency::varchar(3)
    LEFT JOIN investory.app_v_portfolio_daily_fx_rate profit_fx
      ON profit_fx.portfolio_id = account.portfolio_id
     AND profit_fx.valuation_date = p.close_time::date
     AND profit_fx.source_currency = p.profit_currency::varchar(3)
    LEFT JOIN investory.app_v_portfolio_daily_fx_rate commission_fx
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
              AND NOT investory.fx_status_usable(cl.cost_conversion_status)) > 0
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
    LEFT JOIN investory.app_v_portfolio_daily_fx_rate fx
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
              AND NOT investory.fx_status_usable(ol.conversion_status)) > 0
            THEN NULL::numeric
            ELSE SUM(ol.open_notional_native * ol.fx_rate_to_base) FILTER (
                WHERE ol.position_settlement_model = 'CASH_SETTLED')
        END AS position_open_notional_base,
        COUNT(*) FILTER (
            WHERE ol.position_settlement_model = 'CASH_SETTLED'
              AND NOT investory.fx_status_usable(ol.conversion_status))::bigint
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
        nco.normalized_category,
        co.amount,
        fx.fx_rate_to_base,
        fx.conversion_status
    FROM closed_group target
    LEFT JOIN investory.cash_operations co
      ON co.account_id = target.account_id
     AND co.asset_id = target.asset_id
     AND co.date::date = target.valuation_date
     AND (
         co.operation IN ('STOCK_PURCHASE', 'STOCK_SELL', 'CLOSE_TRADE', 'ROLLOVER')
         OR EXISTS (
             SELECT 1
             FROM investory.app_v_normalized_cash_operations classified
             WHERE classified.operation_id = co.id
               AND classified.normalized_category = 'BOND_REDEMPTION'
         )
     )
    LEFT JOIN investory.app_v_normalized_cash_operations nco
      ON nco.operation_id = co.id
    LEFT JOIN investory.app_v_portfolio_daily_fx_rate fx
      ON fx.portfolio_id = target.portfolio_id
     AND fx.valuation_date = target.valuation_date
     AND fx.source_currency = co.currency::varchar(3)
), ledger_group AS (
    SELECT
        lr.account_id,
        lr.asset_id,
        lr.valuation_date,
        COUNT(lr.operation_id) FILTER (
            WHERE lr.raw_operation = 'STOCK_SELL'
               OR lr.normalized_category = 'BOND_REDEMPTION')::bigint AS ledger_sale_row_count,
        COUNT(lr.operation_id) FILTER (
            WHERE lr.raw_operation IN ('CLOSE_TRADE', 'ROLLOVER'))::bigint AS ledger_close_result_row_count,
        CASE WHEN COUNT(lr.operation_id) FILTER (
            WHERE (lr.raw_operation = 'STOCK_SELL'
                  OR lr.normalized_category = 'BOND_REDEMPTION')
              AND NOT investory.fx_status_usable(lr.conversion_status)) > 0
            THEN NULL::numeric
            ELSE SUM(lr.amount * lr.fx_rate_to_base) FILTER (
                WHERE lr.raw_operation = 'STOCK_SELL'
                   OR lr.normalized_category = 'BOND_REDEMPTION')
        END AS ledger_sale_cash_base,
        CASE WHEN COUNT(lr.operation_id) FILTER (
            WHERE lr.raw_operation IN ('CLOSE_TRADE', 'ROLLOVER')
              AND NOT investory.fx_status_usable(lr.conversion_status)) > 0
            THEN NULL::numeric
            ELSE SUM(lr.amount * lr.fx_rate_to_base) FILTER (
                WHERE lr.raw_operation IN ('CLOSE_TRADE', 'ROLLOVER'))
        END AS ledger_close_result_base,
        CASE WHEN COUNT(lr.operation_id) FILTER (
            WHERE lr.raw_operation = 'STOCK_PURCHASE'
              AND NOT investory.fx_status_usable(lr.conversion_status)) > 0
            THEN NULL::numeric
            ELSE -SUM(lr.amount * lr.fx_rate_to_base) FILTER (
                WHERE lr.raw_operation = 'STOCK_PURCHASE')
        END AS ledger_purchase_cash_base,
        COUNT(lr.operation_id) FILTER (
            WHERE NOT investory.fx_status_usable(lr.conversion_status))::bigint
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
), settlement_prices AS (
    SELECT target.account_id,
           target.asset_id,
           target.valuation_date,
           price_slot.price_role,
           price.close_price,
           price.price_scale_factor,
           price.price_currency,
           price.quality_class
    FROM closed_group target
    LEFT JOIN previous_dates pd
      ON pd.account_id = target.account_id
     AND pd.asset_id = target.asset_id
     AND pd.valuation_date = target.valuation_date
    CROSS JOIN LATERAL (
        VALUES ('PREVIOUS'::varchar(16), pd.previous_valuation_date),
               ('CURRENT'::varchar(16), target.valuation_date)
    ) price_slot(price_role, price_date)
    LEFT JOIN LATERAL (
        SELECT
            ndp.selected_price AS close_price,
            1::numeric AS price_scale_factor,
            ndp.price_currency,
            ndp.quality_class
        FROM investory.app_v_normalized_daily_price ndp
        WHERE ndp.asset_id = target.asset_id
          AND ndp.valuation_date = price_slot.price_date
    ) price ON true
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
              OR NOT investory.fx_status_usable(previous_fx.conversion_status) THEN NULL::numeric
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
              OR NOT investory.fx_status_usable(previous_fx.conversion_status) THEN 'FAIL'
            ELSE 'PASS'
        END::varchar(16) AS previous_reconstruction_status,
        COALESCE(current_quantity.open_quantity, 0) AS current_open_quantity,
        CASE
            WHEN COALESCE(current_quantity.open_quantity, 0) = 0 THEN 0::numeric
            WHEN current_price.close_price IS NULL
              OR NOT investory.fx_status_usable(current_fx.conversion_status) THEN NULL::numeric
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
              OR NOT investory.fx_status_usable(current_fx.conversion_status) THEN 'FAIL'
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
    LEFT JOIN settlement_prices previous_price
      ON previous_price.account_id = target.account_id
     AND previous_price.asset_id = target.asset_id
     AND previous_price.valuation_date = target.valuation_date
     AND previous_price.price_role = 'PREVIOUS'
    LEFT JOIN investory.app_v_portfolio_daily_fx_rate previous_fx
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
    LEFT JOIN settlement_prices current_price
      ON current_price.account_id = target.account_id
     AND current_price.asset_id = target.asset_id
     AND current_price.valuation_date = target.valuation_date
     AND current_price.price_role = 'CURRENT'
    LEFT JOIN investory.app_v_portfolio_daily_fx_rate current_fx
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
WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS uq_recon_trade_settlement
    ON investory.recon_v_trade_settlement
        (account_id, asset_id, valuation_date);

CREATE INDEX IF NOT EXISTS idx_recon_trade_settlement_status
    ON investory.recon_v_trade_settlement
        (reconciliation_status, anomaly_code, valuation_date DESC);

COMMENT ON MATERIALIZED VIEW investory.recon_v_trade_settlement IS
    'Grouped account/date/symbol reconciliation of position closes, canonical sale and bond-redemption cash, prior carrying value, same-day opens, and market-value movement. Result-only cash includes CLOSE_TRADE and ROLLOVER; reorganizations remain separate. Authoritative comparisons fail closed when FX or valuation inputs are unavailable.';
CREATE OR REPLACE VIEW investory.recon_v_trade_settlement_by_account AS
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
FROM investory.recon_v_trade_settlement r
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

COMMENT ON VIEW investory.recon_v_trade_settlement_by_account IS
    'Account grouping over trade settlement reconciliation. Authoritative sums become NULL when any required converted value is unavailable.';
