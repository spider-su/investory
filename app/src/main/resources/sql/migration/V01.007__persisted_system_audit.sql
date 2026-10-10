-- Persisted audit API and immutable import-evidence functions and triggers.
-- Consolidated final schema; historical repair steps are not replayed.

SET search_path TO investory, public;
SET check_function_bodies = false;

CREATE FUNCTION investory.run_system_audit(p_import_history_id bigint DEFAULT NULL::bigint, p_trigger_source character varying DEFAULT 'MANUAL'::character varying) RETURNS uuid
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
        jsonb_build_object('source', 'recon_v_reporting_monthly_import_review')
    FROM investory.recon_v_reporting_monthly_import_review review
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

CREATE FUNCTION investory.shared_fn_reject_import_evidence_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'Import evidence is immutable: %.%', TG_TABLE_SCHEMA, TG_TABLE_NAME;
END;
$$;

CREATE VIEW investory.recon_v_import_provenance_issues AS
 WITH latest_attempt AS (
         SELECT DISTINCT ON (import_history.provider, import_history.file_sha256) import_history.id,
            import_history.provider,
            import_history.file_sha256
           FROM investory.import_history
          ORDER BY import_history.provider, import_history.file_sha256, import_history.attempt_no DESC, import_history.id DESC
        ), latest_source_rows AS (
         SELECT r.id,
            r.import_history_id,
            r.source_file_id,
            r.provider,
            r.section_name,
            r.sheet_name,
            r.archive_member_name,
            r.source_row_number,
            r.source_record_id,
            r.source_row_occurrence,
            r.logical_row_sha256,
            r.raw_text,
            r.raw_values,
            r.created_at
           FROM (investory.import_source_rows r
             JOIN latest_attempt a ON ((a.id = r.import_history_id)))
        ), canonical_source_row_ids AS MATERIALIZED (
         SELECT c.import_source_row_id
           FROM investory.cash_operations c
          WHERE (c.import_source_row_id IS NOT NULL)
        UNION
         SELECT p.import_source_row_id
           FROM investory.positions p
          WHERE (p.import_source_row_id IS NOT NULL)
        ), canonical_source_rows AS MATERIALIZED (
         SELECT source.id,
            source.provider,
            source.logical_row_sha256,
            source.section_name,
            source.sheet_name,
            source.archive_member_name,
            source.source_record_id,
            source.source_row_occurrence
           FROM (canonical_source_row_ids linked
             JOIN investory.import_source_rows source ON ((source.id = linked.import_source_row_id)))
        ), canonical_logical_rows AS MATERIALIZED (
         SELECT DISTINCT canonical_source_rows.provider,
            canonical_source_rows.logical_row_sha256
           FROM canonical_source_rows
          WHERE (canonical_source_rows.logical_row_sha256 IS NOT NULL)
        ), canonical_xtb_cash_rows AS MATERIALIZED (
         SELECT DISTINCT source.source_record_id,
            source.source_row_occurrence,
            account.external_account_id AS account_scope
           FROM ((canonical_source_rows source
             JOIN investory.cash_operations operation ON (((operation.import_source_row_id = source.id) AND ((operation.id)::text = (source.source_record_id)::text))))
             JOIN investory.accounts account ON ((account.id = operation.account_id)))
          WHERE (((source.provider)::text = 'XTB'::text) AND ((source.section_name)::text = 'Cash Operations'::text) AND (source.source_record_id IS NOT NULL) AND (source.archive_member_name IS NOT NULL) AND (split_part((source.archive_member_name)::text, '/'::text, 1) = (account.external_account_id)::text))
        )
 SELECT 'CASH_OPERATION_MISSING_IMPORT'::text AS issue_code,
    (c.id)::text AS financial_row_id,
    c.import_history_id,
    c.import_source_row_id,
    'cash_operations'::text AS financial_table
   FROM investory.cash_operations c
  WHERE ((c.import_history_id IS NULL) AND (c.import_source_row_id IS NOT NULL))
UNION ALL
 SELECT 'CASH_OPERATION_MISSING_SOURCE_ROW'::text AS issue_code,
    (c.id)::text AS financial_row_id,
    c.import_history_id,
    c.import_source_row_id,
    'cash_operations'::text AS financial_table
   FROM investory.cash_operations c
  WHERE ((c.import_history_id IS NOT NULL) AND (c.import_source_row_id IS NULL))
UNION ALL
 SELECT 'POSITION_MISSING_IMPORT'::text AS issue_code,
    (p.id)::text AS financial_row_id,
    p.import_history_id,
    p.import_source_row_id,
    'positions'::text AS financial_table
   FROM investory.positions p
  WHERE ((p.import_history_id IS NULL) AND (p.import_source_row_id IS NOT NULL))
UNION ALL
 SELECT 'POSITION_MISSING_SOURCE_ROW'::text AS issue_code,
    (p.id)::text AS financial_row_id,
    p.import_history_id,
    p.import_source_row_id,
    'positions'::text AS financial_table
   FROM investory.positions p
  WHERE ((p.import_history_id IS NOT NULL) AND (p.import_source_row_id IS NULL))
UNION ALL
 SELECT 'SOURCE_ROW_WRONG_IMPORT'::text AS issue_code,
    (r.id)::text AS financial_row_id,
    r.import_history_id,
    r.id AS import_source_row_id,
    'import_source_rows'::text AS financial_table
   FROM ((latest_source_rows r
     JOIN investory.import_source_files f ON ((f.id = r.source_file_id)))
     JOIN investory.import_history h ON ((h.id = r.import_history_id)))
  WHERE (((f.provider)::text <> (h.provider)::text) OR ((f.file_sha256)::text <> (h.file_sha256)::text))
UNION ALL
 SELECT 'ORPHAN_SOURCE_ROW'::text AS issue_code,
    (r.id)::text AS financial_row_id,
    r.import_history_id,
    r.id AS import_source_row_id,
    'import_source_rows'::text AS financial_table
   FROM ((((latest_source_rows r
     LEFT JOIN investory.cash_operations c ON ((c.import_source_row_id = r.id)))
     LEFT JOIN investory.positions p ON ((p.import_source_row_id = r.id)))
     LEFT JOIN canonical_logical_rows logical_row ON ((((logical_row.provider)::text = (r.provider)::text) AND ((logical_row.logical_row_sha256)::text = (r.logical_row_sha256)::text))))
     LEFT JOIN canonical_xtb_cash_rows xtb_cash_row ON ((((r.provider)::text = 'XTB'::text) AND ((r.section_name)::text = 'Cash Operations'::text) AND (r.source_record_id IS NOT NULL) AND (r.archive_member_name IS NOT NULL) AND ((xtb_cash_row.account_scope)::text = split_part((r.archive_member_name)::text, '/'::text, 1)) AND ((xtb_cash_row.source_record_id)::text = (r.source_record_id)::text) AND (xtb_cash_row.source_row_occurrence = r.source_row_occurrence))))
  WHERE ((c.id IS NULL) AND (p.id IS NULL) AND ((logical_row.provider IS NULL) AND (xtb_cash_row.source_record_id IS NULL)))
UNION ALL
 SELECT 'CANONICAL_ROW_WRONG_IMPORT'::text AS issue_code,
    (c.id)::text AS financial_row_id,
    c.import_history_id,
    c.import_source_row_id,
    'cash_operations'::text AS financial_table
   FROM (investory.cash_operations c
     JOIN investory.import_source_rows r ON ((r.id = c.import_source_row_id)))
  WHERE (c.import_history_id <> r.import_history_id)
UNION ALL
 SELECT 'CANONICAL_POSITION_WRONG_IMPORT'::text AS issue_code,
    (p.id)::text AS financial_row_id,
    p.import_history_id,
    p.import_source_row_id,
    'positions'::text AS financial_table
   FROM (investory.positions p
     JOIN investory.import_source_rows r ON ((r.id = p.import_source_row_id)))
  WHERE (p.import_history_id <> r.import_history_id)
UNION ALL
 SELECT 'SOURCE_FILE_CHECKSUM_MISMATCH'::text AS issue_code,
    (f.id)::text AS financial_row_id,
    f.import_history_id,
    NULL::bigint AS import_source_row_id,
    'import_source_files'::text AS financial_table
   FROM (investory.import_source_files f
     JOIN investory.import_history h ON ((h.id = f.import_history_id)))
  WHERE ((f.file_sha256)::text <> (h.file_sha256)::text)
UNION ALL
 SELECT 'DUPLICATE_SOURCE_IDENTITY'::text AS issue_code,
    string_agg((r.id)::text, ','::text ORDER BY r.id) AS financial_row_id,
    min(r.import_history_id) AS import_history_id,
    NULL::bigint AS import_source_row_id,
    'import_source_rows'::text AS financial_table
   FROM latest_source_rows r
  WHERE (r.source_record_id IS NOT NULL)
  GROUP BY r.provider, r.archive_member_name, r.section_name, r.sheet_name, r.source_record_id, r.source_row_occurrence, r.raw_values
 HAVING (count(*) > 1);

CREATE VIEW investory.recon_v_system_audit AS
 SELECT run.id AS audit_run_id,
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
    ((run.notification_status)::text <> 'NONE'::text) AS notification_ready,
    run.summary AS structured_log_payload
   FROM (investory.system_audit_runs run
     LEFT JOIN investory.import_history import_row ON ((import_row.id = run.import_history_id)));

CREATE TRIGGER shared_trg_import_source_files_immutable BEFORE DELETE OR UPDATE ON investory.import_source_files FOR EACH ROW EXECUTE FUNCTION investory.shared_fn_reject_import_evidence_mutation();

CREATE TRIGGER shared_trg_import_source_rows_immutable BEFORE DELETE OR UPDATE ON investory.import_source_rows FOR EACH ROW EXECUTE FUNCTION investory.shared_fn_reject_import_evidence_mutation();
