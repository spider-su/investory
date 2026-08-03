SET search_path TO investory, public;

-- Consolidated actionable queue for valuation inputs. Existing valuation views
-- remain fail-closed: missing or stale inputs produce NULL totals and explicit
-- status fields rather than silently dropping rows or substituting zero.
CREATE OR REPLACE VIEW investory.reporting_valuation_input_issues AS
WITH latest_position_date AS (
    SELECT max(valuation_date) AS valuation_date
    FROM investory.v_reconstructed_position_daily
), position_issues AS (
    SELECT
        CASE
            WHEN rpd.selected_price IS NULL THEN 'MISSING_PRICE'
            WHEN rpd.price_age_days > 10 THEN 'STALE_PRICE'
            WHEN rpd.fx_conversion_status IN ('MISSING', 'MISSING_CURRENCY') THEN 'MISSING_FX'
            WHEN rpd.fx_conversion_status = 'STALE' THEN 'STALE_FX'
        END::varchar(64) AS issue_code,
        CASE
            WHEN rpd.selected_price IS NULL
              OR rpd.fx_conversion_status IN ('MISSING', 'MISSING_CURRENCY') THEN 'ERROR'
            ELSE 'WARN'
        END::varchar(16) AS severity,
        rpd.portfolio_id,
        rpd.account_id,
        rpd.asset_id,
        rpd.valuation_date,
        rpd.price_currency::varchar(3) AS source_currency,
        rpd.base_currency::varchar(3) AS target_currency,
        rpd.selected_price_date AS input_date,
        CASE
            WHEN rpd.selected_price IS NULL THEN NULL
            ELSE rpd.price_age_days
        END::integer AS age_days,
        concat_ws(
            '; ',
            'symbol=' || rpd.asset_symbol,
            'price_quality=' || coalesce(rpd.price_quality, 'NULL'),
            'fx_status=' || coalesce(rpd.fx_conversion_status, 'NULL')
        )::text AS details,
        CASE
            WHEN rpd.selected_price IS NULL
                THEN 'Import a canonical price on or before the valuation date, then rebuild reporting.'
            WHEN rpd.price_age_days > 10
                THEN 'Review whether the last available price is valid for this instrument and valuation date.'
            WHEN rpd.fx_conversion_status IN ('MISSING', 'MISSING_CURRENCY')
                THEN 'Import the required FX rate, then rebuild reporting.'
            ELSE 'Review the stale FX rate before accepting reporting.'
        END::varchar(255) AS required_action
    FROM investory.v_reconstructed_position_daily rpd
    CROSS JOIN latest_position_date latest
    WHERE rpd.valuation_date = latest.valuation_date
      AND rpd.open_quantity <> 0
      AND (
          rpd.selected_price IS NULL
          OR rpd.price_age_days > 10
          OR rpd.fx_conversion_status NOT IN ('OK', 'SAME_CURRENCY')
      )
), cash_fx_issues AS (
    SELECT
        CASE
            WHEN nco.portfolio_conversion_status IN ('MISSING', 'MISSING_CURRENCY') THEN 'MISSING_FX'
            ELSE 'STALE_FX'
        END::varchar(64) AS issue_code,
        CASE
            WHEN nco.portfolio_conversion_status IN ('MISSING', 'MISSING_CURRENCY') THEN 'ERROR'
            ELSE 'WARN'
        END::varchar(16) AS severity,
        nco.portfolio_id,
        nco.account_id,
        nco.asset_id,
        nco.date::date AS valuation_date,
        nco.currency::varchar(3) AS source_currency,
        nco.base_currency::varchar(3) AS target_currency,
        nco.portfolio_source_rate_date AS input_date,
        nco.portfolio_fx_age_days::integer AS age_days,
        concat_ws(
            '; ',
            'operation_id=' || nco.operation_id,
            'operation=' || nco.raw_operation,
            'category=' || nco.normalized_category,
            'fx_status=' || nco.portfolio_conversion_status
        )::text AS details,
        CASE
            WHEN nco.portfolio_conversion_status IN ('MISSING', 'MISSING_CURRENCY')
                THEN 'Import the required FX rate, then rebuild reporting.'
            ELSE 'Review the stale FX rate before accepting reporting.'
        END::varchar(255) AS required_action
    FROM investory.normalized_cash_operations nco
    WHERE nco.portfolio_conversion_status NOT IN ('OK', 'SAME_CURRENCY')
)
SELECT * FROM position_issues WHERE issue_code IS NOT NULL
UNION ALL
SELECT * FROM cash_fx_issues;

COMMENT ON VIEW investory.reporting_valuation_input_issues IS
    'Actionable queue for missing or stale prices and FX. ERROR rows are truly missing required inputs; WARN rows use an as-of input that exceeded the accepted age threshold.';

CREATE OR REPLACE VIEW investory.reporting_monthly_import_review AS
SELECT
    'UNSUPPORTED_TRANSACTION_STATE'::varchar(64) AS check_code,
    count(*)::bigint AS issue_count,
    'Review UNKNOWN or UNCLASSIFIED imported ledger states.'::varchar(255) AS required_action
FROM investory.reporting_unsupported_transaction_states
UNION ALL
SELECT
    'DUPLICATE_POSITION_LOT'::varchar(64),
    count(*)::bigint,
    'Review duplicate source lots before trusting position and P/L totals.'::varchar(255)
FROM investory.reporting_position_lot_duplicates
UNION ALL
SELECT
    'TIMEZONE_NAIVE_COLUMN'::varchar(64),
    count(*)::bigint,
    'Convert event or audit timestamps to timestamptz.'::varchar(255)
FROM investory.reporting_timezone_naive_columns
UNION ALL
SELECT
    'VALUATION_INPUT_ERROR'::varchar(64),
    count(*)::bigint,
    'Import missing prices or FX rates and rebuild reporting.'::varchar(255)
FROM investory.reporting_valuation_input_issues
WHERE severity = 'ERROR'
UNION ALL
SELECT
    'VALUATION_INPUT_WARNING'::varchar(64),
    count(*)::bigint,
    'Review stale price or FX inputs before accepting month-end reporting.'::varchar(255)
FROM investory.reporting_valuation_input_issues
WHERE severity = 'WARN';

COMMENT ON VIEW investory.reporting_monthly_import_review IS
    'Monthly import-review checklist. Error counts must be zero before reporting is accepted; warnings require review. Corporate actions remain a manual broker-statement review.';

DROP PROCEDURE investory.refresh_reporting_materialized_views();

CREATE OR REPLACE PROCEDURE investory.refresh_reporting_materialized_views(
    p_fail_on_missing_inputs boolean DEFAULT false
)
LANGUAGE plpgsql
AS $$
DECLARE
    target record;
    run_id uuid := gen_random_uuid();
    history_id bigint;
    missing_input_count bigint;
BEGIN
    IF p_fail_on_missing_inputs THEN
        SELECT count(*)
        INTO missing_input_count
        FROM investory.reporting_valuation_input_issues
        WHERE severity = 'ERROR';

        IF missing_input_count > 0 THEN
            RAISE EXCEPTION
                'Reporting refresh blocked: % missing required valuation inputs. Review investory.reporting_valuation_input_issues.',
                missing_input_count;
        END IF;
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
            EXECUTE format('REFRESH MATERIALIZED VIEW investory.%I', target.materialized_view);

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
            RAISE WARNING 'Materialized-view refresh stopped at %: %', target.materialized_view, SQLERRM;
            RETURN;
        END;
    END LOOP;
END;
$$;

COMMENT ON PROCEDURE investory.refresh_reporting_materialized_views(boolean) IS
    'Refreshes reporting MVs in dependency order. When fail_on_missing_inputs is true, truly missing prices or FX block refresh; stale as-of inputs remain warnings.';
