create table if not exists currencies (
    id              serial primary key,
    base            varchar(255) not null,
    to_currency     varchar(255) not null,
    rate            double precision not null
);

INSERT INTO currencies (base, to_currency, rate) VALUES
    ('USD', 'PLN', 4.0),
    ('USD', 'EUR', 0.8),
    ('USD', 'USD', 1.0),
    ('EUR', 'PLN', 4.3),
    ('EUR', 'EUR', 1.0),
    ('EUR', 'USD', 1.1),
    ('PLN', 'PLN', 1.0),
    ('PLN', 'EUR', 0.2),
    ('PLN', 'USD', 0.25);

create table if not exists portfolios (
    id           serial primary key,
    name         varchar(255),
    currency     varchar(255) not null,
    owner        varchar(255) not null
);

INSERT INTO portfolios (name, currency, owner) VALUES
    ('Alex Portfolio', 'USD', 'Alex & Olga Kotik');

create table if not exists accounts (
    id              serial primary key,
    account         varchar(255) not null,
    currency        varchar(255) not null,
    name            varchar(255) not null,
    owner           varchar(255) not null,
    portfolio_id    integer
);

INSERT INTO accounts (account, currency, name, owner, portfolio_id) VALUES
    ('51551301', 'PLN', 'IKE Alex',  'Alex Kotik', 1),
    ('50290466', 'PLN', 'Trading',   'Alex Kotik', 1),
    ('51499241', 'USD', 'Trading',   'Alex Kotik', 1),
    ('51548444', 'EUR', 'Trading',   'Alex Kotik', 1),
    ('51993106', 'USD', 'Dividends', 'Alex Kotik', 1),
    ('51707603', 'PLN', 'IKE Olga',  'Olga Kotik', 1),
    ('51822121', 'USD', 'Trading',   'Olga Kotik', 1);

create table if not exists cash_operations(
    id       serial primary key,
    account  varchar(255),
    type     varchar(255) not null, -- CashOperationType (ENUM)
    symbol   varchar(255),
    amount   double precision,
    currency varchar(255) not null, -- CurrencyType (ENUM)
    comment  varchar(2048),
    date     timestamptz  not null
);

create table if not exists closed_positions(
    id                serial primary key,
    account           varchar(255),
    symbol            varchar(255),
    type              varchar(255) not null, -- PositionType (ENUM)
    currency          varchar(255) not null, -- CurrencyType (ENUM)
    volume            double precision,
    open_time         timestamptz  not null,
    open_price        double precision,
    close_time        timestamptz  not null,
    close_price       double precision,
    purchase_value    double precision,
    sale_value        double precision,
    margin            double precision,
    commission        double precision,
    profit            double precision,
    comment           varchar(2048)
);

create table if not exists opened_positions(
    id                serial primary key,
    account           varchar(255),
    symbol            varchar(255),
    type              varchar(255) not null, -- PositionType (ENUM)
    currency          varchar(255) not null, -- CurrencyType (ENUM)
    volume            double precision,
    open_time         timestamptz  not null,
    open_price        double precision,
    market_price      double precision,
    purchase_value    double precision,
    swap              double precision,
    margin            double precision,
    commission        double precision,
    profit            double precision,
    comment           varchar(2048)
);

