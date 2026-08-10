CREATE OR REPLACE FUNCTION investory.fx_status_usable(p_status varchar)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT p_status IN ('OK', 'ESTIMATED', 'SAME_CURRENCY')
$$;

COMMENT ON FUNCTION investory.fx_status_usable(varchar) IS
    'Central FX usability contract. ESTIMATED is usable and retains its provenance; STALE, MISSING_RATE, and MISSING_CURRENCY are not usable.';

DO $$
DECLARE
    view_row record;
    view_sql text;
BEGIN
    FOR view_row IN
        SELECT schemaname, viewname
        FROM pg_views
        WHERE schemaname = 'investory'
    LOOP
        view_sql := pg_get_viewdef(format('%I.%I', view_row.schemaname, view_row.viewname)::regclass, true);
        view_sql := replace(view_sql, 'IN (''OK'', ''SAME_CURRENCY'')', 'IN (''OK'', ''ESTIMATED'', ''SAME_CURRENCY'')');
        view_sql := replace(view_sql, 'NOT IN (''OK'', ''SAME_CURRENCY'')', 'NOT IN (''OK'', ''ESTIMATED'', ''SAME_CURRENCY'')');
        view_sql := replace(view_sql, 'IN (''OK'', ''SAME_CURRENCY'')', 'IN (''OK'', ''ESTIMATED'', ''SAME_CURRENCY'')');
        EXECUTE format('CREATE OR REPLACE VIEW %I.%I AS %s', view_row.schemaname, view_row.viewname, view_sql);
    END LOOP;
END $$;

COMMENT ON COLUMN investory.fx_configuration.config_value IS
    'Runtime FX policy value. daily_history_start is the immutable migration boundary used by SQL and Java resolver calls.';
