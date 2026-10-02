SET search_path TO investory, public;

CREATE OR REPLACE VIEW investory.recon_v_account_daily_cashflow_full_precision AS
WITH ledger_daily AS (
    SELECT nco.account_id,
           (nco.date AT TIME ZONE 'Europe/Warsaw')::date AS snapshot_date,
           nco.account_currency::varchar(3) AS account_currency,
           nco.base_currency::varchar(3) AS base_currency,
           SUM(nco.amount_in_portfolio_base_currency) FILTER (
               WHERE investory.fx_status_usable(nco.portfolio_conversion_status)) AS ledger_cash_base,
           SUM(nco.account_flow_amount_in_portfolio_base_currency) FILTER (
               WHERE nco.account_flow_amount_in_portfolio_base_currency > 0) AS ledger_deposits,
           SUM(-nco.account_flow_amount_in_portfolio_base_currency) FILTER (
               WHERE nco.account_flow_amount_in_portfolio_base_currency < 0) AS ledger_withdrawals,
           SUM(nco.amount_in_portfolio_base_currency) FILTER (
               WHERE nco.normalized_category IN ('DIVIDEND', 'DIVIDEND_REVERSAL')) AS ledger_dividends,
           SUM(nco.amount_in_portfolio_base_currency) FILTER (
               WHERE nco.normalized_category IN ('INTEREST', 'INTEREST_REVERSAL')) AS ledger_interest,
           SUM(-nco.amount_in_portfolio_base_currency) FILTER (
               WHERE nco.normalized_category = 'FEE') AS ledger_fees,
           SUM(-nco.amount_in_portfolio_base_currency) FILTER (
               WHERE nco.normalized_category IN (
                   'WITHHOLDING_TAX', 'WITHHOLDING_TAX_REVERSAL', 'OTHER_TAX')) AS ledger_taxes,
           COUNT(*) FILTER (WHERE nco.normalized_category IN (
               'INTERNAL_TRANSFER_IN', 'INTERNAL_TRANSFER_OUT', 'INTERNAL_BOOKKEEPING'))
               AS internal_operation_count,
           COUNT(*) FILTER (
               WHERE NOT investory.fx_status_usable(nco.portfolio_conversion_status)) = 0 AS is_complete
    FROM investory.app_v_normalized_cash_operation_flows nco
    GROUP BY nco.account_id,
             (nco.date AT TIME ZONE 'Europe/Warsaw')::date,
             nco.account_currency,
             nco.base_currency
), daily_with_prev AS (
    SELECT ad.account_id,
           ad.snapshot_date,
           ad.cash_balance,
           LAG(ad.cash_balance) OVER (
               PARTITION BY ad.account_id ORDER BY ad.snapshot_date) AS previous_cash_balance,
           ad.deposits,
           ad.withdrawals,
           ad.dividends,
           ad.interest,
           ad.fees,
           ad.taxes
    FROM investory.account_daily ad
)
SELECT ad.account_id,
       ad.snapshot_date,
       account.currency::varchar(3) AS account_currency,
       portfolio.base_currency::varchar(3) AS ledger_base_currency,
       COALESCE(ld.is_complete, true) AS is_complete,
       COALESCE(ld.internal_operation_count, 0) AS internal_operation_count,
       ad.cash_balance - COALESCE(ad.previous_cash_balance, 0) AS account_cash_delta,
       COALESCE(ld.ledger_cash_base, 0) AS ledger_cash_delta,
       ad.deposits,
       COALESCE(ld.ledger_deposits, 0) AS ledger_deposits,
       ad.withdrawals,
       COALESCE(ld.ledger_withdrawals, 0) AS ledger_withdrawals,
       ad.dividends,
       COALESCE(ld.ledger_dividends, 0) AS ledger_dividends,
       ad.interest,
       COALESCE(ld.ledger_interest, 0) AS ledger_interest,
       ad.fees,
       COALESCE(ld.ledger_fees, 0) AS ledger_fees,
       ad.taxes,
       COALESCE(ld.ledger_taxes, 0) AS ledger_taxes,
       CASE WHEN account.currency = portfolio.base_currency
            THEN (ad.cash_balance - COALESCE(ad.previous_cash_balance, 0))
                 - COALESCE(ld.ledger_cash_base, 0)
            ELSE NULL::numeric END AS same_currency_cash_delta_gap,
       ad.deposits - COALESCE(ld.ledger_deposits, 0) AS deposits_gap,
       ad.withdrawals - COALESCE(ld.ledger_withdrawals, 0) AS withdrawals_gap,
       ad.dividends - COALESCE(ld.ledger_dividends, 0) AS dividends_gap,
       ad.interest - COALESCE(ld.ledger_interest, 0) AS interest_gap,
       ad.fees - COALESCE(ld.ledger_fees, 0) AS fees_gap,
       ad.taxes - COALESCE(ld.ledger_taxes, 0) AS taxes_gap
FROM daily_with_prev ad
LEFT JOIN ledger_daily ld
  ON ld.account_id = ad.account_id
 AND ld.snapshot_date = ad.snapshot_date
JOIN investory.accounts account ON account.id = ad.account_id
JOIN investory.portfolios portfolio ON portfolio.id = account.portfolio_id;

COMMENT ON VIEW investory.recon_v_account_daily_cashflow_full_precision IS
    'C1 flow components compare in portfolio base currency; cash delta is compared only for native account currency equal to portfolio base, using the Europe/Warsaw reporting date.';
