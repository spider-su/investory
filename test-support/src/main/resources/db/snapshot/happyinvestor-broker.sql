SET search_path TO investory, public;

-- Happy Investor BROKER overlay: the imported ledger (cash operations + positions) for the four
-- canonical accounts. For non-golden ITs this is loaded on top of happyinvestor-common.sql; the
-- golden path reproduces this exact layer by importing the broker fixture files instead.

-- F1-F4 ledger bridge. These rows mirror HappyInvestorScenario: EUR funds half of
-- the canonical PLN/USD account funding through explicit FX transfers; transfers
-- are not profit, and each account has an explicit withdrawal.
INSERT INTO cash_operations
    (id, account_id, operation, amount, currency, comment, date,
     execution_fx_base, execution_fx_to_currency, execution_fx_rate)
VALUES
    (7001, 17959259, 'DEPOSIT', 100000, 'USD', 'Happy Investor external funding', '2024-07-31 12:00:00+02', NULL, NULL, NULL),
    (7002, 17959259, 'WITHDRAWAL', -3000, 'USD', 'Happy Investor explicit withdrawal', '2025-12-31 12:00:00+01', NULL, NULL, NULL),
    (7003, 51499241, 'DEPOSIT', 4000, 'USD', 'Happy Investor external funding', '2024-07-31 12:00:00+02', NULL, NULL, NULL),
    (7004, 51499241, 'WITHDRAWAL', -1000, 'USD', 'Happy Investor explicit withdrawal', '2025-12-31 12:00:00+01', NULL, NULL, NULL),
    (7005, 51551301, 'DEPOSIT', 4000, 'PLN', 'Happy Investor external funding', '2024-07-31 12:00:00+02', NULL, NULL, NULL),
    (7006, 51551301, 'WITHDRAWAL', -1000, 'PLN', 'Happy Investor explicit withdrawal', '2025-12-31 12:00:00+01', NULL, NULL, NULL),
    (7007, 51548444, 'DEPOSIT', 8000, 'EUR', 'Happy Investor external funding', '2024-07-31 12:00:00+02', NULL, NULL, NULL),
    (7008, 51548444, 'WITHDRAWAL', -2000, 'EUR', 'Happy Investor explicit withdrawal', '2025-12-31 12:00:00+01', NULL, NULL, NULL),
    (7009, 51548444, 'TRANSFER', -4000, 'EUR', 'EUR-USD-2024-07-31', '2024-07-31 12:00:00+02', 'EUR', 'USD', 1.082239),
    (7010, 51499241, 'TRANSFER', 4328.956, 'USD', 'EUR-USD-2024-07-31', '2024-07-31 12:00:00+02', 'EUR', 'USD', 1.082239),
    (7011, 51548444, 'TRANSFER', -4000, 'EUR', 'EUR-PLN-2024-07-31', '2024-07-31 12:00:00+02', 'EUR', 'PLN', 4.2952983671),
    (7012, 51551301, 'TRANSFER', 17181.1934684000, 'PLN', 'EUR-PLN-2024-07-31', '2024-07-31 12:00:00+02', 'EUR', 'PLN', 4.2952983671),
    (7013, 51551301, 'TRANSFER', -500, 'PLN', 'PLN-USD-2025-03', '2025-03-31 12:00:00+02', 'PLN', 'USD', 0.2519589810778805),
    (7014, 51499241, 'TRANSFER', 125.9794905389403, 'USD', 'PLN-USD-2025-03', '2025-03-31 12:00:00+02', 'PLN', 'USD', 0.2519589810778805),
    (7015, 51499241, 'TRANSFER', -500, 'USD', 'USD-PLN-2025-03', '2025-03-31 12:00:00+02', 'USD', 'PLN', 3.9993),
    (7016, 51551301, 'TRANSFER', 1999.65, 'PLN', 'USD-PLN-2025-03', '2025-03-31 12:00:00+02', 'USD', 'PLN', 3.9993),
    (7017, 17959259, 'COMMISSION', -1, 'USD', 'IBKR trade commission', '2024-08-08 12:00:00+02', NULL, NULL, NULL),
    (7018, 17959259, 'DIVIDEND', 120, 'USD', 'Canonical dividend', '2025-06-30 12:00:00+02', NULL, NULL, NULL),
    (7019, 17959259, 'WITHHOLDING_TAX', -22.8, 'USD', 'Canonical dividend tax 19%', '2025-06-30 12:00:00+02', NULL, NULL, NULL),
    (7020, 17959259, 'FREE_FUNDS_INTEREST', 231.25, 'USD', 'Canonical Treasury interest', '2025-02-28 12:00:00+01', NULL, NULL, NULL),
    (7021, 17959259, 'FREE_FUNDS_INTEREST_TAX', -43.9375, 'USD', 'Canonical Treasury interest tax 19%', '2025-02-28 12:00:00+01', NULL, NULL, NULL)
ON CONFLICT (id) DO NOTHING;

-- NATGAS RESULT_ONLY CFD settlement cash. The realized trade result folds the gross close (105.90)
-- and the rollover financing (-86.10) into a single CLOSE_TRADE of 19.80 (= net result 19.12 minus
-- the -0.68 swap fee); SWAP (-0.68) stays a separate financing fee. Net cash impact is 19.12, which
-- matches position 7110's stored profit. Asset id + date link these rows to the closed lot.
INSERT INTO cash_operations
    (id, account_id, operation, asset_id, source_asset_symbol, broker_symbol,
     amount, currency, comment, date)
VALUES
    (7022, 51499241, 'CLOSE_TRADE', 501, 'NATGAS', 'NATGAS', 19.80, 'USD', 'NATGAS CFD 2040572606 close (gross 105.90 net of -86.10 rollover)', '2025-09-26 12:00:00+02'),
    (7023, 51499241, 'SWAP', 501, 'NATGAS', 'NATGAS', -0.68, 'USD', 'NATGAS CFD 2040572606 swap', '2025-09-26 12:00:00+02')
ON CONFLICT (id) DO NOTHING;

-- Broker positions. Equities are CASH_SETTLED open lots (unrealized P/L is derived from market
-- price, so the stored profit is 0). NATGAS is the closed RESULT_ONLY CFD lot (position 7110),
-- matching HappyInvestorScenario and the golden import.
INSERT INTO positions
    (id, account_id, asset_id, source_asset_symbol, broker_symbol, operation,
     settlement_model, volume, price_currency, cost_currency, profit_currency,
     commission_currency, open_time, open_price, source_open_price,
     open_conversion_rate, base_value, purchase_value, commission, swap, profit)
VALUES
    (7101, 17959259, 1, 'AAPL.US', 'AAPL', 'BUY', 'CASH_SETTLED', 100, 'USD', 'USD', 'USD', 'USD', '2024-08-08 12:00:00+02', 180, 180, 1, 18000, 18000, -1, NULL, 0),
    (7102, 17959259, 1, 'AAPL.US', 'AAPL', 'BUY', 'CASH_SETTLED', 50, 'USD', 'USD', 'USD', 'USD', '2025-02-12 12:00:00+01', 200, 200, 1, 10000, 10000, -1, NULL, 0),
    (7103, 17959259, 1151, 'VWRA.UK', 'VWRA', 'BUY', 'CASH_SETTLED', 20, 'USD', 'USD', 'USD', 'USD', '2024-07-31 12:00:00+02', 120, 120, 1, 2400, 2400, -1, NULL, 0),
    (7104, 51551301, 1151, 'VWRA.UK', 'VWRA', 'BUY', 'CASH_SETTLED', 10, 'USD', 'USD', 'USD', 'USD', '2024-07-31 12:00:00+02', 130, 130, 1, 1300, 1300, 0, NULL, 0),
    (7105, 51499241, 651, 'NVDA.US', 'NVDA', 'BUY', 'CASH_SETTLED', 10, 'USD', 'USD', 'USD', 'USD', '2024-07-31 12:00:00+02', 100, 100, 1, 1000, 1000, 0, NULL, 0),
    (7106, 51499241, 1001, 'TSLA.US', 'TSLA', 'BUY', 'CASH_SETTLED', 1, 'USD', 'USD', 'USD', 'USD', '2024-07-31 12:00:00+02', 200, 200, 1, 200, 200, 0, NULL, 0),
    (7107, 51551301, 251, 'GOOGL.US', 'GOOGL', 'BUY', 'CASH_SETTLED', 5, 'USD', 'USD', 'USD', 'USD', '2024-07-31 12:00:00+02', 150, 150, 1, 750, 750, 0, NULL, 0),
    (7108, 17959259, 451, 'MSFT.US', 'MSFT', 'BUY', 'CASH_SETTLED', 10, 'USD', 'USD', 'USD', 'USD', '2024-07-31 12:00:00+02', 100, 100, 1, 1000, 1000, -1, NULL, 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO positions
    (id, account_id, asset_id, source_asset_symbol, broker_symbol, operation,
     settlement_model, volume, price_currency, cost_currency, profit_currency,
     commission_currency, open_time, open_price, source_open_price, open_conversion_rate,
     close_time, close_price, source_close_price, close_conversion_rate,
     base_value, purchase_value, sale_value, commission, swap, profit)
VALUES
    (7110, 51499241, 501, 'NATGAS', 'NATGAS', 'BUY', 'RESULT_ONLY', 0.01, 'USD', 'USD', 'USD', 'USD', '2025-09-26 12:00:00+02', 2.946, 2.946, 1, '2025-09-26 12:00:00+02', NULL, NULL, NULL, 0.02946, 0.02946, NULL, 0, -0.68, 19.12)
ON CONFLICT (id) DO NOTHING;

