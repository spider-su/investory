-- Daily-only valuation begins only once a neutral daily/reference graph exists.
-- Until the first successful full refresh, the far-future boundary keeps the historical
-- estimate policy active rather than creating a fail-closed coverage hole.
UPDATE investory.fx_configuration
SET config_value = '9999-12-31'
WHERE config_key = 'daily_history_start';

CREATE OR REPLACE FUNCTION investory.fx_daily_coverage_supported(p_start_date date)
RETURNS boolean
LANGUAGE sql
STABLE
AS $$
    SELECT NOT EXISTS (
        SELECT 1
        FROM investory.currencies c
        WHERE NOT EXISTS (
            SELECT 1
            FROM investory.exchange_rates er
            WHERE er.rate_date = p_start_date
              AND er.method IN ('MARKET_DAILY', 'IBKR_DAILY_REFERENCE')
              AND er.rate > 0
              AND (er.base = c.id OR er.to_currency = c.id)
        )
    );
$$;

CREATE OR REPLACE FUNCTION investory.validate_daily_history_start()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.config_key = 'daily_history_start'
       AND NEW.config_value::date < DATE '9999-12-31'
       AND NOT investory.fx_daily_coverage_supported(NEW.config_value::date) THEN
        RAISE EXCEPTION 'daily_history_start % precedes supported neutral daily FX coverage', NEW.config_value;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_validate_daily_history_start
BEFORE INSERT OR UPDATE OF config_value ON investory.fx_configuration
FOR EACH ROW EXECUTE FUNCTION investory.validate_daily_history_start();

COMMENT ON FUNCTION investory.fx_daily_coverage_supported(date) IS
    'True only when every configured currency participates in neutral daily/reference FX on the proposed boundary date.';
