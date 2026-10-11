-- Operational retirement only. Fresh installations create no archive objects.
-- Existing facts and historical repair snapshots are retained, never deleted.
SET search_path TO investory, public;

DO $$
DECLARE
    r record;
    taxpayer_columns text[] := ARRAY[
        'tax_micro_account', 'taxpayer_date_of_birth', 'taxpayer_email',
        'taxpayer_first_name', 'taxpayer_full_name', 'taxpayer_nip',
        'taxpayer_surname', 'taxpayer_tax_office_code', 'zus_payment_account'
    ];
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'investory'
          AND (c.relname ~ '^(accounting_|ryczalt_)'
               OR c.relname IN ('rental_tax_policies', 'benchmark_monthly_closes_2',
                   'fx_configuration_2', 'integration_instances_2', 'real_estate_valuation',
                   'recon_v_price_currency_integrity_issues')
               OR c.relname LIKE 'flyway_schema_history_backup_%')
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_attribute
        WHERE attrelid = 'investory.portfolios'::regclass
          AND attname = ANY(taxpayer_columns) AND NOT attisdropped
    ) THEN
        RETURN;
    END IF;

    CREATE SCHEMA IF NOT EXISTS investory_legacy_archive;
    COMMENT ON SCHEMA investory_legacy_archive IS
        'Retained retired accounting, Ryczałt and repair facts. Excluded from application reads and writes.';

    CREATE TABLE IF NOT EXISTS investory_legacy_archive.portfolio_taxpayer_history (
        portfolio_id bigint PRIMARY KEY,
        taxpayer_fields jsonb NOT NULL,
        archived_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
    INSERT INTO investory_legacy_archive.portfolio_taxpayer_history(portfolio_id, taxpayer_fields)
    SELECT p.id, jsonb_object_agg(e.key, e.value)
    FROM investory.portfolios p
    CROSS JOIN LATERAL jsonb_each(to_jsonb(p)) e
    WHERE e.key = ANY(taxpayer_columns)
    GROUP BY p.id
    ON CONFLICT (portfolio_id) DO NOTHING;

    FOR r IN
        SELECT c.relname, c.relkind
        FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'investory' AND c.relkind IN ('r', 'p', 'v', 'm')
          AND (c.relname ~ '^(accounting_|ryczalt_)'
               OR c.relname IN ('rental_tax_policies', 'benchmark_monthly_closes_2',
                   'fx_configuration_2', 'integration_instances_2', 'real_estate_valuation',
                   'recon_v_price_currency_integrity_issues')
               OR c.relname LIKE 'flyway_schema_history_backup_%')
        ORDER BY c.relname
    LOOP
        EXECUTE format('ALTER %s investory.%I SET SCHEMA investory_legacy_archive',
            CASE r.relkind WHEN 'v' THEN 'VIEW' WHEN 'm' THEN 'MATERIALIZED VIEW' ELSE 'TABLE' END,
            r.relname);
    END LOOP;

    FOR r IN
        SELECT p.proname, pg_get_function_identity_arguments(p.oid) AS arguments
        FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'investory' AND p.proname IN (
            'populate_ryczalt_obligation_due_date', 'prevent_accounting_source_delete',
            'prevent_accounting_source_mutation', 'ryczalt_is_polish_holiday',
            'ryczalt_obligation_due_date', 'touch_ryczalt_invoice_candidate_updated_at',
            'validate_accounting_document_correction')
    LOOP
        EXECUTE format('ALTER FUNCTION investory.%I(%s) SET SCHEMA investory_legacy_archive',
            r.proname, r.arguments);
    END LOOP;

    FOR r IN
        SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'investory' AND c.relkind = 'S'
          AND c.relname ~ '^(accounting_|ryczalt_)'
    LOOP
        EXECUTE format('ALTER SEQUENCE investory.%I SET SCHEMA investory_legacy_archive', r.relname);
    END LOOP;

    FOR r IN
        SELECT attname FROM pg_attribute
        WHERE attrelid = 'investory.portfolios'::regclass
          AND attname = ANY(taxpayer_columns) AND NOT attisdropped
    LOOP
        EXECUTE format('ALTER TABLE investory.portfolios DROP COLUMN %I', r.attname);
    END LOOP;
END $$;

DO $$
DECLARE
    ns record;
    r record;
    archived_name text;
BEGIN
    FOR ns IN SELECT nspname FROM pg_namespace
        WHERE nspname ~ '^investory_(repair_|sync_|integration_sync_|v005_repair_)'
           OR nspname IN ('investory_old', 'issue112_forecast_repair')
        ORDER BY nspname
    LOOP
        IF EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
                   WHERE n.nspname = ns.nspname)
           OR EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                      WHERE n.nspname = ns.nspname AND c.relkind NOT IN ('r', 'i', 'S', 'v', 'm')) THEN
            RAISE EXCEPTION 'Unexpected objects in repair schema %, requires explicit review', ns.nspname;
        END IF;
        CREATE SCHEMA IF NOT EXISTS investory_legacy_archive;
        CREATE TABLE IF NOT EXISTS investory_legacy_archive.repair_object_manifest (
            source_schema text NOT NULL,
            source_name text NOT NULL,
            archive_name text NOT NULL UNIQUE,
            object_kind text NOT NULL,
            archived_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (source_schema, source_name)
        );
        INSERT INTO investory_legacy_archive.repair_object_manifest
            (source_schema, source_name, archive_name, object_kind)
        SELECT ns.nspname, c.relname,
               'backup_' || substr(md5(ns.nspname || '.' || c.relname), 1, 12)
                   || '_' || left(c.relname, 40), c.relkind::text
        FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = ns.nspname AND c.relkind IN ('r', 'i', 'S', 'v', 'm');

        -- Rename owned indexes/sequences before their tables move automatically.
        FOR r IN SELECT c.relname, c.relkind, m.archive_name
            FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
            JOIN investory_legacy_archive.repair_object_manifest m
                ON m.source_schema = n.nspname AND m.source_name = c.relname
            WHERE n.nspname = ns.nspname AND c.relkind IN ('i', 'S')
        LOOP
            EXECUTE format('ALTER %s %I.%I RENAME TO %I',
                CASE r.relkind WHEN 'i' THEN 'INDEX' ELSE 'SEQUENCE' END,
                ns.nspname, r.relname, r.archive_name);
        END LOOP;
        FOR r IN SELECT c.relname, c.relkind, m.archive_name
            FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
            JOIN investory_legacy_archive.repair_object_manifest m
                ON m.source_schema = n.nspname AND m.source_name = c.relname
            WHERE n.nspname = ns.nspname AND c.relkind IN ('r', 'v', 'm')
            ORDER BY CASE c.relkind WHEN 'r' THEN 0 ELSE 1 END, c.relname
        LOOP
            EXECUTE format('ALTER %s %I.%I RENAME TO %I',
                CASE r.relkind WHEN 'v' THEN 'VIEW' WHEN 'm' THEN 'MATERIALIZED VIEW' ELSE 'TABLE' END,
                ns.nspname, r.relname, r.archive_name);
            EXECUTE format('ALTER %s %I.%I SET SCHEMA investory_legacy_archive',
                CASE r.relkind WHEN 'v' THEN 'VIEW' WHEN 'm' THEN 'MATERIALIZED VIEW' ELSE 'TABLE' END,
                ns.nspname, r.archive_name);
        END LOOP;
        FOR r IN SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                 WHERE n.nspname = ns.nspname AND c.relkind = 'S'
        LOOP
            EXECUTE format('ALTER SEQUENCE %I.%I SET SCHEMA investory_legacy_archive', ns.nspname, r.relname);
        END LOOP;
        -- RESTRICT prevents unreviewed external dependencies from being removed.
        EXECUTE format('DROP SCHEMA %I RESTRICT', ns.nspname);
    END LOOP;

    -- Archives must survive deletion of an active profile or account.
    FOR r IN SELECT c.relname, k.conname
        FROM pg_constraint k
        JOIN pg_class c ON c.oid = k.conrelid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        JOIN pg_class target ON target.oid = k.confrelid
        JOIN pg_namespace target_ns ON target_ns.oid = target.relnamespace
        WHERE n.nspname = 'investory_legacy_archive' AND k.contype = 'f'
          AND target_ns.nspname <> 'investory_legacy_archive'
    LOOP
        EXECUTE format('ALTER TABLE investory_legacy_archive.%I DROP CONSTRAINT %I', r.relname, r.conname);
    END LOOP;
END $$;
