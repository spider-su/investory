SET search_path TO investory, public;

CREATE OR REPLACE VIEW investory.v_canonical_asset_daily_price AS
SELECT DISTINCT ON (aph.asset_id, aph.price_date)
    aph.asset_id,
    aph.price_date,
    aph.source,
    aph.source_symbol,
    aph.price_origin,
    aph.price_currency,
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
    aph.imported_at
FROM investory.asset_price_history aph
ORDER BY
    aph.asset_id,
    aph.price_date,
    aph.quality_score DESC,
    aph.is_observed DESC,
    aph.is_proxy ASC,
    aph.imported_at DESC,
    aph.source;

CREATE OR REPLACE VIEW investory.v_activity_events AS
SELECT
    ad.account_id,
    ad.snapshot_date::timestamptz AS occurred_at,
    'ACCOUNT_DAILY'::varchar(32) AS activity_source,
    CASE
        WHEN COALESCE(ad.deposits, 0) <> 0 THEN 'DEPOSIT'
        WHEN COALESCE(ad.withdrawals, 0) <> 0 THEN 'WITHDRAWAL'
        WHEN COALESCE(ad.dividends, 0) <> 0 THEN 'DIVIDEND'
        WHEN COALESCE(ad.interest, 0) <> 0 THEN 'INTEREST'
        WHEN COALESCE(ad.fees, 0) <> 0 THEN 'FEE'
        WHEN COALESCE(ad.taxes, 0) <> 0 THEN 'TAX'
        WHEN COALESCE(ad.realized_profit, 0) <> 0 THEN 'REALIZED_PROFIT'
        ELSE 'SNAPSHOT'
    END::varchar(64) AS activity_type,
    (ad.account_id::varchar || ':' || ad.snapshot_date::varchar)::varchar(128) AS activity_key
FROM investory.account_daily ad
WHERE
    COALESCE(ad.deposits, 0) <> 0
    OR COALESCE(ad.withdrawals, 0) <> 0
    OR COALESCE(ad.dividends, 0) <> 0
    OR COALESCE(ad.interest, 0) <> 0
    OR COALESCE(ad.fees, 0) <> 0
    OR COALESCE(ad.taxes, 0) <> 0
    OR COALESCE(ad.realized_profit, 0) <> 0;

CREATE OR REPLACE VIEW investory.normalized_cash_operations AS
WITH classified AS (
    SELECT
        co.id AS operation_id,
        co.account_id,
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
        END::varchar(64) AS normalized_category
    FROM investory.cash_operations co
    JOIN investory.accounts a
      ON a.id = co.account_id
    JOIN investory.portfolios p
      ON p.id = a.portfolio_id
),
fx AS (
    SELECT
        c.*,
        CASE
            WHEN c.currency IS NULL OR c.base_currency IS NULL THEN NULL::numeric
            WHEN c.currency = c.base_currency THEN 1::numeric
            WHEN direct.rate IS NOT NULL THEN direct.rate
            WHEN inverse.rate IS NOT NULL AND inverse.rate <> 0 THEN 1::numeric / inverse.rate
            ELSE NULL::numeric
        END AS base_to_operation_rate
    FROM classified c
    LEFT JOIN investory.exchange_rates direct
      ON direct.month = c.rate_month
     AND direct.base::varchar(3) = c.base_currency
     AND direct.to_currency::varchar(3) = c.currency
    LEFT JOIN investory.exchange_rates inverse
      ON inverse.month = c.rate_month
     AND inverse.base::varchar(3) = c.currency
     AND inverse.to_currency::varchar(3) = c.base_currency
)
SELECT
    operation_id,
    account_id,
    account_currency,
    currency,
    base_currency,
    raw_operation,
    normalized_category,
    asset_id,
    amount,
    CASE
        WHEN base_to_operation_rate IS NULL OR base_to_operation_rate = 0 THEN amount
        ELSE amount / base_to_operation_rate
    END AS amount_in_base_currency,
    comment,
    date,
    rate_month,
    base_to_operation_rate
FROM fx;

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
        ad.daily_profit_amount,
        ad.daily_return_pct,
        ad.valuation_currency,
        ROW_NUMBER() OVER (
            PARTITION BY ad.account_id, date_trunc('month', ad.snapshot_date)
            ORDER BY ad.snapshot_date
        ) AS rn_first,
        ROW_NUMBER() OVER (
            PARTITION BY ad.account_id, date_trunc('month', ad.snapshot_date)
            ORDER BY ad.snapshot_date DESC
        ) AS rn_last
    FROM investory.account_daily ad
)
SELECT
    mr.account_id,
    mr.month,
    MIN(mr.snapshot_date) AS first_date,
    MAX(mr.snapshot_date) AS end_date,
    MAX(CASE WHEN mr.rn_first = 1 THEN mr.equity END) AS opening_equity,
    MAX(CASE WHEN mr.rn_last = 1 THEN mr.equity END) AS closing_equity,
    MAX(CASE WHEN mr.rn_last = 1 THEN mr.valuation_currency END)::varchar(3) AS valuation_currency,
    SUM(mr.deposits) AS deposits,
    SUM(mr.withdrawals) AS withdrawals,
    SUM(mr.dividends) AS dividends,
    SUM(mr.interest) AS interest,
    SUM(mr.fees) AS fees,
    SUM(mr.taxes) AS taxes,
    SUM(mr.realized_profit) AS realized_profit,
    SUM(mr.daily_profit_amount) AS total_profit,
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
GROUP BY mr.account_id, mr.month
WITH DATA;

CREATE UNIQUE INDEX ux_mv_account_monthly_account_month
    ON investory.account_monthly_mv(account_id, month);

CREATE OR REPLACE VIEW investory.account_monthly AS
SELECT * FROM investory.account_monthly_mv;

CREATE OR REPLACE VIEW investory.v_portfolio_daily AS
WITH account_rows AS (
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
        ad.valuation_currency
    FROM investory.account_daily ad
    JOIN investory.accounts a
        ON a.id = ad.account_id
    JOIN investory.portfolios p
        ON p.id = a.portfolio_id
)
SELECT
    ar.portfolio_id,
    ar.snapshot_date,
    ar.base_currency,
    SUM(ar.cash_balance) AS cash_balance,
    SUM(ar.market_value) AS market_value,
    SUM(ar.equity) AS equity,
    SUM(ar.deposits) AS deposits,
    SUM(ar.withdrawals) AS withdrawals,
    SUM(ar.dividends) AS dividends,
    SUM(ar.interest) AS interest,
    SUM(ar.fees) AS fees,
    SUM(ar.taxes) AS taxes,
    SUM(ar.realized_profit) AS realized_profit,
    SUM(ar.daily_profit_amount) AS total_profit,
    CASE
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

CREATE MATERIALIZED VIEW investory.portfolio_daily_mv AS
SELECT * FROM investory.v_portfolio_daily
WITH DATA;

CREATE UNIQUE INDEX ux_mv_portfolio_daily_portfolio_date
    ON investory.portfolio_daily_mv(portfolio_id, snapshot_date);

CREATE OR REPLACE VIEW investory.portfolio_daily AS
SELECT * FROM investory.portfolio_daily_mv;

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
    MAX(CASE WHEN mr.rn_first = 1 THEN mr.equity END) AS opening_equity,
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

CREATE OR REPLACE VIEW investory.portfolio_monthly AS
SELECT * FROM investory.portfolio_monthly_mv;

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
flow_totals AS (
    SELECT
        nco.account_id,
        SUM(CASE WHEN nco.normalized_category = 'EXTERNAL_DEPOSIT'
            THEN nco.amount_in_base_currency ELSE 0 END) AS total_deposit,
        SUM(CASE WHEN nco.normalized_category = 'EXTERNAL_WITHDRAWAL'
            THEN ABS(nco.amount_in_base_currency) ELSE 0 END) AS total_withdrawal,
        SUM(CASE WHEN nco.normalized_category IN ('DIVIDEND', 'DIVIDEND_REVERSAL')
            THEN nco.amount_in_base_currency ELSE 0 END) AS dividends,
        SUM(CASE WHEN nco.normalized_category IN ('INTEREST', 'INTEREST_REVERSAL')
            THEN nco.amount_in_base_currency ELSE 0 END) AS interest,
        SUM(CASE WHEN nco.normalized_category = 'FEE'
            THEN -nco.amount_in_base_currency ELSE 0 END) AS fees,
        SUM(CASE WHEN nco.normalized_category IN ('WITHHOLDING_TAX', 'WITHHOLDING_TAX_REVERSAL', 'OTHER_TAX')
            THEN -nco.amount_in_base_currency ELSE 0 END) AS taxes,
        SUM(CASE WHEN nco.normalized_category = 'REALIZED_TRADE_RESULT'
            THEN nco.amount_in_base_currency ELSE 0 END) AS realized_profit,
        SUM(CASE WHEN nco.normalized_category IN ('INTERNAL_TRANSFER_IN', 'INTERNAL_TRANSFER_OUT')
            THEN nco.amount_in_base_currency ELSE 0 END) AS internal_transfer_net,
        SUM(CASE WHEN nco.normalized_category = 'FX_CONVERSION'
            THEN nco.amount_in_base_currency ELSE 0 END) AS fx_conversion_net
    FROM investory.normalized_cash_operations nco
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
    COALESCE(ft.total_deposit, 0) AS total_deposit,
    COALESCE(ft.total_withdrawal, 0) AS total_withdrawal,
    COALESCE(ft.total_deposit, 0)
      - COALESCE(ft.total_withdrawal, 0)
      + COALESCE(ft.internal_transfer_net, 0)
      + COALESCE(ft.fx_conversion_net, 0) AS net_deposit,
    COALESCE(ld.cash_balance, 0) AS cash_balance,
    COALESCE(ld.market_value, 0) AS market_value,
    COALESCE(ld.equity, 0) AS equity,
    COALESCE(ld.cost_base, 0) AS cost_base,
    COALESCE(ft.realized_profit, 0) AS realized_profit,
    COALESCE(ld.unrealized_profit, 0) AS unrealized_profit,
    COALESCE(ft.dividends, 0) AS dividends,
    COALESCE(ft.interest, 0) AS interest,
    COALESCE(ft.fees, 0) AS fees,
    COALESCE(ft.taxes, 0) AS taxes,
    COALESCE(am.activity_count, 0) AS activity_count,
    am.first_activity_at,
    am.last_activity_at,
    ld.snapshot_date AS latest_snapshot_date,
    ld.daily_return_pct AS latest_return_pct,
    NOW() AS updated_at
FROM investory.accounts a
JOIN investory.portfolios p
    ON p.id = a.portfolio_id
LEFT JOIN latest_daily ld
    ON ld.account_id = a.id
LEFT JOIN flow_totals ft
    ON ft.account_id = a.id
LEFT JOIN activity_meta am
    ON am.account_id = a.id
WITH DATA;

CREATE UNIQUE INDEX ux_mv_account_statistics_account
    ON investory.account_statistics(account_id);

CREATE MATERIALIZED VIEW investory.portfolio_kpi_summary AS
WITH latest_portfolio_daily AS (
    SELECT DISTINCT ON (pd.portfolio_id)
        pd.portfolio_id,
        pd.base_currency,
        pd.snapshot_date,
        pd.cash_balance,
        pd.market_value,
        pd.equity
    FROM investory.v_portfolio_daily pd
    ORDER BY pd.portfolio_id, pd.snapshot_date DESC
),
latest_account_stats AS (
    SELECT
        a.portfolio_id,
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
    COALESCE(las.total_deposits, 0) AS total_deposits,
    COALESCE(las.total_withdrawals, 0) AS total_withdrawals,
    COALESCE(las.net_deposits, 0) AS net_deposits,
    COALESCE(lpd.cash_balance, 0) AS total_cash,
    COALESCE(lpd.market_value, 0) AS total_market_value,
    COALESCE(lpd.equity, 0) AS total_equity,
    COALESCE(las.total_realized_profit, 0) AS total_realized_profit,
    COALESCE(las.total_unrealized_profit, 0) AS total_unrealized_profit,
    COALESCE(las.total_dividends, 0) AS total_dividends,
    COALESCE(las.total_interest, 0) AS total_interest,
    COALESCE(las.total_fees, 0) AS total_fees,
    COALESCE(las.total_taxes, 0) AS total_taxes,
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
        CASE
            WHEN ABS(ad.equity - (ad.cash_balance + ad.market_value)) > 1 THEN 'ERROR'
            ELSE 'WARN'
        END::varchar(16) AS severity,
        NULL::varchar(64) AS asset_id,
        NULL::bigint AS operation_id,
        (ad.cash_balance + ad.market_value)::numeric AS expected_value,
        ad.equity::numeric AS actual_value,
        ('equity=' || ad.equity || ', cash+market=' || (ad.cash_balance + ad.market_value))::text AS details
    FROM investory.account_daily ad
    WHERE ABS(ad.equity - (ad.cash_balance + ad.market_value)) > 0.01
),
unrealized_check AS (
    SELECT
        ad.account_id,
        ad.snapshot_date,
        'UNREALIZED_MISMATCH'::varchar(64) AS issue_type,
        CASE
            WHEN ABS(ad.unrealized_profit - (ad.market_value - ad.cost_base)) > 1 THEN 'ERROR'
            ELSE 'WARN'
        END::varchar(16) AS severity,
        NULL::varchar(64) AS asset_id,
        NULL::bigint AS operation_id,
        (ad.market_value - ad.cost_base)::numeric AS expected_value,
        ad.unrealized_profit::numeric AS actual_value,
        ('unrealized=' || ad.unrealized_profit || ', market-cost=' || (ad.market_value - ad.cost_base))::text AS details
    FROM investory.account_daily ad
    WHERE ABS(ad.unrealized_profit - (ad.market_value - ad.cost_base)) > 0.01
),
duplicate_prices AS (
    SELECT
        NULL::bigint AS account_id,
        aph.price_date AS snapshot_date,
        'DUPLICATE_PRICE'::varchar(64) AS issue_type,
        'ERROR'::varchar(16) AS severity,
        a.symbol AS asset_id,
        NULL::bigint AS operation_id,
        1::numeric AS expected_value,
        COUNT(*)::numeric AS actual_value,
        ('price rows=' || COUNT(*) || ' for source=' || aph.source)::text AS details
    FROM investory.asset_price_history aph
    JOIN investory.assets a
      ON a.id = aph.asset_id
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
    GROUP BY
        p.account_id,
        p.asset_id,
        COALESCE(
            COALESCE(p.close_time, p.open_time)::date,
            CURRENT_DATE
        ),
        p.operation,
        COALESCE(p.volume, 0),
        COALESCE(p.currency, ''),
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
      ON a.symbol = p.asset_id
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
        NULL::varchar(64) AS asset_id,
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
      AND (x.market_value / x.prev_market_value > 5 OR x.market_value / x.prev_market_value < 0.2)
      AND ABS(x.market_value - x.prev_market_value) > COALESCE(t.trade_notional, 0) * 1.25 + 250
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
)
SELECT
    a.portfolio_id,
    p.base_currency::varchar(3) AS base_currency,
    'ACCOUNT_LATEST'::varchar(32) AS metric_type,
    lad.valuation_currency::varchar(3) AS currency,
    SUM(lad.cash_balance + lad.market_value) AS amount_local,
    SUM(lad.cash_balance + lad.market_value) AS amount_in_base_currency,
    NOW() AS updated_at
FROM latest_account_daily lad
JOIN investory.accounts a
    ON a.id = lad.account_id
JOIN investory.portfolios p
    ON p.id = a.portfolio_id
GROUP BY a.portfolio_id, p.base_currency, lad.valuation_currency
WITH DATA;

CREATE UNIQUE INDEX ux_mv_portfolio_currency_breakdown_key
    ON investory.portfolio_currency_breakdown(portfolio_id, metric_type, currency);

CREATE OR REPLACE VIEW investory.v_open_position_values AS
WITH canonical_fx AS (
    SELECT DISTINCT ON (er.base, er.to_currency)
        er.base::varchar(3) AS base,
        er.to_currency::varchar(3) AS to_currency,
        er.rate
    FROM investory.exchange_rates er
    ORDER BY er.base, er.to_currency, er.month DESC, er.imported_at DESC, er.source
),
latest_market_price AS (
    SELECT DISTINCT ON (cp.asset_id)
        cp.asset_id,
        cp.price_currency::varchar(3) AS market_currency,
        cp.close_price AS market_price
    FROM investory.v_canonical_asset_daily_price cp
    ORDER BY cp.asset_id, cp.price_date DESC, cp.imported_at DESC, cp.source
),
aggregated_positions AS (
    SELECT
        p.account_id,
        asset.id AS asset_id,
        MIN(p.currency)::varchar(3) AS position_currency,
        COUNT(DISTINCT p.currency) AS currency_count,
        SUM(COALESCE(p.volume, 0)) AS volume,
        SUM(COALESCE(p.purchase_value, p.volume * p.open_price, 0)) AS cost_basis,
        SUM(COALESCE(p.purchase_value, p.volume * p.open_price, 0))
            / NULLIF(SUM(COALESCE(p.volume, 0)), 0) AS average_open_price
    FROM investory.positions p
    JOIN investory.assets asset
        ON asset.symbol = p.asset_id
    WHERE p.close_time IS NULL
      AND p.asset_id IS NOT NULL
      AND COALESCE(p.volume, 0) > 0
    GROUP BY p.account_id, asset.id
    HAVING COUNT(DISTINCT p.currency) = 1
),
position_context AS (
    SELECT
        a.portfolio_id,
        pf.base_currency::varchar(3) AS base_currency,
        ap.account_id,
        ap.asset_id,
        ap.position_currency,
        COALESCE(lmp.market_currency, asset.currency)::varchar(3) AS market_currency,
        ap.volume,
        ap.average_open_price,
        ap.cost_basis,
        COALESCE(lmp.market_price, asset.market_price) AS market_price
    FROM aggregated_positions ap
    JOIN investory.accounts a
        ON a.id = ap.account_id
    JOIN investory.portfolios pf
        ON pf.id = a.portfolio_id
    JOIN investory.assets asset
        ON asset.id = ap.asset_id
    LEFT JOIN latest_market_price lmp
        ON lmp.asset_id = ap.asset_id
),
rates AS (
    SELECT
        pc.*,
        CASE
            WHEN pc.position_currency = pc.base_currency THEN 1::numeric
            WHEN position_direct.rate IS NOT NULL THEN position_direct.rate
            WHEN position_inverse.rate IS NOT NULL THEN 1::numeric / position_inverse.rate
            ELSE NULL::numeric
        END AS position_to_base_rate,
        CASE
            WHEN pc.market_currency = pc.base_currency THEN 1::numeric
            WHEN market_direct.rate IS NOT NULL THEN market_direct.rate
            WHEN market_inverse.rate IS NOT NULL THEN 1::numeric / market_inverse.rate
            ELSE NULL::numeric
        END AS market_to_base_rate
    FROM position_context pc
    LEFT JOIN canonical_fx position_direct
        ON position_direct.base = pc.position_currency
       AND position_direct.to_currency = pc.base_currency
    LEFT JOIN canonical_fx position_inverse
        ON position_inverse.base = pc.base_currency
       AND position_inverse.to_currency = pc.position_currency
    LEFT JOIN canonical_fx market_direct
        ON market_direct.base = pc.market_currency
       AND market_direct.to_currency = pc.base_currency
    LEFT JOIN canonical_fx market_inverse
        ON market_inverse.base = pc.base_currency
       AND market_inverse.to_currency = pc.market_currency
)
SELECT
    r.portfolio_id,
    r.base_currency,
    r.account_id,
    r.asset_id,
    r.position_currency,
    r.market_currency,
    r.volume,
    r.average_open_price,
    r.cost_basis,
    r.market_price,
    CASE
        WHEN r.market_price IS NULL THEN NULL::numeric
        ELSE r.volume * r.market_price
    END AS market_value,
    CASE
        WHEN r.market_price IS NULL THEN NULL::numeric
        WHEN r.position_currency <> r.market_currency THEN NULL::numeric
        ELSE (r.volume * r.market_price) - r.cost_basis
    END AS unrealized_pl,
    r.position_to_base_rate,
    r.market_to_base_rate,
    CASE
        WHEN r.position_to_base_rate IS NULL THEN NULL::numeric
        ELSE r.cost_basis * r.position_to_base_rate
    END AS cost_basis_in_base_currency,
    CASE
        WHEN r.market_price IS NULL OR r.market_to_base_rate IS NULL THEN NULL::numeric
        ELSE r.volume * r.market_price * r.market_to_base_rate
    END AS market_value_in_base_currency,
    CASE
        WHEN r.position_to_base_rate IS NULL
          OR r.market_price IS NULL
          OR r.market_to_base_rate IS NULL
          OR r.position_currency <> r.market_currency
            THEN NULL::numeric
        ELSE (r.volume * r.market_price * r.market_to_base_rate) - (r.cost_basis * r.position_to_base_rate)
    END AS unrealized_pl_in_base_currency,
    (
        (r.position_currency = r.base_currency OR r.position_to_base_rate IS NOT NULL)
        AND
        (r.market_currency = r.base_currency OR r.market_to_base_rate IS NOT NULL)
    ) AS fx_rate_available
FROM rates r;

CREATE MATERIALIZED VIEW investory.portfolio_asset_allocation AS
SELECT
    v.portfolio_id,
    v.base_currency,
    v.asset_id,
    asset.symbol AS asset_symbol,
    SUM(v.volume) AS total_volume,
    SUM(v.cost_basis_in_base_currency) AS cost_basis_in_base_currency,
    SUM(v.market_value_in_base_currency) AS total_value_in_base_currency,
    SUM(v.unrealized_pl_in_base_currency) AS unrealized_pl_in_base_currency,
    NOW() AS updated_at
FROM investory.v_open_position_values v
JOIN investory.assets asset
    ON asset.id = v.asset_id
GROUP BY v.portfolio_id, v.base_currency, v.asset_id
       , asset.symbol
WITH DATA;

CREATE UNIQUE INDEX ux_mv_portfolio_asset_allocation_key
    ON investory.portfolio_asset_allocation(portfolio_id, asset_id);

CREATE MATERIALIZED VIEW investory.symbol_performance AS
WITH latest_positions AS (
    SELECT
        v.portfolio_id,
        v.asset_id,
        SUM(v.unrealized_pl_in_base_currency) AS unrealized_profit,
        SUM(v.cost_basis_in_base_currency) AS cost_basis,
        SUM(v.market_value_in_base_currency) AS market_value,
        SUM(v.volume) AS total_volume
    FROM investory.v_open_position_values v
    GROUP BY v.portfolio_id, v.asset_id
),
cash_dividends AS (
    SELECT
        a.portfolio_id,
        asset.id AS asset_id,
        SUM(CASE WHEN co.operation = 'DIVIDEND' THEN COALESCE(co.amount, 0) ELSE 0 END) AS dividends,
        SUM(CASE WHEN co.operation = 'WITHHOLDING_TAX' THEN ABS(COALESCE(co.amount, 0)) ELSE 0 END) AS withholding_tax
    FROM investory.cash_operations co
    JOIN investory.accounts a
        ON a.id = co.account_id
    JOIN investory.assets asset
        ON asset.symbol = co.asset_id
    WHERE co.asset_id IS NOT NULL
    GROUP BY a.portfolio_id, asset.id
)
SELECT
    COALESCE(lp.portfolio_id, cd.portfolio_id) AS portfolio_id,
    COALESCE(lp.asset_id, cd.asset_id) AS asset_id,
    asset.symbol AS symbol,
    0::numeric AS closed_profit,
    COALESCE(lp.unrealized_profit, 0) AS unrealized_profit,
    COALESCE(lp.unrealized_profit, 0)
        + COALESCE(cd.dividends, 0)
        - COALESCE(cd.withholding_tax, 0) AS total_profit,
    COALESCE(cd.dividends, 0) AS dividends,
    COALESCE(cd.withholding_tax, 0) AS withholding_tax,
    COALESCE(lp.total_volume, 0) AS total_volume,
    COALESCE(lp.cost_basis, 0) AS cost_basis,
    COALESCE(lp.market_value, 0) AS market_value,
    NOW() AS updated_at
FROM latest_positions lp
FULL OUTER JOIN cash_dividends cd
    ON cd.portfolio_id = lp.portfolio_id
   AND cd.asset_id = lp.asset_id
JOIN investory.assets asset
    ON asset.id = COALESCE(lp.asset_id, cd.asset_id)
WITH DATA;

CREATE UNIQUE INDEX ux_mv_symbol_performance_key
    ON investory.symbol_performance(portfolio_id, asset_id);

CREATE OR REPLACE FUNCTION investory.refresh_reporting_views()
RETURNS VOID AS $$
BEGIN
    REFRESH MATERIALIZED VIEW investory.account_monthly_mv;
    REFRESH MATERIALIZED VIEW investory.portfolio_daily_mv;
    REFRESH MATERIALIZED VIEW investory.portfolio_monthly_mv;
    REFRESH MATERIALIZED VIEW investory.account_statistics;
    REFRESH MATERIALIZED VIEW investory.portfolio_kpi_summary;
    REFRESH MATERIALIZED VIEW investory.portfolio_currency_breakdown;
    REFRESH MATERIALIZED VIEW investory.portfolio_asset_allocation;
    REFRESH MATERIALIZED VIEW investory.symbol_performance;
END;
$$ LANGUAGE plpgsql;

SELECT investory.refresh_reporting_views();
