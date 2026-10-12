-- The old singleton row cannot be attributed to a portfolio with certainty.
DELETE FROM investory.yahoo_export_state;

ALTER TABLE investory.yahoo_export_state
    DROP CONSTRAINT yahoo_export_state_id_check,
    DROP CONSTRAINT yahoo_export_state_pkey;

ALTER TABLE investory.yahoo_export_state
    RENAME COLUMN id TO portfolio_id;

ALTER TABLE investory.yahoo_export_state
    ALTER COLUMN portfolio_id TYPE bigint,
    ADD CONSTRAINT yahoo_export_state_pkey PRIMARY KEY (portfolio_id),
    ADD CONSTRAINT yahoo_export_state_portfolio_id_fkey
        FOREIGN KEY (portfolio_id) REFERENCES investory.portfolios(id) ON DELETE CASCADE;

ALTER TABLE investory.import_history
    ADD CONSTRAINT uq_import_history_id_provider UNIQUE (id, provider);

ALTER TABLE investory.import_source_files
    ADD CONSTRAINT uq_import_source_files_id_history_provider
        UNIQUE (id, import_history_id, provider),
    DROP CONSTRAINT import_source_files_import_history_id_fkey,
    ADD CONSTRAINT import_source_files_history_provider_fkey
        FOREIGN KEY (import_history_id, provider)
        REFERENCES investory.import_history(id, provider) ON DELETE RESTRICT;

ALTER TABLE investory.import_source_rows
    ADD CONSTRAINT uq_import_source_rows_id_history UNIQUE (id, import_history_id),
    DROP CONSTRAINT import_source_rows_import_history_id_fkey,
    ADD CONSTRAINT import_source_rows_history_provider_fkey
        FOREIGN KEY (import_history_id, provider)
        REFERENCES investory.import_history(id, provider) ON DELETE RESTRICT,
    DROP CONSTRAINT import_source_rows_source_file_id_fkey,
    ADD CONSTRAINT import_source_rows_file_history_provider_fkey
        FOREIGN KEY (source_file_id, import_history_id, provider)
        REFERENCES investory.import_source_files(id, import_history_id, provider)
        ON DELETE RESTRICT;

ALTER TABLE investory.cash_operations
    ADD CONSTRAINT chk_cash_operations_source_row_requires_import
        CHECK (import_source_row_id IS NULL OR import_history_id IS NOT NULL),
    DROP CONSTRAINT cash_operations_import_source_row_id_fkey,
    ADD CONSTRAINT cash_operations_source_row_history_fkey
        FOREIGN KEY (import_source_row_id, import_history_id)
        REFERENCES investory.import_source_rows(id, import_history_id) ON DELETE RESTRICT;

ALTER TABLE investory.positions
    ADD CONSTRAINT chk_positions_source_row_requires_import
        CHECK (import_source_row_id IS NULL OR import_history_id IS NOT NULL),
    DROP CONSTRAINT positions_import_source_row_id_fkey,
    ADD CONSTRAINT positions_source_row_history_fkey
        FOREIGN KEY (import_source_row_id, import_history_id)
        REFERENCES investory.import_source_rows(id, import_history_id) ON DELETE RESTRICT;

CREATE INDEX ix_import_source_files_history_provider
    ON investory.import_source_files (import_history_id, provider);

CREATE INDEX ix_import_source_rows_history_provider
    ON investory.import_source_rows (import_history_id, provider);

CREATE INDEX ix_import_source_rows_file_history_provider
    ON investory.import_source_rows (source_file_id, import_history_id, provider)
    WHERE source_file_id IS NOT NULL;

CREATE INDEX ix_cash_operations_source_row_history
    ON investory.cash_operations (import_source_row_id, import_history_id)
    WHERE import_source_row_id IS NOT NULL;

CREATE INDEX ix_positions_source_row_history
    ON investory.positions (import_source_row_id, import_history_id)
    WHERE import_source_row_id IS NOT NULL;
