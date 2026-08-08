SET search_path TO investory, public;

-- Manual repair script. This is intentionally not a Flyway migration.
--
-- Rebuild account_monthly_benchmark so every value used by the benchmark is in the
-- portfolio base currency:
--   monthly profit = closing equity (portfolio base)
--                  - opening equity (portfolio base)
--                  - net external flow (portfolio base)
--
-- Internal transfers, bookkeeping entries, FX conversions and corrections are not
-- external funding and therefore must not be removed from investment performance.

CREATE OR REPLACE VIEW investory.account_monthly_benchmark AS
WITH daily_equity_in_portfolio_base AS (
    SELECT
        ad.account_id,
        ad.snapshot_date,
        date_trunc('month', ad.snapshot_date)::date AS month,
        p.base_currency::varchar(3) AS valuation_currency,
        ad.equity * fx.fx_rate_to_base AS equity
    FROM investory.account_daily ad
    JOIN investory.accounts a
      ON a.id = ad.account_id
    JOIN investory.portfolios p
      ON p.id = a.portfolio_id
    JOIN investory.v_portfolio_daily_fx_rate fx
      ON fx.portfolio_id = a.portfolio_id
     AND fx.valuation_date = ad.snapshot_date
     AND fx.source_currency = ad.valuation_currency
    WHERE fx.fx_rate_to_base IS NOT NULL
),
monthly_equity AS (
    SELECT
        account_id,
        month,
        min(snapshot_date) AS first_date,
        max(snapshot_date) AS end_date,
        (array_agg(equity ORDER BY snapshot_date ASC))[1] AS opening_equity,
        (array_agg(equity ORDER BY snapshot_date DESC))[1] AS closing_equity,
        (array_agg(valuation_currency ORDER BY snapshot_date DESC))[1]::varchar(3)
            AS valuation_currency
    FROM daily_equity_in_portfolio_base
    GROUP BY account_id, month
),
portfolio_base_external_flows AS (
    SELECT
        nco.account_id,
        date_trunc('month', nco.date)::date AS month,
        sum(nco.amount_in_portfolio_base_currency) AS net_external_flow
    FROM investory.normalized_cash_operations nco
    WHERE nco.normalized_category IN (
        'EXTERNAL_DEPOSIT',
        'EXTERNAL_WITHDRAWAL'
    )
    GROUP BY nco.account_id, date_trunc('month', nco.date)::date
)
SELECT
    equity.account_id,
    equity.month,
    equity.first_date,
    equity.end_date,
    equity.opening_equity,
    equity.closing_equity,
    equity.valuation_currency::varchar(3) AS valuation_currency,
    monthly.deposits,
    monthly.withdrawals,
    monthly.dividends,
    monthly.interest,
    monthly.fees,
    monthly.taxes,
    monthly.realized_profit,
    equity.closing_equity
        - equity.opening_equity
        - coalesce(flows.net_external_flow, 0) AS total_profit,
    CASE
        WHEN equity.opening_equity = 0 THEN NULL::numeric
        ELSE (
            equity.closing_equity
            - equity.opening_equity
            - coalesce(flows.net_external_flow, 0)
        ) / equity.opening_equity
    END AS compounded_monthly_return,
    monthly.updated_at
FROM monthly_equity equity
JOIN investory.account_monthly_mv monthly
  ON monthly.account_id = equity.account_id
 AND monthly.month = equity.month
LEFT JOIN portfolio_base_external_flows flows
  ON flows.account_id = equity.account_id
 AND flows.month = equity.month;

COMMENT ON VIEW investory.account_monthly_benchmark IS
    'Monthly benchmark performance using portfolio-base opening equity, closing equity and external cash flows. Internal transfers and FX conversions remain part of account equity and are not treated as external funding.';

-- Optional verification after running the script:
-- SELECT account_id, month, valuation_currency, opening_equity, closing_equity,
--        total_profit, compounded_monthly_return
-- FROM investory.account_monthly_benchmark
-- ORDER BY account_id, month;
