SET search_path TO investory, public;

-- V01.005 declared this index, but existing databases can retain the materialized
-- view without it after an interrupted/manual rebuild. Concurrent refresh requires
-- an unconditional unique index on the materialized view.
CREATE UNIQUE INDEX IF NOT EXISTS ux_mv_app_v_portfolio_contribution_summary_mv_portfolio
    ON investory.app_v_portfolio_contribution_summary_mv(portfolio_id);

-- V01.001 was already applied on databases affected by the refresh-order defect.
-- Keep this repair forward-only: the public entry point refreshes every price
-- dependency before reconstructed valuation consumes it.
CREATE OR REPLACE FUNCTION investory.refresh_reconstructed_position_daily()
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
    started_at timestamptz := clock_timestamp();
BEGIN
    PERFORM pg_advisory_xact_lock(2147483647, 1001);
    PERFORM investory.analyze_refresh_sources();
    PERFORM investory.refresh_materialized_view('app_v_canonical_asset_daily_price_mv', false);
    PERFORM investory.refresh_materialized_view('app_v_canonical_asset_daily_price_ranked_mv', true);
    PERFORM investory.refresh_materialized_view('app_v_normalized_daily_price_mv', true);
    PERFORM investory.refresh_materialized_view('recon_v_reconstructed_position_daily_mv', false);
    RAISE LOG 'investory refresh stage=valuation_price_and_reconstruction elapsed_ms=%',
        EXTRACT(milliseconds FROM clock_timestamp() - started_at);
END;
$$;

COMMENT ON FUNCTION investory.refresh_reconstructed_position_daily() IS
    'Refreshes canonical price, normalized price, and reconstructed position valuation in dependency order. Selected price and price_currency remain one observation pair.';
