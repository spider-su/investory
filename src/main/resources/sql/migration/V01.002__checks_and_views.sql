CREATE OR REPLACE FUNCTION refresh_account_statistics(target_account_id BIGINT DEFAULT NULL)
    RETURNS VOID AS $$
BEGIN
    INSERT INTO account_statistics (
        account_id,
        total_deposit,
        total_withdrawal,
        net_deposit,
        cash_balance,
        market_value,
        cost_base,
        realized_profit,
        unrealized_profit,
        dividends,
        interest,
        fees,
        taxes,
        updated_at
    )
    WITH latest_daily AS (
        SELECT DISTINCT ON (ad.account_id)
            ad.account_id,
            ad.cash_balance,
            ad.market_value,
            ad.cost_base,
            ad.realized_profit,
            ad.unrealized_profit,
            ad.dividends,
            ad.interest,
            ad.fees,
            ad.taxes,
            ad.deposits,
            ad.withdrawals
        FROM account_daily ad
        WHERE target_account_id IS NULL OR ad.account_id = target_account_id
        ORDER BY ad.account_id, ad.date DESC, ad.id DESC
    )
    SELECT
        a.id AS account_id,
        CASE
            WHEN abs(COALESCE(ld.cash_balance, 0) + COALESCE(ld.market_value, 0)) < 50 THEN 0
            ELSE COALESCE(ld.deposits, 0)
        END AS total_deposit,
        CASE
            WHEN abs(COALESCE(ld.cash_balance, 0) + COALESCE(ld.market_value, 0)) < 50 THEN 0
            ELSE COALESCE(ld.withdrawals, 0)
        END AS total_withdrawal,
        CASE
            WHEN abs(COALESCE(ld.cash_balance, 0) + COALESCE(ld.market_value, 0)) < 50 THEN 0
            ELSE COALESCE(ld.deposits, 0) + COALESCE(ld.withdrawals, 0)
        END AS net_deposit,
        COALESCE(ld.cash_balance, 0) AS cash_balance,
        COALESCE(ld.market_value, 0) AS market_value,
        COALESCE(ld.cost_base, 0) AS cost_base,
        COALESCE(ld.realized_profit, 0) AS realized_profit,
        COALESCE(ld.unrealized_profit, 0) AS unrealized_profit,
        COALESCE(ld.dividends, 0) AS dividends,
        COALESCE(ld.interest, 0) AS interest,
        COALESCE(ld.fees, 0) AS fees,
        COALESCE(ld.taxes, 0) AS taxes,
        NOW() AS updated_at
    FROM accounts a
             LEFT JOIN latest_daily ld ON ld.account_id = a.id
    WHERE target_account_id IS NULL OR a.id = target_account_id
    ON CONFLICT (account_id) DO UPDATE SET
        total_deposit = EXCLUDED.total_deposit,
        total_withdrawal = EXCLUDED.total_withdrawal,
        net_deposit = EXCLUDED.net_deposit,
        cash_balance = EXCLUDED.cash_balance,
        market_value = EXCLUDED.market_value,
        cost_base = EXCLUDED.cost_base,
        realized_profit = EXCLUDED.realized_profit,
        unrealized_profit = EXCLUDED.unrealized_profit,
        dividends = EXCLUDED.dividends,
        interest = EXCLUDED.interest,
        fees = EXCLUDED.fees,
        taxes = EXCLUDED.taxes,
        updated_at = EXCLUDED.updated_at;
END;
$$ LANGUAGE plpgsql;

COMMENT ON TABLE account_statistics IS
    'Computed account-level KPI aggregates refreshed by refresh_account_statistics(); monetary values are stored in USD.';

CREATE OR REPLACE VIEW account_monthly_performance AS
WITH month_end AS (
    SELECT DISTINCT ON (ad.account_id, date_trunc('month', ad.date)::date)
        ad.account_id,
        date_trunc('month', ad.date)::date AS month,
        ad.date AS end_date,
        ad.equity AS end_equity,
        ad.deposits AS cumulative_deposits,
        ad.withdrawals AS cumulative_withdrawals
    FROM account_daily ad
    ORDER BY ad.account_id, date_trunc('month', ad.date)::date, ad.date DESC, ad.id DESC
),
monthly AS (
    SELECT
        me.account_id,
        me.month,
        me.end_date,
        COALESCE(LAG(me.end_equity) OVER (PARTITION BY me.account_id ORDER BY me.month), 0) AS start_equity,
        me.end_equity,
        me.cumulative_deposits
            - COALESCE(LAG(me.cumulative_deposits) OVER (PARTITION BY me.account_id ORDER BY me.month), 0)
            AS deposit_flow,
        me.cumulative_withdrawals
            - COALESCE(LAG(me.cumulative_withdrawals) OVER (PARTITION BY me.account_id ORDER BY me.month), 0)
            AS withdrawal_flow
    FROM month_end me
)
SELECT
    account_id || ':' || to_char(month, 'YYYY-MM-DD') AS id,
    account_id,
    month,
    end_date,
    start_equity,
    end_equity,
    deposit_flow,
    withdrawal_flow,
    deposit_flow + withdrawal_flow AS net_cashflow,
    end_equity - start_equity - deposit_flow - withdrawal_flow AS profit,
    CASE
        WHEN abs(start_equity + greatest(deposit_flow, 0)) > 0.000001
            THEN (end_equity - start_equity - deposit_flow - withdrawal_flow)
                / (start_equity + greatest(deposit_flow, 0))
        ELSE 0
    END AS return_pct
FROM monthly;

COMMENT ON VIEW account_monthly_performance IS
    'Month-end account performance derived from account_daily. Profit is equity change excluding external funding flows; monetary values are stored in USD.';

CREATE OR REPLACE VIEW v_open_position_values AS
WITH latest_rates AS (
    SELECT DISTINCT ON (base, to_currency)
        base,
        to_currency,
        rate
    FROM exchange_rates
    ORDER BY base, to_currency, month DESC
)
SELECT
    p.id AS portfolio_id,
    p.base_currency,
    pos.account_id,
    pos.asset_id,
    COALESCE(pos.currency, a.currency, asset.currency, p.base_currency) AS position_currency,
    COALESCE(pos.volume, 0) AS volume,
    COALESCE(pos.purchase_value, pos.volume * pos.open_price, 0) AS cost_basis,
    COALESCE(pos.volume, 0) * COALESCE(asset.market_price, pos.open_price, 0) AS market_value,
    COALESCE(pos.profit, 0) + COALESCE(pos.commission, 0) + COALESCE(pos.swap, 0) AS unrealized_pl,
    COALESCE(pos.purchase_value, pos.volume * pos.open_price, 0)
        * CASE
            WHEN COALESCE(pos.currency, a.currency, asset.currency, p.base_currency) = p.base_currency THEN 1
            WHEN position_direct_rate.rate IS NOT NULL THEN position_direct_rate.rate
            WHEN position_inverse_rate.rate IS NOT NULL THEN 1 / position_inverse_rate.rate
            ELSE 1
        END AS cost_basis_in_base_currency,
    COALESCE(pos.volume, 0) * COALESCE(asset.market_price, pos.open_price, 0)
        * CASE
            WHEN COALESCE(asset.currency, pos.currency, a.currency, p.base_currency) = p.base_currency THEN 1
            WHEN asset_direct_rate.rate IS NOT NULL THEN asset_direct_rate.rate
            WHEN asset_inverse_rate.rate IS NOT NULL THEN 1 / asset_inverse_rate.rate
            ELSE 1
        END AS market_value_in_base_currency,
    (COALESCE(pos.profit, 0) + COALESCE(pos.commission, 0) + COALESCE(pos.swap, 0))
        * CASE
            WHEN COALESCE(pos.currency, a.currency, asset.currency, p.base_currency) = p.base_currency THEN 1
            WHEN position_direct_rate.rate IS NOT NULL THEN position_direct_rate.rate
            WHEN position_inverse_rate.rate IS NOT NULL THEN 1 / position_inverse_rate.rate
            ELSE 1
        END AS unrealized_pl_in_base_currency
FROM positions pos
         JOIN accounts a ON a.id = pos.account_id
         JOIN portfolios p ON p.id = COALESCE(
             a.portfolio_id,
             (SELECT fallback_portfolio.id FROM portfolios fallback_portfolio ORDER BY fallback_portfolio.id LIMIT 1)
         )
         LEFT JOIN assets asset ON asset.symbol = pos.asset_id
         LEFT JOIN latest_rates position_direct_rate
             ON position_direct_rate.base = COALESCE(pos.currency, a.currency, asset.currency, p.base_currency)
            AND position_direct_rate.to_currency = p.base_currency
         LEFT JOIN latest_rates position_inverse_rate
             ON position_inverse_rate.base = p.base_currency
            AND position_inverse_rate.to_currency = COALESCE(pos.currency, a.currency, asset.currency, p.base_currency)
         LEFT JOIN latest_rates asset_direct_rate
             ON asset_direct_rate.base = COALESCE(asset.currency, pos.currency, a.currency, p.base_currency)
            AND asset_direct_rate.to_currency = p.base_currency
         LEFT JOIN latest_rates asset_inverse_rate
             ON asset_inverse_rate.base = p.base_currency
            AND asset_inverse_rate.to_currency = COALESCE(asset.currency, pos.currency, a.currency, p.base_currency)
WHERE pos.close_time IS NULL
  AND pos.asset_id IS NOT NULL
  AND pos.volume > 0;

CREATE MATERIALIZED VIEW mv_portfolio_asset_allocation AS
SELECT
    portfolio_id,
    base_currency,
    asset_id,
    SUM(volume) AS total_volume,
    SUM(cost_basis_in_base_currency) AS cost_basis_in_base_currency,
    SUM(market_value_in_base_currency) AS total_value_in_base_currency,
    SUM(unrealized_pl_in_base_currency) AS unrealized_pl_in_base_currency
FROM v_open_position_values
GROUP BY portfolio_id, base_currency, asset_id
WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_portfolio_asset_allocation
    ON mv_portfolio_asset_allocation(portfolio_id, asset_id);

CREATE MATERIALIZED VIEW mv_portfolio_currency_breakdown AS
WITH realized AS (
    SELECT
        p.id AS portfolio_id,
        p.base_currency,
        COALESCE(pos.currency, a.currency, p.base_currency) AS currency,
        SUM(COALESCE(pos.profit, 0) + COALESCE(pos.commission, 0) + COALESCE(pos.swap, 0)) AS amount_local,
        SUM(
            (COALESCE(pos.profit, 0) + COALESCE(pos.commission, 0) + COALESCE(pos.swap, 0))
            * CASE
                WHEN COALESCE(pos.currency, a.currency, p.base_currency) = p.base_currency THEN 1
                WHEN direct_rate.rate IS NOT NULL THEN direct_rate.rate
                WHEN inverse_rate.rate IS NOT NULL THEN 1 / inverse_rate.rate
                ELSE 1
            END
        ) AS amount_in_base_currency
    FROM positions pos
             JOIN accounts a ON a.id = pos.account_id
             JOIN portfolios p ON p.id = COALESCE(
                 a.portfolio_id,
                 (SELECT fallback_portfolio.id FROM portfolios fallback_portfolio ORDER BY fallback_portfolio.id LIMIT 1)
             )
             LEFT JOIN (
                 SELECT DISTINCT ON (base, to_currency) base, to_currency, rate
                 FROM exchange_rates
                 ORDER BY base, to_currency, month DESC
             ) direct_rate
                 ON direct_rate.base = COALESCE(pos.currency, a.currency, p.base_currency)
                AND direct_rate.to_currency = p.base_currency
             LEFT JOIN (
                 SELECT DISTINCT ON (base, to_currency) base, to_currency, rate
                 FROM exchange_rates
                 ORDER BY base, to_currency, month DESC
             ) inverse_rate
                 ON inverse_rate.base = p.base_currency
                AND inverse_rate.to_currency = COALESCE(pos.currency, a.currency, p.base_currency)
    WHERE pos.close_time IS NOT NULL
    GROUP BY p.id, p.base_currency, COALESCE(pos.currency, a.currency, p.base_currency)
),
unrealized AS (
    SELECT
        portfolio_id,
        base_currency,
        position_currency AS currency,
        SUM(unrealized_pl) AS amount_local,
        SUM(unrealized_pl_in_base_currency) AS amount_in_base_currency
    FROM v_open_position_values
    GROUP BY portfolio_id, base_currency, position_currency
),
dividends AS (
    SELECT
        p.id AS portfolio_id,
        p.base_currency,
        COALESCE(co.currency, a.currency, p.base_currency) AS currency,
        SUM(COALESCE(co.amount, 0)) AS amount_local,
        SUM(
            COALESCE(co.amount, 0)
            * CASE
                WHEN COALESCE(co.currency, a.currency, p.base_currency) = p.base_currency THEN 1
                WHEN direct_rate.rate IS NOT NULL THEN direct_rate.rate
                WHEN inverse_rate.rate IS NOT NULL THEN 1 / inverse_rate.rate
                ELSE 1
            END
        ) AS amount_in_base_currency
    FROM cash_operations co
             JOIN accounts a ON a.id = co.account_id
             JOIN portfolios p ON p.id = COALESCE(
                 a.portfolio_id,
                 (SELECT fallback_portfolio.id FROM portfolios fallback_portfolio ORDER BY fallback_portfolio.id LIMIT 1)
             )
             LEFT JOIN (
                 SELECT DISTINCT ON (base, to_currency) base, to_currency, rate
                 FROM exchange_rates
                 ORDER BY base, to_currency, month DESC
             ) direct_rate
                 ON direct_rate.base = COALESCE(co.currency, a.currency, p.base_currency)
                AND direct_rate.to_currency = p.base_currency
             LEFT JOIN (
                 SELECT DISTINCT ON (base, to_currency) base, to_currency, rate
                 FROM exchange_rates
                 ORDER BY base, to_currency, month DESC
             ) inverse_rate
                 ON inverse_rate.base = p.base_currency
                AND inverse_rate.to_currency = COALESCE(co.currency, a.currency, p.base_currency)
    WHERE co.operation = 'DIVIDEND'
    GROUP BY p.id, p.base_currency, COALESCE(co.currency, a.currency, p.base_currency)
)
SELECT portfolio_id, base_currency, 'REALIZED' AS metric_type, currency, amount_local, amount_in_base_currency FROM realized
UNION ALL
SELECT portfolio_id, base_currency, 'UNREALIZED' AS metric_type, currency, amount_local, amount_in_base_currency FROM unrealized
UNION ALL
SELECT portfolio_id, base_currency, 'DIVIDENDS' AS metric_type, currency, amount_local, amount_in_base_currency FROM dividends
WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS ux_mv_portfolio_currency_breakdown
    ON mv_portfolio_currency_breakdown(portfolio_id, metric_type, currency);

CREATE MATERIALIZED VIEW mv_portfolio_kpi_summary AS
WITH account_totals AS (
    SELECT
        p.id AS portfolio_id,
        p.name AS portfolio_name,
        p.base_currency,
        SUM(ast.total_deposit) AS total_deposits,
        SUM(ast.net_deposit) AS net_deposits,
        SUM(ast.cash_balance) AS total_cash,
        SUM(ast.market_value) AS total_market_value,
        SUM(ast.cash_balance + ast.market_value) AS total_equity,
        SUM(ast.realized_profit) AS total_realized_profit,
        SUM(ast.unrealized_profit) AS total_unrealized_profit,
        SUM(ast.dividends) AS total_dividends
    FROM portfolios p
             JOIN accounts a ON a.portfolio_id = p.id
             JOIN account_statistics ast ON ast.account_id = a.id
    GROUP BY p.id, p.name, p.base_currency
)
SELECT
    account_totals.portfolio_id,
    account_totals.portfolio_name,
    account_totals.base_currency,
    account_totals.total_deposits,
    account_totals.net_deposits,
    account_totals.total_cash,
    account_totals.total_market_value,
    account_totals.total_equity,
    account_totals.total_realized_profit,
    account_totals.total_unrealized_profit,
    account_totals.total_dividends
FROM account_totals
WITH DATA;

CREATE UNIQUE INDEX IF NOT EXISTS ux_mv_portfolio_kpi_summary_id
    ON mv_portfolio_kpi_summary(portfolio_id);

SELECT refresh_account_statistics();
REFRESH MATERIALIZED VIEW mv_portfolio_asset_allocation;
REFRESH MATERIALIZED VIEW mv_portfolio_currency_breakdown;
REFRESH MATERIALIZED VIEW mv_portfolio_kpi_summary;
