create table if not exists market_radar_run (
    id uuid primary key,
    started_at timestamp with time zone not null,
    completed_at timestamp with time zone,
    status varchar(16) not null,
    universe_size integer not null,
    attempted integer not null default 0,
    stored integer not null default 0,
    no_data integer not null default 0,
    failed integer not null default 0,
    interesting integer not null default 0,
    normal_count integer not null default 0,
    emerging_count integer not null default 0,
    trending_count integer not null default 0,
    hot_count integer not null default 0,
    extended_count integer not null default 0,
    cooling_count integer not null default 0,
    outcomes_evaluated integer not null default 0,
    duration_ms bigint not null default 0,
    error text
);

create index if not exists idx_market_radar_run_started
    on market_radar_run (started_at desc);
