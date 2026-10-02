ALTER TABLE investory.reconciliation_price_anomaly_reviews
    DROP CONSTRAINT reconciliation_price_anomaly_reviews_resolution_check;

ALTER TABLE investory.reconciliation_price_anomaly_reviews
    ADD CONSTRAINT reconciliation_price_anomaly_reviews_resolution_check
        CHECK (resolution IN (
            'CONFIRMED_MARKET_MOVE',
            'SOURCE_PRICE_CORRECTED',
            'MANUAL_ALTERNATE_LISTING_ACCEPTED'
        ));

CREATE OR REPLACE VIEW investory.recon_v_temporal_anomaly (
    severity, issue_code, entity_type, entity_id, entity_key, event_date, previous_date,
    next_date, gap_days, previous_value, current_value, next_value, change_pct, ratio,
    explanation, source, source_symbol, observation_currency, mapping_currency, scale_factor,
    mapping_scale_factor, price_origin, quality_class, is_proxy
) AS
SELECT severity, issue_code, entity_type, entity_id, entity_key, event_date, previous_date,
       next_date, gap_days, previous_value, current_value, next_value, change_pct, ratio,
       explanation, source, source_symbol, observation_currency, NULL::varchar(3) AS mapping_currency,
       scale_factor, NULL::numeric AS mapping_scale_factor, NULL::varchar(32) AS price_origin,
       NULL::varchar(64) AS quality_class, is_proxy
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
        'CONFIRMED_MARKET_MOVE',
        'SOURCE_PRICE_CORRECTED',
        'MANUAL_ALTERNATE_LISTING_ACCEPTED'
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
SELECT severity, issue_code, entity_type, entity_id, entity_key, event_date, previous_date,
       next_date, gap_days, previous_value, current_value, next_value, change_pct, ratio,
       explanation, source, source_symbol, observation_currency, mapping_currency, scale_factor,
       mapping_scale_factor, price_origin, quality_class, is_proxy
FROM investory.recon_v_account_temporal_anomaly;
