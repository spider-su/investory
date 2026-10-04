-- These price classes are valid for valuation; their provenance remains visible in diagnostics.
CREATE TEMP TABLE _rpd_dependents AS
WITH RECURSIVE deps(oid) AS (
    VALUES ('investory.recon_v_reconstructed_position_daily_mv'::regclass)
    UNION
    SELECT w.ev_class
    FROM deps d
    JOIN pg_depend x ON x.refobjid = d.oid
    JOIN pg_rewrite w ON w.oid = x.objid
)
SELECT c.oid, c.relname AS object_name, c.relkind,
       pg_get_viewdef(c.oid, true) AS object_definition,
       obj_description(c.oid, 'pg_class') AS object_comment,
       false AS dropped
FROM deps d
JOIN pg_class c ON c.oid = d.oid
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'investory'
  AND c.relkind IN ('v', 'm')
  AND c.oid <> 'investory.recon_v_reconstructed_position_daily_mv'::regclass;

CREATE TEMP TABLE _rpd_indexes AS
SELECT DISTINCT i.tablename, i.indexname, i.indexdef
FROM pg_indexes i JOIN _rpd_dependents d ON d.object_name = i.tablename
WHERE i.schemaname = 'investory' AND d.relkind = 'm';

CREATE TEMP TABLE _rpd_source AS
SELECT pg_get_viewdef('investory.recon_v_reconstructed_position_daily_mv'::regclass, true) AS definition,
       obj_description('investory.recon_v_reconstructed_position_daily_mv'::regclass, 'pg_class') AS view_comment;

DO $$
DECLARE v record; progress boolean;
BEGIN
    LOOP
        progress := false;
        FOR v IN SELECT * FROM _rpd_dependents WHERE NOT dropped ORDER BY relkind, object_name LOOP
            BEGIN
                IF v.relkind = 'm' THEN
                    EXECUTE 'DROP MATERIALIZED VIEW investory.' || quote_ident(v.object_name);
                ELSE
                    EXECUTE 'DROP VIEW investory.' || quote_ident(v.object_name);
                END IF;
                UPDATE _rpd_dependents SET dropped = true WHERE oid = v.oid;
                progress := true;
            EXCEPTION WHEN dependent_objects_still_exist THEN NULL;
            END;
        END LOOP;
        EXIT WHEN NOT EXISTS (SELECT 1 FROM _rpd_dependents WHERE NOT dropped);
        IF NOT progress THEN RAISE EXCEPTION 'Could not remove reconstructed-position dependents'; END IF;
    END LOOP;
END
$$;

DROP MATERIALIZED VIEW investory.recon_v_reconstructed_position_daily_mv;

DO $$
DECLARE d text; c text; old_condition text; new_condition text; occurrences integer;
BEGIN
    SELECT definition, view_comment INTO d, c FROM _rpd_source;
    old_condition := 'WHEN p.selection_priority >= 5 AND p.selection_priority <> 6 THEN';
    new_condition := 'WHEN p.selection_priority >= 5 AND p.selection_priority <> 6'
        || ' AND COALESCE(p.price_quality::text, '''') NOT LIKE ''EXACT_LISTING_MARKET_CLOSE%'''
        || ' AND COALESCE(p.price_quality::text, '''') NOT LIKE ''STALE_CARRY_FORWARD%'' THEN';
    occurrences := (length(d) - length(replace(d, old_condition, ''))) / length(old_condition);
    IF occurrences <> 2 THEN
        RAISE EXCEPTION 'Expected two position-price warning conditions, found %', occurrences;
    END IF;
    d := replace(d, old_condition, new_condition);
    EXECUTE 'CREATE MATERIALIZED VIEW investory.recon_v_reconstructed_position_daily_mv AS '
        || regexp_replace(d, ';[[:space:]]*$', '') || ' WITH DATA';
    CREATE UNIQUE INDEX ux_recon_v_reconstructed_position_daily_mv_key
        ON investory.recon_v_reconstructed_position_daily_mv(
            account_id, asset_id, valuation_date, acquisition_currency);
    CREATE INDEX ix_recon_v_reconstructed_position_daily_mv_account_date
        ON investory.recon_v_reconstructed_position_daily_mv(account_id, valuation_date);
    CREATE INDEX ix_recon_v_reconstructed_position_daily_mv_valuation_date
        ON investory.recon_v_reconstructed_position_daily_mv(valuation_date);
    IF c IS NOT NULL THEN
        EXECUTE 'COMMENT ON MATERIALIZED VIEW investory.recon_v_reconstructed_position_daily_mv IS '
            || quote_literal(c);
    END IF;
END
$$;

DO $$
DECLARE v record;
BEGIN
    LOOP
        FOR v IN SELECT * FROM _rpd_dependents WHERE dropped ORDER BY relkind, object_name LOOP
            BEGIN
                IF v.relkind = 'm' THEN
                    EXECUTE 'CREATE MATERIALIZED VIEW investory.' || quote_ident(v.object_name)
                        || ' AS ' || regexp_replace(v.object_definition, ';[[:space:]]*$', '') || ' WITH DATA';
                    UPDATE _rpd_dependents SET dropped = false WHERE oid = v.oid;
                ELSE
                    EXECUTE 'CREATE VIEW investory.' || quote_ident(v.object_name)
                        || ' AS ' || regexp_replace(v.object_definition, ';[[:space:]]*$', '');
                    UPDATE _rpd_dependents SET dropped = false WHERE oid = v.oid;
                END IF;
                IF v.object_comment IS NOT NULL THEN
                    EXECUTE CASE WHEN v.relkind = 'm' THEN 'COMMENT ON MATERIALIZED VIEW investory.'
                        ELSE 'COMMENT ON VIEW investory.' END || quote_ident(v.object_name)
                        || ' IS ' || quote_literal(v.object_comment);
                END IF;
            EXCEPTION WHEN undefined_table OR undefined_object OR dependent_objects_still_exist THEN NULL;
            END;
        END LOOP;
        EXIT WHEN NOT EXISTS (SELECT 1 FROM _rpd_dependents WHERE dropped);
    END LOOP;
END
$$;

DO $$
DECLARE v record;
BEGIN
    FOR v IN SELECT * FROM _rpd_indexes LOOP EXECUTE v.indexdef; END LOOP;
END
$$;
