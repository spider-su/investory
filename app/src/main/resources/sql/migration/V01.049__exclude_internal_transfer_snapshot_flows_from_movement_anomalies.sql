CREATE OR REPLACE VIEW investory.recon_v_account_temporal_anomaly (
    severity, issue_code, entity_type, entity_id, entity_key, event_date, previous_date,
    next_date, gap_days, previous_value, current_value, next_value, change_pct, ratio,
    explanation, source, source_symbol, observation_currency, mapping_currency, scale_factor,
    mapping_scale_factor, price_origin, quality_class, is_proxy, is_exact_listing,
    is_alternate_listing
) AS
WITH days AS (
    SELECT ad.*, LAG(ad.snapshot_date) OVER w AS previous_date,
           LAG(ad.equity) OVER w AS previous_equity,
           LAG(ad.market_value) OVER w AS previous_market_value
    FROM investory.account_daily ad
    WINDOW w AS (PARTITION BY ad.account_id ORDER BY ad.snapshot_date)
), internal_bookkeeping AS (
    SELECT nco.account_id,
           (nco.date AT TIME ZONE 'Europe/Warsaw')::date AS event_date,
           SUM(nco.amount_in_account_currency) FILTER (WHERE nco.amount_in_account_currency > 0)
               AS internal_deposits,
           SUM(ABS(nco.amount_in_account_currency))
               FILTER (WHERE nco.amount_in_account_currency < 0) AS internal_withdrawals
    FROM investory.app_v_normalized_cash_operations nco
    WHERE nco.normalized_category = 'INTERNAL_BOOKKEEPING'
    GROUP BY nco.account_id, (nco.date AT TIME ZONE 'Europe/Warsaw')::date
), movements AS (
    SELECT d.*,
           GREATEST(COALESCE(d.deposits, 0) - COALESCE(ib.internal_deposits, 0), 0)
             - GREATEST(COALESCE(d.withdrawals, 0) - COALESCE(ib.internal_withdrawals, 0), 0)
             + COALESCE(d.dividends, 0) + COALESCE(d.interest, 0)
             - COALESCE(d.fees, 0) - COALESCE(d.taxes, 0)
             + COALESCE(d.realized_profit, 0) AS known_change,
           d.equity - d.previous_equity
             - (GREATEST(COALESCE(d.deposits, 0) - COALESCE(ib.internal_deposits, 0), 0)
                - GREATEST(COALESCE(d.withdrawals, 0) - COALESCE(ib.internal_withdrawals, 0), 0)
                + COALESCE(d.dividends, 0) + COALESCE(d.interest, 0)
                - COALESCE(d.fees, 0) - COALESCE(d.taxes, 0)
                + COALESCE(d.realized_profit, 0)) AS unexplained_equity_change,
           d.market_value - d.previous_market_value AS unexplained_market_change
    FROM days d
    LEFT JOIN internal_bookkeeping ib
      ON ib.account_id = d.account_id AND ib.event_date = d.snapshot_date
), p AS (
    SELECT investory.reconciliation_parameter('reconciliation_temporal_short_gap_days')::integer AS short_gap,
           investory.reconciliation_parameter('reconciliation_account_unexplained_move_ratio') AS move_ratio
)
SELECT 'WARN'::varchar(16) AS severity,
       'ACCOUNT_EQUITY_SPIKE'::varchar(64) AS issue_code,
       'ACCOUNT'::varchar(16) AS entity_type, m.account_id AS entity_id,
       a.name::varchar(64) AS entity_key, m.snapshot_date AS event_date, m.previous_date,
       NULL::date AS next_date, (m.snapshot_date - m.previous_date)::integer AS gap_days,
       m.previous_equity AS previous_value, m.equity AS current_value, NULL::numeric AS next_value,
       m.unexplained_equity_change / GREATEST(ABS(m.previous_equity), ABS(m.equity), 1) AS change_pct,
       m.unexplained_equity_change / NULLIF(m.previous_equity, 0) AS ratio,
       ('unexplained equity change=' || m.unexplained_equity_change
        || ', unexplained market change=' || m.unexplained_market_change
        || ', known change after internal transfers=' || m.known_change
        || ', valuation currency=' || m.valuation_currency)::text,
       NULL::varchar(32) AS source, NULL::varchar(64) AS source_symbol,
       m.valuation_currency AS observation_currency, NULL::varchar(3) AS mapping_currency,
       NULL::numeric AS scale_factor, NULL::numeric AS mapping_scale_factor,
       NULL::varchar(32) AS price_origin, NULL::varchar(64) AS quality_class,
       NULL::boolean AS is_proxy, NULL::boolean AS is_exact_listing,
       NULL::boolean AS is_alternate_listing
FROM movements m
JOIN investory.accounts a ON a.id = m.account_id
CROSS JOIN p
WHERE m.previous_date IS NOT NULL
  AND m.snapshot_date - m.previous_date <= p.short_gap
  AND ABS(m.unexplained_equity_change)
      / GREATEST(ABS(m.previous_equity), ABS(m.equity), 1) >= p.move_ratio;
