SET search_path TO investory, public;

CREATE OR REPLACE VIEW investory.account_monthly_benchmark AS
SELECT
    account_id,
    month,
    first_date,
    end_date,
    opening_equity,
    closing_equity,
    valuation_currency,
    deposits,
    withdrawals,
    dividends,
    interest,
    fees,
    taxes,
    realized_profit,
    CASE
        WHEN compounded_monthly_return IS NULL THEN NULL::numeric
        ELSE opening_equity * compounded_monthly_return
    END AS total_profit,
    compounded_monthly_return,
    updated_at
FROM investory.account_monthly_mv;

COMMENT ON VIEW investory.account_monthly_benchmark IS
    'Benchmark-safe monthly account performance. Monetary P/L is derived from opening equity and the currency-neutral compounded monthly return, preventing account-native cash flows from being mixed with portfolio-base equity.';
