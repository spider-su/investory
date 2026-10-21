-- Cash-only accounts remain available in account-level records, but do not contribute to portfolio-level totals.
DROP VIEW investory.recon_v_portfolio_service_fallback;
DROP VIEW investory.recon_v_portfolio_data_quality;
DROP VIEW investory.recon_v_portfolio_account_quality;
DROP VIEW investory.app_v_portfolio_kpi_summary;
DROP MATERIALIZED VIEW investory.app_v_portfolio_kpi_summary_mv;
DROP MATERIALIZED VIEW investory.app_v_portfolio_contribution_summary_mv;

-- Daily portfolio valuations use the same included-account scope as current totals.
CREATE OR REPLACE VIEW investory.app_v_portfolio_daily AS
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
          WHERE NOT a.cash_only
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
          WHERE NOT account.cash_only
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
             JOIN investory.accounts a ON (((a.id = ast.account_id) AND (NOT a.cash_only))))
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
    COALESCE(las.total_equity, (0)::numeric) AS converted_equity_subtotal,
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
          WHERE NOT acc.cash_only
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
