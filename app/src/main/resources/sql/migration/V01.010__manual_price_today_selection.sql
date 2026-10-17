-- The canonical daily view normally chooses the highest-quality quote. A dated manual
-- override wins only on CURRENT_DATE; all earlier daily selections stay unchanged.
CREATE OR REPLACE VIEW investory.app_v_canonical_asset_daily_price AS
SELECT DISTINCT ON (aph.asset_id, aph.price_date)
       aph.asset_id, aph.price_date, aph.source, aph.source_symbol, aph.price_origin,
       aph.price_currency, aph.source_mapping_id, aph.interpolation_method,
       aph.interpolation_left_date, aph.interpolation_right_date, aph.open_price,
       aph.high_price, aph.low_price, aph.close_price, aph.adjusted_close_price,
       aph.volume, aph.estimated, aph.quality_score, aph.quality_class,
       aph.is_observed, aph.is_proxy, aph.price_scale_factor, aph.scale_reason,
       aph.original_source_symbol, aph.source_date, aph.imported_at
FROM investory.asset_price_history aph
JOIN investory.assets asset ON asset.id = aph.asset_id AND asset.exclude_from_import = false
ORDER BY aph.asset_id, aph.price_date,
         CASE WHEN aph.price_origin = 'MANUAL' AND aph.price_date = CURRENT_DATE THEN 0 ELSE 1 END,
         aph.quality_score DESC, aph.is_observed DESC, aph.is_proxy,
         CASE aph.price_origin WHEN 'XTB_TRADE_OPEN' THEN 0 WHEN 'XTB_TRADE_CLOSE' THEN 2 ELSE 1 END,
         aph.imported_at DESC, aph.source;
