-- Reference tables - the skeleton of the database for portfolio management and reporting
create table if not exists currencies (id varchar(3) primary key);
insert into currencies (id) values ('USD'), ('EUR'), ('PLN');
comment on table currencies is 'Reference table for currencies used in portfolios';

create table if not exists providers (id varchar(7) primary key);
insert into providers (id) values ('XTB'), ('IBKR')
on conflict (id) do nothing;

create table if not exists portfolios (
    id              bigserial primary key,
    name            varchar(255) not null,
    base_currency   varchar(3) not null references currencies(id),
    owner           varchar(255),
    created_at      timestamptz not null default now()
);
comment on table portfolios is 'Full portfolio of accounts, can be used for reporting and analysis';
comment on column portfolios.base_currency is 'Base currency of the portfolio, used for converting accounts currencies';

create table if not exists accounts (
    id              bigint primary key,
    currency        varchar(3) not null references currencies(id),
    provider        varchar(7) not null default 'XTB' references providers(id),
    name            varchar(255) not null,
    owner           varchar(255) not null,
    portfolio_id    bigint references portfolios(id),
    created_at      timestamptz not null default now()
);
comment on table accounts is 'Each account linked to a portfolio with own history, performance and statistics';
comment on column accounts.id is 'Unique real identifier for the account in the broker system, used for linking cash flows and positions';
comment on column accounts.portfolio_id is 'Reference to the portfolio this account belongs to';

create table if not exists assets(
    id              bigserial primary key,
    name            varchar(511) not null,
    symbol          varchar(15) not null,
    ticker          varchar(15) not null,
    ibkr            varchar(15) not null,
    yahoo           varchar(15) not null,
    country         varchar(15) not null,
    currency        varchar(3) not null references currencies(id),
    asset_type      varchar(20),
    active          boolean default true,
    market_price     numeric(20,8),
    market_price_usd numeric(20,8),
    price_source     varchar(255),
    price_updated_at timestamptz
);
create unique index if not exists ux_assets_symbol on assets(symbol);
comment on table assets is 'Stocks and ETFs as element of portfolio';
comment on column assets.symbol is 'Symbol used in the broker system, e.g. "AAPL.US" for Apple Inc. stock or "SPY.US" for SPDR S&P 500 ETF';
comment on column assets.ibkr is 'IBKR identifier for the asset';
comment on column assets.yahoo is 'Yahoo Finance identifier for the asset';
comment on column assets.country is 'Country used in the broker system, e.g. "US" for United States or "PL" for Poland';

create table if not exists asset_source_symbols (
    asset_id                  bigint not null references assets(id) on delete cascade,
    source                    varchar(32) not null,
    source_symbol             varchar(64) not null,
    source_market             varchar(32),
    price_currency            varchar(3) not null references currencies(id),
    active                    boolean not null default true,
    created_at                timestamptz not null default now(),
    updated_at                timestamptz not null default now(),
    xtb_symbol                varchar(64),
    match_method              varchar(64),
    match_status              varchar(64),
    confidence                varchar(32),
    is_exact_listing          boolean not null default false,
    is_alternate_listing      boolean not null default false,
    original_exchange         varchar(32),
    matched_exchange          varchar(32),
    original_currency         varchar(3) references currencies(id),
    matched_currency          varchar(3) references currencies(id),
    requires_fx_conversion    boolean not null default false,
    price_scale_factor        numeric(20,8) not null default 1,
    scale_reason              varchar(255),
    scale_confidence          varchar(32),
    scale_observation_count   integer,
    scale_median_ratio        numeric(20,8),
    scale_dispersion          numeric(20,8),
    manual_approval_status    varchar(32) not null default 'UNREVIEWED',
    substitution_reason       varchar(511),
    primary key (asset_id, source, source_symbol)
);
create index if not exists ix_asset_source_symbols_source_symbol
    on asset_source_symbols(source, source_symbol);
comment on table asset_source_symbols is 'Explicit deterministic mapping between internal assets and provider/source symbols used for historical price imports.';
comment on column asset_source_symbols.source_symbol is 'Provider-native symbol, for example STOOQ aapl.us or cdr.pl.';
comment on column asset_source_symbols.price_currency is 'Currency/unit used by the provider price file.';
comment on column asset_source_symbols.match_method is 'How the provider symbol was selected, for example EXACT_SYMBOL or MANUAL_ALTERNATE_LISTING.';
comment on column asset_source_symbols.match_status is 'Validation status of the provider symbol mapping.';
comment on column asset_source_symbols.is_alternate_listing is 'True when provider data is a reviewed alternate listing proxy rather than exact exchange history.';
comment on column asset_source_symbols.requires_fx_conversion is 'True when provider price currency differs from the imported asset/listing currency and valuation must use FX for the valuation date.';

create table if not exists asset_price_history (
    asset_id                 bigint not null references assets(id) on delete cascade,
    price_date               date not null,
    source                   varchar(32) not null,
    source_symbol            varchar(64) not null,
    price_origin             varchar(32) not null,
    price_currency           varchar(3) not null references currencies(id),
    open_price               numeric(20,8),
    high_price               numeric(20,8),
    low_price                numeric(20,8),
    close_price              numeric(20,8) not null,
    adjusted_close_price     numeric(20,8),
    volume                   numeric(24,8),
    estimated                boolean not null default false,
    interpolation_method     varchar(32),
    interpolation_left_date  date,
    interpolation_right_date date,
    observation_count        integer,
    source_date              date,
    imported_at              timestamptz not null default now(),
    quality_score            integer not null default 0,
    quality_class            varchar(64) not null default 'UNRESOLVED',
    is_observed              boolean not null default true,
    is_proxy                 boolean not null default false,
    price_scale_factor       numeric(20,8) not null default 1,
    scale_reason             varchar(255),
    original_source_symbol   varchar(64),
    primary key (asset_id, price_date, source),
    constraint chk_asset_price_history_close_positive
        check (close_price > 0),
    constraint chk_asset_price_history_ohlc
        check (
            (open_price is null or open_price > 0)
            and (high_price is null or high_price > 0)
            and (low_price is null or low_price > 0)
            and (adjusted_close_price is null or adjusted_close_price > 0)
            and (volume is null or volume >= 0)
            and (high_price is null or low_price is null or high_price >= low_price)
            and (high_price is null or open_price is null or high_price >= open_price)
            and (high_price is null or high_price >= close_price)
            and (low_price is null or open_price is null or low_price <= open_price)
            and (low_price is null or low_price <= close_price)
        ),
    constraint chk_asset_price_history_origin
        check (price_origin in (
            'STOOQ',
            'XTB_TRADE_OPEN',
            'XTB_TRADE_CLOSE',
            'INTERPOLATED_XTB'
        )),
    constraint chk_asset_price_history_estimation_metadata
        check (
            (
                estimated = false
                and interpolation_method is null
                and interpolation_left_date is null
                and interpolation_right_date is null
            )
            or
            (
                estimated = true
                and price_origin = 'INTERPOLATED_XTB'
                and interpolation_method is not null
                and interpolation_left_date is not null
                and interpolation_right_date is not null
                and interpolation_left_date < price_date
                and interpolation_right_date > price_date
            )
        )
);
create index if not exists ix_asset_price_history_asset_date
    on asset_price_history(asset_id, price_date);
create index if not exists ix_asset_price_history_date
    on asset_price_history(price_date);
create index if not exists ix_asset_price_history_source_symbol
    on asset_price_history(source, source_symbol);
create index if not exists ix_asset_price_history_estimated
    on asset_price_history(asset_id, price_date)
    where estimated;
create index if not exists ix_asset_price_history_quality
    on asset_price_history(asset_id, price_date, quality_score desc);
comment on table asset_price_history is 'Historical asset prices with explicit source provenance. Observed rows and interpolated estimates are intentionally stored separately.';
comment on column asset_price_history.source is 'Price source namespace such as STOOQ or XTB.';
comment on column asset_price_history.price_origin is 'Specific origin/provenance of the row. Interpolated rows are never represented as observed market data.';
comment on column asset_price_history.estimated is 'True only for generated estimates between two observed XTB trade-price checkpoints.';
comment on column asset_price_history.adjusted_close_price is 'Provider adjusted close when available. Stooq daily files in this import do not provide a separate adjusted close, so this is usually null.';
comment on column asset_price_history.quality_score is 'Relative source quality. Higher values should win over lower-quality generated rows.';
comment on column asset_price_history.quality_class is 'Quality/provenance class such as EXACT_LISTING_MARKET_CLOSE, EXACT_LISTING_SCALED, XTB_TRADE_OBSERVATION, or INTERPOLATED_XTB.';
comment on column asset_price_history.price_scale_factor is 'Scale applied to provider prices before writing close/open/high/low values.';

-- Summary tables - the main operation tables for portfolio management and reporting, used for calculations and analysis
create table if not exists exchange_rates (
    id              bigserial primary key,
    month           date not null,
    base            varchar(3) not null references currencies(id),
    to_currency     varchar(3) not null references currencies(id),
    rate            numeric(20,8) not null
);
create unique index if not exists ux_currencies_base_to_currency_month on exchange_rates (month, base, to_currency);
comment on table exchange_rates is 'Historical exchange rates for USD/EUR/PLN currencies, used for reporting and analysis';

CREATE TYPE cash_operation_type AS ENUM (
-- Cash movements
'DEPOSIT',
'WITHDRAWAL',
'TRANSFER',
'SUBACCOUNT_TRANSFER',

-- Trading
'STOCK_PURCHASE',
'STOCK_SELL',
'CLOSE_TRADE',
'CORRECTION',
'ROLLOVER',
'SWAP',

-- Income
'DIVIDEND',
'FREE_FUNDS_INTEREST',

-- Fees & Taxes
'COMMISSION',
'SEC_FEE',
'STAMP_DUTY',
'TRANSACTION_TAX',
'WITHHOLDING_TAX',
'FREE_FUNDS_INTEREST_TAX',

-- Fallback
'UNKNOWN'
);

create table if not exists import_history (
    id              bigserial primary key,
    provider        varchar(16) not null references providers(id),
    file_name       varchar(255),
    file_sha256     varchar(255) not null,
    started_at      timestamptz not null,
    finished_at     timestamptz,
    status          text,
    rows_total      integer,
    rows_failed     integer,
    error_message   text,
    source_type     text,
    source_ref      text,
    rows_applied    integer
);
create unique index if not exists ux_import_history_provider_sha256 on import_history (provider, file_sha256);
comment on table import_history is 'History of imports from providers to the system';

create table cash_operations (
    id           bigserial primary key,
    account_id   bigint references accounts(id) on delete cascade,
    operation    cash_operation_type not null,
    asset_id     varchar(15) references assets(symbol),
    amount       numeric(20,8) not null,
    currency     varchar(3) references currencies(id),
    comment      varchar(1023),
    date         timestamp with time zone not null
);
-- drop index if exists ux_cash_operations;
-- create unique index if not exists ux_cash_operations on cash_operations (account_id, date, asset_id);
comment on table cash_operations is 'All RAW historical records of cash operations imported from all portfolio providers';

CREATE TYPE positions_operation_type AS ENUM ('BUY', 'SELL');
create table if not exists positions (
    id              bigserial primary key,
    account_id      bigint references accounts(id) on delete cascade,
    asset_id        varchar(15) references assets(symbol),
    operation       positions_operation_type not null,
    volume          numeric(20,8),
    currency        varchar(3) references currencies(id),
    open_time       timestamptz,
    open_price      numeric(20,8),
    close_time      timestamptz,
    close_price     numeric(20,8),
    base_value      numeric(20,8),
    purchase_value  numeric(20,8),
    sale_value      numeric(20,8),
    margin          numeric(20,8),
    commission      numeric(20,8),
    swap            numeric(20,8),
    profit          numeric(20,8)
);
create unique index if not exists ux_positions_business_key
    on positions (
        account_id,
        asset_id,
        operation,
        coalesce(volume, 0),
        coalesce(currency, ''),
        coalesce(open_time, '-infinity'::timestamptz),
        coalesce(open_price, 0),
        coalesce(close_time, 'infinity'::timestamptz),
        coalesce(close_price, 0),
        coalesce(base_value, 0),
        coalesce(purchase_value, 0),
        coalesce(sale_value, 0),
        coalesce(margin, 0),
        coalesce(commission, 0),
        coalesce(swap, 0),
        coalesce(profit, 0)
    );
comment on table positions is 'Portfolio assets, both active and closed, used for calculating portfolio performance and reporting';
comment on column positions.close_time is 'The time when the position was closed, null if the position is still open';

create table if not exists account_statistics (
    account_id          bigint primary key references accounts(id) on delete cascade,
    total_deposit       numeric(20,8) not null default 0,
    total_withdrawal    numeric(20,8) not null default 0,
    net_deposit         numeric(20,8) not null default 0,
    cash_balance        numeric(20,8) not null default 0,
    market_value        numeric(20,8) not null default 0,
    cost_base           numeric(20,8) not null default 0,
    realized_profit     numeric(20,8) not null default 0,
    unrealized_profit   numeric(20,8) not null default 0,
    dividends           numeric(20,8) not null default 0,
    interest            numeric(20,8) not null default 0,
    fees                numeric(20,8) not null default 0,
    taxes               numeric(20,8) not null default 0,
    updated_at          timestamptz not null default now()
);
comment on table account_statistics is 'Computed account-level KPI aggregates refreshed by refresh_account_statistics(); monetary values are stored in USD.';

create table if not exists account_daily (
    id                  bigserial primary key,
    account_id          bigint not null references accounts(id) on delete cascade,
    date                date not null,
    cash_balance        numeric(20,8) not null default 0,
    market_value        numeric(20,8) not null default 0,
    equity              numeric(20,8) not null default 0,
    unrealized_profit   numeric(20,8) not null default 0,
    cost_base           numeric(20,8) not null default 0,
    realized_profit     numeric(20,8) not null default 0,
    dividends           numeric(20,8) not null default 0,
    interest            numeric(20,8) not null default 0,
    fees                numeric(20,8) not null default 0,
    taxes               numeric(20,8) not null default 0,
    deposits            numeric(20,8) not null default 0,
    withdrawals         numeric(20,8) not null default 0,
    daily_return        numeric(20,8),
    updated_at          timestamptz not null default now()
);
create unique index if not exists ux_account_daily_account_date
    on account_daily(account_id, date);

create table if not exists benchmark_monthly_closes (
    id              bigserial primary key,
    symbol          varchar(32) not null,
    month           date not null,
    close_price     numeric(20,8) not null,
    fetched_at      timestamptz not null default now()
);
create unique index if not exists ux_benchmark_monthly_closes_symbol_month
    on benchmark_monthly_closes(symbol, month);
comment on table benchmark_monthly_closes is 'Persistent cache for benchmark monthly close prices used by benchmark comparisons.';

DO $$
    BEGIN
        -- Exchange rates
        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'chk_exchange_rates_rate_positive'
        ) THEN
            ALTER TABLE exchange_rates
                ADD CONSTRAINT chk_exchange_rates_rate_positive
                    CHECK (rate > 0);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'chk_exchange_rates_month_first_day'
        ) THEN
            ALTER TABLE exchange_rates
                ADD CONSTRAINT chk_exchange_rates_month_first_day
                    CHECK (month = date_trunc('month', month)::date);
        END IF;

        -- Positions
        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'chk_positions_volume_non_negative'
        ) THEN
            ALTER TABLE positions
                ADD CONSTRAINT chk_positions_volume_non_negative
                    CHECK (volume >= 0);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'chk_positions_open_price_non_negative'
        ) THEN
            ALTER TABLE positions
                ADD CONSTRAINT chk_positions_open_price_non_negative
                    CHECK (open_price >= 0);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'chk_positions_close_price_non_negative'
        ) THEN
            ALTER TABLE positions
                ADD CONSTRAINT chk_positions_close_price_non_negative
                    CHECK (close_price IS NULL OR close_price >= 0);
        END IF;
    END
$$;
