CREATE OR REPLACE VIEW investory.recon_v_price_temporal_anomaly (
    severity, issue_code, entity_type, entity_id, entity_key, event_date, previous_date,
    next_date, gap_days, previous_value, current_value, next_value, change_pct, ratio,
    explanation, source, source_symbol, observation_currency, mapping_currency, scale_factor,
    mapping_scale_factor, price_origin, quality_class, is_proxy, requires_fx_conversion,
    is_exact_listing, is_alternate_listing
) AS
WITH observations AS (
    SELECT aph.asset_id, a.symbol AS asset_symbol, aph.source, aph.source_symbol,
           aph.price_date, aph.price_currency, aph.price_origin, aph.quality_class,
           aph.is_proxy, aph.price_scale_factor, aph.source_mapping_id,
           ass.price_currency AS mapping_currency,
           ass.price_scale_factor AS mapping_scale_factor,
           ass.requires_fx_conversion, ass.is_exact_listing, ass.is_alternate_listing,
           aph.close_price * aph.price_scale_factor AS normalized_price,
           LAG(aph.close_price * aph.price_scale_factor) OVER w AS previous_price,
           LAG(aph.price_date) OVER w AS previous_date,
           LAG(aph.price_currency) OVER w AS previous_currency,
           LEAD(aph.close_price * aph.price_scale_factor) OVER w AS next_price,
           LEAD(aph.price_date) OVER w AS next_date
    FROM investory.asset_price_history aph
    JOIN investory.assets a ON a.id = aph.asset_id AND NOT a.exclude_from_import
    LEFT JOIN investory.asset_source_symbols ass ON ass.id = aph.source_mapping_id
    WHERE aph.is_observed AND NOT aph.estimated AND aph.close_price > 0
      AND aph.quality_class IN (
          'EXACT_LISTING_MARKET_CLOSE',
          'VERIFIED_ALTERNATE_LISTING',
          'EXACT_LISTING_SCALED',
          'EXACT_LISTING_MARKET_CLOSE_PERCENT_OF_PAR',
          'MANUAL_ACCEPTED'
      )
    WINDOW w AS (PARTITION BY aph.asset_id, aph.source, aph.source_symbol ORDER BY aph.price_date)
), p AS (
    SELECT investory.reconciliation_parameter('reconciliation_temporal_short_gap_days')::integer AS short_gap,
           investory.reconciliation_parameter('reconciliation_price_extreme_move_ratio') AS extreme_move,
           investory.reconciliation_parameter('reconciliation_price_isolated_spike_ratio') AS spike_ratio,
           investory.reconciliation_parameter('reconciliation_temporal_neighbor_recovery_ratio') AS recovery,
           investory.reconciliation_parameter('reconciliation_price_scale_upper_ratio') AS scale_upper,
           investory.reconciliation_parameter('reconciliation_price_scale_lower_ratio') AS scale_lower,
           investory.reconciliation_parameter('reconciliation_price_scale_ten_upper_ratio') AS ten_upper,
           investory.reconciliation_parameter('reconciliation_price_scale_ten_lower_ratio') AS ten_lower
), short_moves AS (
    SELECT o.*, o.normalized_price / NULLIF(o.previous_price, 0) AS price_ratio,
           o.normalized_price / NULLIF(o.previous_price, 0) - 1 AS change_pct
    FROM observations o CROSS JOIN p
    WHERE o.previous_price > 0 AND o.price_date - o.previous_date <= p.short_gap
), isolated AS (
    SELECT s.* FROM short_moves s CROSS JOIN p
    WHERE s.next_price > 0 AND s.next_date - s.price_date <= p.short_gap
      AND ABS(s.price_ratio - 1) >= p.spike_ratio
      AND ABS(s.next_price / s.previous_price - 1) <= p.recovery
)
SELECT 'WARN'::varchar(16) AS severity, 'PRICE_EXTREME_MOVE'::varchar(64) AS issue_code,
       'ASSET'::varchar(16) AS entity_type, asset_id, asset_symbol::varchar(64) AS entity_key,
       price_date AS event_date, previous_date, next_date,
       (price_date - previous_date)::integer AS gap_days, previous_price AS previous_value,
       normalized_price AS current_value, next_price AS next_value, change_pct, price_ratio AS ratio,
       ('observed ' || asset_symbol || ' ' || source || '/' || source_symbol || ' moved from '
        || previous_price || ' to ' || normalized_price || '; origin=' || price_origin
        || ', quality=' || COALESCE(quality_class, 'n/a'))::text AS explanation,
       source, source_symbol, price_currency AS observation_currency,
       mapping_currency, price_scale_factor AS scale_factor, mapping_scale_factor,
       price_origin, quality_class, is_proxy, requires_fx_conversion,
       is_exact_listing, is_alternate_listing
FROM short_moves s CROSS JOIN p
WHERE ABS(s.change_pct) >= p.extreme_move
  AND NOT EXISTS (SELECT 1 FROM isolated i WHERE i.asset_id=s.asset_id AND i.source=s.source
                  AND i.source_symbol=s.source_symbol AND i.price_date=s.price_date)
UNION ALL
SELECT 'ERROR', 'PRICE_ISOLATED_SPIKE', 'ASSET', asset_id, asset_symbol, price_date,
       previous_date, next_date, (price_date - previous_date)::integer, previous_price,
       normalized_price, next_price, change_pct, price_ratio,
       ('isolated observed price spike; neighbors recover from ' || previous_price || ' to '
        || next_price || ', current=' || normalized_price)::text,
       source, source_symbol, price_currency, mapping_currency, price_scale_factor,
       mapping_scale_factor, price_origin, quality_class, is_proxy, requires_fx_conversion,
       is_exact_listing, is_alternate_listing
FROM isolated
UNION ALL
SELECT 'ERROR', 'PRICE_CURRENCY_MISMATCH', 'ASSET', asset_id, asset_symbol, price_date,
       previous_date, next_date, NULL, previous_price, normalized_price, next_price,
       NULL, NULL, ('observed currency ' || price_currency || ' differs from applicable '
        || 'provider/listing mapping currency ' || mapping_currency)::text,
       source, source_symbol, price_currency, mapping_currency, price_scale_factor,
       mapping_scale_factor, price_origin, quality_class, is_proxy, requires_fx_conversion,
       is_exact_listing, is_alternate_listing
FROM observations
WHERE mapping_currency IS NOT NULL AND price_currency IS DISTINCT FROM mapping_currency
UNION ALL
SELECT 'WARN', 'PRICE_CURRENCY_SWITCH', 'ASSET', asset_id, asset_symbol, price_date,
       previous_date, next_date, (price_date - previous_date)::integer, previous_price,
       normalized_price, next_price, NULL, normalized_price / NULLIF(previous_price, 0),
       ('provider/listing observation currency switched from ' || previous_currency || ' to '
        || price_currency)::text, source, source_symbol, price_currency, mapping_currency,
       price_scale_factor, mapping_scale_factor, price_origin, quality_class, is_proxy,
       requires_fx_conversion, is_exact_listing, is_alternate_listing
FROM observations
WHERE previous_currency IS NOT NULL AND price_currency IS DISTINCT FROM previous_currency
UNION ALL
SELECT 'ERROR', 'PRICE_SCALE_MISMATCH', 'ASSET', asset_id, asset_symbol, price_date,
       previous_date, next_date, NULL, previous_price, normalized_price, next_price, NULL, NULL,
       ('history scale ' || price_scale_factor || ' differs from mapping scale '
        || mapping_scale_factor)::text, source, source_symbol, price_currency, mapping_currency,
       price_scale_factor, mapping_scale_factor, price_origin, quality_class, is_proxy,
       requires_fx_conversion, is_exact_listing, is_alternate_listing
FROM observations
WHERE mapping_scale_factor IS NOT NULL AND price_scale_factor IS DISTINCT FROM mapping_scale_factor
UNION ALL
SELECT 'WARN', 'PRICE_SCALE_DISCONTINUITY', 'ASSET', asset_id, asset_symbol, price_date,
       previous_date, next_date, (price_date - previous_date)::integer, previous_price,
       normalized_price, next_price, normalized_price / NULLIF(previous_price, 0) - 1,
       normalized_price / NULLIF(previous_price, 0),
       ('observed normalized price ratio suggests a unit boundary; ratio='
        || (normalized_price / NULLIF(previous_price, 0)))::text,
       source, source_symbol, price_currency, mapping_currency, price_scale_factor,
       mapping_scale_factor, price_origin, quality_class, is_proxy, requires_fx_conversion,
       is_exact_listing, is_alternate_listing
FROM short_moves s CROSS JOIN p
WHERE s.price_ratio >= p.scale_upper OR s.price_ratio <= p.scale_lower
   OR (s.price_ratio BETWEEN p.ten_lower AND p.ten_upper)
   OR (1 / NULLIF(s.price_ratio, 0) BETWEEN p.ten_lower AND p.ten_upper);
