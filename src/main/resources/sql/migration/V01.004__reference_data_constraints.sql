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

COMMENT ON COLUMN investory.positions.open_price IS
    'Per-lot broker open price. This is not an account/instrument average cost and must not be interpreted using FIFO or LIFO.';

COMMENT ON COLUMN investory.positions.purchase_value IS
    'Per-lot broker-reported purchase value. Portfolio cost basis is derived from individual position lots; no aggregate avg_cost convention is stored here.';

COMMENT ON TABLE investory.positions IS
    'Broker position lots, both active and closed. Multiple rows for one account and asset are valid; reconciliation must operate at source lot/event identity, not enforce one row per account and asset.';

-- Exact duplicate lot detector. A unique constraint on (account_id, asset_id)
-- would reject valid multiple lots, partial closes, BUY/SELL pairs, and mixed
-- settlement models. This view instead detects duplicate rows at full lot identity.
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

-- Event and audit instants use timestamptz. This diagnostic catches future schema
-- changes that accidentally introduce timezone-naive timestamp columns.
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
