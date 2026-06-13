-- Keep a single stock row per symbol before adding uniqueness.
DELETE FROM stocks s
USING stocks d
WHERE s.symbol = d.symbol
  AND s.id < d.id;

CREATE UNIQUE INDEX IF NOT EXISTS ux_stocks_symbol ON stocks (symbol);
CREATE UNIQUE INDEX IF NOT EXISTS ux_currencies_base_to_currency ON currencies (base, to_currency);
CREATE UNIQUE INDEX IF NOT EXISTS ux_open_positions_history_symbol_day ON open_positions_history (symbol, (date::date));
CREATE UNIQUE INDEX IF NOT EXISTS ux_portfolio_history_portfolio_day ON portfolio_history (portfolio_id, (date::date));

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_accounts_currency_allowed') THEN
        ALTER TABLE accounts
            ADD CONSTRAINT chk_accounts_currency_allowed
                CHECK (currency IN ('USD', 'EUR', 'PLN'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_cash_operations_currency_allowed') THEN
        ALTER TABLE cash_operations
            ADD CONSTRAINT chk_cash_operations_currency_allowed
                CHECK (currency IN ('USD', 'EUR', 'PLN'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_closed_positions_currency_allowed') THEN
        ALTER TABLE closed_positions
            ADD CONSTRAINT chk_closed_positions_currency_allowed
                CHECK (currency IN ('USD', 'EUR', 'PLN'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_opened_positions_currency_allowed') THEN
        ALTER TABLE opened_positions
            ADD CONSTRAINT chk_opened_positions_currency_allowed
                CHECK (currency IN ('USD', 'EUR', 'PLN'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_stocks_currency_allowed') THEN
        ALTER TABLE stocks
            ADD CONSTRAINT chk_stocks_currency_allowed
                CHECK (currency IN ('USD', 'EUR', 'PLN'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_open_positions_history_currency_allowed') THEN
        ALTER TABLE open_positions_history
            ADD CONSTRAINT chk_open_positions_history_currency_allowed
                CHECK (currency IN ('USD', 'EUR', 'PLN'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_portfolio_history_currency_allowed') THEN
        ALTER TABLE portfolio_history
            ADD CONSTRAINT chk_portfolio_history_currency_allowed
                CHECK (currency IN ('USD', 'EUR', 'PLN'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_currencies_base_allowed') THEN
        ALTER TABLE currencies
            ADD CONSTRAINT chk_currencies_base_allowed
                CHECK (base IN ('USD', 'EUR', 'PLN'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_currencies_to_currency_allowed') THEN
        ALTER TABLE currencies
            ADD CONSTRAINT chk_currencies_to_currency_allowed
                CHECK (to_currency IN ('USD', 'EUR', 'PLN'));
    END IF;
END $$;

