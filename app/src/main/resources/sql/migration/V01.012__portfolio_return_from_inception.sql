SET search_path TO investory, public;
CREATE OR REPLACE VIEW investory.app_v_portfolio_performance_daily AS
 WITH portfolio_dates AS (
         SELECT DISTINCT a.portfolio_id,
            ad.snapshot_date
           FROM investory.account_daily ad
             JOIN investory.accounts a ON a.id = ad.account_id
          WHERE NOT a.cash_only
        ), account_rows AS (
         SELECT a.portfolio_id,
            p.base_currency,
            pd.snapshot_date,
            a.id AS account_id,
            ad.equity,
            ad.market_value,
                CASE
                    WHEN ad.snapshot_date = pd.snapshot_date THEN ad.deposits
                    ELSE 0::numeric
                END AS deposits,
                CASE
                    WHEN ad.snapshot_date = pd.snapshot_date THEN ad.withdrawals
                    ELSE 0::numeric
                END AS withdrawals,
                CASE
                    WHEN ad.snapshot_date = pd.snapshot_date THEN ad.dividends
                    ELSE 0::numeric
                END AS dividends,
                CASE
                    WHEN ad.snapshot_date = pd.snapshot_date THEN ad.interest
                    ELSE 0::numeric
                END AS interest,
                CASE
                    WHEN ad.snapshot_date = pd.snapshot_date THEN ad.fees
                    ELSE 0::numeric
                END AS fees,
                CASE
                    WHEN ad.snapshot_date = pd.snapshot_date THEN ad.taxes
                    ELSE 0::numeric
                END AS taxes,
                CASE
                    WHEN ad.snapshot_date = pd.snapshot_date THEN ad.realized_profit
                    ELSE 0::numeric
                END AS realized_profit,
                CASE
                    WHEN ad.snapshot_date = pd.snapshot_date THEN ad.daily_profit_amount
                    ELSE 0::numeric
                END AS total_profit,
            fx.fx_rate_to_target AS valuation_to_base_rate,
            fx.conversion_status
           FROM portfolio_dates pd
             JOIN investory.accounts a ON a.portfolio_id = pd.portfolio_id AND NOT a.cash_only
             JOIN investory.portfolios p ON p.id = a.portfolio_id
             JOIN LATERAL ( SELECT latest.id,
                    latest.account_id,
                    latest.snapshot_date,
                    latest.valuation_currency,
                    latest.cash_balance,
                    latest.market_value,
                    latest.equity,
                    latest.cost_base,
                    latest.unrealized_profit,
                    latest.dividends,
                    latest.interest,
                    latest.fees,
                    latest.taxes,
                    latest.deposits,
                    latest.withdrawals,
                    latest.realized_profit,
                    latest.daily_profit_amount,
                    latest.daily_return_pct,
                    latest.portfolio_weight,
                    latest.created_at,
                    latest.updated_at
                   FROM investory.account_daily latest
                  WHERE latest.account_id = a.id AND latest.snapshot_date <= pd.snapshot_date
                  ORDER BY latest.snapshot_date DESC
                 LIMIT 1) ad ON true
             CROSS JOIN LATERAL investory.resolve_fx_rate(pd.snapshot_date, ad.valuation_currency, p.base_currency) fx(source_currency, target_currency, fx_rate_to_target, source, rate_method, rate_source, source_rate_date, age_days, conversion_status)
        ), converted AS (
         SELECT account_rows.portfolio_id,
            account_rows.base_currency,
            account_rows.snapshot_date,
            account_rows.account_id,
                CASE
                    WHEN investory.fx_status_usable(account_rows.conversion_status) THEN account_rows.equity * account_rows.valuation_to_base_rate
                    ELSE NULL::numeric
                END AS equity,
                CASE
                    WHEN investory.fx_status_usable(account_rows.conversion_status) THEN account_rows.market_value * account_rows.valuation_to_base_rate
                    ELSE NULL::numeric
                END AS market_value,
                CASE
                    WHEN investory.fx_status_usable(account_rows.conversion_status) THEN account_rows.deposits * account_rows.valuation_to_base_rate
                    ELSE NULL::numeric
                END AS deposits,
                CASE
                    WHEN investory.fx_status_usable(account_rows.conversion_status) THEN account_rows.withdrawals * account_rows.valuation_to_base_rate
                    ELSE NULL::numeric
                END AS withdrawals,
                CASE
                    WHEN investory.fx_status_usable(account_rows.conversion_status) THEN account_rows.dividends * account_rows.valuation_to_base_rate
                    ELSE NULL::numeric
                END AS dividends,
                CASE
                    WHEN investory.fx_status_usable(account_rows.conversion_status) THEN account_rows.interest * account_rows.valuation_to_base_rate
                    ELSE NULL::numeric
                END AS interest,
                CASE
                    WHEN investory.fx_status_usable(account_rows.conversion_status) THEN account_rows.fees * account_rows.valuation_to_base_rate
                    ELSE NULL::numeric
                END AS fees,
                CASE
                    WHEN investory.fx_status_usable(account_rows.conversion_status) THEN account_rows.taxes * account_rows.valuation_to_base_rate
                    ELSE NULL::numeric
                END AS taxes,
                CASE
                    WHEN investory.fx_status_usable(account_rows.conversion_status) THEN account_rows.realized_profit * account_rows.valuation_to_base_rate
                    ELSE NULL::numeric
                END AS realized_profit,
                CASE
                    WHEN investory.fx_status_usable(account_rows.conversion_status) THEN account_rows.total_profit * account_rows.valuation_to_base_rate
                    ELSE NULL::numeric
                END AS total_profit,
            account_rows.conversion_status
           FROM account_rows
        ), account_boundaries AS (
         SELECT converted_1.portfolio_id,
            converted_1.base_currency,
            converted_1.snapshot_date,
            converted_1.account_id,
            converted_1.equity,
            converted_1.market_value,
            converted_1.deposits,
            converted_1.withdrawals,
            converted_1.dividends,
            converted_1.interest,
            converted_1.fees,
            converted_1.taxes,
            converted_1.realized_profit,
            converted_1.total_profit,
            converted_1.conversion_status,
            lag(converted_1.equity) OVER (PARTITION BY converted_1.account_id ORDER BY converted_1.snapshot_date) AS previous_equity,
            lag(converted_1.market_value) OVER (PARTITION BY converted_1.account_id ORDER BY converted_1.snapshot_date) AS previous_market_value,
                CASE
                    WHEN converted_1.market_value > 0::numeric AND COALESCE(lag(converted_1.market_value) OVER (PARTITION BY converted_1.account_id ORDER BY converted_1.snapshot_date), 0::numeric) = 0::numeric THEN GREATEST(converted_1.equity - COALESCE(lag(converted_1.equity) OVER (PARTITION BY converted_1.account_id ORDER BY converted_1.snapshot_date), 0::numeric) - COALESCE(converted_1.deposits, 0::numeric) + COALESCE(converted_1.withdrawals, 0::numeric), 0::numeric)
                    ELSE 0::numeric
                END AS initialization_adjustment
           FROM converted converted_1
        ), performance_flows AS (
         SELECT a.portfolio_id,
            nco.date::date AS snapshot_date,
                CASE
                    WHEN count(*) FILTER (WHERE NOT investory.fx_status_usable(nco.portfolio_conversion_status)) > 0 THEN NULL::numeric
                    ELSE sum(nco.performance_flow_amount_in_portfolio_base_currency) FILTER (WHERE nco.performance_flow_amount_in_portfolio_base_currency > 0::numeric)
                END AS deposits,
                CASE
                    WHEN count(*) FILTER (WHERE NOT investory.fx_status_usable(nco.portfolio_conversion_status)) > 0 THEN NULL::numeric
                    ELSE sum(- nco.performance_flow_amount_in_portfolio_base_currency) FILTER (WHERE nco.performance_flow_amount_in_portfolio_base_currency < 0::numeric)
                END AS withdrawals,
            count(*) FILTER (WHERE NOT investory.fx_status_usable(nco.portfolio_conversion_status)) AS missing_flow_fx_count
           FROM investory.app_v_normalized_cash_operation_flows nco
             JOIN investory.accounts a ON a.id = nco.account_id AND NOT a.cash_only
          GROUP BY a.portfolio_id, (nco.date::date)
        )
 SELECT converted.portfolio_id,
    converted.snapshot_date,
    converted.base_currency,
        CASE
            WHEN count(*) FILTER (WHERE NOT investory.fx_status_usable(converted.conversion_status)) > 0 THEN NULL::numeric
            ELSE sum(converted.equity)
        END AS equity,
        CASE
            WHEN count(*) FILTER (WHERE NOT investory.fx_status_usable(converted.conversion_status)) > 0 OR max(COALESCE(ef.missing_flow_fx_count, 0::bigint)) > 0 THEN NULL::numeric
            ELSE COALESCE(max(ef.deposits), 0::numeric)
        END AS deposits,
        CASE
            WHEN count(*) FILTER (WHERE NOT investory.fx_status_usable(converted.conversion_status)) > 0 OR max(COALESCE(ef.missing_flow_fx_count, 0::bigint)) > 0 THEN NULL::numeric
            ELSE COALESCE(max(ef.withdrawals), 0::numeric)
        END AS withdrawals,
        CASE
            WHEN count(*) FILTER (WHERE NOT investory.fx_status_usable(converted.conversion_status)) > 0 THEN NULL::numeric
            ELSE sum(converted.dividends)
        END AS dividends,
        CASE
            WHEN count(*) FILTER (WHERE NOT investory.fx_status_usable(converted.conversion_status)) > 0 THEN NULL::numeric
            ELSE sum(converted.interest)
        END AS interest,
        CASE
            WHEN count(*) FILTER (WHERE NOT investory.fx_status_usable(converted.conversion_status)) > 0 THEN NULL::numeric
            ELSE sum(converted.fees)
        END AS fees,
        CASE
            WHEN count(*) FILTER (WHERE NOT investory.fx_status_usable(converted.conversion_status)) > 0 THEN NULL::numeric
            ELSE sum(converted.taxes)
        END AS taxes,
        CASE
            WHEN count(*) FILTER (WHERE NOT investory.fx_status_usable(converted.conversion_status)) > 0 THEN NULL::numeric
            ELSE sum(converted.realized_profit)
        END AS realized_profit,
        CASE
            WHEN count(*) FILTER (WHERE NOT investory.fx_status_usable(converted.conversion_status)) > 0 THEN NULL::numeric
            ELSE sum(converted.equity)
                - lag(sum(converted.equity)) OVER (PARTITION BY converted.portfolio_id ORDER BY converted.snapshot_date)
                - COALESCE(max(ef.deposits), 0::numeric)
                + COALESCE(max(ef.withdrawals), 0::numeric)
                - sum(converted.initialization_adjustment)
        END AS total_profit,
        CASE
            WHEN count(*) FILTER (WHERE NOT investory.fx_status_usable(converted.conversion_status)) > 0 THEN NULL::numeric
            ELSE sum(converted.initialization_adjustment)
        END AS initialization_adjustment,
        CASE
            WHEN count(*) FILTER (WHERE NOT investory.fx_status_usable(converted.conversion_status)) > 0 OR max(COALESCE(ef.missing_flow_fx_count, 0::bigint)) > 0 THEN NULL::numeric
            WHEN lag(sum(converted.equity)) OVER (PARTITION BY converted.portfolio_id ORDER BY converted.snapshot_date) IS NULL THEN NULL::numeric
            ELSE (sum(converted.equity)
                - lag(sum(converted.equity)) OVER (PARTITION BY converted.portfolio_id ORDER BY converted.snapshot_date)
                - COALESCE(max(ef.deposits), 0::numeric)
                + COALESCE(max(ef.withdrawals), 0::numeric)
                - sum(converted.initialization_adjustment)) / NULLIF(lag(sum(converted.equity)) OVER (PARTITION BY converted.portfolio_id ORDER BY converted.snapshot_date) + COALESCE(max(ef.deposits), 0::numeric) - COALESCE(max(ef.withdrawals), 0::numeric) + sum(converted.initialization_adjustment), 0::numeric)
        END AS daily_return_pct
   FROM account_boundaries converted
     LEFT JOIN performance_flows ef ON ef.portfolio_id = converted.portfolio_id AND ef.snapshot_date = converted.snapshot_date
  GROUP BY converted.portfolio_id, converted.snapshot_date, converted.base_currency;
