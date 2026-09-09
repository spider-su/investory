-- Rebind normalized cash operations to the canonical portfolio FX resolver.
-- The materialized view stores its resolver function OID in the expression. Recreating the
-- canonical resolver therefore requires rebuilding this dependent closure.
-- Transaction FX keeps broker execution observations first, but valuation/read models need a
-- deterministic daily fallback when an imported FX leg has no execution observation.
CREATE OR REPLACE FUNCTION investory.resolve_transaction_fx_rate(
    p_transaction_time timestamptz,
    p_source_currency varchar(3),
    p_target_currency varchar(3),
    p_purpose varchar(16)
) RETURNS TABLE (
    source_currency varchar(3), target_currency varchar(3), fx_rate_to_target numeric,
    source varchar(64), rate_method varchar(32), rate_source varchar(32),
    source_rate_date date, age_days integer, conversion_status varchar(32)
) LANGUAGE sql STABLE AS $$
WITH selected AS (
    SELECT er.base::varchar(3) AS source_currency, er.to_currency::varchar(3) AS target_currency,
           er.rate AS fx_rate_to_target, er.method::varchar(32) AS rate_method,
           er.source::varchar(32) AS rate_source, er.rate_date AS source_rate_date,
           0::integer AS age_days, 0 AS direction_priority, er.observed_at, er.source_reference
    FROM investory.exchange_rates er
    WHERE upper(p_purpose) = 'TRANSACTION'
      AND er.method IN ('XTB_EXECUTION', 'IBKR_EXECUTION')
      AND er.rate_date = (p_transaction_time AT TIME ZONE 'Europe/Warsaw')::date
      AND er.observed_at <= p_transaction_time
      AND er.base = p_source_currency AND er.to_currency = p_target_currency
    UNION ALL
    SELECT er.to_currency::varchar(3), er.base::varchar(3), 1 / er.rate,
           er.method::varchar(32), er.source::varchar(32), er.rate_date,
           0::integer, 1, er.observed_at, er.source_reference
    FROM investory.exchange_rates er
    WHERE upper(p_purpose) = 'TRANSACTION'
      AND er.method IN ('XTB_EXECUTION', 'IBKR_EXECUTION')
      AND er.rate_date = (p_transaction_time AT TIME ZONE 'Europe/Warsaw')::date
      AND er.observed_at <= p_transaction_time
      AND er.base = p_target_currency AND er.to_currency = p_source_currency
    ORDER BY direction_priority, observed_at DESC NULLS LAST, source_reference ASC NULLS LAST
    LIMIT 1
), daily AS (
    SELECT d.*
    FROM investory.resolve_fx_rate(
        (p_transaction_time AT TIME ZONE 'Europe/Warsaw')::date,
        p_source_currency, p_target_currency) d
)
SELECT p_source_currency, p_target_currency, 1, 'SAME_CURRENCY', 'SAME_CURRENCY',
       'SAME_CURRENCY', (p_transaction_time AT TIME ZONE 'Europe/Warsaw')::date, 0, 'SAME_CURRENCY'
WHERE upper(p_purpose) = 'TRANSACTION' AND p_source_currency = p_target_currency
UNION ALL
SELECT s.source_currency, s.target_currency, s.fx_rate_to_target,
       ('EXECUTION:' || s.rate_source)::varchar(64), s.rate_method, s.rate_source,
       s.source_rate_date, s.age_days, 'OK'
FROM selected s
WHERE upper(p_purpose) = 'TRANSACTION'
UNION ALL
SELECT d.source_currency, d.target_currency, d.fx_rate_to_target, d.source, d.rate_method,
       d.rate_source, d.source_rate_date, d.age_days, d.conversion_status
FROM daily d
WHERE upper(p_purpose) = 'TRANSACTION'
  AND p_source_currency <> p_target_currency
  AND NOT EXISTS (SELECT 1 FROM selected)
$$;

CREATE TEMP TABLE _nco_portfolio_fx_defs AS
WITH RECURSIVE deps(oid) AS (
    VALUES ('investory.app_v_normalized_cash_operations'::regclass)
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
  AND c.oid <> 'investory.app_v_normalized_cash_operations'::regclass;

CREATE TEMP TABLE _nco_portfolio_fx_indexes AS
SELECT DISTINCT i.tablename, i.indexname, i.indexdef
FROM pg_indexes i JOIN _nco_portfolio_fx_defs d ON d.object_name = i.tablename
WHERE i.schemaname = 'investory' AND d.relkind = 'm';

CREATE TEMP TABLE _nco_portfolio_fx_source AS
SELECT pg_get_viewdef('investory.app_v_normalized_cash_operations'::regclass, true) AS definition,
       obj_description('investory.app_v_normalized_cash_operations'::regclass, 'pg_class') AS view_comment;

DO $$
DECLARE v record; progress boolean;
BEGIN
    LOOP
        progress := false;
        FOR v IN SELECT * FROM _nco_portfolio_fx_defs WHERE NOT dropped ORDER BY relkind, object_name LOOP
            BEGIN
                EXECUTE CASE WHEN v.relkind = 'm' THEN 'DROP MATERIALIZED VIEW investory.' ELSE 'DROP VIEW investory.' END || quote_ident(v.object_name);
                UPDATE _nco_portfolio_fx_defs SET dropped = true WHERE oid = v.oid;
                progress := true;
            EXCEPTION WHEN dependent_objects_still_exist THEN NULL;
            END;
        END LOOP;
        EXIT WHEN NOT EXISTS (SELECT 1 FROM _nco_portfolio_fx_defs WHERE NOT dropped);
        IF NOT progress THEN RAISE EXCEPTION 'Could not remove normalized-cash dependent objects'; END IF;
    END LOOP;
    END
$$;

DROP MATERIALIZED VIEW investory.app_v_normalized_cash_operations;

DO $$
DECLARE d text; r text; p integer; q integer; c text;
BEGIN
    SELECT definition, view_comment INTO d, c FROM _nco_portfolio_fx_source;
    d := regexp_replace(d, ';[[:space:]]*$', '');
    p := strpos(d, 'port_resolved AS (');
    q := p + strpos(substr(d, p), '), acct_needed AS (') - 1;
    r := 'port_resolved AS ( SELECT n.portfolio_id AS k_portfolio_id, n.vdate AS k_vdate, n.currency AS k_currency, fx.fx_rate_to_base, fx.source, fx.source_rate_date, fx.age_days, fx.conversion_status FROM port_needed n CROSS JOIN LATERAL investory.resolve_portfolio_fx_rate(n.portfolio_id, n.vdate, n.currency) fx )';
    d := left(d, p - 1) || r || substr(d, q + 1);
    EXECUTE 'CREATE MATERIALIZED VIEW investory.app_v_normalized_cash_operations AS ' || d || ' WITH DATA';
    CREATE UNIQUE INDEX ux_normalized_cash_operations ON investory.app_v_normalized_cash_operations(operation_id);
    IF c IS NOT NULL THEN EXECUTE 'COMMENT ON MATERIALIZED VIEW investory.app_v_normalized_cash_operations IS ' || quote_literal(c); END IF;
END
$$;

DO $$
DECLARE v record;
BEGIN
    LOOP
        FOR v IN SELECT * FROM _nco_portfolio_fx_defs WHERE dropped ORDER BY relkind, object_name LOOP
            BEGIN
                EXECUTE CASE WHEN v.relkind = 'm' THEN 'CREATE MATERIALIZED VIEW investory.' ELSE 'CREATE VIEW investory.' END
                    || quote_ident(v.object_name) || ' AS ' || regexp_replace(v.object_definition, ';[[:space:]]*$', '')
                    || CASE WHEN v.relkind = 'm' THEN ' WITH DATA' ELSE '' END;
                UPDATE _nco_portfolio_fx_defs SET dropped = false WHERE oid = v.oid;
                IF v.object_comment IS NOT NULL THEN
                    EXECUTE CASE WHEN v.relkind = 'm' THEN 'COMMENT ON MATERIALIZED VIEW investory.' ELSE 'COMMENT ON VIEW investory.' END
                        || quote_ident(v.object_name) || ' IS ' || quote_literal(v.object_comment);
                END IF;
            EXCEPTION WHEN undefined_table OR undefined_object OR dependent_objects_still_exist THEN NULL;
            END;
        END LOOP;
        EXIT WHEN NOT EXISTS (SELECT 1 FROM _nco_portfolio_fx_defs WHERE dropped);
    END LOOP;
END
$$;

DO $$
DECLARE v record;
BEGIN
    FOR v IN SELECT * FROM _nco_portfolio_fx_indexes LOOP EXECUTE v.indexdef; END LOOP;
END
$$;
