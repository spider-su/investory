-- Append-only hardening migration for shared baseline databases.
-- Introduces stricter lifecycle checks and critical lookup indexes.

UPDATE investory.assets
SET active = true
WHERE active IS NULL;

ALTER TABLE investory.assets
    ALTER COLUMN active SET DEFAULT true,
    ALTER COLUMN active SET NOT NULL;

ALTER TABLE investory.import_history
    ADD CONSTRAINT chk_import_history_file_sha256_lower_hex_v01004
        CHECK (file_sha256 ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT chk_import_history_rows_applied_le_rows_total_v01004
        CHECK (rows_total IS NULL OR rows_applied IS NULL OR rows_applied <= rows_total),
    ADD CONSTRAINT chk_import_history_rows_failed_le_rows_total_v01004
        CHECK (rows_total IS NULL OR rows_failed IS NULL OR rows_failed <= rows_total),
    ADD CONSTRAINT chk_import_history_rows_balance_v01004
        CHECK (
            rows_total IS NULL
            OR COALESCE(rows_applied, 0) + COALESCE(rows_failed, 0) <= rows_total
        ),
    ADD CONSTRAINT chk_import_history_source_type_known_v01004
        CHECK (source_type IS NULL OR source_type IN ('MANUAL', 'API', 'TELEGRAM')),
    ADD CONSTRAINT chk_import_history_status_started_lifecycle_v01004
        CHECK (status IS DISTINCT FROM 'STARTED' OR finished_at IS NULL),
    ADD CONSTRAINT chk_import_history_status_completed_lifecycle_v01004
        CHECK (status IS DISTINCT FROM 'COMPLETED' OR finished_at IS NOT NULL),
    ADD CONSTRAINT chk_import_history_status_partial_lifecycle_v01004
        CHECK (
            status IS DISTINCT FROM 'PARTIAL'
            OR (finished_at IS NOT NULL AND COALESCE(rows_failed, 0) > 0)
        ),
    ADD CONSTRAINT chk_import_history_status_failed_lifecycle_v01004
        CHECK (status IS DISTINCT FROM 'FAILED' OR finished_at IS NOT NULL);

CREATE INDEX ix_accounts_portfolio_id
    ON investory.accounts(portfolio_id);

CREATE INDEX ix_cash_operations_account_date
    ON investory.cash_operations(account_id, date);

CREATE INDEX ix_cash_operations_asset_id
    ON investory.cash_operations(asset_id)
    WHERE asset_id IS NOT NULL;

CREATE INDEX ix_cash_operations_account_operation_date
    ON investory.cash_operations(account_id, operation, date);

CREATE INDEX ix_positions_account_close_time
    ON investory.positions(account_id, close_time);

CREATE INDEX ix_positions_asset_close_time
    ON investory.positions(asset_id, close_time);

CREATE INDEX ix_positions_account_open_time
    ON investory.positions(account_id, open_time);

CREATE INDEX ix_positions_account_asset_close_time
    ON investory.positions(account_id, asset_id, close_time);

CREATE INDEX ix_import_history_status_finished_at
    ON investory.import_history(status, finished_at);

CREATE INDEX ix_account_daily_account_snapshot_date_desc
    ON investory.account_daily(account_id, snapshot_date DESC);

