-- Final portfolio reporting views and their canonical reconstruction projections.
-- Consolidated final schema; historical repair steps are not replayed.

SET search_path TO investory, public;

CREATE MATERIALIZED VIEW investory.app_v_account_monthly AS
 WITH source_rows AS (
         SELECT ad.account_id,
            (date_trunc('month'::text, (ad.snapshot_date)::timestamp with time zone))::date AS month,
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
            p.base_currency,
            fx.fx_rate_to_target,
            fx.conversion_status,
            row_number() OVER (PARTITION BY ad.account_id, (date_trunc('month'::text, (ad.snapshot_date)::timestamp with time zone)) ORDER BY ad.snapshot_date DESC) AS rn_last
           FROM (((investory.account_daily ad
             JOIN investory.accounts a ON ((a.id = ad.account_id)))
             JOIN investory.portfolios p ON ((p.id = a.portfolio_id)))
             CROSS JOIN LATERAL investory.resolve_fx_rate(ad.snapshot_date, ad.valuation_currency, p.base_currency) fx(source_currency, target_currency, fx_rate_to_target, source, rate_method, rate_source, source_rate_date, age_days, conversion_status))
        ), month_rows AS (
         SELECT source_rows.account_id,
            source_rows.month,
            source_rows.snapshot_date,
                CASE
                    WHEN investory.fx_status_usable(source_rows.conversion_status) THEN (source_rows.equity * source_rows.fx_rate_to_target)
                    ELSE NULL::numeric
                END AS equity,
                CASE
                    WHEN investory.fx_status_usable(source_rows.conversion_status) THEN (source_rows.deposits * source_rows.fx_rate_to_target)
                    ELSE NULL::numeric
                END AS deposits,
                CASE
                    WHEN investory.fx_status_usable(source_rows.conversion_status) THEN (source_rows.withdrawals * source_rows.fx_rate_to_target)
                    ELSE NULL::numeric
                END AS withdrawals,
                CASE
                    WHEN investory.fx_status_usable(source_rows.conversion_status) THEN (source_rows.dividends * source_rows.fx_rate_to_target)
                    ELSE NULL::numeric
                END AS dividends,
                CASE
                    WHEN investory.fx_status_usable(source_rows.conversion_status) THEN (source_rows.interest * source_rows.fx_rate_to_target)
                    ELSE NULL::numeric
                END AS interest,
                CASE
                    WHEN investory.fx_status_usable(source_rows.conversion_status) THEN (source_rows.fees * source_rows.fx_rate_to_target)
                    ELSE NULL::numeric
                END AS fees,
                CASE
                    WHEN investory.fx_status_usable(source_rows.conversion_status) THEN (source_rows.taxes * source_rows.fx_rate_to_target)
                    ELSE NULL::numeric
                END AS taxes,
                CASE
                    WHEN investory.fx_status_usable(source_rows.conversion_status) THEN (source_rows.realized_profit * source_rows.fx_rate_to_target)
                    ELSE NULL::numeric
                END AS realized_profit,
                CASE
                    WHEN investory.fx_status_usable(source_rows.conversion_status) THEN (source_rows.daily_profit_amount * source_rows.fx_rate_to_target)
                    ELSE NULL::numeric
                END AS daily_profit_amount,
            source_rows.daily_return_pct,
            source_rows.base_currency AS valuation_currency,
            source_rows.rn_last
           FROM source_rows
        ), monthly AS (
         SELECT mr.account_id,
            mr.month,
            min(mr.snapshot_date) AS first_date,
            max(mr.snapshot_date) AS end_date,
            COALESCE(max(
                CASE
                    WHEN (mr.rn_last = 1) THEN mr.equity
                    ELSE NULL::numeric
                END), (0)::numeric) AS closing_equity,
            (COALESCE(max((
                CASE
                    WHEN (mr.rn_last = 1) THEN mr.valuation_currency
                    ELSE NULL::character varying
                END)::text), 'USD'::text))::character varying(3) AS valuation_currency,
            COALESCE(sum(mr.deposits), (0)::numeric) AS deposits,
            COALESCE(sum(mr.withdrawals), (0)::numeric) AS withdrawals,
            COALESCE(sum(mr.dividends), (0)::numeric) AS dividends,
            COALESCE(sum(mr.interest), (0)::numeric) AS interest,
            COALESCE(sum(mr.fees), (0)::numeric) AS fees,
            COALESCE(sum(mr.taxes), (0)::numeric) AS taxes,
            COALESCE(sum(mr.realized_profit), (0)::numeric) AS realized_profit,
            COALESCE(sum(mr.daily_profit_amount), (0)::numeric) AS canonical_profit,
                CASE
                    WHEN (count(mr.daily_return_pct) = 0) THEN NULL::numeric
                    WHEN bool_or((mr.daily_return_pct <= ('-1'::integer)::numeric)) THEN (- (1)::numeric)
                    ELSE (exp(sum(ln(((1)::numeric + mr.daily_return_pct))) FILTER (WHERE ((mr.daily_return_pct IS NOT NULL) AND (mr.daily_return_pct > ('-1'::integer)::numeric)))) - (1)::numeric)
                END AS compounded_monthly_return
           FROM month_rows mr
          GROUP BY mr.account_id, mr.month
        )
 SELECT account_id,
    month,
    first_date,
    end_date,
    COALESCE(lag(closing_equity) OVER (PARTITION BY account_id ORDER BY month), (0)::numeric) AS opening_equity,
    closing_equity,
    valuation_currency,
    deposits,
    withdrawals,
    dividends,
    interest,
    fees,
    taxes,
    realized_profit,
    canonical_profit AS total_profit,
    compounded_monthly_return,
    now() AS updated_at
   FROM monthly m;

CREATE MATERIALIZED VIEW investory.app_v_normalized_cash_operations AS
 WITH classified AS (
         SELECT co.id AS operation_id,
            co.account_id,
            a.portfolio_id,
            a.currency AS account_currency,
            co.currency,
            p.base_currency,
            (co.operation)::character varying(64) AS raw_operation,
            co.asset_id,
            co.amount,
            co.comment,
            co.date,
            co.execution_fx_base,
            co.execution_fx_to_currency,
            co.execution_fx_rate,
            co.execution_fx_observed_at,
            co.execution_fx_source,
            (date_trunc('month'::text, (co.date AT TIME ZONE 'Europe/Warsaw'::text)))::date AS rate_month,
            (
                CASE
                    WHEN ((co.operation = 'DEPOSIT'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'transfer out operation on account%'::text)) THEN 'INTERNAL_TRANSFER_OUT'::text
                    WHEN ((co.operation = 'DEPOSIT'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'transfer in operation on account%'::text)) THEN 'INTERNAL_TRANSFER_IN'::text
                    WHEN ((co.operation = 'DEPOSIT'::investory.cash_operation_type) AND (COALESCE(co.amount, (0)::numeric) = (0)::numeric)) THEN 'UNCLASSIFIED'::text
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~* '(full call|early redemption|redemption)'::text) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ '%per bond%'::text) AND (co.asset_id IS NOT NULL)) THEN 'BOND_REDEMPTION'::text
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) = 'cash transfer'::text) AND (co.amount > (0)::numeric)) THEN 'EXTERNAL_DEPOSIT'::text
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'cash transfer |%'::text) AND (co.amount > (0)::numeric)) THEN 'EXTERNAL_DEPOSIT'::text
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'cash transfer|%'::text) AND (co.amount > (0)::numeric)) THEN 'EXTERNAL_DEPOSIT'::text
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) = 'cash transfer'::text) AND (co.amount < (0)::numeric)) THEN 'EXTERNAL_WITHDRAWAL'::text
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'cash transfer |%'::text) AND (co.amount < (0)::numeric)) THEN 'EXTERNAL_WITHDRAWAL'::text
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'cash transfer|%'::text) AND (co.amount < (0)::numeric)) THEN 'EXTERNAL_WITHDRAWAL'::text
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND ((lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'net amount in base from forex trade:%'::text) OR (lower((COALESCE(co.comment, ''::character varying))::text) ~~ '%ibkrrawtype=forex trade component%'::text))) THEN 'FX_CONVERSION'::text
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'currency conversion,%from ta:%to:%'::text) AND (co.amount >= (0)::numeric)) THEN 'INTERNAL_TRANSFER_IN'::text
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'currency conversion,%from ta:%to:%'::text) AND (co.amount < (0)::numeric)) THEN 'INTERNAL_TRANSFER_OUT'::text
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'currency conversion,%'::text)) THEN 'FX_CONVERSION'::text
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'transfer from % to %'::text) AND (co.amount >= (0)::numeric)) THEN 'INTERNAL_TRANSFER_IN'::text
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'transfer from % to %'::text) AND (co.amount < (0)::numeric)) THEN 'INTERNAL_TRANSFER_OUT'::text
                    WHEN (co.operation = 'SUBACCOUNT_TRANSFER'::investory.cash_operation_type) THEN 'INTERNAL_BOOKKEEPING'::text
                    WHEN ((co.operation = 'DIVIDEND'::investory.cash_operation_type) AND (co.amount < (0)::numeric)) THEN 'DIVIDEND_REVERSAL'::text
                    WHEN (co.operation = 'DIVIDEND'::investory.cash_operation_type) THEN 'DIVIDEND'::text
                    WHEN ((co.operation = 'FREE_FUNDS_INTEREST'::investory.cash_operation_type) AND (co.amount < (0)::numeric)) THEN 'INTEREST_REVERSAL'::text
                    WHEN (co.operation = 'FREE_FUNDS_INTEREST'::investory.cash_operation_type) THEN 'INTEREST'::text
                    WHEN ((co.operation = 'WITHHOLDING_TAX'::investory.cash_operation_type) AND (co.amount > (0)::numeric)) THEN 'WITHHOLDING_TAX_REVERSAL'::text
                    WHEN (co.operation = 'WITHHOLDING_TAX'::investory.cash_operation_type) THEN 'WITHHOLDING_TAX'::text
                    WHEN (co.operation = ANY (ARRAY['FREE_FUNDS_INTEREST_TAX'::investory.cash_operation_type, 'TRANSACTION_TAX'::investory.cash_operation_type, 'STAMP_DUTY'::investory.cash_operation_type])) THEN 'OTHER_TAX'::text
                    WHEN (co.operation = ANY (ARRAY['COMMISSION'::investory.cash_operation_type, 'SEC_FEE'::investory.cash_operation_type, 'SWAP'::investory.cash_operation_type])) THEN 'FEE'::text
                    WHEN (co.operation = 'STOCK_PURCHASE'::investory.cash_operation_type) THEN 'TRADE_PURCHASE'::text
                    WHEN (co.operation = 'STOCK_SELL'::investory.cash_operation_type) THEN 'TRADE_SALE'::text
                    WHEN (co.operation = ANY (ARRAY['CLOSE_TRADE'::investory.cash_operation_type, 'ROLLOVER'::investory.cash_operation_type])) THEN 'REALIZED_TRADE_RESULT'::text
                    WHEN (co.operation = 'CORRECTION'::investory.cash_operation_type) THEN 'CORRECTION'::text
                    WHEN ((co.operation = 'DEPOSIT'::investory.cash_operation_type) AND (co.amount > (0)::numeric)) THEN 'EXTERNAL_DEPOSIT'::text
                    WHEN ((co.operation = 'WITHDRAWAL'::investory.cash_operation_type) AND (co.amount < (0)::numeric)) THEN 'EXTERNAL_WITHDRAWAL'::text
                    ELSE 'UNCLASSIFIED'::text
                END)::character varying(64) AS normalized_category,
            (
                CASE
                    WHEN (co.amount > (0)::numeric) THEN 'INFLOW'::text
                    WHEN (co.amount < (0)::numeric) THEN 'OUTFLOW'::text
                    ELSE 'NEUTRAL'::text
                END)::character varying(16) AS economic_direction,
            (
                CASE
                    WHEN ((co.operation = 'DEPOSIT'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'transfer out operation on account%'::text)) THEN 'XTB_ACCOUNT_TRANSFER_OUT'::text
                    WHEN ((co.operation = 'DEPOSIT'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'transfer in operation on account%'::text)) THEN 'XTB_ACCOUNT_TRANSFER_IN'::text
                    WHEN ((co.operation = 'DEPOSIT'::investory.cash_operation_type) AND (COALESCE(co.amount, (0)::numeric) = (0)::numeric)) THEN 'ZERO_DEPOSIT'::text
                    WHEN ((co.operation = 'DEPOSIT'::investory.cash_operation_type) AND (co.amount < (0)::numeric)) THEN 'RAW_DEPOSIT_NEGATIVE_REVIEW'::text
                    WHEN ((co.operation = 'DEPOSIT'::investory.cash_operation_type) AND (co.amount > (0)::numeric)) THEN 'RAW_DEPOSIT'::text
                    WHEN ((co.operation = 'WITHDRAWAL'::investory.cash_operation_type) AND (co.amount < (0)::numeric)) THEN 'RAW_WITHDRAWAL'::text
                    WHEN ((co.operation = 'WITHDRAWAL'::investory.cash_operation_type) AND (co.amount > (0)::numeric)) THEN 'RAW_WITHDRAWAL_POSITIVE_REVIEW'::text
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~* '(full call|early redemption|redemption)'::text) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ '%per bond%'::text) AND (co.asset_id IS NOT NULL)) THEN 'IBKR_BOND_REDEMPTION'::text
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'cash transfer%'::text) AND (co.amount > (0)::numeric)) THEN 'EXTERNAL_DEPOSIT'::text
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'cash transfer%'::text) AND (co.amount < (0)::numeric)) THEN 'EXTERNAL_WITHDRAWAL'::text
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND ((lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'net amount in base from forex trade:%'::text) OR (lower((COALESCE(co.comment, ''::character varying))::text) ~~ '%ibkrrawtype=forex trade component%'::text))) THEN 'FX_CONVERSION'::text
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'currency conversion,%from ta:%to:%'::text)) THEN 'ACCOUNT_TRANSFER'::text
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'currency conversion,%'::text)) THEN 'FX_CONVERSION'::text
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'transfer from % to %'::text)) THEN 'ACCOUNT_TRANSFER'::text
                    WHEN (co.operation = 'SUBACCOUNT_TRANSFER'::investory.cash_operation_type) THEN 'SUBACCOUNT_TRANSFER'::text
                    WHEN ((co.operation = 'DIVIDEND'::investory.cash_operation_type) AND (co.amount < (0)::numeric)) THEN 'DIVIDEND_REVERSAL'::text
                    WHEN (co.operation = 'DIVIDEND'::investory.cash_operation_type) THEN 'DIVIDEND'::text
                    WHEN ((co.operation = 'FREE_FUNDS_INTEREST'::investory.cash_operation_type) AND (co.amount < (0)::numeric)) THEN 'INTEREST_REVERSAL'::text
                    WHEN (co.operation = 'FREE_FUNDS_INTEREST'::investory.cash_operation_type) THEN 'INTEREST'::text
                    WHEN ((co.operation = 'WITHHOLDING_TAX'::investory.cash_operation_type) AND (co.amount > (0)::numeric)) THEN 'WITHHOLDING_TAX_REVERSAL'::text
                    WHEN (co.operation = 'WITHHOLDING_TAX'::investory.cash_operation_type) THEN 'WITHHOLDING_TAX'::text
                    WHEN (co.operation = ANY (ARRAY['FREE_FUNDS_INTEREST_TAX'::investory.cash_operation_type, 'TRANSACTION_TAX'::investory.cash_operation_type, 'STAMP_DUTY'::investory.cash_operation_type])) THEN 'TAX'::text
                    WHEN (co.operation = ANY (ARRAY['COMMISSION'::investory.cash_operation_type, 'SEC_FEE'::investory.cash_operation_type, 'SWAP'::investory.cash_operation_type])) THEN 'FEE'::text
                    WHEN (co.operation = 'STOCK_PURCHASE'::investory.cash_operation_type) THEN 'TRADE_PURCHASE'::text
                    WHEN (co.operation = 'STOCK_SELL'::investory.cash_operation_type) THEN 'TRADE_SALE'::text
                    WHEN (co.operation = ANY (ARRAY['CLOSE_TRADE'::investory.cash_operation_type, 'ROLLOVER'::investory.cash_operation_type])) THEN 'REALIZED_TRADE_RESULT'::text
                    WHEN (co.operation = 'CORRECTION'::investory.cash_operation_type) THEN 'UNCLASSIFIED_CORRECTION'::text
                    ELSE 'UNCLASSIFIED'::text
                END)::character varying(64) AS normalized_subtype,
            (
                CASE
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~* '(full call|early redemption|redemption)'::text) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ '%per bond%'::text) AND (co.asset_id IS NOT NULL)) THEN 'IBKR fixed-income redemption settlement'::text
                    WHEN ((co.operation = 'DEPOSIT'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'transfer out operation on account%'::text)) THEN 'explicit transfer out comment'::text
                    WHEN ((co.operation = 'DEPOSIT'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'transfer in operation on account%'::text)) THEN 'explicit transfer in comment'::text
                    WHEN ((co.operation = 'DEPOSIT'::investory.cash_operation_type) AND (COALESCE(co.amount, (0)::numeric) = (0)::numeric)) THEN 'zero raw deposit requires review'::text
                    WHEN ((co.operation = 'DEPOSIT'::investory.cash_operation_type) AND (co.amount < (0)::numeric)) THEN 'negative raw deposit requires review'::text
                    WHEN ((co.operation = 'WITHDRAWAL'::investory.cash_operation_type) AND (co.amount > (0)::numeric)) THEN 'positive raw withdrawal requires review'::text
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) = 'cash transfer'::text)) THEN 'explicit IBKR cash transfer comment'::text
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND ((lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'net amount in base from forex trade:%'::text) OR (lower((COALESCE(co.comment, ''::character varying))::text) ~~ '%ibkrrawtype=forex trade component%'::text))) THEN 'explicit IBKR forex trade component comment'::text
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'currency conversion,%'::text)) THEN 'currency conversion comment'::text
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'transfer from % to %'::text)) THEN 'transfer from X to Y comment'::text
                    WHEN (co.operation = 'SUBACCOUNT_TRANSFER'::investory.cash_operation_type) THEN 'raw subaccount transfer'::text
                    WHEN ((co.operation = 'DIVIDEND'::investory.cash_operation_type) AND (co.amount < (0)::numeric)) THEN 'negative/correction dividend'::text
                    WHEN ((co.operation = 'FREE_FUNDS_INTEREST'::investory.cash_operation_type) AND (co.amount < (0)::numeric)) THEN 'negative/correction interest'::text
                    WHEN ((co.operation = 'WITHHOLDING_TAX'::investory.cash_operation_type) AND (co.amount > (0)::numeric)) THEN 'positive/correction withholding tax'::text
                    WHEN (co.operation = 'CORRECTION'::investory.cash_operation_type) THEN 'raw correction requires review'::text
                    ELSE 'raw operation mapping'::text
                END)::character varying(255) AS classification_reason,
                CASE
                    WHEN ((co.operation = 'DEPOSIT'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'transfer out operation on account%'::text)) THEN false
                    WHEN ((co.operation = 'DEPOSIT'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'transfer in operation on account%'::text)) THEN false
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'cash transfer%'::text)) THEN true
                    WHEN ((co.operation = 'DEPOSIT'::investory.cash_operation_type) AND (co.amount > (0)::numeric)) THEN true
                    WHEN ((co.operation = 'WITHDRAWAL'::investory.cash_operation_type) AND (co.amount < (0)::numeric)) THEN true
                    ELSE false
                END AS is_external_flow,
                CASE
                    WHEN ((co.operation = 'DEPOSIT'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'transfer out operation on account%'::text)) THEN true
                    WHEN ((co.operation = 'DEPOSIT'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'transfer in operation on account%'::text)) THEN true
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'currency conversion,%from ta:%to:%'::text)) THEN true
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'transfer from % to %'::text)) THEN true
                    WHEN (co.operation = 'SUBACCOUNT_TRANSFER'::investory.cash_operation_type) THEN true
                    ELSE false
                END AS is_internal_transfer,
                CASE
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND ((lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'net amount in base from forex trade:%'::text) OR (lower((COALESCE(co.comment, ''::character varying))::text) ~~ '%ibkrrawtype=forex trade component%'::text))) THEN true
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'currency conversion,%from ta:%to:%'::text)) THEN false
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ 'currency conversion,%'::text)) THEN true
                    ELSE false
                END AS is_fx_conversion,
                CASE
                    WHEN (co.operation = ANY (ARRAY['STOCK_PURCHASE'::investory.cash_operation_type, 'STOCK_SELL'::investory.cash_operation_type, 'CLOSE_TRADE'::investory.cash_operation_type, 'ROLLOVER'::investory.cash_operation_type])) THEN true
                    WHEN ((co.operation = 'TRANSFER'::investory.cash_operation_type) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~* '(full call|early redemption|redemption)'::text) AND (lower((COALESCE(co.comment, ''::character varying))::text) ~~ '%per bond%'::text) AND (co.asset_id IS NOT NULL)) THEN true
                    ELSE false
                END AS is_trade_cash_flow,
                CASE
                    WHEN (co.operation = 'CORRECTION'::investory.cash_operation_type) THEN true
                    ELSE false
                END AS is_correction,
                CASE
                    WHEN ((co.operation = 'DIVIDEND'::investory.cash_operation_type) AND (co.amount < (0)::numeric)) THEN true
                    WHEN ((co.operation = 'FREE_FUNDS_INTEREST'::investory.cash_operation_type) AND (co.amount < (0)::numeric)) THEN true
                    WHEN ((co.operation = 'WITHHOLDING_TAX'::investory.cash_operation_type) AND (co.amount > (0)::numeric)) THEN true
                    WHEN ((co.operation = 'WITHDRAWAL'::investory.cash_operation_type) AND (co.amount > (0)::numeric)) THEN true
                    ELSE false
                END AS is_reversal
           FROM (((investory.cash_operations co
             JOIN investory.accounts a ON ((a.id = co.account_id)))
             JOIN investory.portfolios p ON ((p.id = a.portfolio_id)))
             LEFT JOIN investory.assets excluded_asset ON ((excluded_asset.id = co.asset_id)))
          WHERE ((co.asset_id IS NULL) OR (excluded_asset.exclude_from_import = false))
        ), port_needed AS (
         SELECT DISTINCT classified.portfolio_id,
            ((classified.date AT TIME ZONE 'Europe/Warsaw'::text))::date AS vdate,
            classified.currency,
            classified.base_currency
           FROM classified
        ), port_resolved AS (
         SELECT n.portfolio_id AS k_portfolio_id,
            n.vdate AS k_vdate,
            n.currency AS k_currency,
            fx_1.fx_rate_to_target AS fx_rate_to_base,
            fx_1.source,
            fx_1.source_rate_date,
            fx_1.age_days,
            fx_1.conversion_status
           FROM ((port_needed n
             JOIN investory.portfolios p ON ((p.id = n.portfolio_id)))
             CROSS JOIN LATERAL investory.resolve_fx_rate(n.vdate, n.currency, p.base_currency) fx_1(source_currency, target_currency, fx_rate_to_target, source, rate_method, rate_source, source_rate_date, age_days, conversion_status))
        ), acct_needed AS (
         SELECT DISTINCT ((classified.date AT TIME ZONE 'Europe/Warsaw'::text))::date AS vdate,
            classified.currency,
            classified.account_currency
           FROM classified
        ), acct_resolved AS (
         SELECT n.vdate AS k_vdate,
            n.currency AS k_currency,
            n.account_currency AS k_account_currency,
            r.fx_rate_to_target,
            r.source,
            r.source_rate_date,
            r.age_days,
            r.conversion_status
           FROM (acct_needed n
             CROSS JOIN LATERAL investory.resolve_fx_rate(n.vdate, n.currency, n.account_currency) r(source_currency, target_currency, fx_rate_to_target, source, rate_method, rate_source, source_rate_date, age_days, conversion_status))
        ), txn_needed AS (
         SELECT DISTINCT classified.date,
            classified.currency,
            classified.base_currency
           FROM classified
          WHERE classified.is_fx_conversion
        ), txn_resolved AS (
         SELECT n.date AS k_date,
            n.currency AS k_currency,
            n.base_currency AS k_base_currency,
            r.fx_rate_to_target,
            r.source,
            r.source_rate_date,
            r.age_days,
            r.conversion_status
           FROM (txn_needed n
             CROSS JOIN LATERAL investory.resolve_transaction_fx_rate(n.date, n.currency, n.base_currency, 'TRANSACTION'::character varying) r(source_currency, target_currency, fx_rate_to_target, source, rate_method, rate_source, source_rate_date, age_days, conversion_status))
        ), fx AS (
         SELECT c.operation_id,
            c.account_id,
            c.portfolio_id,
            c.account_currency,
            c.currency,
            c.base_currency,
            c.raw_operation,
            c.asset_id,
            c.amount,
            c.comment,
            c.date,
            c.execution_fx_base,
            c.execution_fx_to_currency,
            c.execution_fx_rate,
            c.execution_fx_observed_at,
            c.execution_fx_source,
            c.rate_month,
            c.normalized_category,
            c.economic_direction,
            c.normalized_subtype,
            c.classification_reason,
            c.is_external_flow,
            c.is_internal_transfer,
            c.is_fx_conversion,
            c.is_trade_cash_flow,
            c.is_correction,
            c.is_reversal,
                CASE
                    WHEN c.is_fx_conversion THEN tr.fx_rate_to_target
                    ELSE pr.fx_rate_to_base
                END AS fx_rate_to_base,
                CASE
                    WHEN c.is_fx_conversion THEN tr.source
                    ELSE pr.source
                END AS portfolio_fx_source,
                CASE
                    WHEN c.is_fx_conversion THEN tr.source_rate_date
                    ELSE pr.source_rate_date
                END AS portfolio_source_rate_date,
                CASE
                    WHEN c.is_fx_conversion THEN tr.age_days
                    ELSE pr.age_days
                END AS portfolio_fx_age_days,
                CASE
                    WHEN c.is_fx_conversion THEN tr.conversion_status
                    ELSE pr.conversion_status
                END AS portfolio_conversion_status,
            ar.fx_rate_to_target AS fx_rate_to_account_currency,
            ar.source AS account_fx_source,
            ar.source_rate_date AS account_source_rate_date,
            ar.age_days AS account_fx_age_days,
            ar.conversion_status AS account_conversion_status
           FROM (((classified c
             LEFT JOIN port_resolved pr ON (((pr.k_portfolio_id = c.portfolio_id) AND (pr.k_vdate = ((c.date AT TIME ZONE 'Europe/Warsaw'::text))::date) AND ((pr.k_currency)::text = (c.currency)::text))))
             LEFT JOIN acct_resolved ar ON (((ar.k_vdate = ((c.date AT TIME ZONE 'Europe/Warsaw'::text))::date) AND ((ar.k_currency)::text = (c.currency)::text) AND ((ar.k_account_currency)::text = (c.account_currency)::text))))
             LEFT JOIN txn_resolved tr ON (((tr.k_date = c.date) AND ((tr.k_currency)::text = (c.currency)::text) AND ((tr.k_base_currency)::text = (c.base_currency)::text))))
        )
 SELECT operation_id,
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
            WHEN investory.fx_status_usable(portfolio_conversion_status) THEN (amount * fx_rate_to_base)
            ELSE NULL::numeric
        END AS amount_in_portfolio_base_currency,
        CASE
            WHEN investory.fx_status_usable(portfolio_conversion_status) THEN (amount * fx_rate_to_base)
            ELSE NULL::numeric
        END AS amount_in_base_currency,
        CASE
            WHEN investory.fx_status_usable(account_conversion_status) THEN (amount * fx_rate_to_account_currency)
            ELSE NULL::numeric
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
    NULL::character varying(64) AS related_operation_id,
    NULL::character varying(64) AS transfer_group_id,
    (
        CASE
            WHEN ((normalized_category)::text = ANY (ARRAY[('INTERNAL_TRANSFER_IN'::character varying)::text, ('INTERNAL_TRANSFER_OUT'::character varying)::text, ('INTERNAL_BOOKKEEPING'::character varying)::text, ('FX_CONVERSION'::character varying)::text])) THEN 'UNMATCHED'::text
            ELSE 'NOT_REQUIRED'::text
        END)::character varying(32) AS pairing_status,
    classification_reason,
    'sql-v2026-08-09-ibkr-bond'::character varying(64) AS classification_version,
    portfolio_conversion_status AS conversion_status
   FROM fx;

CREATE VIEW investory.app_v_normalized_cash_operation_flows AS
 WITH parsed AS (
         SELECT nco.operation_id,
            nco.account_id,
            nco.portfolio_id,
            nco.account_currency,
            nco.currency,
            nco.portfolio_base_currency,
            nco.base_currency,
            nco.raw_operation,
            nco.normalized_category,
            nco.normalized_subtype,
            nco.economic_direction,
            nco.is_external_flow,
            nco.is_internal_transfer,
            nco.is_fx_conversion,
            nco.is_trade_cash_flow,
            nco.is_correction,
            nco.is_reversal,
            nco.asset_id,
            nco.amount,
            nco.amount_in_portfolio_base_currency,
            nco.amount_in_base_currency,
            nco.amount_in_account_currency,
            nco.comment,
            nco.date,
            nco.rate_month,
            nco.fx_rate_to_base,
            nco.portfolio_fx_source,
            nco.portfolio_source_rate_date,
            nco.portfolio_fx_age_days,
            nco.portfolio_conversion_status,
            nco.fx_rate_to_account_currency,
            nco.account_fx_source,
            nco.account_source_rate_date,
            nco.account_fx_age_days,
            nco.account_conversion_status,
            nco.related_operation_id,
            nco.transfer_group_id,
            nco.pairing_status,
            nco.classification_reason,
            nco.classification_version,
            nco.conversion_status,
                CASE
                    WHEN (((nco.normalized_category)::text = 'INTERNAL_BOOKKEEPING'::text) AND ((nco.comment)::text ~* 'transfer from [0-9]+ to [0-9]+'::text)) THEN ("substring"((nco.comment)::text, '(?i)transfer from ([0-9]+)'::text))::bigint
                    ELSE NULL::bigint
                END AS transfer_source_account,
                CASE
                    WHEN (((nco.normalized_category)::text = 'INTERNAL_BOOKKEEPING'::text) AND ((nco.comment)::text ~* 'transfer from [0-9]+ to [0-9]+'::text)) THEN ("substring"((nco.comment)::text, '(?i)to ([0-9]+)'::text))::bigint
                    ELSE NULL::bigint
                END AS transfer_target_account
           FROM investory.app_v_normalized_cash_operations nco
        ), effects AS (
         SELECT parsed.operation_id,
            parsed.account_id,
            parsed.portfolio_id,
            parsed.account_currency,
            parsed.currency,
            parsed.portfolio_base_currency,
            parsed.base_currency,
            parsed.raw_operation,
            parsed.normalized_category,
            parsed.normalized_subtype,
            parsed.economic_direction,
            parsed.is_external_flow,
            parsed.is_internal_transfer,
            parsed.is_fx_conversion,
            parsed.is_trade_cash_flow,
            parsed.is_correction,
            parsed.is_reversal,
            parsed.asset_id,
            parsed.amount,
            parsed.amount_in_portfolio_base_currency,
            parsed.amount_in_base_currency,
            parsed.amount_in_account_currency,
            parsed.comment,
            parsed.date,
            parsed.rate_month,
            parsed.fx_rate_to_base,
            parsed.portfolio_fx_source,
            parsed.portfolio_source_rate_date,
            parsed.portfolio_fx_age_days,
            parsed.portfolio_conversion_status,
            parsed.fx_rate_to_account_currency,
            parsed.account_fx_source,
            parsed.account_source_rate_date,
            parsed.account_fx_age_days,
            parsed.account_conversion_status,
            parsed.related_operation_id,
            parsed.transfer_group_id,
            parsed.pairing_status,
            parsed.classification_reason,
            parsed.classification_version,
            parsed.conversion_status,
            parsed.transfer_source_account,
            parsed.transfer_target_account,
                CASE
                    WHEN ((parsed.normalized_category)::text = ANY (ARRAY[('EXTERNAL_DEPOSIT'::character varying)::text, ('EXTERNAL_WITHDRAWAL'::character varying)::text, ('INTERNAL_TRANSFER_IN'::character varying)::text, ('INTERNAL_TRANSFER_OUT'::character varying)::text, ('FX_CONVERSION'::character varying)::text])) THEN parsed.amount
                    WHEN (((parsed.normalized_category)::text = 'INTERNAL_BOOKKEEPING'::text) AND (parsed.transfer_source_account = parsed.account_id) AND (parsed.amount < (0)::numeric)) THEN parsed.amount
                    WHEN (((parsed.normalized_category)::text = 'INTERNAL_BOOKKEEPING'::text) AND (parsed.transfer_target_account = parsed.account_id) AND (parsed.amount > (0)::numeric)) THEN parsed.amount
                    ELSE (0)::numeric
                END AS account_flow_amount,
                CASE
                    WHEN ((parsed.normalized_category)::text = ANY (ARRAY[('EXTERNAL_DEPOSIT'::character varying)::text, ('EXTERNAL_WITHDRAWAL'::character varying)::text, ('INTERNAL_TRANSFER_IN'::character varying)::text, ('INTERNAL_TRANSFER_OUT'::character varying)::text])) THEN parsed.amount
                    ELSE (0)::numeric
                END AS performance_flow_amount,
                CASE
                    WHEN ((parsed.normalized_category)::text = ANY (ARRAY[('EXTERNAL_DEPOSIT'::character varying)::text, ('EXTERNAL_WITHDRAWAL'::character varying)::text])) THEN parsed.amount
                    ELSE (0)::numeric
                END AS portfolio_flow_amount
           FROM parsed
        )
 SELECT operation_id,
    account_id,
    portfolio_id,
    account_currency,
    currency,
    portfolio_base_currency,
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
    amount_in_portfolio_base_currency,
    amount_in_base_currency,
    amount_in_account_currency,
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
    related_operation_id,
    transfer_group_id,
    pairing_status,
    classification_reason,
    classification_version,
    conversion_status,
    transfer_source_account,
    transfer_target_account,
    account_flow_amount,
    performance_flow_amount,
    portfolio_flow_amount,
        CASE
            WHEN investory.fx_status_usable(portfolio_conversion_status) THEN (account_flow_amount * fx_rate_to_base)
            ELSE NULL::numeric
        END AS account_flow_amount_in_portfolio_base_currency,
        CASE
            WHEN investory.fx_status_usable(account_conversion_status) THEN (account_flow_amount * fx_rate_to_account_currency)
            ELSE NULL::numeric
        END AS account_flow_amount_in_account_currency,
        CASE
            WHEN investory.fx_status_usable(portfolio_conversion_status) THEN (performance_flow_amount * fx_rate_to_base)
            ELSE NULL::numeric
        END AS performance_flow_amount_in_portfolio_base_currency,
        CASE
            WHEN investory.fx_status_usable(portfolio_conversion_status) THEN (portfolio_flow_amount * fx_rate_to_base)
            ELSE NULL::numeric
        END AS portfolio_flow_amount_in_portfolio_base_currency
   FROM effects;

CREATE VIEW investory.app_v_portfolio_performance_daily AS
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
                    ELSE sum(nco.account_flow_amount_in_portfolio_base_currency) FILTER (WHERE nco.account_flow_amount_in_portfolio_base_currency > 0::numeric)
                END AS deposits,
                CASE
                    WHEN count(*) FILTER (WHERE NOT investory.fx_status_usable(nco.portfolio_conversion_status)) > 0 THEN NULL::numeric
                    ELSE sum(- nco.account_flow_amount_in_portfolio_base_currency) FILTER (WHERE nco.account_flow_amount_in_portfolio_base_currency < 0::numeric)
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
            ELSE sum(converted.total_profit - converted.initialization_adjustment)
        END AS total_profit,
        CASE
            WHEN count(*) FILTER (WHERE NOT investory.fx_status_usable(converted.conversion_status)) > 0 THEN NULL::numeric
            ELSE sum(converted.initialization_adjustment)
        END AS initialization_adjustment,
        CASE
            WHEN count(*) FILTER (WHERE NOT investory.fx_status_usable(converted.conversion_status)) > 0 OR max(COALESCE(ef.missing_flow_fx_count, 0::bigint)) > 0 THEN NULL::numeric
            WHEN lag(sum(converted.equity)) OVER (PARTITION BY converted.portfolio_id ORDER BY converted.snapshot_date) IS NULL THEN NULL::numeric
            ELSE sum(converted.total_profit - converted.initialization_adjustment) / NULLIF(lag(sum(converted.equity)) OVER (PARTITION BY converted.portfolio_id ORDER BY converted.snapshot_date) + COALESCE(max(ef.deposits), 0::numeric) - COALESCE(max(ef.withdrawals), 0::numeric) + sum(converted.initialization_adjustment), 0::numeric)
        END AS daily_return_pct
   FROM account_boundaries converted
     LEFT JOIN performance_flows ef ON ef.portfolio_id = converted.portfolio_id AND ef.snapshot_date = converted.snapshot_date
  GROUP BY converted.portfolio_id, converted.snapshot_date, converted.base_currency;

CREATE MATERIALIZED VIEW investory.app_v_portfolio_monthly AS
 WITH month_rows AS (
         SELECT pd.portfolio_id,
            (date_trunc('month'::text, (pd.snapshot_date)::timestamp with time zone))::date AS month,
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
            lag(pd.equity) OVER (PARTITION BY pd.portfolio_id ORDER BY pd.snapshot_date) AS previous_day_equity,
            row_number() OVER (PARTITION BY pd.portfolio_id, (date_trunc('month'::text, (pd.snapshot_date)::timestamp with time zone)) ORDER BY pd.snapshot_date) AS rn_first,
            row_number() OVER (PARTITION BY pd.portfolio_id, (date_trunc('month'::text, (pd.snapshot_date)::timestamp with time zone)) ORDER BY pd.snapshot_date DESC) AS rn_last
           FROM investory.app_v_portfolio_performance_daily pd
        )
 SELECT portfolio_id,
    month,
    min(snapshot_date) AS first_date,
    max(snapshot_date) AS end_date,
    max(
        CASE
            WHEN (rn_first = 1) THEN COALESCE(previous_day_equity, equity)
            ELSE NULL::numeric
        END) AS opening_equity,
    max(
        CASE
            WHEN (rn_last = 1) THEN equity
            ELSE NULL::numeric
        END) AS closing_equity,
    (max((base_currency)::text))::character varying(3) AS base_currency,
    sum(deposits) AS deposits,
    sum(withdrawals) AS withdrawals,
    sum(dividends) AS dividends,
    sum(interest) AS interest,
    sum(fees) AS fees,
    sum(taxes) AS taxes,
    sum(realized_profit) AS realized_profit,
    sum(total_profit) AS total_profit,
        CASE
            WHEN (count(daily_return_pct) = 0) THEN NULL::numeric
            WHEN bool_or((daily_return_pct <= ('-1'::integer)::numeric)) THEN (- (1)::numeric)
            ELSE (exp(sum(
            CASE
                WHEN (daily_return_pct IS NULL) THEN NULL::numeric
                WHEN (daily_return_pct <= ('-1'::integer)::numeric) THEN NULL::numeric
                ELSE ln(((1)::numeric + daily_return_pct))
            END)) - (1)::numeric)
        END AS compounded_monthly_return,
    now() AS updated_at
   FROM month_rows mr
  GROUP BY portfolio_id, month;

CREATE VIEW investory.app_v_portfolio_monthly_summary AS
 SELECT portfolio_id,
    (((portfolio_id)::text || ':'::text) || to_char((month)::timestamp with time zone, 'YYYY-MM'::text)) AS portfolio_month_key,
    month,
    opening_equity,
    closing_equity,
    deposits,
    withdrawals,
    (deposits - withdrawals) AS net_external_flow,
    total_profit,
    (((((total_profit - realized_profit) - dividends) - interest) + fees) + taxes) AS market_fx,
    realized_profit AS realized,
    dividends,
    interest,
    fees,
    taxes,
    COALESCE(( SELECT count(*) AS count
           FROM (investory.app_v_account_monthly am
             JOIN investory.accounts a ON ((a.id = am.account_id)))
          WHERE ((a.portfolio_id = m.portfolio_id) AND (NOT a.cash_only) AND (am.month = m.month) AND ((abs(am.closing_equity) >= (50)::numeric) OR (abs(am.total_profit) >= 0.005) OR (abs((am.deposits - am.withdrawals)) >= 0.005)))), (0)::bigint) AS active_account_count
   FROM investory.app_v_portfolio_monthly m;

CREATE VIEW investory.app_v_account_monthly_attribution AS
 SELECT m.account_id,
    (((m.account_id)::text || ':'::text) || to_char((m.month)::timestamp with time zone, 'YYYY-MM'::text)) AS account_month_key,
    a.portfolio_id,
    m.month,
    m.opening_equity,
    m.closing_equity,
    (m.deposits - m.withdrawals) AS net_cashflow,
    m.total_profit AS profit,
        CASE
            WHEN (abs(s.total_profit) < 0.000001) THEN (0)::numeric
            ELSE ((m.total_profit / s.total_profit) * (100)::numeric)
        END AS contribution_pct
   FROM ((investory.app_v_account_monthly m
     JOIN investory.accounts a ON (((a.id = m.account_id) AND (NOT a.cash_only))))
     JOIN investory.app_v_portfolio_monthly_summary s ON (((s.portfolio_id = a.portfolio_id) AND (s.month = m.month))));

CREATE VIEW investory.app_v_account_monthly_benchmark AS
 SELECT account_id,
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
    total_profit,
    compounded_monthly_return,
    updated_at
   FROM investory.app_v_account_monthly monthly;

CREATE MATERIALIZED VIEW investory.app_v_canonical_asset_daily_price_mv AS
 SELECT DISTINCT ON (aph.asset_id, aph.price_date) aph.asset_id,
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
   FROM (investory.asset_price_history aph
     JOIN investory.assets asset ON (((asset.id = aph.asset_id) AND (asset.exclude_from_import = false))))
  ORDER BY aph.asset_id, aph.price_date, aph.quality_score DESC, aph.is_observed DESC, aph.is_proxy,
        CASE aph.price_origin
            WHEN 'XTB_TRADE_OPEN'::text THEN 0
            WHEN 'XTB_TRADE_CLOSE'::text THEN 2
            ELSE 1
        END, aph.imported_at DESC, aph.source;

CREATE VIEW investory.app_v_canonical_asset_daily_price AS
 SELECT asset_id,
    price_date,
    source,
    source_symbol,
    price_origin,
    price_currency,
    source_mapping_id,
    interpolation_method,
    interpolation_left_date,
    interpolation_right_date,
    open_price,
    high_price,
    low_price,
    close_price,
    adjusted_close_price,
    volume,
    estimated,
    quality_score,
    quality_class,
    is_observed,
    is_proxy,
    price_scale_factor,
    scale_reason,
    original_source_symbol,
    source_date,
    imported_at
   FROM investory.app_v_canonical_asset_daily_price_mv;

CREATE MATERIALIZED VIEW investory.app_v_current_asset_price_mv AS
 WITH latest_observed_price AS (
         SELECT DISTINCT ON (cp.asset_id) cp.asset_id,
            cp.price_date,
            cp.source,
            cp.source_symbol,
            cp.source_mapping_id,
            cp.price_origin,
            cp.price_currency,
            (cp.close_price * cp.price_scale_factor) AS selected_price,
            cp.quality_score,
            cp.quality_class,
            cp.is_proxy,
            cp.source_date,
            cp.imported_at
           FROM investory.app_v_canonical_asset_daily_price cp
          WHERE (cp.is_observed AND (cp.estimated = false) AND (cp.close_price > (0)::numeric) AND ((cp.price_origin)::text <> 'STALE_CARRY_FORWARD'::text) AND (COALESCE(cp.source_date, cp.price_date) >= (CURRENT_DATE - 10)) AND (COALESCE(cp.source_date, cp.price_date) <= CURRENT_DATE))
          ORDER BY cp.asset_id, COALESCE(cp.source_date, cp.price_date) DESC,
                CASE
                    WHEN ((cp.quality_class)::text = 'EXACT_LISTING_MARKET_CLOSE'::text) THEN 1
                    WHEN ((cp.quality_class)::text = 'EXACT_LISTING_SCALED'::text) THEN 2
                    WHEN (((cp.quality_class)::text ~~ '%ALTERNATE%'::text) OR cp.is_proxy) THEN 3
                    WHEN ((cp.price_origin)::text = 'MANUAL'::text) THEN 4
                    WHEN (cp.estimated OR ((cp.quality_class)::text ~~ 'INTERPOLATED%'::text)) THEN 5
                    WHEN (((cp.quality_class)::text ~~ '%TRADE_OBSERVATION%'::text) OR ((cp.price_origin)::text ~~ '%TRADE%'::text)) THEN 6
                    WHEN (((cp.quality_class)::text ~~ '%STALE%'::text) OR ((cp.price_origin)::text = 'STALE_CARRY_FORWARD'::text)) THEN 7
                    ELSE 9
                END, cp.quality_score DESC, cp.price_date DESC, cp.imported_at DESC, cp.source
        )
 SELECT a.id AS asset_id,
    COALESCE(lp.selected_price, a.market_price) AS selected_price,
        CASE
            WHEN (lp.asset_id IS NOT NULL) THEN lp.price_currency
            ELSE a.currency
        END AS price_currency,
    (
        CASE
            WHEN (lp.asset_id IS NOT NULL) THEN 'HISTORICAL'::text
            ELSE 'ASSET_CURRENT_FALLBACK'::text
        END)::character varying(32) AS price_selection_source,
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
   FROM (investory.assets a
     LEFT JOIN latest_observed_price lp ON ((lp.asset_id = a.id)))
  WHERE (a.exclude_from_import = false);

CREATE MATERIALIZED VIEW investory.app_v_portfolio_daily_fx_rate_mv AS
 WITH portfolio_dates AS (
         SELECT DISTINCT a.portfolio_id,
            ad.snapshot_date AS valuation_date
           FROM (investory.account_daily ad
             JOIN investory.accounts a ON ((a.id = ad.account_id)))
        UNION
         SELECT DISTINCT a.portfolio_id,
            ((co.date AT TIME ZONE 'Europe/Warsaw'::text))::date AS timezone
           FROM (investory.cash_operations co
             JOIN investory.accounts a ON ((a.id = co.account_id)))
        UNION
         SELECT portfolios.id,
            CURRENT_DATE AS "current_date"
           FROM investory.portfolios
        )
 SELECT resolved.portfolio_id,
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
   FROM ((portfolio_dates dates
     CROSS JOIN investory.currencies currencies)
     CROSS JOIN LATERAL investory.resolve_portfolio_fx_rate(dates.portfolio_id, dates.valuation_date, currencies.id) resolved(portfolio_id, valuation_date, source_currency, base_currency, fx_rate_to_base, source, rate_method, rate_source, source_rate_date, age_days, conversion_status));

CREATE VIEW investory.app_v_current_open_position_rows AS
 SELECT pf.id AS portfolio_id,
    pf.base_currency,
    a.id AS account_id,
    a.currency AS account_currency,
    asset.id AS asset_id,
    asset.symbol AS asset_symbol,
    p.id AS position_id,
    p.cost_currency AS cost_basis_currency,
    investory.signed_position_quantity(p.operation, p.volume) AS volume,
    COALESCE(p.purchase_value, (p.volume * p.open_price), (0)::numeric) AS cost_basis_native,
    COALESCE(price.selected_price, asset.market_price) AS market_price,
    COALESCE(price.price_currency, asset.currency) AS market_price_currency,
    price.price_selection_source,
    price.selected_price_date,
    price.price_source,
    price.source_symbol AS market_price_source_symbol,
    cost_fx.fx_rate_to_base AS cost_basis_to_base_rate,
    cost_fx.conversion_status AS cost_basis_fx_status,
    market_fx.fx_rate_to_base AS market_price_to_base_rate,
    market_fx.conversion_status AS market_price_fx_status,
        CASE
            WHEN investory.fx_status_usable(cost_fx.conversion_status) THEN (COALESCE(p.purchase_value, (p.volume * p.open_price), (0)::numeric) * cost_fx.fx_rate_to_base)
            ELSE NULL::numeric
        END AS cost_basis_in_base_currency,
        CASE
            WHEN ((COALESCE(price.selected_price, asset.market_price) IS NOT NULL) AND investory.fx_status_usable(market_fx.conversion_status)) THEN (((investory.signed_position_quantity(p.operation, p.volume) * COALESCE(price.selected_price, asset.market_price)) *
            CASE
                WHEN ((price.quality_class)::text ~~ '%PERCENT_OF_PAR%'::text) THEN 0.01
                ELSE (1)::numeric
            END) * market_fx.fx_rate_to_base)
            ELSE NULL::numeric
        END AS market_value_in_base_currency
   FROM ((((((investory.positions p
     JOIN investory.accounts a ON ((a.id = p.account_id)))
     JOIN investory.portfolios pf ON ((pf.id = a.portfolio_id)))
     JOIN investory.assets asset ON ((asset.id = p.asset_id)))
     LEFT JOIN investory.app_v_current_asset_price_mv price ON ((price.asset_id = asset.id)))
     LEFT JOIN investory.app_v_portfolio_daily_fx_rate_mv cost_fx ON (((cost_fx.portfolio_id = pf.id) AND (cost_fx.valuation_date = CURRENT_DATE) AND ((cost_fx.source_currency)::text = (p.cost_currency)::text))))
     LEFT JOIN investory.app_v_portfolio_daily_fx_rate_mv market_fx ON (((market_fx.portfolio_id = pf.id) AND (market_fx.valuation_date = CURRENT_DATE) AND ((market_fx.source_currency)::text = (COALESCE(price.price_currency, asset.currency))::text))))
  WHERE (((p.open_time IS NULL) OR (p.open_time <= CURRENT_DATE)) AND (p.close_time IS NULL) AND (asset.exclude_from_import = false) AND (COALESCE(p.volume, (0)::numeric) > (0)::numeric));

CREATE MATERIALIZED VIEW investory.app_v_account_statistics AS
 WITH latest_daily AS (
         SELECT DISTINCT ON (ad.account_id) ad.account_id,
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
        ), latest_daily_in_base AS (
         SELECT ld_1.account_id,
            ld_1.snapshot_date,
            pf.base_currency AS valuation_currency,
                CASE
                    WHEN investory.fx_status_usable(fx.conversion_status) THEN (ld_1.cash_balance * fx.fx_rate_to_base)
                    ELSE NULL::numeric
                END AS cash_balance,
            ((ld_1.cash_balance IS NOT NULL) AND (NOT investory.fx_status_usable(fx.conversion_status))) AS cash_fx_missing,
                CASE
                    WHEN investory.fx_status_usable(fx.conversion_status) THEN (ld_1.market_value * fx.fx_rate_to_base)
                    ELSE NULL::numeric
                END AS market_value,
            ((ld_1.market_value IS NOT NULL) AND (NOT investory.fx_status_usable(fx.conversion_status))) AS market_fx_missing,
                CASE
                    WHEN investory.fx_status_usable(fx.conversion_status) THEN (ld_1.equity * fx.fx_rate_to_base)
                    ELSE NULL::numeric
                END AS equity,
                CASE
                    WHEN investory.fx_status_usable(fx.conversion_status) THEN (ld_1.cost_base * fx.fx_rate_to_base)
                    ELSE NULL::numeric
                END AS cost_base,
                CASE
                    WHEN investory.fx_status_usable(fx.conversion_status) THEN (ld_1.unrealized_profit * fx.fx_rate_to_base)
                    ELSE NULL::numeric
                END AS unrealized_profit,
                CASE
                    WHEN investory.fx_status_usable(fx.conversion_status) THEN (ld_1.realized_profit * fx.fx_rate_to_base)
                    ELSE NULL::numeric
                END AS realized_profit,
            ld_1.daily_return_pct
           FROM (((latest_daily ld_1
             JOIN investory.accounts a_1 ON ((a_1.id = ld_1.account_id)))
             JOIN investory.portfolios pf ON ((pf.id = a_1.portfolio_id)))
             LEFT JOIN investory.app_v_portfolio_daily_fx_rate_mv fx ON (((fx.portfolio_id = a_1.portfolio_id) AND (fx.valuation_date = ld_1.snapshot_date) AND ((fx.source_currency)::text = (ld_1.valuation_currency)::text) AND ((fx.base_currency)::text = (pf.base_currency)::text))))
        ), open_position_totals AS (
         SELECT value.account_id,
            count(*) AS position_count,
                CASE
                    WHEN (count(*) FILTER (WHERE (value.cost_basis_in_base_currency IS NOT NULL)) = 0) THEN NULL::numeric
                    ELSE sum(value.cost_basis_in_base_currency) FILTER (WHERE (value.cost_basis_in_base_currency IS NOT NULL))
                END AS cost_base,
                CASE
                    WHEN (count(*) FILTER (WHERE (value.market_value_in_base_currency IS NOT NULL)) = 0) THEN NULL::numeric
                    ELSE sum(value.market_value_in_base_currency) FILTER (WHERE (value.market_value_in_base_currency IS NOT NULL))
                END AS market_value,
                CASE
                    WHEN (count(*) FILTER (WHERE ((value.cost_basis_in_base_currency IS NOT NULL) AND (value.market_value_in_base_currency IS NOT NULL))) = 0) THEN NULL::numeric
                    ELSE sum((value.market_value_in_base_currency - value.cost_basis_in_base_currency)) FILTER (WHERE ((value.cost_basis_in_base_currency IS NOT NULL) AND (value.market_value_in_base_currency IS NOT NULL)))
                END AS unrealized_profit,
            count(*) FILTER (WHERE ((value.cost_basis_in_base_currency IS NULL) OR (value.market_value_in_base_currency IS NULL))) AS missing_fx_count
           FROM investory.app_v_current_open_position_rows value
          GROUP BY value.account_id
        ), closed_position_components AS (
         SELECT a_1.id AS account_id,
            a_1.portfolio_id,
            (p_1.close_time)::date AS valuation_date,
            p_1.profit_currency AS source_currency,
            pf.base_currency,
                CASE
                    WHEN (p_1.settlement_model = 'RESULT_ONLY'::investory.position_settlement_model) THEN COALESCE(p_1.profit, (0)::numeric)
                    ELSE (COALESCE(p_1.profit, (0)::numeric) + COALESCE(p_1.swap, (0)::numeric))
                END AS amount_native
           FROM ((investory.positions p_1
             JOIN investory.accounts a_1 ON ((a_1.id = p_1.account_id)))
             JOIN investory.portfolios pf ON ((pf.id = a_1.portfolio_id)))
          WHERE ((p_1.close_time IS NOT NULL) AND (p_1.asset_id IS NOT NULL))
        UNION ALL
         SELECT a_1.id AS account_id,
            a_1.portfolio_id,
            (p_1.close_time)::date AS valuation_date,
            p_1.commission_currency AS source_currency,
            pf.base_currency,
                CASE
                    WHEN (p_1.settlement_model = 'RESULT_ONLY'::investory.position_settlement_model) THEN (0)::numeric
                    ELSE COALESCE(p_1.commission, (0)::numeric)
                END AS amount_native
           FROM ((investory.positions p_1
             JOIN investory.accounts a_1 ON ((a_1.id = p_1.account_id)))
             JOIN investory.portfolios pf ON ((pf.id = a_1.portfolio_id)))
          WHERE ((p_1.close_time IS NOT NULL) AND (p_1.asset_id IS NOT NULL))
        ), closed_position_totals AS (
         SELECT c.account_id,
                CASE
                    WHEN (count(*) FILTER (WHERE (NOT investory.fx_status_usable(fx.conversion_status))) > 0) THEN NULL::numeric
                    ELSE sum((c.amount_native * fx.fx_rate_to_target))
                END AS realized_profit,
            sum((c.amount_native * fx.fx_rate_to_target)) FILTER (WHERE investory.fx_status_usable(fx.conversion_status)) AS converted_subtotal,
            count(*) FILTER (WHERE (NOT investory.fx_status_usable(fx.conversion_status))) AS missing_fx_count
           FROM (closed_position_components c
             LEFT JOIN LATERAL investory.resolve_fx_rate(c.valuation_date, c.source_currency, c.base_currency) fx(source_currency, target_currency, fx_rate_to_target, source, rate_method, rate_source, source_rate_date, age_days, conversion_status) ON (true))
          GROUP BY c.account_id
        ), portfolio_flow_rows AS (
         SELECT nco.operation_id,
            nco.account_id,
            nco.portfolio_id,
            nco.account_currency,
            nco.currency,
            nco.portfolio_base_currency,
            nco.base_currency,
            nco.raw_operation,
            nco.normalized_category,
            nco.normalized_subtype,
            nco.economic_direction,
            nco.is_external_flow,
            nco.is_internal_transfer,
            nco.is_fx_conversion,
            nco.is_trade_cash_flow,
            nco.is_correction,
            nco.is_reversal,
            nco.asset_id,
            nco.amount,
            nco.amount_in_portfolio_base_currency,
            nco.amount_in_base_currency,
            nco.amount_in_account_currency,
            nco.comment,
            nco.date,
            nco.rate_month,
            nco.fx_rate_to_base,
            nco.portfolio_fx_source,
            nco.portfolio_source_rate_date,
            nco.portfolio_fx_age_days,
            nco.portfolio_conversion_status,
            nco.fx_rate_to_account_currency,
            nco.account_fx_source,
            nco.account_source_rate_date,
            nco.account_fx_age_days,
            nco.account_conversion_status,
            nco.related_operation_id,
            nco.transfer_group_id,
            nco.pairing_status,
            nco.classification_reason,
            nco.classification_version,
            nco.conversion_status,
                CASE
                    WHEN ((nco.normalized_category)::text = ANY ((ARRAY['EXTERNAL_DEPOSIT'::character varying, 'EXTERNAL_WITHDRAWAL'::character varying])::text[])) THEN nco.amount_in_portfolio_base_currency
                    WHEN (((nco.normalized_category)::text = 'INTERNAL_BOOKKEEPING'::text) AND ((nco.comment)::text ~* 'transfer from [0-9]+ to [0-9]+'::text) AND (("substring"((nco.comment)::text, '(?i)to ([0-9]+)'::text))::bigint = nco.account_id) AND (nco.amount > (0)::numeric) AND (NOT (EXISTS ( SELECT 1
                       FROM investory.accounts counterparty
                      WHERE ((counterparty.id = ("substring"((nco.comment)::text, '(?i)transfer from ([0-9]+)'::text))::bigint) AND (counterparty.portfolio_id = ( SELECT source.portfolio_id
                               FROM investory.accounts source
                              WHERE (source.id = nco.account_id)))))))) THEN nco.amount_in_portfolio_base_currency
                    WHEN (((nco.normalized_category)::text = 'INTERNAL_BOOKKEEPING'::text) AND ((nco.comment)::text ~* 'transfer from [0-9]+ to [0-9]+'::text) AND (("substring"((nco.comment)::text, '(?i)transfer from ([0-9]+)'::text))::bigint = nco.account_id) AND (nco.amount < (0)::numeric) AND (NOT (EXISTS ( SELECT 1
                       FROM investory.accounts counterparty
                      WHERE ((counterparty.id = ("substring"((nco.comment)::text, '(?i)to ([0-9]+)'::text))::bigint) AND (counterparty.portfolio_id = ( SELECT source.portfolio_id
                               FROM investory.accounts source
                              WHERE (source.id = nco.account_id)))))))) THEN nco.amount_in_portfolio_base_currency
                    ELSE (0)::numeric
                END AS scoped_portfolio_flow_amount_in_portfolio_base_currency
           FROM investory.app_v_normalized_cash_operations nco
        ), flow_totals AS (
         SELECT nco.account_id,
            count(*) FILTER (WHERE ((NOT investory.fx_status_usable(nco.portfolio_conversion_status)) OR (NOT investory.fx_status_usable(nco.account_conversion_status)))) AS missing_fx_count,
            count(*) FILTER (WHERE (NOT investory.fx_status_usable(nco.account_conversion_status))) AS account_missing_fx_count,
            sum(nco.amount_in_portfolio_base_currency) FILTER (WHERE investory.fx_status_usable(nco.portfolio_conversion_status)) AS converted_subtotal,
            sum(nco.scoped_portfolio_flow_amount_in_portfolio_base_currency) FILTER (WHERE (nco.scoped_portfolio_flow_amount_in_portfolio_base_currency > (0)::numeric)) AS total_deposit,
            sum(
                CASE
                    WHEN ((nco.normalized_category)::text = 'EXTERNAL_DEPOSIT'::text) THEN nco.amount_in_account_currency
                    ELSE NULL::numeric
                END) AS total_deposit_account_currency,
            sum((- nco.scoped_portfolio_flow_amount_in_portfolio_base_currency)) FILTER (WHERE (nco.scoped_portfolio_flow_amount_in_portfolio_base_currency < (0)::numeric)) AS total_withdrawal,
            sum(
                CASE
                    WHEN ((nco.normalized_category)::text = 'EXTERNAL_WITHDRAWAL'::text) THEN abs(nco.amount_in_account_currency)
                    ELSE NULL::numeric
                END) AS total_withdrawal_account_currency,
            sum(
                CASE
                    WHEN ((nco.normalized_category)::text = ANY ((ARRAY['DIVIDEND'::character varying, 'DIVIDEND_REVERSAL'::character varying])::text[])) THEN nco.amount_in_base_currency
                    ELSE NULL::numeric
                END) AS dividends,
            sum(
                CASE
                    WHEN ((nco.normalized_category)::text = ANY ((ARRAY['INTEREST'::character varying, 'INTEREST_REVERSAL'::character varying])::text[])) THEN nco.amount_in_base_currency
                    ELSE NULL::numeric
                END) AS interest,
            sum(
                CASE
                    WHEN ((nco.normalized_category)::text = 'FEE'::text) THEN (- nco.amount_in_base_currency)
                    ELSE NULL::numeric
                END) AS fees,
            sum(
                CASE
                    WHEN ((nco.normalized_category)::text = ANY ((ARRAY['WITHHOLDING_TAX'::character varying, 'WITHHOLDING_TAX_REVERSAL'::character varying, 'OTHER_TAX'::character varying])::text[])) THEN (- nco.amount_in_base_currency)
                    ELSE NULL::numeric
                END) AS taxes
           FROM portfolio_flow_rows nco
          GROUP BY nco.account_id
        ), activity_meta AS (
         SELECT ad.account_id,
            (count(*) FILTER (WHERE ((COALESCE(ad.deposits, (0)::numeric) <> (0)::numeric) OR (COALESCE(ad.withdrawals, (0)::numeric) <> (0)::numeric) OR (COALESCE(ad.dividends, (0)::numeric) <> (0)::numeric) OR (COALESCE(ad.interest, (0)::numeric) <> (0)::numeric) OR (COALESCE(ad.fees, (0)::numeric) <> (0)::numeric) OR (COALESCE(ad.taxes, (0)::numeric) <> (0)::numeric) OR (COALESCE(ad.realized_profit, (0)::numeric) <> (0)::numeric))))::integer AS activity_count,
            (min(ad.snapshot_date) FILTER (WHERE ((COALESCE(ad.deposits, (0)::numeric) <> (0)::numeric) OR (COALESCE(ad.withdrawals, (0)::numeric) <> (0)::numeric) OR (COALESCE(ad.dividends, (0)::numeric) <> (0)::numeric) OR (COALESCE(ad.interest, (0)::numeric) <> (0)::numeric) OR (COALESCE(ad.fees, (0)::numeric) <> (0)::numeric) OR (COALESCE(ad.taxes, (0)::numeric) <> (0)::numeric) OR (COALESCE(ad.realized_profit, (0)::numeric) <> (0)::numeric))))::timestamp with time zone AS first_activity_at,
            (max(ad.snapshot_date) FILTER (WHERE ((COALESCE(ad.deposits, (0)::numeric) <> (0)::numeric) OR (COALESCE(ad.withdrawals, (0)::numeric) <> (0)::numeric) OR (COALESCE(ad.dividends, (0)::numeric) <> (0)::numeric) OR (COALESCE(ad.interest, (0)::numeric) <> (0)::numeric) OR (COALESCE(ad.fees, (0)::numeric) <> (0)::numeric) OR (COALESCE(ad.taxes, (0)::numeric) <> (0)::numeric) OR (COALESCE(ad.realized_profit, (0)::numeric) <> (0)::numeric))))::timestamp with time zone AS last_activity_at
           FROM investory.account_daily ad
          GROUP BY ad.account_id
        )
 SELECT a.id AS account_id,
    p.base_currency AS valuation_currency,
        CASE
            WHEN (COALESCE(ft.missing_fx_count, (0)::bigint) > 0) THEN NULL::numeric
            ELSE COALESCE(ft.total_deposit, (0)::numeric)
        END AS total_deposit,
        CASE
            WHEN (COALESCE(ft.missing_fx_count, (0)::bigint) > 0) THEN NULL::numeric
            ELSE COALESCE(ft.total_withdrawal, (0)::numeric)
        END AS total_withdrawal,
        CASE
            WHEN (COALESCE(ft.missing_fx_count, (0)::bigint) > 0) THEN NULL::numeric
            ELSE (COALESCE(ft.total_deposit, (0)::numeric) - COALESCE(ft.total_withdrawal, (0)::numeric))
        END AS net_deposit,
        CASE
            WHEN (COALESCE(ft.account_missing_fx_count, (0)::bigint) > 0) THEN NULL::numeric
            ELSE (COALESCE(ft.total_deposit_account_currency, (0)::numeric) - COALESCE(ft.total_withdrawal_account_currency, (0)::numeric))
        END AS account_net_deposit,
        CASE
            WHEN COALESCE(ld.cash_fx_missing, false) THEN NULL::numeric
            ELSE COALESCE(ld.cash_balance, (0)::numeric)
        END AS cash_balance,
        CASE
            WHEN COALESCE(ld.market_fx_missing, false) THEN NULL::numeric
            WHEN (COALESCE(opt.position_count, (0)::bigint) > 0) THEN opt.market_value
            ELSE COALESCE(ld.market_value, (0)::numeric)
        END AS market_value,
        CASE
            WHEN COALESCE(ld.cash_fx_missing, false) THEN NULL::numeric
            WHEN (COALESCE(opt.position_count, (0)::bigint) > 0) THEN
            CASE
                WHEN (opt.market_value IS NULL) THEN NULL::numeric
                ELSE (COALESCE(ld.cash_balance, (0)::numeric) + opt.market_value)
            END
            ELSE COALESCE(ld.equity, COALESCE(ld.cash_balance, (0)::numeric))
        END AS equity,
        CASE
            WHEN (COALESCE(opt.position_count, (0)::bigint) > 0) THEN opt.cost_base
            ELSE COALESCE(ld.cost_base, (0)::numeric)
        END AS cost_base,
        CASE
            WHEN (COALESCE(cpt.missing_fx_count, (0)::bigint) > 0) THEN NULL::numeric
            ELSE COALESCE(cpt.realized_profit, (0)::numeric)
        END AS realized_profit,
        CASE
            WHEN (COALESCE(opt.position_count, (0)::bigint) > 0) THEN opt.unrealized_profit
            ELSE COALESCE(ld.unrealized_profit, (0)::numeric)
        END AS unrealized_profit,
        CASE
            WHEN (COALESCE(ft.missing_fx_count, (0)::bigint) > 0) THEN NULL::numeric
            ELSE COALESCE(ft.dividends, (0)::numeric)
        END AS dividends,
        CASE
            WHEN (COALESCE(ft.missing_fx_count, (0)::bigint) > 0) THEN NULL::numeric
            ELSE COALESCE(ft.interest, (0)::numeric)
        END AS interest,
        CASE
            WHEN (COALESCE(ft.missing_fx_count, (0)::bigint) > 0) THEN NULL::numeric
            ELSE COALESCE(ft.fees, (0)::numeric)
        END AS fees,
        CASE
            WHEN (COALESCE(ft.missing_fx_count, (0)::bigint) > 0) THEN NULL::numeric
            ELSE COALESCE(ft.taxes, (0)::numeric)
        END AS taxes,
    COALESCE(ft.converted_subtotal, (0)::numeric) AS converted_cash_subtotal,
    ((((COALESCE(ft.missing_fx_count, (0)::bigint) + COALESCE(cpt.missing_fx_count, (0)::bigint)) + COALESCE(opt.missing_fx_count, (0)::bigint)) +
        CASE
            WHEN COALESCE(ld.cash_fx_missing, false) THEN 1
            ELSE 0
        END) +
        CASE
            WHEN COALESCE(ld.market_fx_missing, false) THEN 1
            ELSE 0
        END) AS missing_fx_count,
    ((COALESCE(ft.missing_fx_count, (0)::bigint) = 0) AND (COALESCE(cpt.missing_fx_count, (0)::bigint) = 0) AND (COALESCE(opt.missing_fx_count, (0)::bigint) = 0) AND (NOT COALESCE(ld.cash_fx_missing, false)) AND (NOT COALESCE(ld.market_fx_missing, false))) AS is_complete,
    COALESCE(am.activity_count, 0) AS activity_count,
    am.first_activity_at,
    am.last_activity_at,
    ld.snapshot_date AS latest_snapshot_date,
    ld.daily_return_pct AS latest_return_pct,
    now() AS updated_at,
    a.cash_only,
    ((abs((COALESCE(ld.cash_balance, (0)::numeric) + COALESCE(
        CASE
            WHEN (COALESCE(opt.position_count, (0)::bigint) > 0) THEN opt.market_value
            ELSE COALESCE(ld.market_value, (0)::numeric)
        END, (0)::numeric))) >= (50)::numeric) OR (abs(COALESCE(
        CASE
            WHEN (COALESCE(ft.missing_fx_count, (0)::bigint) > 0) THEN NULL::numeric
            ELSE (COALESCE(ft.total_deposit, (0)::numeric) - COALESCE(ft.total_withdrawal, (0)::numeric))
        END, (0)::numeric)) >= (50)::numeric) OR (abs(COALESCE(
        CASE
            WHEN (COALESCE(ft.account_missing_fx_count, (0)::bigint) > 0) THEN NULL::numeric
            ELSE (COALESCE(ft.total_deposit_account_currency, (0)::numeric) - COALESCE(ft.total_withdrawal_account_currency, (0)::numeric))
        END, (0)::numeric)) >= (50)::numeric)) AS is_visible
   FROM ((((((investory.accounts a
     JOIN investory.portfolios p ON ((p.id = a.portfolio_id)))
     LEFT JOIN latest_daily_in_base ld ON ((ld.account_id = a.id)))
     LEFT JOIN open_position_totals opt ON ((opt.account_id = a.id)))
     LEFT JOIN flow_totals ft ON ((ft.account_id = a.id)))
     LEFT JOIN activity_meta am ON ((am.account_id = a.id)))
     LEFT JOIN closed_position_totals cpt ON ((cpt.account_id = a.id)));

CREATE VIEW investory.app_v_account_statistics_reporting AS
 SELECT account_id,
    valuation_currency,
    total_deposit,
    total_withdrawal,
    net_deposit,
    account_net_deposit,
    cash_balance,
    market_value,
    equity,
    cost_base,
    realized_profit,
    unrealized_profit,
    dividends,
    interest,
    fees,
    taxes,
    converted_cash_subtotal,
    missing_fx_count,
    is_complete,
    activity_count,
    first_activity_at,
    last_activity_at,
    latest_snapshot_date,
    latest_return_pct,
    updated_at,
    cash_only,
    is_visible
   FROM investory.app_v_account_statistics;

CREATE MATERIALIZED VIEW investory.app_v_canonical_asset_daily_price_ranked_mv AS
 SELECT asset_id,
    price_date,
    source,
    source_symbol,
    price_origin,
    price_currency,
    source_mapping_id,
    interpolation_method,
    interpolation_left_date,
    interpolation_right_date,
    open_price,
    high_price,
    low_price,
    close_price,
    adjusted_close_price,
    volume,
    estimated,
    quality_score,
    quality_class,
    is_observed,
    is_proxy,
    price_scale_factor,
    scale_reason,
    original_source_symbol,
    source_date,
    imported_at,
        CASE
            WHEN (estimated AND (interpolation_left_date IS NOT NULL)) THEN interpolation_left_date
            ELSE COALESCE(source_date, price_date)
        END AS effective_observation_date,
        CASE
            WHEN ((quality_class)::text = 'EXACT_LISTING_MARKET_CLOSE'::text) THEN 1
            WHEN ((quality_class)::text = 'EXACT_LISTING_SCALED'::text) THEN 2
            WHEN (((quality_class)::text ~~ '%ALTERNATE%'::text) OR is_proxy) THEN 3
            WHEN ((price_origin)::text = 'MANUAL'::text) THEN 4
            WHEN (estimated OR ((quality_class)::text ~~ 'INTERPOLATED%'::text)) THEN 5
            WHEN (((quality_class)::text ~~ '%TRADE_OBSERVATION%'::text) OR ((price_origin)::text ~~ '%TRADE%'::text)) THEN 6
            WHEN (((quality_class)::text ~~ '%STALE%'::text) OR ((price_origin)::text = 'STALE_CARRY_FORWARD'::text)) THEN 7
            ELSE 9
        END AS selection_priority
   FROM investory.app_v_canonical_asset_daily_price_mv cp;

CREATE VIEW investory.app_v_canonical_asset_daily_return AS
 WITH canonical_prices AS (
         SELECT cp.asset_id,
            cp.price_date,
            (COALESCE(cp.adjusted_close_price, cp.close_price) * COALESCE(cp.price_scale_factor, (1)::numeric)) AS return_price
           FROM investory.app_v_canonical_asset_daily_price cp
          WHERE (COALESCE(cp.adjusted_close_price, cp.close_price) > (0)::numeric)
        ), with_previous AS (
         SELECT canonical_prices.asset_id,
            canonical_prices.price_date,
            canonical_prices.return_price,
            lag(canonical_prices.return_price) OVER (PARTITION BY canonical_prices.asset_id ORDER BY canonical_prices.price_date) AS previous_return_price
           FROM canonical_prices
        )
 SELECT asset_id,
    price_date,
    return_price,
        CASE
            WHEN (previous_return_price > (0)::numeric) THEN ((return_price / previous_return_price) - (1)::numeric)
            ELSE NULL::numeric
        END AS daily_return_pct
   FROM with_previous;

CREATE VIEW investory.app_v_current_asset_price AS
 SELECT asset_id,
    selected_price,
    price_currency,
    price_selection_source,
    selected_price_date,
    underlying_observation_date,
    price_source,
    source_symbol,
    source_mapping_id,
    price_origin,
    quality_score,
    quality_class,
    is_proxy,
    imported_at
   FROM investory.app_v_current_asset_price_mv;

CREATE MATERIALIZED VIEW investory.app_v_normalized_daily_price_mv AS
 WITH position_dates AS (
         SELECT DISTINCT a.id AS asset_id,
            d.snapshot_date AS valuation_date
           FROM ((investory.positions p
             JOIN investory.assets a ON (((a.id = p.asset_id) AND (NOT a.exclude_from_import))))
             JOIN investory.account_daily d ON (((d.account_id = p.account_id) AND (d.snapshot_date >= COALESCE((p.open_time)::date, d.snapshot_date)) AND ((p.close_time IS NULL) OR (d.snapshot_date < (p.close_time)::date)))))
        )
 SELECT w.asset_id,
    pd.valuation_date,
    (w.close_price * COALESCE(w.price_scale_factor, (1)::numeric)) AS selected_price,
    w.price_date AS selected_price_date,
    w.effective_observation_date AS underlying_observation_date,
    GREATEST(0, (pd.valuation_date - w.effective_observation_date)) AS price_age_days,
    w.price_currency,
    (((((((w.asset_id)::character varying)::text || ':'::text) || ((w.price_date)::character varying)::text) || ':'::text) || (w.source)::text))::character varying(255) AS selected_price_history_id,
    w.price_origin,
    w.quality_class,
    w.source_symbol,
    w.original_source_symbol,
    w.price_scale_factor,
    w.scale_reason,
    w.source AS source_name,
    NULL::bigint AS proxy_asset_id,
    (w.estimated OR ((w.quality_class)::text ~~ 'INTERPOLATED%'::text)) AS is_interpolated,
    w.selection_priority,
    (
        CASE
            WHEN ((w.close_price IS NULL) OR (w.close_price <= (0)::numeric)) THEN 'FAIL'::text
            WHEN (w.price_currency IS NULL) THEN 'FAIL'::text
            WHEN (w.selection_priority >= 3) THEN 'WARN'::text
            ELSE 'PASS'::text
        END)::character varying(16) AS validation_status,
        CASE w.selection_priority
            WHEN 1 THEN 'exact listing market close'::text
            WHEN 2 THEN 'exact listing scaled/normalized close'::text
            WHEN 3 THEN 'verified alternate/proxy listing'::text
            WHEN 4 THEN 'manual price'::text
            WHEN 5 THEN 'interpolated price'::text
            WHEN 6 THEN 'trade observation fallback'::text
            WHEN 7 THEN 'stale carry-forward fallback'::text
            ELSE 'unclassified price source'::text
        END AS validation_message
   FROM ((position_dates pd
     JOIN LATERAL ( SELECT aph.effective_observation_date
           FROM investory.app_v_canonical_asset_daily_price_ranked_mv aph
          WHERE ((aph.asset_id = pd.asset_id) AND (aph.price_date <= pd.valuation_date) AND ((NOT aph.estimated) OR (aph.interpolation_right_date IS NULL) OR (aph.interpolation_right_date <= pd.valuation_date)) AND (aph.effective_observation_date <= pd.valuation_date))
          ORDER BY aph.effective_observation_date DESC
         LIMIT 1) latest ON (true))
     JOIN LATERAL ( SELECT aph.asset_id,
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
            aph.imported_at,
            aph.effective_observation_date,
            aph.selection_priority
           FROM investory.app_v_canonical_asset_daily_price_ranked_mv aph
          WHERE ((aph.asset_id = pd.asset_id) AND (aph.effective_observation_date = latest.effective_observation_date) AND (aph.price_date <= pd.valuation_date) AND ((NOT aph.estimated) OR (aph.interpolation_right_date IS NULL) OR (aph.interpolation_right_date <= pd.valuation_date)))
          ORDER BY
                CASE
                    WHEN (aph.effective_observation_date IS NULL) THEN 1
                    ELSE 0
                END, aph.effective_observation_date DESC, aph.selection_priority, aph.quality_score DESC, aph.price_date DESC,
                CASE aph.price_origin
                    WHEN 'XTB_TRADE_OPEN'::text THEN 0
                    WHEN 'XTB_TRADE_CLOSE'::text THEN 2
                    ELSE 1
                END, aph.source, aph.source_symbol
         LIMIT 1) w ON (true));

CREATE VIEW investory.app_v_normalized_daily_price AS
 SELECT asset_id,
    valuation_date,
    selected_price,
    selected_price_date,
    underlying_observation_date,
    price_age_days,
    price_currency,
    selected_price_history_id,
    price_origin,
    quality_class,
    source_symbol,
    original_source_symbol,
    price_scale_factor,
    scale_reason,
    source_name,
    proxy_asset_id,
    is_interpolated,
    selection_priority,
    validation_status,
    validation_message
   FROM investory.app_v_normalized_daily_price_mv;

CREATE VIEW investory.app_v_open_position_values AS
 WITH position_rows AS (
         SELECT app_v_current_open_position_rows.portfolio_id,
            app_v_current_open_position_rows.base_currency,
            app_v_current_open_position_rows.account_id,
            app_v_current_open_position_rows.account_currency,
            app_v_current_open_position_rows.asset_id,
            app_v_current_open_position_rows.asset_symbol,
            app_v_current_open_position_rows.position_id,
            app_v_current_open_position_rows.cost_basis_currency,
            app_v_current_open_position_rows.volume,
            app_v_current_open_position_rows.cost_basis_native,
            app_v_current_open_position_rows.market_price,
            app_v_current_open_position_rows.market_price_currency,
            app_v_current_open_position_rows.price_selection_source,
            app_v_current_open_position_rows.selected_price_date,
            app_v_current_open_position_rows.price_source,
            app_v_current_open_position_rows.market_price_source_symbol,
            app_v_current_open_position_rows.cost_basis_to_base_rate,
            app_v_current_open_position_rows.cost_basis_fx_status,
            app_v_current_open_position_rows.market_price_to_base_rate,
            app_v_current_open_position_rows.market_price_fx_status,
            app_v_current_open_position_rows.cost_basis_in_base_currency,
            app_v_current_open_position_rows.market_value_in_base_currency
           FROM investory.app_v_current_open_position_rows
        ), position_rows_with_fx AS (
         SELECT position_rows.portfolio_id,
            position_rows.base_currency,
            position_rows.account_id,
            position_rows.account_currency,
            position_rows.asset_id,
            position_rows.asset_symbol,
            position_rows.position_id,
            position_rows.cost_basis_currency,
            position_rows.volume,
            position_rows.cost_basis_native,
            position_rows.market_price,
            position_rows.market_price_currency,
            position_rows.price_selection_source,
            position_rows.selected_price_date,
            position_rows.price_source,
            position_rows.market_price_source_symbol,
            position_rows.cost_basis_to_base_rate,
            position_rows.cost_basis_fx_status,
            position_rows.market_price_to_base_rate,
            position_rows.market_price_fx_status,
            position_rows.cost_basis_in_base_currency,
            position_rows.market_value_in_base_currency
           FROM position_rows
        ), row_values AS (
         SELECT pr.portfolio_id,
            pr.base_currency,
            pr.account_id,
            pr.account_currency,
            pr.asset_id,
            pr.asset_symbol,
            pr.position_id,
            pr.cost_basis_currency,
            pr.volume,
            pr.cost_basis_native,
            pr.market_price,
            pr.market_price_currency,
            pr.price_selection_source,
            pr.selected_price_date,
            pr.price_source,
            pr.market_price_source_symbol,
            pr.cost_basis_to_base_rate,
            pr.cost_basis_fx_status,
            pr.market_price_to_base_rate,
            pr.market_price_fx_status,
            pr.cost_basis_in_base_currency,
            pr.market_value_in_base_currency,
                CASE
                    WHEN (pr.market_price IS NULL) THEN NULL::numeric
                    ELSE ((pr.volume * pr.market_price) *
                    CASE
                        WHEN ((price.quality_class)::text ~~ '%PERCENT_OF_PAR%'::text) THEN 0.01
                        ELSE (1)::numeric
                    END)
                END AS market_value_native
           FROM (position_rows_with_fx pr
             LEFT JOIN investory.app_v_current_asset_price_mv price ON ((price.asset_id = pr.asset_id)))
        ), rollup AS (
         SELECT rv.portfolio_id,
            rv.base_currency,
            rv.account_id,
            rv.account_currency,
            rv.asset_id,
            min((rv.asset_symbol)::text) AS asset_symbol,
                CASE
                    WHEN (count(DISTINCT rv.cost_basis_currency) = 1) THEN (min((rv.cost_basis_currency)::text))::character varying
                    ELSE 'MIXED'::character varying(8)
                END AS cost_basis_currency,
                CASE
                    WHEN (count(DISTINCT rv.market_price_currency) = 1) THEN (min((rv.market_price_currency)::text))::character varying
                    ELSE 'MIXED'::character varying(8)
                END AS market_price_currency,
            count(*) AS position_row_count,
            count(DISTINCT rv.cost_basis_currency) AS cost_basis_currency_count,
            count(DISTINCT rv.market_price_currency) AS market_price_currency_count,
            sum(rv.volume) AS volume,
                CASE
                    WHEN (count(DISTINCT rv.cost_basis_currency) = 1) THEN (sum(rv.cost_basis_native) / NULLIF(sum(rv.volume), (0)::numeric))
                    ELSE NULL::numeric
                END AS average_open_price,
                CASE
                    WHEN (count(DISTINCT rv.cost_basis_currency) = 1) THEN sum(rv.cost_basis_native)
                    ELSE NULL::numeric
                END AS cost_basis,
                CASE
                    WHEN (count(DISTINCT rv.market_price_currency) = 1) THEN min(rv.market_price)
                    ELSE NULL::numeric
                END AS market_price,
                CASE
                    WHEN (count(DISTINCT rv.market_price_currency) = 1) THEN sum(rv.market_value_native)
                    ELSE NULL::numeric
                END AS market_value,
            sum(rv.cost_basis_in_base_currency) AS cost_basis_in_base_currency,
            sum(rv.market_value_in_base_currency) AS market_value_in_base_currency
           FROM row_values rv
          GROUP BY rv.portfolio_id, rv.base_currency, rv.account_id, rv.account_currency, rv.asset_id
        )
 SELECT portfolio_id,
    base_currency,
    account_id,
    account_currency,
    asset_id,
    asset_symbol,
    cost_basis_currency AS position_currency,
    market_price_currency AS market_currency,
    position_row_count,
    cost_basis_currency_count,
    market_price_currency_count,
    volume,
    average_open_price,
    cost_basis,
    market_price,
    market_value,
        CASE
            WHEN ((market_value IS NULL) OR (cost_basis IS NULL) OR ((market_price_currency)::text <> (cost_basis_currency)::text)) THEN NULL::numeric
            ELSE (market_value - cost_basis)
        END AS unrealized_pl,
    NULL::numeric AS position_to_base_rate,
    NULL::numeric AS market_to_base_rate,
    cost_basis_in_base_currency,
    market_value_in_base_currency,
        CASE
            WHEN ((market_value_in_base_currency IS NULL) OR (cost_basis_in_base_currency IS NULL)) THEN NULL::numeric
            ELSE (market_value_in_base_currency - cost_basis_in_base_currency)
        END AS unrealized_pl_in_base_currency,
    ((cost_basis_in_base_currency IS NOT NULL) AND (market_value_in_base_currency IS NOT NULL)) AS fx_rate_available
   FROM rollup r;

CREATE MATERIALIZED VIEW investory.app_v_portfolio_asset_allocation AS
 SELECT portfolio_id,
    base_currency,
    asset_id,
    asset_symbol,
    sum(volume) AS total_volume,
        CASE
            WHEN (count(DISTINCT market_currency) = 1) THEN min(market_price)
            ELSE NULL::numeric
        END AS market_price,
        CASE
            WHEN (count(DISTINCT market_currency) = 1) THEN min((market_currency)::text)
            ELSE NULL::text
        END AS market_price_currency,
        CASE
            WHEN (count(*) FILTER (WHERE (NOT fx_rate_available)) > 0) THEN NULL::numeric
            ELSE sum(cost_basis_in_base_currency)
        END AS cost_basis_in_base_currency,
        CASE
            WHEN (count(*) FILTER (WHERE (NOT fx_rate_available)) > 0) THEN NULL::numeric
            ELSE sum(market_value_in_base_currency)
        END AS total_value_in_base_currency,
        CASE
            WHEN (count(*) FILTER (WHERE (NOT fx_rate_available)) > 0) THEN NULL::numeric
            ELSE sum(unrealized_pl_in_base_currency)
        END AS unrealized_pl_in_base_currency,
    sum(market_value_in_base_currency) FILTER (WHERE fx_rate_available) AS converted_value_subtotal,
    count(*) FILTER (WHERE (NOT fx_rate_available)) AS missing_fx_count,
    (count(*) FILTER (WHERE (NOT fx_rate_available)) = 0) AS is_complete,
    now() AS updated_at
   FROM investory.app_v_open_position_values v
  GROUP BY portfolio_id, base_currency, asset_id, asset_symbol;

CREATE MATERIALIZED VIEW investory.app_v_portfolio_contribution_summary_mv AS
 WITH contribution_rows AS (
         SELECT p_1.id AS portfolio_id,
            p_1.base_currency,
            nco.portfolio_conversion_status,
                CASE
                    WHEN ((nco.normalized_category)::text = 'EXTERNAL_DEPOSIT'::text) THEN 'EXTERNAL_DEPOSIT'::text
                    WHEN ((nco.normalized_category)::text = 'EXTERNAL_WITHDRAWAL'::text) THEN 'EXTERNAL_WITHDRAWAL'::text
                    WHEN (((nco.normalized_category)::text = 'INTERNAL_BOOKKEEPING'::text) AND ((nco.comment)::text ~* 'transfer from [0-9]+ to [0-9]+'::text) AND (("substring"((nco.comment)::text, '(?i)to ([0-9]+)'::text))::bigint = nco.account_id) AND (nco.amount > (0)::numeric) AND (NOT (EXISTS ( SELECT 1
                       FROM investory.accounts counterparty
                      WHERE ((counterparty.id = ("substring"((nco.comment)::text, '(?i)transfer from ([0-9]+)'::text))::bigint) AND (counterparty.portfolio_id = ( SELECT source.portfolio_id
                               FROM investory.accounts source
                              WHERE (source.id = nco.account_id)))))))) THEN 'BOUNDARY_TRANSFER'::text
                    WHEN (((nco.normalized_category)::text = 'INTERNAL_BOOKKEEPING'::text) AND ((nco.comment)::text ~* 'transfer from [0-9]+ to [0-9]+'::text) AND (("substring"((nco.comment)::text, '(?i)transfer from ([0-9]+)'::text))::bigint = nco.account_id) AND (nco.amount < (0)::numeric) AND (NOT (EXISTS ( SELECT 1
                       FROM investory.accounts counterparty
                      WHERE ((counterparty.id = ("substring"((nco.comment)::text, '(?i)to ([0-9]+)'::text))::bigint) AND (counterparty.portfolio_id = ( SELECT source.portfolio_id
                               FROM investory.accounts source
                              WHERE (source.id = nco.account_id)))))))) THEN 'BOUNDARY_TRANSFER'::text
                    ELSE NULL::text
                END AS contribution_kind,
            nco.amount_in_portfolio_base_currency AS amount_in_base_currency
           FROM ((investory.app_v_normalized_cash_operations nco
             JOIN investory.accounts account ON ((account.id = nco.account_id)))
             JOIN investory.portfolios p_1 ON ((p_1.id = account.portfolio_id)))
        ), contribution_totals AS (
         SELECT contribution_rows.portfolio_id,
            sum(contribution_rows.amount_in_base_currency) FILTER (WHERE (contribution_rows.contribution_kind = 'EXTERNAL_DEPOSIT'::text)) AS external_deposits,
            sum((- contribution_rows.amount_in_base_currency)) FILTER (WHERE (contribution_rows.contribution_kind = 'EXTERNAL_WITHDRAWAL'::text)) AS external_withdrawals,
            sum(contribution_rows.amount_in_base_currency) FILTER (WHERE (contribution_rows.contribution_kind = 'BOUNDARY_TRANSFER'::text)) AS boundary_transfer_net,
            count(*) FILTER (WHERE ((contribution_rows.contribution_kind IS NOT NULL) AND (NOT investory.fx_status_usable(contribution_rows.portfolio_conversion_status)))) AS missing_fx_count
           FROM contribution_rows
          GROUP BY contribution_rows.portfolio_id
        )
 SELECT p.id AS portfolio_id,
    p.base_currency,
        CASE
            WHEN (COALESCE(t.missing_fx_count, (0)::bigint) > 0) THEN NULL::numeric
            ELSE (COALESCE(t.external_deposits, (0)::numeric) + GREATEST(COALESCE(t.boundary_transfer_net, (0)::numeric), (0)::numeric))
        END AS total_deposits,
        CASE
            WHEN (COALESCE(t.missing_fx_count, (0)::bigint) > 0) THEN NULL::numeric
            ELSE (COALESCE(t.external_withdrawals, (0)::numeric) + GREATEST((- COALESCE(t.boundary_transfer_net, (0)::numeric)), (0)::numeric))
        END AS total_withdrawals,
        CASE
            WHEN (COALESCE(t.missing_fx_count, (0)::bigint) > 0) THEN NULL::numeric
            ELSE ((COALESCE(t.external_deposits, (0)::numeric) - COALESCE(t.external_withdrawals, (0)::numeric)) + COALESCE(t.boundary_transfer_net, (0)::numeric))
        END AS net_deposits,
    COALESCE(t.external_deposits, (0)::numeric) AS external_deposits,
    COALESCE(t.external_withdrawals, (0)::numeric) AS external_withdrawals,
    COALESCE(t.boundary_transfer_net, (0)::numeric) AS boundary_transfer_net,
    COALESCE(t.missing_fx_count, (0)::bigint) AS missing_fx_count,
    (COALESCE(t.missing_fx_count, (0)::bigint) = 0) AS is_complete,
    now() AS updated_at
   FROM (investory.portfolios p
     LEFT JOIN contribution_totals t ON ((t.portfolio_id = p.id)));

CREATE MATERIALIZED VIEW investory.app_v_portfolio_currency_breakdown AS
 WITH latest_account_daily AS (
         SELECT DISTINCT ON (ad.account_id) ad.account_id,
            ad.valuation_currency,
            ad.cash_balance,
            ad.market_value,
            ad.snapshot_date
           FROM investory.account_daily ad
          ORDER BY ad.account_id, ad.snapshot_date DESC, ad.id DESC
        ), latest_with_fx AS (
         SELECT lad.account_id,
            lad.valuation_currency,
            lad.cash_balance,
            lad.market_value,
            lad.snapshot_date,
            a.portfolio_id,
            p.base_currency,
            fx.fx_rate_to_base,
            fx.conversion_status
           FROM (((latest_account_daily lad
             JOIN investory.accounts a ON ((a.id = lad.account_id)))
             JOIN investory.portfolios p ON ((p.id = a.portfolio_id)))
             LEFT JOIN investory.app_v_portfolio_daily_fx_rate_mv fx ON (((fx.portfolio_id = a.portfolio_id) AND (fx.valuation_date = lad.snapshot_date) AND ((fx.source_currency)::text = (lad.valuation_currency)::text))))
        ), account_latest AS (
         SELECT latest_with_fx.portfolio_id,
            latest_with_fx.base_currency,
            'ACCOUNT_LATEST'::character varying(32) AS metric_type,
            latest_with_fx.valuation_currency AS currency,
            sum((latest_with_fx.cash_balance + latest_with_fx.market_value)) AS amount_local,
                CASE
                    WHEN (count(*) FILTER (WHERE (NOT investory.fx_status_usable(latest_with_fx.conversion_status))) > 0) THEN NULL::numeric
                    ELSE sum(((latest_with_fx.cash_balance + latest_with_fx.market_value) * latest_with_fx.fx_rate_to_base))
                END AS amount_in_base_currency,
            count(*) FILTER (WHERE (NOT investory.fx_status_usable(latest_with_fx.conversion_status))) AS missing_fx_count,
            (count(*) FILTER (WHERE (NOT investory.fx_status_usable(latest_with_fx.conversion_status))) = 0) AS is_complete
           FROM latest_with_fx
          GROUP BY latest_with_fx.portfolio_id, latest_with_fx.base_currency, latest_with_fx.valuation_currency
        ), realized_components AS (
         SELECT a.portfolio_id,
            pf.base_currency,
            (p.close_time)::date AS valuation_date,
            p.profit_currency AS currency,
            (COALESCE(p.profit, (0)::numeric) + COALESCE(p.swap, (0)::numeric)) AS amount_local
           FROM ((investory.positions p
             JOIN investory.accounts a ON ((a.id = p.account_id)))
             JOIN investory.portfolios pf ON ((pf.id = a.portfolio_id)))
          WHERE (p.close_time IS NOT NULL)
        UNION ALL
         SELECT a.portfolio_id,
            pf.base_currency,
            (p.close_time)::date AS close_time,
            p.commission_currency,
            COALESCE(p.commission, (0)::numeric) AS "coalesce"
           FROM ((investory.positions p
             JOIN investory.accounts a ON ((a.id = p.account_id)))
             JOIN investory.portfolios pf ON ((pf.id = a.portfolio_id)))
          WHERE (p.close_time IS NOT NULL)
        ), realized_with_fx AS (
         SELECT rc.portfolio_id,
            rc.base_currency,
            rc.valuation_date,
            rc.currency,
            rc.amount_local,
            fx.fx_rate_to_target AS fx_rate_to_base,
            fx.conversion_status
           FROM (realized_components rc
             LEFT JOIN LATERAL investory.resolve_fx_rate(rc.valuation_date, rc.currency, rc.base_currency) fx(source_currency, target_currency, fx_rate_to_target, source, rate_method, rate_source, source_rate_date, age_days, conversion_status) ON (true))
        ), realized AS (
         SELECT realized_with_fx.portfolio_id,
            realized_with_fx.base_currency,
            'REALIZED'::character varying(32) AS metric_type,
            realized_with_fx.currency,
            sum(realized_with_fx.amount_local) AS amount_local,
                CASE
                    WHEN (count(*) FILTER (WHERE (NOT investory.fx_status_usable(realized_with_fx.conversion_status))) > 0) THEN NULL::numeric
                    ELSE sum((realized_with_fx.amount_local * realized_with_fx.fx_rate_to_base))
                END AS amount_in_base_currency,
            count(*) FILTER (WHERE (NOT investory.fx_status_usable(realized_with_fx.conversion_status))) AS missing_fx_count,
            (count(*) FILTER (WHERE (NOT investory.fx_status_usable(realized_with_fx.conversion_status))) = 0) AS is_complete
           FROM realized_with_fx
          GROUP BY realized_with_fx.portfolio_id, realized_with_fx.base_currency, realized_with_fx.currency
        ), unrealized_components AS (
         SELECT a.portfolio_id,
            pf.base_currency,
            p.profit_currency AS currency,
            (COALESCE(p.profit, (0)::numeric) + COALESCE(p.swap, (0)::numeric)) AS amount_local
           FROM ((investory.positions p
             JOIN investory.accounts a ON ((a.id = p.account_id)))
             JOIN investory.portfolios pf ON ((pf.id = a.portfolio_id)))
          WHERE (p.close_time IS NULL)
        UNION ALL
         SELECT a.portfolio_id,
            pf.base_currency,
            p.commission_currency,
            COALESCE(p.commission, (0)::numeric) AS "coalesce"
           FROM ((investory.positions p
             JOIN investory.accounts a ON ((a.id = p.account_id)))
             JOIN investory.portfolios pf ON ((pf.id = a.portfolio_id)))
          WHERE (p.close_time IS NULL)
        ), unrealized_with_fx AS (
         SELECT uc.portfolio_id,
            uc.base_currency,
            uc.currency,
            uc.amount_local,
            fx.fx_rate_to_base,
            fx.conversion_status
           FROM (unrealized_components uc
             LEFT JOIN investory.app_v_portfolio_daily_fx_rate_mv fx ON (((fx.portfolio_id = uc.portfolio_id) AND (fx.valuation_date = CURRENT_DATE) AND ((fx.source_currency)::text = (uc.currency)::text))))
        ), unrealized AS (
         SELECT unrealized_with_fx.portfolio_id,
            unrealized_with_fx.base_currency,
            'UNREALIZED'::character varying(32) AS metric_type,
            unrealized_with_fx.currency,
            sum(unrealized_with_fx.amount_local) AS amount_local,
                CASE
                    WHEN (count(*) FILTER (WHERE (NOT investory.fx_status_usable(unrealized_with_fx.conversion_status))) > 0) THEN NULL::numeric
                    ELSE sum((unrealized_with_fx.amount_local * unrealized_with_fx.fx_rate_to_base))
                END AS amount_in_base_currency,
            count(*) FILTER (WHERE (NOT investory.fx_status_usable(unrealized_with_fx.conversion_status))) AS missing_fx_count,
            (count(*) FILTER (WHERE (NOT investory.fx_status_usable(unrealized_with_fx.conversion_status))) = 0) AS is_complete
           FROM unrealized_with_fx
          GROUP BY unrealized_with_fx.portfolio_id, unrealized_with_fx.base_currency, unrealized_with_fx.currency
        ), dividends AS (
         SELECT a.portfolio_id,
            pf.base_currency,
            'DIVIDENDS'::character varying(32) AS metric_type,
            nco.currency,
            sum(nco.amount) AS amount_local,
                CASE
                    WHEN (count(*) FILTER (WHERE (NOT investory.fx_status_usable(nco.portfolio_conversion_status))) > 0) THEN NULL::numeric
                    ELSE sum(nco.amount_in_portfolio_base_currency)
                END AS amount_in_base_currency,
            count(*) FILTER (WHERE (NOT investory.fx_status_usable(nco.portfolio_conversion_status))) AS missing_fx_count,
            (count(*) FILTER (WHERE (NOT investory.fx_status_usable(nco.portfolio_conversion_status))) = 0) AS is_complete
           FROM ((investory.app_v_normalized_cash_operations nco
             JOIN investory.accounts a ON ((a.id = nco.account_id)))
             JOIN investory.portfolios pf ON ((pf.id = a.portfolio_id)))
          WHERE ((NOT a.cash_only) AND ((nco.normalized_category)::text = ANY (ARRAY[('DIVIDEND'::character varying)::text, ('DIVIDEND_REVERSAL'::character varying)::text])))
          GROUP BY a.portfolio_id, pf.base_currency, nco.currency
        )
 SELECT account_latest.portfolio_id,
    account_latest.base_currency,
    account_latest.metric_type,
    account_latest.currency,
    account_latest.amount_local,
    account_latest.amount_in_base_currency,
    account_latest.missing_fx_count,
    account_latest.is_complete,
    now() AS updated_at
   FROM account_latest
UNION ALL
 SELECT realized.portfolio_id,
    realized.base_currency,
    realized.metric_type,
    realized.currency,
    realized.amount_local,
    realized.amount_in_base_currency,
    realized.missing_fx_count,
    realized.is_complete,
    now() AS updated_at
   FROM realized
UNION ALL
 SELECT unrealized.portfolio_id,
    unrealized.base_currency,
    unrealized.metric_type,
    unrealized.currency,
    unrealized.amount_local,
    unrealized.amount_in_base_currency,
    unrealized.missing_fx_count,
    unrealized.is_complete,
    now() AS updated_at
   FROM unrealized
UNION ALL
 SELECT dividends.portfolio_id,
    dividends.base_currency,
    dividends.metric_type,
    dividends.currency,
    dividends.amount_local,
    dividends.amount_in_base_currency,
    dividends.missing_fx_count,
    dividends.is_complete,
    now() AS updated_at
   FROM dividends;

CREATE VIEW investory.app_v_portfolio_daily AS
 WITH account_rows_with_fx AS (
         SELECT a.portfolio_id,
            p.base_currency,
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
           FROM (((investory.account_daily ad
             JOIN investory.accounts a ON ((a.id = ad.account_id)))
             JOIN investory.portfolios p ON ((p.id = a.portfolio_id)))
             CROSS JOIN LATERAL investory.resolve_fx_rate(ad.snapshot_date, ad.valuation_currency, p.base_currency) fx(source_currency, target_currency, fx_rate_to_target, source, rate_method, rate_source, source_rate_date, age_days, conversion_status))
        ), account_rows AS (
         SELECT account_rows_with_fx.portfolio_id,
            account_rows_with_fx.base_currency,
            account_rows_with_fx.snapshot_date,
            account_rows_with_fx.account_id,
            account_rows_with_fx.conversion_status,
                CASE
                    WHEN investory.fx_status_usable(account_rows_with_fx.conversion_status) THEN (account_rows_with_fx.cash_balance * account_rows_with_fx.valuation_to_base_rate)
                    ELSE NULL::numeric
                END AS cash_balance,
                CASE
                    WHEN investory.fx_status_usable(account_rows_with_fx.conversion_status) THEN (account_rows_with_fx.market_value * account_rows_with_fx.valuation_to_base_rate)
                    ELSE NULL::numeric
                END AS market_value,
                CASE
                    WHEN investory.fx_status_usable(account_rows_with_fx.conversion_status) THEN (account_rows_with_fx.equity * account_rows_with_fx.valuation_to_base_rate)
                    ELSE NULL::numeric
                END AS equity,
                CASE
                    WHEN investory.fx_status_usable(account_rows_with_fx.conversion_status) THEN (account_rows_with_fx.dividends * account_rows_with_fx.valuation_to_base_rate)
                    ELSE NULL::numeric
                END AS dividends,
                CASE
                    WHEN investory.fx_status_usable(account_rows_with_fx.conversion_status) THEN (account_rows_with_fx.interest * account_rows_with_fx.valuation_to_base_rate)
                    ELSE NULL::numeric
                END AS interest,
                CASE
                    WHEN investory.fx_status_usable(account_rows_with_fx.conversion_status) THEN (account_rows_with_fx.fees * account_rows_with_fx.valuation_to_base_rate)
                    ELSE NULL::numeric
                END AS fees,
                CASE
                    WHEN investory.fx_status_usable(account_rows_with_fx.conversion_status) THEN (account_rows_with_fx.taxes * account_rows_with_fx.valuation_to_base_rate)
                    ELSE NULL::numeric
                END AS taxes,
                CASE
                    WHEN investory.fx_status_usable(account_rows_with_fx.conversion_status) THEN (account_rows_with_fx.realized_profit * account_rows_with_fx.valuation_to_base_rate)
                    ELSE NULL::numeric
                END AS realized_profit
           FROM account_rows_with_fx
        ), external_flows AS (
         SELECT app_v_normalized_cash_operation_flows.account_id,
            (app_v_normalized_cash_operation_flows.date)::date AS snapshot_date,
            sum(app_v_normalized_cash_operation_flows.portfolio_flow_amount_in_portfolio_base_currency) FILTER (WHERE (app_v_normalized_cash_operation_flows.portfolio_flow_amount_in_portfolio_base_currency > (0)::numeric)) AS deposits,
            sum((- app_v_normalized_cash_operation_flows.portfolio_flow_amount_in_portfolio_base_currency)) FILTER (WHERE (app_v_normalized_cash_operation_flows.portfolio_flow_amount_in_portfolio_base_currency < (0)::numeric)) AS withdrawals,
            count(*) FILTER (WHERE (NOT investory.fx_status_usable(app_v_normalized_cash_operation_flows.portfolio_conversion_status))) AS missing_flow_fx_count
           FROM investory.app_v_normalized_cash_operation_flows
          GROUP BY app_v_normalized_cash_operation_flows.account_id, ((app_v_normalized_cash_operation_flows.date)::date)
        )
 SELECT ar.portfolio_id,
    ar.snapshot_date,
    ar.base_currency,
        CASE
            WHEN (count(*) FILTER (WHERE (NOT investory.fx_status_usable(ar.conversion_status))) > 0) THEN NULL::numeric
            ELSE sum(ar.cash_balance)
        END AS cash_balance,
        CASE
            WHEN (count(*) FILTER (WHERE (NOT investory.fx_status_usable(ar.conversion_status))) > 0) THEN NULL::numeric
            ELSE sum(ar.market_value)
        END AS market_value,
        CASE
            WHEN (count(*) FILTER (WHERE (NOT investory.fx_status_usable(ar.conversion_status))) > 0) THEN NULL::numeric
            ELSE sum(ar.equity)
        END AS equity,
        CASE
            WHEN ((count(*) FILTER (WHERE (NOT investory.fx_status_usable(ar.conversion_status))) > 0) OR (max(COALESCE(ef.missing_flow_fx_count, (0)::bigint)) > 0)) THEN NULL::numeric
            ELSE sum(COALESCE(ef.deposits, (0)::numeric))
        END AS deposits,
        CASE
            WHEN ((count(*) FILTER (WHERE (NOT investory.fx_status_usable(ar.conversion_status))) > 0) OR (max(COALESCE(ef.missing_flow_fx_count, (0)::bigint)) > 0)) THEN NULL::numeric
            ELSE sum(COALESCE(ef.withdrawals, (0)::numeric))
        END AS withdrawals,
        CASE
            WHEN (count(*) FILTER (WHERE (NOT investory.fx_status_usable(ar.conversion_status))) > 0) THEN NULL::numeric
            ELSE sum(ar.dividends)
        END AS dividends,
        CASE
            WHEN (count(*) FILTER (WHERE (NOT investory.fx_status_usable(ar.conversion_status))) > 0) THEN NULL::numeric
            ELSE sum(ar.interest)
        END AS interest,
        CASE
            WHEN (count(*) FILTER (WHERE (NOT investory.fx_status_usable(ar.conversion_status))) > 0) THEN NULL::numeric
            ELSE sum(ar.fees)
        END AS fees,
        CASE
            WHEN (count(*) FILTER (WHERE (NOT investory.fx_status_usable(ar.conversion_status))) > 0) THEN NULL::numeric
            ELSE sum(ar.taxes)
        END AS taxes,
        CASE
            WHEN (count(*) FILTER (WHERE (NOT investory.fx_status_usable(ar.conversion_status))) > 0) THEN NULL::numeric
            ELSE sum(ar.realized_profit)
        END AS realized_profit,
        CASE
            WHEN ((count(*) FILTER (WHERE (NOT investory.fx_status_usable(ar.conversion_status))) > 0) OR (max(COALESCE(ef.missing_flow_fx_count, (0)::bigint)) > 0)) THEN NULL::numeric
            ELSE (((sum(ar.equity) - lag(sum(ar.equity)) OVER (PARTITION BY ar.portfolio_id ORDER BY ar.snapshot_date)) - sum(COALESCE(ef.deposits, (0)::numeric))) + sum(COALESCE(ef.withdrawals, (0)::numeric)))
        END AS total_profit,
    sum(ar.equity) AS converted_equity_subtotal,
    count(*) FILTER (WHERE (NOT investory.fx_status_usable(ar.conversion_status))) AS missing_fx_count,
    ((count(*) FILTER (WHERE (NOT investory.fx_status_usable(ar.conversion_status))) = 0) AND (max(COALESCE(ef.missing_flow_fx_count, (0)::bigint)) = 0)) AS is_complete,
        CASE
            WHEN ((count(*) FILTER (WHERE (NOT investory.fx_status_usable(ar.conversion_status))) > 0) OR (max(COALESCE(ef.missing_flow_fx_count, (0)::bigint)) > 0)) THEN NULL::numeric
            WHEN (lag(sum(ar.equity)) OVER (PARTITION BY ar.portfolio_id ORDER BY ar.snapshot_date) IS NULL) THEN NULL::numeric
            ELSE ((((sum(ar.equity) - lag(sum(ar.equity)) OVER (PARTITION BY ar.portfolio_id ORDER BY ar.snapshot_date)) - sum(COALESCE(ef.deposits, (0)::numeric))) + sum(COALESCE(ef.withdrawals, (0)::numeric))) / NULLIF(((lag(sum(ar.equity)) OVER (PARTITION BY ar.portfolio_id ORDER BY ar.snapshot_date) + sum(COALESCE(ef.deposits, (0)::numeric))) - sum(COALESCE(ef.withdrawals, (0)::numeric))), (0)::numeric))
        END AS daily_return_pct,
    now() AS updated_at
   FROM (account_rows ar
     LEFT JOIN external_flows ef ON (((ef.account_id = ar.account_id) AND (ef.snapshot_date = ar.snapshot_date))))
  GROUP BY ar.portfolio_id, ar.snapshot_date, ar.base_currency;

CREATE VIEW investory.app_v_portfolio_daily_fx_rate AS
 SELECT portfolio_id,
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
   FROM investory.app_v_portfolio_daily_fx_rate_mv;

CREATE MATERIALIZED VIEW investory.app_v_portfolio_kpi_summary_mv AS
 WITH latest_portfolio_daily AS (
         SELECT DISTINCT ON (pd.portfolio_id) pd.portfolio_id,
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
        ), latest_account_stats AS (
         SELECT a.portfolio_id,
            (sum(ast.missing_fx_count))::bigint AS missing_fx_count,
            sum(ast.converted_cash_subtotal) AS converted_cash_subtotal,
            sum(ast.cash_balance) AS total_cash,
            sum(ast.market_value) AS total_market_value,
            sum(ast.equity) AS total_equity,
            sum(ast.total_deposit) AS total_deposits,
            sum(ast.total_withdrawal) AS total_withdrawals,
            sum((ast.total_deposit - ast.total_withdrawal)) AS net_deposits,
            sum(ast.realized_profit) AS total_realized_profit,
            sum(ast.unrealized_profit) AS total_unrealized_profit,
            sum(ast.dividends) AS total_dividends,
            sum(ast.interest) AS total_interest,
            sum(ast.fees) AS total_fees,
            sum(ast.taxes) AS total_taxes,
            sum(ast.activity_count) AS activity_count,
            min(ast.first_activity_at) AS first_activity_at,
            max(ast.last_activity_at) AS last_activity_at
           FROM (investory.app_v_account_statistics ast
             JOIN investory.accounts a ON ((a.id = ast.account_id)))
          GROUP BY a.portfolio_id
        )
 SELECT p.id AS portfolio_id,
    p.name AS portfolio_name,
    p.base_currency,
        CASE
            WHEN (COALESCE(las.missing_fx_count, (0)::bigint) > 0) THEN NULL::numeric
            ELSE COALESCE(las.total_deposits, (0)::numeric)
        END AS total_deposits,
        CASE
            WHEN (COALESCE(las.missing_fx_count, (0)::bigint) > 0) THEN NULL::numeric
            ELSE COALESCE(las.total_withdrawals, (0)::numeric)
        END AS total_withdrawals,
        CASE
            WHEN (COALESCE(las.missing_fx_count, (0)::bigint) > 0) THEN NULL::numeric
            ELSE COALESCE(las.net_deposits, (0)::numeric)
        END AS net_deposits,
    las.total_cash,
    las.total_market_value,
    las.total_equity,
    las.total_realized_profit,
    las.total_unrealized_profit,
    las.total_dividends,
    las.total_interest,
    las.total_fees,
    las.total_taxes,
    COALESCE(las.converted_cash_subtotal, (0)::numeric) AS converted_cash_subtotal,
    COALESCE(lpd.converted_equity_subtotal, (0)::numeric) AS converted_equity_subtotal,
    COALESCE(las.missing_fx_count, (0)::bigint) AS missing_fx_count,
    (COALESCE(las.missing_fx_count, (0)::bigint) = 0) AS is_complete,
    COALESCE(las.activity_count, (0)::bigint) AS activity_count,
    las.first_activity_at,
    las.last_activity_at,
    lpd.snapshot_date AS source_max_date,
    now() AS updated_at
   FROM ((investory.portfolios p
     LEFT JOIN latest_portfolio_daily lpd ON ((lpd.portfolio_id = p.id)))
     LEFT JOIN latest_account_stats las ON ((las.portfolio_id = p.id)));

CREATE VIEW investory.app_v_portfolio_kpi_summary AS
 SELECT src.portfolio_id,
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
        CASE
            WHEN (contributions.net_deposits > (0)::numeric) THEN investory.application_display_value((((src.total_equity - contributions.net_deposits) / contributions.net_deposits) * (100)::numeric))
            ELSE (0)::numeric
        END AS roi_pct,
    investory.application_display_value(src.converted_cash_subtotal) AS converted_cash_subtotal,
    investory.application_display_value(src.converted_equity_subtotal) AS converted_equity_subtotal,
    GREATEST(src.missing_fx_count, contributions.missing_fx_count) AS missing_fx_count,
    (src.is_complete AND contributions.is_complete) AS is_complete,
    src.activity_count,
    src.first_activity_at,
    src.last_activity_at,
    src.source_max_date,
    GREATEST(src.updated_at, contributions.updated_at) AS updated_at
   FROM (investory.app_v_portfolio_kpi_summary_mv src
     JOIN investory.app_v_portfolio_contribution_summary_mv contributions ON ((contributions.portfolio_id = src.portfolio_id)));

CREATE VIEW investory.app_v_portfolio_tax_year_realized AS
 SELECT a.portfolio_id,
    (((a.portfolio_id)::text || ':'::text) || (EXTRACT(year FROM p.close_time))::text) AS portfolio_tax_year_key,
    (EXTRACT(year FROM p.close_time))::integer AS tax_year,
    sum((((COALESCE(p.profit, (0)::numeric) + COALESCE(p.swap, (0)::numeric)) * profit_fx.fx_rate_to_target) + (COALESCE(p.commission, (0)::numeric) * commission_fx.fx_rate_to_target))) AS realized_result
   FROM ((((investory.positions p
     JOIN investory.accounts a ON ((a.id = p.account_id)))
     JOIN investory.portfolios portfolio ON ((portfolio.id = a.portfolio_id)))
     CROSS JOIN LATERAL investory.resolve_fx_rate((p.close_time)::date, p.profit_currency, portfolio.base_currency) profit_fx(source_currency, target_currency, fx_rate_to_target, source, rate_method, rate_source, source_rate_date, age_days, conversion_status))
     CROSS JOIN LATERAL investory.resolve_fx_rate((p.close_time)::date, p.commission_currency, portfolio.base_currency) commission_fx(source_currency, target_currency, fx_rate_to_target, source, rate_method, rate_source, source_rate_date, age_days, conversion_status))
  WHERE ((p.close_time IS NOT NULL) AND investory.fx_status_usable(profit_fx.conversion_status) AND investory.fx_status_usable(commission_fx.conversion_status))
  GROUP BY a.portfolio_id, (EXTRACT(year FROM p.close_time));

CREATE MATERIALIZED VIEW investory.recon_v_reconstructed_cash_daily_mv AS
 WITH valuation_dates AS (
         SELECT DISTINCT ad.account_id,
            a.portfolio_id,
            ad.snapshot_date AS valuation_date
           FROM (investory.account_daily ad
             JOIN investory.accounts a ON ((a.id = ad.account_id)))
        ), cash_legs AS (
         SELECT vd.account_id,
            vd.portfolio_id,
            vd.valuation_date,
            nco.currency AS operation_currency,
            sum(
                CASE
                    WHEN ((nco.date)::date <= vd.valuation_date) THEN COALESCE(nco.amount, (0)::numeric)
                    ELSE NULL::numeric
                END) AS ending_native_cash,
            sum(
                CASE
                    WHEN ((nco.date)::date = vd.valuation_date) THEN COALESCE(nco.amount, (0)::numeric)
                    ELSE (0)::numeric
                END) AS daily_native_cash_movement
           FROM (valuation_dates vd
             JOIN investory.app_v_normalized_cash_operations nco ON ((nco.account_id = vd.account_id)))
          GROUP BY vd.account_id, vd.portfolio_id, vd.valuation_date, nco.currency
        )
 SELECT cl.account_id,
    cl.valuation_date,
    cl.operation_currency,
    cl.ending_native_cash,
    fx.fx_rate_to_base,
    cl.daily_native_cash_movement,
        CASE
            WHEN (NOT investory.fx_status_usable(fx.conversion_status)) THEN NULL::numeric
            ELSE (cl.daily_native_cash_movement * fx.fx_rate_to_base)
        END AS transaction_cash_movement_base,
        CASE
            WHEN (NOT investory.fx_status_usable(fx.conversion_status)) THEN NULL::numeric
            ELSE (cl.ending_native_cash * fx.fx_rate_to_base)
        END AS reconstructed_cash_component_base,
    (
        CASE
            WHEN (NOT investory.fx_status_usable(fx.conversion_status)) THEN 'FAIL'::text
            ELSE 'PASS'::text
        END)::character varying(16) AS status,
        CASE
            WHEN (NOT investory.fx_status_usable(fx.conversion_status)) THEN 'missing or stale FX for cash balance revaluation'::text
            ELSE 'cash reconstructed from normalized cash ledger'::text
        END AS validation_message
   FROM (cash_legs cl
     LEFT JOIN investory.app_v_portfolio_daily_fx_rate fx ON (((fx.portfolio_id = cl.portfolio_id) AND (fx.valuation_date = cl.valuation_date) AND ((fx.source_currency)::text = (cl.operation_currency)::text))));

CREATE VIEW investory.app_v_reconstructed_cash_daily AS
 SELECT account_id,
    valuation_date,
    operation_currency,
    ending_native_cash,
    fx_rate_to_base,
    daily_native_cash_movement,
    transaction_cash_movement_base,
    reconstructed_cash_component_base,
    status,
    validation_message
   FROM investory.recon_v_reconstructed_cash_daily_mv;

CREATE MATERIALIZED VIEW investory.recon_v_reconstructed_position_daily_mv AS
 WITH active_position_dates AS (
         SELECT p_1.account_id,
            account.portfolio_id,
            a.id AS asset_id,
            d.snapshot_date AS valuation_date,
            p_1.cost_currency AS acquisition_currency,
            a.asset_type,
            sum(investory.signed_position_quantity(p_1.operation, p_1.volume)) AS open_quantity,
            sum(
                CASE
                    WHEN (p_1.settlement_model = 'RESULT_ONLY'::investory.position_settlement_model) THEN (0)::numeric
                    ELSE investory.signed_position_quantity(p_1.operation, p_1.volume)
                END) AS market_quantity,
            sum(COALESCE(p_1.purchase_value, (COALESCE(p_1.volume, (0)::numeric) * COALESCE(p_1.open_price, (0)::numeric)), (0)::numeric)) AS reconstructed_cost_base_local,
            sum(
                CASE
                    WHEN (NOT investory.fx_status_usable(acq.conversion_status)) THEN NULL::numeric
                    ELSE (COALESCE(p_1.purchase_value, (COALESCE(p_1.volume, (0)::numeric) * COALESCE(p_1.open_price, (0)::numeric)), (0)::numeric) * acq.fx_rate_to_base)
                END) AS reconstructed_cost_base_base
           FROM ((((investory.positions p_1
             JOIN investory.assets a ON (((a.id = p_1.asset_id) AND (a.exclude_from_import = false))))
             JOIN investory.accounts account ON ((account.id = p_1.account_id)))
             JOIN investory.account_daily d ON (((d.account_id = p_1.account_id) AND (d.snapshot_date >= COALESCE((p_1.open_time)::date, d.snapshot_date)) AND ((p_1.close_time IS NULL) OR (d.snapshot_date < (p_1.close_time)::date)))))
             LEFT JOIN investory.app_v_portfolio_daily_fx_rate acq ON (((acq.portfolio_id = account.portfolio_id) AND (acq.valuation_date = COALESCE((p_1.open_time)::date, d.snapshot_date)) AND ((acq.source_currency)::text = (p_1.cost_currency)::text))))
          GROUP BY p_1.account_id, account.portfolio_id, a.id, d.snapshot_date, p_1.cost_currency, a.asset_type
        ), priced AS (
         SELECT apd.account_id,
            apd.portfolio_id,
            apd.asset_id,
            apd.valuation_date,
            apd.acquisition_currency,
            apd.asset_type,
            apd.open_quantity,
            apd.market_quantity,
            apd.reconstructed_cost_base_local,
            apd.reconstructed_cost_base_base,
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
                    WHEN ((ndp.quality_class)::text ~~ '%PERCENT_OF_PAR%'::text) THEN 0.01
                    ELSE (1)::numeric
                END AS contract_multiplier
           FROM (active_position_dates apd
             LEFT JOIN investory.app_v_normalized_daily_price ndp ON (((ndp.asset_id = apd.asset_id) AND (ndp.valuation_date = apd.valuation_date))))
        )
 SELECT p.account_id,
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
            WHEN (p.market_quantity = (0)::numeric) THEN (0)::numeric
            WHEN ((p.selected_price IS NULL) OR (p.contract_multiplier IS NULL) OR (NOT investory.fx_status_usable(val.conversion_status))) THEN NULL::numeric
            ELSE (((p.market_quantity * p.selected_price) * p.contract_multiplier) * val.fx_rate_to_base)
        END AS reconstructed_market_value_base,
        CASE
            WHEN (p.market_quantity = (0)::numeric) THEN (- p.reconstructed_cost_base_base)
            WHEN ((p.selected_price IS NULL) OR (p.contract_multiplier IS NULL) OR (NOT investory.fx_status_usable(val.conversion_status)) OR (p.reconstructed_cost_base_base IS NULL)) THEN NULL::numeric
            ELSE ((((p.open_quantity * p.selected_price) * p.contract_multiplier) * val.fx_rate_to_base) - p.reconstructed_cost_base_base)
        END AS reconstructed_unrealized_profit_base,
    p.price_origin,
    p.price_quality,
    (
        CASE
            WHEN (p.open_quantity = (0)::numeric) THEN 'PASS'::text
            WHEN ((p.selected_price IS NULL) OR (p.contract_multiplier IS NULL) OR (NOT investory.fx_status_usable(val.conversion_status)) OR (p.reconstructed_cost_base_base IS NULL)) THEN 'FAIL'::text
            WHEN ((p.selection_priority >= 5) AND (p.selection_priority <> 6) AND (COALESCE((p.price_quality)::text, ''::text) !~~ 'EXACT_LISTING_MARKET_CLOSE%'::text) AND (COALESCE((p.price_quality)::text, ''::text) !~~ 'STALE_CARRY_FORWARD%'::text)) THEN 'WARN'::text
            ELSE 'PASS'::text
        END)::character varying(16) AS reconstruction_status,
        CASE
            WHEN (p.open_quantity = (0)::numeric) THEN 'zero quantity -> zero valuation'::text
            WHEN (p.selected_price IS NULL) THEN 'missing valuation price'::text
            WHEN (p.contract_multiplier IS NULL) THEN 'missing explicit multiplier metadata'::text
            WHEN (NOT investory.fx_status_usable(val.conversion_status)) THEN 'missing or stale valuation FX'::text
            WHEN (p.reconstructed_cost_base_base IS NULL) THEN 'missing acquisition FX'::text
            WHEN ((p.selection_priority >= 5) AND (p.selection_priority <> 6) AND (COALESCE((p.price_quality)::text, ''::text) !~~ 'EXACT_LISTING_MARKET_CLOSE%'::text) AND (COALESCE((p.price_quality)::text, ''::text) !~~ 'STALE_CARRY_FORWARD%'::text)) THEN p.price_validation_message
            WHEN (p.selection_priority = 6) THEN 'trade observation accepted for position valuation'::text
            ELSE 'reconstructed from positions + selected daily price + FX'::text
        END AS reconstruction_message
   FROM (priced p
     LEFT JOIN investory.app_v_portfolio_daily_fx_rate val ON (((val.portfolio_id = p.portfolio_id) AND (val.valuation_date = p.valuation_date) AND ((val.source_currency)::text = (p.price_currency)::text))));

CREATE VIEW investory.app_v_reconstructed_position_daily AS
 SELECT account_id,
    asset_id,
    valuation_date,
    open_quantity,
    reconstructed_cost_base_local,
    reconstructed_cost_base_base,
    acquisition_currency,
    selected_price,
    selected_price_date,
    underlying_observation_date,
    price_age_days,
    price_currency,
    contract_multiplier,
    selection_priority,
    base_currency,
    fx_rate_to_base,
    fx_conversion_status,
    reconstructed_market_value_base,
    reconstructed_unrealized_profit_base,
    price_origin,
    price_quality,
    reconstruction_status,
    reconstruction_message
   FROM investory.recon_v_reconstructed_position_daily_mv;

CREATE MATERIALIZED VIEW investory.app_v_symbol_performance AS
 WITH latest_positions AS (
         SELECT v.portfolio_id,
            v.asset_id,
                CASE
                    WHEN (count(*) FILTER (WHERE (NOT v.fx_rate_available)) > 0) THEN NULL::numeric
                    ELSE sum(v.unrealized_pl_in_base_currency)
                END AS unrealized_profit,
                CASE
                    WHEN (count(*) FILTER (WHERE (NOT v.fx_rate_available)) > 0) THEN NULL::numeric
                    ELSE sum(v.cost_basis_in_base_currency)
                END AS cost_basis,
                CASE
                    WHEN (count(*) FILTER (WHERE (NOT v.fx_rate_available)) > 0) THEN NULL::numeric
                    ELSE sum(v.market_value_in_base_currency)
                END AS market_value,
            count(*) FILTER (WHERE (NOT v.fx_rate_available)) AS missing_fx_count,
            (count(*) FILTER (WHERE (NOT v.fx_rate_available)) = 0) AS is_complete,
            sum(v.volume) AS total_volume
           FROM investory.app_v_open_position_values v
          GROUP BY v.portfolio_id, v.asset_id
        ), closed_position_components AS (
         SELECT a.portfolio_id,
            asset_1.id AS asset_id,
            p.profit_currency AS source_currency,
            pf.base_currency,
            (p.close_time)::date AS valuation_date,
            (COALESCE(p.profit, (0)::numeric) + COALESCE(p.swap, (0)::numeric)) AS amount_native
           FROM (((investory.positions p
             JOIN investory.accounts a ON ((a.id = p.account_id)))
             JOIN investory.portfolios pf ON ((pf.id = a.portfolio_id)))
             JOIN investory.assets asset_1 ON (((asset_1.id = p.asset_id) AND (asset_1.exclude_from_import = false))))
          WHERE ((p.close_time IS NOT NULL) AND (p.asset_id IS NOT NULL))
        UNION ALL
         SELECT a.portfolio_id,
            asset_1.id,
            p.commission_currency,
            pf.base_currency,
            (p.close_time)::date AS close_time,
            COALESCE(p.commission, (0)::numeric) AS "coalesce"
           FROM (((investory.positions p
             JOIN investory.accounts a ON ((a.id = p.account_id)))
             JOIN investory.portfolios pf ON ((pf.id = a.portfolio_id)))
             JOIN investory.assets asset_1 ON (((asset_1.id = p.asset_id) AND (asset_1.exclude_from_import = false))))
          WHERE ((p.close_time IS NOT NULL) AND (p.asset_id IS NOT NULL))
        ), closed_positions AS (
         SELECT cpr.portfolio_id,
            cpr.asset_id,
            sum(
                CASE
                    WHEN investory.fx_status_usable(fx.conversion_status) THEN (cpr.amount_native * fx.fx_rate_to_base)
                    ELSE NULL::numeric
                END) AS closed_profit,
            count(*) FILTER (WHERE (NOT investory.fx_status_usable(fx.conversion_status))) AS missing_fx_count,
            (count(*) FILTER (WHERE (NOT investory.fx_status_usable(fx.conversion_status))) = 0) AS is_complete
           FROM (closed_position_components cpr
             LEFT JOIN investory.app_v_portfolio_daily_fx_rate_mv fx ON (((fx.portfolio_id = cpr.portfolio_id) AND (fx.valuation_date = cpr.valuation_date) AND ((fx.source_currency)::text = (cpr.source_currency)::text))))
          GROUP BY cpr.portfolio_id, cpr.asset_id
        ), cash_dividends AS (
         SELECT a.portfolio_id,
            asset_1.id AS asset_id,
            sum(nco.amount_in_portfolio_base_currency) FILTER (WHERE ((nco.normalized_category)::text = ANY (ARRAY[('DIVIDEND'::character varying)::text, ('DIVIDEND_REVERSAL'::character varying)::text]))) AS dividends,
            sum((- nco.amount_in_portfolio_base_currency)) FILTER (WHERE ((nco.normalized_category)::text = ANY (ARRAY[('WITHHOLDING_TAX'::character varying)::text, ('WITHHOLDING_TAX_REVERSAL'::character varying)::text]))) AS withholding_tax,
            count(*) FILTER (WHERE (NOT investory.fx_status_usable(nco.portfolio_conversion_status))) AS missing_fx_count,
            (count(*) FILTER (WHERE (NOT investory.fx_status_usable(nco.portfolio_conversion_status))) = 0) AS is_complete
           FROM ((investory.app_v_normalized_cash_operations nco
             JOIN investory.accounts a ON ((a.id = nco.account_id)))
             JOIN investory.assets asset_1 ON (((asset_1.id = nco.asset_id) AND (asset_1.exclude_from_import = false))))
          GROUP BY a.portfolio_id, asset_1.id
        )
 SELECT COALESCE(lp.portfolio_id, cp.portfolio_id, cd.portfolio_id) AS portfolio_id,
    asset.symbol,
    asset.id AS asset_id,
        CASE
            WHEN COALESCE(cp.is_complete, true) THEN COALESCE(cp.closed_profit, (0)::numeric)
            ELSE NULL::numeric
        END AS closed_profit,
        CASE
            WHEN COALESCE(lp.is_complete, true) THEN COALESCE(lp.unrealized_profit, (0)::numeric)
            ELSE NULL::numeric
        END AS unrealized_profit,
        CASE
            WHEN (COALESCE(lp.is_complete, true) AND COALESCE(cp.is_complete, true) AND COALESCE(cd.is_complete, true)) THEN (((COALESCE(cp.closed_profit, (0)::numeric) + COALESCE(lp.unrealized_profit, (0)::numeric)) + COALESCE(cd.dividends, (0)::numeric)) - COALESCE(cd.withholding_tax, (0)::numeric))
            ELSE NULL::numeric
        END AS total_profit,
        CASE
            WHEN COALESCE(cd.is_complete, true) THEN COALESCE(cd.dividends, (0)::numeric)
            ELSE NULL::numeric
        END AS dividends,
        CASE
            WHEN COALESCE(cd.is_complete, true) THEN COALESCE(cd.withholding_tax, (0)::numeric)
            ELSE NULL::numeric
        END AS withholding_tax,
    COALESCE(lp.total_volume, (0)::numeric) AS total_volume,
        CASE
            WHEN COALESCE(lp.is_complete, true) THEN COALESCE(lp.cost_basis, (0)::numeric)
            ELSE NULL::numeric
        END AS cost_basis,
        CASE
            WHEN COALESCE(lp.is_complete, true) THEN COALESCE(lp.market_value, (0)::numeric)
            ELSE NULL::numeric
        END AS market_value,
    ((COALESCE(lp.missing_fx_count, (0)::bigint) + COALESCE(cp.missing_fx_count, (0)::bigint)) + COALESCE(cd.missing_fx_count, (0)::bigint)) AS missing_fx_count,
    (COALESCE(lp.is_complete, true) AND COALESCE(cp.is_complete, true) AND COALESCE(cd.is_complete, true)) AS is_complete,
    now() AS updated_at
   FROM (((latest_positions lp
     FULL JOIN closed_positions cp ON (((cp.portfolio_id = lp.portfolio_id) AND (cp.asset_id = lp.asset_id))))
     FULL JOIN cash_dividends cd ON (((cd.portfolio_id = COALESCE(lp.portfolio_id, cp.portfolio_id)) AND (cd.asset_id = COALESCE(lp.asset_id, cp.asset_id)))))
     JOIN investory.assets asset ON ((asset.id = COALESCE(lp.asset_id, cp.asset_id, cd.asset_id))));

CREATE INDEX ix_app_v_canonical_asset_daily_price_ranked_mv_order ON investory.app_v_canonical_asset_daily_price_ranked_mv USING btree (asset_id, effective_observation_date DESC, selection_priority, quality_score DESC, price_date DESC, source, source_symbol);

CREATE INDEX ix_app_v_portfolio_daily_fx_rate_mv_date ON investory.app_v_portfolio_daily_fx_rate_mv USING btree (valuation_date);

CREATE INDEX ix_recon_v_reconstructed_cash_daily_mv_valuation_date ON investory.recon_v_reconstructed_cash_daily_mv USING btree (valuation_date);

CREATE INDEX ix_recon_v_reconstructed_position_daily_mv_account_date ON investory.recon_v_reconstructed_position_daily_mv USING btree (account_id, valuation_date);

CREATE INDEX ix_recon_v_reconstructed_position_daily_mv_valuation_date ON investory.recon_v_reconstructed_position_daily_mv USING btree (valuation_date);

CREATE UNIQUE INDEX ux_app_v_canonical_asset_daily_price_mv_key ON investory.app_v_canonical_asset_daily_price_mv USING btree (asset_id, price_date);

CREATE UNIQUE INDEX ux_app_v_canonical_asset_daily_price_ranked_mv_key ON investory.app_v_canonical_asset_daily_price_ranked_mv USING btree (asset_id, price_date);

CREATE UNIQUE INDEX ux_app_v_current_asset_price_mv_asset ON investory.app_v_current_asset_price_mv USING btree (asset_id);

CREATE UNIQUE INDEX ux_app_v_normalized_daily_price_mv_key ON investory.app_v_normalized_daily_price_mv USING btree (asset_id, valuation_date);

CREATE UNIQUE INDEX ux_app_v_portfolio_daily_fx_rate_mv_key ON investory.app_v_portfolio_daily_fx_rate_mv USING btree (portfolio_id, valuation_date, source_currency);

CREATE UNIQUE INDEX ux_app_v_portfolio_kpi_summary_mv_portfolio ON investory.app_v_portfolio_kpi_summary_mv USING btree (portfolio_id);

CREATE UNIQUE INDEX ux_mv_account_monthly_account_month ON investory.app_v_account_monthly USING btree (account_id, month);

CREATE UNIQUE INDEX ux_mv_account_statistics_account ON investory.app_v_account_statistics USING btree (account_id);

CREATE UNIQUE INDEX ux_mv_app_v_portfolio_contribution_summary_mv_portfolio ON investory.app_v_portfolio_contribution_summary_mv USING btree (portfolio_id);

CREATE UNIQUE INDEX ux_mv_portfolio_asset_allocation_key ON investory.app_v_portfolio_asset_allocation USING btree (portfolio_id, asset_id);

CREATE UNIQUE INDEX ux_mv_portfolio_currency_breakdown_key ON investory.app_v_portfolio_currency_breakdown USING btree (portfolio_id, metric_type, currency);

CREATE UNIQUE INDEX ux_mv_portfolio_monthly_portfolio_month ON investory.app_v_portfolio_monthly USING btree (portfolio_id, month);

CREATE UNIQUE INDEX ux_mv_symbol_performance_key ON investory.app_v_symbol_performance USING btree (portfolio_id, asset_id);

CREATE UNIQUE INDEX ux_normalized_cash_operations ON investory.app_v_normalized_cash_operations USING btree (operation_id);

CREATE UNIQUE INDEX ux_recon_v_reconstructed_cash_daily_mv_key ON investory.recon_v_reconstructed_cash_daily_mv USING btree (account_id, valuation_date, operation_currency);

CREATE UNIQUE INDEX ux_recon_v_reconstructed_position_daily_mv_key ON investory.recon_v_reconstructed_position_daily_mv USING btree (account_id, asset_id, valuation_date, acquisition_currency);
