SET search_path TO investory, public;

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
