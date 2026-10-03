create table if not exists market_radar_outcome (
    id bigserial primary key,
    symbol varchar(64) not null,
    signal_date date not null,
    horizon_days integer not null,
    evaluated_on date not null,
    symbol_return double precision not null,
    benchmark varchar(64) not null,
    benchmark_return double precision,
    excess_return double precision,
    created_at timestamp with time zone not null default current_timestamp,
    constraint uk_market_radar_outcome unique (symbol, signal_date, horizon_days)
);

create index if not exists idx_market_radar_outcome_symbol_signal
    on market_radar_outcome (symbol, signal_date desc);
