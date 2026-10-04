create table if not exists market_radar_theme_snapshot (
    id bigserial primary key,
    theme varchar(128) not null,
    proxy_symbol varchar(32) not null,
    observed_on date not null,
    state varchar(32) not null,
    member_count integer not null,
    breadth_above_sma50 double precision not null,
    breadth_outperforming_benchmark double precision not null,
    proxy_return_20d double precision not null,
    benchmark_return_20d double precision not null,
    relative_strength_20d double precision not null,
    created_at timestamp with time zone not null default current_timestamp,
    constraint uk_market_radar_theme_snapshot unique (theme, observed_on)
);

create index if not exists idx_market_radar_theme_snapshot_observed
    on market_radar_theme_snapshot (observed_on desc);
