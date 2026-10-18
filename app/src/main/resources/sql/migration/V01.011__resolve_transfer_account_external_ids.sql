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


-- Rebuild the explicit dependent reporting closure without CASCADE.
-- Flyway runs this migration transactionally; any unexpected dependency aborts it.
DROP VIEW investory.recon_v_portfolio_service_fallback;
DROP VIEW investory.recon_v_portfolio_data_quality;
DROP VIEW investory.recon_v_portfolio_account_quality;
DROP MATERIALIZED VIEW investory.recon_v_account_statistics_vs_daily;
DROP VIEW investory.app_v_portfolio_kpi_summary;
DROP MATERIALIZED VIEW investory.app_v_portfolio_kpi_summary_mv;
DROP MATERIALIZED VIEW investory.app_v_portfolio_contribution_summary_mv;
DROP VIEW investory.app_v_account_statistics_reporting;
DROP MATERIALIZED VIEW investory.app_v_account_statistics;

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
                    WHEN (((nco.normalized_category)::text = 'INTERNAL_BOOKKEEPING'::text) AND ((nco.comment)::text ~* 'transfer from [0-9]+ to [0-9]+'::text) AND ("substring"((nco.comment)::text, '(?i)to ([0-9]+)'::text) = (SELECT identity_account.external_account_id FROM investory.accounts identity_account WHERE identity_account.id = nco.account_id)) AND (nco.amount > (0)::numeric) AND (NOT (EXISTS ( SELECT 1
                       FROM investory.accounts counterparty
                      WHERE ((counterparty.external_account_id = "substring"((nco.comment)::text, '(?i)transfer from ([0-9]+)'::text) AND counterparty.provider = (SELECT identity_account.provider FROM investory.accounts identity_account WHERE identity_account.id = nco.account_id)) AND (counterparty.portfolio_id = ( SELECT source.portfolio_id
                               FROM investory.accounts source
                              WHERE (source.id = nco.account_id)))))))) THEN nco.amount_in_portfolio_base_currency
                    WHEN (((nco.normalized_category)::text = 'INTERNAL_BOOKKEEPING'::text) AND ((nco.comment)::text ~* 'transfer from [0-9]+ to [0-9]+'::text) AND ("substring"((nco.comment)::text, '(?i)transfer from ([0-9]+)'::text) = (SELECT identity_account.external_account_id FROM investory.accounts identity_account WHERE identity_account.id = nco.account_id)) AND (nco.amount < (0)::numeric) AND (NOT (EXISTS ( SELECT 1
                       FROM investory.accounts counterparty
                      WHERE ((counterparty.external_account_id = "substring"((nco.comment)::text, '(?i)to ([0-9]+)'::text) AND counterparty.provider = (SELECT identity_account.provider FROM investory.accounts identity_account WHERE identity_account.id = nco.account_id)) AND (counterparty.portfolio_id = ( SELECT source.portfolio_id
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

CREATE UNIQUE INDEX ux_mv_account_statistics_account ON investory.app_v_account_statistics USING btree (account_id);

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

CREATE MATERIALIZED VIEW investory.app_v_portfolio_contribution_summary_mv AS
 WITH contribution_rows AS (
         SELECT p_1.id AS portfolio_id,
            p_1.base_currency,
            nco.portfolio_conversion_status,
                CASE
                    WHEN ((nco.normalized_category)::text = 'EXTERNAL_DEPOSIT'::text) THEN 'EXTERNAL_DEPOSIT'::text
                    WHEN ((nco.normalized_category)::text = 'EXTERNAL_WITHDRAWAL'::text) THEN 'EXTERNAL_WITHDRAWAL'::text
                    WHEN (((nco.normalized_category)::text = 'INTERNAL_BOOKKEEPING'::text) AND ((nco.comment)::text ~* 'transfer from [0-9]+ to [0-9]+'::text) AND ("substring"((nco.comment)::text, '(?i)to ([0-9]+)'::text) = (SELECT identity_account.external_account_id FROM investory.accounts identity_account WHERE identity_account.id = nco.account_id)) AND (nco.amount > (0)::numeric) AND (NOT (EXISTS ( SELECT 1
                       FROM investory.accounts counterparty
                      WHERE ((counterparty.external_account_id = "substring"((nco.comment)::text, '(?i)transfer from ([0-9]+)'::text) AND counterparty.provider = (SELECT identity_account.provider FROM investory.accounts identity_account WHERE identity_account.id = nco.account_id)) AND (counterparty.portfolio_id = ( SELECT source.portfolio_id
                               FROM investory.accounts source
                              WHERE (source.id = nco.account_id)))))))) THEN 'BOUNDARY_TRANSFER'::text
                    WHEN (((nco.normalized_category)::text = 'INTERNAL_BOOKKEEPING'::text) AND ((nco.comment)::text ~* 'transfer from [0-9]+ to [0-9]+'::text) AND ("substring"((nco.comment)::text, '(?i)transfer from ([0-9]+)'::text) = (SELECT identity_account.external_account_id FROM investory.accounts identity_account WHERE identity_account.id = nco.account_id)) AND (nco.amount < (0)::numeric) AND (NOT (EXISTS ( SELECT 1
                       FROM investory.accounts counterparty
                      WHERE ((counterparty.external_account_id = "substring"((nco.comment)::text, '(?i)to ([0-9]+)'::text) AND counterparty.provider = (SELECT identity_account.provider FROM investory.accounts identity_account WHERE identity_account.id = nco.account_id)) AND (counterparty.portfolio_id = ( SELECT source.portfolio_id
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

CREATE UNIQUE INDEX ux_mv_app_v_portfolio_contribution_summary_mv_portfolio ON investory.app_v_portfolio_contribution_summary_mv USING btree (portfolio_id);

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

CREATE UNIQUE INDEX ux_app_v_portfolio_kpi_summary_mv_portfolio ON investory.app_v_portfolio_kpi_summary_mv USING btree (portfolio_id);

CREATE VIEW investory.app_v_portfolio_kpi_summary AS
 SELECT src.portfolio_id,
    src.portfolio_name,
    src.base_currency,
    investory.application_display_value(contributions.total_deposits) AS total_deposits,
    investory.application_display_value(contributions.total_withdrawals) AS total_withdrawals,
    investory.application_display_value(contributions.net_deposits) AS net_deposits,
    CASE WHEN src.is_complete THEN investory.application_display_value(src.total_cash) ELSE NULL::numeric END AS total_cash,
    CASE WHEN src.is_complete THEN investory.application_display_value(src.total_market_value) ELSE NULL::numeric END AS total_market_value,
    CASE WHEN src.is_complete THEN investory.application_display_value(src.total_equity) ELSE NULL::numeric END AS total_equity,
    investory.application_display_value(src.total_realized_profit) AS total_realized_profit,
    CASE WHEN src.is_complete THEN investory.application_display_value(src.total_unrealized_profit) ELSE NULL::numeric END AS total_unrealized_profit,
    investory.application_display_value(src.total_dividends) AS total_dividends,
    investory.application_display_value(src.total_interest) AS total_interest,
    investory.application_display_value(src.total_fees) AS total_fees,
    investory.application_display_value(src.total_taxes) AS total_taxes,
        CASE
            WHEN NOT src.is_complete THEN NULL::numeric
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

CREATE MATERIALIZED VIEW investory.recon_v_account_statistics_vs_daily AS
 WITH latest_daily AS (
         SELECT DISTINCT ON (ad.account_id) ad.account_id,
            ad.snapshot_date,
            ad.valuation_currency,
            ad.cash_balance,
            ad.market_value,
            ad.equity,
            ad.cost_base,
            ad.unrealized_profit
           FROM investory.account_daily ad
          ORDER BY ad.account_id, ad.snapshot_date DESC, ad.id DESC
        ), daily_flow_totals AS (
         SELECT ad.account_id,
            sum(COALESCE(ad.realized_profit, (0)::numeric)) AS realized_profit,
            sum(COALESCE(ad.dividends, (0)::numeric)) AS dividends,
            sum(COALESCE(ad.interest, (0)::numeric)) AS interest,
            sum(COALESCE(ad.fees, (0)::numeric)) AS fees,
            sum(COALESCE(ad.taxes, (0)::numeric)) AS taxes
           FROM investory.account_daily ad
          GROUP BY ad.account_id
        )
 SELECT a.id AS account_id,
    a.name AS account_name,
    a.currency AS account_currency,
    CURRENT_DATE AS statistics_valuation_date,
    ast.valuation_currency AS statistics_currency,
    ld.valuation_currency AS latest_daily_currency,
    ast.latest_snapshot_date AS statistics_snapshot_date,
    ld.snapshot_date AS latest_daily_snapshot_date,
    round(COALESCE(ast.cash_balance, (0)::numeric), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS statistics_cash_balance,
    round(COALESCE(ld.cash_balance, (0)::numeric), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS latest_daily_cash_balance,
    round((COALESCE(ast.cash_balance, (0)::numeric) - COALESCE(ld.cash_balance, (0)::numeric)), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS cash_balance_difference,
    round(COALESCE(ast.market_value, (0)::numeric), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS statistics_market_value,
    round(COALESCE(ld.market_value, (0)::numeric), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS latest_daily_market_value,
    round((COALESCE(ast.market_value, (0)::numeric) - COALESCE(ld.market_value, (0)::numeric)), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS market_value_difference,
    round(COALESCE(ast.equity, (0)::numeric), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS statistics_equity,
    round(COALESCE(ld.equity, (0)::numeric), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS latest_daily_equity,
    round((COALESCE(ast.equity, (0)::numeric) - COALESCE(ld.equity, (0)::numeric)), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS equity_difference,
    round(COALESCE(ast.cost_base, (0)::numeric), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS statistics_cost_base,
    round(COALESCE(ld.cost_base, (0)::numeric), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS latest_daily_cost_base,
    round((COALESCE(ast.cost_base, (0)::numeric) - COALESCE(ld.cost_base, (0)::numeric)), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS cost_base_difference,
    round(COALESCE(ast.realized_profit, (0)::numeric), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS statistics_realized_profit,
    round(COALESCE(dft.realized_profit, (0)::numeric), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS cumulative_daily_realized_profit,
    round((COALESCE(ast.realized_profit, (0)::numeric) - COALESCE(dft.realized_profit, (0)::numeric)), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS realized_profit_difference,
    round(COALESCE(ast.unrealized_profit, (0)::numeric), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS statistics_unrealized_profit,
    round(COALESCE(ld.unrealized_profit, (0)::numeric), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS latest_daily_unrealized_profit,
    round((COALESCE(ast.unrealized_profit, (0)::numeric) - COALESCE(ld.unrealized_profit, (0)::numeric)), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS unrealized_profit_difference,
    round(COALESCE(ast.dividends, (0)::numeric), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS statistics_dividends,
    round(COALESCE(dft.dividends, (0)::numeric), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS cumulative_daily_dividends,
    round((COALESCE(ast.dividends, (0)::numeric) - COALESCE(dft.dividends, (0)::numeric)), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS dividends_difference,
    round(COALESCE(ast.interest, (0)::numeric), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS statistics_interest,
    round(COALESCE(dft.interest, (0)::numeric), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS cumulative_daily_interest,
    round((COALESCE(ast.interest, (0)::numeric) - COALESCE(dft.interest, (0)::numeric)), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS interest_difference,
    round(COALESCE(ast.fees, (0)::numeric), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS statistics_fees,
    round(COALESCE(dft.fees, (0)::numeric), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS cumulative_daily_fees,
    round((COALESCE(ast.fees, (0)::numeric) - COALESCE(dft.fees, (0)::numeric)), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS fees_difference,
    round(COALESCE(ast.taxes, (0)::numeric), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS statistics_taxes,
    round(COALESCE(dft.taxes, (0)::numeric), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS cumulative_daily_taxes,
    round((COALESCE(ast.taxes, (0)::numeric) - COALESCE(dft.taxes, (0)::numeric)), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS taxes_difference,
    round(COALESCE(ast.net_deposit, (0)::numeric), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS statistics_net_deposit,
    round(COALESCE(ast.account_net_deposit, (0)::numeric), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS statistics_account_net_deposit,
    round(GREATEST(investory.reconciliation_effective_tolerance(ast.cash_balance, ld.cash_balance), investory.reconciliation_effective_tolerance(ast.market_value, ld.market_value), investory.reconciliation_effective_tolerance(ast.equity, ld.equity), investory.reconciliation_effective_tolerance(ast.cost_base, ld.cost_base), investory.reconciliation_effective_tolerance(ast.realized_profit, dft.realized_profit), investory.reconciliation_effective_tolerance(ast.unrealized_profit, ld.unrealized_profit), investory.reconciliation_effective_tolerance(ast.dividends, dft.dividends), investory.reconciliation_effective_tolerance(ast.interest, dft.interest), investory.reconciliation_effective_tolerance(ast.fees, dft.fees), investory.reconciliation_effective_tolerance(ast.taxes, dft.taxes)), (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer) AS effective_tolerance,
        CASE
            WHEN ((ast.latest_snapshot_date IS NULL) AND (ld.snapshot_date IS NULL)) THEN 'NO_DATA'::text
            WHEN ((ast.latest_snapshot_date IS NULL) OR (ld.snapshot_date IS NULL)) THEN 'MISSING_SIDE'::text
            WHEN ((ast.valuation_currency)::text IS DISTINCT FROM (ld.valuation_currency)::text) THEN 'CURRENCY_MISMATCH'::text
            WHEN (ast.latest_snapshot_date IS DISTINCT FROM ld.snapshot_date) THEN 'VALUATION_ASOF_DIFFERENCE'::text
            WHEN (CURRENT_DATE <> ld.snapshot_date) THEN 'VALUATION_ASOF_DIFFERENCE'::text
            WHEN (investory.reconciliation_values_match(ast.cash_balance, ld.cash_balance) AND investory.reconciliation_values_match(ast.market_value, ld.market_value) AND investory.reconciliation_values_match(ast.equity, ld.equity) AND investory.reconciliation_values_match(ast.cost_base, ld.cost_base) AND investory.reconciliation_values_match(ast.unrealized_profit, ld.unrealized_profit) AND investory.reconciliation_values_match(ast.dividends, dft.dividends) AND investory.reconciliation_values_match(ast.interest, dft.interest) AND investory.reconciliation_values_match(ast.fees, dft.fees) AND investory.reconciliation_values_match(ast.taxes, dft.taxes) AND (NOT investory.reconciliation_values_match(ast.realized_profit, dft.realized_profit))) THEN 'REALIZED_PROFIT_SEMANTIC_REVIEW'::text
            WHEN ((NOT investory.reconciliation_values_match(ast.cash_balance, ld.cash_balance)) OR (NOT investory.reconciliation_values_match(ast.market_value, ld.market_value)) OR (NOT investory.reconciliation_values_match(ast.equity, ld.equity)) OR (NOT investory.reconciliation_values_match(ast.cost_base, ld.cost_base)) OR (NOT investory.reconciliation_values_match(ast.realized_profit, dft.realized_profit)) OR (NOT investory.reconciliation_values_match(ast.unrealized_profit, ld.unrealized_profit)) OR (NOT investory.reconciliation_values_match(ast.dividends, dft.dividends)) OR (NOT investory.reconciliation_values_match(ast.interest, dft.interest)) OR (NOT investory.reconciliation_values_match(ast.fees, dft.fees)) OR (NOT investory.reconciliation_values_match(ast.taxes, dft.taxes))) THEN 'VALUE_MISMATCH'::text
            ELSE 'OK'::text
        END AS reconciliation_status
   FROM (((investory.accounts a
     LEFT JOIN investory.app_v_account_statistics ast ON ((ast.account_id = a.id)))
     LEFT JOIN latest_daily ld ON ((ld.account_id = a.id)))
     LEFT JOIN daily_flow_totals dft ON ((dft.account_id = a.id)));

CREATE UNIQUE INDEX ux_mv_recon_account_stats_account ON investory.recon_v_account_statistics_vs_daily USING btree (account_id);

CREATE VIEW investory.recon_v_portfolio_account_quality AS
 WITH latest_validation AS (
         SELECT DISTINCT ON (recon_v_reporting_validation_summary.account_id) recon_v_reporting_validation_summary.account_id,
            recon_v_reporting_validation_summary.valuation_date,
            recon_v_reporting_validation_summary.status,
            recon_v_reporting_validation_summary.missing_prices,
            recon_v_reporting_validation_summary.missing_fx_rates,
            recon_v_reporting_validation_summary.missing_multipliers,
            recon_v_reporting_validation_summary.residual_positions,
            recon_v_reporting_validation_summary.reconciliation_failures
           FROM investory.recon_v_reporting_validation_summary
          ORDER BY recon_v_reporting_validation_summary.account_id, recon_v_reporting_validation_summary.valuation_date DESC
        ), market_reviews AS (
         SELECT recon_v_account_daily_market_value_semantic_review.account_id,
            recon_v_account_daily_market_value_semantic_review.valuation_date,
            count(*) AS review_count
           FROM investory.recon_v_account_daily_market_value_semantic_review
          GROUP BY recon_v_account_daily_market_value_semantic_review.account_id, recon_v_account_daily_market_value_semantic_review.valuation_date
        ), statistics_status AS (
         SELECT recon_v_account_statistics_vs_daily.account_id,
            recon_v_account_statistics_vs_daily.reconciliation_status
           FROM investory.recon_v_account_statistics_vs_daily
        )
 SELECT a.id AS account_id,
    a.name AS account_name,
    a.provider,
    lv.valuation_date,
    (
        CASE
            WHEN (lv.account_id IS NULL) THEN 'REVIEW'::text
            WHEN ((COALESCE(lv.missing_prices, (0)::bigint) > 0) OR (COALESCE(lv.missing_fx_rates, (0)::bigint) > 0) OR (COALESCE(lv.missing_multipliers, (0)::bigint) > 0) OR (COALESCE(lv.residual_positions, (0)::bigint) > 0)) THEN 'UNRECONCILED'::text
            WHEN ((COALESCE(lv.reconciliation_failures, (0)::bigint) > 0) AND (COALESCE(mr.review_count, (0)::bigint) <> lv.reconciliation_failures)) THEN 'UNRECONCILED'::text
            WHEN (COALESCE(ss.reconciliation_status, 'OK'::text) = 'VALUATION_ASOF_DIFFERENCE'::text) THEN 'REVIEW'::text
            WHEN ((lv.status)::text = 'PASS'::text) THEN 'RECONCILED'::text
            ELSE 'REVIEW'::text
        END)::character varying(16) AS quality_status,
    (
        CASE
            WHEN (lv.account_id IS NULL) THEN 'NO_VALIDATION_SNAPSHOT'::text
            WHEN (COALESCE(lv.missing_prices, (0)::bigint) > 0) THEN 'MISSING_REQUIRED_PRICE'::text
            WHEN (COALESCE(lv.missing_fx_rates, (0)::bigint) > 0) THEN 'MISSING_REQUIRED_FX'::text
            WHEN (COALESCE(lv.missing_multipliers, (0)::bigint) > 0) THEN 'MISSING_REQUIRED_MULTIPLIER'::text
            WHEN (COALESCE(lv.residual_positions, (0)::bigint) > 0) THEN 'RESIDUAL_POSITION_VALUE'::text
            WHEN ((COALESCE(lv.reconciliation_failures, (0)::bigint) > 0) AND (COALESCE(mr.review_count, (0)::bigint) = lv.reconciliation_failures)) THEN 'MARKET_VALUE_SEMANTIC_REVIEW'::text
            WHEN (COALESCE(ss.reconciliation_status, 'OK'::text) = 'VALUATION_ASOF_DIFFERENCE'::text) THEN 'VALUATION_ASOF_REVIEW'::text
            WHEN ((lv.status)::text = 'PASS'::text) THEN 'RECONCILED'::text
            ELSE 'VALIDATION_REVIEW'::text
        END)::character varying(64) AS quality_reason
   FROM (((investory.accounts a
     LEFT JOIN latest_validation lv ON ((lv.account_id = a.id)))
     LEFT JOIN market_reviews mr ON (((mr.account_id = lv.account_id) AND (mr.valuation_date = lv.valuation_date))))
     LEFT JOIN statistics_status ss ON ((ss.account_id = a.id)));

CREATE VIEW investory.recon_v_portfolio_data_quality AS
 WITH active_accounts AS (
         SELECT count(*) AS total_accounts
           FROM investory.accounts
        ), states AS (
         SELECT count(*) FILTER (WHERE ((recon_v_portfolio_account_quality.quality_status)::text = 'RECONCILED'::text)) AS reconciled_accounts,
            count(*) FILTER (WHERE ((recon_v_portfolio_account_quality.quality_status)::text = 'REVIEW'::text)) AS review_accounts,
            count(*) FILTER (WHERE ((recon_v_portfolio_account_quality.quality_status)::text = 'UNRECONCILED'::text)) AS unreconciled_accounts
           FROM investory.recon_v_portfolio_account_quality
        ), latest_positions AS (
         SELECT app_v_reconstructed_position_daily.account_id,
            app_v_reconstructed_position_daily.asset_id,
            app_v_reconstructed_position_daily.valuation_date,
            app_v_reconstructed_position_daily.open_quantity,
            app_v_reconstructed_position_daily.reconstructed_cost_base_local,
            app_v_reconstructed_position_daily.reconstructed_cost_base_base,
            app_v_reconstructed_position_daily.acquisition_currency,
            app_v_reconstructed_position_daily.selected_price,
            app_v_reconstructed_position_daily.selected_price_date,
            app_v_reconstructed_position_daily.underlying_observation_date,
            app_v_reconstructed_position_daily.price_age_days,
            app_v_reconstructed_position_daily.price_currency,
            app_v_reconstructed_position_daily.contract_multiplier,
            app_v_reconstructed_position_daily.selection_priority,
            app_v_reconstructed_position_daily.base_currency,
            app_v_reconstructed_position_daily.fx_rate_to_base,
            app_v_reconstructed_position_daily.fx_conversion_status,
            app_v_reconstructed_position_daily.reconstructed_market_value_base,
            app_v_reconstructed_position_daily.reconstructed_unrealized_profit_base,
            app_v_reconstructed_position_daily.price_origin,
            app_v_reconstructed_position_daily.price_quality,
            app_v_reconstructed_position_daily.reconstruction_status,
            app_v_reconstructed_position_daily.reconstruction_message
           FROM investory.app_v_reconstructed_position_daily
          WHERE (app_v_reconstructed_position_daily.valuation_date = ( SELECT max(app_v_reconstructed_position_daily_1.valuation_date) AS max
                   FROM investory.app_v_reconstructed_position_daily app_v_reconstructed_position_daily_1))
        ), pq AS (
         SELECT count(*) AS total_open_positions,
            count(*) FILTER (WHERE ((latest_positions.selected_price IS NOT NULL) AND ((latest_positions.reconstruction_status)::text <> 'FAIL'::text))) AS priced_open_positions,
            count(*) FILTER (WHERE (latest_positions.selected_price IS NULL)) AS missing_price_count,
            count(*) FILTER (WHERE (latest_positions.price_age_days > 10)) AS stale_price_count,
            count(*) FILTER (WHERE ((latest_positions.selection_priority = ANY (ARRAY[3, 5])) OR ((latest_positions.price_quality)::text ~~* '%PROXY%'::text) OR ((latest_positions.price_quality)::text ~~* '%ALTERNATE%'::text) OR ((latest_positions.price_quality)::text ~~* '%INTERPOLAT%'::text))) AS proxy_price_count,
            count(*) FILTER (WHERE ((latest_positions.selection_priority = 5) OR ((latest_positions.price_quality)::text ~~* '%INTERPOLAT%'::text))) AS estimated_price_count,
            count(*) FILTER (WHERE (NOT investory.fx_status_usable(latest_positions.fx_conversion_status))) AS missing_fx_count,
            max(latest_positions.selected_price_date) AS latest_price_date
           FROM latest_positions
          WHERE (latest_positions.open_quantity <> (0)::numeric)
        ), cq AS (
         SELECT count(*) AS ambiguous_cost_basis_currency_count
           FROM investory.recon_v_position_currency_validation
          WHERE (recon_v_position_currency_validation.anomaly_code = ANY (ARRAY['MIXED_OPEN_POSITION_CURRENCIES'::text, 'MISSING_POSITION_CURRENCY'::text]))
        ), cashq AS (
         SELECT count(*) AS unclassified_cash_operation_count
           FROM investory.app_v_normalized_cash_operations
          WHERE ((app_v_normalized_cash_operations.normalized_category)::text = 'UNCLASSIFIED'::text)
        ), q AS (
         SELECT aa.total_accounts,
            s.reconciled_accounts,
            s.unreconciled_accounts,
            pq.total_open_positions,
            pq.priced_open_positions,
            pq.missing_price_count,
            pq.stale_price_count,
            pq.proxy_price_count,
            pq.estimated_price_count,
            pq.missing_fx_count,
            cq.ambiguous_cost_basis_currency_count,
            cq.ambiguous_cost_basis_currency_count AS excluded_position_count,
            cashq.unclassified_cash_operation_count,
            ( SELECT max(import_history.finished_at) AS max
                   FROM investory.import_history
                  WHERE (import_history.status = 'COMPLETED'::text)) AS latest_broker_reconciliation_at,
            ( SELECT max(import_history.finished_at) AS max
                   FROM investory.import_history
                  WHERE (import_history.status = 'COMPLETED'::text)) AS latest_import_at,
            pq.latest_price_date,
            ( SELECT max(exchange_rates.rate_date) AS max
                   FROM investory.exchange_rates
                  WHERE ((exchange_rates.purpose)::text = 'VALUATION'::text)) AS latest_fx_date,
            ( SELECT max(account_daily.updated_at) AS max
                   FROM investory.account_daily) AS latest_reporting_refresh_at,
            s.review_accounts
           FROM ((((active_accounts aa
             CROSS JOIN states s)
             CROSS JOIN pq)
             CROSS JOIN cq)
             CROSS JOIN cashq)
        )
 SELECT total_accounts,
    reconciled_accounts,
    unreconciled_accounts,
    total_open_positions,
    priced_open_positions,
    missing_price_count,
    stale_price_count,
    proxy_price_count,
    estimated_price_count,
    missing_fx_count,
    ambiguous_cost_basis_currency_count,
    excluded_position_count,
    unclassified_cash_operation_count,
    latest_broker_reconciliation_at,
    latest_import_at,
    latest_price_date,
    latest_fx_date,
    latest_reporting_refresh_at,
    (
        CASE
            WHEN ((unreconciled_accounts > 0) OR (missing_price_count > 0) OR (missing_fx_count > 0) OR (ambiguous_cost_basis_currency_count > 0) OR (priced_open_positions < total_open_positions)) THEN 'CRITICAL'::text
            WHEN ((review_accounts > 0) OR (stale_price_count > 0) OR (proxy_price_count > 0) OR (estimated_price_count > 0) OR (unclassified_cash_operation_count > 0)) THEN 'REVIEW'::text
            ELSE 'HEALTHY'::text
        END)::character varying(16) AS quality_state,
    review_accounts
   FROM q;

CREATE VIEW investory.recon_v_portfolio_service_fallback AS
 WITH position_components AS (
         SELECT pf.id AS portfolio_id,
            'REALIZED'::character varying(16) AS metric_type,
            (pos.close_time)::date AS valuation_date,
            pos.profit_currency AS source_currency,
            pf.base_currency,
                CASE
                    WHEN (pos.settlement_model = 'RESULT_ONLY'::investory.position_settlement_model) THEN COALESCE(pos.profit, (0)::numeric)
                    ELSE (COALESCE(pos.profit, (0)::numeric) + COALESCE(pos.swap, (0)::numeric))
                END AS amount_native
           FROM (((investory.positions pos
             JOIN investory.accounts acc ON ((acc.id = pos.account_id)))
             JOIN investory.portfolios pf ON ((pf.id = acc.portfolio_id)))
             JOIN investory.assets asset ON ((asset.id = pos.asset_id)))
          WHERE (pos.close_time IS NOT NULL)
        UNION ALL
         SELECT pf.id AS portfolio_id,
            'REALIZED'::character varying(16) AS metric_type,
            (pos.close_time)::date AS valuation_date,
            pos.commission_currency AS source_currency,
            pf.base_currency,
                CASE
                    WHEN (pos.settlement_model = 'RESULT_ONLY'::investory.position_settlement_model) THEN (0)::numeric
                    ELSE COALESCE(pos.commission, (0)::numeric)
                END AS amount_native
           FROM (((investory.positions pos
             JOIN investory.accounts acc ON ((acc.id = pos.account_id)))
             JOIN investory.portfolios pf ON ((pf.id = acc.portfolio_id)))
             JOIN investory.assets asset ON ((asset.id = pos.asset_id)))
          WHERE (pos.close_time IS NOT NULL)
        UNION ALL
         SELECT pf.id AS portfolio_id,
            'UNREALIZED'::character varying(16) AS metric_type,
            r.valuation_date,
            pf.base_currency AS source_currency,
            pf.base_currency,
            r.reconstructed_unrealized_profit_base AS amount_native
           FROM ((investory.app_v_reconstructed_position_daily r
             JOIN investory.accounts acc ON ((acc.id = r.account_id)))
             JOIN investory.portfolios pf ON ((pf.id = acc.portfolio_id)))
          WHERE ((r.valuation_date = ( SELECT max(app_v_reconstructed_position_daily.valuation_date) AS max
                   FROM investory.app_v_reconstructed_position_daily)) AND (r.open_quantity <> (0)::numeric) AND (r.reconstructed_unrealized_profit_base IS NOT NULL))
        ), converted_position_components AS (
         SELECT pc.portfolio_id,
            pc.metric_type,
            pc.valuation_date,
            pc.source_currency,
            pc.base_currency,
            pc.amount_native,
            fx.fx_rate_to_target,
            fx.conversion_status
           FROM (position_components pc
             LEFT JOIN LATERAL investory.resolve_fx_rate(pc.valuation_date, pc.source_currency, pc.base_currency) fx(source_currency, target_currency, fx_rate_to_target, source, rate_method, rate_source, source_rate_date, age_days, conversion_status) ON (true))
        ), raw_position_totals AS (
         SELECT cpc.portfolio_id,
                CASE
                    WHEN (count(*) FILTER (WHERE (((cpc.metric_type)::text = 'REALIZED'::text) AND (NOT investory.fx_status_usable(cpc.conversion_status)))) > 0) THEN NULL::numeric
                    ELSE sum((cpc.amount_native * cpc.fx_rate_to_target)) FILTER (WHERE ((cpc.metric_type)::text = 'REALIZED'::text))
                END AS fallback_realized_profit,
                CASE
                    WHEN (count(*) FILTER (WHERE (((cpc.metric_type)::text = 'UNREALIZED'::text) AND (NOT investory.fx_status_usable(cpc.conversion_status)))) > 0) THEN NULL::numeric
                    ELSE sum((cpc.amount_native * cpc.fx_rate_to_target)) FILTER (WHERE ((cpc.metric_type)::text = 'UNREALIZED'::text))
                END AS fallback_unrealized_profit,
            count(*) FILTER (WHERE (NOT investory.fx_status_usable(cpc.conversion_status))) AS fallback_position_fx_missing_count
           FROM converted_position_components cpc
          GROUP BY cpc.portfolio_id
        ), raw_cash_totals AS (
         SELECT pr.id AS portfolio_id,
            sum(nco.amount_in_portfolio_base_currency) FILTER (WHERE ((nco.normalized_category)::text = ANY (ARRAY[('DIVIDEND'::character varying)::text, ('DIVIDEND_REVERSAL'::character varying)::text]))) AS fallback_dividends,
            sum(nco.amount_in_portfolio_base_currency) FILTER (WHERE ((nco.normalized_category)::text = ANY (ARRAY[('INTEREST'::character varying)::text, ('INTEREST_REVERSAL'::character varying)::text]))) AS fallback_interest,
            count(*) FILTER (WHERE (NOT investory.fx_status_usable(nco.portfolio_conversion_status))) AS fallback_cash_fx_missing_count,
            (count(*) FILTER (WHERE (NOT investory.fx_status_usable(nco.portfolio_conversion_status))) = 0) AS fallback_cash_is_complete
           FROM ((investory.app_v_normalized_cash_operations nco
             JOIN investory.accounts acc ON ((acc.id = nco.account_id)))
             JOIN investory.portfolios pr ON ((pr.id = acc.portfolio_id)))
          GROUP BY pr.id
        )
 SELECT pk.portfolio_id,
    pk.base_currency,
    pk.total_realized_profit AS canonical_realized_profit,
        CASE
            WHEN (rp.portfolio_id IS NULL) THEN (0)::numeric
            ELSE rp.fallback_realized_profit
        END AS fallback_realized_profit,
    pk.total_unrealized_profit AS canonical_unrealized_profit,
        CASE
            WHEN (rp.portfolio_id IS NULL) THEN (0)::numeric
            ELSE rp.fallback_unrealized_profit
        END AS fallback_unrealized_profit,
    COALESCE(rp.fallback_position_fx_missing_count, (0)::bigint) AS fallback_position_fx_missing_count,
    pk.total_dividends AS canonical_dividends,
        CASE
            WHEN COALESCE(rc.fallback_cash_is_complete, true) THEN COALESCE(rc.fallback_dividends, (0)::numeric)
            ELSE NULL::numeric
        END AS fallback_dividends,
    pk.total_interest AS canonical_interest,
        CASE
            WHEN COALESCE(rc.fallback_cash_is_complete, true) THEN COALESCE(rc.fallback_interest, (0)::numeric)
            ELSE NULL::numeric
        END AS fallback_interest,
    (
        CASE
            WHEN (rp.portfolio_id IS NULL) THEN (0)::numeric
            ELSE rp.fallback_realized_profit
        END - pk.total_realized_profit) AS realized_profit_difference,
    (
        CASE
            WHEN (rp.portfolio_id IS NULL) THEN (0)::numeric
            ELSE rp.fallback_unrealized_profit
        END - pk.total_unrealized_profit) AS unrealized_profit_difference,
        CASE
            WHEN COALESCE(rc.fallback_cash_is_complete, true) THEN (COALESCE(rc.fallback_dividends, (0)::numeric) - pk.total_dividends)
            ELSE NULL::numeric
        END AS dividends_difference,
        CASE
            WHEN COALESCE(rc.fallback_cash_is_complete, true) THEN (COALESCE(rc.fallback_interest, (0)::numeric) - pk.total_interest)
            ELSE NULL::numeric
        END AS interest_difference,
    (COALESCE(rp.fallback_position_fx_missing_count, (0)::bigint) + COALESCE(rc.fallback_cash_fx_missing_count, (0)::bigint)) AS missing_fx_count,
    ((COALESCE(rp.fallback_position_fx_missing_count, (0)::bigint) = 0) AND COALESCE(rc.fallback_cash_is_complete, true)) AS is_complete,
        CASE
            WHEN ((COALESCE(rp.fallback_position_fx_missing_count, (0)::bigint) = 0) AND COALESCE(rc.fallback_cash_is_complete, true) AND investory.reconciliation_values_match(pk.total_realized_profit,
            CASE
                WHEN (rp.portfolio_id IS NULL) THEN (0)::numeric
                ELSE rp.fallback_realized_profit
            END) AND (pk.total_unrealized_profit IS NOT NULL) AND (
            CASE
                WHEN (rp.portfolio_id IS NULL) THEN (0)::numeric
                ELSE rp.fallback_unrealized_profit
            END IS NOT NULL) AND (abs((
            CASE
                WHEN (rp.portfolio_id IS NULL) THEN (0)::numeric
                ELSE rp.fallback_unrealized_profit
            END - pk.total_unrealized_profit)) <= GREATEST(investory.reconciliation_parameter('reconciliation_fallback_unrealized_absolute_tolerance'::character varying), (investory.reconciliation_parameter('reconciliation_relative_tolerance'::character varying) * GREATEST(abs(pk.total_unrealized_profit), abs(
            CASE
                WHEN (rp.portfolio_id IS NULL) THEN (0)::numeric
                ELSE rp.fallback_unrealized_profit
            END))))) AND investory.reconciliation_values_match(pk.total_dividends, COALESCE(rc.fallback_dividends, (0)::numeric)) AND investory.reconciliation_values_match(pk.total_interest, COALESCE(rc.fallback_interest, (0)::numeric))) THEN 'MATCH'::text
            ELSE 'REVIEW'::text
        END AS fallback_reconciliation_status
   FROM ((investory.app_v_portfolio_kpi_summary_mv pk
     LEFT JOIN raw_position_totals rp ON ((rp.portfolio_id = pk.portfolio_id)))
     LEFT JOIN raw_cash_totals rc ON ((rc.portfolio_id = pk.portfolio_id)));
