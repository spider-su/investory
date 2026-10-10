create table if not exists market_radar_forecast_institution (
    id bigserial primary key,
    institution_key varchar(96) not null unique,
    display_name varchar(256) not null,
    active boolean not null default true
);

create table if not exists market_radar_forecast_source (
    id bigserial primary key,
    source_url text not null unique,
    source_title varchar(512) not null,
    source_type varchar(16) not null,
    constraint ck_market_radar_forecast_source_type
      check (source_type in ('PRIMARY', 'SECONDARY')),
    constraint ck_market_radar_forecast_source_http
      check (source_url ~ '^https?://[^[:space:]]+$')
);

create table if not exists market_radar_forecast_observation (
    id bigserial primary key,
    institution_id bigint not null references market_radar_forecast_institution(id),
    source_id bigint not null references market_radar_forecast_source(id),
    strategist varchar(256),
    published_at timestamp with time zone not null,
    target_date date not null,
    target_level numeric(14, 4) not null,
    observed_at timestamp with time zone not null default current_timestamp,
    supersedes_id bigint references market_radar_forecast_observation(id),
    quality varchar(16) not null default 'VERIFIED',
    status varchar(16) not null default 'ELIGIBLE',
    constraint ck_market_radar_forecast_target_positive check (target_level > 0),
    constraint ck_market_radar_forecast_dates check (target_date > (published_at at time zone 'UTC')::date),
    constraint ck_market_radar_forecast_quality check (quality in ('VERIFIED', 'SECONDARY')),
    constraint ck_market_radar_forecast_status check (status in ('ELIGIBLE')),
    constraint uk_market_radar_forecast_source_observation unique (source_id, institution_id, target_date)
);

create index if not exists idx_market_radar_forecast_horizon_history
    on market_radar_forecast_observation (target_date, published_at desc, observed_at desc, id desc);
