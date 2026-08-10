SET search_path TO investory, public;

CREATE TABLE investory.system_audit_runs (
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

CREATE INDEX ix_system_audit_runs_import_started
    ON investory.system_audit_runs(import_history_id, started_at DESC)
    WHERE import_history_id IS NOT NULL;
CREATE INDEX ix_system_audit_runs_status_finished
    ON investory.system_audit_runs(status, finished_at DESC);

CREATE TABLE investory.system_audit_issues (
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
    'Persisted system-level validation result created automatically whenever an import reaches a terminal status.';
COMMENT ON TABLE investory.system_audit_issues IS
    'Non-zero actionable checks captured for one system audit run. Stable check codes are suitable for notifications and structured logging.';

CREATE OR REPLACE FUNCTION investory.run_system_audit(
    p_import_history_id bigint DEFAULT NULL,
    p_trigger_source varchar(32) DEFAULT 'MANUAL'
) RETURNS uuid
LANGUAGE plpgsql
AS $$
DECLARE
    audit_id uuid := gen_random_uuid();
    import_row investory.import_history%ROWTYPE;
    errors bigint;
    warnings bigint;
    error_checks bigint;
    warning_checks bigint;
    final_status varchar(16);
    notification varchar(32);
    payload jsonb;
BEGIN
    IF p_import_history_id IS NOT NULL THEN
        SELECT * INTO STRICT import_row
        FROM investory.import_history
        WHERE id = p_import_history_id;

    END IF;

    INSERT INTO investory.system_audit_runs(
        id, import_history_id, trigger_source, status, notification_status
    ) VALUES (
        audit_id, p_import_history_id, p_trigger_source, 'STARTED', 'NONE'
    );

    INSERT INTO investory.system_audit_issues(
        audit_run_id, check_code, severity, issue_count, required_action, details
    )
    SELECT
        audit_id,
        review.check_code,
        CASE
            WHEN review.check_code IN (
                'UNSUPPORTED_TRANSACTION_STATE',
                'DUPLICATE_POSITION_LOT',
                'TIMEZONE_NAIVE_COLUMN',
                'VALUATION_INPUT_ERROR'
            ) THEN 'ERROR'
            WHEN review.check_code = 'VALUATION_INPUT_WARNING' THEN 'WARN'
            ELSE 'ERROR'
        END,
        review.issue_count,
        review.required_action,
        jsonb_build_object('source', 'reporting_monthly_import_review')
    FROM investory.reporting_monthly_import_review review
    WHERE review.issue_count > 0;

    IF p_import_history_id IS NOT NULL AND import_row.status IN ('FAILED', 'NOT_READY') THEN
        INSERT INTO investory.system_audit_issues(
            audit_run_id, check_code, severity, issue_count, required_action, details
        ) VALUES (
            audit_id,
            'IMPORT_FAILED',
            'ERROR',
            1,
            'Review the import error and rerun the source file after correction.',
            jsonb_build_object(
                'provider', import_row.provider,
                'file_name', import_row.file_name,
                'error_message', import_row.error_message
            )
        ) ON CONFLICT (audit_run_id, check_code) DO NOTHING;
    ELSIF p_import_history_id IS NOT NULL AND import_row.status = 'PARTIAL' THEN
        INSERT INTO investory.system_audit_issues(
            audit_run_id, check_code, severity, issue_count, required_action, details
        ) VALUES (
            audit_id,
            'IMPORT_PARTIAL',
            'WARN',
            GREATEST(COALESCE(import_row.rows_failed, 1), 1),
            'Review rejected rows before accepting reporting as complete.',
            jsonb_build_object(
                'provider', import_row.provider,
                'file_name', import_row.file_name,
                'rows_total', import_row.rows_total,
                'rows_applied', import_row.rows_applied,
                'rows_failed', import_row.rows_failed
            )
        ) ON CONFLICT (audit_run_id, check_code) DO NOTHING;
    END IF;

    SELECT
        COALESCE(sum(issue_count) FILTER (WHERE severity = 'ERROR'), 0)::bigint,
        COALESCE(sum(issue_count) FILTER (WHERE severity = 'WARN'), 0)::bigint,
        count(*) FILTER (WHERE severity = 'ERROR'),
        count(*) FILTER (WHERE severity = 'WARN')
    INTO errors, warnings, error_checks, warning_checks
    FROM investory.system_audit_issues
    WHERE audit_run_id = audit_id;

    final_status := CASE
        WHEN errors > 0 THEN 'ERROR'
        WHEN warnings > 0 THEN 'WARN'
        ELSE 'HEALTHY'
    END;
    notification := CASE
        WHEN errors > 0 THEN 'READY_ERROR'
        WHEN warnings > 0 THEN 'READY_WARNING'
        ELSE 'NONE'
    END;

    SELECT jsonb_build_object(
        'audit_run_id', audit_id,
        'import_history_id', p_import_history_id,
        'trigger_source', p_trigger_source,
        'status', final_status,
        'notification_status', notification,
        'error_count', errors,
        'warning_count', warnings,
        'error_checks', error_checks,
        'warning_checks', warning_checks,
        'issues', COALESCE(jsonb_agg(
            jsonb_build_object(
                'check_code', issue.check_code,
                'severity', issue.severity,
                'issue_count', issue.issue_count,
                'required_action', issue.required_action,
                'details', issue.details
            ) ORDER BY issue.severity DESC, issue.check_code
        ) FILTER (WHERE issue.id IS NOT NULL), '[]'::jsonb)
    )
    INTO payload
    FROM investory.system_audit_issues issue
    WHERE issue.audit_run_id = audit_id;

    UPDATE investory.system_audit_runs
    SET finished_at = clock_timestamp(),
        status = final_status,
        error_count = errors,
        warning_count = warnings,
        notification_status = notification,
        summary = payload
    WHERE id = audit_id;

    RAISE LOG 'investory_audit %', payload::text;
    RETURN audit_id;
END;
$$;

COMMENT ON FUNCTION investory.run_system_audit(bigint, varchar) IS
    'Runs and persists the canonical reporting audit. The JSON summary is suitable for structured application logs and notification adapters.';

CREATE OR REPLACE FUNCTION investory.audit_finalized_import()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.status IN ('COMPLETED', 'PARTIAL', 'FAILED', 'NOT_READY')
       AND (
           TG_OP = 'INSERT'
           OR (
               OLD.status IS DISTINCT FROM NEW.status
               AND (OLD.status IS NULL OR OLD.status = 'STARTED')
           )
       ) THEN
        PERFORM investory.run_system_audit(NEW.id, 'IMPORT_FINALIZED');
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_import_history_system_audit
AFTER INSERT OR UPDATE OF status ON investory.import_history
FOR EACH ROW
EXECUTE FUNCTION investory.audit_finalized_import();

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
