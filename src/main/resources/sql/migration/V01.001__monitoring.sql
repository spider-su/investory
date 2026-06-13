create table if not exists stocks(
    id           serial primary key,
    symbol       varchar(255),
    ticker       varchar(255),
    currency     varchar(255) not null, -- CurrencyType (ENUM)
    amount       double precision,
    open_price   double precision,
    market_price double precision,
    profit       double precision,
    updated_date timestamptz  not null,
    sync_date    timestamptz  not null
);

create table if not exists open_positions_history(
    id           serial primary key,
    symbol       varchar(255),
    currency     varchar(255) not null, -- CurrencyType (ENUM)
    amount       double precision,
    open_price   double precision,
    close_price  double precision,
    open_profit  double precision,
    close_profit double precision,
    date         timestamptz  not null
);

create table if not exists portfolio_history(
    id           serial primary key,
    portfolio_id integer,
    currency     varchar(255) not null, -- CurrencyType (ENUM)
    open_total   double precision,
    close_total  double precision,
    date         timestamptz  not null
);

create table if not exists fundamental_indicators (
    id            serial primary key,
    symbol        varchar(255) not null,
    pe_ratio      double precision,
    eps           double precision,
    dividend_yield double precision,
    sync_date     timestamptz not null
);

create table if not exists technical_indicators (
    id            serial primary key,
    symbol        varchar(255) not null,
    macd          double precision,
    rsi           double precision,
    volume        bigint,
    timestamp     timestamptz not null, -- When the data was valid
    sync_date     timestamptz not null  -- When we synced with the API
);
