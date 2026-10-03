create table if not exists market_radar_snapshot (
    id bigserial primary key,
    symbol varchar(64) not null,
    observed_on date not null,
    state varchar(32) not null,
    close_price double precision not null,
    return_20d double precision,
    return_60d double precision,
    relative_volume_20d double precision,
    distance_sma50 double precision,
    rsi14 double precision,
    reasons text,
    created_at timestamp with time zone not null default current_timestamp,
    constraint uk_market_radar_snapshot_symbol_date unique (symbol, observed_on)
);

create index if not exists idx_market_radar_snapshot_observed
    on market_radar_snapshot (observed_on desc);
