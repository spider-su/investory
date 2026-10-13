-- Incomplete valuation must not expose the surviving partial account subtotal.
SET search_path TO investory, public;

CREATE OR REPLACE VIEW investory.app_v_portfolio_kpi_summary AS
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

