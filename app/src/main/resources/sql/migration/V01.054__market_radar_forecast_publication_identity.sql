-- Distinct publications can reuse the same institutional URL.
alter table market_radar_forecast_observation drop constraint uk_market_radar_forecast_source_observation;
alter table market_radar_forecast_observation add constraint uk_market_radar_forecast_source_observation unique (source_id, institution_id, target_date, published_at);
