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
            WHEN normalized_category IN ('EXTERNAL_DEPOSIT', 'EXTERNAL_WITHDRAWAL')
                THEN amount
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
        THEN portfolio_flow_amount * fx_rate_to_base END
        AS portfolio_flow_amount_in_portfolio_base_currency
FROM effects;

COMMENT ON VIEW investory.normalized_cash_operation_flows IS
    'Single scoped-flow contract. Account flows include external and internal funding effects; portfolio flows include external deposits/withdrawals only. Paired XTB subaccount rows contribute once by parsed source/target direction.';

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
        SUM(portfolio_flow_amount_in_portfolio_base_currency)
            FILTER (WHERE portfolio_flow_amount_in_portfolio_base_currency > 0) AS deposits,
        SUM(-portfolio_flow_amount_in_portfolio_base_currency)
            FILTER (WHERE portfolio_flow_amount_in_portfolio_base_currency < 0) AS withdrawals
        ,COUNT(*) FILTER (
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
