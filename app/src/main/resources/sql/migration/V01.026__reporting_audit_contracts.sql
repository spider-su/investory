SET search_path TO investory, public;

-- Audits run after the import transaction commits through application orchestration.
-- The baseline trigger ran inside the import transaction and produced duplicate runs
-- when the post-commit orchestration executed the same audit.
DROP TRIGGER IF EXISTS trg_import_history_system_audit ON investory.import_history;

COMMENT ON TABLE investory.system_audit_runs IS
    'Persisted system-level validation result created by post-import application orchestration or explicit manual execution.';

COMMENT ON FUNCTION investory.audit_finalized_import() IS
    'Legacy trigger function retained for compatibility. No trigger invokes it; audits run after import commit through application orchestration.';

-- Keep the diagnostic cash-flow-scope materialized view on both reconciliation
-- refresh paths. It was added after the original refresh functions were defined.
CREATE OR REPLACE FUNCTION investory.refresh_reconciliation_reporting_views()
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    started_at timestamptz := clock_timestamp();
    step_started timestamptz;
BEGIN
    step_started := clock_timestamp();
    REFRESH MATERIALIZED VIEW investory.reporting_account_monthly_profit_reconciliation;
    ANALYZE investory.reporting_account_monthly_profit_reconciliation;
    RAISE LOG 'investory refresh stage=reporting_account_monthly_profit_reconciliation elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    step_started := clock_timestamp();
    REFRESH MATERIALIZED VIEW investory.reporting_account_statistics_vs_daily_reconciliation;
    ANALYZE investory.reporting_account_statistics_vs_daily_reconciliation;
    RAISE LOG 'investory refresh stage=reporting_account_statistics_vs_daily_reconciliation elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    step_started := clock_timestamp();
    REFRESH MATERIALIZED VIEW investory.reporting_account_daily_cashflow_reconciliation;
    ANALYZE investory.reporting_account_daily_cashflow_reconciliation;
    RAISE LOG 'investory refresh stage=reporting_account_daily_cashflow_reconciliation elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    step_started := clock_timestamp();
    REFRESH MATERIALIZED VIEW investory.reporting_account_daily_cashflow_scope;
    ANALYZE investory.reporting_account_daily_cashflow_scope;
    RAISE LOG 'investory refresh stage=reporting_account_daily_cashflow_scope elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    step_started := clock_timestamp();
    REFRESH MATERIALIZED VIEW investory.reporting_trade_settlement_reconciliation;
    ANALYZE investory.reporting_trade_settlement_reconciliation;
    RAISE LOG 'investory refresh stage=reporting_trade_settlement_reconciliation elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - step_started);
    RAISE LOG 'investory refresh stage=reconciliation_reporting_total elapsed_ms=%', EXTRACT(milliseconds FROM clock_timestamp() - started_at);
END;
$$;

CREATE OR REPLACE FUNCTION investory.refresh_reconciliation_views()
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM investory.refresh_reconstructed_position_daily();
    PERFORM investory.refresh_reconstructed_account_market_daily();
    PERFORM investory.refresh_reconstructed_cash_daily();
    PERFORM investory.refresh_account_daily_reconciliation();
    PERFORM investory.refresh_reconciliation_reporting_views();
END;
$$;
