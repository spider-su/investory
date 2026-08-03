SET search_path TO investory, public;

CREATE TABLE investory.materialized_view_refresh_history (
    id                bigserial PRIMARY KEY,
    refresh_run_id    uuid NOT NULL,
    materialized_view varchar(128) NOT NULL,
    dependency_level  integer NOT NULL,
    started_at        timestamptz NOT NULL,
    finished_at       timestamptz,
    status            varchar(16) NOT NULL,
    error_message     text,
    CONSTRAINT chk_mv_refresh_status
        CHECK (status IN ('STARTED', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_mv_refresh_dependency_level_non_negative
        CHECK (dependency_level >= 0),
    CONSTRAINT chk_mv_refresh_finished_after_started
        CHECK (finished_at IS NULL OR finished_at >= started_at)
);

CREATE INDEX ix_mv_refresh_history_view_finished
    ON investory.materialized_view_refresh_history(materialized_view, finished_at DESC);

COMMENT ON TABLE investory.materialized_view_refresh_history IS
    'Audit history for ordered reporting materialized-view refreshes. A row is written for every view in every refresh run.';

CREATE OR REPLACE VIEW investory.reporting_materialized_view_dependencies AS
WITH RECURSIVE materialized_views AS (
    SELECT c.oid, c.relname
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'investory'
      AND c.relkind = 'm'
), dependency_edges AS (
    SELECT DISTINCT
        dependent.oid AS dependent_oid,
        dependent.relname AS dependent_view,
        referenced.oid AS referenced_oid,
        referenced.relname AS referenced_view
    FROM pg_depend d
    JOIN pg_rewrite rw ON rw.oid = d.objid
    JOIN materialized_views dependent ON dependent.oid = rw.ev_class
    JOIN materialized_views referenced ON referenced.oid = d.refobjid
    WHERE dependent.oid <> referenced.oid
), dependency_walk AS (
    SELECT mv.oid, mv.relname, 0 AS dependency_level
    FROM materialized_views mv
    WHERE NOT EXISTS (
        SELECT 1
        FROM dependency_edges edge
        WHERE edge.dependent_oid = mv.oid
    )
    UNION ALL
    SELECT edge.dependent_oid, edge.dependent_view, walk.dependency_level + 1
    FROM dependency_walk walk
    JOIN dependency_edges edge ON edge.referenced_oid = walk.oid
), levels AS (
    SELECT mv.oid, mv.relname, COALESCE(max(walk.dependency_level), 0)::integer AS dependency_level
    FROM materialized_views mv
    LEFT JOIN dependency_walk walk ON walk.oid = mv.oid
    GROUP BY mv.oid, mv.relname
), concurrent_eligibility AS (
    SELECT
        mv.oid,
        EXISTS (
            SELECT 1
            FROM pg_index idx
            WHERE idx.indrelid = mv.oid
              AND idx.indisunique
              AND idx.indisvalid
              AND idx.indpred IS NULL
              AND idx.indexprs IS NULL
        ) AS concurrent_refresh_eligible
    FROM materialized_views mv
)
SELECT
    levels.relname::varchar(128) AS materialized_view,
    levels.dependency_level,
    eligibility.concurrent_refresh_eligible,
    array_remove(array_agg(edge.referenced_view ORDER BY edge.referenced_view), NULL)::varchar[] AS depends_on
FROM levels
JOIN concurrent_eligibility eligibility ON eligibility.oid = levels.oid
LEFT JOIN dependency_edges edge ON edge.dependent_oid = levels.oid
GROUP BY levels.relname, levels.dependency_level, eligibility.concurrent_refresh_eligible;

COMMENT ON VIEW investory.reporting_materialized_view_dependencies IS
    'Dependency order and concurrent-refresh eligibility for Investory materialized views. Concurrent eligibility requires a valid, unconditional, non-expression unique index.';

CREATE OR REPLACE VIEW investory.reporting_materialized_view_refresh_status AS
SELECT
    dependencies.materialized_view,
    dependencies.dependency_level,
    dependencies.depends_on,
    dependencies.concurrent_refresh_eligible,
    latest.refresh_run_id,
    latest.started_at AS last_refresh_started_at,
    latest.finished_at AS last_refresh_finished_at,
    latest.status AS last_refresh_status,
    latest.error_message AS last_refresh_error
FROM investory.reporting_materialized_view_dependencies dependencies
LEFT JOIN LATERAL (
    SELECT history.*
    FROM investory.materialized_view_refresh_history history
    WHERE history.materialized_view = dependencies.materialized_view
    ORDER BY history.started_at DESC, history.id DESC
    LIMIT 1
) latest ON true;

COMMENT ON VIEW investory.reporting_materialized_view_refresh_status IS
    'Current refresh status, last successful or failed refresh timestamp, dependency level, and concurrent-refresh eligibility for every reporting materialized view.';

CREATE OR REPLACE PROCEDURE investory.refresh_reporting_materialized_views()
LANGUAGE plpgsql
AS $$
DECLARE
    target record;
    run_id uuid := gen_random_uuid();
    history_id bigint;
BEGIN
    IF EXISTS (
        SELECT 1
        FROM investory.reporting_materialized_view_dependencies
        WHERE materialized_view IS NULL
    ) THEN
        RAISE EXCEPTION 'Unable to resolve materialized-view dependency order';
    END IF;

    FOR target IN
        SELECT materialized_view, dependency_level
        FROM investory.reporting_materialized_view_dependencies
        ORDER BY dependency_level, materialized_view
    LOOP
        INSERT INTO investory.materialized_view_refresh_history (
            refresh_run_id,
            materialized_view,
            dependency_level,
            started_at,
            status
        ) VALUES (
            run_id,
            target.materialized_view,
            target.dependency_level,
            clock_timestamp(),
            'STARTED'
        ) RETURNING id INTO history_id;

        BEGIN
            EXECUTE format(
                'REFRESH MATERIALIZED VIEW investory.%I',
                target.materialized_view
            );

            UPDATE investory.materialized_view_refresh_history
            SET finished_at = clock_timestamp(),
                status = 'COMPLETED'
            WHERE id = history_id;
        EXCEPTION WHEN OTHERS THEN
            UPDATE investory.materialized_view_refresh_history
            SET finished_at = clock_timestamp(),
                status = 'FAILED',
                error_message = SQLERRM
            WHERE id = history_id;
            RAISE;
        END;
    END LOOP;
END;
$$;

COMMENT ON PROCEDURE investory.refresh_reporting_materialized_views() IS
    'Refreshes all Investory materialized views in dependency order and records per-view start, finish, status, and error metadata. Uses ordinary refresh because concurrent refresh cannot be safely executed inside this transactional procedure.';

COMMENT ON COLUMN investory.assets.asset_type IS
    'Current broad instrument classification. Sector allocation must not be introduced until a canonical sector taxonomy and an explicit asset-to-sector mapping are defined.';
