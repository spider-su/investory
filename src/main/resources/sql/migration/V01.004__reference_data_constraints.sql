SET search_path TO investory, public;

-- The current baseline already enforces the reference contracts raised by the
-- schema review:
--   accounts.currency       -> currencies(id)
--   accounts.provider       -> providers(id)
--   assets.currency         -> currencies(id)
--   assets.asset_type       -> asset_types(id)
--   cash_operations.currency -> currencies(id)
--   cash_operations.operation uses cash_operation_type
--   positions currency columns -> currencies(id)
--   positions.operation uses positions_operation_type
-- Cash operations and positions already store timestamptz values.
-- There is intentionally no separate transactions table: immutable broker events
-- are represented by cash_operations and lot-level positions.

COMMENT ON COLUMN investory.positions.open_price IS
    'Per-lot broker open price. This is not an account/instrument average cost and must not be interpreted using FIFO or LIFO.';

COMMENT ON COLUMN investory.positions.purchase_value IS
    'Per-lot broker-reported purchase value. Portfolio cost basis is derived from individual position lots; no aggregate avg_cost convention is stored here.';

COMMENT ON TABLE investory.positions IS
    'Broker position lots, both active and closed. Multiple rows for one account and asset are valid; reconciliation must operate at source lot/event identity, not enforce one row per account and asset.';

-- Exact duplicate lot detector. It does not reject legitimate partial closes or
-- repeated lots. It exposes rows whose complete source identity and economics are
-- duplicated under different primary keys so imports/reconciliation can flag them.
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
