SET search_path TO investory, public;







CREATE OR REPLACE VIEW investory.reporting_system_audit AS
SELECT
    run.id AS audit_run_id,
    run.import_history_id,
    import_row.provider,
    import_row.file_name,
    import_row.status AS import_status,
    run.trigger_source,
    run.started_at,
    run.finished_at,
    run.status AS audit_status,
    run.error_count,
    run.warning_count,
    run.notification_status,
    run.notification_status <> 'NONE' AS notification_ready,
    run.summary AS structured_log_payload
FROM investory.system_audit_runs run
LEFT JOIN investory.import_history import_row ON import_row.id = run.import_history_id;

COMMENT ON VIEW investory.reporting_system_audit IS
    'Canonical persisted audit API. structured_log_payload can be logged as JSON and notification_ready can drive Telegram, email, or other adapters outside the database.';
CREATE OR REPLACE FUNCTION investory.reject_import_evidence_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Import evidence is immutable: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME;
END;
$$;
CREATE TRIGGER trg_import_source_files_immutable
    BEFORE UPDATE OR DELETE ON investory.import_source_files
    FOR EACH ROW EXECUTE FUNCTION investory.reject_import_evidence_mutation();
CREATE TRIGGER trg_import_source_rows_immutable
    BEFORE UPDATE OR DELETE ON investory.import_source_rows
    FOR EACH ROW EXECUTE FUNCTION investory.reject_import_evidence_mutation();

CREATE INDEX ix_cash_operations_import_source_row
    ON investory.cash_operations(import_source_row_id)
    WHERE import_source_row_id IS NOT NULL;
CREATE INDEX ix_positions_import_source_row
    ON investory.positions(import_source_row_id)
    WHERE import_source_row_id IS NOT NULL;

-- Final clean-baseline provenance rules. Reprocessed attempts use the latest
-- source artifact, and duplicate identity includes the raw source values.
CREATE OR REPLACE VIEW investory.reporting_import_provenance_issues AS
WITH latest_attempt AS (
    SELECT DISTINCT ON (provider, file_sha256)
           id, provider, file_sha256
    FROM investory.import_history
    ORDER BY provider, file_sha256, attempt_no DESC, id DESC
),
latest_source_rows AS (
    SELECT r.*
    FROM investory.import_source_rows r
    JOIN latest_attempt a ON a.id = r.import_history_id
)
SELECT 'CASH_OPERATION_MISSING_IMPORT'::text AS issue_code, c.id::text AS financial_row_id,
       c.import_history_id, c.import_source_row_id, 'cash_operations'::text AS financial_table
FROM investory.cash_operations c
WHERE c.import_history_id IS NULL AND c.import_source_row_id IS NOT NULL
UNION ALL
SELECT 'CASH_OPERATION_MISSING_SOURCE_ROW', c.id::text, c.import_history_id,
       c.import_source_row_id, 'cash_operations'
FROM investory.cash_operations c
WHERE c.import_history_id IS NOT NULL AND c.import_source_row_id IS NULL
UNION ALL
SELECT 'POSITION_MISSING_IMPORT', p.id::text, p.import_history_id,
       p.import_source_row_id, 'positions'
FROM investory.positions p
WHERE p.import_history_id IS NULL AND p.import_source_row_id IS NOT NULL
UNION ALL
SELECT 'POSITION_MISSING_SOURCE_ROW', p.id::text, p.import_history_id,
       p.import_source_row_id, 'positions'
FROM investory.positions p
WHERE p.import_history_id IS NOT NULL AND p.import_source_row_id IS NULL
UNION ALL
SELECT 'SOURCE_ROW_WRONG_IMPORT', r.id::text, r.import_history_id,
       r.id, 'import_source_rows'
FROM latest_source_rows r
JOIN investory.import_source_files f ON f.id = r.source_file_id
JOIN investory.import_history h ON h.id = r.import_history_id
WHERE f.provider <> h.provider OR f.file_sha256 <> h.file_sha256
UNION ALL
SELECT 'ORPHAN_SOURCE_ROW', r.id::text, r.import_history_id,
       r.id, 'import_source_rows'
FROM latest_source_rows r
LEFT JOIN investory.cash_operations c ON c.import_source_row_id = r.id
LEFT JOIN investory.positions p ON p.import_source_row_id = r.id
WHERE c.id IS NULL AND p.id IS NULL
UNION ALL
SELECT 'CANONICAL_ROW_WRONG_IMPORT', c.id::text, c.import_history_id,
       c.import_source_row_id, 'cash_operations'
FROM investory.cash_operations c
JOIN investory.import_source_rows r ON r.id = c.import_source_row_id
WHERE c.import_history_id <> r.import_history_id
UNION ALL
SELECT 'CANONICAL_POSITION_WRONG_IMPORT', p.id::text, p.import_history_id,
       p.import_source_row_id, 'positions'
FROM investory.positions p
JOIN investory.import_source_rows r ON r.id = p.import_source_row_id
WHERE p.import_history_id <> r.import_history_id
UNION ALL
SELECT 'SOURCE_FILE_CHECKSUM_MISMATCH', f.id::text, f.import_history_id,
       NULL::bigint, 'import_source_files'
FROM investory.import_source_files f
JOIN investory.import_history h ON h.id = f.import_history_id
WHERE f.file_sha256 <> h.file_sha256
UNION ALL
SELECT 'DUPLICATE_SOURCE_IDENTITY',
       string_agg(r.id::text, ',' ORDER BY r.id),
       min(r.import_history_id), NULL, 'import_source_rows'
FROM latest_source_rows r
WHERE r.source_record_id IS NOT NULL
GROUP BY r.provider, r.archive_member_name, r.section_name, r.sheet_name,
         r.source_record_id, r.source_row_occurrence, r.raw_values
HAVING count(*) > 1;

COMMENT ON VIEW investory.reporting_import_provenance_issues IS
    'Diagnostic view. Reprocessed attempts may reuse an immutable source artifact; current provenance is checked on the latest attempt per file checksum and duplicate identity includes raw source values.';
