-- Rebuild dependents around account statistics because PostgreSQL cannot replace a
-- materialized view while another view depends on it.
-- Known valued rows remain additive; missing valuation is reported by missing_fx_count.
DROP TABLE IF EXISTS _account_stats_dependent_defs;
DROP TABLE IF EXISTS _account_stats_dependent_mv_defs;
DROP TABLE IF EXISTS _account_stats_dependent_indexes;
CREATE TEMP TABLE _account_stats_dependent_defs AS
SELECT c.relname AS view_name, pg_get_viewdef(c.oid, true) AS view_definition,
       obj_description(c.oid, 'pg_class') AS view_comment, c.relkind
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'investory' AND c.relkind = 'v'
  AND c.relname IN ('app_v_portfolio_kpi_summary', 'recon_v_portfolio_service_fallback',
                    'recon_v_portfolio_account_quality', 'recon_v_portfolio_data_quality');
CREATE TEMP TABLE _account_stats_dependent_mv_defs AS
SELECT c.relname AS mv_name, pg_get_viewdef(c.oid, true) AS mv_definition,
       obj_description(c.oid, 'pg_class') AS mv_comment
FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'investory' AND c.relkind = 'm'
  AND c.relname = 'recon_v_account_statistics_vs_daily';
CREATE TEMP TABLE _account_stats_dependent_indexes AS
SELECT tablename, indexname, indexdef
FROM pg_indexes WHERE schemaname = 'investory'
  AND tablename IN ('recon_v_account_statistics_vs_daily');
DROP VIEW IF EXISTS investory.recon_v_portfolio_data_quality;
DROP VIEW IF EXISTS investory.recon_v_portfolio_account_quality;
DROP VIEW IF EXISTS investory.recon_v_portfolio_service_fallback;
DROP VIEW IF EXISTS investory.app_v_portfolio_kpi_summary;
DROP MATERIALIZED VIEW IF EXISTS investory.recon_v_account_statistics_vs_daily;
DROP MATERIALIZED VIEW IF EXISTS investory.app_v_portfolio_kpi_summary_mv;
DROP VIEW IF EXISTS investory.app_v_account_statistics_reporting;
DROP MATERIALIZED VIEW IF EXISTS investory.app_v_account_statistics;

CREATE MATERIALIZED VIEW investory.app_v_account_statistics AS
WITH latest_daily AS (
    SELECT DISTINCT ON (ad.account_id)
        ad.account_id, ad.snapshot_date, ad.valuation_currency, ad.cash_balance,
        ad.market_value, ad.equity, ad.cost_base, ad.unrealized_profit,
        ad.realized_profit, ad.daily_return_pct
    FROM investory.account_daily ad
    ORDER BY ad.account_id, ad.snapshot_date DESC, ad.id DESC
), latest_daily_in_base AS (
    SELECT ld.account_id, ld.snapshot_date,
        pf.base_currency::varchar(3) AS valuation_currency,
        CASE WHEN investory.fx_status_usable(fx.conversion_status) THEN ld.cash_balance * fx.fx_rate_to_base END AS cash_balance,
        (ld.cash_balance IS NOT NULL AND NOT investory.fx_status_usable(fx.conversion_status)) AS cash_fx_missing,
        CASE WHEN investory.fx_status_usable(fx.conversion_status) THEN ld.market_value * fx.fx_rate_to_base END AS market_value,
        (ld.market_value IS NOT NULL AND NOT investory.fx_status_usable(fx.conversion_status)) AS market_fx_missing,
        CASE WHEN investory.fx_status_usable(fx.conversion_status) THEN ld.equity * fx.fx_rate_to_base END AS equity,
        CASE WHEN investory.fx_status_usable(fx.conversion_status) THEN ld.cost_base * fx.fx_rate_to_base END AS cost_base,
        CASE WHEN investory.fx_status_usable(fx.conversion_status) THEN ld.unrealized_profit * fx.fx_rate_to_base END AS unrealized_profit,
        CASE WHEN investory.fx_status_usable(fx.conversion_status) THEN ld.realized_profit * fx.fx_rate_to_base END AS realized_profit,
        ld.daily_return_pct
    FROM latest_daily ld
    JOIN investory.accounts a ON a.id = ld.account_id
    JOIN investory.portfolios pf ON pf.id = a.portfolio_id
    LEFT JOIN investory.app_v_portfolio_daily_fx_rate_mv fx
      ON fx.portfolio_id = a.portfolio_id
     AND fx.valuation_date = ld.snapshot_date::date
     AND fx.source_currency = ld.valuation_currency::varchar(3)
     AND fx.base_currency = pf.base_currency::varchar(3)
), open_position_totals AS (
    SELECT value.account_id,
        COUNT(*)::bigint AS position_count,
        CASE WHEN COUNT(*) FILTER (WHERE value.cost_basis_in_base_currency IS NOT NULL) = 0 THEN NULL::numeric ELSE SUM(value.cost_basis_in_base_currency) FILTER (WHERE value.cost_basis_in_base_currency IS NOT NULL) END AS cost_base,
        CASE WHEN COUNT(*) FILTER (WHERE value.market_value_in_base_currency IS NOT NULL) = 0 THEN NULL::numeric ELSE SUM(value.market_value_in_base_currency) FILTER (WHERE value.market_value_in_base_currency IS NOT NULL) END AS market_value,
        CASE WHEN COUNT(*) FILTER (WHERE value.cost_basis_in_base_currency IS NOT NULL AND value.market_value_in_base_currency IS NOT NULL) = 0 THEN NULL::numeric ELSE SUM(value.market_value_in_base_currency - value.cost_basis_in_base_currency) FILTER (WHERE value.cost_basis_in_base_currency IS NOT NULL AND value.market_value_in_base_currency IS NOT NULL) END AS unrealized_profit,
        COUNT(*) FILTER (WHERE value.cost_basis_in_base_currency IS NULL OR value.market_value_in_base_currency IS NULL)::bigint AS missing_fx_count
    FROM investory.app_v_current_open_position_rows value
    GROUP BY value.account_id
), closed_position_components AS (
    SELECT a.id AS account_id, a.portfolio_id, p.close_time::date AS valuation_date,
        p.profit_currency::varchar(3) AS source_currency, pf.base_currency::varchar(3) AS base_currency,
        CASE WHEN p.settlement_model = 'RESULT_ONLY' THEN COALESCE(p.profit, 0) ELSE COALESCE(p.profit, 0) + COALESCE(p.swap, 0) END AS amount_native
    FROM investory.positions p
    JOIN investory.accounts a ON a.id = p.account_id
    JOIN investory.portfolios pf ON pf.id = a.portfolio_id
    WHERE p.close_time IS NOT NULL AND p.asset_id IS NOT NULL
    UNION ALL
    SELECT a.id AS account_id, a.portfolio_id, p.close_time::date AS valuation_date,
        p.commission_currency::varchar(3) AS source_currency, pf.base_currency::varchar(3) AS base_currency,
        CASE WHEN p.settlement_model = 'RESULT_ONLY' THEN 0 ELSE COALESCE(p.commission, 0) END AS amount_native
    FROM investory.positions p
    JOIN investory.accounts a ON a.id = p.account_id
    JOIN investory.portfolios pf ON pf.id = a.portfolio_id
    WHERE p.close_time IS NOT NULL AND p.asset_id IS NOT NULL
), closed_position_totals AS (
    SELECT c.account_id,
        CASE WHEN COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(fx.conversion_status)) > 0 THEN NULL::numeric ELSE SUM(c.amount_native * fx.fx_rate_to_target) END AS realized_profit,
        SUM(c.amount_native * fx.fx_rate_to_target) FILTER (WHERE investory.fx_status_usable(fx.conversion_status)) AS converted_subtotal,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(fx.conversion_status))::bigint AS missing_fx_count
    FROM closed_position_components c
    LEFT JOIN LATERAL investory.resolve_fx_rate(
        c.valuation_date,
        c.source_currency,
        c.base_currency
    ) fx ON true
    GROUP BY c.account_id
), portfolio_flow_rows AS (
    SELECT nco.*,
        CASE
            WHEN nco.normalized_category IN ('EXTERNAL_DEPOSIT', 'EXTERNAL_WITHDRAWAL') THEN nco.amount_in_portfolio_base_currency
            WHEN nco.normalized_category = 'INTERNAL_BOOKKEEPING' AND nco.comment ~* 'transfer from [0-9]+ to [0-9]+' AND substring(nco.comment from '(?i)to ([0-9]+)')::bigint = nco.account_id AND nco.amount > 0 AND NOT EXISTS (SELECT 1 FROM investory.accounts counterparty WHERE counterparty.id = substring(nco.comment from '(?i)transfer from ([0-9]+)')::bigint) THEN nco.amount_in_portfolio_base_currency
            WHEN nco.normalized_category = 'INTERNAL_BOOKKEEPING' AND nco.comment ~* 'transfer from [0-9]+ to [0-9]+' AND substring(nco.comment from '(?i)transfer from ([0-9]+)')::bigint = nco.account_id AND nco.amount < 0 AND NOT EXISTS (SELECT 1 FROM investory.accounts counterparty WHERE counterparty.id = substring(nco.comment from '(?i)to ([0-9]+)')::bigint) THEN nco.amount_in_portfolio_base_currency
            ELSE 0::numeric
        END AS scoped_portfolio_flow_amount_in_portfolio_base_currency
    FROM investory.app_v_normalized_cash_operations nco
), flow_totals AS (
    SELECT nco.account_id,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(nco.portfolio_conversion_status) OR NOT investory.fx_status_usable(nco.account_conversion_status))::bigint AS missing_fx_count,
        COUNT(*) FILTER (WHERE NOT investory.fx_status_usable(nco.account_conversion_status))::bigint AS account_missing_fx_count,
        SUM(nco.amount_in_portfolio_base_currency) FILTER (WHERE investory.fx_status_usable(nco.portfolio_conversion_status)) AS converted_subtotal,
        SUM(nco.scoped_portfolio_flow_amount_in_portfolio_base_currency) FILTER (WHERE nco.scoped_portfolio_flow_amount_in_portfolio_base_currency > 0) AS total_deposit,
        SUM(CASE WHEN nco.normalized_category = 'EXTERNAL_DEPOSIT' THEN nco.amount_in_account_currency ELSE NULL::numeric END) AS total_deposit_account_currency,
        SUM(-nco.scoped_portfolio_flow_amount_in_portfolio_base_currency) FILTER (WHERE nco.scoped_portfolio_flow_amount_in_portfolio_base_currency < 0) AS total_withdrawal,
        SUM(CASE WHEN nco.normalized_category = 'EXTERNAL_WITHDRAWAL' THEN ABS(nco.amount_in_account_currency) ELSE NULL::numeric END) AS total_withdrawal_account_currency,
        SUM(CASE WHEN nco.normalized_category IN ('DIVIDEND', 'DIVIDEND_REVERSAL') THEN nco.amount_in_base_currency END) AS dividends,
        SUM(CASE WHEN nco.normalized_category IN ('INTEREST', 'INTEREST_REVERSAL') THEN nco.amount_in_base_currency END) AS interest,
        SUM(CASE WHEN nco.normalized_category = 'FEE' THEN -nco.amount_in_base_currency END) AS fees,
        SUM(CASE WHEN nco.normalized_category IN ('WITHHOLDING_TAX', 'WITHHOLDING_TAX_REVERSAL', 'OTHER_TAX') THEN -nco.amount_in_base_currency END) AS taxes
    FROM portfolio_flow_rows nco GROUP BY nco.account_id
), activity_meta AS (
    SELECT ad.account_id,
        COUNT(*) FILTER (WHERE COALESCE(ad.deposits, 0) <> 0 OR COALESCE(ad.withdrawals, 0) <> 0 OR COALESCE(ad.dividends, 0) <> 0 OR COALESCE(ad.interest, 0) <> 0 OR COALESCE(ad.fees, 0) <> 0 OR COALESCE(ad.taxes, 0) <> 0 OR COALESCE(ad.realized_profit, 0) <> 0)::integer AS activity_count,
        MIN(ad.snapshot_date) FILTER (WHERE COALESCE(ad.deposits, 0) <> 0 OR COALESCE(ad.withdrawals, 0) <> 0 OR COALESCE(ad.dividends, 0) <> 0 OR COALESCE(ad.interest, 0) <> 0 OR COALESCE(ad.fees, 0) <> 0 OR COALESCE(ad.taxes, 0) <> 0 OR COALESCE(ad.realized_profit, 0) <> 0)::timestamptz AS first_activity_at,
        MAX(ad.snapshot_date) FILTER (WHERE COALESCE(ad.deposits, 0) <> 0 OR COALESCE(ad.withdrawals, 0) <> 0 OR COALESCE(ad.dividends, 0) <> 0 OR COALESCE(ad.interest, 0) <> 0 OR COALESCE(ad.fees, 0) <> 0 OR COALESCE(ad.taxes, 0) <> 0 OR COALESCE(ad.realized_profit, 0) <> 0)::timestamptz AS last_activity_at
    FROM investory.account_daily ad GROUP BY ad.account_id
)
SELECT a.id AS account_id, p.base_currency::varchar(3) AS valuation_currency,
    CASE WHEN COALESCE(ft.missing_fx_count, 0) > 0 THEN NULL ELSE COALESCE(ft.total_deposit, 0) END AS total_deposit,
    CASE WHEN COALESCE(ft.missing_fx_count, 0) > 0 THEN NULL ELSE COALESCE(ft.total_withdrawal, 0) END AS total_withdrawal,
    CASE WHEN COALESCE(ft.missing_fx_count, 0) > 0 THEN NULL ELSE COALESCE(ft.total_deposit, 0) - COALESCE(ft.total_withdrawal, 0) END AS net_deposit,
    CASE WHEN COALESCE(ft.account_missing_fx_count, 0) > 0 THEN NULL ELSE COALESCE(ft.total_deposit_account_currency, 0) - COALESCE(ft.total_withdrawal_account_currency, 0) END AS account_net_deposit,
    CASE WHEN COALESCE(ld.cash_fx_missing, false) THEN NULL ELSE COALESCE(ld.cash_balance, 0) END AS cash_balance,
    CASE WHEN COALESCE(ld.market_fx_missing, false) THEN NULL
         WHEN COALESCE(opt.position_count, 0) > 0 THEN opt.market_value
         ELSE COALESCE(ld.market_value, 0) END AS market_value,
    CASE WHEN COALESCE(ld.cash_fx_missing, false) THEN NULL
         WHEN COALESCE(opt.position_count, 0) > 0 THEN
             CASE WHEN opt.market_value IS NULL THEN NULL ELSE COALESCE(ld.cash_balance, 0) + opt.market_value END
         ELSE COALESCE(ld.equity, COALESCE(ld.cash_balance, 0)) END AS equity,
    CASE WHEN COALESCE(opt.position_count, 0) > 0 THEN opt.cost_base ELSE COALESCE(ld.cost_base, 0) END AS cost_base,
    CASE WHEN COALESCE(cpt.missing_fx_count, 0) > 0 THEN NULL ELSE COALESCE(cpt.realized_profit, 0) END AS realized_profit,
    CASE WHEN COALESCE(opt.position_count, 0) > 0 THEN opt.unrealized_profit ELSE COALESCE(ld.unrealized_profit, 0) END AS unrealized_profit,
    CASE WHEN COALESCE(ft.missing_fx_count, 0) > 0 THEN NULL ELSE COALESCE(ft.dividends, 0) END AS dividends,
    CASE WHEN COALESCE(ft.missing_fx_count, 0) > 0 THEN NULL ELSE COALESCE(ft.interest, 0) END AS interest,
    CASE WHEN COALESCE(ft.missing_fx_count, 0) > 0 THEN NULL ELSE COALESCE(ft.fees, 0) END AS fees,
    CASE WHEN COALESCE(ft.missing_fx_count, 0) > 0 THEN NULL ELSE COALESCE(ft.taxes, 0) END AS taxes,
    COALESCE(ft.converted_subtotal, 0) AS converted_cash_subtotal,
    COALESCE(ft.missing_fx_count, 0) + COALESCE(cpt.missing_fx_count, 0) + COALESCE(opt.missing_fx_count, 0)
        + CASE WHEN COALESCE(ld.cash_fx_missing, false) THEN 1 ELSE 0 END
        + CASE WHEN COALESCE(ld.market_fx_missing, false) THEN 1 ELSE 0 END AS missing_fx_count,
    COALESCE(ft.missing_fx_count, 0) = 0 AND COALESCE(cpt.missing_fx_count, 0) = 0
        AND COALESCE(opt.missing_fx_count, 0) = 0 AND NOT COALESCE(ld.cash_fx_missing, false)
        AND NOT COALESCE(ld.market_fx_missing, false) AS is_complete,
    COALESCE(am.activity_count, 0) AS activity_count, am.first_activity_at, am.last_activity_at,
    ld.snapshot_date AS latest_snapshot_date, ld.daily_return_pct AS latest_return_pct, NOW() AS updated_at
FROM investory.accounts a JOIN investory.portfolios p ON p.id = a.portfolio_id
LEFT JOIN latest_daily_in_base ld ON ld.account_id = a.id
LEFT JOIN open_position_totals opt ON opt.account_id = a.id
LEFT JOIN flow_totals ft ON ft.account_id = a.id
LEFT JOIN activity_meta am ON am.account_id = a.id
LEFT JOIN closed_position_totals cpt ON cpt.account_id = a.id
WITH DATA;

CREATE UNIQUE INDEX ux_mv_account_statistics_account ON investory.app_v_account_statistics(account_id);

CREATE OR REPLACE VIEW investory.app_v_account_statistics_reporting AS
SELECT s.*, a.cash_only,
       (abs(COALESCE(s.cash_balance, 0) + COALESCE(s.market_value, 0)) >= 50
        OR abs(COALESCE(s.account_net_deposit, 0)) >= 50
        OR abs(COALESCE(s.net_deposit, 0)) >= 50) AS is_visible
FROM investory.app_v_account_statistics s
JOIN investory.accounts a ON a.id = s.account_id;

COMMENT ON VIEW investory.app_v_account_statistics_reporting IS
  'Authoritative account reporting boundary. cash_only comes from accounts and visibility uses one 50-unit threshold rule.';

CREATE MATERIALIZED VIEW investory.app_v_portfolio_kpi_summary_mv AS
WITH latest_portfolio_daily AS (
    SELECT DISTINCT ON (pd.portfolio_id) pd.portfolio_id, pd.base_currency, pd.snapshot_date,
        pd.cash_balance, pd.market_value, pd.equity, pd.converted_equity_subtotal,
        pd.missing_fx_count, pd.is_complete
    FROM investory.app_v_portfolio_daily pd ORDER BY pd.portfolio_id, pd.snapshot_date DESC
), latest_account_stats AS (
    SELECT a.portfolio_id, SUM(ast.missing_fx_count)::bigint AS missing_fx_count,
        SUM(ast.converted_cash_subtotal) AS converted_cash_subtotal, SUM(ast.cash_balance) AS total_cash,
        SUM(ast.market_value) AS total_market_value, SUM(ast.equity) AS total_equity,
        SUM(ast.total_deposit) AS total_deposits, SUM(ast.total_withdrawal) AS total_withdrawals,
        SUM(ast.total_deposit - ast.total_withdrawal) AS net_deposits,
        SUM(ast.realized_profit) AS total_realized_profit, SUM(ast.unrealized_profit) AS total_unrealized_profit,
        SUM(ast.dividends) AS total_dividends, SUM(ast.interest) AS total_interest,
        SUM(ast.fees) AS total_fees, SUM(ast.taxes) AS total_taxes, SUM(ast.activity_count) AS activity_count,
        MIN(ast.first_activity_at) AS first_activity_at, MAX(ast.last_activity_at) AS last_activity_at
    FROM investory.app_v_account_statistics ast JOIN investory.accounts a ON a.id = ast.account_id
    GROUP BY a.portfolio_id
)
SELECT p.id AS portfolio_id, p.name AS portfolio_name, p.base_currency::varchar(3) AS base_currency,
    CASE WHEN COALESCE(las.missing_fx_count, 0) > 0 THEN NULL ELSE COALESCE(las.total_deposits, 0) END AS total_deposits,
    CASE WHEN COALESCE(las.missing_fx_count, 0) > 0 THEN NULL ELSE COALESCE(las.total_withdrawals, 0) END AS total_withdrawals,
    CASE WHEN COALESCE(las.missing_fx_count, 0) > 0 THEN NULL ELSE COALESCE(las.net_deposits, 0) END AS net_deposits,
    las.total_cash AS total_cash,
    las.total_market_value AS total_market_value,
    las.total_equity AS total_equity,
    las.total_realized_profit AS total_realized_profit,
    las.total_unrealized_profit AS total_unrealized_profit,
    las.total_dividends AS total_dividends,
    las.total_interest AS total_interest,
    las.total_fees AS total_fees,
    las.total_taxes AS total_taxes,
    COALESCE(las.converted_cash_subtotal, 0) AS converted_cash_subtotal, COALESCE(lpd.converted_equity_subtotal, 0) AS converted_equity_subtotal,
    COALESCE(las.missing_fx_count, 0) AS missing_fx_count, COALESCE(las.missing_fx_count, 0) = 0 AS is_complete,
    COALESCE(las.activity_count, 0) AS activity_count, las.first_activity_at, las.last_activity_at,
    lpd.snapshot_date AS source_max_date, NOW() AS updated_at
FROM investory.portfolios p LEFT JOIN latest_portfolio_daily lpd ON lpd.portfolio_id = p.id
LEFT JOIN latest_account_stats las ON las.portfolio_id = p.id
WITH DATA;

CREATE UNIQUE INDEX ux_app_v_portfolio_kpi_summary_mv_portfolio
    ON investory.app_v_portfolio_kpi_summary_mv(portfolio_id);

DO $$
DECLARE v record;
BEGIN
    FOR v IN SELECT * FROM _account_stats_dependent_defs WHERE view_name = 'app_v_portfolio_kpi_summary' LOOP
        EXECUTE 'CREATE VIEW investory.' || quote_ident(v.view_name) || ' AS ' || regexp_replace(v.view_definition, ';[[:space:]]*$', '');
        IF v.view_comment IS NOT NULL THEN EXECUTE 'COMMENT ON VIEW investory.' || quote_ident(v.view_name) || ' IS ' || quote_literal(v.view_comment); END IF;
    END LOOP;
    FOR v IN SELECT * FROM _account_stats_dependent_mv_defs LOOP
        EXECUTE 'CREATE MATERIALIZED VIEW investory.' || quote_ident(v.mv_name) || ' AS ' || regexp_replace(v.mv_definition, ';[[:space:]]*$', '') || ' WITH DATA';
        IF v.mv_comment IS NOT NULL THEN EXECUTE 'COMMENT ON MATERIALIZED VIEW investory.' || quote_ident(v.mv_name) || ' IS ' || quote_literal(v.mv_comment); END IF;
    END LOOP;
    FOR v IN SELECT * FROM _account_stats_dependent_defs
             WHERE view_name IN ('recon_v_portfolio_account_quality', 'recon_v_portfolio_data_quality', 'recon_v_portfolio_service_fallback')
             ORDER BY CASE view_name WHEN 'recon_v_portfolio_account_quality' THEN 1 WHEN 'recon_v_portfolio_data_quality' THEN 2 ELSE 3 END LOOP
        EXECUTE 'CREATE VIEW investory.' || quote_ident(v.view_name) || ' AS ' || regexp_replace(v.view_definition, ';[[:space:]]*$', '');
        IF v.view_comment IS NOT NULL THEN
            EXECUTE 'COMMENT ON VIEW investory.' || quote_ident(v.view_name) || ' IS ' || quote_literal(v.view_comment);
        END IF;
    END LOOP;
    FOR v IN SELECT * FROM _account_stats_dependent_indexes LOOP
        EXECUTE v.indexdef;
    END LOOP;
END
$$;
