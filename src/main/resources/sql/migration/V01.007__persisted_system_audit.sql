SET search_path TO investory, public;







DO $$
DECLARE
    import_id bigint;
BEGIN
    FOR import_id IN
        SELECT id
        FROM investory.import_history
        WHERE status IN ('COMPLETED', 'PARTIAL', 'FAILED', 'NOT_READY')
        ORDER BY id
    LOOP
        PERFORM investory.run_system_audit(import_id, 'MIGRATION_BACKFILL');
    END LOOP;
END;
$$;

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
