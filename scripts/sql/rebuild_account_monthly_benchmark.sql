SET search_path TO investory, public;

-- Manual repair script. This is intentionally not a Flyway migration.
--
-- Rebuild account_monthly_benchmark as a thin projection of the canonical
-- account_monthly_mv performance aggregation. Boundary equity and cash-flow
-- arithmetic belongs only to reconciliation views.

CREATE OR REPLACE VIEW investory.account_monthly_benchmark AS
SELECT
    monthly.*
FROM investory.account_monthly_mv monthly;

COMMENT ON VIEW investory.account_monthly_benchmark IS
    'Monthly benchmark performance delegates portfolio P/L and return to account_monthly_mv, sourced from account_daily.';

-- Optional verification after running the script:
-- SELECT account_id, month, valuation_currency, opening_equity, closing_equity,
--        total_profit, compounded_monthly_return
-- FROM investory.account_monthly_benchmark
-- ORDER BY account_id, month;
