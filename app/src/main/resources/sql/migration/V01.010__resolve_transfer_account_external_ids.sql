-- Resolve broker transfer comment identifiers through scoped account identity.
-- The comments contain broker external IDs, never generated accounts.id values.

SET search_path TO investory, public;

CREATE OR REPLACE VIEW investory.app_v_normalized_cash_operation_flows AS
WITH parsed AS (
    SELECT nco.*,
           CASE WHEN nco.normalized_category = 'INTERNAL_BOOKKEEPING'
                     AND nco.comment ~* 'transfer from [0-9]+ to [0-9]+'
                THEN substring(nco.comment FROM '(?i)transfer from ([0-9]+)') END AS transfer_source_external_id,
           CASE WHEN nco.normalized_category = 'INTERNAL_BOOKKEEPING'
                     AND nco.comment ~* 'transfer from [0-9]+ to [0-9]+'
                THEN substring(nco.comment FROM '(?i)to ([0-9]+)') END AS transfer_target_external_id
      FROM investory.app_v_normalized_cash_operations nco
), resolved AS (
    SELECT parsed.*,
           source_account.id AS transfer_source_account,
           target_account.id AS transfer_target_account
      FROM parsed
      JOIN investory.accounts current_account ON current_account.id = parsed.account_id
      LEFT JOIN LATERAL (
          SELECT a.id
            FROM investory.accounts a
           WHERE a.portfolio_id = current_account.portfolio_id
             AND a.provider = current_account.provider
             AND a.external_account_id = parsed.transfer_source_external_id
           LIMIT 1
      ) source_account ON true
      LEFT JOIN LATERAL (
          SELECT a.id
            FROM investory.accounts a
           WHERE a.portfolio_id = current_account.portfolio_id
             AND a.provider = current_account.provider
             AND a.external_account_id = parsed.transfer_target_external_id
           LIMIT 1
      ) target_account ON true
), effects AS (
    SELECT resolved.*,
           CASE
             WHEN normalized_category = ANY (ARRAY['EXTERNAL_DEPOSIT','EXTERNAL_WITHDRAWAL','INTERNAL_TRANSFER_IN','INTERNAL_TRANSFER_OUT','FX_CONVERSION']) THEN amount
             WHEN normalized_category = 'INTERNAL_BOOKKEEPING' AND transfer_source_account = account_id AND amount < 0 THEN amount
             WHEN normalized_category = 'INTERNAL_BOOKKEEPING' AND transfer_target_account = account_id AND amount > 0 THEN amount
             ELSE 0::numeric
           END AS account_flow_amount,
           CASE WHEN normalized_category = ANY (ARRAY['EXTERNAL_DEPOSIT','EXTERNAL_WITHDRAWAL','INTERNAL_TRANSFER_IN','INTERNAL_TRANSFER_OUT']) THEN amount ELSE 0::numeric END AS performance_flow_amount,
           CASE WHEN normalized_category = ANY (ARRAY['EXTERNAL_DEPOSIT','EXTERNAL_WITHDRAWAL']) THEN amount ELSE 0::numeric END AS portfolio_flow_amount
      FROM resolved
)
SELECT operation_id, account_id, portfolio_id, account_currency, currency,
       portfolio_base_currency, base_currency, raw_operation, normalized_category,
       normalized_subtype, economic_direction, is_external_flow, is_internal_transfer,
       is_fx_conversion, is_trade_cash_flow, is_correction, is_reversal, asset_id,
       amount, amount_in_portfolio_base_currency, amount_in_base_currency,
       amount_in_account_currency, comment, date, rate_month, fx_rate_to_base,
       portfolio_fx_source, portfolio_source_rate_date, portfolio_fx_age_days,
       portfolio_conversion_status, fx_rate_to_account_currency, account_fx_source,
       account_source_rate_date, account_fx_age_days, account_conversion_status,
       related_operation_id, transfer_group_id, pairing_status, classification_reason,
       classification_version, conversion_status, transfer_source_account,
       transfer_target_account, account_flow_amount, performance_flow_amount,
       portfolio_flow_amount,
       CASE WHEN investory.fx_status_usable(portfolio_conversion_status) THEN account_flow_amount * fx_rate_to_base END AS account_flow_amount_in_portfolio_base_currency,
       CASE WHEN investory.fx_status_usable(account_conversion_status) THEN account_flow_amount * fx_rate_to_account_currency END AS account_flow_amount_in_account_currency,
       CASE WHEN investory.fx_status_usable(portfolio_conversion_status) THEN performance_flow_amount * fx_rate_to_base END AS performance_flow_amount_in_portfolio_base_currency,
       CASE WHEN investory.fx_status_usable(portfolio_conversion_status) THEN portfolio_flow_amount * fx_rate_to_base END AS portfolio_flow_amount_in_portfolio_base_currency
  FROM effects;

