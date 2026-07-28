CREATE SCHEMA IF NOT EXISTS investory;

SET search_path TO investory, public;

-- Reference tables - the skeleton of the database for portfolio management and reporting
CREATE TABLE IF NOT EXISTS investory.currencies (
    id varchar(3) PRIMARY KEY,
    CONSTRAINT chk_currencies_id_uppercase_iso CHECK (id ~ '^[A-Z]{3}$')
);
INSERT INTO investory.currencies (id) VALUES ('USD'), ('EUR'), ('PLN')
ON CONFLICT (id) DO NOTHING;
COMMENT ON TABLE investory.currencies IS 'Reference table for currencies used in portfolios';

CREATE TABLE IF NOT EXISTS investory.providers (
    id varchar(7) PRIMARY KEY,
    CONSTRAINT chk_providers_id_uppercase CHECK (id ~ '^[A-Z0-9_]{2,7}$')
);
INSERT INTO investory.providers (id) VALUES ('XTB'), ('IBKR')
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS investory.asset_types (
    id varchar(32) PRIMARY KEY,
    CONSTRAINT chk_asset_types_id_uppercase CHECK (id ~ '^[A-Z_]+$')
);
INSERT INTO investory.asset_types (id) VALUES
    ('EQUITY'),
    ('ETF'),
    ('BOND'),
    ('CRYPTOCURRENCY'),
    ('COMMODITY'),
    ('CASH'),
    ('FUND'),
    ('REIT'),
    ('DERIVATIVE'),
    ('INDEX'),
    ('OTHER')
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS investory.portfolios (
    id              bigserial PRIMARY KEY,
    name            varchar(255) NOT NULL,
    base_currency   varchar(3) NOT NULL REFERENCES investory.currencies(id),
    owner           varchar(255),
    created_at      timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE investory.portfolios IS 'Full portfolio of accounts, can be used for reporting and analysis';
COMMENT ON COLUMN investory.portfolios.base_currency IS 'Base currency of the portfolio, used for converting accounts currencies';

CREATE TABLE IF NOT EXISTS investory.accounts (
    id              bigint PRIMARY KEY,
    currency        varchar(3) NOT NULL REFERENCES investory.currencies(id),
    provider        varchar(7) NOT NULL DEFAULT 'XTB' REFERENCES investory.providers(id),
    name            varchar(255) NOT NULL,
    owner           varchar(255) NOT NULL,
    portfolio_id    bigint NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    created_at      timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE investory.accounts IS 'Each account linked to a portfolio with own history, performance and statistics';
COMMENT ON COLUMN investory.accounts.id IS 'Unique real identifier for the account in the broker system, used for linking cash flows and positions';
COMMENT ON COLUMN investory.accounts.portfolio_id IS 'Reference to the portfolio this account belongs to';

CREATE TABLE IF NOT EXISTS investory.assets(
    id               bigserial PRIMARY KEY,
    name             varchar(511) NOT NULL,
    symbol           varchar(64) NOT NULL,
    ticker           varchar(64) NOT NULL,
    ibkr             varchar(64) NOT NULL,
    yahoo            varchar(64) NOT NULL,
    country          varchar(15) NOT NULL,
    currency         varchar(3) NOT NULL REFERENCES investory.currencies(id),
    asset_type       varchar(32) NOT NULL REFERENCES investory.asset_types(id),
    isin             varchar(12),
    figi             varchar(16),
    exchange_mic     varchar(4),
    active           boolean DEFAULT true,
    market_price     numeric(20,8),
    market_price_usd numeric(20,8),
    price_source     varchar(255),
    price_updated_at timestamptz
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_assets_symbol ON investory.assets(symbol);
COMMENT ON TABLE investory.assets IS 'Stocks and ETFs as element of portfolio';
COMMENT ON COLUMN investory.assets.symbol IS 'Symbol used in the broker system, e.g. "AAPL.US" for Apple Inc. stock or "SPY.US" for SPDR S&P 500 ETF';
COMMENT ON COLUMN investory.assets.ibkr IS 'IBKR identifier for the asset';
COMMENT ON COLUMN investory.assets.yahoo IS 'Yahoo Finance identifier for the asset';
COMMENT ON COLUMN investory.assets.country IS 'Country used in the broker system, e.g. "US" for United States or "PL" for Poland';

CREATE TABLE IF NOT EXISTS investory.asset_source_symbols (
    asset_id                  bigint NOT NULL REFERENCES investory.assets(id) ON DELETE CASCADE,
    source                    varchar(32) NOT NULL,
    source_symbol             varchar(64) NOT NULL,
    source_market             varchar(32),
    price_currency            varchar(3) NOT NULL REFERENCES investory.currencies(id),
    active                    boolean NOT NULL DEFAULT true,
    created_at                timestamptz NOT NULL DEFAULT now(),
    updated_at                timestamptz NOT NULL DEFAULT now(),
    xtb_symbol                varchar(64),
    match_method              varchar(64),
    match_status              varchar(64),
    confidence                varchar(32),
    is_exact_listing          boolean NOT NULL DEFAULT false,
    is_alternate_listing      boolean NOT NULL DEFAULT false,
    original_exchange         varchar(32),
    matched_exchange          varchar(32),
    original_currency         varchar(3) REFERENCES investory.currencies(id),
    matched_currency          varchar(3) REFERENCES investory.currencies(id),
    requires_fx_conversion    boolean NOT NULL DEFAULT false,
    price_scale_factor        numeric(20,8) NOT NULL DEFAULT 1,
    scale_reason              varchar(255),
    scale_confidence          varchar(32),
    scale_observation_count   integer,
    scale_median_ratio        numeric(20,8),
    scale_dispersion          numeric(20,8),
    manual_approval_status    varchar(32) NOT NULL DEFAULT 'UNREVIEWED',
    substitution_reason       varchar(511),
    PRIMARY KEY (asset_id, source, source_symbol),
    CONSTRAINT chk_asset_source_symbols_price_scale_factor_positive
        CHECK (price_scale_factor > 0),
    CONSTRAINT chk_asset_source_symbols_scale_observation_count_non_negative
        CHECK (scale_observation_count IS NULL OR scale_observation_count >= 0)
);
CREATE INDEX IF NOT EXISTS ix_asset_source_symbols_source_symbol ON investory.asset_source_symbols(source, source_symbol);
COMMENT ON TABLE investory.asset_source_symbols IS 'Explicit deterministic mapping between internal assets and provider/source symbols used for historical price imports.';
COMMENT ON COLUMN investory.asset_source_symbols.source_symbol IS 'Provider-native symbol, for example STOOQ aapl.us or cdr.pl.';
COMMENT ON COLUMN investory.asset_source_symbols.price_currency IS 'Currency/unit used by the provider price file.';
COMMENT ON COLUMN investory.asset_source_symbols.match_method IS 'How the provider symbol was selected, for example EXACT_SYMBOL or MANUAL_ALTERNATE_LISTING.';
COMMENT ON COLUMN investory.asset_source_symbols.match_status IS 'Validation status of the provider symbol mapping.';
COMMENT ON COLUMN investory.asset_source_symbols.is_alternate_listing IS 'True when provider data is a reviewed alternate listing proxy rather than exact exchange history.';
COMMENT ON COLUMN investory.asset_source_symbols.requires_fx_conversion IS 'True when provider price currency differs from the imported asset/listing currency and valuation must use FX for the valuation date.';

CREATE TABLE IF NOT EXISTS investory.asset_price_history (
    asset_id                 bigint NOT NULL REFERENCES investory.assets(id) ON DELETE CASCADE,
    price_date               date NOT NULL,
    source                   varchar(32) NOT NULL,
    source_symbol            varchar(64) NOT NULL,
    price_origin             varchar(32) NOT NULL,
    price_currency           varchar(3) NOT NULL REFERENCES investory.currencies(id),
    open_price               numeric(20,8),
    high_price               numeric(20,8),
    low_price                numeric(20,8),
    close_price              numeric(20,8) NOT NULL,
    adjusted_close_price     numeric(20,8),
    volume                   numeric(24,8),
    estimated                boolean NOT NULL DEFAULT false,
    interpolation_method     varchar(32),
    interpolation_left_date  date,
    interpolation_right_date date,
    observation_count        integer,
    source_date              date,
    imported_at              timestamptz NOT NULL DEFAULT now(),
    quality_score            integer NOT NULL DEFAULT 0,
    quality_class            varchar(64) NOT NULL DEFAULT 'UNRESOLVED',
    is_observed              boolean NOT NULL DEFAULT true,
    is_proxy                 boolean NOT NULL DEFAULT false,
    price_scale_factor       numeric(20,8) NOT NULL DEFAULT 1,
    scale_reason             varchar(255),
    original_source_symbol   varchar(64),
    PRIMARY KEY (asset_id, price_date, source),
    CONSTRAINT chk_asset_price_history_close_positive
        CHECK (close_price > 0),
    CONSTRAINT chk_asset_price_history_quality_score_range
        CHECK (quality_score BETWEEN 0 AND 100),
    CONSTRAINT chk_asset_price_history_price_scale_factor_positive
        CHECK (price_scale_factor > 0),
    CONSTRAINT chk_asset_price_history_observation_count_non_negative
        CHECK (observation_count IS NULL OR observation_count >= 0),
    CONSTRAINT chk_asset_price_history_ohlc
        CHECK (
            (open_price IS NULL OR open_price > 0)
            AND (high_price IS NULL OR high_price > 0)
            AND (low_price IS NULL OR low_price > 0)
            AND (adjusted_close_price IS NULL OR adjusted_close_price > 0)
            AND (volume IS NULL OR volume >= 0)
            AND (high_price IS NULL OR low_price IS NULL OR high_price >= low_price)
            AND (high_price IS NULL OR open_price IS NULL OR high_price >= open_price)
            AND (high_price IS NULL OR high_price >= close_price)
            AND (low_price IS NULL OR open_price IS NULL OR low_price <= open_price)
            AND (low_price IS NULL OR low_price <= close_price)
        ),
    CONSTRAINT chk_asset_price_history_estimation_metadata
        CHECK (
            (
                estimated = false
                AND interpolation_method IS NULL
                AND interpolation_left_date IS NULL
                AND interpolation_right_date IS NULL
                AND is_observed = true
            )
            OR
            (
                estimated = true
                AND is_observed = false
                AND price_origin = 'INTERPOLATED_XTB'
                AND interpolation_method IS NOT NULL
                AND interpolation_left_date IS NOT NULL
                AND interpolation_right_date IS NOT NULL
                AND interpolation_left_date < price_date
                AND interpolation_right_date > price_date
            )
        )
);
CREATE INDEX IF NOT EXISTS ix_asset_price_history_asset_date
    ON investory.asset_price_history(asset_id, price_date);
CREATE INDEX IF NOT EXISTS ix_asset_price_history_date
    ON investory.asset_price_history(price_date);
CREATE INDEX IF NOT EXISTS ix_asset_price_history_source_symbol
    ON investory.asset_price_history(source, source_symbol);
CREATE INDEX IF NOT EXISTS ix_asset_price_history_estimated
    ON investory.asset_price_history(asset_id, price_date)
    WHERE estimated;
CREATE INDEX IF NOT EXISTS ix_asset_price_history_quality
    ON investory.asset_price_history(asset_id, price_date, quality_score DESC);
COMMENT ON TABLE investory.asset_price_history IS 'Historical asset prices with explicit source provenance. Observed rows and interpolated estimates are intentionally stored separately.';
COMMENT ON COLUMN investory.asset_price_history.source IS 'Price source namespace such as STOOQ or XTB.';
COMMENT ON COLUMN investory.asset_price_history.price_origin IS 'Specific origin/provenance of the row. Interpolated rows are never represented as observed market data.';
COMMENT ON COLUMN investory.asset_price_history.estimated IS 'True only for generated estimates between two observed XTB trade-price checkpoints.';
COMMENT ON COLUMN investory.asset_price_history.adjusted_close_price IS 'Provider adjusted close when available. Stooq daily files in this import do not provide a separate adjusted close, so this is usually null.';
COMMENT ON COLUMN investory.asset_price_history.quality_score IS 'Relative source quality. Higher values should win over lower-quality generated rows.';
COMMENT ON COLUMN investory.asset_price_history.quality_class IS 'Quality/provenance class such as EXACT_LISTING_MARKET_CLOSE, EXACT_LISTING_SCALED, XTB_TRADE_OBSERVATION, or INTERPOLATED_XTB.';
COMMENT ON COLUMN investory.asset_price_history.price_scale_factor IS 'Scale applied to provider prices before writing close/open/high/low values.';

-- Summary tables - the main operation tables for portfolio management and reporting, used for calculations and analysis
CREATE TABLE IF NOT EXISTS investory.exchange_rates (
    id              bigserial PRIMARY KEY,
    month           date NOT NULL,
    base            varchar(3) NOT NULL REFERENCES investory.currencies(id),
    to_currency     varchar(3) NOT NULL REFERENCES investory.currencies(id),
    rate            numeric(20,8) NOT NULL,
    source          varchar(32) NOT NULL DEFAULT 'MANUAL',
    imported_at     timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_currencies_base_to_currency_month
    ON investory.exchange_rates (month, base, to_currency);
COMMENT ON TABLE investory.exchange_rates IS 'Historical exchange rates for USD/EUR/PLN currencies, used for reporting and analysis';

CREATE TYPE investory.cash_operation_type AS ENUM (
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

CREATE TABLE IF NOT EXISTS investory.import_history (
    id              bigserial PRIMARY KEY,
    provider        varchar(16) NOT NULL REFERENCES investory.providers(id),
    file_name       varchar(255),
    file_sha256     varchar(255) NOT NULL,
    started_at      timestamptz NOT NULL,
    finished_at     timestamptz,
    status          text,
    rows_total      integer,
    rows_failed     integer,
    error_message   text,
    source_type     text,
    source_ref      text,
    rows_applied    integer,
    CONSTRAINT chk_import_history_status
        CHECK (status IS NULL OR status IN ('STARTED', 'COMPLETED', 'PARTIAL', 'FAILED')),
    CONSTRAINT chk_import_history_rows_total_non_negative
        CHECK (rows_total IS NULL OR rows_total >= 0),
    CONSTRAINT chk_import_history_rows_failed_non_negative
        CHECK (rows_failed IS NULL OR rows_failed >= 0),
    CONSTRAINT chk_import_history_rows_applied_non_negative
        CHECK (rows_applied IS NULL OR rows_applied >= 0),
    CONSTRAINT chk_import_history_finished_after_started
        CHECK (finished_at IS NULL OR finished_at >= started_at)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_import_history_provider_sha256
    ON investory.import_history (provider, file_sha256);
COMMENT ON TABLE investory.import_history IS 'History of imports from providers to the system';

CREATE TABLE investory.cash_operations (
    id           bigserial PRIMARY KEY,
    account_id   bigint REFERENCES investory.accounts(id) ON DELETE CASCADE,
    operation    investory.cash_operation_type NOT NULL,
    asset_id     varchar(64) REFERENCES investory.assets(symbol),
    amount       numeric(20,8) NOT NULL,
    currency     varchar(3) REFERENCES investory.currencies(id),
    comment      varchar(1023),
    date         timestamp with time zone NOT NULL
);
-- drop index if exists ux_cash_operations;
-- create unique index if not exists ux_cash_operations on cash_operations (account_id, date, asset_id);
COMMENT ON TABLE investory.cash_operations IS 'All RAW historical records of cash operations imported from all portfolio providers';

CREATE TYPE investory.positions_operation_type AS ENUM ('BUY', 'SELL');
CREATE TABLE IF NOT EXISTS investory.positions (
    id              bigserial PRIMARY KEY,
    account_id      bigint REFERENCES investory.accounts(id) ON DELETE CASCADE,
    asset_id        varchar(64) REFERENCES investory.assets(symbol),
    operation       investory.positions_operation_type NOT NULL,
    volume          numeric(20,8),
    currency        varchar(3) REFERENCES investory.currencies(id),
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
-- CREATE UNIQUE INDEX IF NOT EXISTS ux_positions_business_key
--     ON investory.positions (
--         account_id,
--         asset_id,
--         operation,
--         COALESCE(volume, 0),
--         COALESCE(currency, ''),
--         COALESCE(open_time, '-infinity'::timestamptz),
--         COALESCE(open_price, 0),
--         COALESCE(close_time, 'infinity'::timestamptz),
--         COALESCE(close_price, 0),
--         COALESCE(base_value, 0),
--         COALESCE(purchase_value, 0),
--         COALESCE(sale_value, 0),
--         COALESCE(margin, 0),
--         COALESCE(commission, 0),
--         COALESCE(swap, 0),
--         COALESCE(profit, 0)
--     );
COMMENT ON TABLE investory.positions IS 'Portfolio assets, both active and closed, used for calculating portfolio performance and reporting';
COMMENT ON COLUMN investory.positions.close_time IS 'The time when the position was closed, null if the position is still open';

CREATE TABLE IF NOT EXISTS investory.account_daily (
    id                  bigserial PRIMARY KEY,
    account_id          bigint NOT NULL REFERENCES investory.accounts(id) ON DELETE CASCADE,
    snapshot_date       date NOT NULL,
    valuation_currency  varchar(3) NOT NULL REFERENCES investory.currencies(id),
    cash_balance        numeric(20,8) NOT NULL DEFAULT 0,
    market_value        numeric(20,8) NOT NULL DEFAULT 0,
    equity              numeric(20,8) NOT NULL DEFAULT 0,
    cost_base           numeric(20,8) NOT NULL DEFAULT 0,
    unrealized_profit   numeric(20,8) NOT NULL DEFAULT 0,
    dividends           numeric(20,8) NOT NULL DEFAULT 0,
    interest            numeric(20,8) NOT NULL DEFAULT 0,
    fees                numeric(20,8) NOT NULL DEFAULT 0,
    taxes               numeric(20,8) NOT NULL DEFAULT 0,
    deposits            numeric(20,8) NOT NULL DEFAULT 0,
    withdrawals         numeric(20,8) NOT NULL DEFAULT 0,
    realized_profit     numeric(20,8) NOT NULL DEFAULT 0,
    daily_profit_amount numeric(20,8) NOT NULL DEFAULT 0,
    daily_return_pct    numeric(20,8),
    portfolio_weight    numeric(20,8),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_account_daily_account_date
    ON investory.account_daily(account_id, snapshot_date);
CREATE INDEX IF NOT EXISTS ix_account_daily_snapshot_date
    ON investory.account_daily(snapshot_date);
COMMENT ON TABLE investory.account_daily IS 'Single persisted historical reporting fact table. One row per account per snapshot date in the account valuation currency.';
COMMENT ON COLUMN investory.account_daily.snapshot_date IS 'Historical snapshot date for the account projection row.';
COMMENT ON COLUMN investory.account_daily.valuation_currency IS 'Currency of all monetary values stored in this snapshot row.';
COMMENT ON COLUMN investory.account_daily.cash_balance IS 'End-of-day account cash balance in valuation currency.';
COMMENT ON COLUMN investory.account_daily.market_value IS 'End-of-day market value of open positions in valuation currency.';
COMMENT ON COLUMN investory.account_daily.equity IS 'End-of-day total account equity in valuation currency. Expected to equal cash_balance + market_value.';
COMMENT ON COLUMN investory.account_daily.cost_base IS 'End-of-day cost basis of open positions in valuation currency.';
COMMENT ON COLUMN investory.account_daily.unrealized_profit IS 'End-of-day unrealized profit of open positions in valuation currency.';
COMMENT ON COLUMN investory.account_daily.deposits IS 'Positive external funding deposited on snapshot_date in valuation currency.';
COMMENT ON COLUMN investory.account_daily.withdrawals IS 'Positive external funding withdrawn on snapshot_date in valuation currency.';
COMMENT ON COLUMN investory.account_daily.dividends IS 'Dividend income booked on snapshot_date in valuation currency.';
COMMENT ON COLUMN investory.account_daily.interest IS 'Interest income booked on snapshot_date in valuation currency.';
COMMENT ON COLUMN investory.account_daily.fees IS 'Positive fee amount booked on snapshot_date in valuation currency.';
COMMENT ON COLUMN investory.account_daily.taxes IS 'Positive tax amount booked on snapshot_date in valuation currency.';
COMMENT ON COLUMN investory.account_daily.realized_profit IS 'Realized trading profit booked on snapshot_date in valuation currency.';
COMMENT ON COLUMN investory.account_daily.daily_profit_amount IS 'Daily total profit contribution in valuation currency after flows, realized P/L and mark-to-market effects.';
COMMENT ON COLUMN investory.account_daily.daily_return_pct IS 'Daily return ratio for the account snapshot. Stored as decimal ratio, not percent points.';
COMMENT ON COLUMN investory.account_daily.portfolio_weight IS 'Share of account equity within its portfolio on snapshot_date, stored as decimal ratio.';

CREATE TABLE IF NOT EXISTS investory.benchmark_monthly_closes (
    id              bigserial PRIMARY KEY,
    symbol          varchar(32) NOT NULL,
    month           date NOT NULL,
    close_price     numeric(20,8) NOT NULL,
    fetched_at      timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_benchmark_monthly_closes_symbol_month
    ON investory.benchmark_monthly_closes(symbol, month);
COMMENT ON TABLE investory.benchmark_monthly_closes IS 'Persistent cache for benchmark monthly close prices used by benchmark comparisons.';

DO $$
    BEGIN
        -- Exchange rates
        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'chk_exchange_rates_rate_positive'
        ) THEN
            ALTER TABLE investory.exchange_rates
                ADD CONSTRAINT chk_exchange_rates_rate_positive
                    CHECK (rate > 0);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'chk_exchange_rates_month_first_day'
        ) THEN
            ALTER TABLE investory.exchange_rates
                ADD CONSTRAINT chk_exchange_rates_month_first_day
                    CHECK (month = date_trunc('month', month)::date);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'chk_exchange_rates_base_differs_from_to_currency'
        ) THEN
            ALTER TABLE investory.exchange_rates
                ADD CONSTRAINT chk_exchange_rates_base_differs_from_to_currency
                    CHECK (base <> to_currency);
        END IF;

        -- Positions
        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'chk_positions_volume_non_negative'
        ) THEN
            ALTER TABLE investory.positions
                ADD CONSTRAINT chk_positions_volume_non_negative
                    CHECK (volume >= 0);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'chk_positions_open_price_non_negative'
        ) THEN
            ALTER TABLE investory.positions
                ADD CONSTRAINT chk_positions_open_price_non_negative
                    CHECK (open_price >= 0);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'chk_positions_close_price_non_negative'
        ) THEN
            ALTER TABLE investory.positions
                ADD CONSTRAINT chk_positions_close_price_non_negative
                    CHECK (close_price IS NULL OR close_price >= 0);
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'chk_positions_close_time_after_open_time'
        ) THEN
            ALTER TABLE investory.positions
                ADD CONSTRAINT chk_positions_close_time_after_open_time
                    CHECK (
                        close_time IS NULL
                        OR open_time IS NULL
                        OR close_time >= open_time
                    );
        END IF;
    END
$$;
