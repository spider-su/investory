create table if not exists market_radar_media_observation (
    id bigserial primary key,
    external_id varchar(512) not null,
    source_type varchar(32) not null,
    source varchar(128) not null,
    author varchar(256),
    headline text not null,
    url text,
    published_at timestamp with time zone not null,
    stance varchar(32) not null,
    created_at timestamp with time zone not null default current_timestamp,
    constraint uk_market_radar_media_external unique (external_id)
);

create table if not exists market_radar_media_symbol (
    observation_id bigint not null references market_radar_media_observation(id) on delete cascade,
    symbol varchar(64) not null,
    primary key (observation_id, symbol)
);

create index if not exists idx_market_radar_media_published
    on market_radar_media_observation (published_at desc);

create index if not exists idx_market_radar_media_symbol_symbol
    on market_radar_media_symbol (symbol);
