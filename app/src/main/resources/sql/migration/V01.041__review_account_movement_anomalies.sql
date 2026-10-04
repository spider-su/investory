CREATE TABLE investory.reconciliation_account_movement_reviews (
    id                  bigserial PRIMARY KEY,
    issue_code          varchar(64) NOT NULL
        CHECK (issue_code IN ('ACCOUNT_MARKET_VALUE_SPIKE', 'ACCOUNT_EQUITY_SPIKE')),
    account_id          bigint NOT NULL REFERENCES investory.accounts(id) ON DELETE RESTRICT,
    event_date          date NOT NULL,
    event_fingerprint   varchar(32) NOT NULL
        CHECK (event_fingerprint ~ '^[0-9a-f]{32}$'),
    resolution          varchar(32) NOT NULL
        CHECK (resolution IN ('EXPLAINED', 'CORRECTED', 'STILL_UNDER_REVIEW')),
    rationale           text NOT NULL CHECK (length(btrim(rationale)) > 0),
    evidence_reference text NOT NULL CHECK (length(btrim(evidence_reference)) > 0),
    reviewed_at         timestamptz NOT NULL DEFAULT now(),
    reviewed_by         varchar(128) NOT NULL
);

CREATE INDEX ix_reconciliation_account_movement_reviews_event
    ON investory.reconciliation_account_movement_reviews
       (issue_code, account_id, event_date, event_fingerprint, id DESC);

COMMENT ON TABLE investory.reconciliation_account_movement_reviews IS
    'Append-only manual dispositions for exact account movement anomaly observations. A disposition applies only while its event fingerprint matches.';

CREATE OR REPLACE VIEW investory.recon_v_account_temporal_anomaly_fingerprinted AS
SELECT t.severity, t.issue_code, t.entity_type, t.entity_id, t.entity_key,
       t.event_date, t.previous_date, t.next_date, t.gap_days,
       t.previous_value, t.current_value, t.next_value, t.change_pct, t.ratio,
       t.explanation, t.source, t.source_symbol, t.observation_currency,
       t.mapping_currency, t.scale_factor, t.mapping_scale_factor, t.price_origin,
       t.quality_class, t.is_proxy,
       md5(jsonb_build_array(
           t.issue_code, t.entity_id, t.event_date, t.previous_date,
           previous_snapshot.valuation_currency, previous_snapshot.cash_balance,
           previous_snapshot.market_value, previous_snapshot.equity, previous_snapshot.deposits,
           previous_snapshot.withdrawals, previous_snapshot.dividends, previous_snapshot.interest,
           previous_snapshot.fees, previous_snapshot.taxes, previous_snapshot.realized_profit,
           current_snapshot.valuation_currency, current_snapshot.cash_balance,
           current_snapshot.market_value, current_snapshot.equity, current_snapshot.deposits,
           current_snapshot.withdrawals, current_snapshot.dividends, current_snapshot.interest,
           current_snapshot.fees, current_snapshot.taxes, current_snapshot.realized_profit
       )::text) AS event_fingerprint
FROM investory.recon_v_account_temporal_anomaly t
JOIN investory.account_daily previous_snapshot
  ON previous_snapshot.account_id = t.entity_id
 AND previous_snapshot.snapshot_date = t.previous_date
JOIN investory.account_daily current_snapshot
  ON current_snapshot.account_id = t.entity_id
 AND current_snapshot.snapshot_date = t.event_date;

CREATE OR REPLACE VIEW investory.recon_v_temporal_anomaly (
    severity, issue_code, entity_type, entity_id, entity_key, event_date, previous_date,
    next_date, gap_days, previous_value, current_value, next_value, change_pct, ratio,
    explanation, source, source_symbol, observation_currency, mapping_currency, scale_factor,
    mapping_scale_factor, price_origin, quality_class, is_proxy
) AS
SELECT severity, issue_code, entity_type, entity_id, entity_key, event_date, previous_date,
       next_date, gap_days, previous_value, current_value, next_value, change_pct, ratio,
       explanation, source, source_symbol, observation_currency, NULL::varchar(3),
       scale_factor, NULL::numeric, NULL::varchar(32), NULL::varchar(64), is_proxy
FROM investory.recon_v_fx_temporal_anomaly
UNION ALL
SELECT p.severity, p.issue_code, p.entity_type, p.entity_id, p.entity_key, p.event_date,
       p.previous_date, p.next_date, p.gap_days, p.previous_value, p.current_value,
       p.next_value, p.change_pct, p.ratio, p.explanation, p.source, p.source_symbol,
       p.observation_currency, p.mapping_currency, p.scale_factor, p.mapping_scale_factor,
       p.price_origin, p.quality_class, p.is_proxy
FROM investory.recon_v_price_temporal_anomaly p
WHERE NOT EXISTS (
    SELECT 1
    FROM investory.reconciliation_price_anomaly_reviews r
    WHERE r.resolution IN (
        'CONFIRMED_MARKET_MOVE', 'SOURCE_PRICE_CORRECTED', 'MANUAL_ALTERNATE_LISTING_ACCEPTED'
    )
      AND r.issue_code = p.issue_code
      AND r.entity_id = p.entity_id
      AND r.event_date = p.event_date
      AND r.previous_date = p.previous_date
      AND r.source = p.source
      AND r.source_symbol = p.source_symbol
      AND r.previous_value = p.previous_value
      AND r.current_value = p.current_value
)
UNION ALL
SELECT t.severity, t.issue_code, t.entity_type, t.entity_id, t.entity_key, t.event_date,
       t.previous_date, t.next_date, t.gap_days, t.previous_value, t.current_value,
       t.next_value, t.change_pct, t.ratio, t.explanation, t.source, t.source_symbol,
       t.observation_currency, t.mapping_currency, t.scale_factor, t.mapping_scale_factor,
       t.price_origin, t.quality_class, t.is_proxy
FROM investory.recon_v_account_temporal_anomaly_fingerprinted t
WHERE NOT EXISTS (
    SELECT 1
    FROM investory.reconciliation_account_movement_reviews r
    WHERE r.resolution IN ('EXPLAINED', 'CORRECTED')
      AND r.issue_code = t.issue_code
      AND r.account_id = t.entity_id
      AND r.event_date = t.event_date
      AND r.event_fingerprint = t.event_fingerprint
      AND r.id = (
          SELECT latest.id
          FROM investory.reconciliation_account_movement_reviews latest
          WHERE latest.issue_code = r.issue_code
            AND latest.account_id = r.account_id
            AND latest.event_date = r.event_date
          ORDER BY latest.id DESC
          LIMIT 1
      )
);

COMMENT ON VIEW investory.recon_v_account_temporal_anomaly_fingerprinted IS
    'Account movement anomaly plus a fingerprint of the exact prior/current account_daily inputs used to produce it.';
