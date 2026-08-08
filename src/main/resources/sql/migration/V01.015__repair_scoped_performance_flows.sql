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
