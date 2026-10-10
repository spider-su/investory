-- Final core, identity, integration, and Market Radar tables; reference configuration.
-- Consolidated final schema; historical repair steps are not replayed.


CREATE SCHEMA IF NOT EXISTS investory;

SET search_path TO investory, public;

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TYPE investory.cash_operation_type AS ENUM (
    'DEPOSIT',
    'WITHDRAWAL',
    'TRANSFER',
    'SUBACCOUNT_TRANSFER',
    'STOCK_PURCHASE',
    'STOCK_SELL',
    'CLOSE_TRADE',
    'CORRECTION',
    'ROLLOVER',
    'SWAP',
    'DIVIDEND',
    'FREE_FUNDS_INTEREST',
    'COMMISSION',
    'SEC_FEE',
    'STAMP_DUTY',
    'TRANSACTION_TAX',
    'WITHHOLDING_TAX',
    'FREE_FUNDS_INTEREST_TAX',
    'UNKNOWN'
);

CREATE TYPE investory.position_settlement_model AS ENUM (
    'CASH_SETTLED',
    'RESULT_ONLY',
    'UNCLASSIFIED'
);

CREATE TYPE investory.positions_operation_type AS ENUM (
    'BUY',
    'SELL'
);

CREATE SEQUENCE investory.account_daily_id_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE investory.app_user_invitations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE investory.app_users_id_seq
    START WITH 3
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE investory.asset_source_symbols_id_seq
    START WITH 22
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE investory.assets_id_seq
    START WITH 1252
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE investory.benchmark_monthly_closes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE investory.exchange_rates_id_seq
    START WITH 121
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE investory.import_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE investory.import_source_files_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE investory.import_source_rows_id_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE investory.integration_instances_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE investory.integration_jobs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE investory.integration_secrets_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE investory.market_radar_media_observation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE investory.market_radar_outcome_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE investory.market_radar_snapshot_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE investory.market_radar_theme_snapshot_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE investory.portfolios_id_seq
    START WITH 3
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE investory.reconciliation_account_movement_reviews_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE SEQUENCE investory.system_audit_issues_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE investory.app_users (
    id bigint DEFAULT nextval('investory.app_users_id_seq'::regclass) NOT NULL,
    username character varying(64) NOT NULL,
    display_name character varying(255) NOT NULL,
    birth_date date,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    password_hash character varying(255),
    role character varying(32) DEFAULT 'USER'::character varying NOT NULL,
    email character varying(320),
    google_subject character varying(255),
    CONSTRAINT chk_app_users_display_name_not_blank CHECK ((btrim((display_name)::text) <> ''::text)),
    CONSTRAINT chk_app_users_username_not_blank CHECK ((btrim((username)::text) <> ''::text)),
    CONSTRAINT app_users_pkey PRIMARY KEY (id),
    CONSTRAINT app_users_username_key UNIQUE (username)
);

CREATE TABLE investory.asset_types (
    id character varying(32) NOT NULL,
    CONSTRAINT chk_asset_types_id_uppercase CHECK (((id)::text ~ '^[A-Z_]+$'::text)),
    CONSTRAINT asset_types_pkey PRIMARY KEY (id)
);

CREATE TABLE investory.benchmark_monthly_closes (
    id bigint DEFAULT nextval('investory.benchmark_monthly_closes_id_seq'::regclass) NOT NULL,
    symbol character varying(32) NOT NULL,
    month date NOT NULL,
    close_price numeric(20,8) NOT NULL,
    fetched_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_benchmark_monthly_closes_close_price_positive CHECK ((close_price > (0)::numeric)),
    CONSTRAINT chk_benchmark_monthly_closes_month_first_day CHECK ((month = (date_trunc('month'::text, (month)::timestamp with time zone))::date)),
    CONSTRAINT benchmark_monthly_closes_pkey PRIMARY KEY (id)
);

CREATE TABLE investory.currencies (
    id character varying(3) NOT NULL,
    CONSTRAINT chk_currencies_id_uppercase_iso CHECK (((id)::text ~ '^[A-Z]{3}$'::text)),
    CONSTRAINT currencies_pkey PRIMARY KEY (id)
);

CREATE TABLE investory.drawdown_alert_state (
    id bigint NOT NULL,
    peak_equity numeric(30,12) DEFAULT 0 NOT NULL,
    last_alert_at timestamp with time zone,
    CONSTRAINT drawdown_alert_state_id_check CHECK ((id = 1)),
    CONSTRAINT drawdown_alert_state_peak_equity_check CHECK ((peak_equity >= (0)::numeric)),
    CONSTRAINT drawdown_alert_state_pkey PRIMARY KEY (id)
);

CREATE TABLE investory.fx_configuration (
    config_key character varying(64) NOT NULL,
    config_value character varying(64) NOT NULL,
    CONSTRAINT fx_configuration_pkey PRIMARY KEY (config_key)
);

CREATE TABLE investory.market_radar_media_observation (
    id bigint DEFAULT nextval('investory.market_radar_media_observation_id_seq'::regclass) NOT NULL,
    external_id character varying(512) NOT NULL,
    source_type character varying(32) NOT NULL,
    source character varying(128) NOT NULL,
    author character varying(256),
    headline text NOT NULL,
    url text,
    published_at timestamp with time zone NOT NULL,
    stance character varying(32) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT market_radar_media_observation_pkey PRIMARY KEY (id),
    CONSTRAINT uk_market_radar_media_external UNIQUE (external_id)
);

CREATE TABLE investory.market_radar_outcome (
    id bigint DEFAULT nextval('investory.market_radar_outcome_id_seq'::regclass) NOT NULL,
    symbol character varying(64) NOT NULL,
    signal_date date NOT NULL,
    horizon_days integer NOT NULL,
    evaluated_on date NOT NULL,
    symbol_return double precision NOT NULL,
    benchmark character varying(64) NOT NULL,
    benchmark_return double precision,
    excess_return double precision,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT market_radar_outcome_pkey PRIMARY KEY (id),
    CONSTRAINT uk_market_radar_outcome UNIQUE (symbol, signal_date, horizon_days)
);

CREATE TABLE investory.market_radar_run (
    id uuid NOT NULL,
    started_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    status character varying(16) NOT NULL,
    universe_size integer NOT NULL,
    attempted integer DEFAULT 0 NOT NULL,
    stored integer DEFAULT 0 NOT NULL,
    no_data integer DEFAULT 0 NOT NULL,
    failed integer DEFAULT 0 NOT NULL,
    interesting integer DEFAULT 0 NOT NULL,
    normal_count integer DEFAULT 0 NOT NULL,
    emerging_count integer DEFAULT 0 NOT NULL,
    trending_count integer DEFAULT 0 NOT NULL,
    hot_count integer DEFAULT 0 NOT NULL,
    extended_count integer DEFAULT 0 NOT NULL,
    cooling_count integer DEFAULT 0 NOT NULL,
    outcomes_evaluated integer DEFAULT 0 NOT NULL,
    duration_ms bigint DEFAULT 0 NOT NULL,
    error text,
    CONSTRAINT market_radar_run_pkey PRIMARY KEY (id)
);

CREATE TABLE investory.market_radar_snapshot (
    id bigint DEFAULT nextval('investory.market_radar_snapshot_id_seq'::regclass) NOT NULL,
    symbol character varying(64) NOT NULL,
    observed_on date NOT NULL,
    state character varying(32) NOT NULL,
    close_price double precision NOT NULL,
    return_20d double precision,
    return_60d double precision,
    relative_volume_20d double precision,
    distance_sma50 double precision,
    rsi14 double precision,
    reasons text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT market_radar_snapshot_pkey PRIMARY KEY (id),
    CONSTRAINT uk_market_radar_snapshot_symbol_date UNIQUE (symbol, observed_on)
);

CREATE TABLE investory.market_radar_theme_snapshot (
    id bigint DEFAULT nextval('investory.market_radar_theme_snapshot_id_seq'::regclass) NOT NULL,
    theme character varying(128) NOT NULL,
    proxy_symbol character varying(32) NOT NULL,
    observed_on date NOT NULL,
    state character varying(32) NOT NULL,
    member_count integer NOT NULL,
    breadth_above_sma50 double precision NOT NULL,
    breadth_outperforming_benchmark double precision CONSTRAINT market_radar_theme_snapshot_breadth_outperforming_benc_not_null NOT NULL,
    proxy_return_20d double precision NOT NULL,
    benchmark_return_20d double precision NOT NULL,
    relative_strength_20d double precision NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT market_radar_theme_snapshot_pkey PRIMARY KEY (id),
    CONSTRAINT uk_market_radar_theme_snapshot UNIQUE (theme, observed_on)
);

CREATE TABLE investory.notification_alert_state (
    rule_code character varying(64) NOT NULL,
    active boolean DEFAULT false NOT NULL,
    incident_sequence bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_notification_alert_state_sequence CHECK ((incident_sequence >= 0)),
    CONSTRAINT notification_alert_state_pkey PRIMARY KEY (rule_code)
);

CREATE TABLE investory.notification_event (
    id bigint GENERATED BY DEFAULT AS IDENTITY (SEQUENCE NAME investory.notification_event_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1) NOT NULL,
    event_type character varying(64) NOT NULL,
    severity character varying(16) NOT NULL,
    portfolio_id bigint,
    source_entity_type character varying(64) NOT NULL,
    source_entity_id character varying(128) NOT NULL,
    fingerprint character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone NOT NULL,
    delivery_state character varying(24) DEFAULT 'PENDING'::character varying NOT NULL,
    delivered_at timestamp with time zone,
    attempt_count integer DEFAULT 0 NOT NULL,
    last_attempt_at timestamp with time zone,
    next_attempt_at timestamp with time zone NOT NULL,
    last_error character varying(1000),
    processing_lease_until timestamp with time zone,
    processing_token character varying(64),
    CONSTRAINT ck_notification_event_attempt_count CHECK ((attempt_count >= 0)),
    CONSTRAINT ck_notification_event_delivery_state CHECK (((delivery_state)::text = ANY ((ARRAY['PENDING'::character varying, 'RETRYABLE'::character varying, 'PROCESSING'::character varying, 'DELIVERED'::character varying, 'EXHAUSTED'::character varying])::text[]))),
    CONSTRAINT ck_notification_event_delivery_time CHECK (((((delivery_state)::text = 'DELIVERED'::text) AND (delivered_at IS NOT NULL)) OR (((delivery_state)::text <> 'DELIVERED'::text) AND (delivered_at IS NULL)))),
    CONSTRAINT ck_notification_event_severity CHECK (((severity)::text = ANY ((ARRAY['WARNING'::character varying, 'ERROR'::character varying, 'CRITICAL'::character varying])::text[]))),
    CONSTRAINT ck_notification_event_type CHECK (((event_type)::text = ANY ((ARRAY['DAILY_DIGEST'::character varying, 'THRESHOLD_ALERT'::character varying, 'SYSTEM_AUDIT_ERROR'::character varying, 'IMPORT_FAILED_OR_PARTIAL'::character varying, 'INTEGRATION_JOB_ALERT'::character varying, 'PLAN_BECAME_UNSUSTAINABLE'::character varying])::text[]))),
    CONSTRAINT notification_event_pkey PRIMARY KEY (id),
    CONSTRAINT uq_notification_event_fingerprint UNIQUE (fingerprint)
);

CREATE TABLE investory.providers (
    id character varying(32) NOT NULL,
    CONSTRAINT chk_providers_id_uppercase CHECK (((id)::text ~ '^[A-Z0-9_]{2,32}$'::text)),
    CONSTRAINT providers_pkey PRIMARY KEY (id)
);

CREATE TABLE investory.reconciliation_parameters (
    parameter_name character varying(96) NOT NULL,
    numeric_value numeric(30,12) NOT NULL,
    description character varying(255) NOT NULL,
    CONSTRAINT reconciliation_parameters_pkey PRIMARY KEY (parameter_name)
);

CREATE TABLE investory.reconciliation_price_anomaly_reviews (
    issue_code character varying(64) NOT NULL,
    entity_id bigint NOT NULL,
    entity_key character varying(255) NOT NULL,
    event_date date NOT NULL,
    previous_date date NOT NULL,
    source character varying(64) NOT NULL,
    source_symbol character varying(128) NOT NULL,
    previous_value numeric NOT NULL,
    current_value numeric NOT NULL,
    resolution character varying(40) NOT NULL,
    rationale text NOT NULL,
    evidence_url text NOT NULL,
    reviewed_at timestamp with time zone DEFAULT now() NOT NULL,
    reviewed_by character varying(128) DEFAULT CURRENT_USER NOT NULL,
    CONSTRAINT reconciliation_price_anomaly_reviews_resolution_check CHECK (((resolution)::text = ANY ((ARRAY['CONFIRMED_MARKET_MOVE'::character varying, 'SOURCE_PRICE_CORRECTED'::character varying, 'MANUAL_ALTERNATE_LISTING_ACCEPTED'::character varying])::text[]))),
    CONSTRAINT reconciliation_price_anomaly_reviews_pkey PRIMARY KEY (issue_code, entity_id, event_date, previous_date, source, source_symbol, previous_value, current_value)
);

CREATE TABLE investory.yahoo_export_state (
    id integer NOT NULL,
    exported_at timestamp with time zone NOT NULL,
    portfolio_fingerprint character varying(64) NOT NULL,
    position_count integer DEFAULT 0 NOT NULL,
    CONSTRAINT yahoo_export_state_id_check CHECK ((id = 1)),
    CONSTRAINT yahoo_export_state_pkey PRIMARY KEY (id)
);

CREATE TABLE investory.assets (
    id bigint DEFAULT nextval('investory.assets_id_seq'::regclass) NOT NULL,
    name character varying(511) NOT NULL,
    symbol character varying(64) NOT NULL,
    ticker character varying(64) NOT NULL,
    ibkr character varying(64) NOT NULL,
    yahoo character varying(64),
    country character varying(15) NOT NULL,
    currency character varying(3) NOT NULL,
    asset_type character varying(32) NOT NULL,
    isin character varying(12),
    figi character varying(16),
    exchange_mic character varying(4),
    active boolean DEFAULT true NOT NULL,
    exclude_from_import boolean DEFAULT false NOT NULL,
    market_price numeric(20,8),
    market_price_usd numeric(20,8),
    price_source character varying(255),
    price_updated_at timestamp with time zone,
    CONSTRAINT chk_assets_exchange_mic_format CHECK (((exchange_mic IS NULL) OR (upper(btrim((exchange_mic)::text)) ~ '^[A-Z0-9]{4}$'::text))),
    CONSTRAINT chk_assets_figi_format CHECK (((figi IS NULL) OR (upper(btrim((figi)::text)) ~ '^[A-Z0-9]{12}$'::text))),
    CONSTRAINT chk_assets_ibkr_not_blank CHECK ((btrim((ibkr)::text) <> ''::text)),
    CONSTRAINT chk_assets_isin_format CHECK (((isin IS NULL) OR (upper(btrim((isin)::text)) ~ '^[A-Z]{2}[A-Z0-9]{9}[0-9]$'::text))),
    CONSTRAINT chk_assets_symbol_not_blank CHECK ((btrim((symbol)::text) <> ''::text)),
    CONSTRAINT chk_assets_ticker_not_blank CHECK ((btrim((ticker)::text) <> ''::text)),
    CONSTRAINT chk_assets_yahoo_not_blank CHECK ((btrim((yahoo)::text) <> ''::text)),
    CONSTRAINT assets_pkey PRIMARY KEY (id),
    CONSTRAINT assets_asset_type_fkey FOREIGN KEY (asset_type) REFERENCES investory.asset_types(id),
    CONSTRAINT assets_currency_fkey FOREIGN KEY (currency) REFERENCES investory.currencies(id)
);

CREATE TABLE investory.exchange_rates (
    id bigint DEFAULT nextval('investory.exchange_rates_id_seq'::regclass) NOT NULL,
    rate_date date NOT NULL,
    base character varying(3) NOT NULL,
    to_currency character varying(3) NOT NULL,
    rate numeric(20,8) NOT NULL,
    purpose character varying(16) DEFAULT 'VALUATION'::character varying NOT NULL,
    source character varying(32) DEFAULT 'STATIC_BOOTSTRAP'::character varying NOT NULL,
    method character varying(32) DEFAULT 'OBSERVED'::character varying NOT NULL,
    source_rate_date date,
    observed_at timestamp with time zone,
    source_reference character varying(256),
    imported_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_exchange_rates_base_differs_from_to_currency CHECK (((base)::text <> (to_currency)::text)),
    CONSTRAINT chk_exchange_rates_method CHECK (((method)::text = ANY ((ARRAY['OBSERVED'::character varying, 'INTERPOLATED'::character varying, 'XTB_EXECUTION'::character varying, 'IBKR_EXECUTION'::character varying])::text[]))),
    CONSTRAINT chk_exchange_rates_purpose CHECK (((purpose)::text = ANY ((ARRAY['VALUATION'::character varying, 'EXECUTION'::character varying])::text[]))),
    CONSTRAINT chk_exchange_rates_purpose_method CHECK (((((purpose)::text = 'VALUATION'::text) AND ((method)::text = ANY ((ARRAY['OBSERVED'::character varying, 'INTERPOLATED'::character varying])::text[]))) OR (((purpose)::text = 'EXECUTION'::text) AND ((method)::text = ANY ((ARRAY['XTB_EXECUTION'::character varying, 'IBKR_EXECUTION'::character varying])::text[]))))),
    CONSTRAINT chk_exchange_rates_rate_positive CHECK ((rate > (0)::numeric)),
    CONSTRAINT exchange_rates_pkey PRIMARY KEY (id),
    CONSTRAINT exchange_rates_base_fkey FOREIGN KEY (base) REFERENCES investory.currencies(id),
    CONSTRAINT exchange_rates_to_currency_fkey FOREIGN KEY (to_currency) REFERENCES investory.currencies(id)
);

CREATE TABLE investory.integration_instances (
    id bigint DEFAULT nextval('investory.integration_instances_id_seq'::regclass) NOT NULL,
    owner_id bigint,
    plugin_id character varying(128) NOT NULL,
    plugin_type character varying(32) NOT NULL,
    enabled boolean DEFAULT false NOT NULL,
    config_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    last_test_at timestamp with time zone,
    last_test_status character varying(32),
    last_test_message character varying(500),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_integration_instances_type CHECK (((plugin_type)::text = ANY ((ARRAY['BROKER_IMPORT'::character varying, 'MARKET_DATA'::character varying, 'FX_DATA'::character varying, 'NOTIFICATION'::character varying, 'AI'::character varying, 'EXPORT'::character varying])::text[]))),
    CONSTRAINT integration_instances_pkey PRIMARY KEY (id),
    CONSTRAINT ux_integration_instances_plugin_owner UNIQUE (owner_id, plugin_id, plugin_type),
    CONSTRAINT integration_instances_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES investory.app_users(id) ON DELETE RESTRICT
);

CREATE TABLE investory.market_radar_media_symbol (
    observation_id bigint NOT NULL,
    symbol character varying(64) NOT NULL,
    CONSTRAINT market_radar_media_symbol_pkey PRIMARY KEY (observation_id, symbol),
    CONSTRAINT market_radar_media_symbol_observation_id_fkey FOREIGN KEY (observation_id) REFERENCES investory.market_radar_media_observation(id) ON DELETE CASCADE
);

CREATE TABLE investory.portfolios (
    id bigint DEFAULT nextval('investory.portfolios_id_seq'::regclass) NOT NULL,
    name character varying(255) NOT NULL,
    base_currency character varying(3) NOT NULL,
    local_currency character varying(3) DEFAULT 'PLN'::character varying NOT NULL,
    owner character varying(255),
    user_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT portfolios_pkey PRIMARY KEY (id),
    CONSTRAINT portfolios_base_currency_fkey FOREIGN KEY (base_currency) REFERENCES investory.currencies(id),
    CONSTRAINT portfolios_local_currency_fkey FOREIGN KEY (local_currency) REFERENCES investory.currencies(id),
    CONSTRAINT portfolios_user_id_fkey FOREIGN KEY (user_id) REFERENCES investory.app_users(id)
);

CREATE TABLE investory.accounts (
    id bigint GENERATED BY DEFAULT AS IDENTITY (SEQUENCE NAME investory.accounts_id_seq START WITH 91000012 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1) NOT NULL,
    external_account_id character varying(128) NOT NULL,
    currency character varying(3) NOT NULL,
    provider character varying(32) DEFAULT 'XTB'::character varying NOT NULL,
    name character varying(255) NOT NULL,
    owner character varying(255) NOT NULL,
    portfolio_id bigint NOT NULL,
    cash_only boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT accounts_pkey PRIMARY KEY (id),
    CONSTRAINT ux_accounts_portfolio_provider_external_account UNIQUE (portfolio_id, provider, external_account_id),
    CONSTRAINT accounts_currency_fkey FOREIGN KEY (currency) REFERENCES investory.currencies(id),
    CONSTRAINT accounts_portfolio_id_fkey FOREIGN KEY (portfolio_id) REFERENCES investory.portfolios(id),
    CONSTRAINT accounts_provider_fkey FOREIGN KEY (provider) REFERENCES investory.providers(id)
);

CREATE TABLE investory.app_user_invitations (
    id bigint DEFAULT nextval('investory.app_user_invitations_id_seq'::regclass) NOT NULL,
    email character varying(320) NOT NULL,
    display_name character varying(255) NOT NULL,
    profile_id bigint NOT NULL,
    profile_role character varying(16) NOT NULL,
    token_hash character varying(64) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone,
    created_by bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_app_user_invitations_display_name_not_blank CHECK ((btrim((display_name)::text) <> ''::text)),
    CONSTRAINT chk_app_user_invitations_email_not_blank CHECK ((btrim((email)::text) <> ''::text)),
    CONSTRAINT chk_app_user_invitations_role CHECK (((profile_role)::text = ANY ((ARRAY['OWNER'::character varying, 'USER'::character varying])::text[]))),
    CONSTRAINT app_user_invitations_pkey PRIMARY KEY (id),
    CONSTRAINT app_user_invitations_token_hash_key UNIQUE (token_hash),
    CONSTRAINT app_user_invitations_created_by_fkey FOREIGN KEY (created_by) REFERENCES investory.app_users(id),
    CONSTRAINT app_user_invitations_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES investory.portfolios(id) ON DELETE CASCADE
);

CREATE TABLE investory.asset_source_symbols (
    id bigint DEFAULT nextval('investory.asset_source_symbols_id_seq'::regclass) NOT NULL,
    asset_id bigint NOT NULL,
    source character varying(32) NOT NULL,
    source_symbol character varying(64) NOT NULL,
    source_market character varying(32),
    price_currency character varying(3) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    xtb_symbol character varying(64),
    match_method character varying(64),
    match_status character varying(64),
    confidence character varying(32),
    is_exact_listing boolean DEFAULT false NOT NULL,
    is_alternate_listing boolean DEFAULT false NOT NULL,
    original_exchange character varying(32),
    matched_exchange character varying(32),
    original_currency character varying(3),
    matched_currency character varying(3),
    requires_fx_conversion boolean DEFAULT false NOT NULL,
    price_scale_factor numeric(20,8) DEFAULT 1 NOT NULL,
    scale_reason character varying(255),
    scale_confidence character varying(32),
    scale_observation_count integer,
    scale_median_ratio numeric(20,8),
    scale_dispersion numeric(20,8),
    manual_approval_status character varying(32) DEFAULT 'UNREVIEWED'::character varying NOT NULL,
    substitution_reason character varying(511),
    CONSTRAINT chk_asset_source_symbols_price_scale_factor_positive CHECK ((price_scale_factor > (0)::numeric)),
    CONSTRAINT chk_asset_source_symbols_scale_observation_count_non_negative CHECK (((scale_observation_count IS NULL) OR (scale_observation_count >= 0))),
    CONSTRAINT chk_asset_source_symbols_source_code CHECK (((source)::text ~ '^[A-Z][A-Z0-9_]{0,31}$'::text)),
    CONSTRAINT chk_asset_source_symbols_source_symbol_not_blank CHECK ((btrim((source_symbol)::text) <> ''::text)),
    CONSTRAINT asset_source_symbols_pkey PRIMARY KEY (id),
    CONSTRAINT uq_asset_source_symbols_asset_source_symbol UNIQUE (asset_id, source, source_symbol),
    CONSTRAINT asset_source_symbols_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES investory.assets(id) ON DELETE CASCADE,
    CONSTRAINT asset_source_symbols_matched_currency_fkey FOREIGN KEY (matched_currency) REFERENCES investory.currencies(id),
    CONSTRAINT asset_source_symbols_original_currency_fkey FOREIGN KEY (original_currency) REFERENCES investory.currencies(id),
    CONSTRAINT asset_source_symbols_price_currency_fkey FOREIGN KEY (price_currency) REFERENCES investory.currencies(id)
);

CREATE TABLE investory.import_history (
    id bigint DEFAULT nextval('investory.import_history_id_seq'::regclass) NOT NULL,
    provider character varying(32) NOT NULL,
    file_name character varying(255),
    file_sha256 character varying(255) NOT NULL,
    started_at timestamp with time zone NOT NULL,
    finished_at timestamp with time zone,
    status text,
    rows_total integer,
    rows_failed integer,
    error_message text,
    source_type text,
    source_ref text,
    rows_applied integer,
    attempt_no integer DEFAULT 1 NOT NULL,
    reprocess_of bigint,
    portfolio_id bigint,
    CONSTRAINT chk_import_history_attempt_no_positive CHECK ((attempt_no >= 1)),
    CONSTRAINT chk_import_history_file_sha256_lower_hex CHECK (((file_sha256)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT chk_import_history_finished_after_started CHECK (((finished_at IS NULL) OR (finished_at >= started_at))),
    CONSTRAINT chk_import_history_reprocess_not_self CHECK (((reprocess_of IS NULL) OR (reprocess_of <> id))),
    CONSTRAINT chk_import_history_rows_applied_le_rows_total CHECK (((rows_total IS NULL) OR (rows_applied IS NULL) OR (rows_applied <= rows_total))),
    CONSTRAINT chk_import_history_rows_applied_non_negative CHECK (((rows_applied IS NULL) OR (rows_applied >= 0))),
    CONSTRAINT chk_import_history_rows_balance CHECK (((rows_total IS NULL) OR ((COALESCE(rows_applied, 0) + COALESCE(rows_failed, 0)) <= rows_total))),
    CONSTRAINT chk_import_history_rows_failed_le_rows_total CHECK (((rows_total IS NULL) OR (rows_failed IS NULL) OR (rows_failed <= rows_total))),
    CONSTRAINT chk_import_history_rows_failed_non_negative CHECK (((rows_failed IS NULL) OR (rows_failed >= 0))),
    CONSTRAINT chk_import_history_rows_total_non_negative CHECK (((rows_total IS NULL) OR (rows_total >= 0))),
    CONSTRAINT chk_import_history_source_type_known CHECK (((source_type IS NULL) OR (source_type = ANY (ARRAY['MANUAL'::text, 'API'::text, 'TELEGRAM'::text])))),
    CONSTRAINT chk_import_history_status CHECK (((status IS NULL) OR (status = ANY (ARRAY['STARTED'::text, 'COMPLETED'::text, 'PARTIAL'::text, 'FAILED'::text, 'NOT_READY'::text])))),
    CONSTRAINT chk_import_history_status_completed_lifecycle CHECK (((status IS DISTINCT FROM 'COMPLETED'::text) OR (finished_at IS NOT NULL))),
    CONSTRAINT chk_import_history_status_failed_lifecycle CHECK (((status IS DISTINCT FROM 'FAILED'::text) OR (finished_at IS NOT NULL))),
    CONSTRAINT chk_import_history_status_not_ready_lifecycle CHECK (((status IS DISTINCT FROM 'NOT_READY'::text) OR (finished_at IS NOT NULL))),
    CONSTRAINT chk_import_history_status_partial_lifecycle CHECK (((status IS DISTINCT FROM 'PARTIAL'::text) OR ((finished_at IS NOT NULL) AND (COALESCE(rows_failed, 0) > 0)))),
    CONSTRAINT chk_import_history_status_started_lifecycle CHECK (((status IS DISTINCT FROM 'STARTED'::text) OR (finished_at IS NULL))),
    CONSTRAINT import_history_pkey PRIMARY KEY (id),
    CONSTRAINT import_history_portfolio_id_fkey FOREIGN KEY (portfolio_id) REFERENCES investory.portfolios(id),
    CONSTRAINT import_history_provider_fkey FOREIGN KEY (provider) REFERENCES investory.providers(id),
    CONSTRAINT import_history_reprocess_of_fkey FOREIGN KEY (reprocess_of) REFERENCES investory.import_history(id)
);

CREATE TABLE investory.integration_jobs (
    id bigint DEFAULT nextval('investory.integration_jobs_id_seq'::regclass) NOT NULL,
    integration_instance_id bigint NOT NULL,
    job_type character varying(128) NOT NULL,
    enabled boolean DEFAULT false NOT NULL,
    cron character varying(128) NOT NULL,
    timezone character varying(64) DEFAULT 'Europe/Warsaw'::character varying NOT NULL,
    parameters_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    last_started_at timestamp with time zone,
    last_completed_at timestamp with time zone,
    last_status character varying(32),
    last_error text,
    CONSTRAINT chk_integration_jobs_status CHECK (((last_status IS NULL) OR ((last_status)::text = ANY ((ARRAY['STARTED'::character varying, 'SUCCESS'::character varying, 'FAILED'::character varying, 'SKIPPED'::character varying])::text[])))),
    CONSTRAINT integration_jobs_pkey PRIMARY KEY (id),
    CONSTRAINT ux_integration_jobs_instance_type UNIQUE (integration_instance_id, job_type),
    CONSTRAINT integration_jobs_integration_instance_id_fkey FOREIGN KEY (integration_instance_id) REFERENCES investory.integration_instances(id) ON DELETE CASCADE
);

CREATE TABLE investory.integration_secrets (
    id bigint DEFAULT nextval('investory.integration_secrets_id_seq'::regclass) NOT NULL,
    integration_instance_id bigint NOT NULL,
    secret_name character varying(128) NOT NULL,
    ciphertext text NOT NULL,
    key_version character varying(64) NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT integration_secrets_pkey PRIMARY KEY (id),
    CONSTRAINT ux_integration_secrets_instance_name UNIQUE (integration_instance_id, secret_name),
    CONSTRAINT integration_secrets_integration_instance_id_fkey FOREIGN KEY (integration_instance_id) REFERENCES investory.integration_instances(id) ON DELETE CASCADE
);

CREATE TABLE investory.profile_memberships (
    user_id bigint NOT NULL,
    profile_id bigint NOT NULL,
    role character varying(16) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_profile_memberships_role CHECK (((role)::text = ANY ((ARRAY['OWNER'::character varying, 'USER'::character varying])::text[]))),
    CONSTRAINT pk_profile_memberships PRIMARY KEY (user_id, profile_id),
    CONSTRAINT profile_memberships_profile_id_fkey FOREIGN KEY (profile_id) REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    CONSTRAINT profile_memberships_user_id_fkey FOREIGN KEY (user_id) REFERENCES investory.app_users(id) ON DELETE CASCADE
);

CREATE TABLE investory.account_daily (
    id bigint DEFAULT nextval('investory.account_daily_id_seq'::regclass) NOT NULL,
    account_id bigint NOT NULL,
    snapshot_date date NOT NULL,
    valuation_currency character varying(3) NOT NULL,
    cash_balance numeric(20,8) DEFAULT 0 NOT NULL,
    market_value numeric(20,8) DEFAULT 0 NOT NULL,
    equity numeric(20,8) DEFAULT 0 NOT NULL,
    cost_base numeric(20,8) DEFAULT 0 NOT NULL,
    unrealized_profit numeric(20,8) DEFAULT 0 NOT NULL,
    dividends numeric(20,8) DEFAULT 0 NOT NULL,
    interest numeric(20,8) DEFAULT 0 NOT NULL,
    fees numeric(20,8) DEFAULT 0 NOT NULL,
    taxes numeric(20,8) DEFAULT 0 NOT NULL,
    deposits numeric(20,8) DEFAULT 0 NOT NULL,
    withdrawals numeric(20,8) DEFAULT 0 NOT NULL,
    realized_profit numeric(20,8) DEFAULT 0 NOT NULL,
    daily_profit_amount numeric(20,8) DEFAULT 0 NOT NULL,
    daily_return_pct numeric(20,8),
    portfolio_weight numeric(20,8),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT account_daily_pkey PRIMARY KEY (id),
    CONSTRAINT account_daily_account_id_fkey FOREIGN KEY (account_id) REFERENCES investory.accounts(id) ON DELETE CASCADE,
    CONSTRAINT account_daily_valuation_currency_fkey FOREIGN KEY (valuation_currency) REFERENCES investory.currencies(id)
);

CREATE TABLE investory.asset_price_history (
    asset_id bigint NOT NULL,
    price_date date NOT NULL,
    source character varying(32) NOT NULL,
    source_symbol character varying(64) NOT NULL,
    source_mapping_id bigint,
    price_origin character varying(32) NOT NULL,
    price_currency character varying(3) NOT NULL,
    open_price numeric(20,8),
    high_price numeric(20,8),
    low_price numeric(20,8),
    close_price numeric(20,8) NOT NULL,
    adjusted_close_price numeric(20,8),
    volume numeric(24,8),
    estimated boolean DEFAULT false NOT NULL,
    interpolation_method character varying(32),
    interpolation_left_date date,
    interpolation_right_date date,
    observation_count integer,
    source_date date,
    imported_at timestamp with time zone DEFAULT now() NOT NULL,
    quality_score integer DEFAULT 0 NOT NULL,
    quality_class character varying(64) DEFAULT 'UNRESOLVED'::character varying NOT NULL,
    is_observed boolean DEFAULT true NOT NULL,
    is_proxy boolean DEFAULT false NOT NULL,
    price_scale_factor numeric(20,8) DEFAULT 1 NOT NULL,
    scale_reason character varying(255),
    original_source_symbol character varying(64),
    CONSTRAINT chk_asset_price_history_close_positive CHECK ((close_price > (0)::numeric)),
    CONSTRAINT chk_asset_price_history_estimation_metadata CHECK ((((estimated = false) AND (interpolation_method IS NULL) AND (interpolation_left_date IS NULL) AND (interpolation_right_date IS NULL) AND (is_observed = true)) OR ((estimated = true) AND (is_observed = false) AND ((price_origin)::text = 'INTERPOLATED_XTB'::text) AND (interpolation_method IS NOT NULL) AND (interpolation_left_date IS NOT NULL) AND (interpolation_right_date IS NOT NULL) AND (interpolation_left_date < price_date) AND (interpolation_right_date > price_date)) OR ((estimated = true) AND (is_observed = false) AND ((price_origin)::text = 'STALE_CARRY_FORWARD'::text) AND (interpolation_method IS NULL) AND (interpolation_left_date IS NULL) AND (interpolation_right_date IS NULL)))),
    CONSTRAINT chk_asset_price_history_observation_count_non_negative CHECK (((observation_count IS NULL) OR (observation_count >= 0))),
    CONSTRAINT chk_asset_price_history_ohlc CHECK ((((open_price IS NULL) OR (open_price > (0)::numeric)) AND ((high_price IS NULL) OR (high_price > (0)::numeric)) AND ((low_price IS NULL) OR (low_price > (0)::numeric)) AND ((adjusted_close_price IS NULL) OR (adjusted_close_price > (0)::numeric)) AND ((volume IS NULL) OR (volume >= (0)::numeric)) AND ((high_price IS NULL) OR (low_price IS NULL) OR (high_price >= low_price)) AND ((high_price IS NULL) OR (open_price IS NULL) OR (high_price >= open_price)) AND ((high_price IS NULL) OR (high_price >= close_price)) AND ((low_price IS NULL) OR (open_price IS NULL) OR (low_price <= open_price)) AND ((low_price IS NULL) OR (low_price <= close_price)))),
    CONSTRAINT chk_asset_price_history_price_origin_code CHECK (((price_origin)::text ~ '^[A-Z][A-Z0-9_]{0,31}$'::text)),
    CONSTRAINT chk_asset_price_history_price_scale_factor_positive CHECK ((price_scale_factor > (0)::numeric)),
    CONSTRAINT chk_asset_price_history_quality_class_code CHECK (((quality_class)::text ~ '^[A-Z][A-Z0-9_]{0,63}$'::text)),
    CONSTRAINT chk_asset_price_history_quality_score_range CHECK (((quality_score >= 0) AND (quality_score <= 100))),
    CONSTRAINT chk_asset_price_history_source_code CHECK (((source)::text ~ '^[A-Z][A-Z0-9_]{0,31}$'::text)),
    CONSTRAINT chk_asset_price_history_source_symbol_not_blank CHECK ((btrim((source_symbol)::text) <> ''::text)),
    CONSTRAINT asset_price_history_pkey PRIMARY KEY (asset_id, price_date, source),
    CONSTRAINT asset_price_history_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES investory.assets(id) ON DELETE CASCADE,
    CONSTRAINT asset_price_history_price_currency_fkey FOREIGN KEY (price_currency) REFERENCES investory.currencies(id),
    CONSTRAINT asset_price_history_source_mapping_id_fkey FOREIGN KEY (source_mapping_id) REFERENCES investory.asset_source_symbols(id) ON DELETE RESTRICT
);

CREATE TABLE investory.import_source_files (
    id bigint DEFAULT nextval('investory.import_source_files_id_seq'::regclass) NOT NULL,
    provider character varying(32) NOT NULL,
    import_history_id bigint NOT NULL,
    file_name character varying(255),
    content_type character varying(255),
    file_sha256 character varying(64) NOT NULL,
    original_size bigint NOT NULL,
    raw_payload bytea NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_import_source_files_sha256_lower_hex CHECK (((file_sha256)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT chk_import_source_files_size_non_negative CHECK ((original_size >= 0)),
    CONSTRAINT import_source_files_pkey PRIMARY KEY (id),
    CONSTRAINT import_source_files_import_history_id_fkey FOREIGN KEY (import_history_id) REFERENCES investory.import_history(id) ON DELETE RESTRICT,
    CONSTRAINT import_source_files_provider_fkey FOREIGN KEY (provider) REFERENCES investory.providers(id)
);

CREATE TABLE investory.reconciliation_account_movement_reviews (
    id bigint DEFAULT nextval('investory.reconciliation_account_movement_reviews_id_seq'::regclass) NOT NULL,
    issue_code character varying(64) NOT NULL,
    account_id bigint NOT NULL,
    event_date date NOT NULL,
    event_fingerprint character varying(32) CONSTRAINT reconciliation_account_movement_revi_event_fingerprint_not_null NOT NULL,
    resolution character varying(32) NOT NULL,
    rationale text NOT NULL,
    evidence_reference text CONSTRAINT reconciliation_account_movement_rev_evidence_reference_not_null NOT NULL,
    reviewed_at timestamp with time zone DEFAULT now() NOT NULL,
    reviewed_by character varying(128) NOT NULL,
    CONSTRAINT reconciliation_account_movement_review_evidence_reference_check CHECK ((length(btrim(evidence_reference)) > 0)),
    CONSTRAINT reconciliation_account_movement_reviews_event_fingerprint_check CHECK (((event_fingerprint)::text ~ '^[0-9a-f]{32}$'::text)),
    CONSTRAINT reconciliation_account_movement_reviews_issue_code_check CHECK (((issue_code)::text = ANY ((ARRAY['ACCOUNT_MARKET_VALUE_SPIKE'::character varying, 'ACCOUNT_EQUITY_SPIKE'::character varying])::text[]))),
    CONSTRAINT reconciliation_account_movement_reviews_rationale_check CHECK ((length(btrim(rationale)) > 0)),
    CONSTRAINT reconciliation_account_movement_reviews_resolution_check CHECK (((resolution)::text = ANY ((ARRAY['EXPLAINED'::character varying, 'CORRECTED'::character varying, 'STILL_UNDER_REVIEW'::character varying])::text[]))),
    CONSTRAINT reconciliation_account_movement_reviews_pkey PRIMARY KEY (id),
    CONSTRAINT reconciliation_account_movement_reviews_account_id_fkey FOREIGN KEY (account_id) REFERENCES investory.accounts(id) ON DELETE RESTRICT
);

CREATE TABLE investory.system_audit_runs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    import_history_id bigint,
    trigger_source character varying(32) NOT NULL,
    started_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    finished_at timestamp with time zone,
    status character varying(16) NOT NULL,
    error_count bigint DEFAULT 0 NOT NULL,
    warning_count bigint DEFAULT 0 NOT NULL,
    notification_status character varying(32) NOT NULL,
    summary jsonb,
    CONSTRAINT chk_system_audit_counts_non_negative CHECK (((error_count >= 0) AND (warning_count >= 0))),
    CONSTRAINT chk_system_audit_finished_after_started CHECK (((finished_at IS NULL) OR (finished_at >= started_at))),
    CONSTRAINT chk_system_audit_notification_status CHECK (((notification_status)::text = ANY ((ARRAY['NONE'::character varying, 'READY_WARNING'::character varying, 'READY_ERROR'::character varying])::text[]))),
    CONSTRAINT chk_system_audit_status CHECK (((status)::text = ANY ((ARRAY['STARTED'::character varying, 'HEALTHY'::character varying, 'WARN'::character varying, 'ERROR'::character varying])::text[]))),
    CONSTRAINT system_audit_runs_pkey PRIMARY KEY (id),
    CONSTRAINT system_audit_runs_import_history_id_fkey FOREIGN KEY (import_history_id) REFERENCES investory.import_history(id) ON DELETE SET NULL
);

CREATE TABLE investory.import_source_rows (
    id bigint DEFAULT nextval('investory.import_source_rows_id_seq'::regclass) NOT NULL,
    import_history_id bigint NOT NULL,
    source_file_id bigint,
    provider character varying(32) NOT NULL,
    section_name character varying(255),
    sheet_name character varying(255),
    archive_member_name character varying(512),
    source_row_number integer,
    source_record_id character varying(255),
    source_row_occurrence integer DEFAULT 1 NOT NULL,
    logical_row_sha256 character varying(64),
    raw_text text,
    raw_values jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_import_source_rows_location CHECK (((source_row_number IS NOT NULL) OR (source_record_id IS NOT NULL))),
    CONSTRAINT chk_import_source_rows_logical_sha256_lower_hex CHECK (((logical_row_sha256 IS NULL) OR ((logical_row_sha256)::text ~ '^[0-9a-f]{64}$'::text))),
    CONSTRAINT chk_import_source_rows_occurrence_positive CHECK ((source_row_occurrence >= 1)),
    CONSTRAINT import_source_rows_pkey PRIMARY KEY (id),
    CONSTRAINT import_source_rows_import_history_id_fkey FOREIGN KEY (import_history_id) REFERENCES investory.import_history(id) ON DELETE RESTRICT,
    CONSTRAINT import_source_rows_provider_fkey FOREIGN KEY (provider) REFERENCES investory.providers(id),
    CONSTRAINT import_source_rows_source_file_id_fkey FOREIGN KEY (source_file_id) REFERENCES investory.import_source_files(id) ON DELETE RESTRICT
);

CREATE TABLE investory.system_audit_issues (
    id bigint DEFAULT nextval('investory.system_audit_issues_id_seq'::regclass) NOT NULL,
    audit_run_id uuid NOT NULL,
    check_code character varying(64) NOT NULL,
    severity character varying(16) NOT NULL,
    issue_count bigint NOT NULL,
    required_action character varying(255) NOT NULL,
    details jsonb,
    CONSTRAINT chk_system_audit_issue_count_positive CHECK ((issue_count > 0)),
    CONSTRAINT chk_system_audit_issue_severity CHECK (((severity)::text = ANY ((ARRAY['WARN'::character varying, 'ERROR'::character varying])::text[]))),
    CONSTRAINT system_audit_issues_audit_run_id_check_code_key UNIQUE (audit_run_id, check_code),
    CONSTRAINT system_audit_issues_pkey PRIMARY KEY (id),
    CONSTRAINT system_audit_issues_audit_run_id_fkey FOREIGN KEY (audit_run_id) REFERENCES investory.system_audit_runs(id) ON DELETE CASCADE
);

CREATE TABLE investory.cash_operations (
    id bigint NOT NULL,
    account_id bigint NOT NULL,
    operation investory.cash_operation_type NOT NULL,
    asset_id bigint,
    source_asset_symbol character varying(128),
    broker_symbol character varying(128),
    amount numeric(20,8) NOT NULL,
    currency character varying(3) NOT NULL,
    comment character varying(1023),
    date timestamp with time zone NOT NULL,
    execution_fx_base character varying(3),
    execution_fx_to_currency character varying(3),
    execution_fx_rate numeric(20,8),
    execution_fx_observed_at timestamp with time zone,
    execution_fx_source character varying(32),
    execution_fx_reference character varying(256),
    import_history_id bigint,
    import_source_row_id bigint,
    CONSTRAINT cash_operations_pkey PRIMARY KEY (id),
    CONSTRAINT cash_operations_account_id_fkey FOREIGN KEY (account_id) REFERENCES investory.accounts(id),
    CONSTRAINT cash_operations_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES investory.assets(id),
    CONSTRAINT cash_operations_currency_fkey FOREIGN KEY (currency) REFERENCES investory.currencies(id),
    CONSTRAINT cash_operations_import_history_id_fkey FOREIGN KEY (import_history_id) REFERENCES investory.import_history(id) ON DELETE RESTRICT,
    CONSTRAINT cash_operations_import_source_row_id_fkey FOREIGN KEY (import_source_row_id) REFERENCES investory.import_source_rows(id) ON DELETE RESTRICT
);

CREATE TABLE investory.positions (
    id bigint NOT NULL,
    account_id bigint NOT NULL,
    asset_id bigint NOT NULL,
    source_asset_symbol character varying(128) NOT NULL,
    broker_symbol character varying(128),
    broker_product character varying(255),
    source_position_id character varying(128),
    source_row_occurrence integer DEFAULT 1 NOT NULL,
    operation investory.positions_operation_type NOT NULL,
    settlement_model investory.position_settlement_model DEFAULT 'CASH_SETTLED'::investory.position_settlement_model NOT NULL,
    volume numeric(20,8) NOT NULL,
    price_currency character varying(3) NOT NULL,
    cost_currency character varying(3) NOT NULL,
    profit_currency character varying(3) NOT NULL,
    commission_currency character varying(3) NOT NULL,
    open_time timestamp with time zone NOT NULL,
    open_price numeric(20,8) NOT NULL,
    source_open_price numeric(20,8),
    open_conversion_rate numeric(20,8),
    close_time timestamp with time zone,
    close_price numeric(20,8),
    source_close_price numeric(20,8),
    close_conversion_rate numeric(20,8),
    base_value numeric(20,8),
    purchase_value numeric(20,8),
    sale_value numeric(20,8),
    margin numeric(20,8),
    commission numeric(20,8),
    swap numeric(20,8),
    profit numeric(20,8),
    import_history_id bigint,
    import_source_row_id bigint,
    CONSTRAINT chk_positions_close_price_non_negative CHECK (((close_price IS NULL) OR (close_price >= (0)::numeric))),
    CONSTRAINT chk_positions_close_time_after_open_time CHECK (((close_time IS NULL) OR (open_time IS NULL) OR (close_time >= open_time))),
    CONSTRAINT chk_positions_open_price_non_negative CHECK ((open_price >= (0)::numeric)),
    CONSTRAINT chk_positions_source_row_occurrence_one_based CHECK ((source_row_occurrence >= 1)),
    CONSTRAINT chk_positions_volume_non_negative CHECK ((volume >= (0)::numeric)),
    CONSTRAINT positions_pkey PRIMARY KEY (id),
    CONSTRAINT positions_account_id_fkey FOREIGN KEY (account_id) REFERENCES investory.accounts(id),
    CONSTRAINT positions_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES investory.assets(id),
    CONSTRAINT positions_commission_currency_fkey FOREIGN KEY (commission_currency) REFERENCES investory.currencies(id),
    CONSTRAINT positions_cost_currency_fkey FOREIGN KEY (cost_currency) REFERENCES investory.currencies(id),
    CONSTRAINT positions_import_history_id_fkey FOREIGN KEY (import_history_id) REFERENCES investory.import_history(id) ON DELETE RESTRICT,
    CONSTRAINT positions_import_source_row_id_fkey FOREIGN KEY (import_source_row_id) REFERENCES investory.import_source_rows(id) ON DELETE RESTRICT,
    CONSTRAINT positions_price_currency_fkey FOREIGN KEY (price_currency) REFERENCES investory.currencies(id),
    CONSTRAINT positions_profit_currency_fkey FOREIGN KEY (profit_currency) REFERENCES investory.currencies(id)
);

INSERT INTO investory.currencies (id) VALUES ('USD');

INSERT INTO investory.currencies (id) VALUES ('EUR');

INSERT INTO investory.currencies (id) VALUES ('PLN');

INSERT INTO investory.providers (id) VALUES ('XTB');

INSERT INTO investory.providers (id) VALUES ('IBKR');

INSERT INTO investory.asset_types (id) VALUES ('EQUITY');

INSERT INTO investory.asset_types (id) VALUES ('ETF');

INSERT INTO investory.asset_types (id) VALUES ('BOND');

INSERT INTO investory.asset_types (id) VALUES ('CRYPTOCURRENCY');

INSERT INTO investory.asset_types (id) VALUES ('COMMODITY');

INSERT INTO investory.asset_types (id) VALUES ('CASH');

INSERT INTO investory.asset_types (id) VALUES ('FUND');

INSERT INTO investory.asset_types (id) VALUES ('REIT');

INSERT INTO investory.asset_types (id) VALUES ('DERIVATIVE');

INSERT INTO investory.asset_types (id) VALUES ('INDEX');

INSERT INTO investory.asset_types (id) VALUES ('OTHER');

INSERT INTO investory.fx_configuration (config_key, config_value) VALUES ('daily_history_start', '9999-12-31');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_reporting_scale', 0.000000000000, 'Decimal places used for displayed reconciliation values.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_absolute_tolerance', 0.050000000000, 'Shared absolute monetary tolerance.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_relative_tolerance', 0.000010000000, 'Shared relative monetary tolerance.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_fallback_unrealized_absolute_tolerance', 100.000000000000, 'C6 fallback unrealized-profit absolute tolerance.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_market_value_absolute_tolerance', 400.000000000000, 'Absolute tolerance for daily account market-value reconciliation.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_market_value_relative_tolerance', 0.020000000000, 'Relative tolerance for daily account market-value reconciliation.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_cash_absolute_tolerance', 5.000000000000, 'Absolute tolerance for daily account cash reconciliation.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_quantity_tolerance', 0.000001000000, 'Tolerance for signed quantity matching; not a monetary tolerance.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_trade_cash_absolute_tolerance', 5.000000000000, 'Domain threshold for trade cash settlement differences.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_trade_cash_relative_tolerance', 0.050000000000, 'Domain threshold for trade cash settlement differences.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_carrying_value_absolute_threshold', 250.000000000000, 'Domain threshold for carrying-value outliers.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_carrying_value_relative_threshold', 0.500000000000, 'Domain threshold for carrying-value outliers.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_reorganization_absolute_threshold', 250.000000000000, 'Domain threshold for reorganization value jumps.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_reorganization_relative_threshold', 0.200000000000, 'Domain threshold for reorganization value jumps.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_market_bridge_absolute_threshold', 250.000000000000, 'Domain threshold for market-value bridge outliers.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_market_bridge_relative_threshold', 0.200000000000, 'Domain threshold for market-value bridge outliers.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_position_input_absolute_tolerance', 0.050000000000, 'Input validation tolerance for position notional reconstruction.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_position_input_relative_tolerance', 0.150000000000, 'Input validation tolerance for position notional reconstruction.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_local_sale_price_relative_threshold', 0.050000000000, 'Domain threshold for local sale-vs-carrying-value comparison.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_local_flat_base_jump_relative_threshold', 0.150000000000, 'Domain threshold for local-flat/base-currency valuation jumps.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_price_jump_upper_ratio', 100.000000000000, 'Domain threshold for extreme upward price jumps.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_price_jump_lower_ratio', 0.010000000000, 'Domain threshold for extreme downward price jumps.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_valuation_jump_upper_ratio', 5.000000000000, 'Domain threshold for valuation-jump detection.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_valuation_jump_lower_ratio', 0.200000000000, 'Domain threshold for valuation-jump detection.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_valuation_jump_trade_multiplier', 1.250000000000, 'Domain threshold for valuation-jump trade allowance.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_valuation_jump_absolute_threshold', 250.000000000000, 'Domain threshold for valuation-jump detection.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_source_disagreement_ratio', 1.250000000000, 'Price-source disagreement ratio that requires review.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_interpolation_deviation_ratio', 0.250000000000, 'Interpolation deviation ratio that requires review.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_reconstruction_window_days', 90.000000000000, 'Number of recent calendar days retained by settlement reconciliation reconstruction.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_temporal_short_gap_days', 3.000000000000, 'Maximum elapsed observation gap considered a short-period temporal anomaly.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_fx_extreme_move_ratio', 0.100000000000, 'Absolute canonical daily FX move that creates a review issue.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_fx_isolated_spike_ratio', 0.250000000000, 'FX absolute current/neighbor move for an isolated spike candidate.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_fx_reciprocal_tolerance', 0.000100000000, 'Allowed absolute deviation of a same-date reciprocal FX product from one.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_price_extreme_move_ratio', 0.200000000000, 'Absolute observed short-gap asset-price move that creates a review issue.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_price_isolated_spike_ratio', 1.500000000000, 'Asset current/neighbor ratio for an isolated spike candidate.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_temporal_neighbor_recovery_ratio', 0.100000000000, 'Allowed relative difference between the observations surrounding an isolated spike.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_account_unexplained_move_ratio', 0.200000000000, 'Unexplained short-gap account movement that creates a review issue.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_price_scale_upper_ratio', 100.000000000000, 'Upper ratio treated as a possible price scale discontinuity.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_price_scale_lower_ratio', 0.010000000000, 'Lower ratio treated as a possible price scale discontinuity.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_price_scale_ten_upper_ratio', 10.500000000000, 'Upper boundary for a possible tenfold price scale discontinuity.');

INSERT INTO investory.reconciliation_parameters (parameter_name, numeric_value, description) VALUES ('reconciliation_price_scale_ten_lower_ratio', 9.500000000000, 'Lower boundary for a possible tenfold price scale discontinuity.');

CREATE INDEX idx_market_radar_media_published ON investory.market_radar_media_observation USING btree (published_at DESC);

CREATE INDEX idx_market_radar_media_symbol_symbol ON investory.market_radar_media_symbol USING btree (symbol);

CREATE INDEX idx_market_radar_outcome_symbol_signal ON investory.market_radar_outcome USING btree (symbol, signal_date DESC);

CREATE INDEX idx_market_radar_run_started ON investory.market_radar_run USING btree (started_at DESC);

CREATE INDEX idx_market_radar_snapshot_observed ON investory.market_radar_snapshot USING btree (observed_on DESC);

CREATE INDEX idx_market_radar_theme_snapshot_observed ON investory.market_radar_theme_snapshot USING btree (observed_on DESC);

CREATE INDEX ix_account_daily_account_date_equity ON investory.account_daily USING btree (account_id, snapshot_date, equity);

CREATE INDEX ix_account_daily_snapshot_date ON investory.account_daily USING btree (snapshot_date);

CREATE INDEX ix_accounts_cash_only ON investory.accounts USING btree (cash_only) WHERE cash_only;

CREATE INDEX ix_accounts_portfolio_id_account ON investory.accounts USING btree (portfolio_id, id);

CREATE INDEX ix_app_user_invitations_email ON investory.app_user_invitations USING btree (lower((email)::text), expires_at) WHERE (consumed_at IS NULL);

CREATE INDEX ix_asset_price_history_date ON investory.asset_price_history USING btree (price_date);

CREATE INDEX ix_asset_price_history_estimated ON investory.asset_price_history USING btree (asset_id, price_date) WHERE estimated;

CREATE INDEX ix_asset_price_history_quality ON investory.asset_price_history USING btree (asset_id, price_date, quality_score DESC);

CREATE INDEX ix_asset_price_history_source_mapping ON investory.asset_price_history USING btree (source_mapping_id) WHERE (source_mapping_id IS NOT NULL);

CREATE INDEX ix_asset_price_history_source_symbol ON investory.asset_price_history USING btree (source, source_symbol);

CREATE INDEX ix_cash_operations_account_date ON investory.cash_operations USING btree (account_id, date);

CREATE INDEX ix_cash_operations_account_operation_date ON investory.cash_operations USING btree (account_id, operation, date);

CREATE INDEX ix_cash_operations_asset_id ON investory.cash_operations USING btree (asset_id) WHERE (asset_id IS NOT NULL);

CREATE INDEX ix_cash_operations_import_source_row ON investory.cash_operations USING btree (import_source_row_id) WHERE (import_source_row_id IS NOT NULL);

CREATE INDEX ix_import_history_portfolio_status ON investory.import_history USING btree (portfolio_id, status, finished_at DESC);

CREATE INDEX ix_import_history_status_finished_at ON investory.import_history USING btree (status, finished_at);

CREATE INDEX ix_import_source_files_import_history ON investory.import_source_files USING btree (import_history_id);

CREATE INDEX ix_import_source_rows_import_history ON investory.import_source_rows USING btree (import_history_id);

CREATE INDEX ix_import_source_rows_logical_identity ON investory.import_source_rows USING btree (provider, logical_row_sha256) WHERE (logical_row_sha256 IS NOT NULL);

CREATE INDEX ix_import_source_rows_record_identity ON investory.import_source_rows USING btree (source_record_id) WHERE (source_record_id IS NOT NULL);

CREATE INDEX ix_import_source_rows_source_file ON investory.import_source_rows USING btree (source_file_id);

CREATE INDEX ix_integration_instances_enabled ON investory.integration_instances USING btree (enabled);

CREATE INDEX ix_integration_instances_owner ON investory.integration_instances USING btree (owner_id);

CREATE INDEX ix_integration_jobs_enabled ON investory.integration_jobs USING btree (enabled);

CREATE INDEX ix_notification_event_dispatch ON investory.notification_event USING btree (next_attempt_at, created_at) WHERE ((delivery_state)::text = ANY ((ARRAY['PENDING'::character varying, 'RETRYABLE'::character varying])::text[]));

CREATE INDEX ix_notification_event_processing_lease ON investory.notification_event USING btree (processing_lease_until) WHERE ((delivery_state)::text = 'PROCESSING'::text);

CREATE INDEX ix_notification_event_source ON investory.notification_event USING btree (source_entity_type, source_entity_id);

CREATE INDEX ix_portfolios_user_id ON investory.portfolios USING btree (user_id);

CREATE INDEX ix_positions_account_asset_close_time ON investory.positions USING btree (account_id, asset_id, close_time);

CREATE INDEX ix_positions_account_close_time ON investory.positions USING btree (account_id, close_time);

CREATE INDEX ix_positions_account_open_time ON investory.positions USING btree (account_id, open_time);

CREATE INDEX ix_positions_asset_close_time ON investory.positions USING btree (asset_id, close_time);

CREATE INDEX ix_positions_import_source_row ON investory.positions USING btree (import_source_row_id) WHERE (import_source_row_id IS NOT NULL);

CREATE INDEX ix_positions_source_position_id ON investory.positions USING btree (account_id, source_position_id) WHERE (source_position_id IS NOT NULL);

CREATE INDEX ix_profile_memberships_profile ON investory.profile_memberships USING btree (profile_id, role);

CREATE INDEX ix_reconciliation_account_movement_reviews_event ON investory.reconciliation_account_movement_reviews USING btree (issue_code, account_id, event_date, event_fingerprint, id DESC);

CREATE INDEX ix_system_audit_runs_import_started ON investory.system_audit_runs USING btree (import_history_id, started_at DESC) WHERE (import_history_id IS NOT NULL);

CREATE INDEX ix_system_audit_runs_status_finished ON investory.system_audit_runs USING btree (status, finished_at DESC);

CREATE UNIQUE INDEX uq_app_users_email_ci ON investory.app_users USING btree (lower((email)::text)) WHERE (email IS NOT NULL);

CREATE UNIQUE INDEX uq_app_users_google_subject ON investory.app_users USING btree (google_subject) WHERE (google_subject IS NOT NULL);

CREATE UNIQUE INDEX ux_account_daily_account_date ON investory.account_daily USING btree (account_id, snapshot_date);

CREATE UNIQUE INDEX ux_asset_source_symbols_source_symbol_ci ON investory.asset_source_symbols USING btree (source, upper((source_symbol)::text));

CREATE UNIQUE INDEX ux_assets_figi ON investory.assets USING btree (upper((figi)::text)) WHERE ((figi IS NOT NULL) AND (btrim((figi)::text) <> ''::text));

CREATE UNIQUE INDEX ux_assets_ibkr ON investory.assets USING btree (ibkr);

CREATE UNIQUE INDEX ux_assets_isin ON investory.assets USING btree (upper((isin)::text)) WHERE ((isin IS NOT NULL) AND (btrim((isin)::text) <> ''::text));

CREATE UNIQUE INDEX ux_assets_symbol ON investory.assets USING btree (symbol);

CREATE UNIQUE INDEX ux_assets_ticker_exchange_mic ON investory.assets USING btree (upper((ticker)::text), upper((exchange_mic)::text)) WHERE ((exchange_mic IS NOT NULL) AND (btrim((exchange_mic)::text) <> ''::text));

CREATE UNIQUE INDEX ux_benchmark_monthly_closes_symbol_month ON investory.benchmark_monthly_closes USING btree (symbol, month);

CREATE UNIQUE INDEX ux_exchange_rates_execution_observation ON investory.exchange_rates USING btree (rate_date, base, to_currency, source, method, COALESCE(source_reference, ''::character varying)) WHERE ((purpose)::text = 'EXECUTION'::text);

CREATE UNIQUE INDEX ux_exchange_rates_valuation ON investory.exchange_rates USING btree (rate_date, base, to_currency) WHERE ((purpose)::text = 'VALUATION'::text);

CREATE UNIQUE INDEX ux_import_history_provider_sha256_attempt ON investory.import_history USING btree (portfolio_id, provider, file_sha256, attempt_no);

CREATE UNIQUE INDEX ux_import_source_files_provider_sha256 ON investory.import_source_files USING btree (provider, file_sha256);

CREATE UNIQUE INDEX ux_integration_instances_global_plugin ON investory.integration_instances USING btree (plugin_id, plugin_type) WHERE (owner_id IS NULL);
