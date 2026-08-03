SET search_path TO investory, public;

-- The current baseline already enforces the reference and completeness contracts
-- raised by the schema review:
--   accounts.currency        NOT NULL -> currencies(id)
--   accounts.provider        NOT NULL -> providers(id)
--   accounts.name            NOT NULL
--   accounts.owner           NOT NULL
--   assets.name              NOT NULL
--   assets.symbol            NOT NULL + unique index
--   assets.currency          NOT NULL -> currencies(id)
--   assets.asset_type        NOT NULL -> asset_types(id)
--   cash_operations.currency NOT NULL -> currencies(id)
--   cash_operations.operation uses cash_operation_type
--   positions currency columns NOT NULL -> currencies(id)
--   positions.operation uses positions_operation_type
-- Cash operations and positions already store timestamptz values.
-- There is intentionally no separate transactions table: immutable broker events
-- are represented by cash_operations and lot-level positions.

COMMENT ON COLUMN investory.accounts.provider IS
    'Required canonical broker/provider code. Values are constrained by providers(id); null or free-text broker labels are not allowed.';

COMMENT ON COLUMN investory.assets.name IS
    'Required canonical instrument display name. Incomplete unnamed assets are rejected by the NOT NULL constraint.';

COMMENT ON TYPE investory.cash_operation_type IS
    'Supported raw cash-event vocabulary. PostgreSQL rejects labels outside this enum. UNKNOWN is an explicit quarantine value: it preserves an imported event but excludes it from trusted classification until reviewed.';

COMMENT ON TYPE investory.positions_operation_type IS
    'Supported position direction vocabulary. Only BUY and SELL are accepted; unsupported labels fail at insert time.';

COMMENT ON TYPE investory.position_settlement_model IS
    'Position valuation contract. CASH_SETTLED represents owned notional, RESULT_ONLY represents P/L-only contracts, and UNCLASSIFIED is a review-required quarantine state.';

COMMENT ON COLUMN investory.positions.open_price IS
    'Per-lot broker open price. This is not an account/instrument average cost and must not be interpreted using FIFO or LIFO.';

COMMENT ON COLUMN investory.positions.purchase_value IS
    'Per-lot broker-reported purchase value. Portfolio cost basis is derived from individual position lots; no aggregate avg_cost convention is stored here.';

COMMENT ON TABLE investory.positions IS
    'Broker position lots, both active and closed. Multiple rows for one account and asset are valid; reconciliation must operate at source lot/event identity, not enforce one row per account and asset. Position state supports imported BUY/SELL lots, partial closes, explicit settlement models, currencies, commissions, swaps, margin, and realized profit. Corporate actions such as splits, mergers, spin-offs, symbol changes, and tax-lot elections are not inferred unless the broker importer emits explicit adjusted rows.';

CREATE OR REPLACE VIEW investory.reporting_position_lot_duplicates AS
SELECT
    account_id,
    asset_id,
    source_position_id,
    source_row_occurrence,
    operation,
    settlement_model,
    open_time,
    close_time,
    volume,
    open_price,
    purchase_value,
    price_currency,
    cost_currency,
    profit_currency,
    commission_currency,
    count(*) AS duplicate_count,
    array_agg(id ORDER BY id) AS position_ids
FROM investory.positions
GROUP BY
    account_id,
    asset_id,
    source_position_id,
    source_row_occurrence,
    operation,
    settlement_model,
    open_time,
    close_time,
    volume,
    open_price,
    purchase_value,
    price_currency,
    cost_currency,
    profit_currency,
    commission_currency
HAVING count(*) > 1;

COMMENT ON VIEW investory.reporting_position_lot_duplicates IS
    'Diagnostic view for exact duplicate position lots imported under different IDs. Empty result is the expected healthy state.';

CREATE OR REPLACE VIEW investory.reporting_unsupported_transaction_states AS
SELECT
    'cash_operations'::varchar(32) AS source_table,
    co.id AS row_id,
    co.account_id,
    co.asset_id,
    co.date AS occurred_at,
    'UNKNOWN_CASH_OPERATION'::varchar(64) AS issue_code,
    co.operation::text AS raw_state,
    co.comment AS evidence
FROM investory.cash_operations co
WHERE co.operation = 'UNKNOWN'
UNION ALL
SELECT
    'cash_operations'::varchar(32),
    nco.operation_id,
    nco.account_id,
    nco.asset_id,
    nco.date,
    'UNCLASSIFIED_CASH_OPERATION'::varchar(64),
    nco.raw_operation::text,
    nco.comment
FROM investory.normalized_cash_operations nco
WHERE nco.normalized_category = 'UNCLASSIFIED'
UNION ALL
SELECT
    'positions'::varchar(32),
    p.id,
    p.account_id,
    p.asset_id,
    p.open_time,
    'UNCLASSIFIED_SETTLEMENT_MODEL'::varchar(64),
    p.settlement_model::text,
    p.broker_product
FROM investory.positions p
WHERE p.settlement_model = 'UNCLASSIFIED';

COMMENT ON VIEW investory.reporting_unsupported_transaction_states IS
    'Required review queue for preserved but unsupported or unresolved ledger states. Empty result is expected before trusted reporting; unknown enum labels are rejected directly by PostgreSQL.';

CREATE OR REPLACE VIEW investory.reporting_timezone_naive_columns AS
SELECT
    table_name,
    column_name,
    data_type
FROM information_schema.columns
WHERE table_schema = 'investory'
  AND data_type = 'timestamp without time zone';

COMMENT ON VIEW investory.reporting_timezone_naive_columns IS
    'Schema diagnostic. Empty result is expected; event and audit instants must use timestamp with time zone. Date-only business periods remain DATE.';

CREATE UNIQUE INDEX IF NOT EXISTS ux_assets_isin
    ON investory.assets (upper(isin))
    WHERE isin IS NOT NULL AND btrim(isin) <> '';

CREATE UNIQUE INDEX IF NOT EXISTS ux_assets_figi
    ON investory.assets (upper(figi))
    WHERE figi IS NOT NULL AND btrim(figi) <> '';

CREATE UNIQUE INDEX IF NOT EXISTS ux_assets_ticker_exchange_mic
    ON investory.assets (upper(ticker), upper(exchange_mic))
    WHERE exchange_mic IS NOT NULL AND btrim(exchange_mic) <> '';

COMMENT ON COLUMN investory.assets.symbol IS
    'Stable canonical application symbol used for display and provider routing. It is not assumed to be a globally unique bare ticker; exchange-qualified symbols are preferred.';

COMMENT ON COLUMN investory.assets.exchange_mic IS
    'ISO 10383 market identifier used with ticker to identify a concrete exchange listing. When present, ticker plus exchange_mic is unique.';

COMMENT ON COLUMN investory.assets.isin IS
    'Optional global security identifier. Nonblank ISIN values are unique across assets.';

COMMENT ON COLUMN investory.assets.figi IS
    'Optional global security identifier. Nonblank FIGI values are unique across assets.';

CREATE TABLE investory.app_users (
    id           bigserial PRIMARY KEY,
    username     varchar(64) NOT NULL UNIQUE,
    display_name varchar(255) NOT NULL,
    active       boolean NOT NULL DEFAULT true,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_app_users_username_not_blank CHECK (btrim(username) <> ''),
    CONSTRAINT chk_app_users_display_name_not_blank CHECK (btrim(display_name) <> '')
);

INSERT INTO investory.app_users (id, username, display_name)
VALUES (1, 'alex', 'Alex');

SELECT setval(
    pg_get_serial_sequence('investory.app_users', 'id'),
    (SELECT max(id) FROM investory.app_users),
    true
);

ALTER TABLE investory.portfolios
    ADD COLUMN user_id bigint;

UPDATE investory.portfolios
SET user_id = 1
WHERE user_id IS NULL;

ALTER TABLE investory.portfolios
    ALTER COLUMN user_id SET NOT NULL,
    ADD CONSTRAINT fk_portfolios_user
        FOREIGN KEY (user_id) REFERENCES investory.app_users(id);

CREATE INDEX ix_portfolios_user_id
    ON investory.portfolios(user_id);

COMMENT ON TABLE investory.app_users IS
    'Basic application identity boundary for future multi-user support. Authentication and authorization are intentionally out of scope.';

COMMENT ON COLUMN investory.portfolios.user_id IS
    'Required portfolio owner. Accounts, cash operations, and position lots inherit user ownership through portfolio_id.';

CREATE INDEX IF NOT EXISTS ix_positions_account_open_close
    ON investory.positions (account_id, open_time, close_time);

COMMENT ON INDEX investory.ix_positions_account_open_close IS
    'Supports point-in-time position scans by account. Large recurring reports should continue to use refreshed account_daily and materialized reporting projections instead of repeatedly reconstructing the full lot history.';

CREATE OR REPLACE VIEW investory.reporting_monthly_import_review AS
SELECT
    'UNSUPPORTED_TRANSACTION_STATE'::varchar(64) AS check_code,
    count(*)::bigint AS issue_count,
    'Review UNKNOWN or UNCLASSIFIED imported ledger states.'::varchar(255) AS required_action
FROM investory.reporting_unsupported_transaction_states
UNION ALL
SELECT
    'DUPLICATE_POSITION_LOT'::varchar(64),
    count(*)::bigint,
    'Review duplicate source lots before trusting position and P/L totals.'::varchar(255)
FROM investory.reporting_position_lot_duplicates
UNION ALL
SELECT
    'TIMEZONE_NAIVE_COLUMN'::varchar(64),
    count(*)::bigint,
    'Convert event or audit timestamps to timestamptz.'::varchar(255)
FROM investory.reporting_timezone_naive_columns;

COMMENT ON VIEW investory.reporting_monthly_import_review IS
    'Monthly import-review checklist. All issue_count values should be zero before month-end reporting is accepted. Corporate actions remain a manual broker-statement review because they are not inferred automatically.';
