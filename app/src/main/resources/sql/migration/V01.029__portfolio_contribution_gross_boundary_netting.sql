SET search_path TO investory, public;

CREATE MATERIALIZED VIEW investory.portfolio_contribution_summary AS
WITH contribution_rows AS (
    SELECT
        p.id AS portfolio_id,
        p.base_currency,
        nco.portfolio_conversion_status,
        CASE
            WHEN nco.normalized_category = 'EXTERNAL_DEPOSIT' THEN 'EXTERNAL_DEPOSIT'
            WHEN nco.normalized_category = 'EXTERNAL_WITHDRAWAL' THEN 'EXTERNAL_WITHDRAWAL'
            WHEN nco.normalized_category = 'INTERNAL_BOOKKEEPING'
             AND nco.comment ~* 'transfer from [0-9]+ to [0-9]+'
             AND substring(nco.comment from '(?i)to ([0-9]+)')::bigint = nco.account_id
             AND nco.amount > 0
             AND NOT EXISTS (
                 SELECT 1
                 FROM investory.accounts counterparty
                 WHERE counterparty.id = substring(
                     nco.comment from '(?i)transfer from ([0-9]+)')::bigint
             ) THEN 'BOUNDARY_TRANSFER'
            WHEN nco.normalized_category = 'INTERNAL_BOOKKEEPING'
             AND nco.comment ~* 'transfer from [0-9]+ to [0-9]+'
             AND substring(nco.comment from '(?i)transfer from ([0-9]+)')::bigint = nco.account_id
             AND nco.amount < 0
             AND NOT EXISTS (
                 SELECT 1
                 FROM investory.accounts counterparty
                 WHERE counterparty.id = substring(
                     nco.comment from '(?i)to ([0-9]+)')::bigint
             ) THEN 'BOUNDARY_TRANSFER'
            ELSE NULL
        END AS contribution_kind,
        nco.amount_in_portfolio_base_currency AS amount_in_base_currency
    FROM investory.normalized_cash_operations nco
    JOIN investory.accounts account ON account.id = nco.account_id
    JOIN investory.portfolios p ON p.id = account.portfolio_id
), contribution_totals AS (
    SELECT
        portfolio_id,
        SUM(amount_in_base_currency) FILTER (
            WHERE contribution_kind = 'EXTERNAL_DEPOSIT') AS external_deposits,
        SUM(-amount_in_base_currency) FILTER (
            WHERE contribution_kind = 'EXTERNAL_WITHDRAWAL') AS external_withdrawals,
        SUM(amount_in_base_currency) FILTER (
            WHERE contribution_kind = 'BOUNDARY_TRANSFER') AS boundary_transfer_net,
        COUNT(*) FILTER (
            WHERE contribution_kind IS NOT NULL
              AND NOT investory.fx_status_usable(portfolio_conversion_status))::bigint
            AS missing_fx_count
    FROM contribution_rows
    GROUP BY portfolio_id
)
SELECT
    p.id AS portfolio_id,
    p.base_currency,
    CASE WHEN COALESCE(t.missing_fx_count, 0) > 0 THEN NULL::numeric ELSE
        COALESCE(t.external_deposits, 0)
            + GREATEST(COALESCE(t.boundary_transfer_net, 0), 0)
    END AS total_deposits,
    CASE WHEN COALESCE(t.missing_fx_count, 0) > 0 THEN NULL::numeric ELSE
        COALESCE(t.external_withdrawals, 0)
            + GREATEST(-COALESCE(t.boundary_transfer_net, 0), 0)
    END AS total_withdrawals,
    CASE WHEN COALESCE(t.missing_fx_count, 0) > 0 THEN NULL::numeric ELSE
        COALESCE(t.external_deposits, 0)
            - COALESCE(t.external_withdrawals, 0)
            + COALESCE(t.boundary_transfer_net, 0)
    END AS net_deposits,
    COALESCE(t.external_deposits, 0) AS external_deposits,
    COALESCE(t.external_withdrawals, 0) AS external_withdrawals,
    COALESCE(t.boundary_transfer_net, 0) AS boundary_transfer_net,
    COALESCE(t.missing_fx_count, 0) AS missing_fx_count,
    COALESCE(t.missing_fx_count, 0) = 0 AS is_complete,
    NOW() AS updated_at
FROM investory.portfolios p
LEFT JOIN contribution_totals t ON t.portfolio_id = p.id;

CREATE UNIQUE INDEX ux_mv_portfolio_contribution_summary_portfolio
    ON investory.portfolio_contribution_summary(portfolio_id);

COMMENT ON MATERIALIZED VIEW investory.portfolio_contribution_summary IS
    'Portfolio external contributions with boundary transfers netted before their signed net is assigned to deposits or withdrawals. Tracked-account transfers are excluded.';

CREATE OR REPLACE VIEW investory.app_v_portfolio_kpi_summary AS
SELECT
    src.portfolio_id,
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
    investory.application_display_value(src.converted_cash_subtotal) AS converted_cash_subtotal,
    investory.application_display_value(src.converted_equity_subtotal) AS converted_equity_subtotal,
    GREATEST(src.missing_fx_count, contributions.missing_fx_count) AS missing_fx_count,
    src.is_complete AND contributions.is_complete AS is_complete,
    src.activity_count,
    src.first_activity_at,
    src.last_activity_at,
    src.source_max_date,
    GREATEST(src.updated_at, contributions.updated_at) AS updated_at
FROM investory.portfolio_kpi_summary src
JOIN investory.portfolio_contribution_summary contributions
    ON contributions.portfolio_id = src.portfolio_id;

COMMENT ON VIEW investory.app_v_portfolio_kpi_summary IS
    'Application-facing KPI view. Contribution gross amounts use external flows plus the signed net of transfers crossing the tracked-account boundary.';

CREATE OR REPLACE FUNCTION investory.refresh_reporting_views()
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    started_at timestamptz := clock_timestamp();
    step_started timestamptz;
BEGIN
    step_started := clock_timestamp();
    REFRESH MATERIALIZED VIEW investory.account_monthly_mv;
    RAISE LOG 'investory refresh stage=account_monthly_mv elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    step_started := clock_timestamp();
    REFRESH MATERIALIZED VIEW investory.portfolio_monthly_mv;
    RAISE LOG 'investory refresh stage=portfolio_monthly_mv elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    step_started := clock_timestamp();
    REFRESH MATERIALIZED VIEW investory.account_statistics;
    RAISE LOG 'investory refresh stage=account_statistics elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    step_started := clock_timestamp();
    REFRESH MATERIALIZED VIEW investory.portfolio_contribution_summary;
    RAISE LOG 'investory refresh stage=portfolio_contribution_summary elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    step_started := clock_timestamp();
    REFRESH MATERIALIZED VIEW investory.portfolio_currency_breakdown;
    RAISE LOG 'investory refresh stage=portfolio_currency_breakdown elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    step_started := clock_timestamp();
    REFRESH MATERIALIZED VIEW investory.portfolio_asset_allocation;
    RAISE LOG 'investory refresh stage=portfolio_asset_allocation elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    step_started := clock_timestamp();
    REFRESH MATERIALIZED VIEW investory.symbol_performance;
    RAISE LOG 'investory refresh stage=symbol_performance elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    step_started := clock_timestamp();
    REFRESH MATERIALIZED VIEW investory.portfolio_kpi_summary;
    RAISE LOG 'investory refresh stage=portfolio_kpi_summary elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    RAISE LOG 'investory refresh stage=app_reporting_total elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - started_at);
END;
$$;
