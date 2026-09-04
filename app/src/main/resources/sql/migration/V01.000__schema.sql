CREATE SCHEMA IF NOT EXISTS investory;

SET search_path TO investory, public;

CREATE TABLE IF NOT EXISTS investory.reconciliation_parameters (
    parameter_name varchar(96) PRIMARY KEY,
    numeric_value numeric(30,12) NOT NULL,
    description varchar(255) NOT NULL
);

INSERT INTO investory.reconciliation_parameters(parameter_name, numeric_value, description)
VALUES
    ('reconciliation_reporting_scale', 0, 'Decimal places used for displayed reconciliation values.'),
    ('reconciliation_absolute_tolerance', 0.05, 'Shared absolute monetary tolerance.'),
    ('reconciliation_relative_tolerance', 0.00001, 'Shared relative monetary tolerance.'),
    ('reconciliation_quantity_tolerance', 0.000001, 'Tolerance for signed quantity matching; not a monetary tolerance.'),
    ('reconciliation_trade_cash_absolute_tolerance', 5, 'Domain threshold for trade cash settlement differences.'),
    ('reconciliation_trade_cash_relative_tolerance', 0.05, 'Domain threshold for trade cash settlement differences.'),
    ('reconciliation_carrying_value_absolute_threshold', 250, 'Domain threshold for carrying-value outliers.'),
    ('reconciliation_carrying_value_relative_threshold', 0.50, 'Domain threshold for carrying-value outliers.'),
    ('reconciliation_reorganization_absolute_threshold', 250, 'Domain threshold for reorganization value jumps.'),
    ('reconciliation_reorganization_relative_threshold', 0.20, 'Domain threshold for reorganization value jumps.'),
    ('reconciliation_market_bridge_absolute_threshold', 250, 'Domain threshold for market-value bridge outliers.'),
    ('reconciliation_market_bridge_relative_threshold', 0.20, 'Domain threshold for market-value bridge outliers.'),
    ('reconciliation_position_input_absolute_tolerance', 0.05, 'Input validation tolerance for position notional reconstruction.'),
    ('reconciliation_position_input_relative_tolerance', 0.15, 'Input validation tolerance for position notional reconstruction.'),
    ('reconciliation_local_sale_price_relative_threshold', 0.05, 'Domain threshold for local sale-vs-carrying-value comparison.'),
    ('reconciliation_local_flat_base_jump_relative_threshold', 0.15, 'Domain threshold for local-flat/base-currency valuation jumps.'),
    ('reconciliation_price_jump_upper_ratio', 100, 'Domain threshold for extreme upward price jumps.'),
    ('reconciliation_price_jump_lower_ratio', 0.01, 'Domain threshold for extreme downward price jumps.'),
    ('reconciliation_valuation_jump_upper_ratio', 5, 'Domain threshold for valuation-jump detection.'),
    ('reconciliation_valuation_jump_lower_ratio', 0.20, 'Domain threshold for valuation-jump detection.'),
    ('reconciliation_valuation_jump_trade_multiplier', 1.25, 'Domain threshold for valuation-jump trade allowance.'),
    ('reconciliation_valuation_jump_absolute_threshold', 250, 'Domain threshold for valuation-jump detection.')
;

COMMENT ON TABLE investory.reconciliation_parameters IS
    'Central reconciliation display precision, numeric tolerances, and explicitly named domain anomaly thresholds. Status uses full precision; reporting values are rounded only for presentation.';

-- Reference tables - the skeleton of the database for portfolio management and reporting
CREATE TABLE IF NOT EXISTS investory.currencies (
    id varchar(3) PRIMARY KEY,
    CONSTRAINT chk_currencies_id_uppercase_iso CHECK (id ~ '^[A-Z]{3}$')
);
INSERT INTO investory.currencies (id) VALUES ('USD'), ('EUR'), ('PLN')
;
COMMENT ON TABLE investory.currencies IS 'Reference table for currencies used in portfolios';

CREATE TABLE IF NOT EXISTS investory.providers (
    id varchar(32) PRIMARY KEY,
    CONSTRAINT chk_providers_id_uppercase CHECK (id ~ '^[A-Z0-9_]{2,32}$')
);
INSERT INTO investory.providers (id) VALUES ('XTB'), ('IBKR')
;

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
;

CREATE TABLE IF NOT EXISTS investory.app_users (
    id           bigserial PRIMARY KEY,
    username     varchar(64) NOT NULL UNIQUE,
    display_name varchar(255) NOT NULL,
    birth_date   date,
    active       boolean NOT NULL DEFAULT true,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    password_hash varchar(255),
    role         varchar(32) NOT NULL DEFAULT 'USER',
    CONSTRAINT chk_app_users_username_not_blank CHECK (btrim(username) <> ''),
    CONSTRAINT chk_app_users_display_name_not_blank CHECK (btrim(display_name) <> '')
);

COMMENT ON TABLE investory.app_users IS
    'Basic application identity boundary for future multi-user support. Authentication and authorization are intentionally out of scope.';

CREATE TABLE IF NOT EXISTS investory.portfolios (
    id              bigserial PRIMARY KEY,
    name            varchar(255) NOT NULL,
    base_currency   varchar(3) NOT NULL REFERENCES investory.currencies(id),
    local_currency  varchar(3) NOT NULL DEFAULT 'PLN' REFERENCES investory.currencies(id),
    owner           varchar(255),
    user_id         bigint NOT NULL REFERENCES investory.app_users(id),
    created_at      timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE investory.portfolios IS 'Full portfolio of accounts, can be used for reporting and analysis';
COMMENT ON COLUMN investory.portfolios.user_id IS
    'Required portfolio owner. Accounts, cash operations, and position lots inherit user ownership through portfolio_id.';
COMMENT ON COLUMN investory.portfolios.base_currency IS 'Base currency of the portfolio, used for converting accounts currencies';

CREATE TABLE IF NOT EXISTS investory.accounts (
    id              bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    external_account_id varchar(128) NOT NULL,
    currency        varchar(3) NOT NULL REFERENCES investory.currencies(id),
    provider        varchar(32) NOT NULL DEFAULT 'XTB' REFERENCES investory.providers(id),
    name            varchar(255) NOT NULL,
    owner           varchar(255) NOT NULL,
    portfolio_id    bigint NOT NULL REFERENCES investory.portfolios(id),
    cash_only       boolean NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_accounts_portfolio_provider_external_account
        UNIQUE (portfolio_id, provider, external_account_id)
);
COMMENT ON TABLE investory.accounts IS 'Each account linked to a portfolio with own history, performance and statistics';
COMMENT ON COLUMN investory.accounts.id IS 'Internal generated database identity used by ledger and projection foreign keys';
COMMENT ON COLUMN investory.accounts.external_account_id IS 'Provider-native broker account identifier. It is unique only within provider scope.';
COMMENT ON COLUMN investory.accounts.portfolio_id IS 'Reference to the portfolio this account belongs to';
COMMENT ON COLUMN investory.accounts.cash_only IS
    'When true, this account contributes cash operations only. Position trades and position valuation are excluded from projections and trading reports.';
COMMENT ON COLUMN investory.accounts.provider IS
    'Required canonical broker/provider code. Values are constrained by providers(id); null or free-text broker labels are not allowed.';
CREATE INDEX IF NOT EXISTS ix_accounts_cash_only ON investory.accounts(cash_only) WHERE cash_only;

CREATE TABLE IF NOT EXISTS investory.assets(
    id               bigserial PRIMARY KEY,
    name             varchar(511) NOT NULL,
    symbol           varchar(64) NOT NULL,
    ticker           varchar(64) NOT NULL,
    ibkr             varchar(64) NOT NULL,
    yahoo            varchar(64),
    country          varchar(15) NOT NULL,
    currency         varchar(3) NOT NULL REFERENCES investory.currencies(id),
    asset_type       varchar(32) NOT NULL REFERENCES investory.asset_types(id),
    isin             varchar(12),
    figi             varchar(16),
    exchange_mic     varchar(4),
    active           boolean NOT NULL DEFAULT true,
    exclude_from_import boolean NOT NULL DEFAULT false,
    market_price     numeric(20,8),
    market_price_usd numeric(20,8),
    price_source     varchar(255),
    price_updated_at timestamptz,
    CONSTRAINT chk_assets_symbol_not_blank CHECK (btrim(symbol) <> ''),
    CONSTRAINT chk_assets_ticker_not_blank CHECK (btrim(ticker) <> ''),
    CONSTRAINT chk_assets_ibkr_not_blank CHECK (btrim(ibkr) <> ''),
    CONSTRAINT chk_assets_yahoo_not_blank CHECK (btrim(yahoo) <> ''),
    CONSTRAINT chk_assets_isin_format
        CHECK (isin IS NULL OR upper(btrim(isin)) ~ '^[A-Z]{2}[A-Z0-9]{9}[0-9]$'),
    CONSTRAINT chk_assets_figi_format
        CHECK (figi IS NULL OR upper(btrim(figi)) ~ '^[A-Z0-9]{12}$'),
    CONSTRAINT chk_assets_exchange_mic_format
        CHECK (exchange_mic IS NULL OR upper(btrim(exchange_mic)) ~ '^[A-Z0-9]{4}$')
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_assets_symbol ON investory.assets(symbol);
CREATE UNIQUE INDEX IF NOT EXISTS ux_assets_ibkr ON investory.assets(ibkr);
COMMENT ON TABLE investory.assets IS 'Stocks and ETFs as element of portfolio';
COMMENT ON COLUMN investory.assets.currency IS
    'Canonical native quote currency of assets.market_price. Historical rows retain their own observed price_currency and are converted directly from that currency during valuation.';
COMMENT ON COLUMN investory.assets.ibkr IS
    'Primary IBKR contract or symbol used by transaction import identity resolution. Each nonblank identifier resolves to one canonical asset; it is not a generalized price-provider mapping.';
COMMENT ON COLUMN investory.assets.yahoo IS
    'Optional Yahoo Finance symbol override. Normal symbols derive from assets.symbol; this is not an asset identity.';
COMMENT ON COLUMN investory.assets.market_price IS
    'Explicit current native quote fallback in assets.currency. Reporting prefers app_v_current_asset_price historical observations when available.';
COMMENT ON COLUMN investory.assets.market_price_usd IS
    'Legacy USD cache for UI/export compatibility. It is not an authoritative valuation input; reporting converts the selected native price exactly once.';
COMMENT ON COLUMN investory.assets.country IS 'Country used in the broker system, e.g. "US" for United States or "PL" for Poland';
COMMENT ON COLUMN investory.assets.exclude_from_import IS
    'When true, retain raw imported rows but exclude the asset and all asset-linked derived valuation, P/L, statistics, reconciliation, and normalized cash calculations.';
COMMENT ON COLUMN investory.assets.name IS
    'Required canonical instrument display name. Incomplete unnamed assets are rejected by the NOT NULL constraint.';
COMMENT ON COLUMN investory.assets.symbol IS
    'Stable canonical application symbol used for display and provider routing. It is not assumed to be a globally unique bare ticker; exchange-qualified symbols are preferred.';
COMMENT ON COLUMN investory.assets.exchange_mic IS
    'ISO 10383 market identifier used with ticker to identify a concrete exchange listing. When present, ticker plus exchange_mic is unique.';
COMMENT ON COLUMN investory.assets.isin IS
    'Optional global security identifier. Nonblank ISIN values are unique across assets.';
COMMENT ON COLUMN investory.assets.figi IS
    'Optional global security identifier. Nonblank FIGI values are unique across assets.';
ALTER SEQUENCE IF EXISTS investory.assets_id_seq INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS investory.asset_source_symbols (
    id                        bigserial PRIMARY KEY,
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
    CONSTRAINT uq_asset_source_symbols_asset_source_symbol UNIQUE (asset_id, source, source_symbol),
    CONSTRAINT chk_asset_source_symbols_source_code
        CHECK (source ~ '^[A-Z][A-Z0-9_]{0,31}$'),
    CONSTRAINT chk_asset_source_symbols_source_symbol_not_blank
        CHECK (btrim(source_symbol) <> ''),
    CONSTRAINT chk_asset_source_symbols_price_scale_factor_positive
        CHECK (price_scale_factor > 0),
    CONSTRAINT chk_asset_source_symbols_scale_observation_count_non_negative
        CHECK (scale_observation_count IS NULL OR scale_observation_count >= 0)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_asset_source_symbols_source_symbol_ci
    ON investory.asset_source_symbols(source, upper(source_symbol));
COMMENT ON TABLE investory.asset_source_symbols IS 'Explicit deterministic mapping between internal assets and provider/source symbols used for historical price imports.';
COMMENT ON COLUMN investory.asset_source_symbols.source_symbol IS 'Provider-native symbol, for example STOOQ aapl.us or cdr.pl.';
COMMENT ON COLUMN investory.asset_source_symbols.price_currency IS 'Expected raw provider quote currency before any source-specific normalization. asset_price_history.price_currency records the currency of the stored valuation row.';
COMMENT ON COLUMN investory.asset_source_symbols.match_method IS 'How the provider symbol was selected, for example EXACT_SYMBOL or MANUAL_ALTERNATE_LISTING.';
COMMENT ON COLUMN investory.asset_source_symbols.match_status IS 'Validation status of the provider symbol mapping.';
COMMENT ON COLUMN investory.asset_source_symbols.is_alternate_listing IS 'True when provider data is a reviewed alternate listing proxy rather than exact exchange history.';
COMMENT ON COLUMN investory.asset_source_symbols.requires_fx_conversion IS 'True when this mapping intentionally supplies a quote in a currency different from assets.currency. Valuation always converts the selected row price_currency directly to its target.';

CREATE TABLE IF NOT EXISTS investory.asset_price_history (
    asset_id                 bigint NOT NULL REFERENCES investory.assets(id) ON DELETE CASCADE,
    price_date               date NOT NULL,
    source                   varchar(32) NOT NULL,
    source_symbol            varchar(64) NOT NULL,
    source_mapping_id        bigint REFERENCES investory.asset_source_symbols(id) ON DELETE RESTRICT,
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
    CONSTRAINT chk_asset_price_history_source_code
        CHECK (source ~ '^[A-Z][A-Z0-9_]{0,31}$'),
    CONSTRAINT chk_asset_price_history_source_symbol_not_blank
        CHECK (btrim(source_symbol) <> ''),
    CONSTRAINT chk_asset_price_history_price_origin_code
        CHECK (price_origin ~ '^[A-Z][A-Z0-9_]{0,31}$'),
    CONSTRAINT chk_asset_price_history_quality_class_code
        CHECK (quality_class ~ '^[A-Z][A-Z0-9_]{0,63}$'),
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
            OR
            (
                estimated = true
                AND is_observed = false
                AND price_origin = 'STALE_CARRY_FORWARD'
                AND interpolation_method IS NULL
                AND interpolation_left_date IS NULL
                AND interpolation_right_date IS NULL
            )
        )
);
CREATE INDEX IF NOT EXISTS ix_asset_price_history_date
    ON investory.asset_price_history(price_date);
CREATE INDEX IF NOT EXISTS ix_asset_price_history_source_symbol
    ON investory.asset_price_history(source, source_symbol);
CREATE INDEX IF NOT EXISTS ix_asset_price_history_source_mapping
    ON investory.asset_price_history(source_mapping_id)
    WHERE source_mapping_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_asset_price_history_estimated
    ON investory.asset_price_history(asset_id, price_date)
    WHERE estimated;
CREATE INDEX IF NOT EXISTS ix_asset_price_history_quality
    ON investory.asset_price_history(asset_id, price_date, quality_score DESC);
COMMENT ON TABLE investory.asset_price_history IS 'Historical asset prices with explicit source provenance. Observed rows and interpolated estimates are intentionally stored separately.';
COMMENT ON COLUMN investory.asset_price_history.source IS 'Price source namespace such as STOOQ or XTB.';
COMMENT ON COLUMN investory.asset_price_history.source_mapping_id IS 'Approved asset_source_symbols mapping used for this row when one exists. Unmapped manual/import rows remain explicit and nullable.';
COMMENT ON COLUMN investory.asset_price_history.price_origin IS 'Specific origin/provenance of the row. Interpolated rows are never represented as observed market data.';
COMMENT ON COLUMN investory.asset_price_history.estimated IS 'True only for generated estimates between two observed XTB trade-price checkpoints.';
COMMENT ON COLUMN investory.asset_price_history.adjusted_close_price IS 'Provider adjusted close when available. Stooq daily files in this import do not provide a separate adjusted close, so this is usually null.';
COMMENT ON COLUMN investory.asset_price_history.quality_score IS 'Relative source quality. Higher values should win over lower-quality generated rows.';
COMMENT ON COLUMN investory.asset_price_history.quality_class IS 'Quality/provenance class such as EXACT_LISTING_MARKET_CLOSE, EXACT_LISTING_SCALED, XTB_TRADE_OBSERVATION, or INTERPOLATED_XTB.';
COMMENT ON COLUMN investory.asset_price_history.price_scale_factor IS 'Multiplier applied once by canonical price-selection views before valuation.';

-- Summary tables - the main operation tables for portfolio management and reporting, used for calculations and analysis
CREATE TABLE IF NOT EXISTS investory.exchange_rates (
    id              bigserial PRIMARY KEY,
    rate_date       date NOT NULL,
    base            varchar(3) NOT NULL REFERENCES investory.currencies(id),
    to_currency     varchar(3) NOT NULL REFERENCES investory.currencies(id),
    rate            numeric(20,8) NOT NULL,
    source          varchar(32) NOT NULL DEFAULT 'STATIC_BOOTSTRAP',
    method          varchar(32) NOT NULL DEFAULT 'HISTORICAL_MONTHLY',
    observed_at     timestamptz,
    source_reference varchar(256),
    imported_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_exchange_rates_rate_positive CHECK (rate > 0),
    CONSTRAINT chk_exchange_rates_base_differs_from_to_currency CHECK (base <> to_currency),
    CONSTRAINT chk_exchange_rates_source CHECK (source IN ('NBP','ECB','EXCHANGERATE_HOST','IBKR','XTB','STATIC_BOOTSTRAP','MANUAL','TEST')),
    CONSTRAINT chk_exchange_rates_method CHECK (method IN ('MARKET_DAILY','IBKR_DAILY_REFERENCE','IBKR_EXECUTION','XTB_EXECUTION','HISTORICAL_MONTHLY','INTERPOLATED','CARRY_FORWARD'))
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_exchange_rates_observation
    ON investory.exchange_rates (rate_date, base, to_currency, source, method, COALESCE(source_reference, ''));
COMMENT ON TABLE investory.exchange_rates IS 'Historical exchange rates for USD/EUR/PLN currencies, used for reporting and analysis';

CREATE TABLE IF NOT EXISTS investory.fx_configuration (
    config_key varchar(64) PRIMARY KEY,
    config_value varchar(64) NOT NULL
);
INSERT INTO investory.fx_configuration(config_key, config_value) VALUES
    ('daily_history_start', '9999-12-31'),
    ('max_age_days', '4');

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
    provider        varchar(32) NOT NULL REFERENCES investory.providers(id),
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
    attempt_no      integer NOT NULL DEFAULT 1,
    reprocess_of    bigint REFERENCES investory.import_history(id),
    portfolio_id    bigint REFERENCES investory.portfolios(id),
    CONSTRAINT chk_import_history_status
        CHECK (status IS NULL OR status IN ('STARTED', 'COMPLETED', 'PARTIAL', 'FAILED', 'NOT_READY')),
    CONSTRAINT chk_import_history_rows_total_non_negative
        CHECK (rows_total IS NULL OR rows_total >= 0),
    CONSTRAINT chk_import_history_rows_failed_non_negative
        CHECK (rows_failed IS NULL OR rows_failed >= 0),
    CONSTRAINT chk_import_history_rows_applied_non_negative
        CHECK (rows_applied IS NULL OR rows_applied >= 0),
    CONSTRAINT chk_import_history_finished_after_started
        CHECK (finished_at IS NULL OR finished_at >= started_at),
    CONSTRAINT chk_import_history_file_sha256_lower_hex
        CHECK (file_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_import_history_rows_applied_le_rows_total
        CHECK (rows_total IS NULL OR rows_applied IS NULL OR rows_applied <= rows_total),
    CONSTRAINT chk_import_history_rows_failed_le_rows_total
        CHECK (rows_total IS NULL OR rows_failed IS NULL OR rows_failed <= rows_total),
    CONSTRAINT chk_import_history_rows_balance
        CHECK (
            rows_total IS NULL
            OR COALESCE(rows_applied, 0) + COALESCE(rows_failed, 0) <= rows_total
        ),
    CONSTRAINT chk_import_history_attempt_no_positive
        CHECK (attempt_no >= 1),
    CONSTRAINT chk_import_history_reprocess_not_self
        CHECK (reprocess_of IS NULL OR reprocess_of <> id),
    CONSTRAINT chk_import_history_source_type_known
        CHECK (source_type IS NULL OR source_type IN ('MANUAL', 'API', 'TELEGRAM')),
    CONSTRAINT chk_import_history_status_started_lifecycle
        CHECK (status IS DISTINCT FROM 'STARTED' OR finished_at IS NULL),
    CONSTRAINT chk_import_history_status_completed_lifecycle
        CHECK (status IS DISTINCT FROM 'COMPLETED' OR finished_at IS NOT NULL),
    CONSTRAINT chk_import_history_status_partial_lifecycle
        CHECK (
            status IS DISTINCT FROM 'PARTIAL'
            OR (finished_at IS NOT NULL AND COALESCE(rows_failed, 0) > 0)
        ),
    CONSTRAINT chk_import_history_status_failed_lifecycle
        CHECK (status IS DISTINCT FROM 'FAILED' OR finished_at IS NOT NULL),
    CONSTRAINT chk_import_history_status_not_ready_lifecycle
        CHECK (status IS DISTINCT FROM 'NOT_READY' OR finished_at IS NOT NULL)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_import_history_provider_sha256_attempt
    ON investory.import_history (portfolio_id, provider, file_sha256, attempt_no);
COMMENT ON TABLE investory.import_history IS 'History of imports from providers to the system';

-- Immutable broker evidence. One physical artifact is reused by reprocess attempts.
CREATE TABLE IF NOT EXISTS investory.import_source_files (
    id                bigserial PRIMARY KEY,
    provider          varchar(32) NOT NULL REFERENCES investory.providers(id),
    import_history_id bigint NOT NULL REFERENCES investory.import_history(id) ON DELETE RESTRICT,
    file_name         varchar(255),
    content_type      varchar(255),
    file_sha256       varchar(64) NOT NULL,
    original_size     bigint NOT NULL,
    raw_payload       bytea NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_import_source_files_sha256_lower_hex
        CHECK (file_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_import_source_files_size_non_negative
        CHECK (original_size >= 0)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_import_source_files_provider_sha256
    ON investory.import_source_files(provider, file_sha256);
CREATE INDEX IF NOT EXISTS ix_import_source_files_import_history
    ON investory.import_source_files(import_history_id);
COMMENT ON TABLE investory.import_source_files IS
    'Immutable uploaded broker artifacts. Reprocess attempts reuse the artifact by provider and SHA-256.';

CREATE TABLE IF NOT EXISTS investory.import_source_rows (
    id                    bigserial PRIMARY KEY,
    import_history_id     bigint NOT NULL REFERENCES investory.import_history(id) ON DELETE RESTRICT,
    source_file_id        bigint REFERENCES investory.import_source_files(id) ON DELETE RESTRICT,
    provider              varchar(32) NOT NULL REFERENCES investory.providers(id),
    section_name          varchar(255),
    sheet_name            varchar(255),
    archive_member_name   varchar(512),
    source_row_number     integer,
    source_record_id      varchar(255),
    source_row_occurrence integer NOT NULL DEFAULT 1,
    logical_row_sha256    varchar(64),
    raw_text              text,
    raw_values            jsonb NOT NULL,
    created_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_import_source_rows_occurrence_positive
        CHECK (source_row_occurrence >= 1),
    CONSTRAINT chk_import_source_rows_location
        CHECK (source_row_number IS NOT NULL OR source_record_id IS NOT NULL),
    CONSTRAINT chk_import_source_rows_logical_sha256_lower_hex
        CHECK (logical_row_sha256 IS NULL OR logical_row_sha256 ~ '^[0-9a-f]{64}$')
);
CREATE INDEX IF NOT EXISTS ix_import_source_rows_import_history
    ON investory.import_source_rows(import_history_id);
CREATE INDEX IF NOT EXISTS ix_import_source_rows_source_file
    ON investory.import_source_rows(source_file_id);
CREATE INDEX IF NOT EXISTS ix_import_source_rows_record_identity
    ON investory.import_source_rows(source_record_id)
    WHERE source_record_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_import_source_rows_logical_identity
    ON investory.import_source_rows(provider, logical_row_sha256)
    WHERE logical_row_sha256 IS NOT NULL;
COMMENT ON TABLE investory.import_source_rows IS
    'Immutable broker row evidence. raw_values preserves parsed source fields; canonical values live elsewhere.';
COMMENT ON COLUMN investory.import_source_rows.logical_row_sha256 IS
    'Stable logical broker-row identity. Excludes file, batch, import time, archive member, and physical row location so overlapping files can be recognized while each physical observation remains auditable.';

CREATE TABLE IF NOT EXISTS investory.cash_operations (
    id           bigint PRIMARY KEY,
    account_id   bigint NOT NULL REFERENCES investory.accounts(id),
    operation    investory.cash_operation_type NOT NULL,
    asset_id     bigint REFERENCES investory.assets(id),
    source_asset_symbol varchar(128),
    broker_symbol varchar(128),
    amount       numeric(20,8) NOT NULL,
    currency     varchar(3) NOT NULL REFERENCES investory.currencies(id),
    comment      varchar(1023),
    date         timestamp with time zone NOT NULL,
    execution_fx_base varchar(3),
    execution_fx_to_currency varchar(3),
    execution_fx_rate numeric(20,8),
    execution_fx_observed_at timestamp with time zone,
    execution_fx_source varchar(32),
    execution_fx_reference varchar(256),
    import_history_id bigint REFERENCES investory.import_history(id) ON DELETE RESTRICT,
    import_source_row_id bigint REFERENCES investory.import_source_rows(id) ON DELETE RESTRICT
);
COMMENT ON TABLE investory.cash_operations IS 'All RAW historical records of cash operations imported from all portfolio providers';

CREATE TYPE investory.positions_operation_type AS ENUM ('BUY', 'SELL');
CREATE TYPE investory.position_settlement_model AS ENUM (
    'CASH_SETTLED',
    'RESULT_ONLY',
    'UNCLASSIFIED'
);
CREATE TABLE IF NOT EXISTS investory.positions (
    id              bigint PRIMARY KEY,
    account_id      bigint NOT NULL REFERENCES investory.accounts(id),
    asset_id        bigint NOT NULL REFERENCES investory.assets(id),
    source_asset_symbol varchar(128) NOT NULL,
    broker_symbol   varchar(128),
    broker_product  varchar(255),
    source_position_id varchar(128),
    source_row_occurrence integer NOT NULL DEFAULT 1,
    operation       investory.positions_operation_type NOT NULL,
    settlement_model investory.position_settlement_model NOT NULL DEFAULT 'CASH_SETTLED',
    volume          numeric(20,8) NOT NULL,
    price_currency  varchar(3) NOT NULL REFERENCES investory.currencies(id),
    cost_currency   varchar(3) NOT NULL REFERENCES investory.currencies(id),
    profit_currency varchar(3) NOT NULL REFERENCES investory.currencies(id),
    commission_currency varchar(3) NOT NULL REFERENCES investory.currencies(id),
    open_time       timestamptz NOT NULL,
    open_price      numeric(20,8) NOT NULL,
    source_open_price numeric(20,8),
    open_conversion_rate numeric(20,8),
    close_time      timestamptz,
    close_price     numeric(20,8),
    source_close_price numeric(20,8),
    close_conversion_rate numeric(20,8),
    base_value      numeric(20,8),
    purchase_value  numeric(20,8),
    sale_value      numeric(20,8),
    margin          numeric(20,8),
    commission      numeric(20,8),
    swap            numeric(20,8),
    profit          numeric(20,8),
    import_history_id bigint REFERENCES investory.import_history(id) ON DELETE RESTRICT,
    import_source_row_id bigint REFERENCES investory.import_source_rows(id) ON DELETE RESTRICT,
    CONSTRAINT chk_positions_volume_non_negative CHECK (volume >= 0),
    CONSTRAINT chk_positions_source_row_occurrence_one_based
        CHECK (source_row_occurrence >= 1),
    CONSTRAINT chk_positions_open_price_non_negative CHECK (open_price >= 0),
    CONSTRAINT chk_positions_close_price_non_negative
        CHECK (close_price IS NULL OR close_price >= 0),
    CONSTRAINT chk_positions_close_time_after_open_time
        CHECK (
            close_time IS NULL
            OR open_time IS NULL
            OR close_time >= open_time
        )
);
CREATE INDEX IF NOT EXISTS ix_positions_source_position_id
    ON investory.positions(account_id, source_position_id)
    WHERE source_position_id IS NOT NULL;
COMMENT ON COLUMN investory.positions.id IS
    'Stable broker/source row identity assigned by the importer. The primary key is the row-level deduplication boundary across overlapping import files.';
COMMENT ON COLUMN investory.cash_operations.id IS
    'Stable broker/source row identity assigned by the importer. The primary key is the row-level deduplication boundary across overlapping import files.';
COMMENT ON TABLE investory.positions IS 'Portfolio assets, both active and closed, used for calculating portfolio performance and reporting';
COMMENT ON COLUMN investory.positions.close_time IS 'The time when the position was closed, null if the position is still open';
COMMENT ON COLUMN investory.positions.asset_id IS 'Canonical application identity. Numeric foreign key to assets.id; broker symbols are never identity.';
COMMENT ON COLUMN investory.positions.source_asset_symbol IS 'Unmodified symbol text from the imported source row, retained for audit.';
COMMENT ON COLUMN investory.positions.broker_symbol IS 'Optional broker-specific symbol or contract identifier retained for provenance.';
COMMENT ON COLUMN investory.positions.broker_product IS
    'Broker product text retained from the source row. XTB uses this as settlement-model evidence when present.';
COMMENT ON COLUMN investory.positions.source_position_id IS
    'Broker position identifier retained for provenance. It is not row-unique because partial closes can reuse one broker position id.';
COMMENT ON COLUMN investory.positions.source_row_occurrence IS
    'One-based occurrence of otherwise identical source rows. Legitimate repeated lots must remain separate.';
COMMENT ON COLUMN investory.positions.open_conversion_rate IS
    'Broker-reported multiplier from source_open_price currency to purchase-value currency.';
COMMENT ON COLUMN investory.positions.close_conversion_rate IS
    'Broker-reported multiplier from source_close_price currency to sale-value currency.';
COMMENT ON COLUMN investory.positions.source_open_price IS
    'Unmodified broker open price before any import-time normalization into a supported currency.';
COMMENT ON COLUMN investory.positions.source_close_price IS
    'Unmodified broker close price before any import-time normalization into a supported currency.';
COMMENT ON COLUMN investory.positions.volume IS 'Non-negative absolute quantity. Canonical signed quantity is BUY => volume and SELL => -volume.';
COMMENT ON COLUMN investory.positions.settlement_model IS
    'Per-position settlement contract. CASH_SETTLED moves trade notional through cash; RESULT_ONLY moves only realized profit/loss, swap, and fees. It is position-scoped because one account and symbol can contain both contracts.';
COMMENT ON COLUMN investory.positions.price_currency IS 'Currency of open_price and close_price.';
COMMENT ON COLUMN investory.positions.cost_currency IS 'Currency of purchase_value, sale_value, base_value, and margin.';
COMMENT ON COLUMN investory.positions.profit_currency IS 'Currency of profit and swap.';
COMMENT ON COLUMN investory.positions.commission_currency IS 'Currency of commission.';
COMMENT ON COLUMN investory.cash_operations.asset_id IS 'Optional canonical application identity. Numeric foreign key to assets.id.';
COMMENT ON COLUMN investory.cash_operations.source_asset_symbol IS 'Unmodified source symbol when the cash row names an instrument.';
COMMENT ON COLUMN investory.cash_operations.currency IS 'Currency of amount. This is independent from accounts.currency and asset quote currency.';

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
ALTER SEQUENCE IF EXISTS investory.account_daily_id_seq INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS investory.benchmark_monthly_closes (
    id              bigserial PRIMARY KEY,
    symbol          varchar(32) NOT NULL,
    month           date NOT NULL,
    close_price     numeric(20,8) NOT NULL,
    fetched_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_benchmark_monthly_closes_month_first_day
        CHECK (month = date_trunc('month', month)::date),
    CONSTRAINT chk_benchmark_monthly_closes_close_price_positive
        CHECK (close_price > 0)
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_benchmark_monthly_closes_symbol_month
    ON investory.benchmark_monthly_closes(symbol, month);
COMMENT ON TABLE investory.benchmark_monthly_closes IS 'Persistent cache for benchmark monthly close prices used by benchmark comparisons.';

CREATE INDEX IF NOT EXISTS ix_portfolios_user_id
    ON investory.portfolios(user_id);
CREATE INDEX IF NOT EXISTS ix_cash_operations_account_date
    ON investory.cash_operations(account_id, date);
CREATE INDEX IF NOT EXISTS ix_cash_operations_asset_id
    ON investory.cash_operations(asset_id)
    WHERE asset_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_cash_operations_account_operation_date
    ON investory.cash_operations(account_id, operation, date);
CREATE INDEX IF NOT EXISTS ix_positions_account_close_time
    ON investory.positions(account_id, close_time);
CREATE INDEX IF NOT EXISTS ix_positions_asset_close_time
    ON investory.positions(asset_id, close_time);
CREATE INDEX IF NOT EXISTS ix_positions_account_open_time
    ON investory.positions(account_id, open_time);
CREATE INDEX IF NOT EXISTS ix_positions_account_asset_close_time
    ON investory.positions(account_id, asset_id, close_time);
CREATE INDEX IF NOT EXISTS ix_import_history_status_finished_at
    ON investory.import_history(status, finished_at);
CREATE INDEX IF NOT EXISTS ix_import_history_portfolio_status
    ON investory.import_history(portfolio_id, status, finished_at DESC);
CREATE INDEX IF NOT EXISTS ix_accounts_portfolio_id_account
    ON investory.accounts(portfolio_id, id);
CREATE INDEX IF NOT EXISTS ix_account_daily_account_date_equity
    ON investory.account_daily(account_id, snapshot_date, equity);
CREATE UNIQUE INDEX IF NOT EXISTS ux_assets_isin
    ON investory.assets (upper(isin))
    WHERE isin IS NOT NULL AND btrim(isin) <> '';
CREATE UNIQUE INDEX IF NOT EXISTS ux_assets_figi
    ON investory.assets (upper(figi))
    WHERE figi IS NOT NULL AND btrim(figi) <> '';
CREATE UNIQUE INDEX IF NOT EXISTS ux_assets_ticker_exchange_mic
    ON investory.assets (upper(ticker), upper(exchange_mic))
    WHERE exchange_mic IS NOT NULL AND btrim(exchange_mic) <> '';

CREATE TABLE IF NOT EXISTS investory.system_audit_runs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    import_history_id bigint REFERENCES investory.import_history(id) ON DELETE SET NULL,
    trigger_source varchar(32) NOT NULL,
    started_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    finished_at timestamptz,
    status varchar(16) NOT NULL,
    error_count bigint NOT NULL DEFAULT 0,
    warning_count bigint NOT NULL DEFAULT 0,
    notification_status varchar(32) NOT NULL,
    summary jsonb,
    CONSTRAINT chk_system_audit_status CHECK (status IN ('STARTED', 'HEALTHY', 'WARN', 'ERROR')),
    CONSTRAINT chk_system_audit_notification_status CHECK (
        notification_status IN ('NONE', 'READY_WARNING', 'READY_ERROR')
    ),
    CONSTRAINT chk_system_audit_counts_non_negative CHECK (error_count >= 0 AND warning_count >= 0),
    CONSTRAINT chk_system_audit_finished_after_started CHECK (
        finished_at IS NULL OR finished_at >= started_at
    )
);

CREATE INDEX IF NOT EXISTS ix_system_audit_runs_import_started
    ON investory.system_audit_runs(import_history_id, started_at DESC)
    WHERE import_history_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_system_audit_runs_status_finished
    ON investory.system_audit_runs(status, finished_at DESC);

CREATE TABLE IF NOT EXISTS investory.system_audit_issues (
    id bigserial PRIMARY KEY,
    audit_run_id uuid NOT NULL REFERENCES investory.system_audit_runs(id) ON DELETE CASCADE,
    check_code varchar(64) NOT NULL,
    severity varchar(16) NOT NULL,
    issue_count bigint NOT NULL,
    required_action varchar(255) NOT NULL,
    details jsonb,
    CONSTRAINT chk_system_audit_issue_severity CHECK (severity IN ('WARN', 'ERROR')),
    CONSTRAINT chk_system_audit_issue_count_positive CHECK (issue_count > 0),
    UNIQUE (audit_run_id, check_code)
);

COMMENT ON TABLE investory.system_audit_runs IS
    'Persisted system-level validation result created by post-import application orchestration or explicit manual execution.';
COMMENT ON TABLE investory.system_audit_issues IS
    'Non-zero actionable checks captured for one system audit run. Stable check codes are suitable for notifications and structured logging.';

CREATE TABLE IF NOT EXISTS investory.integration_instances (
    id          bigserial PRIMARY KEY,
    owner_id    bigint REFERENCES investory.app_users(id) ON DELETE RESTRICT,
    plugin_id   varchar(128) NOT NULL,
    plugin_type varchar(32) NOT NULL,
    enabled     boolean NOT NULL DEFAULT false,
    config_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    last_test_at timestamptz,
    last_test_status varchar(32),
    last_test_message varchar(500),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_integration_instances_type
        CHECK (plugin_type IN ('BROKER_IMPORT', 'MARKET_DATA', 'FX_DATA', 'NOTIFICATION', 'AI', 'EXPORT')),
    CONSTRAINT ux_integration_instances_plugin_owner UNIQUE (owner_id, plugin_id, plugin_type)
);
CREATE INDEX IF NOT EXISTS ix_integration_instances_enabled ON investory.integration_instances(enabled);
CREATE INDEX IF NOT EXISTS ix_integration_instances_owner ON investory.integration_instances(owner_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_integration_instances_global_plugin
    ON investory.integration_instances(plugin_id, plugin_type)
    WHERE owner_id IS NULL;

CREATE TABLE IF NOT EXISTS investory.integration_secrets (
    id                    bigserial PRIMARY KEY,
    integration_instance_id bigint NOT NULL REFERENCES investory.integration_instances(id) ON DELETE CASCADE,
    secret_name           varchar(128) NOT NULL,
    ciphertext            text NOT NULL,
    key_version           varchar(64) NOT NULL,
    updated_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_integration_secrets_instance_name UNIQUE (integration_instance_id, secret_name)
);

CREATE TABLE IF NOT EXISTS investory.integration_jobs (
    id                      bigserial PRIMARY KEY,
    integration_instance_id bigint NOT NULL REFERENCES investory.integration_instances(id) ON DELETE CASCADE,
    job_type                varchar(128) NOT NULL,
    enabled                 boolean NOT NULL DEFAULT false,
    cron                    varchar(128) NOT NULL,
    timezone                varchar(64) NOT NULL DEFAULT 'Europe/Warsaw',
    parameters_json         jsonb NOT NULL DEFAULT '{}'::jsonb,
    last_started_at         timestamptz,
    last_completed_at       timestamptz,
    last_status             varchar(32),
    last_error              text,
    CONSTRAINT ux_integration_jobs_instance_type UNIQUE (integration_instance_id, job_type),
    CONSTRAINT chk_integration_jobs_status
        CHECK (last_status IS NULL OR last_status IN ('STARTED', 'SUCCESS', 'FAILED', 'SKIPPED'))
);
CREATE INDEX IF NOT EXISTS ix_integration_jobs_enabled ON investory.integration_jobs(enabled);

CREATE TABLE IF NOT EXISTS investory.yahoo_export_state (
    id                  integer PRIMARY KEY CHECK (id = 1),
    exported_at         timestamptz NOT NULL,
    portfolio_fingerprint varchar(64) NOT NULL,
    position_count      integer NOT NULL DEFAULT 0
);

COMMENT ON TABLE investory.yahoo_export_state IS
    'Latest successful Yahoo-compatible export and the portfolio state it represented.';

COMMENT ON TABLE investory.integration_instances IS
    'Plugin configuration instances. owner_id is nullable for global/single-user configuration.';
COMMENT ON TABLE investory.integration_secrets IS
    'Encrypted plugin secrets. Plaintext values never belong in config_json or logs.';
COMMENT ON TABLE investory.integration_jobs IS
    'Dynamic plugin jobs. Existing fixed schedulers remain compatibility paths during migration.';

CREATE TABLE IF NOT EXISTS investory.drawdown_alert_state (
    id bigint PRIMARY KEY CHECK (id = 1),
    peak_equity numeric(30,12) NOT NULL DEFAULT 0 CHECK (peak_equity >= 0),
    last_alert_at timestamptz
);

COMMENT ON TABLE investory.drawdown_alert_state IS
    'Singleton durable state for peak-to-trough drawdown notifications and their cooldown.';

CREATE TABLE IF NOT EXISTS investory.notification_alert_state (
    rule_code        varchar(64) PRIMARY KEY,
    active           boolean NOT NULL DEFAULT false,
    incident_sequence bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_notification_alert_state_sequence CHECK (incident_sequence >= 0)
);

COMMENT ON TABLE investory.notification_alert_state IS
    'Durable NORMAL/ACTIVE state and monotonic incident identity for threshold alert rules.';

CREATE TABLE IF NOT EXISTS investory.notification_event (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    event_type varchar(64) NOT NULL,
    severity varchar(16) NOT NULL,
    portfolio_id bigint,
    source_entity_type varchar(64) NOT NULL,
    source_entity_id varchar(128) NOT NULL,
    fingerprint varchar(255) NOT NULL,
    title varchar(255) NOT NULL,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL,
    delivery_state varchar(24) NOT NULL DEFAULT 'PENDING',
    delivered_at timestamptz,
    attempt_count integer NOT NULL DEFAULT 0,
    last_attempt_at timestamptz,
    next_attempt_at timestamptz NOT NULL,
    last_error varchar(1000),
    processing_lease_until timestamptz,
    processing_token varchar(64),
    CONSTRAINT uq_notification_event_fingerprint UNIQUE (fingerprint),
    CONSTRAINT ck_notification_event_type CHECK (event_type IN (
        'DAILY_DIGEST', 'THRESHOLD_ALERT', 'SYSTEM_AUDIT_ERROR',
        'IMPORT_FAILED_OR_PARTIAL', 'PLAN_BECAME_UNSUSTAINABLE')),
    CONSTRAINT ck_notification_event_severity CHECK (severity IN ('WARNING', 'ERROR', 'CRITICAL')),
    CONSTRAINT ck_notification_event_delivery_state CHECK (delivery_state IN (
        'PENDING', 'RETRYABLE', 'PROCESSING', 'DELIVERED', 'EXHAUSTED')),
    CONSTRAINT ck_notification_event_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_notification_event_delivery_time CHECK (
        (delivery_state = 'DELIVERED' AND delivered_at IS NOT NULL)
        OR (delivery_state <> 'DELIVERED' AND delivered_at IS NULL))
);

CREATE INDEX IF NOT EXISTS ix_notification_event_dispatch
    ON investory.notification_event (next_attempt_at, created_at)
    WHERE delivery_state IN ('PENDING', 'RETRYABLE');

CREATE INDEX IF NOT EXISTS ix_notification_event_source
    ON investory.notification_event (source_entity_type, source_entity_id);
CREATE INDEX IF NOT EXISTS ix_notification_event_processing_lease
    ON investory.notification_event(processing_lease_until)
    WHERE delivery_state = 'PROCESSING';

COMMENT ON TABLE investory.notification_event IS
    'Channel-neutral durable notification outbox. Fingerprints enforce producer idempotency; delivery is retryable and at-least-once.';
