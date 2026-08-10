/*
 * IBKR direct fixed-income contract.
 *
 * The existing Treasury seed used the broker display symbol as the canonical
 * asset identity and stored face quantities as bond units. Keep the asset row
 * and its history, but move it to the stable security identity and face-value
 * quantity contract.
 */

UPDATE investory.assets
SET symbol = 'US91282CKB62',
    ticker = 'US91282CKB62',
    ibkr = 'T458022826',
    yahoo = 'US91282CKB62',
    isin = 'US91282CKB62',
    asset_type = 'BOND',
    currency = 'USD',
    country = 'US'
WHERE symbol = 'T458022826.US'
  AND isin = 'US91282CKB62';

UPDATE investory.positions
SET volume = volume * 1000,
    open_price = open_price / 1000,
    source_open_price = source_open_price / 1000,
    close_price = close_price / 1000,
    source_close_price = source_close_price / 1000
WHERE asset_id = (
    SELECT id
    FROM investory.assets
    WHERE symbol = 'US91282CKB62'
      AND asset_type = 'BOND'
);

UPDATE investory.asset_price_history
SET open_price = open_price / 10,
    high_price = high_price / 10,
    low_price = low_price / 10,
    close_price = close_price / 10,
    quality_class = 'IBKR_TRADE_OBSERVATION_PERCENT_OF_PAR',
    price_scale_factor = 1,
    scale_reason = 'IBKR direct bond quote is percent of par'
WHERE asset_id = (
    SELECT id
    FROM investory.assets
    WHERE symbol = 'US91282CKB62'
      AND asset_type = 'BOND'
)
  AND source = 'IBKR'
  AND quality_class NOT LIKE '%PERCENT_OF_PAR%';

COMMENT ON COLUMN investory.assets.symbol IS
    'Canonical asset identity. Direct IBKR fixed-income assets use the security identifier when no exchange ticker exists.';

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
        co.execution_fx_base,
        co.execution_fx_to_currency,
        co.execution_fx_rate,
        co.execution_fx_observed_at,
        co.execution_fx_source,
        date_trunc('month', co.date AT TIME ZONE 'Europe/Warsaw')::date AS rate_month,
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
             AND lower(COALESCE(co.comment, '')) ~* '(full call|early redemption|redemption)'
             AND lower(COALESCE(co.comment, '')) LIKE '%per bond%'
             AND co.asset_id IS NOT NULL
                THEN 'BOND_REDEMPTION'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) IN ('cash transfer')
             AND co.amount > 0
                THEN 'EXTERNAL_DEPOSIT'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) LIKE 'cash transfer |%'
             AND co.amount > 0
                THEN 'EXTERNAL_DEPOSIT'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) LIKE 'cash transfer|%'
             AND co.amount > 0
                THEN 'EXTERNAL_DEPOSIT'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) IN ('cash transfer')
             AND co.amount < 0
                THEN 'EXTERNAL_WITHDRAWAL'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) LIKE 'cash transfer |%'
             AND co.amount < 0
                THEN 'EXTERNAL_WITHDRAWAL'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) LIKE 'cash transfer|%'
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
            WHEN co.operation = 'DIVIDEND' AND co.amount < 0
                THEN 'DIVIDEND_REVERSAL'
            WHEN co.operation = 'DIVIDEND'
                THEN 'DIVIDEND'
            WHEN co.operation = 'FREE_FUNDS_INTEREST' AND co.amount < 0
                THEN 'INTEREST_REVERSAL'
            WHEN co.operation = 'FREE_FUNDS_INTEREST'
                THEN 'INTEREST'
            WHEN co.operation = 'WITHHOLDING_TAX' AND co.amount > 0
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
            WHEN co.operation = 'DEPOSIT' AND co.amount > 0
                THEN 'EXTERNAL_DEPOSIT'
            WHEN co.operation = 'WITHDRAWAL' AND co.amount < 0
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
            WHEN co.operation = 'DEPOSIT' AND COALESCE(co.amount, 0) = 0
                THEN 'ZERO_DEPOSIT'
            WHEN co.operation = 'DEPOSIT' AND co.amount < 0
                THEN 'RAW_DEPOSIT_NEGATIVE_REVIEW'
            WHEN co.operation = 'DEPOSIT' AND co.amount > 0
                THEN 'RAW_DEPOSIT'
            WHEN co.operation = 'WITHDRAWAL' AND co.amount < 0
                THEN 'RAW_WITHDRAWAL'
            WHEN co.operation = 'WITHDRAWAL' AND co.amount > 0
                THEN 'RAW_WITHDRAWAL_POSITIVE_REVIEW'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) ~* '(full call|early redemption|redemption)'
             AND lower(COALESCE(co.comment, '')) LIKE '%per bond%'
             AND co.asset_id IS NOT NULL
                THEN 'IBKR_BOND_REDEMPTION'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) LIKE 'cash transfer%'
             AND co.amount > 0
                THEN 'EXTERNAL_DEPOSIT'
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) LIKE 'cash transfer%'
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
            WHEN co.operation = 'DIVIDEND' AND co.amount < 0
                THEN 'DIVIDEND_REVERSAL'
            WHEN co.operation = 'DIVIDEND'
                THEN 'DIVIDEND'
            WHEN co.operation = 'FREE_FUNDS_INTEREST' AND co.amount < 0
                THEN 'INTEREST_REVERSAL'
            WHEN co.operation = 'FREE_FUNDS_INTEREST'
                THEN 'INTEREST'
            WHEN co.operation = 'WITHHOLDING_TAX' AND co.amount > 0
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
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) ~* '(full call|early redemption|redemption)'
             AND lower(COALESCE(co.comment, '')) LIKE '%per bond%'
             AND co.asset_id IS NOT NULL
                THEN 'IBKR fixed-income redemption settlement'
            WHEN co.operation = 'DEPOSIT'
             AND lower(COALESCE(co.comment, '')) LIKE 'transfer out operation on account%'
                THEN 'explicit transfer out comment'
            WHEN co.operation = 'DEPOSIT'
             AND lower(COALESCE(co.comment, '')) LIKE 'transfer in operation on account%'
                THEN 'explicit transfer in comment'
            WHEN co.operation = 'DEPOSIT' AND COALESCE(co.amount, 0) = 0
                THEN 'zero raw deposit requires review'
            WHEN co.operation = 'DEPOSIT' AND co.amount < 0
                THEN 'negative raw deposit requires review'
            WHEN co.operation = 'WITHDRAWAL' AND co.amount > 0
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
            WHEN co.operation = 'DIVIDEND' AND co.amount < 0
                THEN 'negative/correction dividend'
            WHEN co.operation = 'FREE_FUNDS_INTEREST' AND co.amount < 0
                THEN 'negative/correction interest'
            WHEN co.operation = 'WITHHOLDING_TAX' AND co.amount > 0
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
             AND lower(COALESCE(co.comment, '')) LIKE 'cash transfer%'
                THEN true
            WHEN co.operation = 'DEPOSIT' AND co.amount > 0
                THEN true
            WHEN co.operation = 'WITHDRAWAL' AND co.amount < 0
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
            WHEN co.operation = 'TRANSFER'
             AND lower(COALESCE(co.comment, '')) ~* '(full call|early redemption|redemption)'
             AND lower(COALESCE(co.comment, '')) LIKE '%per bond%'
             AND co.asset_id IS NOT NULL
                THEN true
            ELSE false
        END AS is_trade_cash_flow,
        CASE WHEN co.operation = 'CORRECTION' THEN true ELSE false END AS is_correction,
        CASE
            WHEN co.operation = 'DIVIDEND' AND co.amount < 0 THEN true
            WHEN co.operation = 'FREE_FUNDS_INTEREST' AND co.amount < 0 THEN true
            WHEN co.operation = 'WITHHOLDING_TAX' AND co.amount > 0 THEN true
            WHEN co.operation = 'WITHDRAWAL' AND co.amount > 0 THEN true
            ELSE false
        END AS is_reversal
    FROM investory.cash_operations co
    JOIN investory.accounts a ON a.id = co.account_id
    JOIN investory.portfolios p ON p.id = a.portfolio_id
), fx AS (
    SELECT
        c.*,
        CASE WHEN c.is_fx_conversion AND transaction_fx.conversion_status = 'OK'
             THEN transaction_fx.fx_rate_to_target ELSE portfolio_fx.fx_rate_to_base END AS fx_rate_to_base,
        CASE WHEN c.is_fx_conversion AND transaction_fx.conversion_status = 'OK'
             THEN transaction_fx.source ELSE portfolio_fx.source END AS portfolio_fx_source,
        CASE WHEN c.is_fx_conversion AND transaction_fx.conversion_status = 'OK'
             THEN transaction_fx.source_rate_date ELSE portfolio_fx.source_rate_date END AS portfolio_source_rate_date,
        CASE WHEN c.is_fx_conversion AND transaction_fx.conversion_status = 'OK'
             THEN transaction_fx.age_days ELSE portfolio_fx.age_days END AS portfolio_fx_age_days,
        CASE WHEN c.is_fx_conversion AND transaction_fx.conversion_status = 'OK'
             THEN transaction_fx.conversion_status ELSE portfolio_fx.conversion_status END AS portfolio_conversion_status,
        account_fx.fx_rate_to_target AS fx_rate_to_account_currency,
        account_fx.source AS account_fx_source,
        account_fx.source_rate_date AS account_source_rate_date,
        account_fx.age_days AS account_fx_age_days,
        account_fx.conversion_status AS account_conversion_status
    FROM classified c
    CROSS JOIN LATERAL investory.resolve_portfolio_fx_rate(
        c.portfolio_id,
        (c.date AT TIME ZONE 'Europe/Warsaw')::date,
        c.currency
    ) portfolio_fx
    CROSS JOIN LATERAL investory.resolve_fx_rate(
        (c.date AT TIME ZONE 'Europe/Warsaw')::date,
        c.currency,
        c.account_currency
    ) account_fx
    LEFT JOIN LATERAL (
        SELECT c.execution_fx_rate AS fx_rate_to_target,
               ('EXECUTION:' || c.execution_fx_source)::varchar(64) AS source,
               c.execution_fx_observed_at::date AS source_rate_date,
               0::integer AS age_days,
               'OK'::varchar(32) AS conversion_status
        WHERE c.execution_fx_base = c.currency
          AND c.execution_fx_to_currency = c.base_currency
          AND c.execution_fx_rate > 0
        UNION ALL
        SELECT 1 / c.execution_fx_rate,
               ('EXECUTION:' || c.execution_fx_source)::varchar(64),
               c.execution_fx_observed_at::date,
               0::integer,
               'OK'::varchar(32)
        WHERE c.execution_fx_base = c.base_currency
          AND c.execution_fx_to_currency = c.currency
          AND c.execution_fx_rate > 0
        LIMIT 1
    ) transaction_fx ON true
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
    CASE WHEN portfolio_conversion_status IN ('OK', 'ESTIMATED', 'SAME_CURRENCY')
        THEN amount * fx_rate_to_base END AS amount_in_portfolio_base_currency,
    CASE WHEN portfolio_conversion_status IN ('OK', 'ESTIMATED', 'SAME_CURRENCY')
        THEN amount * fx_rate_to_base END AS amount_in_base_currency,
    CASE WHEN account_conversion_status IN ('OK', 'ESTIMATED', 'SAME_CURRENCY')
        THEN amount * fx_rate_to_account_currency END AS amount_in_account_currency,
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
    'sql-v2026-08-09-ibkr-bond'::varchar(64) AS classification_version,
    portfolio_conversion_status AS conversion_status
FROM fx;

COMMENT ON VIEW investory.normalized_cash_operations IS
    'Canonical classified cash ledger. IBKR fixed-income redemptions are settlement cash, not external funding.';
