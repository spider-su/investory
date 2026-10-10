-- A reused URL can publish a revised forecast under a new title.
-- Preserve the title captured for each publication without rewriting earlier migrations.
alter table market_radar_forecast_observation add column source_title varchar(512);
update market_radar_forecast_observation o
set source_title = s.source_title
from market_radar_forecast_source s
where s.id = o.source_id;
alter table market_radar_forecast_observation alter column source_title set not null;
