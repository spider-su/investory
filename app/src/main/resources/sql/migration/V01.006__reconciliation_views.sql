-- Final reconciliation views and materialized projections.
-- Consolidated final schema; historical repair steps are not replayed.

SET search_path TO investory, public;

CREATE VIEW investory.recon_v_realized_result AS
 WITH trade_components AS MATERIALIZED (
         SELECT p.account_id,
            (p.close_time)::date AS valuation_date,
            pf.base_currency,
            p.profit_currency AS source_currency,
            'PROFIT'::character varying(16) AS component_type,
            COALESCE(p.profit, (0)::numeric) AS amount_native
           FROM (((investory.positions p
             JOIN investory.accounts a ON ((a.id = p.account_id)))
             JOIN investory.portfolios pf ON ((pf.id = a.portfolio_id)))
             JOIN investory.assets asset ON (((asset.id = p.asset_id) AND (asset.exclude_from_import = false))))
          WHERE (p.close_time IS NOT NULL)
        UNION ALL
         SELECT p.account_id,
            (p.close_time)::date AS close_time,
            pf.base_currency,
            p.profit_currency,
            'SWAP'::character varying(16) AS "varchar",
            COALESCE(p.swap, (0)::numeric) AS "coalesce"
           FROM (((investory.positions p
             JOIN investory.accounts a ON ((a.id = p.account_id)))
             JOIN investory.portfolios pf ON ((pf.id = a.portfolio_id)))
             JOIN investory.assets asset ON (((asset.id = p.asset_id) AND (asset.exclude_from_import = false))))
          WHERE ((p.close_time IS NOT NULL) AND (p.settlement_model <> 'RESULT_ONLY'::investory.position_settlement_model))
        UNION ALL
         SELECT p.account_id,
            (p.close_time)::date AS close_time,
            pf.base_currency,
            p.commission_currency,
            'COMMISSION'::character varying(16) AS "varchar",
            COALESCE(p.commission, (0)::numeric) AS "coalesce"
           FROM (((investory.positions p
             JOIN investory.accounts a ON ((a.id = p.account_id)))
             JOIN investory.portfolios pf ON ((pf.id = a.portfolio_id)))
             JOIN investory.assets asset ON (((asset.id = p.asset_id) AND (asset.exclude_from_import = false))))
          WHERE (p.close_time IS NOT NULL)
        ), trade_fx_keys AS MATERIALIZED (
         SELECT DISTINCT trade_components.valuation_date,
            trade_components.source_currency,
            trade_components.base_currency
           FROM trade_components
        ), resolved_trade_fx AS MATERIALIZED (
         SELECT k.valuation_date,
            k.source_currency,
            k.base_currency,
            fx.fx_rate_to_target,
            fx.conversion_status
           FROM (trade_fx_keys k
             LEFT JOIN LATERAL investory.resolve_fx_rate(k.valuation_date, k.source_currency, k.base_currency) fx(source_currency, target_currency, fx_rate_to_target, source, rate_method, rate_source, source_rate_date, age_days, conversion_status) ON (true))
        ), converted_trade_components AS (
         SELECT tc.account_id,
            tc.valuation_date,
            tc.base_currency,
            tc.source_currency,
            tc.component_type,
            tc.amount_native,
            fx.fx_rate_to_target,
            fx.conversion_status
           FROM (trade_components tc
             JOIN resolved_trade_fx fx ON (((fx.valuation_date = tc.valuation_date) AND (NOT ((fx.source_currency)::text IS DISTINCT FROM (tc.source_currency)::text)) AND (NOT ((fx.base_currency)::text IS DISTINCT FROM (tc.base_currency)::text)))))
        ), trade_result AS (
         SELECT ctc.account_id,
            ctc.valuation_date,
            sum((ctc.amount_native * ctc.fx_rate_to_target)) FILTER (WHERE (((ctc.component_type)::text = 'PROFIT'::text) AND investory.fx_status_usable(ctc.conversion_status))) AS realized_trade_profit,
            sum((ctc.amount_native * ctc.fx_rate_to_target)) FILTER (WHERE (((ctc.component_type)::text = 'SWAP'::text) AND investory.fx_status_usable(ctc.conversion_status))) AS swap_cost,
            sum((ctc.amount_native * ctc.fx_rate_to_target)) FILTER (WHERE (((ctc.component_type)::text = 'COMMISSION'::text) AND investory.fx_status_usable(ctc.conversion_status))) AS commission_cost,
            count(*) FILTER (WHERE (NOT investory.fx_status_usable(ctc.conversion_status))) AS missing_fx_count,
            (count(*) FILTER (WHERE (NOT investory.fx_status_usable(ctc.conversion_status))) = 0) AS is_complete
           FROM converted_trade_components ctc
          GROUP BY ctc.account_id, ctc.valuation_date
        ), cash_components AS (
         SELECT nco.account_id,
            (nco.date)::date AS valuation_date,
            sum((- nco.amount_in_portfolio_base_currency)) FILTER (WHERE ((nco.normalized_category)::text = 'FEE'::text)) AS fee_component,
            sum((- nco.amount_in_portfolio_base_currency)) FILTER (WHERE ((nco.raw_operation)::text = 'SWAP'::text)) AS swap_component,
            sum(nco.amount_in_portfolio_base_currency) FILTER (WHERE ((nco.raw_operation)::text = 'ROLLOVER'::text)) AS rollover_component,
            sum(nco.amount_in_portfolio_base_currency) FILTER (WHERE ((nco.normalized_category)::text = 'CORRECTION'::text)) AS correction_component,
            count(*) FILTER (WHERE (NOT investory.fx_status_usable(nco.portfolio_conversion_status))) AS missing_fx_count,
            (count(*) FILTER (WHERE (NOT investory.fx_status_usable(nco.portfolio_conversion_status))) = 0) AS is_complete
           FROM investory.app_v_normalized_cash_operations nco
          GROUP BY nco.account_id, ((nco.date)::date)
        )
 SELECT COALESCE(tr.account_id, cc.account_id) AS account_id,
    COALESCE(tr.valuation_date, cc.valuation_date) AS valuation_date,
        CASE
            WHEN COALESCE(tr.is_complete, true) THEN COALESCE(tr.realized_trade_profit, (0)::numeric)
            ELSE NULL::numeric
        END AS realized_trade_profit,
        CASE
            WHEN COALESCE(tr.is_complete, true) THEN COALESCE(tr.swap_cost, (0)::numeric)
            ELSE NULL::numeric
        END AS swap_cost,
        CASE
            WHEN COALESCE(tr.is_complete, true) THEN COALESCE(tr.commission_cost, (0)::numeric)
            ELSE NULL::numeric
        END AS commission_cost,
        CASE
            WHEN COALESCE(cc.is_complete, true) THEN COALESCE(cc.fee_component, (0)::numeric)
            ELSE NULL::numeric
        END AS fee_component,
        CASE
            WHEN COALESCE(cc.is_complete, true) THEN COALESCE(cc.swap_component, (0)::numeric)
            ELSE NULL::numeric
        END AS cash_swap_component,
        CASE
            WHEN COALESCE(cc.is_complete, true) THEN COALESCE(cc.rollover_component, (0)::numeric)
            ELSE NULL::numeric
        END AS rollover_component,
        CASE
            WHEN COALESCE(cc.is_complete, true) THEN COALESCE(cc.correction_component, (0)::numeric)
            ELSE NULL::numeric
        END AS correction_component,
    (COALESCE(tr.missing_fx_count, (0)::bigint) + COALESCE(cc.missing_fx_count, (0)::bigint)) AS missing_fx_count,
    (COALESCE(tr.is_complete, true) AND COALESCE(cc.is_complete, true)) AS is_complete,
        CASE
            WHEN (COALESCE(tr.is_complete, true) AND COALESCE(cc.is_complete, true)) THEN ((COALESCE(tr.realized_trade_profit, (0)::numeric) + COALESCE(tr.swap_cost, (0)::numeric)) + COALESCE(tr.commission_cost, (0)::numeric))
            ELSE NULL::numeric
        END AS reconstructed_total_realized_result
   FROM (trade_result tr
     FULL JOIN cash_components cc ON (((cc.account_id = tr.account_id) AND (cc.valuation_date = tr.valuation_date))));

CREATE MATERIALIZED VIEW investory.recon_v_reconstructed_account_market_daily_mv AS
 SELECT account_id,
    valuation_date,
    sum(COALESCE(reconstructed_market_value_base, (0)::numeric)) AS reconstructed_market_value,
    sum(COALESCE(reconstructed_cost_base_base, (0)::numeric)) AS reconstructed_cost_base,
    sum(COALESCE(reconstructed_unrealized_profit_base, (0)::numeric)) AS reconstructed_unrealized_profit,
    max(
        CASE
            WHEN ((reconstruction_status)::text = 'FAIL'::text) THEN 1
            ELSE 0
        END) AS has_market_fail,
    max(
        CASE
            WHEN (((reconstruction_status)::text = 'WARN'::text) AND ((price_quality IS NULL) OR (((price_quality)::text <> 'STALE_CARRY_FORWARD'::text) AND ((price_quality)::text !~~ '%TRADE%OBSERVATION%'::text)))) THEN 1
            ELSE 0
        END) AS has_market_warn
   FROM investory.recon_v_reconstructed_position_daily_mv rpd
  GROUP BY account_id, valuation_date;

CREATE MATERIALIZED VIEW investory.recon_v_account_daily_reconciliation_mv AS
 WITH parameters AS (
         SELECT (investory.reconciliation_parameter('reconciliation_reporting_scale'::character varying))::integer AS reporting_scale,
            investory.reconciliation_parameter('reconciliation_absolute_tolerance'::character varying) AS absolute_tolerance,
            investory.reconciliation_parameter('reconciliation_relative_tolerance'::character varying) AS relative_tolerance,
            investory.reconciliation_parameter('reconciliation_cash_absolute_tolerance'::character varying) AS cash_absolute_tolerance,
            investory.reconciliation_parameter('reconciliation_market_value_absolute_tolerance'::character varying) AS market_value_absolute_tolerance,
            investory.reconciliation_parameter('reconciliation_market_value_relative_tolerance'::character varying) AS market_value_relative_tolerance
        ), cash_side AS (
         SELECT rcd.account_id,
            rcd.valuation_date,
            sum(COALESCE(rcd.reconstructed_cash_component_base, (0)::numeric)) AS reconstructed_cash_balance,
            max(
                CASE
                    WHEN ((rcd.status)::text = 'FAIL'::text) THEN 1
                    ELSE 0
                END) AS has_cash_fail
           FROM investory.recon_v_reconstructed_cash_daily_mv rcd
          GROUP BY rcd.account_id, rcd.valuation_date
        ), realized_side AS (
         SELECT r.account_id,
            r.valuation_date,
                CASE
                    WHEN (count(*) FILTER (WHERE ((NOT COALESCE(r.is_complete, false)) OR (r.reconstructed_total_realized_result IS NULL))) > 0) THEN NULL::numeric
                    ELSE sum(r.reconstructed_total_realized_result)
                END AS reconstructed_total_realized_result,
            max(
                CASE
                    WHEN (NOT COALESCE(r.is_complete, false)) THEN 1
                    ELSE 0
                END) AS has_realized_fail
           FROM investory.recon_v_realized_result r
          GROUP BY r.account_id, r.valuation_date
        ), values_to_compare AS (
         SELECT ad.account_id,
            ad.snapshot_date AS valuation_date,
            ad.cash_balance AS reported_cash_balance,
            COALESCE(cs.reconstructed_cash_balance, (0)::numeric) AS reconstructed_cash_balance,
            ad.market_value AS reported_market_value,
            COALESCE(ms.reconstructed_market_value, (0)::numeric) AS reconstructed_market_value,
            ad.cost_base AS reported_cost_base,
            COALESCE(ms.reconstructed_cost_base, (0)::numeric) AS reconstructed_cost_base,
            ad.unrealized_profit AS reported_unrealized_profit,
            COALESCE(ms.reconstructed_unrealized_profit, (0)::numeric) AS reconstructed_unrealized_profit,
            ad.equity AS reported_equity,
            (COALESCE(cs.reconstructed_cash_balance, (0)::numeric) + COALESCE(ms.reconstructed_market_value, (0)::numeric)) AS reconstructed_equity,
            ad.realized_profit AS reported_realized_profit,
            rs.reconstructed_total_realized_result,
            COALESCE(ms.has_market_fail, 0) AS has_market_fail,
            COALESCE(ms.has_market_warn, 0) AS has_market_warn,
            COALESCE(cs.has_cash_fail, 0) AS has_cash_fail,
            COALESCE(rs.has_realized_fail, 0) AS has_realized_fail,
            rs.account_id AS realized_account_id
           FROM (((investory.account_daily ad
             LEFT JOIN investory.recon_v_reconstructed_account_market_daily_mv ms ON (((ms.account_id = ad.account_id) AND (ms.valuation_date = ad.snapshot_date))))
             LEFT JOIN cash_side cs ON (((cs.account_id = ad.account_id) AND (cs.valuation_date = ad.snapshot_date))))
             LEFT JOIN realized_side rs ON (((rs.account_id = ad.account_id) AND (rs.valuation_date = ad.snapshot_date))))
        ), checks AS (
         SELECT v.account_id,
            v.valuation_date,
            v.reported_cash_balance,
            v.reconstructed_cash_balance,
            v.reported_market_value,
            v.reconstructed_market_value,
            v.reported_cost_base,
            v.reconstructed_cost_base,
            v.reported_unrealized_profit,
            v.reconstructed_unrealized_profit,
            v.reported_equity,
            v.reconstructed_equity,
            v.reported_realized_profit,
            v.reconstructed_total_realized_result,
            v.has_market_fail,
            v.has_market_warn,
            v.has_cash_fail,
            v.has_realized_fail,
            v.realized_account_id,
            p.reporting_scale,
            p.absolute_tolerance,
            p.relative_tolerance,
            p.cash_absolute_tolerance,
            p.market_value_absolute_tolerance,
            p.market_value_relative_tolerance,
            ((v.reported_market_value IS NOT NULL) AND (abs((v.reconstructed_market_value - v.reported_market_value)) <= GREATEST(p.market_value_absolute_tolerance, (p.market_value_relative_tolerance * GREATEST(abs(v.reported_market_value), abs(v.reconstructed_market_value)))))) AS market_matches,
            ((v.reported_cash_balance IS NOT NULL) AND (abs((v.reconstructed_cash_balance - v.reported_cash_balance)) <= GREATEST(p.cash_absolute_tolerance, (p.relative_tolerance * GREATEST(abs(v.reported_cash_balance), abs(v.reconstructed_cash_balance)))))) AS cash_matches,
            ((v.reported_equity IS NOT NULL) AND (abs((v.reconstructed_equity - v.reported_equity)) <= GREATEST(p.market_value_absolute_tolerance, (p.market_value_relative_tolerance * GREATEST(abs(v.reported_equity), abs(v.reconstructed_equity)))))) AS equity_matches,
            ((v.reported_cost_base IS NOT NULL) AND (abs((v.reconstructed_cost_base - v.reported_cost_base)) <= GREATEST(p.absolute_tolerance, (p.relative_tolerance * GREATEST(abs(v.reported_cost_base), abs(v.reconstructed_cost_base)))))) AS cost_base_matches,
            ((v.reported_unrealized_profit IS NOT NULL) AND (abs((v.reconstructed_unrealized_profit - v.reported_unrealized_profit)) <= GREATEST(p.market_value_absolute_tolerance, (p.market_value_relative_tolerance * GREATEST(abs(v.reported_market_value), abs(v.reconstructed_market_value)))))) AS unrealized_matches,
            ((v.reported_realized_profit IS NOT NULL) AND (abs((COALESCE(v.reconstructed_total_realized_result, (0)::numeric) - v.reported_realized_profit)) <= GREATEST(p.absolute_tolerance, (p.relative_tolerance * GREATEST(abs(v.reported_realized_profit), abs(COALESCE(v.reconstructed_total_realized_result, (0)::numeric))))))) AS realized_matches
           FROM (values_to_compare v
             CROSS JOIN parameters p)
        )
 SELECT account_id,
    valuation_date,
    round(reported_cash_balance, reporting_scale) AS reported_cash_balance,
    round(reconstructed_cash_balance, reporting_scale) AS reconstructed_cash_balance,
    round((reported_cash_balance - reconstructed_cash_balance), reporting_scale) AS cash_difference,
    round(reported_market_value, reporting_scale) AS reported_market_value,
    round(reconstructed_market_value, reporting_scale) AS reconstructed_market_value,
    round((reported_market_value - reconstructed_market_value), reporting_scale) AS market_value_difference,
    round(reported_cost_base, reporting_scale) AS reported_cost_base,
    round(reconstructed_cost_base, reporting_scale) AS reconstructed_cost_base,
    round((reported_cost_base - reconstructed_cost_base), reporting_scale) AS cost_base_difference,
    round(reported_unrealized_profit, reporting_scale) AS reported_unrealized_profit,
    round(reconstructed_unrealized_profit, reporting_scale) AS reconstructed_unrealized_profit,
    round((reported_unrealized_profit - reconstructed_unrealized_profit), reporting_scale) AS unrealized_difference,
    round(reported_equity, reporting_scale) AS reported_equity,
    round(reconstructed_equity, reporting_scale) AS reconstructed_equity,
    round((reported_equity - reconstructed_equity), reporting_scale) AS equity_difference,
    round(reported_realized_profit, reporting_scale) AS reported_realized_profit,
        CASE
            WHEN (has_realized_fail = 1) THEN NULL::numeric
            ELSE round(COALESCE(reconstructed_total_realized_result, (0)::numeric), reporting_scale)
        END AS reconstructed_total_realized_result,
        CASE
            WHEN ((has_realized_fail = 1) OR ((reconstructed_total_realized_result IS NULL) AND (realized_account_id IS NOT NULL))) THEN NULL::numeric
            ELSE round((reported_realized_profit - COALESCE(reconstructed_total_realized_result, (0)::numeric)), reporting_scale)
        END AS realized_difference,
        CASE
            WHEN ((reported_market_value IS NULL) OR (reconstructed_market_value IS NULL)) THEN NULL::numeric
            ELSE round(GREATEST(market_value_absolute_tolerance, (market_value_relative_tolerance * GREATEST(abs(reported_market_value), abs(reconstructed_market_value)))), reporting_scale)
        END AS market_value_effective_tolerance,
        CASE
            WHEN ((reported_cash_balance IS NULL) OR (reconstructed_cash_balance IS NULL)) THEN NULL::numeric
            ELSE round(GREATEST(cash_absolute_tolerance, (relative_tolerance * GREATEST(abs(reported_cash_balance), abs(reconstructed_cash_balance)))), reporting_scale)
        END AS cash_effective_tolerance,
        CASE
            WHEN ((reported_equity IS NULL) OR (reconstructed_equity IS NULL)) THEN NULL::numeric
            ELSE round(GREATEST(market_value_absolute_tolerance, (market_value_relative_tolerance * GREATEST(abs(reported_equity), abs(reconstructed_equity)))), reporting_scale)
        END AS equity_effective_tolerance,
        CASE
            WHEN ((reported_cost_base IS NULL) OR (reconstructed_cost_base IS NULL)) THEN NULL::numeric
            ELSE round(GREATEST(absolute_tolerance, (relative_tolerance * GREATEST(abs(reported_cost_base), abs(reconstructed_cost_base)))), reporting_scale)
        END AS cost_base_effective_tolerance,
        CASE
            WHEN ((reported_unrealized_profit IS NULL) OR (reconstructed_unrealized_profit IS NULL)) THEN NULL::numeric
            ELSE round(GREATEST(market_value_absolute_tolerance, (market_value_relative_tolerance * GREATEST(abs(reported_market_value), abs(reconstructed_market_value)))), reporting_scale)
        END AS unrealized_effective_tolerance,
        CASE
            WHEN (reported_realized_profit IS NULL) THEN NULL::numeric
            ELSE round(GREATEST(absolute_tolerance, (relative_tolerance * GREATEST(abs(reported_realized_profit), abs(COALESCE(reconstructed_total_realized_result, (0)::numeric))))), reporting_scale)
        END AS realized_effective_tolerance,
    (
        CASE
            WHEN ((has_market_fail = 1) OR (has_cash_fail = 1) OR (has_realized_fail = 1)) THEN 'FAIL'::text
            WHEN ((NOT market_matches) OR (NOT cash_matches) OR (NOT equity_matches) OR (NOT cost_base_matches) OR (NOT unrealized_matches) OR (NOT realized_matches)) THEN 'FAIL'::text
            WHEN (has_market_warn = 1) THEN 'WARN'::text
            ELSE 'PASS'::text
        END)::character varying(16) AS status,
    (
        CASE
            WHEN ((has_market_fail = 1) OR (has_cash_fail = 1) OR (has_realized_fail = 1)) THEN 'ERROR'::text
            WHEN (has_market_warn = 1) THEN 'WARN'::text
            ELSE 'INFO'::text
        END)::character varying(16) AS severity,
        CASE
            WHEN (has_market_fail = 1) THEN 'position reconstruction failed'::text
            WHEN (has_cash_fail = 1) THEN 'cash reconstruction failed'::text
            WHEN (has_realized_fail = 1) THEN 'realized result reconstruction failed'::text
            WHEN (NOT market_matches) THEN 'market value mismatch'::text
            WHEN (NOT cash_matches) THEN 'cash mismatch'::text
            WHEN (NOT equity_matches) THEN 'equity mismatch'::text
            WHEN (NOT cost_base_matches) THEN 'cost base mismatch'::text
            WHEN (NOT unrealized_matches) THEN 'unrealized mismatch'::text
            WHEN (NOT realized_matches) THEN 'realized result mismatch'::text
            WHEN (has_market_warn = 1) THEN 'valuation used lower-quality price source'::text
            ELSE 'reconciliation passed'::text
        END AS validation_message
   FROM checks;

CREATE VIEW investory.recon_v_account_daily AS
 SELECT account_id,
    valuation_date,
    reported_cash_balance,
    reconstructed_cash_balance,
    cash_difference,
    reported_market_value,
    reconstructed_market_value,
    market_value_difference,
    reported_cost_base,
    reconstructed_cost_base,
    cost_base_difference,
    reported_unrealized_profit,
    reconstructed_unrealized_profit,
    unrealized_difference,
    reported_equity,
    reconstructed_equity,
    equity_difference,
    reported_realized_profit,
    reconstructed_total_realized_result,
    realized_difference,
    market_value_effective_tolerance,
    cash_effective_tolerance,
    equity_effective_tolerance,
    cost_base_effective_tolerance,
    unrealized_effective_tolerance,
    realized_effective_tolerance,
    status,
    severity,
    validation_message
   FROM investory.recon_v_account_daily_reconciliation_mv;

CREATE MATERIALIZED VIEW investory.recon_v_account_daily_cashflow AS
 WITH ledger_daily AS (
         SELECT nco.account_id,
            (nco.date)::date AS snapshot_date,
            nco.account_currency,
            nco.base_currency,
                CASE
                    WHEN (count(DISTINCT nco.currency) = 1) THEN sum(nco.amount)
                    ELSE NULL::numeric
                END AS ledger_cash_native,
            sum(nco.amount_in_portfolio_base_currency) FILTER (WHERE investory.fx_status_usable(nco.portfolio_conversion_status)) AS ledger_cash_base,
            sum(nco.amount_in_portfolio_base_currency) FILTER (WHERE ((nco.normalized_category)::text = 'EXTERNAL_DEPOSIT'::text)) AS ledger_deposits,
            sum(abs(nco.amount_in_portfolio_base_currency)) FILTER (WHERE ((nco.normalized_category)::text = 'EXTERNAL_WITHDRAWAL'::text)) AS ledger_withdrawals,
            sum(nco.amount_in_portfolio_base_currency) FILTER (WHERE ((nco.normalized_category)::text = ANY (ARRAY[('DIVIDEND'::character varying)::text, ('DIVIDEND_REVERSAL'::character varying)::text]))) AS ledger_dividends,
            sum(nco.amount_in_portfolio_base_currency) FILTER (WHERE ((nco.normalized_category)::text = ANY (ARRAY[('INTEREST'::character varying)::text, ('INTEREST_REVERSAL'::character varying)::text]))) AS ledger_interest,
            sum((- nco.amount_in_portfolio_base_currency)) FILTER (WHERE ((nco.normalized_category)::text = 'FEE'::text)) AS ledger_fees,
            sum((- nco.amount_in_portfolio_base_currency)) FILTER (WHERE ((nco.normalized_category)::text = ANY (ARRAY[('WITHHOLDING_TAX'::character varying)::text, ('WITHHOLDING_TAX_REVERSAL'::character varying)::text, ('OTHER_TAX'::character varying)::text]))) AS ledger_taxes,
            count(*) FILTER (WHERE (NOT investory.fx_status_usable(nco.portfolio_conversion_status))) AS missing_fx_count,
            (count(*) FILTER (WHERE (NOT investory.fx_status_usable(nco.portfolio_conversion_status))) = 0) AS is_complete
           FROM investory.app_v_normalized_cash_operations nco
          GROUP BY nco.account_id, ((nco.date)::date), nco.account_currency, nco.base_currency
        ), daily_with_prev AS (
         SELECT ad_1.account_id,
            ad_1.snapshot_date,
            ad_1.valuation_currency,
            ad_1.cash_balance,
            lag(ad_1.cash_balance) OVER (PARTITION BY ad_1.account_id ORDER BY ad_1.snapshot_date) AS previous_cash_balance,
            ad_1.deposits,
            ad_1.withdrawals,
            ad_1.dividends,
            ad_1.interest,
            ad_1.fees,
            ad_1.taxes
           FROM investory.account_daily ad_1
        )
 SELECT ad.account_id,
    ad.snapshot_date,
    ld.account_currency,
    COALESCE(ld.base_currency, ad.valuation_currency) AS ledger_base_currency,
    ad.valuation_currency,
    investory.reconciliation_display_value(ad.previous_cash_balance) AS previous_cash_balance,
    investory.reconciliation_display_value(ad.cash_balance) AS cash_balance,
    investory.reconciliation_display_value((ad.cash_balance - COALESCE(ad.previous_cash_balance, (0)::numeric))) AS account_daily_cash_delta,
    ld.ledger_cash_native,
    ld.ledger_cash_base,
    ld.missing_fx_count,
    ld.is_complete,
        CASE
            WHEN ((ld.account_currency)::text = (COALESCE(ld.base_currency, ad.valuation_currency))::text) THEN investory.reconciliation_display_value(ld.ledger_cash_base)
            ELSE NULL::numeric
        END AS expected_cash_delta_base_for_same_currency_account,
        CASE
            WHEN ((ld.account_currency)::text = (COALESCE(ld.base_currency, ad.valuation_currency))::text) THEN investory.reconciliation_display_value(((ad.cash_balance - COALESCE(ad.previous_cash_balance, (0)::numeric)) - COALESCE(ld.ledger_cash_base, (0)::numeric)))
            ELSE NULL::numeric
        END AS same_currency_cash_delta_gap,
    investory.reconciliation_display_value(ad.deposits) AS account_daily_deposits,
    investory.reconciliation_display_value(ld.ledger_deposits) AS ledger_deposits,
    investory.reconciliation_display_value((ad.deposits - COALESCE(ld.ledger_deposits, (0)::numeric))) AS deposits_gap,
    investory.reconciliation_display_value(ad.withdrawals) AS account_daily_withdrawals,
    investory.reconciliation_display_value(ld.ledger_withdrawals) AS ledger_withdrawals,
    investory.reconciliation_display_value((ad.withdrawals - COALESCE(ld.ledger_withdrawals, (0)::numeric))) AS withdrawals_gap,
    investory.reconciliation_display_value(ad.dividends) AS account_daily_dividends,
    investory.reconciliation_display_value(ld.ledger_dividends) AS ledger_dividends,
    investory.reconciliation_display_value((ad.dividends - COALESCE(ld.ledger_dividends, (0)::numeric))) AS dividends_gap,
    investory.reconciliation_display_value(ad.interest) AS account_daily_interest,
    investory.reconciliation_display_value(ld.ledger_interest) AS ledger_interest,
    investory.reconciliation_display_value((ad.interest - COALESCE(ld.ledger_interest, (0)::numeric))) AS interest_gap,
    investory.reconciliation_display_value(ad.fees) AS account_daily_fees,
    investory.reconciliation_display_value(ld.ledger_fees) AS ledger_fees,
    investory.reconciliation_display_value((ad.fees - COALESCE(ld.ledger_fees, (0)::numeric))) AS fees_gap,
    investory.reconciliation_display_value(ad.taxes) AS account_daily_taxes,
    investory.reconciliation_display_value(ld.ledger_taxes) AS ledger_taxes,
    investory.reconciliation_display_value((ad.taxes - COALESCE(ld.ledger_taxes, (0)::numeric))) AS taxes_gap,
    investory.reconciliation_display_value(investory.reconciliation_effective_tolerance((ad.cash_balance - COALESCE(ad.previous_cash_balance, (0)::numeric)), ld.ledger_cash_base)) AS cash_delta_effective_tolerance,
    investory.reconciliation_display_value(investory.reconciliation_effective_tolerance(ad.deposits, ld.ledger_deposits)) AS deposits_effective_tolerance,
    investory.reconciliation_display_value(investory.reconciliation_effective_tolerance(ad.withdrawals, ld.ledger_withdrawals)) AS withdrawals_effective_tolerance,
    investory.reconciliation_display_value(investory.reconciliation_effective_tolerance(ad.dividends, ld.ledger_dividends)) AS dividends_effective_tolerance,
    investory.reconciliation_display_value(investory.reconciliation_effective_tolerance(ad.interest, ld.ledger_interest)) AS interest_effective_tolerance,
    investory.reconciliation_display_value(investory.reconciliation_effective_tolerance(ad.fees, ld.ledger_fees)) AS fees_effective_tolerance,
    investory.reconciliation_display_value(investory.reconciliation_effective_tolerance(ad.taxes, ld.ledger_taxes)) AS taxes_effective_tolerance
   FROM (daily_with_prev ad
     LEFT JOIN ledger_daily ld ON (((ld.account_id = ad.account_id) AND (ld.snapshot_date = ad.snapshot_date))));

CREATE VIEW investory.recon_v_account_daily_cashflow_full_precision AS
 WITH ledger_daily AS (
         SELECT nco.account_id,
            ((nco.date AT TIME ZONE 'Europe/Warsaw'::text))::date AS snapshot_date,
            nco.account_currency,
            nco.base_currency,
            sum(nco.amount_in_portfolio_base_currency) FILTER (WHERE investory.fx_status_usable(nco.portfolio_conversion_status)) AS ledger_cash_base,
            sum(nco.account_flow_amount_in_portfolio_base_currency) FILTER (WHERE (nco.account_flow_amount_in_portfolio_base_currency > (0)::numeric)) AS ledger_deposits,
            sum((- nco.account_flow_amount_in_portfolio_base_currency)) FILTER (WHERE (nco.account_flow_amount_in_portfolio_base_currency < (0)::numeric)) AS ledger_withdrawals,
            sum(nco.amount_in_portfolio_base_currency) FILTER (WHERE ((nco.normalized_category)::text = ANY ((ARRAY['DIVIDEND'::character varying, 'DIVIDEND_REVERSAL'::character varying])::text[]))) AS ledger_dividends,
            sum(nco.amount_in_portfolio_base_currency) FILTER (WHERE ((nco.normalized_category)::text = ANY ((ARRAY['INTEREST'::character varying, 'INTEREST_REVERSAL'::character varying])::text[]))) AS ledger_interest,
            sum((- nco.amount_in_portfolio_base_currency)) FILTER (WHERE ((nco.normalized_category)::text = 'FEE'::text)) AS ledger_fees,
            sum((- nco.amount_in_portfolio_base_currency)) FILTER (WHERE ((nco.normalized_category)::text = ANY ((ARRAY['WITHHOLDING_TAX'::character varying, 'WITHHOLDING_TAX_REVERSAL'::character varying, 'OTHER_TAX'::character varying])::text[]))) AS ledger_taxes,
            count(*) FILTER (WHERE ((nco.normalized_category)::text = ANY ((ARRAY['INTERNAL_TRANSFER_IN'::character varying, 'INTERNAL_TRANSFER_OUT'::character varying, 'INTERNAL_BOOKKEEPING'::character varying])::text[]))) AS internal_operation_count,
            (count(*) FILTER (WHERE (NOT investory.fx_status_usable(nco.portfolio_conversion_status))) = 0) AS is_complete
           FROM investory.app_v_normalized_cash_operation_flows nco
          GROUP BY nco.account_id, (((nco.date AT TIME ZONE 'Europe/Warsaw'::text))::date), nco.account_currency, nco.base_currency
        ), daily_with_prev AS (
         SELECT ad_1.account_id,
            ad_1.snapshot_date,
            ad_1.cash_balance,
            lag(ad_1.cash_balance) OVER (PARTITION BY ad_1.account_id ORDER BY ad_1.snapshot_date) AS previous_cash_balance,
            ad_1.deposits,
            ad_1.withdrawals,
            ad_1.dividends,
            ad_1.interest,
            ad_1.fees,
            ad_1.taxes
           FROM investory.account_daily ad_1
        )
 SELECT ad.account_id,
    ad.snapshot_date,
    account.currency AS account_currency,
    portfolio.base_currency AS ledger_base_currency,
    COALESCE(ld.is_complete, true) AS is_complete,
    COALESCE(ld.internal_operation_count, (0)::bigint) AS internal_operation_count,
    (ad.cash_balance - COALESCE(ad.previous_cash_balance, (0)::numeric)) AS account_cash_delta,
    COALESCE(ld.ledger_cash_base, (0)::numeric) AS ledger_cash_delta,
    ad.deposits,
    COALESCE(ld.ledger_deposits, (0)::numeric) AS ledger_deposits,
    ad.withdrawals,
    COALESCE(ld.ledger_withdrawals, (0)::numeric) AS ledger_withdrawals,
    ad.dividends,
    COALESCE(ld.ledger_dividends, (0)::numeric) AS ledger_dividends,
    ad.interest,
    COALESCE(ld.ledger_interest, (0)::numeric) AS ledger_interest,
    ad.fees,
    COALESCE(ld.ledger_fees, (0)::numeric) AS ledger_fees,
    ad.taxes,
    COALESCE(ld.ledger_taxes, (0)::numeric) AS ledger_taxes,
        CASE
            WHEN ((account.currency)::text = (portfolio.base_currency)::text) THEN ((ad.cash_balance - COALESCE(ad.previous_cash_balance, (0)::numeric)) - COALESCE(ld.ledger_cash_base, (0)::numeric))
            ELSE NULL::numeric
        END AS same_currency_cash_delta_gap,
    (ad.deposits - COALESCE(ld.ledger_deposits, (0)::numeric)) AS deposits_gap,
    (ad.withdrawals - COALESCE(ld.ledger_withdrawals, (0)::numeric)) AS withdrawals_gap,
    (ad.dividends - COALESCE(ld.ledger_dividends, (0)::numeric)) AS dividends_gap,
    (ad.interest - COALESCE(ld.ledger_interest, (0)::numeric)) AS interest_gap,
    (ad.fees - COALESCE(ld.ledger_fees, (0)::numeric)) AS fees_gap,
    (ad.taxes - COALESCE(ld.ledger_taxes, (0)::numeric)) AS taxes_gap
   FROM (((daily_with_prev ad
     LEFT JOIN ledger_daily ld ON (((ld.account_id = ad.account_id) AND (ld.snapshot_date = ad.snapshot_date))))
     JOIN investory.accounts account ON ((account.id = ad.account_id)))
     JOIN investory.portfolios portfolio ON ((portfolio.id = account.portfolio_id)));

CREATE MATERIALIZED VIEW investory.recon_v_account_daily_cashflow_scope AS
 WITH internal_daily AS (
         SELECT nco.account_id,
            (nco.date)::date AS snapshot_date,
            COALESCE(sum(nco.amount_in_portfolio_base_currency) FILTER (WHERE ((nco.amount_in_portfolio_base_currency > (0)::numeric) AND ((nco.normalized_category)::text = ANY (ARRAY[('INTERNAL_TRANSFER_IN'::character varying)::text, ('INTERNAL_BOOKKEEPING'::character varying)::text])))), (0)::numeric) AS internal_inflow_base,
            COALESCE(sum((- nco.amount_in_portfolio_base_currency)) FILTER (WHERE ((nco.amount_in_portfolio_base_currency < (0)::numeric) AND ((nco.normalized_category)::text = ANY (ARRAY[('INTERNAL_TRANSFER_OUT'::character varying)::text, ('INTERNAL_BOOKKEEPING'::character varying)::text])))), (0)::numeric) AS internal_outflow_base,
            count(*) FILTER (WHERE ((nco.normalized_category)::text = ANY (ARRAY[('INTERNAL_TRANSFER_IN'::character varying)::text, ('INTERNAL_TRANSFER_OUT'::character varying)::text, ('INTERNAL_BOOKKEEPING'::character varying)::text]))) AS internal_operation_count
           FROM investory.app_v_normalized_cash_operations nco
          GROUP BY nco.account_id, ((nco.date)::date)
        ), evidence AS (
         SELECT r.account_id,
            r.snapshot_date,
            r.account_currency,
            r.ledger_base_currency,
            r.valuation_currency,
            r.previous_cash_balance,
            r.cash_balance,
            r.account_daily_cash_delta,
            r.ledger_cash_native,
            r.ledger_cash_base,
            r.missing_fx_count,
            r.is_complete,
            r.expected_cash_delta_base_for_same_currency_account,
            r.same_currency_cash_delta_gap,
            r.account_daily_deposits,
            r.ledger_deposits,
            r.deposits_gap,
            r.account_daily_withdrawals,
            r.ledger_withdrawals,
            r.withdrawals_gap,
            r.account_daily_dividends,
            r.ledger_dividends,
            r.dividends_gap,
            r.account_daily_interest,
            r.ledger_interest,
            r.interest_gap,
            r.account_daily_fees,
            r.ledger_fees,
            r.fees_gap,
            r.account_daily_taxes,
            r.ledger_taxes,
            r.taxes_gap,
            r.cash_delta_effective_tolerance,
            r.deposits_effective_tolerance,
            r.withdrawals_effective_tolerance,
            r.dividends_effective_tolerance,
            r.interest_effective_tolerance,
            r.fees_effective_tolerance,
            r.taxes_effective_tolerance,
            COALESCE(i.internal_inflow_base, (0)::numeric) AS internal_inflow_base,
            COALESCE(i.internal_outflow_base, (0)::numeric) AS internal_outflow_base,
            COALESCE(i.internal_operation_count, (0)::bigint) AS internal_operation_count
           FROM (investory.recon_v_account_daily_cashflow r
             LEFT JOIN internal_daily i ON (((i.account_id = r.account_id) AND (i.snapshot_date = r.snapshot_date))))
        )
 SELECT account_id,
    snapshot_date,
    account_currency,
    ledger_base_currency,
    valuation_currency,
    previous_cash_balance,
    cash_balance,
    account_daily_cash_delta,
    ledger_cash_native,
    ledger_cash_base,
    missing_fx_count,
    is_complete,
    expected_cash_delta_base_for_same_currency_account,
    same_currency_cash_delta_gap,
    account_daily_deposits,
    ledger_deposits,
    deposits_gap,
    account_daily_withdrawals,
    ledger_withdrawals,
    withdrawals_gap,
    account_daily_dividends,
    ledger_dividends,
    dividends_gap,
    account_daily_interest,
    ledger_interest,
    interest_gap,
    account_daily_fees,
    ledger_fees,
    fees_gap,
    account_daily_taxes,
    ledger_taxes,
    taxes_gap,
    cash_delta_effective_tolerance,
    deposits_effective_tolerance,
    withdrawals_effective_tolerance,
    dividends_effective_tolerance,
    interest_effective_tolerance,
    fees_effective_tolerance,
    taxes_effective_tolerance,
    internal_inflow_base,
    internal_outflow_base,
    internal_operation_count,
        CASE
            WHEN ((internal_operation_count > 0) AND (((abs(COALESCE(deposits_gap, (0)::numeric)) > (20)::numeric) AND (abs(deposits_gap) > (0.01 * GREATEST(abs(COALESCE(account_daily_deposits, (0)::numeric)), abs(COALESCE(ledger_deposits, (0)::numeric)))))) OR ((abs(COALESCE(withdrawals_gap, (0)::numeric)) > (20)::numeric) AND (abs(withdrawals_gap) > (0.01 * GREATEST(abs(COALESCE(account_daily_withdrawals, (0)::numeric)), abs(COALESCE(ledger_withdrawals, (0)::numeric)))))))) THEN 'INTERNAL_TRANSFER_SCOPE_REVIEW'::text
            ELSE 'UNCLASSIFIED'::text
        END AS component_diagnostic_code
   FROM evidence e;

CREATE VIEW investory.recon_v_account_daily_diagnostic AS
 WITH raw_cash AS (
         SELECT ad.account_id,
            ad.snapshot_date AS valuation_date,
            ad.cash_balance AS reported_cash_balance_raw,
            sum(rc.reconstructed_cash_component_base) AS reconstructed_cash_balance_raw
           FROM (investory.account_daily ad
             LEFT JOIN investory.recon_v_reconstructed_cash_daily_mv rc ON (((rc.account_id = ad.account_id) AND (rc.valuation_date = ad.snapshot_date))))
          GROUP BY ad.account_id, ad.snapshot_date, ad.cash_balance
        ), classified AS (
         SELECT r_1.account_id,
            r_1.valuation_date,
            r_1.reported_cash_balance,
            r_1.reconstructed_cash_balance,
            r_1.cash_difference,
            r_1.reported_market_value,
            r_1.reconstructed_market_value,
            r_1.market_value_difference,
            r_1.reported_cost_base,
            r_1.reconstructed_cost_base,
            r_1.cost_base_difference,
            r_1.reported_unrealized_profit,
            r_1.reconstructed_unrealized_profit,
            r_1.unrealized_difference,
            r_1.reported_equity,
            r_1.reconstructed_equity,
            r_1.equity_difference,
            r_1.reported_realized_profit,
            r_1.reconstructed_total_realized_result,
            r_1.realized_difference,
            r_1.market_value_effective_tolerance,
            r_1.cash_effective_tolerance,
            r_1.equity_effective_tolerance,
            r_1.cost_base_effective_tolerance,
            r_1.unrealized_effective_tolerance,
            r_1.realized_effective_tolerance,
            r_1.status,
            r_1.severity,
            r_1.validation_message,
            c.reported_cash_balance_raw,
            c.reconstructed_cash_balance_raw,
            (abs(r_1.market_value_difference) <= GREATEST(investory.reconciliation_parameter('reconciliation_market_value_absolute_tolerance'::character varying), (investory.reconciliation_parameter('reconciliation_market_value_relative_tolerance'::character varying) * GREATEST(abs(r_1.reported_market_value), abs(r_1.reconstructed_market_value))))) AS market_value_within_tolerance,
            ((c.reported_cash_balance_raw IS NOT NULL) AND (c.reconstructed_cash_balance_raw IS NOT NULL) AND (abs((c.reported_cash_balance_raw - c.reconstructed_cash_balance_raw)) <= GREATEST(investory.reconciliation_parameter('reconciliation_cash_absolute_tolerance'::character varying), (investory.reconciliation_parameter('reconciliation_relative_tolerance'::character varying) * GREATEST(abs(c.reported_cash_balance_raw), abs(c.reconstructed_cash_balance_raw)))))) AS cash_within_tolerance,
            (EXISTS ( SELECT 1
                   FROM investory.app_v_reconstructed_position_daily p
                  WHERE ((p.account_id = r_1.account_id) AND (p.valuation_date = r_1.valuation_date) AND ((p.reconstruction_status)::text = 'WARN'::text)))) AS has_lower_quality_valuation
           FROM (investory.recon_v_account_daily r_1
             LEFT JOIN raw_cash c ON (((c.account_id = r_1.account_id) AND (c.valuation_date = r_1.valuation_date))))
        )
 SELECT account_id,
    valuation_date,
    reported_cash_balance,
    reconstructed_cash_balance,
    cash_difference,
    reported_market_value,
    reconstructed_market_value,
    market_value_difference,
    reported_cost_base,
    reconstructed_cost_base,
    cost_base_difference,
    reported_unrealized_profit,
    reconstructed_unrealized_profit,
    unrealized_difference,
    reported_equity,
    reconstructed_equity,
    equity_difference,
    reported_realized_profit,
    reconstructed_total_realized_result,
    realized_difference,
    market_value_effective_tolerance,
    cash_effective_tolerance,
    equity_effective_tolerance,
    cost_base_effective_tolerance,
    unrealized_effective_tolerance,
    realized_effective_tolerance,
    (
        CASE
            WHEN ((validation_message = 'market value mismatch'::text) AND market_value_within_tolerance AND has_lower_quality_valuation) THEN 'WARN'::character varying
            WHEN ((validation_message = 'market value mismatch'::text) AND market_value_within_tolerance) THEN 'PASS'::character varying
            WHEN ((validation_message = 'cash mismatch'::text) AND cash_within_tolerance AND has_lower_quality_valuation) THEN 'WARN'::character varying
            WHEN ((validation_message = 'cash mismatch'::text) AND cash_within_tolerance) THEN 'PASS'::character varying
            ELSE status
        END)::character varying(16) AS status,
    (
        CASE
            WHEN ((validation_message = ANY (ARRAY['market value mismatch'::text, 'cash mismatch'::text])) AND (((validation_message = 'market value mismatch'::text) AND market_value_within_tolerance) OR ((validation_message = 'cash mismatch'::text) AND cash_within_tolerance)) AND has_lower_quality_valuation) THEN 'WARN'::character varying
            WHEN ((validation_message = ANY (ARRAY['market value mismatch'::text, 'cash mismatch'::text])) AND (((validation_message = 'market value mismatch'::text) AND market_value_within_tolerance) OR ((validation_message = 'cash mismatch'::text) AND cash_within_tolerance))) THEN 'INFO'::character varying
            ELSE severity
        END)::character varying(16) AS severity,
        CASE
            WHEN ((validation_message = ANY (ARRAY['market value mismatch'::text, 'cash mismatch'::text])) AND (((validation_message = 'market value mismatch'::text) AND market_value_within_tolerance) OR ((validation_message = 'cash mismatch'::text) AND cash_within_tolerance)) AND has_lower_quality_valuation) THEN 'valuation used lower-quality price source'::text
            WHEN ((validation_message = ANY (ARRAY['market value mismatch'::text, 'cash mismatch'::text])) AND (((validation_message = 'market value mismatch'::text) AND market_value_within_tolerance) OR ((validation_message = 'cash mismatch'::text) AND cash_within_tolerance))) THEN 'reconciliation passed'::text
            ELSE validation_message
        END AS validation_message,
    (
        CASE validation_message
            WHEN 'cash mismatch'::text THEN 'ACCOUNT_DAILY_CASH_RECONCILIATION'::text
            WHEN 'cash reconstruction failed'::text THEN 'ACCOUNT_DAILY_CASH_RECONCILIATION'::text
            WHEN 'market value mismatch'::text THEN 'ACCOUNT_DAILY_MARKET_VALUE_RECONCILIATION'::text
            WHEN 'position reconstruction failed'::text THEN 'ACCOUNT_DAILY_MARKET_VALUE_RECONCILIATION'::text
            WHEN 'valuation used lower-quality price source'::text THEN 'ACCOUNT_DAILY_MARKET_VALUE_RECONCILIATION'::text
            WHEN 'cost base mismatch'::text THEN 'ACCOUNT_DAILY_COST_BASE_RECONCILIATION'::text
            WHEN 'unrealized mismatch'::text THEN 'ACCOUNT_DAILY_UNREALIZED_RECONCILIATION'::text
            WHEN 'realized result mismatch'::text THEN 'ACCOUNT_DAILY_REALIZED_RECONCILIATION'::text
            WHEN 'realized result reconstruction failed'::text THEN 'ACCOUNT_DAILY_REALIZED_RECONCILIATION'::text
            WHEN 'equity mismatch'::text THEN 'ACCOUNT_DAILY_EQUITY_RECONCILIATION'::text
            ELSE 'UNKNOWN'::text
        END)::character varying(96) AS diagnostic_code
   FROM classified r;

CREATE VIEW investory.recon_v_account_daily_market_value_semantic_review AS
 SELECT r.account_id,
    a.name AS account_name,
    a.provider,
    r.valuation_date,
    r.reported_market_value,
    r.reconstructed_market_value,
    r.market_value_difference,
    r.reported_equity,
    r.reconstructed_equity,
    r.equity_difference,
    r.reported_unrealized_profit,
    r.reconstructed_unrealized_profit,
    r.unrealized_difference,
    'MARKET_VALUE_SEMANTIC_REVIEW'::character varying(64) AS diagnostic_code,
    'Imported broker market value differs from canonical position reconstruction; cash, cost base, and realized result are unchanged.'::text AS diagnostic_message
   FROM (investory.recon_v_account_daily r
     JOIN investory.accounts a ON ((a.id = r.account_id)))
  WHERE (((r.status)::text = 'FAIL'::text) AND (r.validation_message = 'market value mismatch'::text) AND (r.cash_difference = (0)::numeric) AND (r.cost_base_difference = (0)::numeric) AND (r.realized_difference = (0)::numeric) AND (r.equity_difference = r.market_value_difference) AND (r.unrealized_difference = r.market_value_difference));

CREATE VIEW investory.recon_v_account_daily_performance_flow AS
 WITH account_days AS (
         SELECT ad_1.account_id,
            ad_1.snapshot_date,
            lag(ad_1.equity, 1, (0)::numeric) OVER (PARTITION BY ad_1.account_id ORDER BY ad_1.snapshot_date) AS previous_equity,
            ad_1.equity,
            ad_1.deposits,
            ad_1.withdrawals,
            ad_1.daily_profit_amount
           FROM investory.account_daily ad_1
        ), flows AS (
         SELECT nco.account_id,
            (nco.date)::date AS snapshot_date,
                CASE
                    WHEN (count(*) FILTER (WHERE (NOT investory.fx_status_usable(nco.portfolio_conversion_status))) > 0) THEN NULL::numeric
                    ELSE sum(COALESCE(nco.account_flow_amount_in_portfolio_base_currency, (0)::numeric))
                END AS account_funding_flow,
                CASE
                    WHEN (count(*) FILTER (WHERE (NOT investory.fx_status_usable(nco.portfolio_conversion_status))) > 0) THEN NULL::numeric
                    ELSE sum(COALESCE(nco.performance_flow_amount_in_portfolio_base_currency, (0)::numeric))
                END AS performance_flow,
                CASE
                    WHEN (count(*) FILTER (WHERE (NOT investory.fx_status_usable(nco.portfolio_conversion_status))) > 0) THEN NULL::numeric
                    ELSE sum(COALESCE(nco.portfolio_flow_amount_in_portfolio_base_currency, (0)::numeric))
                END AS portfolio_flow
           FROM investory.app_v_normalized_cash_operation_flows nco
          GROUP BY nco.account_id, ((nco.date)::date)
        )
 SELECT ad.account_id,
    ad.snapshot_date,
    ad.previous_equity,
    ad.equity,
    (ad.equity - ad.previous_equity) AS daily_equity_change,
    f.account_funding_flow,
    f.performance_flow,
    f.portfolio_flow,
    ad.daily_profit_amount AS reported_daily_profit,
    (((ad.equity - ad.previous_equity) - COALESCE(ad.deposits, (0)::numeric)) + COALESCE(ad.withdrawals, (0)::numeric)) AS derived_daily_profit,
    (ad.daily_profit_amount - (((ad.equity - ad.previous_equity) - COALESCE(ad.deposits, (0)::numeric)) + COALESCE(ad.withdrawals, (0)::numeric))) AS daily_profit_difference
   FROM (account_days ad
     LEFT JOIN flows f ON (((f.account_id = ad.account_id) AND (f.snapshot_date = ad.snapshot_date))));

CREATE MATERIALIZED VIEW investory.recon_v_account_monthly_profit AS
 WITH daily AS (
         SELECT account_daily.account_id,
            (date_trunc('month'::text, (account_daily.snapshot_date)::timestamp with time zone))::date AS month,
            sum(COALESCE(account_daily.daily_profit_amount, (0)::numeric)) AS summed_daily_profit
           FROM investory.account_daily
          GROUP BY account_daily.account_id, ((date_trunc('month'::text, (account_daily.snapshot_date)::timestamp with time zone))::date)
        ), flows AS (
         SELECT app_v_normalized_cash_operations.account_id,
            (date_trunc('month'::text, app_v_normalized_cash_operations.date))::date AS month,
            COALESCE(sum(app_v_normalized_cash_operations.amount_in_portfolio_base_currency) FILTER (WHERE ((app_v_normalized_cash_operations.is_external_flow OR app_v_normalized_cash_operations.is_internal_transfer) AND (app_v_normalized_cash_operations.amount_in_portfolio_base_currency > (0)::numeric))), (0)::numeric) AS inflows,
            COALESCE(sum((- app_v_normalized_cash_operations.amount_in_portfolio_base_currency)) FILTER (WHERE ((app_v_normalized_cash_operations.is_external_flow OR app_v_normalized_cash_operations.is_internal_transfer) AND (app_v_normalized_cash_operations.amount_in_portfolio_base_currency < (0)::numeric))), (0)::numeric) AS outflows
           FROM investory.app_v_normalized_cash_operations
          GROUP BY app_v_normalized_cash_operations.account_id, ((date_trunc('month'::text, app_v_normalized_cash_operations.date))::date)
        ), "values" AS (
         SELECT am.account_id,
            am.month,
            am.first_date,
            am.end_date,
            am.opening_equity,
            am.closing_equity,
            am.valuation_currency,
            am.deposits,
            am.withdrawals,
            am.dividends,
            am.interest,
            am.fees,
            am.taxes,
            am.realized_profit,
            am.total_profit,
            am.compounded_monthly_return,
            am.updated_at,
            COALESCE(d.summed_daily_profit, (0)::numeric) AS profit,
            COALESCE(f.inflows, (0)::numeric) AS inflows,
            COALESCE(f.outflows, (0)::numeric) AS outflows
           FROM ((investory.app_v_account_monthly am
             LEFT JOIN daily d ON (((d.account_id = am.account_id) AND (d.month = am.month))))
             LEFT JOIN flows f ON (((f.account_id = am.account_id) AND (f.month = am.month))))
        ), calc AS (
         SELECT v.account_id,
            v.month,
            v.first_date,
            v.end_date,
            v.opening_equity,
            v.closing_equity,
            v.valuation_currency,
            v.deposits,
            v.withdrawals,
            v.dividends,
            v.interest,
            v.fees,
            v.taxes,
            v.realized_profit,
            v.total_profit,
            v.compounded_monthly_return,
            v.updated_at,
            v.profit,
            v.inflows,
            v.outflows,
            (((v.closing_equity - v.opening_equity) - v.inflows) + v.outflows) AS boundary,
            ((((v.profit - v.closing_equity) + v.opening_equity) + v.inflows) - v.outflows) AS diff
           FROM "values" v
        )
 SELECT account_id,
    month,
    first_date,
    end_date,
    opening_equity,
    closing_equity,
    deposits,
    withdrawals,
    dividends,
    interest,
    fees,
    taxes,
    realized_profit,
    profit AS canonical_profit,
    investory.reconciliation_display_value(boundary) AS expected_boundary_profit,
    investory.reconciliation_display_value(diff) AS difference,
    investory.reconciliation_display_value(investory.reconciliation_effective_tolerance(profit, boundary)) AS monthly_effective_tolerance,
        CASE
            WHEN (NOT investory.reconciliation_values_match(profit, boundary)) THEN 'MISMATCH'::text
            ELSE 'OK'::text
        END AS reconciliation_status
   FROM calc;

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

CREATE VIEW investory.recon_v_account_temporal_anomaly AS
 WITH days AS (
         SELECT ad.id,
            ad.account_id,
            ad.snapshot_date,
            ad.valuation_currency,
            ad.cash_balance,
            ad.market_value,
            ad.equity,
            ad.cost_base,
            ad.unrealized_profit,
            ad.dividends,
            ad.interest,
            ad.fees,
            ad.taxes,
            ad.deposits,
            ad.withdrawals,
            ad.realized_profit,
            ad.daily_profit_amount,
            ad.daily_return_pct,
            ad.portfolio_weight,
            ad.created_at,
            ad.updated_at,
            lag(ad.snapshot_date) OVER w AS previous_date,
            lag(ad.equity) OVER w AS previous_equity,
            lag(ad.market_value) OVER w AS previous_market_value
           FROM investory.account_daily ad
          WINDOW w AS (PARTITION BY ad.account_id ORDER BY ad.snapshot_date)
        ), internal_bookkeeping AS (
         SELECT nco.account_id,
            ((nco.date AT TIME ZONE 'Europe/Warsaw'::text))::date AS event_date,
            sum(nco.amount_in_account_currency) FILTER (WHERE (nco.amount_in_account_currency > (0)::numeric)) AS internal_deposits,
            sum(abs(nco.amount_in_account_currency)) FILTER (WHERE (nco.amount_in_account_currency < (0)::numeric)) AS internal_withdrawals
           FROM investory.app_v_normalized_cash_operations nco
          WHERE ((nco.normalized_category)::text = 'INTERNAL_BOOKKEEPING'::text)
          GROUP BY nco.account_id, (((nco.date AT TIME ZONE 'Europe/Warsaw'::text))::date)
        ), movements AS (
         SELECT d.id,
            d.account_id,
            d.snapshot_date,
            d.valuation_currency,
            d.cash_balance,
            d.market_value,
            d.equity,
            d.cost_base,
            d.unrealized_profit,
            d.dividends,
            d.interest,
            d.fees,
            d.taxes,
            d.deposits,
            d.withdrawals,
            d.realized_profit,
            d.daily_profit_amount,
            d.daily_return_pct,
            d.portfolio_weight,
            d.created_at,
            d.updated_at,
            d.previous_date,
            d.previous_equity,
            d.previous_market_value,
            ((((((GREATEST((COALESCE(d.deposits, (0)::numeric) - COALESCE(ib.internal_deposits, (0)::numeric)), (0)::numeric) - GREATEST((COALESCE(d.withdrawals, (0)::numeric) - COALESCE(ib.internal_withdrawals, (0)::numeric)), (0)::numeric)) + COALESCE(d.dividends, (0)::numeric)) + COALESCE(d.interest, (0)::numeric)) - COALESCE(d.fees, (0)::numeric)) - COALESCE(d.taxes, (0)::numeric)) + COALESCE(d.realized_profit, (0)::numeric)) AS known_change,
            ((d.equity - d.previous_equity) - ((((((GREATEST((COALESCE(d.deposits, (0)::numeric) - COALESCE(ib.internal_deposits, (0)::numeric)), (0)::numeric) - GREATEST((COALESCE(d.withdrawals, (0)::numeric) - COALESCE(ib.internal_withdrawals, (0)::numeric)), (0)::numeric)) + COALESCE(d.dividends, (0)::numeric)) + COALESCE(d.interest, (0)::numeric)) - COALESCE(d.fees, (0)::numeric)) - COALESCE(d.taxes, (0)::numeric)) + COALESCE(d.realized_profit, (0)::numeric))) AS unexplained_equity_change,
            (d.market_value - d.previous_market_value) AS unexplained_market_change
           FROM (days d
             LEFT JOIN internal_bookkeeping ib ON (((ib.account_id = d.account_id) AND (ib.event_date = d.snapshot_date))))
        ), p AS (
         SELECT (investory.reconciliation_parameter('reconciliation_temporal_short_gap_days'::character varying))::integer AS short_gap,
            investory.reconciliation_parameter('reconciliation_account_unexplained_move_ratio'::character varying) AS move_ratio
        )
 SELECT 'WARN'::character varying(16) AS severity,
    (
        CASE
            WHEN ((abs(m.unexplained_market_change) / GREATEST(abs(m.previous_market_value), abs(m.market_value), (1)::numeric)) >= p.move_ratio) THEN 'ACCOUNT_MARKET_VALUE_SPIKE'::text
            ELSE 'ACCOUNT_EQUITY_SPIKE'::text
        END)::character varying(64) AS issue_code,
    'ACCOUNT'::character varying(16) AS entity_type,
    m.account_id AS entity_id,
    (a.name)::character varying(64) AS entity_key,
    m.snapshot_date AS event_date,
    m.previous_date,
    NULL::date AS next_date,
    (m.snapshot_date - m.previous_date) AS gap_days,
    m.previous_equity AS previous_value,
    m.equity AS current_value,
    NULL::numeric AS next_value,
    (m.unexplained_equity_change / GREATEST(abs(m.previous_equity), abs(m.equity), (1)::numeric)) AS change_pct,
    (m.unexplained_equity_change / NULLIF(m.previous_equity, (0)::numeric)) AS ratio,
    ((((((('unexplained equity change='::text || m.unexplained_equity_change) || ', unexplained market change='::text) || m.unexplained_market_change) || ', known change after internal transfers='::text) || m.known_change) || ', valuation currency='::text) || (m.valuation_currency)::text) AS explanation,
    NULL::character varying(32) AS source,
    NULL::character varying(64) AS source_symbol,
    m.valuation_currency AS observation_currency,
    NULL::character varying(3) AS mapping_currency,
    NULL::numeric AS scale_factor,
    NULL::numeric AS mapping_scale_factor,
    NULL::character varying(32) AS price_origin,
    NULL::character varying(64) AS quality_class,
    NULL::boolean AS is_proxy,
    NULL::boolean AS is_exact_listing,
    NULL::boolean AS is_alternate_listing
   FROM ((movements m
     JOIN investory.accounts a ON ((a.id = m.account_id)))
     CROSS JOIN p)
  WHERE ((m.previous_date IS NOT NULL) AND ((m.snapshot_date - m.previous_date) <= p.short_gap) AND (((abs(m.unexplained_market_change) / GREATEST(abs(m.previous_market_value), abs(m.market_value), (1)::numeric)) >= p.move_ratio) OR ((abs(m.unexplained_equity_change) / GREATEST(abs(m.previous_equity), abs(m.equity), (1)::numeric)) >= p.move_ratio)));

CREATE VIEW investory.recon_v_account_temporal_anomaly_fingerprinted AS
 SELECT t.severity,
    t.issue_code,
    t.entity_type,
    t.entity_id,
    t.entity_key,
    t.event_date,
    t.previous_date,
    t.next_date,
    t.gap_days,
    t.previous_value,
    t.current_value,
    t.next_value,
    t.change_pct,
    t.ratio,
    t.explanation,
    t.source,
    t.source_symbol,
    t.observation_currency,
    t.mapping_currency,
    t.scale_factor,
    t.mapping_scale_factor,
    t.price_origin,
    t.quality_class,
    t.is_proxy,
    md5((jsonb_build_array(t.issue_code, t.entity_id, t.event_date, t.previous_date, previous_snapshot.valuation_currency, previous_snapshot.cash_balance, previous_snapshot.market_value, previous_snapshot.equity, previous_snapshot.deposits, previous_snapshot.withdrawals, previous_snapshot.dividends, previous_snapshot.interest, previous_snapshot.fees, previous_snapshot.taxes, previous_snapshot.realized_profit, current_snapshot.valuation_currency, current_snapshot.cash_balance, current_snapshot.market_value, current_snapshot.equity, current_snapshot.deposits, current_snapshot.withdrawals, current_snapshot.dividends, current_snapshot.interest, current_snapshot.fees, current_snapshot.taxes, current_snapshot.realized_profit))::text) AS event_fingerprint
   FROM ((investory.recon_v_account_temporal_anomaly t
     JOIN investory.account_daily previous_snapshot ON (((previous_snapshot.account_id = t.entity_id) AND (previous_snapshot.snapshot_date = t.previous_date))))
     JOIN investory.account_daily current_snapshot ON (((current_snapshot.account_id = t.entity_id) AND (current_snapshot.snapshot_date = t.event_date))));

CREATE VIEW investory.recon_v_asset_identity_issues AS
 SELECT 'SOURCE_MAPPING_CURRENCY_MISMATCH'::character varying(64) AS issue_code,
    'ERROR'::character varying(16) AS severity,
    a.id AS asset_id,
    a.symbol AS asset_symbol,
    ass.source,
    ass.source_symbol,
    ((('mapping currency '::text || (ass.price_currency)::text) || ' differs from asset currency '::text) || (a.currency)::text) AS details
   FROM (investory.asset_source_symbols ass
     JOIN investory.assets a ON ((a.id = ass.asset_id)))
  WHERE ((a.exclude_from_import = false) AND ass.active AND ((ass.price_currency)::text IS DISTINCT FROM (a.currency)::text) AND (ass.requires_fx_conversion = false))
UNION ALL
 SELECT 'ALTERNATE_LISTING_NOT_MARKED'::character varying AS issue_code,
    'WARN'::character varying AS severity,
    a.id AS asset_id,
    a.symbol AS asset_symbol,
    ass.source,
    ass.source_symbol,
    ((('mapping market '::text || (COALESCE(ass.matched_exchange, ass.source_market))::text) || ' differs from asset country '::text) || (a.country)::text) AS details
   FROM (investory.asset_source_symbols ass
     JOIN investory.assets a ON ((a.id = ass.asset_id)))
  WHERE ((a.exclude_from_import = false) AND ass.active AND (COALESCE(ass.matched_exchange, ass.source_market) IS NOT NULL) AND (upper((COALESCE(ass.matched_exchange, ass.source_market))::text) <> upper((a.country)::text)) AND (ass.is_alternate_listing = false))
UNION ALL
 SELECT 'TICKER_COLLISION_WITHOUT_STABLE_IDENTIFIER'::character varying AS issue_code,
    'WARN'::character varying AS severity,
    a.id AS asset_id,
    a.symbol AS asset_symbol,
    NULL::character varying(32) AS source,
    NULL::character varying(64) AS source_symbol,
    (('ticker '::text || (a.ticker)::text) || ' is shared by multiple included assets without ISIN/FIGI'::text) AS details
   FROM investory.assets a
  WHERE ((a.exclude_from_import = false) AND (a.isin IS NULL) AND (a.figi IS NULL) AND (EXISTS ( SELECT 1
           FROM investory.assets other
          WHERE ((other.exclude_from_import = false) AND (other.id <> a.id) AND (upper((other.ticker)::text) = upper((a.ticker)::text)) AND ((other.currency)::text IS DISTINCT FROM (a.currency)::text)))));

CREATE VIEW investory.recon_v_asset_price_quality_issues AS
 WITH included_history AS MATERIALIZED (
         SELECT aph.asset_id,
            a.symbol AS asset_symbol,
            aph.price_date,
            aph.source,
            aph.source_symbol,
            aph.price_currency,
            (aph.close_price * aph.price_scale_factor) AS normalized_price,
            aph.estimated,
            aph.interpolation_left_date,
            aph.interpolation_right_date,
            ass.requires_fx_conversion,
            a.currency AS asset_currency
           FROM ((investory.asset_price_history aph
             JOIN investory.assets a ON ((a.id = aph.asset_id)))
             LEFT JOIN investory.asset_source_symbols ass ON ((ass.id = aph.source_mapping_id)))
          WHERE (a.exclude_from_import = false)
        ), source_disagreement AS (
         SELECT included_history.asset_id,
            included_history.asset_symbol,
            included_history.price_date,
            min(included_history.normalized_price) AS minimum_price,
            max(included_history.normalized_price) AS maximum_price,
            count(*) AS observation_count
           FROM included_history
          GROUP BY included_history.asset_id, included_history.asset_symbol, included_history.price_date
         HAVING ((count(*) > 1) AND (min(included_history.normalized_price) > (0)::numeric) AND ((max(included_history.normalized_price) / min(included_history.normalized_price)) >= investory.reconciliation_parameter('reconciliation_source_disagreement_ratio'::character varying)))
        ), canonical_prices AS (
         SELECT cp.asset_id,
            cp.price_date,
            cp.source,
            cp.source_symbol,
            (cp.close_price * cp.price_scale_factor) AS normalized_price,
            lag((cp.close_price * cp.price_scale_factor)) OVER (PARTITION BY cp.asset_id ORDER BY cp.price_date) AS previous_normalized_price
           FROM investory.app_v_canonical_asset_daily_price_mv cp
        ), interpolation_checks AS (
         SELECT current.asset_id,
            current.asset_symbol,
            current.price_date,
            current.source,
            current.source_symbol,
            current.normalized_price,
            left_price.normalized_price AS left_normalized_price,
            right_price.normalized_price AS right_normalized_price,
            left_price.price_date AS left_date,
            right_price.price_date AS right_date
           FROM ((included_history current
             LEFT JOIN LATERAL ( SELECT cp.price_date,
                    (cp.close_price * cp.price_scale_factor) AS normalized_price
                   FROM investory.app_v_canonical_asset_daily_price_mv cp
                  WHERE ((cp.asset_id = current.asset_id) AND (cp.price_date = current.interpolation_left_date))) left_price ON (true))
             LEFT JOIN LATERAL ( SELECT cp.price_date,
                    (cp.close_price * cp.price_scale_factor) AS normalized_price
                   FROM investory.app_v_canonical_asset_daily_price_mv cp
                  WHERE ((cp.asset_id = current.asset_id) AND (cp.price_date = current.interpolation_right_date))) right_price ON (true))
          WHERE current.estimated
        ), interpolation_deviation AS (
         SELECT interpolation_checks.asset_id,
            interpolation_checks.asset_symbol,
            interpolation_checks.price_date,
            interpolation_checks.source,
            interpolation_checks.source_symbol,
            interpolation_checks.normalized_price,
            interpolation_checks.left_normalized_price,
            interpolation_checks.right_normalized_price,
            interpolation_checks.left_date,
            interpolation_checks.right_date,
            (interpolation_checks.left_normalized_price + (((interpolation_checks.right_normalized_price - interpolation_checks.left_normalized_price) * ((interpolation_checks.price_date - interpolation_checks.left_date))::numeric) / (NULLIF((interpolation_checks.right_date - interpolation_checks.left_date), 0))::numeric)) AS expected_normalized_price
           FROM interpolation_checks
          WHERE ((interpolation_checks.left_normalized_price > (0)::numeric) AND (interpolation_checks.right_normalized_price > (0)::numeric))
        ), jump_limits AS MATERIALIZED (
         SELECT investory.reconciliation_parameter('reconciliation_price_jump_upper_ratio'::character varying) AS upper_ratio,
            investory.reconciliation_parameter('reconciliation_price_jump_lower_ratio'::character varying) AS lower_ratio
        )
 SELECT 'PRICE_CURRENCY_MISMATCH'::character varying(64) AS issue_code,
    'ERROR'::character varying(16) AS severity,
    included_history.asset_id,
    included_history.asset_symbol,
    included_history.price_date,
    included_history.source,
    included_history.source_symbol,
    ((('price currency '::text || (included_history.price_currency)::text) || ' differs from asset currency '::text) || (included_history.asset_currency)::text) AS text
   FROM included_history
  WHERE (((included_history.price_currency)::text IS DISTINCT FROM (included_history.asset_currency)::text) AND (COALESCE(included_history.requires_fx_conversion, false) = false))
UNION ALL
 SELECT 'EXTREME_SAME_DATE_SOURCE_DISAGREEMENT'::character varying AS issue_code,
    'WARN'::character varying AS severity,
    source_disagreement.asset_id,
    source_disagreement.asset_symbol,
    source_disagreement.price_date,
    NULL::character varying AS source,
    NULL::character varying AS source_symbol,
    (((((('source prices range from '::text || source_disagreement.minimum_price) || ' to '::text) || source_disagreement.maximum_price) || ' across '::text) || source_disagreement.observation_count) || ' rows'::text) AS text
   FROM source_disagreement
UNION ALL
 SELECT 'SUSPICIOUS_INTERPOLATION'::character varying AS issue_code,
    'WARN'::character varying AS severity,
    interpolation_deviation.asset_id,
    interpolation_deviation.asset_symbol,
    interpolation_deviation.price_date,
    interpolation_deviation.source,
    interpolation_deviation.source_symbol,
    ((('interpolated price '::text || interpolation_deviation.normalized_price) || ' differs from expected '::text) || interpolation_deviation.expected_normalized_price) AS text
   FROM interpolation_deviation
  WHERE ((abs((interpolation_deviation.normalized_price - interpolation_deviation.expected_normalized_price)) / NULLIF(interpolation_deviation.expected_normalized_price, (0)::numeric)) >= investory.reconciliation_parameter('reconciliation_interpolation_deviation_ratio'::character varying))
UNION ALL
 SELECT 'EXTREME_DAILY_MOVE'::character varying AS issue_code,
    'WARN'::character varying AS severity,
    cp.asset_id,
    a.symbol AS asset_symbol,
    cp.price_date,
    cp.source,
    cp.source_symbol,
    ((('canonical price moved from '::text || cp.previous_normalized_price) || ' to '::text) || cp.normalized_price) AS text
   FROM ((canonical_prices cp
     JOIN investory.assets a ON ((a.id = cp.asset_id)))
     CROSS JOIN jump_limits limits)
  WHERE ((cp.previous_normalized_price > (0)::numeric) AND (((cp.normalized_price / cp.previous_normalized_price) >= limits.upper_ratio) OR ((cp.normalized_price / cp.previous_normalized_price) <= limits.lower_ratio)))
UNION ALL
 SELECT 'PRICE_SCALE_MAPPING_MISMATCH'::character varying AS issue_code,
    'ERROR'::character varying AS severity,
    h.asset_id,
    h.asset_symbol,
    h.price_date,
    h.source,
    h.source_symbol,
    'source price scale or currency does not match its explicit mapping'::text AS text
   FROM ((included_history h
     JOIN investory.asset_price_history aph ON (((aph.asset_id = h.asset_id) AND (aph.price_date = h.price_date) AND ((aph.source)::text = (h.source)::text) AND ((aph.source_symbol)::text = (h.source_symbol)::text))))
     JOIN investory.asset_source_symbols ass ON ((ass.id = aph.source_mapping_id)))
  WHERE ((aph.price_scale_factor IS DISTINCT FROM ass.price_scale_factor) OR ((ass.requires_fx_conversion = false) AND ((aph.price_currency)::text IS DISTINCT FROM (ass.price_currency)::text)));

CREATE VIEW investory.recon_v_fx AS
 WITH pairs AS (
         SELECT c1.id AS source_currency,
            c2.id AS target_currency
           FROM (investory.currencies c1
             CROSS JOIN investory.currencies c2)
          WHERE ((c1.id)::text <> (c2.id)::text)
        ), latest AS (
         SELECT DISTINCT ON (fx.base, fx.to_currency) fx.base AS source_currency,
            fx.to_currency AS target_currency,
            fx.rate_date,
            fx.source,
            fx.method,
            fx.rate
           FROM investory.exchange_rates fx
          WHERE ((fx.purpose)::text = 'VALUATION'::text)
          ORDER BY fx.base, fx.to_currency, fx.rate_date DESC, fx.source_reference DESC NULLS LAST
        ), resolved AS (
         SELECT p.source_currency,
            p.target_currency,
            r_1.fx_rate_to_target,
            r_1.source_rate_date,
            r_1.rate_source,
            r_1.rate_method,
            r_1.age_days,
            r_1.conversion_status
           FROM (pairs p
             CROSS JOIN LATERAL investory.resolve_fx_rate(CURRENT_DATE, p.source_currency, p.target_currency) r_1(source_currency, target_currency, fx_rate_to_target, source, rate_method, rate_source, source_rate_date, age_days, conversion_status))
        )
 SELECT r.source_currency,
    r.target_currency,
    r.fx_rate_to_target,
    r.source_rate_date,
    r.rate_source,
    r.rate_method,
    r.age_days,
    r.conversion_status,
    l.rate AS latest_stored_rate,
    l.rate_date AS latest_stored_date,
    l.source AS latest_stored_source,
    l.method AS latest_stored_method,
    (0)::bigint AS interpolated_observation_count,
    ( SELECT count(*) AS count
           FROM investory.exchange_rates e
          WHERE ((e.method)::text = 'XTB_EXECUTION'::text)) AS xtb_execution_observation_count,
    ( SELECT count(*) AS count
           FROM investory.exchange_rates e
          WHERE ((e.method)::text = 'IBKR_EXECUTION'::text)) AS ibkr_execution_observation_count
   FROM (resolved r
     LEFT JOIN latest l ON ((((l.source_currency)::text = (r.source_currency)::text) AND ((l.target_currency)::text = (r.target_currency)::text))));

CREATE VIEW investory.recon_v_fx_consistency AS
 SELECT 'RECIPROCAL_MISMATCH'::character varying AS issue_code,
    fx.base,
    fx.to_currency,
    fx.rate_date,
    abs(((fx.rate * inverse_fx.rate) - (1)::numeric)) AS deviation,
    'Direct and reciprocal observations differ'::text AS details
   FROM (investory.exchange_rates fx
     JOIN investory.exchange_rates inverse_fx ON (((inverse_fx.rate_date = fx.rate_date) AND ((inverse_fx.base)::text = (fx.to_currency)::text) AND ((inverse_fx.to_currency)::text = (fx.base)::text))))
  WHERE (((fx.purpose)::text = 'VALUATION'::text) AND ((inverse_fx.purpose)::text = 'VALUATION'::text) AND (abs(((fx.rate * inverse_fx.rate) - (1)::numeric)) > 0.0001));

CREATE VIEW investory.recon_v_fx_data_quality AS
 WITH jumps AS (
         SELECT exchange_rates.base,
            exchange_rates.to_currency,
            exchange_rates.rate_date,
            exchange_rates.rate,
            lag(exchange_rates.rate) OVER (PARTITION BY exchange_rates.base, exchange_rates.to_currency ORDER BY exchange_rates.rate_date) AS previous_rate
           FROM investory.exchange_rates
          WHERE ((exchange_rates.purpose)::text = 'VALUATION'::text)
        )
 SELECT 'FX_SPIKE'::character varying AS issue_code,
    jumps.base,
    jumps.to_currency,
    jumps.rate_date,
    jumps.rate,
    'Daily neutral move exceeds 5%'::text AS details
   FROM jumps
  WHERE ((jumps.previous_rate IS NOT NULL) AND (abs(((jumps.rate / jumps.previous_rate) - (1)::numeric)) > 0.05))
UNION ALL
 SELECT 'DAILY_COVERAGE_GAP'::character varying AS issue_code,
    (pairs.base)::character varying(3) AS base,
    (pairs.to_currency)::character varying(3) AS to_currency,
    CURRENT_DATE AS rate_date,
    NULL::numeric AS rate,
    'No canonical neutral FX observation available on or before today'::text AS details
   FROM ( VALUES ('USD'::character varying(3),'EUR'::character varying(3)), ('USD'::character varying,'PLN'::character varying), ('EUR'::character varying,'USD'::character varying), ('EUR'::character varying,'PLN'::character varying), ('PLN'::character varying,'USD'::character varying), ('PLN'::character varying,'EUR'::character varying)) pairs(base, to_currency)
  WHERE ((CURRENT_DATE >= ( SELECT (fx_configuration.config_value)::date AS config_value
           FROM investory.fx_configuration
          WHERE ((fx_configuration.config_key)::text = 'daily_history_start'::text))) AND (NOT (EXISTS ( SELECT 1
           FROM investory.exchange_rates fx
          WHERE ((fx.rate_date <= CURRENT_DATE) AND ((fx.purpose)::text = 'VALUATION'::text) AND ((fx.base)::text = (pairs.base)::text) AND ((fx.to_currency)::text = (pairs.to_currency)::text))))));

CREATE VIEW investory.recon_v_fx_temporal_anomaly AS
 WITH observations AS (
         SELECT fx.base,
            fx.to_currency,
            fx.rate_date,
            fx.rate,
            fx.source,
            fx.method,
            lag(fx.rate) OVER w AS previous_rate,
            lag(fx.rate_date) OVER w AS previous_date,
            lead(fx.rate) OVER w AS next_rate,
            lead(fx.rate_date) OVER w AS next_date
           FROM investory.exchange_rates fx
          WHERE ((fx.purpose)::text = 'VALUATION'::text)
          WINDOW w AS (PARTITION BY fx.base, fx.to_currency ORDER BY fx.rate_date)
        ), params AS (
         SELECT (investory.reconciliation_parameter('reconciliation_temporal_short_gap_days'::character varying))::integer AS short_gap,
            investory.reconciliation_parameter('reconciliation_fx_extreme_move_ratio'::character varying) AS extreme_move,
            investory.reconciliation_parameter('reconciliation_fx_isolated_spike_ratio'::character varying) AS spike_ratio,
            investory.reconciliation_parameter('reconciliation_temporal_neighbor_recovery_ratio'::character varying) AS recovery,
            investory.reconciliation_parameter('reconciliation_fx_reciprocal_tolerance'::character varying) AS reciprocal_tolerance
        )
 SELECT (
        CASE
            WHEN ((abs(((o.rate / NULLIF(o.previous_rate, (0)::numeric)) - (1)::numeric)) >= p.spike_ratio) AND (abs(((o.next_rate / NULLIF(o.previous_rate, (0)::numeric)) - (1)::numeric)) <= p.recovery)) THEN 'ERROR'::text
            ELSE 'WARN'::text
        END)::character varying(16) AS severity,
    (
        CASE
            WHEN ((abs(((o.rate / NULLIF(o.previous_rate, (0)::numeric)) - (1)::numeric)) >= p.spike_ratio) AND (abs(((o.next_rate / NULLIF(o.previous_rate, (0)::numeric)) - (1)::numeric)) <= p.recovery)) THEN 'FX_ISOLATED_SPIKE'::text
            ELSE 'FX_EXTREME_MOVE'::text
        END)::character varying(64) AS issue_code,
    'FX'::character varying(16) AS entity_type,
    NULL::bigint AS entity_id,
    ((((o.base)::text || '/'::text) || (o.to_currency)::text))::character varying(64) AS entity_key,
    o.base AS base_currency,
    o.to_currency AS quote_currency,
    o.rate_date AS event_date,
    o.previous_date,
    o.next_date,
    (o.rate_date - o.previous_date) AS gap_days,
    o.previous_rate AS previous_value,
    o.rate AS current_value,
    o.next_rate AS next_value,
    ((o.rate / NULLIF(o.previous_rate, (0)::numeric)) - (1)::numeric) AS change_pct,
    (o.rate / NULLIF(o.previous_rate, (0)::numeric)) AS ratio,
    ((((((((((((('canonical neutral FX '::text || (o.base)::text) || '/'::text) || (o.to_currency)::text) || ' moved from '::text) || o.previous_rate) || ' to '::text) || o.rate) || ' over '::text) || (o.rate_date - o.previous_date)) || ' day(s); source='::text) || (o.source)::text) || ', method='::text) || (o.method)::text) AS explanation,
    o.source,
    o.method AS source_symbol,
    NULL::character varying(3) AS observation_currency,
    NULL::numeric AS scale_factor,
    NULL::boolean AS is_proxy
   FROM (observations o
     CROSS JOIN params p)
  WHERE ((o.previous_rate > (0)::numeric) AND ((o.rate_date - o.previous_date) <= p.short_gap) AND (abs(((o.rate / o.previous_rate) - (1)::numeric)) >= p.extreme_move) AND (NOT ((abs(((o.rate / NULLIF(o.previous_rate, (0)::numeric)) - (1)::numeric)) >= p.spike_ratio) AND (o.next_rate > (0)::numeric) AND ((o.next_date - o.rate_date) <= p.short_gap) AND (abs(((o.next_rate / NULLIF(o.previous_rate, (0)::numeric)) - (1)::numeric)) <= p.recovery))))
UNION ALL
 SELECT 'ERROR'::character varying AS severity,
    'FX_ISOLATED_SPIKE'::character varying AS issue_code,
    'FX'::character varying AS entity_type,
    NULL::bigint AS entity_id,
    (((o.base)::text || '/'::text) || (o.to_currency)::text) AS entity_key,
    o.base AS base_currency,
    o.to_currency AS quote_currency,
    o.rate_date AS event_date,
    o.previous_date,
    o.next_date,
    (o.rate_date - o.previous_date) AS gap_days,
    o.previous_rate AS previous_value,
    o.rate AS current_value,
    o.next_rate AS next_value,
    ((o.rate / NULLIF(o.previous_rate, (0)::numeric)) - (1)::numeric) AS change_pct,
    (o.rate / NULLIF(o.previous_rate, (0)::numeric)) AS ratio,
    ((((('isolated canonical FX spike: neighbors recover from '::text || o.previous_rate) || ' to '::text) || o.next_rate) || ', current='::text) || o.rate) AS explanation,
    o.source,
    o.method AS source_symbol,
    NULL::character varying AS observation_currency,
    NULL::numeric AS scale_factor,
    NULL::boolean AS is_proxy
   FROM (observations o
     CROSS JOIN params p)
  WHERE ((o.previous_rate > (0)::numeric) AND (o.next_rate > (0)::numeric) AND ((o.rate_date - o.previous_date) <= p.short_gap) AND ((o.next_date - o.rate_date) <= p.short_gap) AND (abs(((o.rate / o.previous_rate) - (1)::numeric)) >= p.spike_ratio) AND (abs(((o.next_rate / o.previous_rate) - (1)::numeric)) <= p.recovery))
UNION ALL
 SELECT 'ERROR'::character varying AS severity,
    'FX_RECIPROCAL_INCONSISTENCY'::character varying AS issue_code,
    'FX'::character varying AS entity_type,
    NULL::bigint AS entity_id,
    (((fx.base)::text || '/'::text) || (fx.to_currency)::text) AS entity_key,
    fx.base AS base_currency,
    fx.to_currency AS quote_currency,
    fx.rate_date AS event_date,
    NULL::date AS previous_date,
    NULL::date AS next_date,
    NULL::integer AS gap_days,
    NULL::numeric AS previous_value,
    fx.rate AS current_value,
    inverse.rate AS next_value,
    abs(((fx.rate * inverse.rate) - (1)::numeric)) AS change_pct,
    (fx.rate * inverse.rate) AS ratio,
    ((('reciprocal product='::text || (fx.rate * inverse.rate)) || ', tolerance='::text) || p.reciprocal_tolerance) AS explanation,
    fx.source,
    fx.method AS source_symbol,
    NULL::character varying AS observation_currency,
    NULL::numeric AS scale_factor,
    NULL::boolean AS is_proxy
   FROM ((investory.exchange_rates fx
     JOIN investory.exchange_rates inverse ON (((inverse.rate_date = fx.rate_date) AND ((inverse.base)::text = (fx.to_currency)::text) AND ((inverse.to_currency)::text = (fx.base)::text))))
     CROSS JOIN params p)
  WHERE (((fx.purpose)::text = 'VALUATION'::text) AND ((inverse.purpose)::text = 'VALUATION'::text) AND (abs(((fx.rate * inverse.rate) - (1)::numeric)) > p.reciprocal_tolerance));

CREATE VIEW investory.recon_v_non_usd_closed_trade AS
 WITH closed_positions AS MATERIALIZED (
         SELECT p.id AS position_id,
            p.account_id,
            account.portfolio_id,
            p.asset_id,
            a.id AS asset_numeric_id,
            p.profit_currency AS trade_currency,
            p.commission_currency,
            portfolio.base_currency AS portfolio_base_currency,
            (p.open_time)::date AS open_date,
            (p.close_time)::date AS close_date,
            COALESCE(p.volume, (0)::numeric) AS close_quantity,
            COALESCE(p.purchase_value, (COALESCE(p.volume, (0)::numeric) * COALESCE(p.open_price, (0)::numeric)), (0)::numeric) AS purchase_value_local,
            COALESCE(p.sale_value, (COALESCE(p.volume, (0)::numeric) * COALESCE(p.close_price, (0)::numeric)), (0)::numeric) AS sale_value_local,
            COALESCE(p.profit, (0)::numeric) AS realized_profit_local,
            COALESCE(p.commission, (0)::numeric) AS commission_local,
            COALESCE(p.swap, (0)::numeric) AS swap_local,
            COALESCE(p.close_price, (0)::numeric) AS close_price_local
           FROM (((investory.positions p
             JOIN investory.assets a ON ((a.id = p.asset_id)))
             JOIN investory.accounts account ON ((account.id = p.account_id)))
             JOIN investory.portfolios portfolio ON ((portfolio.id = account.portfolio_id)))
          WHERE ((p.close_time IS NOT NULL) AND (((p.profit_currency)::text <> (portfolio.base_currency)::text) OR ((p.commission_currency)::text <> (portfolio.base_currency)::text)))
        ), previous_trade_day AS (
         SELECT cp_1.position_id,
            previous_day.previous_valuation_date
           FROM (closed_positions cp_1
             LEFT JOIN LATERAL ( SELECT ad.snapshot_date AS previous_valuation_date
                   FROM investory.account_daily ad
                  WHERE ((ad.account_id = cp_1.account_id) AND (ad.snapshot_date < cp_1.close_date))
                  ORDER BY ad.snapshot_date DESC
                 LIMIT 1) previous_day ON (true))
        ), close_day_account AS (
         SELECT cp_1.position_id,
            ad.snapshot_date AS valuation_date,
            ad.cash_balance,
            ad.market_value,
            ad.equity,
            ad.daily_profit_amount,
            lag(ad.cash_balance) OVER (PARTITION BY ad.account_id ORDER BY ad.snapshot_date) AS previous_cash_balance,
            lag(ad.market_value) OVER (PARTITION BY ad.account_id ORDER BY ad.snapshot_date) AS previous_market_value,
            lag(ad.equity) OVER (PARTITION BY ad.account_id ORDER BY ad.snapshot_date) AS previous_equity
           FROM (closed_positions cp_1
             JOIN investory.account_daily ad ON (((ad.account_id = cp_1.account_id) AND (ad.snapshot_date = cp_1.close_date))))
        ), previous_symbol_value AS (
         SELECT cp_1.position_id,
            ptd.previous_valuation_date,
            rpd.open_quantity AS previous_open_quantity,
            rpd.selected_price AS previous_selected_price_local,
            rpd.price_currency AS previous_price_currency,
            rpd.reconstructed_market_value_base AS previous_symbol_market_value_base,
            rpd.reconstructed_cost_base_base AS previous_symbol_cost_base_base
           FROM ((closed_positions cp_1
             LEFT JOIN previous_trade_day ptd ON ((ptd.position_id = cp_1.position_id)))
             LEFT JOIN investory.app_v_reconstructed_position_daily rpd ON (((rpd.account_id = cp_1.account_id) AND (rpd.asset_id = cp_1.asset_numeric_id) AND (rpd.valuation_date = ptd.previous_valuation_date))))
        ), fx_keys AS MATERIALIZED (
         SELECT closed_positions.close_date,
            closed_positions.trade_currency AS source_currency,
            closed_positions.portfolio_base_currency
           FROM closed_positions
        UNION
         SELECT closed_positions.close_date,
            closed_positions.commission_currency AS source_currency,
            closed_positions.portfolio_base_currency
           FROM closed_positions
        ), fx_rates AS MATERIALIZED (
         SELECT k.close_date,
            k.source_currency,
            k.portfolio_base_currency,
            fx_1.fx_rate_to_target,
            fx_1.conversion_status
           FROM (fx_keys k
             LEFT JOIN LATERAL investory.resolve_fx_rate(k.close_date, k.source_currency, k.portfolio_base_currency) fx_1(source_currency, target_currency, fx_rate_to_target, source, rate_method, rate_source, source_rate_date, age_days, conversion_status) ON (true))
        ), fx_at_close AS (
         SELECT cp_1.position_id,
            profit_fx.fx_rate_to_target AS close_fx_rate_to_base,
            profit_fx.conversion_status AS profit_conversion_status,
            commission_fx.fx_rate_to_target AS commission_fx_rate_to_base,
            commission_fx.conversion_status AS commission_conversion_status
           FROM ((closed_positions cp_1
             LEFT JOIN fx_rates profit_fx ON (((profit_fx.close_date = cp_1.close_date) AND ((profit_fx.source_currency)::text = (cp_1.trade_currency)::text) AND ((profit_fx.portfolio_base_currency)::text = (cp_1.portfolio_base_currency)::text))))
             LEFT JOIN fx_rates commission_fx ON (((commission_fx.close_date = cp_1.close_date) AND ((commission_fx.source_currency)::text = (cp_1.commission_currency)::text) AND ((commission_fx.portfolio_base_currency)::text = (cp_1.portfolio_base_currency)::text))))
        ), day_cash_other_flows AS (
         SELECT cp_1.position_id,
            sum(
                CASE
                    WHEN ((nco.normalized_category)::text = ANY (ARRAY[('TRADE_PURCHASE'::character varying)::text, ('TRADE_SALE'::character varying)::text, ('REALIZED_TRADE_RESULT'::character varying)::text])) THEN (0)::numeric
                    ELSE nco.amount_in_portfolio_base_currency
                END) AS non_trade_cash_flow_base
           FROM (closed_positions cp_1
             LEFT JOIN investory.app_v_normalized_cash_operations nco ON (((nco.account_id = cp_1.account_id) AND ((nco.date)::date = cp_1.close_date))))
          GROUP BY cp_1.position_id
        )
 SELECT cp.position_id,
    cp.account_id,
    cp.asset_id,
    cp.trade_currency,
    cp.commission_currency,
    cp.open_date,
    cp.close_date AS valuation_date,
    psv.previous_valuation_date,
    cp.close_quantity,
    cp.purchase_value_local,
    cp.sale_value_local,
    cp.realized_profit_local,
    cp.commission_local,
    cp.swap_local,
    cp.close_price_local,
    psv.previous_open_quantity,
    psv.previous_selected_price_local,
    psv.previous_price_currency,
    psv.previous_symbol_market_value_base,
    psv.previous_symbol_cost_base_base,
    fx.close_fx_rate_to_base,
    fx.commission_fx_rate_to_base,
        CASE
            WHEN (fx.close_fx_rate_to_base IS NULL) THEN NULL::numeric
            ELSE (cp.sale_value_local * fx.close_fx_rate_to_base)
        END AS sale_value_base_at_close_fx,
        CASE
            WHEN ((NOT investory.fx_status_usable(fx.profit_conversion_status)) OR (NOT investory.fx_status_usable(fx.commission_conversion_status))) THEN NULL::numeric
            ELSE (((cp.realized_profit_local + cp.swap_local) * fx.close_fx_rate_to_base) + (cp.commission_local * fx.commission_fx_rate_to_base))
        END AS net_realized_result_base_at_close_fx,
        CASE
            WHEN (COALESCE(psv.previous_open_quantity, (0)::numeric) = (0)::numeric) THEN NULL::numeric
            ELSE ((COALESCE(psv.previous_symbol_market_value_base, (0)::numeric) * cp.close_quantity) / psv.previous_open_quantity)
        END AS allocated_previous_market_value_base,
    (COALESCE(cda.cash_balance, (0)::numeric) - COALESCE(cda.previous_cash_balance, (0)::numeric)) AS account_cash_delta_base,
    (COALESCE(cda.market_value, (0)::numeric) - COALESCE(cda.previous_market_value, (0)::numeric)) AS account_market_delta_base,
    (COALESCE(cda.equity, (0)::numeric) - COALESCE(cda.previous_equity, (0)::numeric)) AS account_equity_delta_base,
    COALESCE(cda.daily_profit_amount, (0)::numeric) AS reported_daily_profit_base,
    COALESCE(dcof.non_trade_cash_flow_base, (0)::numeric) AS non_trade_cash_flow_base,
    adr.status AS account_reconciliation_status,
    adr.validation_message AS account_reconciliation_message,
    (
        CASE
            WHEN ((adr.status)::text = 'FAIL'::text) THEN 'ACCOUNT_RECON_FAIL'::text
            WHEN ((fx.close_fx_rate_to_base IS NULL) OR (fx.commission_fx_rate_to_base IS NULL)) THEN 'MISSING_CLOSE_FX'::text
            WHEN (psv.previous_valuation_date IS NULL) THEN 'MISSING_PREVIOUS_DAY'::text
            WHEN (COALESCE(psv.previous_open_quantity, (0)::numeric) = (0)::numeric) THEN 'MISSING_PREVIOUS_POSITION'::text
            WHEN (psv.previous_selected_price_local IS NULL) THEN 'MISSING_PREVIOUS_PRICE'::text
            WHEN ((abs((cp.sale_value_local - (cp.close_quantity * COALESCE(psv.previous_selected_price_local, (0)::numeric)))) <= (investory.reconciliation_parameter('reconciliation_local_sale_price_relative_threshold'::character varying) * GREATEST((1)::numeric, abs(cp.sale_value_local)))) AND (abs((COALESCE((cp.sale_value_local * fx.close_fx_rate_to_base), (0)::numeric) - COALESCE(((psv.previous_symbol_market_value_base * cp.close_quantity) / NULLIF(psv.previous_open_quantity, (0)::numeric)), (0)::numeric))) > (investory.reconciliation_parameter('reconciliation_local_flat_base_jump_relative_threshold'::character varying) * GREATEST((1)::numeric, abs(COALESCE(((psv.previous_symbol_market_value_base * cp.close_quantity) / NULLIF(psv.previous_open_quantity, (0)::numeric)), (0)::numeric)))))) THEN 'LOCAL_FLAT_BASE_JUMP'::text
            ELSE 'OK'::text
        END)::character varying(64) AS anomaly_code
   FROM (((((closed_positions cp
     LEFT JOIN previous_symbol_value psv ON ((psv.position_id = cp.position_id)))
     LEFT JOIN fx_at_close fx ON ((fx.position_id = cp.position_id)))
     LEFT JOIN close_day_account cda ON ((cda.position_id = cp.position_id)))
     LEFT JOIN day_cash_other_flows dcof ON ((dcof.position_id = cp.position_id)))
     LEFT JOIN investory.recon_v_account_daily adr ON (((adr.account_id = cp.account_id) AND (adr.valuation_date = cp.close_date))));

CREATE VIEW investory.recon_v_position_valuation_validation AS
 WITH neighboring AS (
         SELECT rpd.account_id,
            rpd.asset_id,
            rpd.valuation_date,
            rpd.open_quantity,
            rpd.reconstructed_cost_base_local,
            rpd.reconstructed_cost_base_base,
            rpd.acquisition_currency,
            rpd.selected_price,
            rpd.selected_price_date,
            rpd.underlying_observation_date,
            rpd.price_age_days,
            rpd.price_currency,
            rpd.contract_multiplier,
            rpd.selection_priority,
            rpd.base_currency,
            rpd.fx_rate_to_base,
            rpd.fx_conversion_status,
            rpd.reconstructed_market_value_base,
            rpd.reconstructed_unrealized_profit_base,
            rpd.price_origin,
            rpd.price_quality,
            rpd.reconstruction_status,
            rpd.reconstruction_message,
            lag(rpd.selected_price) OVER (PARTITION BY rpd.asset_id ORDER BY rpd.valuation_date) AS prev_selected_price,
            lag(rpd.valuation_date) OVER (PARTITION BY rpd.asset_id ORDER BY rpd.valuation_date) AS prev_valuation_date,
            lag(rpd.open_quantity) OVER (PARTITION BY rpd.account_id, rpd.asset_id ORDER BY rpd.valuation_date) AS prev_open_quantity
           FROM investory.app_v_reconstructed_position_daily rpd
        ), classified AS (
         SELECT n_1.account_id,
            n_1.asset_id,
            n_1.valuation_date,
            n_1.open_quantity,
            n_1.reconstructed_cost_base_local,
            n_1.reconstructed_cost_base_base,
            n_1.acquisition_currency,
            n_1.selected_price,
            n_1.selected_price_date,
            n_1.underlying_observation_date,
            n_1.price_age_days,
            n_1.price_currency,
            n_1.contract_multiplier,
            n_1.selection_priority,
            n_1.base_currency,
            n_1.fx_rate_to_base,
            n_1.fx_conversion_status,
            n_1.reconstructed_market_value_base,
            n_1.reconstructed_unrealized_profit_base,
            n_1.price_origin,
            n_1.price_quality,
            n_1.reconstruction_status,
            n_1.reconstruction_message,
            n_1.prev_selected_price,
            n_1.prev_valuation_date,
            n_1.prev_open_quantity,
            ((n_1.prev_valuation_date = (n_1.valuation_date - 1)) AND (n_1.prev_open_quantity > (0)::numeric) AND ((n_1.open_quantity / n_1.prev_open_quantity) >= 9.5) AND ((n_1.open_quantity / n_1.prev_open_quantity) <= 10.5) AND (n_1.prev_selected_price > (0)::numeric) AND ((n_1.selected_price / n_1.prev_selected_price) >= 0.05) AND ((n_1.selected_price / n_1.prev_selected_price) <= 0.20)) AS corporate_action_price_reset
           FROM neighboring n_1
        )
 SELECT n.account_id,
    n.asset_id,
    n.valuation_date,
    (
        CASE
            WHEN ((n.reconstruction_status)::text = 'FAIL'::text) THEN 'ERROR'::text
            WHEN (n.selection_priority = 6) THEN 'INFO'::text
            WHEN (n.selection_priority = 5) THEN 'INFO'::text
            WHEN ((n.reconstruction_status)::text = 'WARN'::text) THEN 'WARN'::text
            WHEN ((n.open_quantity = (0)::numeric) AND (COALESCE(n.reconstructed_market_value_base, (0)::numeric) <> (0)::numeric)) THEN 'ERROR'::text
            WHEN ((n.open_quantity <> (0)::numeric) AND (COALESCE(n.selected_price, (0)::numeric) = (0)::numeric)) THEN 'ERROR'::text
            WHEN n.corporate_action_price_reset THEN 'WARN'::text
            WHEN ((n.price_origin)::text = 'MANUAL_WEEKLY'::text) THEN 'WARN'::text
            WHEN ((n.prev_valuation_date = (n.valuation_date - 1)) AND (n.prev_selected_price IS NOT NULL) AND (n.prev_selected_price > (0)::numeric) AND (((n.selected_price / n.prev_selected_price) >= investory.reconciliation_parameter('reconciliation_price_jump_upper_ratio'::character varying)) OR ((n.selected_price / n.prev_selected_price) <= investory.reconciliation_parameter('reconciliation_price_jump_lower_ratio'::character varying)))) THEN 'WARN'::text
            ELSE 'INFO'::text
        END)::character varying(16) AS severity,
    (
        CASE
            WHEN (((n.reconstruction_status)::text = 'FAIL'::text) AND (n.selected_price IS NULL)) THEN 'MISSING_PRICE'::text
            WHEN (((n.reconstruction_status)::text = 'FAIL'::text) AND (n.contract_multiplier IS NULL)) THEN 'MISSING_MULTIPLIER'::text
            WHEN (((n.reconstruction_status)::text = 'FAIL'::text) AND (COALESCE(n.fx_rate_to_base, (0)::numeric) = (0)::numeric)) THEN 'MISSING_FX'::text
            WHEN n.corporate_action_price_reset THEN 'CORPORATE_ACTION_PRICE_RESET'::text
            WHEN ((n.price_origin)::text = 'MANUAL_WEEKLY'::text) THEN 'MANUAL_WEEKLY_CONTINUITY_REVIEW'::text
            WHEN (n.selection_priority = 6) THEN 'TRADE_OBSERVATION_SELECTED'::text
            WHEN (n.selection_priority = 5) THEN 'INTERPOLATED_PRICE_SELECTED'::text
            WHEN (n.selection_priority = 3) THEN 'ALTERNATE_LISTING_SELECTED'::text
            WHEN ((n.open_quantity = (0)::numeric) AND (COALESCE(n.reconstructed_market_value_base, (0)::numeric) <> (0)::numeric)) THEN 'ZERO_QUANTITY_NONZERO_VALUE'::text
            WHEN ((n.open_quantity <> (0)::numeric) AND (COALESCE(n.selected_price, (0)::numeric) = (0)::numeric)) THEN 'NONZERO_QUANTITY_ZERO_PRICE'::text
            WHEN ((n.prev_valuation_date = (n.valuation_date - 1)) AND (n.prev_selected_price IS NOT NULL) AND (n.prev_selected_price > (0)::numeric) AND (((n.selected_price / n.prev_selected_price) >= investory.reconciliation_parameter('reconciliation_price_jump_upper_ratio'::character varying)) OR ((n.selected_price / n.prev_selected_price) <= investory.reconciliation_parameter('reconciliation_price_jump_lower_ratio'::character varying)))) THEN 'PRICE_RATIO_100X'::text
            ELSE 'OK'::text
        END)::character varying(64) AS validation_code,
    comparison.expected_value,
    n.reconstructed_market_value_base AS actual_value,
    (n.reconstructed_market_value_base - comparison.expected_value) AS difference,
    ((n.reconstructed_market_value_base - comparison.expected_value) / NULLIF(abs(comparison.expected_value), (0)::numeric)) AS relative_difference,
    n.reconstruction_message AS message
   FROM (classified n
     CROSS JOIN LATERAL ( SELECT
                CASE
                    WHEN (n.corporate_action_price_reset OR (n.selection_priority = 3) OR ((n.price_origin)::text = 'MANUAL_WEEKLY'::text) OR (n.prev_valuation_date <> (n.valuation_date - 1)) OR (n.prev_selected_price IS NULL) OR (n.open_quantity IS NULL) OR (n.contract_multiplier IS NULL) OR (NOT investory.fx_status_usable(n.fx_conversion_status)) OR (n.fx_rate_to_base IS NULL)) THEN NULL::numeric
                    ELSE (((n.open_quantity * n.prev_selected_price) * n.contract_multiplier) * n.fx_rate_to_base)
                END AS expected_value) comparison)
  WHERE (((n.reconstruction_status)::text <> 'PASS'::text) OR (n.selection_priority >= 3) OR ((n.prev_valuation_date = (n.valuation_date - 1)) AND (n.prev_selected_price IS NOT NULL) AND (n.prev_selected_price > (0)::numeric) AND (((n.selected_price / n.prev_selected_price) >= investory.reconciliation_parameter('reconciliation_price_jump_upper_ratio'::character varying)) OR ((n.selected_price / n.prev_selected_price) <= investory.reconciliation_parameter('reconciliation_price_jump_lower_ratio'::character varying)))));

CREATE VIEW investory.recon_v_reporting_validation_summary AS
 WITH price_summary AS (
         SELECT rpd.valuation_date,
            rpd.account_id,
            count(*) AS total_positions,
            count(*) FILTER (WHERE ((rpd.price_quality)::text = 'EXACT_LISTING_MARKET_CLOSE'::text)) AS exact_prices,
            count(*) FILTER (WHERE (((rpd.price_quality)::text ~~ '%ALTERNATE%'::text) OR ((rpd.price_quality)::text ~~ '%PROXY%'::text))) AS alternate_listing_prices,
            count(*) FILTER (WHERE ((rpd.price_quality)::text ~~ '%PROXY%'::text)) AS proxy_prices,
            count(*) FILTER (WHERE ((rpd.price_quality)::text ~~ 'INTERPOLATED%'::text)) AS interpolated_prices,
            count(*) FILTER (WHERE ((rpd.price_quality)::text ~~ '%TRADE_OBSERVATION%'::text)) AS trade_observation_prices,
            count(*) FILTER (WHERE (rpd.selected_price IS NULL)) AS missing_prices,
            count(*) FILTER (WHERE (NOT investory.fx_status_usable(rpd.fx_conversion_status))) AS missing_fx_rates,
            count(*) FILTER (WHERE (rpd.contract_multiplier IS NULL)) AS missing_multipliers,
            count(*) FILTER (WHERE ((rpd.open_quantity = (0)::numeric) AND (COALESCE(rpd.reconstructed_market_value_base, (0)::numeric) <> (0)::numeric))) AS residual_positions
           FROM investory.app_v_reconstructed_position_daily rpd
          GROUP BY rpd.valuation_date, rpd.account_id
        ), validation_counts AS (
         SELECT vpv.valuation_date,
            vpv.account_id,
            count(*) FILTER (WHERE ((vpv.validation_code)::text = 'PRICE_RATIO_100X'::text)) AS price_anomalies
           FROM investory.recon_v_position_valuation_validation vpv
          GROUP BY vpv.valuation_date, vpv.account_id
        ), recon AS (
         SELECT adr.valuation_date,
            adr.account_id,
            count(*) FILTER (WHERE ((adr.status)::text = 'FAIL'::text)) AS reconciliation_failures,
            max(abs(adr.market_value_difference)) AS maximum_market_value_difference,
            max(abs(adr.equity_difference)) AS maximum_equity_difference,
            max((adr.status)::text) AS status_hint
           FROM investory.recon_v_account_daily_diagnostic adr
          GROUP BY adr.valuation_date, adr.account_id
        )
 SELECT ps.valuation_date,
    ps.account_id,
    ps.total_positions,
    ps.exact_prices,
    ps.alternate_listing_prices,
    ps.proxy_prices,
    ps.interpolated_prices,
    ps.trade_observation_prices,
    ps.missing_prices,
    ps.missing_fx_rates,
    ps.missing_multipliers,
    ps.residual_positions,
    COALESCE(vc.price_anomalies, (0)::bigint) AS price_anomalies,
    COALESCE(r.reconciliation_failures, (0)::bigint) AS reconciliation_failures,
    COALESCE(r.maximum_market_value_difference, (0)::numeric) AS maximum_market_value_difference,
    COALESCE(r.maximum_equity_difference, (0)::numeric) AS maximum_equity_difference,
    (
        CASE
            WHEN ((ps.missing_prices > 0) OR (ps.missing_fx_rates > 0) OR (ps.missing_multipliers > 0) OR (ps.residual_positions > 0) OR (COALESCE(r.reconciliation_failures, (0)::bigint) > 0)) THEN 'FAIL'::text
            WHEN ((ps.interpolated_prices > 0) OR (COALESCE(vc.price_anomalies, (0)::bigint) > 0)) THEN 'WARN'::text
            ELSE 'PASS'::text
        END)::character varying(16) AS status
   FROM ((price_summary ps
     LEFT JOIN validation_counts vc ON (((vc.valuation_date = ps.valuation_date) AND (vc.account_id = ps.account_id))))
     LEFT JOIN recon r ON (((r.valuation_date = ps.valuation_date) AND (r.account_id = ps.account_id))));

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

CREATE VIEW investory.recon_v_position_currency_validation AS
 WITH per_row AS (
         SELECT p.id AS position_id,
            p.account_id,
            a.portfolio_id,
            portfolio.base_currency,
            a.name AS account_name,
            a.provider,
            a.currency AS account_currency,
            p.asset_id,
            asset.currency AS asset_currency,
            p.cost_currency AS position_currency,
            p.volume,
            p.open_price,
            p.purchase_value,
            p.close_time,
            p.close_price,
            p.sale_value,
                CASE
                    WHEN ((p.cost_currency IS NULL) OR (p.price_currency IS NULL) OR (p.profit_currency IS NULL) OR (p.commission_currency IS NULL)) THEN 'MISSING_POSITION_CURRENCY'::text
                    WHEN (asset.currency IS NULL) THEN 'MISSING_ASSET_CURRENCY'::text
                    WHEN (((a.provider)::text = 'XTB'::text) AND ((p.price_currency)::text IS DISTINCT FROM (asset.currency)::text) AND (((p.volume > (0)::numeric) AND (p.open_price > (0)::numeric) AND (p.purchase_value > (0)::numeric) AND (abs((abs((p.purchase_value / NULLIF(p.volume, (0)::numeric))) - p.open_price)) <= GREATEST(investory.reconciliation_parameter('reconciliation_position_input_absolute_tolerance'::character varying), (abs(p.open_price) * investory.reconciliation_parameter('reconciliation_position_input_relative_tolerance'::character varying))))) OR ((p.close_time IS NOT NULL) AND (p.volume > (0)::numeric) AND (p.close_price > (0)::numeric) AND (p.sale_value > (0)::numeric) AND (abs((abs((p.sale_value / NULLIF(p.volume, (0)::numeric))) - p.close_price)) <= GREATEST(investory.reconciliation_parameter('reconciliation_position_input_absolute_tolerance'::character varying), (abs(p.close_price) * investory.reconciliation_parameter('reconciliation_position_input_relative_tolerance'::character varying))))))) THEN 'POSITION_ASSET_CURRENCY_MISMATCH'::text
                    ELSE 'OK'::text
                END AS anomaly_code,
                CASE
                    WHEN ((p.cost_currency IS NULL) OR (p.price_currency IS NULL) OR (p.profit_currency IS NULL) OR (p.commission_currency IS NULL)) THEN 'One or more explicit position currencies are null.'::text
                    WHEN (asset.currency IS NULL) THEN 'Asset currency is null.'::text
                    WHEN (((a.provider)::text = 'XTB'::text) AND ((p.price_currency)::text IS DISTINCT FROM (asset.currency)::text)) THEN 'XTB price currency differs from the asset quote currency.'::text
                    WHEN ((p.price_currency)::text IS DISTINCT FROM (asset.currency)::text) THEN 'Broker price currency differs from asset listing currency; review the source mapping.'::text
                    ELSE 'No issue detected.'::text
                END AS details
           FROM (((investory.positions p
             JOIN investory.accounts a ON ((a.id = p.account_id)))
             JOIN investory.portfolios portfolio ON ((portfolio.id = a.portfolio_id)))
             LEFT JOIN investory.assets asset ON ((asset.id = p.asset_id)))
          WHERE (p.asset_id IS NOT NULL)
        ), mixed_groups AS (
         SELECT p.account_id,
            p.asset_id,
            count(DISTINCT p.cost_currency) AS distinct_position_currencies
           FROM investory.positions p
          WHERE ((p.close_time IS NULL) AND (p.asset_id IS NOT NULL) AND (COALESCE(p.volume, (0)::numeric) > (0)::numeric))
          GROUP BY p.account_id, p.asset_id
         HAVING (count(DISTINCT p.cost_currency) > 1)
        )
 SELECT pr.position_id,
    pr.account_id,
    pr.account_name,
    pr.provider,
    pr.account_currency,
    pr.asset_id,
    pr.asset_currency,
    pr.position_currency,
    pr.volume,
    pr.open_price,
    pr.purchase_value,
    pr.close_time,
    pr.close_price,
    pr.sale_value,
        CASE
            WHEN (mg.distinct_position_currencies IS NOT NULL) THEN 'MIXED_OPEN_POSITION_CURRENCIES'::text
            ELSE pr.anomaly_code
        END AS anomaly_code,
        CASE
            WHEN (mg.distinct_position_currencies IS NOT NULL) THEN 'Open positions for this account/asset have conflicting position currencies.'::text
            ELSE pr.details
        END AS details
   FROM (per_row pr
     LEFT JOIN mixed_groups mg ON (((mg.account_id = pr.account_id) AND (mg.asset_id = pr.asset_id))))
  WHERE ((pr.anomaly_code <> 'OK'::text) OR (mg.distinct_position_currencies IS NOT NULL));

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

CREATE VIEW investory.recon_v_portfolio_data_quality_issue AS
 SELECT 'POSITION'::character varying(32) AS issue_type,
    (account_id)::character varying(64) AS account_id,
    (asset_id)::character varying(64) AS asset_id,
    (
        CASE
            WHEN (selected_price IS NULL) THEN 'MISSING_PRICE'::text
            WHEN (price_age_days > 10) THEN 'STALE_PRICE'::text
            WHEN (NOT investory.fx_status_usable(fx_conversion_status)) THEN 'MISSING_FX'::text
            WHEN ((selection_priority = 3) OR ((price_quality)::text ~~* '%PROXY%'::text) OR ((price_quality)::text ~~* '%ALTERNATE%'::text)) THEN 'PROXY_PRICE'::text
            WHEN ((selection_priority = 5) OR ((price_quality)::text ~~* '%INTERPOLAT%'::text)) THEN 'ESTIMATED_PRICE'::text
            ELSE NULL::text
        END)::character varying(64) AS issue_code,
    price_age_days,
    selected_price_date,
    price_currency,
    price_quality,
    price_origin,
    reconstruction_status,
    NULL::bigint AS selected_price_history_id
   FROM investory.app_v_reconstructed_position_daily r
  WHERE ((valuation_date = ( SELECT max(app_v_reconstructed_position_daily.valuation_date) AS max
           FROM investory.app_v_reconstructed_position_daily)) AND (open_quantity <> (0)::numeric) AND ((selected_price IS NULL) OR (price_age_days > 10) OR (NOT investory.fx_status_usable(fx_conversion_status)) OR (selection_priority = ANY (ARRAY[3, 5])) OR ((price_quality)::text ~~* '%PROXY%'::text) OR ((price_quality)::text ~~* '%ALTERNATE%'::text) OR ((price_quality)::text ~~* '%INTERPOLAT%'::text)));

CREATE VIEW investory.recon_v_portfolio_data_quality_refresh AS
 SELECT ( SELECT max(import_history.finished_at) AS max
           FROM investory.import_history
          WHERE (import_history.status = 'COMPLETED'::text)) AS broker_imported_at,
    ( SELECT max(assets.price_updated_at) AS max
           FROM investory.assets) AS prices_updated_at,
    ( SELECT ((max(exchange_rates.rate_date))::timestamp without time zone AT TIME ZONE 'UTC'::text) AS timezone
           FROM investory.exchange_rates
          WHERE ((exchange_rates.purpose)::text = 'VALUATION'::text)) AS fx_updated_at,
    ( SELECT max(account_daily.updated_at) AS max
           FROM investory.account_daily) AS projections_rebuilt_at,
    ( SELECT max(account_daily.updated_at) AS max
           FROM investory.account_daily) AS reporting_refreshed_at;

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

CREATE VIEW investory.recon_v_position_lot_duplicates AS
 SELECT account_id,
    asset_id,
    source_position_id,
    source_row_occurrence,
    operation,
    settlement_model,
    open_time,
    close_time,
    volume,
    open_price,
    purchase_value,
    price_currency,
    cost_currency,
    profit_currency,
    commission_currency,
    count(*) AS duplicate_count,
    array_agg(id ORDER BY id) AS position_ids
   FROM investory.positions
  GROUP BY account_id, asset_id, source_position_id, source_row_occurrence, operation, settlement_model, open_time, close_time, volume, open_price, purchase_value, price_currency, cost_currency, profit_currency, commission_currency, COALESCE(commission, (0)::numeric), COALESCE(profit, (0)::numeric)
 HAVING (count(*) > 1);

CREATE VIEW investory.recon_v_price_history_contract_issues AS
 WITH mapped_history AS (
         SELECT aph.asset_id,
            asset.symbol AS asset_symbol,
            aph.price_date,
            aph.source,
            aph.source_symbol,
            aph.price_origin,
            aph.estimated,
            aph.source_mapping_id,
            aph.price_currency,
            aph.price_scale_factor,
            asset.currency AS asset_currency,
            ass.id AS mapping_id,
            ass.asset_id AS mapping_asset_id,
            ass.source AS mapping_source,
            ass.source_symbol AS mapping_source_symbol,
            ass.price_currency AS mapping_price_currency,
            ass.price_scale_factor AS mapping_scale_factor
           FROM ((investory.asset_price_history aph
             JOIN investory.assets asset ON (((asset.id = aph.asset_id) AND (asset.exclude_from_import = false))))
             LEFT JOIN investory.asset_source_symbols ass ON ((ass.id = aph.source_mapping_id)))
        )
 SELECT mapped_history.asset_id,
    mapped_history.asset_symbol,
    mapped_history.price_date,
    mapped_history.source,
    mapped_history.source_symbol,
    'SOURCE_MAPPING_MISMATCH'::character varying(64) AS issue_code,
    'source_mapping_id does not match asset/source/source_symbol'::text AS issue_message
   FROM mapped_history
  WHERE ((mapped_history.source_mapping_id IS NOT NULL) AND ((mapped_history.mapping_id IS NULL) OR (mapped_history.mapping_asset_id IS DISTINCT FROM mapped_history.asset_id) OR ((mapped_history.mapping_source)::text IS DISTINCT FROM (mapped_history.source)::text) OR (lower((mapped_history.mapping_source_symbol)::text) IS DISTINCT FROM lower((mapped_history.source_symbol)::text))))
UNION ALL
 SELECT mapped_history.asset_id,
    mapped_history.asset_symbol,
    mapped_history.price_date,
    mapped_history.source,
    mapped_history.source_symbol,
    'STOOQ_MAPPING_MISSING'::character varying(64) AS issue_code,
    'STOOQ history row has no source mapping'::text AS issue_message
   FROM mapped_history
  WHERE (((mapped_history.source)::text = 'STOOQ'::text) AND (mapped_history.source_mapping_id IS NULL))
UNION ALL
 SELECT mapped_history.asset_id,
    mapped_history.asset_symbol,
    mapped_history.price_date,
    mapped_history.source,
    mapped_history.source_symbol,
    'STOOQ_CURRENCY_MISMATCH'::character varying(64) AS issue_code,
    'history price_currency differs from the bound provider mapping'::text AS issue_message
   FROM mapped_history
  WHERE (((mapped_history.source)::text = 'STOOQ'::text) AND (mapped_history.source_mapping_id IS NOT NULL) AND ((mapped_history.price_currency)::text IS DISTINCT FROM (mapped_history.mapping_price_currency)::text))
UNION ALL
 SELECT mapped_history.asset_id,
    mapped_history.asset_symbol,
    mapped_history.price_date,
    mapped_history.source,
    mapped_history.source_symbol,
    'STOOQ_SCALE_MISMATCH'::character varying(64) AS issue_code,
    'history price_scale_factor differs from the bound provider mapping'::text AS issue_message
   FROM mapped_history
  WHERE (((mapped_history.source)::text = 'STOOQ'::text) AND (mapped_history.source_mapping_id IS NOT NULL) AND (mapped_history.price_scale_factor IS DISTINCT FROM mapped_history.mapping_scale_factor))
UNION ALL
 SELECT mapped_history.asset_id,
    mapped_history.asset_symbol,
    mapped_history.price_date,
    mapped_history.source,
    mapped_history.source_symbol,
    'XTB_QUOTE_CURRENCY_MISMATCH'::character varying(64) AS issue_code,
    'XTB price observation currency differs from the canonical asset quote currency'::text AS issue_message
   FROM mapped_history
  WHERE (((mapped_history.source)::text = ANY ((ARRAY['XTB_TRADE_OPEN'::character varying, 'XTB_TRADE_CLOSE'::character varying, 'INTERPOLATED_XTB'::character varying])::text[])) AND ((mapped_history.price_currency)::text IS DISTINCT FROM (mapped_history.asset_currency)::text))
UNION ALL
 SELECT mapped_history.asset_id,
    mapped_history.asset_symbol,
    mapped_history.price_date,
    mapped_history.source,
    mapped_history.source_symbol,
    'GENERATED_PRICE_CURRENCY_MISMATCH'::character varying(64) AS issue_code,
    'generated price currency differs from the canonical asset quote currency'::text AS issue_message
   FROM mapped_history
  WHERE ((((mapped_history.price_origin)::text = 'STALE_CARRY_FORWARD'::text) OR mapped_history.estimated) AND ((mapped_history.price_currency)::text IS DISTINCT FROM (mapped_history.asset_currency)::text));

CREATE VIEW investory.recon_v_price_temporal_anomaly AS
 WITH observations AS (
         SELECT aph.asset_id,
            a.symbol AS asset_symbol,
            aph.source,
            aph.source_symbol,
            aph.price_date,
            aph.price_currency,
            aph.price_origin,
            aph.quality_class,
            aph.is_proxy,
            aph.price_scale_factor,
            aph.source_mapping_id,
            ass.price_currency AS mapping_currency,
            ass.price_scale_factor AS mapping_scale_factor,
            ass.requires_fx_conversion,
            ass.is_exact_listing,
            ass.is_alternate_listing,
            (aph.close_price * aph.price_scale_factor) AS normalized_price,
            lag((aph.close_price * aph.price_scale_factor)) OVER w AS previous_price,
            lag(aph.price_date) OVER w AS previous_date,
            lag(aph.price_currency) OVER w AS previous_currency,
            lead((aph.close_price * aph.price_scale_factor)) OVER w AS next_price,
            lead(aph.price_date) OVER w AS next_date
           FROM ((investory.asset_price_history aph
             JOIN investory.assets a ON (((a.id = aph.asset_id) AND (NOT a.exclude_from_import))))
             LEFT JOIN investory.asset_source_symbols ass ON ((ass.id = aph.source_mapping_id)))
          WHERE (aph.is_observed AND (NOT aph.estimated) AND (aph.close_price > (0)::numeric) AND ((aph.quality_class)::text = ANY ((ARRAY['EXACT_LISTING_MARKET_CLOSE'::character varying, 'VERIFIED_ALTERNATE_LISTING'::character varying, 'EXACT_LISTING_SCALED'::character varying, 'EXACT_LISTING_MARKET_CLOSE_PERCENT_OF_PAR'::character varying, 'MANUAL_ACCEPTED'::character varying])::text[])))
          WINDOW w AS (PARTITION BY aph.asset_id, aph.source, aph.source_symbol ORDER BY aph.price_date)
        ), p AS (
         SELECT (investory.reconciliation_parameter('reconciliation_temporal_short_gap_days'::character varying))::integer AS short_gap,
            investory.reconciliation_parameter('reconciliation_price_extreme_move_ratio'::character varying) AS extreme_move,
            investory.reconciliation_parameter('reconciliation_price_isolated_spike_ratio'::character varying) AS spike_ratio,
            investory.reconciliation_parameter('reconciliation_temporal_neighbor_recovery_ratio'::character varying) AS recovery,
            investory.reconciliation_parameter('reconciliation_price_scale_upper_ratio'::character varying) AS scale_upper,
            investory.reconciliation_parameter('reconciliation_price_scale_lower_ratio'::character varying) AS scale_lower,
            investory.reconciliation_parameter('reconciliation_price_scale_ten_upper_ratio'::character varying) AS ten_upper,
            investory.reconciliation_parameter('reconciliation_price_scale_ten_lower_ratio'::character varying) AS ten_lower
        ), short_moves AS (
         SELECT o.asset_id,
            o.asset_symbol,
            o.source,
            o.source_symbol,
            o.price_date,
            o.price_currency,
            o.price_origin,
            o.quality_class,
            o.is_proxy,
            o.price_scale_factor,
            o.source_mapping_id,
            o.mapping_currency,
            o.mapping_scale_factor,
            o.requires_fx_conversion,
            o.is_exact_listing,
            o.is_alternate_listing,
            o.normalized_price,
            o.previous_price,
            o.previous_date,
            o.previous_currency,
            o.next_price,
            o.next_date,
            (o.normalized_price / NULLIF(o.previous_price, (0)::numeric)) AS price_ratio,
            ((o.normalized_price / NULLIF(o.previous_price, (0)::numeric)) - (1)::numeric) AS change_pct
           FROM (observations o
             CROSS JOIN p)
          WHERE ((o.previous_price > (0)::numeric) AND ((o.price_date - o.previous_date) <= p.short_gap))
        ), isolated AS (
         SELECT s.asset_id,
            s.asset_symbol,
            s.source,
            s.source_symbol,
            s.price_date,
            s.price_currency,
            s.price_origin,
            s.quality_class,
            s.is_proxy,
            s.price_scale_factor,
            s.source_mapping_id,
            s.mapping_currency,
            s.mapping_scale_factor,
            s.requires_fx_conversion,
            s.is_exact_listing,
            s.is_alternate_listing,
            s.normalized_price,
            s.previous_price,
            s.previous_date,
            s.previous_currency,
            s.next_price,
            s.next_date,
            s.price_ratio,
            s.change_pct
           FROM (short_moves s
             CROSS JOIN p)
          WHERE ((s.next_price > (0)::numeric) AND ((s.next_date - s.price_date) <= p.short_gap) AND (abs((s.price_ratio - (1)::numeric)) >= p.spike_ratio) AND (abs(((s.next_price / s.previous_price) - (1)::numeric)) <= p.recovery))
        )
 SELECT 'WARN'::character varying(16) AS severity,
    'PRICE_EXTREME_MOVE'::character varying(64) AS issue_code,
    'ASSET'::character varying(16) AS entity_type,
    s.asset_id AS entity_id,
    s.asset_symbol AS entity_key,
    s.price_date AS event_date,
    s.previous_date,
    s.next_date,
    (s.price_date - s.previous_date) AS gap_days,
    s.previous_price AS previous_value,
    s.normalized_price AS current_value,
    s.next_price AS next_value,
    s.change_pct,
    s.price_ratio AS ratio,
    ((((((((((((('observed '::text || (s.asset_symbol)::text) || ' '::text) || (s.source)::text) || '/'::text) || (s.source_symbol)::text) || ' moved from '::text) || s.previous_price) || ' to '::text) || s.normalized_price) || '; origin='::text) || (s.price_origin)::text) || ', quality='::text) || (COALESCE(s.quality_class, 'n/a'::character varying))::text) AS explanation,
    s.source,
    s.source_symbol,
    s.price_currency AS observation_currency,
    s.mapping_currency,
    s.price_scale_factor AS scale_factor,
    s.mapping_scale_factor,
    s.price_origin,
    s.quality_class,
    s.is_proxy,
    s.requires_fx_conversion,
    s.is_exact_listing,
    s.is_alternate_listing
   FROM (short_moves s
     CROSS JOIN p)
  WHERE ((abs(s.change_pct) >= p.extreme_move) AND (NOT (EXISTS ( SELECT 1
           FROM isolated i
          WHERE ((i.asset_id = s.asset_id) AND ((i.source)::text = (s.source)::text) AND ((i.source_symbol)::text = (s.source_symbol)::text) AND (i.price_date = s.price_date))))))
UNION ALL
 SELECT 'ERROR'::character varying AS severity,
    'PRICE_ISOLATED_SPIKE'::character varying AS issue_code,
    'ASSET'::character varying AS entity_type,
    isolated.asset_id AS entity_id,
    isolated.asset_symbol AS entity_key,
    isolated.price_date AS event_date,
    isolated.previous_date,
    isolated.next_date,
    (isolated.price_date - isolated.previous_date) AS gap_days,
    isolated.previous_price AS previous_value,
    isolated.normalized_price AS current_value,
    isolated.next_price AS next_value,
    isolated.change_pct,
    isolated.price_ratio AS ratio,
    ((((('isolated observed price spike; neighbors recover from '::text || isolated.previous_price) || ' to '::text) || isolated.next_price) || ', current='::text) || isolated.normalized_price) AS explanation,
    isolated.source,
    isolated.source_symbol,
    isolated.price_currency AS observation_currency,
    isolated.mapping_currency,
    isolated.price_scale_factor AS scale_factor,
    isolated.mapping_scale_factor,
    isolated.price_origin,
    isolated.quality_class,
    isolated.is_proxy,
    isolated.requires_fx_conversion,
    isolated.is_exact_listing,
    isolated.is_alternate_listing
   FROM isolated
UNION ALL
 SELECT 'ERROR'::character varying AS severity,
    'PRICE_CURRENCY_MISMATCH'::character varying AS issue_code,
    'ASSET'::character varying AS entity_type,
    observations.asset_id AS entity_id,
    observations.asset_symbol AS entity_key,
    observations.price_date AS event_date,
    observations.previous_date,
    observations.next_date,
    NULL::integer AS gap_days,
    observations.previous_price AS previous_value,
    observations.normalized_price AS current_value,
    observations.next_price AS next_value,
    NULL::numeric AS change_pct,
    NULL::numeric AS ratio,
    (((('observed currency '::text || (observations.price_currency)::text) || ' differs from applicable '::text) || 'provider/listing mapping currency '::text) || (observations.mapping_currency)::text) AS explanation,
    observations.source,
    observations.source_symbol,
    observations.price_currency AS observation_currency,
    observations.mapping_currency,
    observations.price_scale_factor AS scale_factor,
    observations.mapping_scale_factor,
    observations.price_origin,
    observations.quality_class,
    observations.is_proxy,
    observations.requires_fx_conversion,
    observations.is_exact_listing,
    observations.is_alternate_listing
   FROM observations
  WHERE ((observations.mapping_currency IS NOT NULL) AND ((observations.price_currency)::text IS DISTINCT FROM (observations.mapping_currency)::text))
UNION ALL
 SELECT 'WARN'::character varying AS severity,
    'PRICE_CURRENCY_SWITCH'::character varying AS issue_code,
    'ASSET'::character varying AS entity_type,
    observations.asset_id AS entity_id,
    observations.asset_symbol AS entity_key,
    observations.price_date AS event_date,
    observations.previous_date,
    observations.next_date,
    (observations.price_date - observations.previous_date) AS gap_days,
    observations.previous_price AS previous_value,
    observations.normalized_price AS current_value,
    observations.next_price AS next_value,
    NULL::numeric AS change_pct,
    (observations.normalized_price / NULLIF(observations.previous_price, (0)::numeric)) AS ratio,
    ((('provider/listing observation currency switched from '::text || (observations.previous_currency)::text) || ' to '::text) || (observations.price_currency)::text) AS explanation,
    observations.source,
    observations.source_symbol,
    observations.price_currency AS observation_currency,
    observations.mapping_currency,
    observations.price_scale_factor AS scale_factor,
    observations.mapping_scale_factor,
    observations.price_origin,
    observations.quality_class,
    observations.is_proxy,
    observations.requires_fx_conversion,
    observations.is_exact_listing,
    observations.is_alternate_listing
   FROM observations
  WHERE ((observations.previous_currency IS NOT NULL) AND ((observations.price_currency)::text IS DISTINCT FROM (observations.previous_currency)::text))
UNION ALL
 SELECT 'ERROR'::character varying AS severity,
    'PRICE_SCALE_MISMATCH'::character varying AS issue_code,
    'ASSET'::character varying AS entity_type,
    observations.asset_id AS entity_id,
    observations.asset_symbol AS entity_key,
    observations.price_date AS event_date,
    observations.previous_date,
    observations.next_date,
    NULL::integer AS gap_days,
    observations.previous_price AS previous_value,
    observations.normalized_price AS current_value,
    observations.next_price AS next_value,
    NULL::numeric AS change_pct,
    NULL::numeric AS ratio,
    ((('history scale '::text || observations.price_scale_factor) || ' differs from mapping scale '::text) || observations.mapping_scale_factor) AS explanation,
    observations.source,
    observations.source_symbol,
    observations.price_currency AS observation_currency,
    observations.mapping_currency,
    observations.price_scale_factor AS scale_factor,
    observations.mapping_scale_factor,
    observations.price_origin,
    observations.quality_class,
    observations.is_proxy,
    observations.requires_fx_conversion,
    observations.is_exact_listing,
    observations.is_alternate_listing
   FROM observations
  WHERE ((observations.mapping_scale_factor IS NOT NULL) AND (observations.price_scale_factor IS DISTINCT FROM observations.mapping_scale_factor))
UNION ALL
 SELECT 'WARN'::character varying AS severity,
    'PRICE_SCALE_DISCONTINUITY'::character varying AS issue_code,
    'ASSET'::character varying AS entity_type,
    s.asset_id AS entity_id,
    s.asset_symbol AS entity_key,
    s.price_date AS event_date,
    s.previous_date,
    s.next_date,
    (s.price_date - s.previous_date) AS gap_days,
    s.previous_price AS previous_value,
    s.normalized_price AS current_value,
    s.next_price AS next_value,
    ((s.normalized_price / NULLIF(s.previous_price, (0)::numeric)) - (1)::numeric) AS change_pct,
    (s.normalized_price / NULLIF(s.previous_price, (0)::numeric)) AS ratio,
    ('observed normalized price ratio suggests a unit boundary; ratio='::text || (s.normalized_price / NULLIF(s.previous_price, (0)::numeric))) AS explanation,
    s.source,
    s.source_symbol,
    s.price_currency AS observation_currency,
    s.mapping_currency,
    s.price_scale_factor AS scale_factor,
    s.mapping_scale_factor,
    s.price_origin,
    s.quality_class,
    s.is_proxy,
    s.requires_fx_conversion,
    s.is_exact_listing,
    s.is_alternate_listing
   FROM (short_moves s
     CROSS JOIN p)
  WHERE ((s.price_ratio >= p.scale_upper) OR (s.price_ratio <= p.scale_lower) OR ((s.price_ratio >= p.ten_lower) AND (s.price_ratio <= p.ten_upper)) OR ((((1)::numeric / NULLIF(s.price_ratio, (0)::numeric)) >= p.ten_lower) AND (((1)::numeric / NULLIF(s.price_ratio, (0)::numeric)) <= p.ten_upper)));

CREATE VIEW investory.recon_v_reconciliation_position_issues AS
 WITH reconstructed AS (
         SELECT app_v_reconstructed_position_daily.account_id,
            app_v_reconstructed_position_daily.asset_id,
            app_v_reconstructed_position_daily.valuation_date,
            sum(COALESCE(app_v_reconstructed_position_daily.open_quantity, (0)::numeric)) AS open_quantity,
            sum(COALESCE(app_v_reconstructed_position_daily.reconstructed_cost_base_base, (0)::numeric)) AS reconstructed_cost_base_base,
            max(app_v_reconstructed_position_daily.selected_price) AS selected_price,
            max(app_v_reconstructed_position_daily.reconstructed_market_value_base) AS reconstructed_market_value_base,
            max(app_v_reconstructed_position_daily.selected_price_date) AS selected_price_date,
            max(app_v_reconstructed_position_daily.underlying_observation_date) AS underlying_observation_date,
            max(app_v_reconstructed_position_daily.price_age_days) AS price_age_days,
            string_agg(DISTINCT (app_v_reconstructed_position_daily.price_currency)::text, ', '::text ORDER BY (app_v_reconstructed_position_daily.price_currency)::text) AS price_currency,
            max(app_v_reconstructed_position_daily.fx_rate_to_base) AS fx_rate_to_base,
            string_agg(DISTINCT (app_v_reconstructed_position_daily.fx_conversion_status)::text, ', '::text ORDER BY (app_v_reconstructed_position_daily.fx_conversion_status)::text) AS fx_conversion_status,
            string_agg(DISTINCT (app_v_reconstructed_position_daily.price_origin)::text, ', '::text ORDER BY (app_v_reconstructed_position_daily.price_origin)::text) AS price_origin,
            string_agg(DISTINCT (app_v_reconstructed_position_daily.price_quality)::text, ', '::text ORDER BY (app_v_reconstructed_position_daily.price_quality)::text) AS price_quality,
            max(app_v_reconstructed_position_daily.selection_priority) AS selection_priority,
            (
                CASE
                    WHEN bool_or(((app_v_reconstructed_position_daily.reconstruction_status)::text = 'FAIL'::text)) THEN 'FAIL'::text
                    WHEN bool_or(((app_v_reconstructed_position_daily.reconstruction_status)::text = 'WARN'::text)) THEN 'WARN'::text
                    ELSE 'PASS'::text
                END)::character varying(16) AS reconstruction_status,
            string_agg(DISTINCT app_v_reconstructed_position_daily.reconstruction_message, ' | '::text ORDER BY app_v_reconstructed_position_daily.reconstruction_message) AS reconstruction_message
           FROM investory.app_v_reconstructed_position_daily
          GROUP BY app_v_reconstructed_position_daily.account_id, app_v_reconstructed_position_daily.asset_id, app_v_reconstructed_position_daily.valuation_date
        )
 SELECT vpv.account_id,
    account.name AS account_name,
    account.provider,
    vpv.asset_id,
    asset.symbol,
    vpv.valuation_date,
    vpv.severity,
    vpv.validation_code,
    (
        CASE
            WHEN ((vpv.validation_code)::text = ANY (ARRAY[('MISSING_PRICE'::character varying)::text, ('MISSING_MULTIPLIER'::character varying)::text, ('MISSING_FX'::character varying)::text])) THEN 'INCOMPLETE_INPUT'::text
            WHEN ((vpv.validation_code)::text = ANY (ARRAY[('PRICE_RATIO_100X'::character varying)::text, ('TRADE_OBSERVATION_SELECTED'::character varying)::text, ('INTERPOLATED_PRICE_SELECTED'::character varying)::text, ('ALTERNATE_LISTING_SELECTED'::character varying)::text])) THEN 'LIKELY_PRICE_OR_SCALE'::text
            WHEN ((vpv.validation_code)::text = ANY (ARRAY[('ZERO_QUANTITY_NONZERO_VALUE'::character varying)::text, ('NONZERO_QUANTITY_ZERO_PRICE'::character varying)::text])) THEN 'LIKELY_POSITION_OR_SETTLEMENT'::text
            ELSE 'REVIEW'::text
        END)::character varying(64) AS suspected_source,
    rpd.selected_price,
    rpd.reconstructed_market_value_base,
    vpv.difference,
    vpv.relative_difference,
    vpv.message,
    rpd.open_quantity,
    rpd.reconstructed_cost_base_base,
    rpd.selected_price_date,
    rpd.underlying_observation_date,
    rpd.price_age_days,
    rpd.price_currency,
    rpd.fx_rate_to_base,
    rpd.fx_conversion_status,
    rpd.price_origin,
    rpd.price_quality,
    rpd.selection_priority,
    rpd.reconstruction_status,
    rpd.reconstruction_message,
    adr.status AS account_reconciliation_status,
    adr.market_value_difference AS account_market_value_difference,
    adr.cash_difference AS account_cash_difference,
    adr.equity_difference AS account_equity_difference
   FROM ((((investory.recon_v_position_valuation_validation vpv
     JOIN investory.accounts account ON ((account.id = vpv.account_id)))
     JOIN investory.assets asset ON ((asset.id = vpv.asset_id)))
     LEFT JOIN reconstructed rpd ON (((rpd.account_id = vpv.account_id) AND (rpd.asset_id = vpv.asset_id) AND (rpd.valuation_date = vpv.valuation_date))))
     LEFT JOIN investory.recon_v_account_daily adr ON (((adr.account_id = vpv.account_id) AND (adr.valuation_date = vpv.valuation_date))));

CREATE VIEW investory.recon_v_timezone_naive_columns AS
 SELECT table_name,
    column_name,
    data_type
   FROM information_schema.columns
  WHERE (((table_schema)::name = 'investory'::name) AND ((data_type)::text = 'timestamp without time zone'::text) AND ((table_name)::name <> 'flyway_schema_history'::name));

CREATE VIEW investory.recon_v_unsupported_transaction_states AS
 SELECT 'cash_operations'::character varying(32) AS source_table,
    co.id AS row_id,
    co.account_id,
    co.asset_id,
    co.date AS occurred_at,
    'UNKNOWN_CASH_OPERATION'::character varying(64) AS issue_code,
    (co.operation)::text AS raw_state,
    co.comment AS evidence
   FROM investory.cash_operations co
  WHERE (co.operation = 'UNKNOWN'::investory.cash_operation_type)
UNION ALL
 SELECT 'cash_operations'::character varying(32) AS source_table,
    nco.operation_id AS row_id,
    nco.account_id,
    nco.asset_id,
    nco.date AS occurred_at,
    'UNCLASSIFIED_CASH_OPERATION'::character varying(64) AS issue_code,
    (nco.raw_operation)::text AS raw_state,
    nco.comment AS evidence
   FROM investory.app_v_normalized_cash_operations nco
  WHERE ((nco.normalized_category)::text = 'UNCLASSIFIED'::text)
UNION ALL
 SELECT 'positions'::character varying(32) AS source_table,
    p.id AS row_id,
    p.account_id,
    p.asset_id,
    p.open_time AS occurred_at,
    'UNCLASSIFIED_SETTLEMENT_MODEL'::character varying(64) AS issue_code,
    (p.settlement_model)::text AS raw_state,
    p.broker_product AS evidence
   FROM investory.positions p
  WHERE (p.settlement_model = 'UNCLASSIFIED'::investory.position_settlement_model);

CREATE VIEW investory.recon_v_valuation_input_issues AS
 WITH latest_position_date AS (
         SELECT max(d.snapshot_date) AS valuation_date
           FROM investory.account_daily d
          WHERE (EXISTS ( SELECT 1
                   FROM (investory.positions p
                     JOIN investory.assets a ON ((a.id = p.asset_id)))
                  WHERE ((p.account_id = d.account_id) AND (a.exclude_from_import = false) AND (d.snapshot_date >= COALESCE((p.open_time)::date, d.snapshot_date)) AND ((p.close_time IS NULL) OR (d.snapshot_date < (p.close_time)::date)))))
        ), position_issues AS (
         SELECT (
                CASE
                    WHEN (rpd.selected_price IS NULL) THEN 'MISSING_PRICE'::text
                    WHEN (rpd.price_age_days > 10) THEN 'STALE_PRICE'::text
                    WHEN ((rpd.fx_conversion_status)::text = ANY (ARRAY[('MISSING_RATE'::character varying)::text, ('MISSING_CURRENCY'::character varying)::text])) THEN 'MISSING_FX'::text
                    WHEN ((rpd.fx_conversion_status)::text = 'STALE'::text) THEN 'STALE_FX'::text
                    ELSE NULL::text
                END)::character varying(64) AS issue_code,
            (
                CASE
                    WHEN ((rpd.selected_price IS NULL) OR ((rpd.fx_conversion_status)::text = ANY (ARRAY[('MISSING_RATE'::character varying)::text, ('MISSING_CURRENCY'::character varying)::text]))) THEN 'ERROR'::text
                    ELSE 'WARN'::text
                END)::character varying(16) AS severity,
            account.portfolio_id,
            rpd.account_id,
            rpd.asset_id,
            rpd.valuation_date,
            rpd.price_currency AS source_currency,
            portfolio.base_currency AS target_currency,
            rpd.selected_price_date AS input_date,
                CASE
                    WHEN (rpd.selected_price IS NULL) THEN NULL::integer
                    ELSE rpd.price_age_days
                END AS age_days,
            concat_ws('; '::text, ('symbol='::text || (asset.symbol)::text), ('price_quality='::text || (COALESCE(rpd.price_quality, 'NULL'::character varying))::text), ('fx_status='::text || (COALESCE(rpd.fx_conversion_status, 'NULL'::character varying))::text)) AS details,
            (
                CASE
                    WHEN (rpd.selected_price IS NULL) THEN 'Import a canonical price on or before the valuation date, then rebuild reporting.'::text
                    WHEN (rpd.price_age_days > 10) THEN 'Review whether the last available price is valid for this instrument and valuation date.'::text
                    WHEN ((rpd.fx_conversion_status)::text = ANY (ARRAY[('MISSING_RATE'::character varying)::text, ('MISSING_CURRENCY'::character varying)::text])) THEN 'Import the required FX rate, then rebuild reporting.'::text
                    ELSE 'Review the stale FX rate before accepting reporting.'::text
                END)::character varying(255) AS required_action
           FROM ((((investory.app_v_reconstructed_position_daily rpd
             JOIN investory.accounts account ON ((account.id = rpd.account_id)))
             JOIN investory.portfolios portfolio ON ((portfolio.id = account.portfolio_id)))
             JOIN investory.assets asset ON ((asset.id = rpd.asset_id)))
             CROSS JOIN latest_position_date latest)
          WHERE ((rpd.valuation_date = latest.valuation_date) AND (rpd.open_quantity <> (0)::numeric) AND ((rpd.selected_price IS NULL) OR (rpd.price_age_days > 10) OR (NOT investory.fx_status_usable(rpd.fx_conversion_status))))
        ), cash_fx_issues AS (
         SELECT (
                CASE
                    WHEN ((nco.portfolio_conversion_status)::text = ANY (ARRAY[('MISSING_RATE'::character varying)::text, ('MISSING_CURRENCY'::character varying)::text])) THEN 'MISSING_FX'::text
                    ELSE 'STALE_FX'::text
                END)::character varying(64) AS issue_code,
            (
                CASE
                    WHEN ((nco.portfolio_conversion_status)::text = ANY (ARRAY[('MISSING_RATE'::character varying)::text, ('MISSING_CURRENCY'::character varying)::text])) THEN 'ERROR'::text
                    ELSE 'WARN'::text
                END)::character varying(16) AS severity,
            nco.portfolio_id,
            nco.account_id,
            nco.asset_id,
            (nco.date)::date AS valuation_date,
            nco.currency AS source_currency,
            nco.base_currency AS target_currency,
            nco.portfolio_source_rate_date AS input_date,
            nco.portfolio_fx_age_days AS age_days,
            concat_ws('; '::text, ('operation_id='::text || nco.operation_id), ('operation='::text || (nco.raw_operation)::text), ('category='::text || (nco.normalized_category)::text), ('fx_status='::text || (nco.portfolio_conversion_status)::text)) AS details,
            (
                CASE
                    WHEN ((nco.portfolio_conversion_status)::text = ANY (ARRAY[('MISSING_RATE'::character varying)::text, ('MISSING_CURRENCY'::character varying)::text])) THEN 'Import the required FX rate, then rebuild reporting.'::text
                    ELSE 'Review the stale FX rate before accepting reporting.'::text
                END)::character varying(255) AS required_action
           FROM investory.app_v_normalized_cash_operations nco
          WHERE (NOT investory.fx_status_usable(nco.portfolio_conversion_status))
        )
 SELECT position_issues.issue_code,
    position_issues.severity,
    position_issues.portfolio_id,
    position_issues.account_id,
    position_issues.asset_id,
    position_issues.valuation_date,
    position_issues.source_currency,
    position_issues.target_currency,
    position_issues.input_date,
    position_issues.age_days,
    position_issues.details,
    position_issues.required_action
   FROM position_issues
  WHERE (position_issues.issue_code IS NOT NULL)
UNION ALL
 SELECT cash_fx_issues.issue_code,
    cash_fx_issues.severity,
    cash_fx_issues.portfolio_id,
    cash_fx_issues.account_id,
    cash_fx_issues.asset_id,
    cash_fx_issues.valuation_date,
    cash_fx_issues.source_currency,
    cash_fx_issues.target_currency,
    cash_fx_issues.input_date,
    cash_fx_issues.age_days,
    cash_fx_issues.details,
    cash_fx_issues.required_action
   FROM cash_fx_issues;

CREATE VIEW investory.recon_v_reporting_monthly_import_review AS
 WITH valuation_issue_counts AS MATERIALIZED (
         SELECT count(*) FILTER (WHERE ((recon_v_valuation_input_issues.severity)::text = 'ERROR'::text)) AS error_count,
            count(*) FILTER (WHERE ((recon_v_valuation_input_issues.severity)::text = 'WARN'::text)) AS warning_count
           FROM investory.recon_v_valuation_input_issues
        )
 SELECT 'UNSUPPORTED_TRANSACTION_STATE'::character varying(64) AS check_code,
    count(*) AS issue_count,
    'Review UNKNOWN or UNCLASSIFIED imported ledger states.'::character varying(255) AS required_action
   FROM investory.recon_v_unsupported_transaction_states
UNION ALL
 SELECT 'DUPLICATE_POSITION_LOT'::character varying(64) AS check_code,
    count(*) AS issue_count,
    'Review duplicate source lots before trusting position and P/L totals.'::character varying(255) AS required_action
   FROM investory.recon_v_position_lot_duplicates
UNION ALL
 SELECT 'TIMEZONE_NAIVE_COLUMN'::character varying(64) AS check_code,
    count(*) AS issue_count,
    'Convert event or audit timestamps to timestamptz.'::character varying(255) AS required_action
   FROM investory.recon_v_timezone_naive_columns
UNION ALL
 SELECT 'VALUATION_INPUT_ERROR'::character varying(64) AS check_code,
    valuation_issue_counts.error_count AS issue_count,
    'Import missing prices or FX rates and rebuild reporting.'::character varying(255) AS required_action
   FROM valuation_issue_counts
UNION ALL
 SELECT 'VALUATION_INPUT_WARNING'::character varying(64) AS check_code,
    valuation_issue_counts.warning_count AS issue_count,
    'Review stale price or FX inputs before accepting month-end reporting.'::character varying(255) AS required_action
   FROM valuation_issue_counts;

CREATE VIEW investory.recon_v_temporal_anomaly AS
 SELECT recon_v_fx_temporal_anomaly.severity,
    recon_v_fx_temporal_anomaly.issue_code,
    recon_v_fx_temporal_anomaly.entity_type,
    recon_v_fx_temporal_anomaly.entity_id,
    recon_v_fx_temporal_anomaly.entity_key,
    recon_v_fx_temporal_anomaly.event_date,
    recon_v_fx_temporal_anomaly.previous_date,
    recon_v_fx_temporal_anomaly.next_date,
    recon_v_fx_temporal_anomaly.gap_days,
    recon_v_fx_temporal_anomaly.previous_value,
    recon_v_fx_temporal_anomaly.current_value,
    recon_v_fx_temporal_anomaly.next_value,
    recon_v_fx_temporal_anomaly.change_pct,
    recon_v_fx_temporal_anomaly.ratio,
    recon_v_fx_temporal_anomaly.explanation,
    recon_v_fx_temporal_anomaly.source,
    recon_v_fx_temporal_anomaly.source_symbol,
    recon_v_fx_temporal_anomaly.observation_currency,
    NULL::character varying(3) AS mapping_currency,
    recon_v_fx_temporal_anomaly.scale_factor,
    NULL::numeric AS mapping_scale_factor,
    NULL::character varying(32) AS price_origin,
    NULL::character varying(64) AS quality_class,
    recon_v_fx_temporal_anomaly.is_proxy
   FROM investory.recon_v_fx_temporal_anomaly
UNION ALL
 SELECT p.severity,
    p.issue_code,
    p.entity_type,
    p.entity_id,
    p.entity_key,
    p.event_date,
    p.previous_date,
    p.next_date,
    p.gap_days,
    p.previous_value,
    p.current_value,
    p.next_value,
    p.change_pct,
    p.ratio,
    p.explanation,
    p.source,
    p.source_symbol,
    p.observation_currency,
    p.mapping_currency,
    p.scale_factor,
    p.mapping_scale_factor,
    p.price_origin,
    p.quality_class,
    p.is_proxy
   FROM investory.recon_v_price_temporal_anomaly p
  WHERE (NOT (EXISTS ( SELECT 1
           FROM investory.reconciliation_price_anomaly_reviews r
          WHERE (((r.resolution)::text = ANY ((ARRAY['CONFIRMED_MARKET_MOVE'::character varying, 'SOURCE_PRICE_CORRECTED'::character varying, 'MANUAL_ALTERNATE_LISTING_ACCEPTED'::character varying])::text[])) AND ((r.issue_code)::text = (p.issue_code)::text) AND (r.entity_id = p.entity_id) AND (r.event_date = p.event_date) AND (r.previous_date = p.previous_date) AND ((r.source)::text = (p.source)::text) AND ((r.source_symbol)::text = (p.source_symbol)::text) AND (r.previous_value = p.previous_value) AND (r.current_value = p.current_value)))))
UNION ALL
 SELECT t.severity,
    t.issue_code,
    t.entity_type,
    t.entity_id,
    t.entity_key,
    t.event_date,
    t.previous_date,
    t.next_date,
    t.gap_days,
    t.previous_value,
    t.current_value,
    t.next_value,
    t.change_pct,
    t.ratio,
    t.explanation,
    t.source,
    t.source_symbol,
    t.observation_currency,
    t.mapping_currency,
    t.scale_factor,
    t.mapping_scale_factor,
    t.price_origin,
    t.quality_class,
    t.is_proxy
   FROM investory.recon_v_account_temporal_anomaly_fingerprinted t
  WHERE (NOT (EXISTS ( SELECT 1
           FROM investory.reconciliation_account_movement_reviews r
          WHERE (((r.resolution)::text = ANY ((ARRAY['EXPLAINED'::character varying, 'CORRECTED'::character varying])::text[])) AND ((r.issue_code)::text = (t.issue_code)::text) AND (r.account_id = t.entity_id) AND (r.event_date = t.event_date) AND ((r.event_fingerprint)::text = t.event_fingerprint) AND (r.id = ( SELECT latest.id
                   FROM investory.reconciliation_account_movement_reviews latest
                  WHERE (((latest.issue_code)::text = (r.issue_code)::text) AND (latest.account_id = r.account_id) AND (latest.event_date = r.event_date))
                  ORDER BY latest.id DESC
                 LIMIT 1))))));

CREATE MATERIALIZED VIEW investory.recon_v_trade_settlement AS
 WITH closed_lots AS (
         SELECT account.portfolio_id,
            p.account_id,
            p.asset_id,
            asset.symbol,
            (p.close_time)::date AS valuation_date,
            (p.settlement_model)::character varying(32) AS position_settlement_model,
            abs(COALESCE(p.volume, (0)::numeric)) AS closed_quantity,
                CASE
                    WHEN (p.settlement_model = 'CASH_SETTLED'::investory.position_settlement_model) THEN COALESCE(p.sale_value, (abs(COALESCE(p.volume, (0)::numeric)) * COALESCE(p.close_price, (0)::numeric)), (0)::numeric)
                    ELSE NULL::numeric
                END AS close_notional_native,
                CASE
                    WHEN ((p.settlement_model = 'RESULT_ONLY'::investory.position_settlement_model) AND investory.fx_status_usable(profit_fx.conversion_status) AND ((COALESCE(p.commission, (0)::numeric) = (0)::numeric) OR investory.fx_status_usable(commission_fx.conversion_status))) THEN (((COALESCE(p.profit, (0)::numeric) - COALESCE(p.swap, (0)::numeric)) * profit_fx.fx_rate_to_base) - (COALESCE(p.commission, (0)::numeric) * COALESCE(commission_fx.fx_rate_to_base, (0)::numeric)))
                    ELSE NULL::numeric
                END AS close_result_base,
            cost_fx.fx_rate_to_base AS cost_fx_rate_to_base,
            cost_fx.conversion_status AS cost_conversion_status,
                CASE
                    WHEN ((p.settlement_model = 'CASH_SETTLED'::investory.position_settlement_model) AND (NOT investory.fx_status_usable(cost_fx.conversion_status))) THEN 1
                    WHEN ((p.settlement_model = 'RESULT_ONLY'::investory.position_settlement_model) AND ((NOT investory.fx_status_usable(profit_fx.conversion_status)) OR ((COALESCE(p.commission, (0)::numeric) <> (0)::numeric) AND (NOT investory.fx_status_usable(commission_fx.conversion_status))))) THEN 1
                    ELSE 0
                END AS missing_fx_count
           FROM (((((investory.positions p
             JOIN investory.accounts account ON ((account.id = p.account_id)))
             JOIN investory.assets asset ON (((asset.id = p.asset_id) AND (asset.exclude_from_import = false))))
             LEFT JOIN investory.app_v_portfolio_daily_fx_rate cost_fx ON (((cost_fx.portfolio_id = account.portfolio_id) AND (cost_fx.valuation_date = (p.close_time)::date) AND ((cost_fx.source_currency)::text = (p.cost_currency)::text))))
             LEFT JOIN investory.app_v_portfolio_daily_fx_rate profit_fx ON (((profit_fx.portfolio_id = account.portfolio_id) AND (profit_fx.valuation_date = (p.close_time)::date) AND ((profit_fx.source_currency)::text = (p.profit_currency)::text))))
             LEFT JOIN investory.app_v_portfolio_daily_fx_rate commission_fx ON (((commission_fx.portfolio_id = account.portfolio_id) AND (commission_fx.valuation_date = (p.close_time)::date) AND ((commission_fx.source_currency)::text = (p.commission_currency)::text))))
          WHERE (p.close_time IS NOT NULL)
        ), closed_group AS (
         SELECT cl.portfolio_id,
            cl.account_id,
            cl.asset_id,
            cl.symbol,
            cl.valuation_date,
            count(*) AS closed_lot_count,
            count(*) FILTER (WHERE ((cl.position_settlement_model)::text = 'CASH_SETTLED'::text)) AS cash_settled_lot_count,
            count(*) FILTER (WHERE ((cl.position_settlement_model)::text = 'RESULT_ONLY'::text)) AS result_only_lot_count,
            count(*) FILTER (WHERE ((cl.position_settlement_model)::text = 'UNCLASSIFIED'::text)) AS unclassified_lot_count,
            sum(cl.closed_quantity) AS closed_quantity,
            sum(cl.closed_quantity) FILTER (WHERE ((cl.position_settlement_model)::text = 'CASH_SETTLED'::text)) AS cash_settled_closed_quantity,
            sum(cl.close_notional_native) FILTER (WHERE ((cl.position_settlement_model)::text = 'CASH_SETTLED'::text)) AS position_close_notional_native,
                CASE
                    WHEN (count(*) FILTER (WHERE (((cl.position_settlement_model)::text = 'CASH_SETTLED'::text) AND (NOT investory.fx_status_usable(cl.cost_conversion_status)))) > 0) THEN NULL::numeric
                    ELSE sum((cl.close_notional_native * cl.cost_fx_rate_to_base)) FILTER (WHERE ((cl.position_settlement_model)::text = 'CASH_SETTLED'::text))
                END AS position_close_notional_base,
                CASE
                    WHEN (count(*) FILTER (WHERE (((cl.position_settlement_model)::text = 'RESULT_ONLY'::text) AND (cl.close_result_base IS NULL))) > 0) THEN NULL::numeric
                    ELSE sum(cl.close_result_base) FILTER (WHERE ((cl.position_settlement_model)::text = 'RESULT_ONLY'::text))
                END AS position_close_result_base,
            sum(cl.missing_fx_count) AS close_missing_fx_count
           FROM closed_lots cl
          GROUP BY cl.portfolio_id, cl.account_id, cl.asset_id, cl.symbol, cl.valuation_date
        ), opened_lots AS (
         SELECT p.account_id,
            p.asset_id,
            (p.open_time)::date AS valuation_date,
            (p.settlement_model)::character varying(32) AS position_settlement_model,
            abs(COALESCE(p.volume, (0)::numeric)) AS opened_quantity,
                CASE
                    WHEN (p.settlement_model = 'CASH_SETTLED'::investory.position_settlement_model) THEN COALESCE(p.purchase_value, (abs(COALESCE(p.volume, (0)::numeric)) * COALESCE(p.open_price, (0)::numeric)), (0)::numeric)
                    ELSE NULL::numeric
                END AS open_notional_native,
            fx.fx_rate_to_base,
            fx.conversion_status
           FROM (((investory.positions p
             JOIN investory.accounts account ON ((account.id = p.account_id)))
             JOIN closed_group target ON (((target.account_id = p.account_id) AND (target.asset_id = p.asset_id) AND (target.valuation_date = (p.open_time)::date))))
             LEFT JOIN investory.app_v_portfolio_daily_fx_rate fx ON (((fx.portfolio_id = account.portfolio_id) AND (fx.valuation_date = (p.open_time)::date) AND ((fx.source_currency)::text = (p.cost_currency)::text))))
        ), opened_group AS (
         SELECT ol.account_id,
            ol.asset_id,
            ol.valuation_date,
            count(*) AS opened_lot_count,
            sum(ol.opened_quantity) AS opened_quantity,
            sum(ol.opened_quantity) FILTER (WHERE ((ol.position_settlement_model)::text = 'CASH_SETTLED'::text)) AS cash_settled_opened_quantity,
                CASE
                    WHEN (count(*) FILTER (WHERE (((ol.position_settlement_model)::text = 'CASH_SETTLED'::text) AND (NOT investory.fx_status_usable(ol.conversion_status)))) > 0) THEN NULL::numeric
                    ELSE sum((ol.open_notional_native * ol.fx_rate_to_base)) FILTER (WHERE ((ol.position_settlement_model)::text = 'CASH_SETTLED'::text))
                END AS position_open_notional_base,
            count(*) FILTER (WHERE (((ol.position_settlement_model)::text = 'CASH_SETTLED'::text) AND (NOT investory.fx_status_usable(ol.conversion_status)))) AS open_missing_fx_count
           FROM opened_lots ol
          GROUP BY ol.account_id, ol.asset_id, ol.valuation_date
        ), ledger_rows AS (
         SELECT target.account_id,
            target.asset_id,
            target.valuation_date,
            co.id AS operation_id,
            (co.operation)::character varying(64) AS raw_operation,
            nco.normalized_category,
            co.amount,
            fx.fx_rate_to_base,
            fx.conversion_status
           FROM (((closed_group target
             LEFT JOIN investory.cash_operations co ON (((co.account_id = target.account_id) AND (co.asset_id = target.asset_id) AND ((co.date)::date = target.valuation_date) AND ((co.operation = ANY (ARRAY['STOCK_PURCHASE'::investory.cash_operation_type, 'STOCK_SELL'::investory.cash_operation_type, 'CLOSE_TRADE'::investory.cash_operation_type, 'ROLLOVER'::investory.cash_operation_type])) OR (EXISTS ( SELECT 1
                   FROM investory.app_v_normalized_cash_operations classified
                  WHERE ((classified.operation_id = co.id) AND ((classified.normalized_category)::text = 'BOND_REDEMPTION'::text))))))))
             LEFT JOIN investory.app_v_normalized_cash_operations nco ON ((nco.operation_id = co.id)))
             LEFT JOIN investory.app_v_portfolio_daily_fx_rate fx ON (((fx.portfolio_id = target.portfolio_id) AND (fx.valuation_date = target.valuation_date) AND ((fx.source_currency)::text = (co.currency)::text))))
        ), ledger_group AS (
         SELECT lr.account_id,
            lr.asset_id,
            lr.valuation_date,
            count(lr.operation_id) FILTER (WHERE (((lr.raw_operation)::text = 'STOCK_SELL'::text) OR ((lr.normalized_category)::text = 'BOND_REDEMPTION'::text))) AS ledger_sale_row_count,
            count(lr.operation_id) FILTER (WHERE ((lr.raw_operation)::text = ANY ((ARRAY['CLOSE_TRADE'::character varying, 'ROLLOVER'::character varying])::text[]))) AS ledger_close_result_row_count,
                CASE
                    WHEN (count(lr.operation_id) FILTER (WHERE ((((lr.raw_operation)::text = 'STOCK_SELL'::text) OR ((lr.normalized_category)::text = 'BOND_REDEMPTION'::text)) AND (NOT investory.fx_status_usable(lr.conversion_status)))) > 0) THEN NULL::numeric
                    ELSE sum((lr.amount * lr.fx_rate_to_base)) FILTER (WHERE (((lr.raw_operation)::text = 'STOCK_SELL'::text) OR ((lr.normalized_category)::text = 'BOND_REDEMPTION'::text)))
                END AS ledger_sale_cash_base,
                CASE
                    WHEN (count(lr.operation_id) FILTER (WHERE (((lr.raw_operation)::text = ANY ((ARRAY['CLOSE_TRADE'::character varying, 'ROLLOVER'::character varying])::text[])) AND (NOT investory.fx_status_usable(lr.conversion_status)))) > 0) THEN NULL::numeric
                    ELSE sum((lr.amount * lr.fx_rate_to_base)) FILTER (WHERE ((lr.raw_operation)::text = ANY ((ARRAY['CLOSE_TRADE'::character varying, 'ROLLOVER'::character varying])::text[])))
                END AS ledger_close_result_base,
                CASE
                    WHEN (count(lr.operation_id) FILTER (WHERE (((lr.raw_operation)::text = 'STOCK_PURCHASE'::text) AND (NOT investory.fx_status_usable(lr.conversion_status)))) > 0) THEN NULL::numeric
                    ELSE (- sum((lr.amount * lr.fx_rate_to_base)) FILTER (WHERE ((lr.raw_operation)::text = 'STOCK_PURCHASE'::text)))
                END AS ledger_purchase_cash_base,
            count(lr.operation_id) FILTER (WHERE (NOT investory.fx_status_usable(lr.conversion_status))) AS ledger_missing_fx_count
           FROM ledger_rows lr
          GROUP BY lr.account_id, lr.asset_id, lr.valuation_date
        ), previous_dates AS (
         SELECT target.account_id,
            target.asset_id,
            target.valuation_date,
            max(ad.snapshot_date) AS previous_valuation_date
           FROM (closed_group target
             LEFT JOIN investory.account_daily ad ON (((ad.account_id = target.account_id) AND (ad.snapshot_date < target.valuation_date))))
          GROUP BY target.account_id, target.asset_id, target.valuation_date
        ), settlement_prices AS (
         SELECT target.account_id,
            target.asset_id,
            target.valuation_date,
            price_slot.price_role,
            price.close_price,
            price.price_scale_factor,
            price.price_currency,
            price.quality_class
           FROM (((closed_group target
             LEFT JOIN previous_dates pd ON (((pd.account_id = target.account_id) AND (pd.asset_id = target.asset_id) AND (pd.valuation_date = target.valuation_date))))
             CROSS JOIN LATERAL ( VALUES ('PREVIOUS'::character varying(16),pd.previous_valuation_date), ('CURRENT'::character varying(16),target.valuation_date)) price_slot(price_role, price_date))
             LEFT JOIN LATERAL ( SELECT ndp.selected_price AS close_price,
                    (1)::numeric AS price_scale_factor,
                    ndp.price_currency,
                    ndp.quality_class
                   FROM investory.app_v_normalized_daily_price ndp
                  WHERE ((ndp.asset_id = target.asset_id) AND (ndp.valuation_date = price_slot.price_date))) price ON (true))
        ), symbol_values AS (
         SELECT target.account_id,
            target.asset_id,
            target.valuation_date,
            pd.previous_valuation_date,
            COALESCE(previous_quantity.open_quantity, (0)::numeric) AS previous_open_quantity,
                CASE
                    WHEN (COALESCE(previous_quantity.open_quantity, (0)::numeric) = (0)::numeric) THEN (0)::numeric
                    WHEN ((previous_price.close_price IS NULL) OR (NOT investory.fx_status_usable(previous_fx.conversion_status))) THEN NULL::numeric
                    ELSE ((((previous_quantity.open_quantity * previous_price.close_price) * COALESCE(previous_price.price_scale_factor, (1)::numeric)) *
                    CASE
                        WHEN ((previous_price.quality_class)::text ~~ '%PERCENT_OF_PAR%'::text) THEN 0.01
                        ELSE (1)::numeric
                    END) * previous_fx.fx_rate_to_base)
                END AS previous_symbol_market_value_base,
            (
                CASE
                    WHEN (COALESCE(previous_quantity.open_quantity, (0)::numeric) = (0)::numeric) THEN 'PASS'::text
                    WHEN ((previous_price.close_price IS NULL) OR (NOT investory.fx_status_usable(previous_fx.conversion_status))) THEN 'FAIL'::text
                    ELSE 'PASS'::text
                END)::character varying(16) AS previous_reconstruction_status,
            COALESCE(current_quantity.open_quantity, (0)::numeric) AS current_open_quantity,
                CASE
                    WHEN (COALESCE(current_quantity.open_quantity, (0)::numeric) = (0)::numeric) THEN (0)::numeric
                    WHEN ((current_price.close_price IS NULL) OR (NOT investory.fx_status_usable(current_fx.conversion_status))) THEN NULL::numeric
                    ELSE ((((current_quantity.open_quantity * current_price.close_price) * COALESCE(current_price.price_scale_factor, (1)::numeric)) *
                    CASE
                        WHEN ((current_price.quality_class)::text ~~ '%PERCENT_OF_PAR%'::text) THEN 0.01
                        ELSE (1)::numeric
                    END) * current_fx.fx_rate_to_base)
                END AS current_symbol_market_value_base,
            (
                CASE
                    WHEN (COALESCE(current_quantity.open_quantity, (0)::numeric) = (0)::numeric) THEN 'PASS'::text
                    WHEN ((current_price.close_price IS NULL) OR (NOT investory.fx_status_usable(current_fx.conversion_status))) THEN 'FAIL'::text
                    ELSE 'PASS'::text
                END)::character varying(16) AS current_reconstruction_status
           FROM (((((((closed_group target
             LEFT JOIN previous_dates pd ON (((pd.account_id = target.account_id) AND (pd.asset_id = target.asset_id) AND (pd.valuation_date = target.valuation_date))))
             LEFT JOIN LATERAL ( SELECT sum(investory.signed_position_quantity(p.operation, p.volume)) AS open_quantity
                   FROM investory.positions p
                  WHERE ((p.account_id = target.account_id) AND (p.asset_id = target.asset_id) AND (p.settlement_model = 'CASH_SETTLED'::investory.position_settlement_model) AND ((p.open_time)::date <= pd.previous_valuation_date) AND ((p.close_time IS NULL) OR (pd.previous_valuation_date < (p.close_time)::date)))) previous_quantity ON (true))
             LEFT JOIN settlement_prices previous_price ON (((previous_price.account_id = target.account_id) AND (previous_price.asset_id = target.asset_id) AND (previous_price.valuation_date = target.valuation_date) AND ((previous_price.price_role)::text = 'PREVIOUS'::text))))
             LEFT JOIN investory.app_v_portfolio_daily_fx_rate previous_fx ON (((previous_fx.portfolio_id = target.portfolio_id) AND (previous_fx.valuation_date = pd.previous_valuation_date) AND ((previous_fx.source_currency)::text = (previous_price.price_currency)::text))))
             LEFT JOIN LATERAL ( SELECT sum(investory.signed_position_quantity(p.operation, p.volume)) AS open_quantity
                   FROM investory.positions p
                  WHERE ((p.account_id = target.account_id) AND (p.asset_id = target.asset_id) AND (p.settlement_model = 'CASH_SETTLED'::investory.position_settlement_model) AND ((p.open_time)::date <= target.valuation_date) AND ((p.close_time IS NULL) OR (target.valuation_date < (p.close_time)::date)))) current_quantity ON (true))
             LEFT JOIN settlement_prices current_price ON (((current_price.account_id = target.account_id) AND (current_price.asset_id = target.asset_id) AND (current_price.valuation_date = target.valuation_date) AND ((current_price.price_role)::text = 'CURRENT'::text))))
             LEFT JOIN investory.app_v_portfolio_daily_fx_rate current_fx ON (((current_fx.portfolio_id = target.portfolio_id) AND (current_fx.valuation_date = target.valuation_date) AND ((current_fx.source_currency)::text = (current_price.price_currency)::text))))
        ), account_daily_with_previous AS (
         SELECT ad.id,
            ad.account_id,
            ad.snapshot_date,
            ad.valuation_currency,
            ad.cash_balance,
            ad.market_value,
            ad.equity,
            ad.cost_base,
            ad.unrealized_profit,
            ad.dividends,
            ad.interest,
            ad.fees,
            ad.taxes,
            ad.deposits,
            ad.withdrawals,
            ad.realized_profit,
            ad.daily_profit_amount,
            ad.daily_return_pct,
            ad.portfolio_weight,
            ad.created_at,
            ad.updated_at,
            lag(ad.cash_balance) OVER (PARTITION BY ad.account_id ORDER BY ad.snapshot_date) AS previous_cash_balance,
            lag(ad.market_value) OVER (PARTITION BY ad.account_id ORDER BY ad.snapshot_date) AS previous_market_value,
            lag(ad.equity) OVER (PARTITION BY ad.account_id ORDER BY ad.snapshot_date) AS previous_equity
           FROM investory.account_daily ad
        ), combined AS (
         SELECT cg.portfolio_id,
            cg.account_id,
            cg.asset_id,
            cg.symbol,
            cg.valuation_date,
            cg.closed_lot_count,
            cg.cash_settled_lot_count,
            cg.result_only_lot_count,
            cg.unclassified_lot_count,
            cg.closed_quantity,
            cg.cash_settled_closed_quantity,
            cg.position_close_notional_native,
            cg.position_close_notional_base,
            cg.position_close_result_base,
            cg.close_missing_fx_count,
            portfolio.base_currency,
            COALESCE(og.opened_lot_count, (0)::bigint) AS opened_lot_count,
            COALESCE(og.opened_quantity, (0)::numeric) AS opened_quantity,
            COALESCE(og.cash_settled_opened_quantity, (0)::numeric) AS cash_settled_opened_quantity,
            og.position_open_notional_base,
            COALESCE(og.open_missing_fx_count, (0)::bigint) AS open_missing_fx_count,
            COALESCE(lg.ledger_sale_row_count, (0)::bigint) AS ledger_sale_row_count,
            COALESCE(lg.ledger_close_result_row_count, (0)::bigint) AS ledger_close_result_row_count,
            lg.ledger_sale_cash_base,
            lg.ledger_close_result_base,
            lg.ledger_purchase_cash_base,
            COALESCE(lg.ledger_missing_fx_count, (0)::bigint) AS ledger_missing_fx_count,
            sv.previous_valuation_date,
            sv.previous_open_quantity,
            sv.previous_symbol_market_value_base,
            sv.previous_reconstruction_status,
            COALESCE(sv.current_open_quantity, (0)::numeric) AS current_open_quantity,
            COALESCE(sv.current_symbol_market_value_base, (0)::numeric) AS current_symbol_market_value_base,
            COALESCE(sv.current_reconstruction_status, 'PASS'::character varying) AS current_reconstruction_status,
            ad.previous_cash_balance,
            ad.cash_balance,
            ad.previous_market_value,
            ad.market_value,
            ad.previous_equity,
            ad.equity,
            ad.daily_profit_amount,
            (
                CASE
                    WHEN ((cg.result_only_lot_count > 0) AND (cg.cash_settled_lot_count > 0)) THEN 'MIXED'::text
                    WHEN (cg.result_only_lot_count > 0) THEN 'RESULT_ONLY'::text
                    WHEN (cg.unclassified_lot_count > 0) THEN 'UNCLASSIFIED'::text
                    WHEN (COALESCE(lg.ledger_sale_row_count, (0)::bigint) > 0) THEN 'CASH_SETTLED'::text
                    WHEN ((COALESCE(og.opened_quantity, (0)::numeric) > (0)::numeric) AND (abs((COALESCE(og.position_open_notional_base, (0)::numeric) - COALESCE(cg.position_close_notional_base, (0)::numeric))) <= (investory.reconciliation_parameter('reconciliation_reorganization_relative_threshold'::character varying) * GREATEST((1)::numeric, abs(COALESCE(cg.position_close_notional_base, (0)::numeric)))))) THEN 'REORGANIZATION'::text
                    ELSE 'UNCLASSIFIED'::text
                END)::character varying(32) AS settlement_model
           FROM (((((closed_group cg
             JOIN investory.portfolios portfolio ON ((portfolio.id = cg.portfolio_id)))
             LEFT JOIN opened_group og ON (((og.account_id = cg.account_id) AND (og.asset_id = cg.asset_id) AND (og.valuation_date = cg.valuation_date))))
             LEFT JOIN ledger_group lg ON (((lg.account_id = cg.account_id) AND (lg.asset_id = cg.asset_id) AND (lg.valuation_date = cg.valuation_date))))
             LEFT JOIN symbol_values sv ON (((sv.account_id = cg.account_id) AND (sv.asset_id = cg.asset_id) AND (sv.valuation_date = cg.valuation_date))))
             LEFT JOIN account_daily_with_previous ad ON (((ad.account_id = cg.account_id) AND (ad.snapshot_date = cg.valuation_date))))
        ), quantities AS (
         SELECT c.portfolio_id,
            c.account_id,
            c.asset_id,
            c.symbol,
            c.valuation_date,
            c.closed_lot_count,
            c.cash_settled_lot_count,
            c.result_only_lot_count,
            c.unclassified_lot_count,
            c.closed_quantity,
            c.cash_settled_closed_quantity,
            c.position_close_notional_native,
            c.position_close_notional_base,
            c.position_close_result_base,
            c.close_missing_fx_count,
            c.base_currency,
            c.opened_lot_count,
            c.opened_quantity,
            c.cash_settled_opened_quantity,
            c.position_open_notional_base,
            c.open_missing_fx_count,
            c.ledger_sale_row_count,
            c.ledger_close_result_row_count,
            c.ledger_sale_cash_base,
            c.ledger_close_result_base,
            c.ledger_purchase_cash_base,
            c.ledger_missing_fx_count,
            c.previous_valuation_date,
            c.previous_open_quantity,
            c.previous_symbol_market_value_base,
            c.previous_reconstruction_status,
            c.current_open_quantity,
            c.current_symbol_market_value_base,
            c.current_reconstruction_status,
            c.previous_cash_balance,
            c.cash_balance,
            c.previous_market_value,
            c.market_value,
            c.previous_equity,
            c.equity,
            c.daily_profit_amount,
            c.settlement_model,
            LEAST(COALESCE(c.cash_settled_closed_quantity, (0)::numeric), abs(COALESCE(c.previous_open_quantity, (0)::numeric))) AS carried_close_quantity,
            LEAST(GREATEST((COALESCE(c.cash_settled_closed_quantity, (0)::numeric) - abs(COALESCE(c.previous_open_quantity, (0)::numeric))), (0)::numeric), COALESCE(c.cash_settled_opened_quantity, (0)::numeric)) AS same_day_round_trip_quantity
           FROM combined c
        ), metrics AS (
         SELECT q.portfolio_id,
            q.account_id,
            q.asset_id,
            q.symbol,
            q.valuation_date,
            q.closed_lot_count,
            q.cash_settled_lot_count,
            q.result_only_lot_count,
            q.unclassified_lot_count,
            q.closed_quantity,
            q.cash_settled_closed_quantity,
            q.position_close_notional_native,
            q.position_close_notional_base,
            q.position_close_result_base,
            q.close_missing_fx_count,
            q.base_currency,
            q.opened_lot_count,
            q.opened_quantity,
            q.cash_settled_opened_quantity,
            q.position_open_notional_base,
            q.open_missing_fx_count,
            q.ledger_sale_row_count,
            q.ledger_close_result_row_count,
            q.ledger_sale_cash_base,
            q.ledger_close_result_base,
            q.ledger_purchase_cash_base,
            q.ledger_missing_fx_count,
            q.previous_valuation_date,
            q.previous_open_quantity,
            q.previous_symbol_market_value_base,
            q.previous_reconstruction_status,
            q.current_open_quantity,
            q.current_symbol_market_value_base,
            q.current_reconstruction_status,
            q.previous_cash_balance,
            q.cash_balance,
            q.previous_market_value,
            q.market_value,
            q.previous_equity,
            q.equity,
            q.daily_profit_amount,
            q.settlement_model,
            q.carried_close_quantity,
            q.same_day_round_trip_quantity,
            GREATEST(((COALESCE(q.cash_settled_closed_quantity, (0)::numeric) - q.carried_close_quantity) - q.same_day_round_trip_quantity), (0)::numeric) AS unmatched_close_quantity,
                CASE
                    WHEN (q.carried_close_quantity = (0)::numeric) THEN (0)::numeric
                    WHEN ((q.previous_symbol_market_value_base IS NULL) OR (abs(COALESCE(q.previous_open_quantity, (0)::numeric)) = (0)::numeric)) THEN NULL::numeric
                    ELSE ((q.previous_symbol_market_value_base * q.carried_close_quantity) / abs(q.previous_open_quantity))
                END AS allocated_previous_market_value_base,
                CASE
                    WHEN (q.carried_close_quantity = (0)::numeric) THEN (0)::numeric
                    WHEN ((q.ledger_sale_cash_base IS NULL) OR (COALESCE(q.cash_settled_closed_quantity, (0)::numeric) = (0)::numeric)) THEN NULL::numeric
                    ELSE ((q.ledger_sale_cash_base * q.carried_close_quantity) / q.cash_settled_closed_quantity)
                END AS carried_sale_cash_base,
                CASE
                    WHEN (q.same_day_round_trip_quantity = (0)::numeric) THEN (0)::numeric
                    WHEN ((q.ledger_sale_cash_base IS NULL) OR (COALESCE(q.cash_settled_closed_quantity, (0)::numeric) = (0)::numeric)) THEN NULL::numeric
                    ELSE ((q.ledger_sale_cash_base * q.same_day_round_trip_quantity) / q.cash_settled_closed_quantity)
                END AS same_day_sale_cash_base,
                CASE
                    WHEN (q.same_day_round_trip_quantity = (0)::numeric) THEN (0)::numeric
                    WHEN ((q.position_open_notional_base IS NULL) OR (q.cash_settled_opened_quantity = (0)::numeric)) THEN NULL::numeric
                    ELSE ((q.position_open_notional_base * q.same_day_round_trip_quantity) / q.cash_settled_opened_quantity)
                END AS same_day_closed_open_notional_base,
            (q.current_symbol_market_value_base - COALESCE(q.previous_symbol_market_value_base, (0)::numeric)) AS symbol_market_value_delta_base,
            (COALESCE(q.cash_balance, (0)::numeric) - COALESCE(q.previous_cash_balance, (0)::numeric)) AS account_cash_delta_base,
            (COALESCE(q.market_value, (0)::numeric) - COALESCE(q.previous_market_value, (0)::numeric)) AS account_market_value_delta_base,
            (COALESCE(q.equity, (0)::numeric) - COALESCE(q.previous_equity, (0)::numeric)) AS account_equity_delta_base,
            ((q.close_missing_fx_count + q.open_missing_fx_count) + q.ledger_missing_fx_count) AS missing_fx_count
           FROM quantities q
        ), reconciled AS (
         SELECT m.portfolio_id,
            m.account_id,
            m.asset_id,
            m.symbol,
            m.valuation_date,
            m.closed_lot_count,
            m.cash_settled_lot_count,
            m.result_only_lot_count,
            m.unclassified_lot_count,
            m.closed_quantity,
            m.cash_settled_closed_quantity,
            m.position_close_notional_native,
            m.position_close_notional_base,
            m.position_close_result_base,
            m.close_missing_fx_count,
            m.base_currency,
            m.opened_lot_count,
            m.opened_quantity,
            m.cash_settled_opened_quantity,
            m.position_open_notional_base,
            m.open_missing_fx_count,
            m.ledger_sale_row_count,
            m.ledger_close_result_row_count,
            m.ledger_sale_cash_base,
            m.ledger_close_result_base,
            m.ledger_purchase_cash_base,
            m.ledger_missing_fx_count,
            m.previous_valuation_date,
            m.previous_open_quantity,
            m.previous_symbol_market_value_base,
            m.previous_reconstruction_status,
            m.current_open_quantity,
            m.current_symbol_market_value_base,
            m.current_reconstruction_status,
            m.previous_cash_balance,
            m.cash_balance,
            m.previous_market_value,
            m.market_value,
            m.previous_equity,
            m.equity,
            m.daily_profit_amount,
            m.settlement_model,
            m.carried_close_quantity,
            m.same_day_round_trip_quantity,
            m.unmatched_close_quantity,
            m.allocated_previous_market_value_base,
            m.carried_sale_cash_base,
            m.same_day_sale_cash_base,
            m.same_day_closed_open_notional_base,
            m.symbol_market_value_delta_base,
            m.account_cash_delta_base,
            m.account_market_value_delta_base,
            m.account_equity_delta_base,
            m.missing_fx_count,
                CASE
                    WHEN ((m.ledger_sale_cash_base IS NULL) OR (m.position_close_notional_base IS NULL)) THEN NULL::numeric
                    ELSE (m.ledger_sale_cash_base - m.position_close_notional_base)
                END AS settlement_cash_difference_base,
                CASE
                    WHEN ((m.carried_sale_cash_base IS NULL) OR (m.allocated_previous_market_value_base IS NULL)) THEN NULL::numeric
                    ELSE (m.carried_sale_cash_base - m.allocated_previous_market_value_base)
                END AS sale_vs_previous_market_value_difference_base,
                CASE
                    WHEN ((m.allocated_previous_market_value_base IS NULL) OR (m.position_open_notional_base IS NULL) OR (m.same_day_closed_open_notional_base IS NULL)) THEN NULL::numeric
                    ELSE (m.symbol_market_value_delta_base - ((m.position_open_notional_base - m.same_day_closed_open_notional_base) - m.allocated_previous_market_value_base))
                END AS symbol_market_bridge_difference_base,
                CASE
                    WHEN ((m.position_close_result_base IS NULL) OR (m.ledger_close_result_base IS NULL)) THEN NULL::numeric
                    ELSE (m.ledger_close_result_base - m.position_close_result_base)
                END AS result_settlement_difference_base,
                CASE
                    WHEN ((m.settlement_model)::text = 'RESULT_ONLY'::text) THEN ((m.missing_fx_count = 0) AND (m.position_close_result_base IS NOT NULL) AND (m.ledger_close_result_base IS NOT NULL))
                    WHEN ((m.settlement_model)::text = 'MIXED'::text) THEN ((m.missing_fx_count = 0) AND (m.previous_valuation_date IS NOT NULL) AND ((COALESCE(m.previous_reconstruction_status, 'FAIL'::character varying))::text <> 'FAIL'::text) AND ((m.current_reconstruction_status)::text <> 'FAIL'::text) AND (m.position_close_result_base IS NOT NULL) AND (m.ledger_close_result_base IS NOT NULL))
                    ELSE ((m.missing_fx_count = 0) AND (m.previous_valuation_date IS NOT NULL) AND ((COALESCE(m.previous_reconstruction_status, 'FAIL'::character varying))::text <> 'FAIL'::text) AND ((m.current_reconstruction_status)::text <> 'FAIL'::text))
                END AS is_complete
           FROM metrics m
        )
 SELECT portfolio_id,
    account_id,
    asset_id,
    symbol,
    valuation_date,
    previous_valuation_date,
    base_currency,
    settlement_model,
    closed_lot_count,
    opened_lot_count,
    previous_open_quantity,
    closed_quantity,
    cash_settled_closed_quantity,
    opened_quantity,
    cash_settled_opened_quantity,
    carried_close_quantity,
    same_day_round_trip_quantity,
    unmatched_close_quantity,
    current_open_quantity,
    position_close_notional_native,
    position_close_notional_base,
    position_close_result_base,
    position_open_notional_base,
    ledger_sale_cash_base,
    carried_sale_cash_base,
    same_day_sale_cash_base,
    same_day_closed_open_notional_base,
    ledger_purchase_cash_base,
    ledger_close_result_base,
    previous_symbol_market_value_base,
    allocated_previous_market_value_base,
    current_symbol_market_value_base,
    symbol_market_value_delta_base,
    account_cash_delta_base,
    account_market_value_delta_base,
    account_equity_delta_base,
    daily_profit_amount AS reported_daily_profit_base,
    investory.reconciliation_display_value(settlement_cash_difference_base) AS settlement_cash_difference_base,
    investory.reconciliation_display_value(result_settlement_difference_base) AS result_settlement_difference_base,
    investory.reconciliation_display_value(sale_vs_previous_market_value_difference_base) AS sale_vs_previous_market_value_difference_base,
    investory.reconciliation_display_value(symbol_market_bridge_difference_base) AS symbol_market_bridge_difference_base,
    investory.reconciliation_display_value(investory.reconciliation_effective_tolerance(position_close_result_base, ledger_close_result_base)) AS result_effective_tolerance,
    investory.reconciliation_display_value(GREATEST(investory.reconciliation_parameter('reconciliation_trade_cash_absolute_tolerance'::character varying), (investory.reconciliation_parameter('reconciliation_trade_cash_relative_tolerance'::character varying) * abs(COALESCE(position_close_notional_base, (0)::numeric))))) AS settlement_cash_effective_tolerance,
    investory.reconciliation_display_value(GREATEST(investory.reconciliation_parameter('reconciliation_carrying_value_absolute_threshold'::character varying), (investory.reconciliation_parameter('reconciliation_carrying_value_relative_threshold'::character varying) * abs(COALESCE(allocated_previous_market_value_base, (0)::numeric))))) AS carrying_value_effective_threshold,
    investory.reconciliation_display_value(GREATEST(investory.reconciliation_parameter('reconciliation_market_bridge_absolute_threshold'::character varying), (investory.reconciliation_parameter('reconciliation_market_bridge_relative_threshold'::character varying) * abs(COALESCE(previous_symbol_market_value_base, (0)::numeric))))) AS market_bridge_effective_threshold,
    investory.reconciliation_display_value(GREATEST(investory.reconciliation_parameter('reconciliation_reorganization_absolute_threshold'::character varying), (investory.reconciliation_parameter('reconciliation_reorganization_relative_threshold'::character varying) * abs(COALESCE(previous_symbol_market_value_base, (0)::numeric))))) AS reorganization_effective_threshold,
    missing_fx_count,
    is_complete,
    (
        CASE
            WHEN (NOT is_complete) THEN 'INCOMPLETE'::text
            WHEN (((settlement_model)::text = 'RESULT_ONLY'::text) AND investory.reconciliation_values_match(position_close_result_base, ledger_close_result_base)) THEN 'PASS'::text
            WHEN (((settlement_model)::text = 'REORGANIZATION'::text) AND (abs(symbol_market_value_delta_base) <= GREATEST(investory.reconciliation_parameter('reconciliation_reorganization_absolute_threshold'::character varying), (investory.reconciliation_parameter('reconciliation_reorganization_relative_threshold'::character varying) * abs(COALESCE(previous_symbol_market_value_base, (0)::numeric)))))) THEN 'PASS'::text
            WHEN (((settlement_model)::text = 'CASH_SETTLED'::text) AND (abs(COALESCE(settlement_cash_difference_base, (0)::numeric)) <= GREATEST(investory.reconciliation_parameter('reconciliation_trade_cash_absolute_tolerance'::character varying), (investory.reconciliation_parameter('reconciliation_trade_cash_relative_tolerance'::character varying) * abs(COALESCE(position_close_notional_base, (0)::numeric))))) AND (abs(COALESCE(sale_vs_previous_market_value_difference_base, (0)::numeric)) <= GREATEST(investory.reconciliation_parameter('reconciliation_carrying_value_absolute_threshold'::character varying), (investory.reconciliation_parameter('reconciliation_carrying_value_relative_threshold'::character varying) * abs(COALESCE(allocated_previous_market_value_base, (0)::numeric))))) AND (abs(COALESCE(symbol_market_bridge_difference_base, (0)::numeric)) <= GREATEST(investory.reconciliation_parameter('reconciliation_market_bridge_absolute_threshold'::character varying), (investory.reconciliation_parameter('reconciliation_market_bridge_relative_threshold'::character varying) * abs(COALESCE(previous_symbol_market_value_base, (0)::numeric)))))) THEN 'PASS'::text
            ELSE 'REVIEW'::text
        END)::character varying(16) AS reconciliation_status,
    (
        CASE
            WHEN ((NOT is_complete) AND (missing_fx_count > 0)) THEN 'MISSING_FX'::text
            WHEN ((NOT is_complete) AND (previous_valuation_date IS NULL)) THEN 'MISSING_PREVIOUS_VALUATION'::text
            WHEN (NOT is_complete) THEN 'VALUATION_RECONSTRUCTION_FAILED'::text
            WHEN (((settlement_model)::text = 'RESULT_ONLY'::text) AND (NOT investory.reconciliation_values_match(position_close_result_base, ledger_close_result_base))) THEN 'RESULT_ONLY_CASH_MISMATCH'::text
            WHEN ((settlement_model)::text = 'RESULT_ONLY'::text) THEN 'OK'::text
            WHEN ((settlement_model)::text = 'MIXED'::text) THEN 'MIXED_SETTLEMENT_MODEL'::text
            WHEN ((settlement_model)::text = 'UNCLASSIFIED'::text) THEN 'UNCLASSIFIED_SETTLEMENT_MODEL'::text
            WHEN (((settlement_model)::text = 'REORGANIZATION'::text) AND (abs(symbol_market_value_delta_base) > GREATEST(investory.reconciliation_parameter('reconciliation_reorganization_absolute_threshold'::character varying), (investory.reconciliation_parameter('reconciliation_reorganization_relative_threshold'::character varying) * abs(COALESCE(previous_symbol_market_value_base, (0)::numeric)))))) THEN 'REORGANIZATION_VALUE_JUMP'::text
            WHEN ((settlement_model)::text = 'REORGANIZATION'::text) THEN 'OK'::text
            WHEN (unmatched_close_quantity > investory.reconciliation_parameter('reconciliation_quantity_tolerance'::character varying)) THEN 'UNMATCHED_CLOSE_QUANTITY'::text
            WHEN (abs(COALESCE(settlement_cash_difference_base, (0)::numeric)) > GREATEST(investory.reconciliation_parameter('reconciliation_trade_cash_absolute_tolerance'::character varying), (investory.reconciliation_parameter('reconciliation_trade_cash_relative_tolerance'::character varying) * abs(COALESCE(position_close_notional_base, (0)::numeric))))) THEN 'SALE_CASH_MISMATCH'::text
            WHEN (abs(COALESCE(sale_vs_previous_market_value_difference_base, (0)::numeric)) > GREATEST(investory.reconciliation_parameter('reconciliation_carrying_value_absolute_threshold'::character varying), (investory.reconciliation_parameter('reconciliation_carrying_value_relative_threshold'::character varying) * abs(COALESCE(allocated_previous_market_value_base, (0)::numeric))))) THEN 'SALE_VS_CARRYING_VALUE_OUTLIER'::text
            WHEN (abs(COALESCE(symbol_market_bridge_difference_base, (0)::numeric)) > GREATEST(investory.reconciliation_parameter('reconciliation_market_bridge_absolute_threshold'::character varying), (investory.reconciliation_parameter('reconciliation_market_bridge_relative_threshold'::character varying) * abs(COALESCE(previous_symbol_market_value_base, (0)::numeric))))) THEN 'MARKET_VALUE_BRIDGE_OUTLIER'::text
            ELSE 'OK'::text
        END)::character varying(64) AS anomaly_code
   FROM reconciled r;

CREATE VIEW investory.recon_v_trade_settlement_by_account AS
 SELECT r.portfolio_id,
    r.account_id,
    account.name AS account_name,
    account.provider,
    account.currency AS account_currency,
    r.base_currency,
    r.settlement_model,
    r.anomaly_code,
    count(*) AS reconciliation_row_count,
    count(DISTINCT r.asset_id) AS asset_count,
    count(DISTINCT r.symbol) AS symbol_count,
    min(r.valuation_date) AS first_valuation_date,
    max(r.valuation_date) AS last_valuation_date,
        CASE
            WHEN (count(*) FILTER (WHERE (r.position_close_notional_base IS NULL)) > 0) THEN NULL::numeric
            ELSE sum(r.position_close_notional_base)
        END AS position_close_notional_base,
    sum(r.position_close_notional_base) AS position_close_notional_converted_subtotal_base,
        CASE
            WHEN (count(*) FILTER (WHERE (((r.settlement_model)::text = 'RESULT_ONLY'::text) AND (r.ledger_close_result_base IS NULL))) > 0) THEN NULL::numeric
            ELSE sum(r.ledger_close_result_base)
        END AS ledger_close_result_base,
    sum(r.ledger_close_result_base) AS ledger_close_result_converted_subtotal_base,
    (sum(r.missing_fx_count))::bigint AS missing_fx_count,
    bool_and(r.is_complete) AS is_complete
   FROM (investory.recon_v_trade_settlement r
     JOIN investory.accounts account ON ((account.id = r.account_id)))
  GROUP BY r.portfolio_id, r.account_id, account.name, account.provider, account.currency, r.base_currency, r.settlement_model, r.anomaly_code;

CREATE VIEW investory.recon_v_validation_issue AS
 WITH equity_check AS (
         SELECT ad.account_id,
            ad.snapshot_date,
            'EQUITY_MISMATCH'::character varying(64) AS issue_type,
            'ERROR'::character varying(16) AS severity,
            NULL::bigint AS asset_id,
            NULL::bigint AS operation_id,
            (ad.cash_balance + ad.market_value) AS expected_value,
            (ad.equity)::numeric AS actual_value,
            ((('equity='::text || ad.equity) || ', cash+market='::text) || (ad.cash_balance + ad.market_value)) AS details
           FROM investory.account_daily ad
          WHERE (NOT investory.reconciliation_values_match((ad.cash_balance + ad.market_value), ad.equity))
        ), unrealized_check AS (
         SELECT ad.account_id,
            ad.snapshot_date,
            'UNREALIZED_MISMATCH'::character varying(64) AS issue_type,
            'ERROR'::character varying(16) AS severity,
            NULL::bigint AS asset_id,
            NULL::bigint AS operation_id,
            (ad.market_value - ad.cost_base) AS expected_value,
            (ad.unrealized_profit)::numeric AS actual_value,
            ((('unrealized='::text || ad.unrealized_profit) || ', market-cost='::text) || (ad.market_value - ad.cost_base)) AS details
           FROM investory.account_daily ad
          WHERE (NOT investory.reconciliation_values_match((ad.market_value - ad.cost_base), ad.unrealized_profit))
        ), duplicate_prices AS (
         SELECT NULL::bigint AS account_id,
            aph.price_date AS snapshot_date,
            'DUPLICATE_PRICE'::character varying(64) AS issue_type,
            'ERROR'::character varying(16) AS severity,
            aph.asset_id,
            NULL::bigint AS operation_id,
            (1)::numeric AS expected_value,
            (count(*))::numeric AS actual_value,
            ((('price rows='::text || count(*)) || ' for source='::text) || (aph.source)::text) AS details
           FROM (investory.asset_price_history aph
             JOIN investory.assets a ON (((a.id = aph.asset_id) AND (a.exclude_from_import = false))))
          GROUP BY aph.asset_id, a.symbol, aph.price_date, aph.source
         HAVING (count(*) > 1)
        ), duplicate_positions AS (
         SELECT p.account_id,
            COALESCE((COALESCE(p.close_time, p.open_time))::date, CURRENT_DATE) AS snapshot_date,
            'DUPLICATE_POSITION'::character varying(64) AS issue_type,
            'ERROR'::character varying(16) AS severity,
            p.asset_id,
            NULL::bigint AS operation_id,
            (1)::numeric AS expected_value,
            (count(*))::numeric AS actual_value,
            ('duplicate business-key positions='::text || count(*)) AS details
           FROM (investory.positions p
             JOIN investory.assets asset ON (((asset.id = p.asset_id) AND (asset.exclude_from_import = false))))
          GROUP BY p.account_id, p.asset_id, COALESCE((COALESCE(p.close_time, p.open_time))::date, CURRENT_DATE), p.operation, COALESCE(p.volume, (0)::numeric), p.price_currency, p.cost_currency, p.profit_currency, p.commission_currency, COALESCE(p.open_time, '-infinity'::timestamp with time zone), COALESCE(p.open_price, (0)::numeric), COALESCE(p.close_time, 'infinity'::timestamp with time zone), COALESCE(p.close_price, (0)::numeric), COALESCE(p.base_value, (0)::numeric), COALESCE(p.purchase_value, (0)::numeric), COALESCE(p.sale_value, (0)::numeric), COALESCE(p.margin, (0)::numeric), COALESCE(p.commission, (0)::numeric), COALESCE(p.swap, (0)::numeric), COALESCE(p.profit, (0)::numeric)
         HAVING (count(*) > 1)
        ), missing_price AS (
         SELECT DISTINCT p.account_id,
            d.snapshot_date,
            'MISSING_PRICE'::character varying(64) AS issue_type,
            'WARN'::character varying(16) AS severity,
            p.asset_id,
            NULL::bigint AS operation_id,
            NULL::numeric AS expected_value,
            NULL::numeric AS actual_value,
            'no canonical price row for open position date'::text AS details
           FROM (((investory.positions p
             JOIN investory.assets a ON (((a.id = p.asset_id) AND (a.exclude_from_import = false))))
             JOIN ( SELECT DISTINCT account_daily.account_id,
                    account_daily.snapshot_date
                   FROM investory.account_daily) d ON (((d.account_id = p.account_id) AND (d.snapshot_date >= (p.open_time)::date) AND ((p.close_time IS NULL) OR (d.snapshot_date <= (p.close_time)::date)))))
             LEFT JOIN LATERAL ( SELECT cap_1.price_date
                   FROM investory.app_v_canonical_asset_daily_price_mv cap_1
                  WHERE ((cap_1.asset_id = a.id) AND (cap_1.price_date <= d.snapshot_date))
                  ORDER BY cap_1.price_date DESC
                 LIMIT 1) cap ON (true))
          WHERE ((p.close_time IS NULL) AND ((cap.price_date IS NULL) OR (cap.price_date < (d.snapshot_date - '10 days'::interval))))
        ), valuation_jump AS (
         SELECT x.account_id,
            x.snapshot_date,
            'VALUATION_JUMP'::character varying(64) AS issue_type,
            'WARN'::character varying(16) AS severity,
            NULL::bigint AS asset_id,
            NULL::bigint AS operation_id,
            x.prev_market_value AS expected_value,
            (x.market_value)::numeric AS actual_value,
            ((((('market jump from '::text || x.prev_market_value) || ' to '::text) || x.market_value) || ', trade_notional='::text) || COALESCE(t.trade_notional, (0)::numeric)) AS details
           FROM (( SELECT ad.account_id,
                    ad.snapshot_date,
                    ad.market_value,
                    lag(ad.market_value) OVER (PARTITION BY ad.account_id ORDER BY ad.snapshot_date) AS prev_market_value
                   FROM investory.account_daily ad) x
             LEFT JOIN ( SELECT e.account_id,
                    e.snapshot_date,
                    sum(e.trade_notional) AS trade_notional
                   FROM (( SELECT p.account_id,
                            (p.open_time)::date AS snapshot_date,
                            COALESCE(p.purchase_value, (abs(COALESCE(p.volume, (0)::numeric)) * COALESCE(p.open_price, (0)::numeric)), (0)::numeric) AS trade_notional
                           FROM investory.positions p
                          WHERE (p.open_time IS NOT NULL)
                        UNION ALL
                         SELECT p.account_id,
                            (p.close_time)::date AS snapshot_date,
                            COALESCE(p.sale_value, (abs(COALESCE(p.volume, (0)::numeric)) * COALESCE(p.close_price, (0)::numeric)), (0)::numeric) AS trade_notional
                           FROM investory.positions p
                          WHERE (p.close_time IS NOT NULL)) e
                     JOIN ( SELECT DISTINCT account_daily.account_id,
                            account_daily.snapshot_date
                           FROM investory.account_daily) d ON (((d.account_id = e.account_id) AND (d.snapshot_date = e.snapshot_date))))
                  GROUP BY e.account_id, e.snapshot_date) t ON (((t.account_id = x.account_id) AND (t.snapshot_date = x.snapshot_date))))
          WHERE ((x.prev_market_value IS NOT NULL) AND (x.prev_market_value > (0)::numeric) AND (((x.market_value / x.prev_market_value) > investory.reconciliation_parameter('reconciliation_valuation_jump_upper_ratio'::character varying)) OR ((x.market_value / x.prev_market_value) < investory.reconciliation_parameter('reconciliation_valuation_jump_lower_ratio'::character varying))) AND (abs((x.market_value - x.prev_market_value)) > ((COALESCE(t.trade_notional, (0)::numeric) * investory.reconciliation_parameter('reconciliation_valuation_jump_trade_multiplier'::character varying)) + investory.reconciliation_parameter('reconciliation_valuation_jump_absolute_threshold'::character varying))))
        ), unclassified_cash AS (
         SELECT co.account_id,
            (co.date)::date AS snapshot_date,
            'UNCLASSIFIED_OPERATION'::character varying(64) AS issue_type,
            'WARN'::character varying(16) AS severity,
            co.asset_id,
            co.id AS operation_id,
            NULL::numeric AS expected_value,
            (co.amount)::numeric AS actual_value,
            ((('raw operation='::text || co.operation) || ', comment='::text) || (COALESCE(co.comment, ''::character varying))::text) AS details
           FROM investory.cash_operations co
          WHERE ((co.operation = 'UNKNOWN'::investory.cash_operation_type) AND (abs(co.amount) > (0)::numeric))
        )
 SELECT account_id,
    snapshot_date,
    issue_type,
    severity,
    asset_id,
    operation_id,
    expected_value,
    actual_value,
    details,
    now() AS created_at
   FROM ( SELECT equity_check.account_id,
            equity_check.snapshot_date,
            equity_check.issue_type,
            equity_check.severity,
            equity_check.asset_id,
            equity_check.operation_id,
            equity_check.expected_value,
            equity_check.actual_value,
            equity_check.details
           FROM equity_check
        UNION ALL
         SELECT unrealized_check.account_id,
            unrealized_check.snapshot_date,
            unrealized_check.issue_type,
            unrealized_check.severity,
            unrealized_check.asset_id,
            unrealized_check.operation_id,
            unrealized_check.expected_value,
            unrealized_check.actual_value,
            unrealized_check.details
           FROM unrealized_check
        UNION ALL
         SELECT duplicate_prices.account_id,
            duplicate_prices.snapshot_date,
            duplicate_prices.issue_type,
            duplicate_prices.severity,
            duplicate_prices.asset_id,
            duplicate_prices.operation_id,
            duplicate_prices.expected_value,
            duplicate_prices.actual_value,
            duplicate_prices.details
           FROM duplicate_prices
        UNION ALL
         SELECT duplicate_positions.account_id,
            duplicate_positions.snapshot_date,
            duplicate_positions.issue_type,
            duplicate_positions.severity,
            duplicate_positions.asset_id,
            duplicate_positions.operation_id,
            duplicate_positions.expected_value,
            duplicate_positions.actual_value,
            duplicate_positions.details
           FROM duplicate_positions
        UNION ALL
         SELECT missing_price.account_id,
            missing_price.snapshot_date,
            missing_price.issue_type,
            missing_price.severity,
            missing_price.asset_id,
            missing_price.operation_id,
            missing_price.expected_value,
            missing_price.actual_value,
            missing_price.details
           FROM missing_price
        UNION ALL
         SELECT valuation_jump.account_id,
            valuation_jump.snapshot_date,
            valuation_jump.issue_type,
            valuation_jump.severity,
            valuation_jump.asset_id,
            valuation_jump.operation_id,
            valuation_jump.expected_value,
            valuation_jump.actual_value,
            valuation_jump.details
           FROM valuation_jump
        UNION ALL
         SELECT unclassified_cash.account_id,
            unclassified_cash.snapshot_date,
            unclassified_cash.issue_type,
            unclassified_cash.severity,
            unclassified_cash.asset_id,
            unclassified_cash.operation_id,
            unclassified_cash.expected_value,
            unclassified_cash.actual_value,
            unclassified_cash.details
           FROM unclassified_cash) issues;

CREATE INDEX idx_recon_trade_settlement_status ON investory.recon_v_trade_settlement USING btree (reconciliation_status, anomaly_code, valuation_date DESC);

CREATE INDEX ix_recon_v_account_daily_reconciliation_mv_valuation_date ON investory.recon_v_account_daily_reconciliation_mv USING btree (valuation_date);

CREATE INDEX ix_recon_v_reconstructed_account_market_daily_mv_valuation_date ON investory.recon_v_reconstructed_account_market_daily_mv USING btree (valuation_date);

CREATE UNIQUE INDEX uq_recon_trade_settlement ON investory.recon_v_trade_settlement USING btree (account_id, asset_id, valuation_date);

CREATE UNIQUE INDEX ux_mv_recon_account_daily_cashflow_key ON investory.recon_v_account_daily_cashflow USING btree (account_id, snapshot_date);

CREATE UNIQUE INDEX ux_mv_recon_account_daily_cashflow_scope_key ON investory.recon_v_account_daily_cashflow_scope USING btree (account_id, snapshot_date);

CREATE UNIQUE INDEX ux_mv_recon_account_monthly_profit_key ON investory.recon_v_account_monthly_profit USING btree (account_id, month);

CREATE UNIQUE INDEX ux_mv_recon_account_stats_account ON investory.recon_v_account_statistics_vs_daily USING btree (account_id);

CREATE UNIQUE INDEX ux_recon_v_account_daily_reconciliation_mv_key ON investory.recon_v_account_daily_reconciliation_mv USING btree (account_id, valuation_date);

CREATE UNIQUE INDEX ux_recon_v_reconstructed_account_market_daily_mv_key ON investory.recon_v_reconstructed_account_market_daily_mv USING btree (account_id, valuation_date);
