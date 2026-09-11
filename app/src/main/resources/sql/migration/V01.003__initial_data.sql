
SET search_path TO investory, public;

INSERT INTO investory.app_users (id, username, display_name, birth_date)
VALUES (1, 'sample.user', 'Sample User', DATE '1985-09-09')
ON CONFLICT (id) DO UPDATE
    SET username = EXCLUDED.username,
        display_name = EXCLUDED.display_name,
        birth_date = EXCLUDED.birth_date;

INSERT INTO investory.app_users (id, username, display_name, birth_date)
VALUES (2, 'happy.investor', 'Happy Investor', DATE '1984-01-01')
ON CONFLICT (id) DO UPDATE
    SET username = EXCLUDED.username,
        display_name = EXCLUDED.display_name,
        birth_date = EXCLUDED.birth_date;

SELECT setval(
               pg_get_serial_sequence('investory.app_users', 'id'),
               COALESCE((SELECT max(id) FROM investory.app_users), 1),
               true
       );

INSERT INTO investory.portfolios (id, name, base_currency, owner, user_id) VALUES
    (1, 'Sample Portfolio', 'USD', 'Sample User', 1)
ON CONFLICT DO NOTHING;

INSERT INTO investory.portfolios (id, name, base_currency, owner, user_id) VALUES
    (2, 'Happy Investor Portfolio', 'PLN', 'Happy Investor', 2)
ON CONFLICT DO NOTHING;

SELECT setval(
               pg_get_serial_sequence('investory.portfolios', 'id'),
               COALESCE((SELECT MAX(id) FROM investory.portfolios), 1),
               true
       );

INSERT INTO accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only) VALUES
    ('51551301', '51551301', 'PLN', 'XTB', 'Sample PLN Account', 'Sample User', 1, false),
    ('51822121', '51822121', 'USD', 'XTB', 'Sample USD Account', 'Sample User', 1, false),
    ('51747407', '51747407', 'EUR', 'XTB', 'Sample EUR Account', 'Sample User', 1, true),
    ('53582946', '53582946', 'USD', 'XTB', 'Sample Metals Account', 'Sample User', 1, false),
    ('51729109', '51729109', 'PLN', 'XTB', 'Sample Retirement Account', 'Sample User', 1, false),
    ('50290466', '50290466', 'PLN', 'XTB', 'Sample PLN Cash Account', 'Sample User', 1, true),
    ('51499241', '51499241', 'USD', 'XTB', 'Sample USD Trading Account', 'Sample User', 1, false),
    ('51548444', '51548444', 'EUR', 'XTB', 'Sample EUR Cash Account', 'Sample User', 1, true),
    ('51993106', '51993106', 'USD', 'XTB', 'Sample Income Account', 'Sample User', 1, false),
    ('51707603', '51707603', 'PLN', 'XTB', 'Sample PLN Reserve Account', 'Sample User', 1, true),
    ('17959259', '17959259', 'USD', 'IBKR', 'Sample IBKR Account', 'Sample User', 1, false),
    ('2051551301', '51551301', 'PLN', 'XTB', 'XTB PLN investment account', 'Happy Investor', 2, false),
    ('2051822121', '51822121', 'USD', 'XTB', 'XTB USD reserve account', 'Happy Investor', 2, false),
    ('2051747407', '51747407', 'EUR', 'XTB', 'XTB EUR cash account', 'Happy Investor', 2, true),
    ('2053582946', '53582946', 'USD', 'XTB', 'XTB metals account', 'Happy Investor', 2, false),
    ('2051729109', '51729109', 'PLN', 'XTB', 'XTB retirement account', 'Happy Investor', 2, false),
    ('2050290466', '50290466', 'PLN', 'XTB', 'XTB cash account', 'Happy Investor', 2, true),
    ('2051499241', '51499241', 'USD', 'XTB', 'XTB USD investment account', 'Happy Investor', 2, false),
    ('2051548444', '51548444', 'EUR', 'XTB', 'XTB EUR reserve account', 'Happy Investor', 2, true),
    ('2051993106', '51993106', 'USD', 'XTB', 'XTB income account', 'Happy Investor', 2, false),
    ('2051707603', '51707603', 'PLN', 'XTB', 'XTB PLN reserve account', 'Happy Investor', 2, true),
    ('2017959259', '17959259', 'USD', 'IBKR', 'IBKR USD investment account', 'Happy Investor', 2, false);

-- Canonical valuation observations for the reduced Happy Investor fixture. Missing calendar
-- dates are resolved from the latest earlier observation and reported as CARRY_FORWARD.
WITH fixture_anchors(rate_date, base, to_currency, rate) AS (
    VALUES
        (DATE '2024-07-31', 'EUR', 'USD', 1.08223900), (DATE '2024-07-31', 'EUR', 'PLN', 4.2952983671), (DATE '2024-07-31', 'USD', 'PLN', 3.96890000), (DATE '2024-07-31', 'PLN', 'USD', 0.25195898),
        (DATE '2025-03-01', 'EUR', 'USD', 1.03955700), (DATE '2025-03-01', 'EUR', 'PLN', 4.2096400758), (DATE '2025-03-01', 'USD', 'PLN', 3.99930000), (DATE '2025-03-01', 'PLN', 'USD', 0.25099000),
        (DATE '2025-04-01', 'EUR', 'USD', 1.08270600), (DATE '2025-04-01', 'EUR', 'PLN', 4.2096400758), (DATE '2025-04-01', 'USD', 'PLN', 3.86430000), (DATE '2025-04-01', 'PLN', 'USD', 0.25860800),
        (DATE '2025-05-01', 'EUR', 'USD', 1.13719900), (DATE '2025-05-01', 'EUR', 'PLN', 4.2096400758), (DATE '2025-05-01', 'USD', 'PLN', 3.76170000), (DATE '2025-05-01', 'PLN', 'USD', 0.25860800),
        (DATE '2025-06-01', 'EUR', 'USD', 1.13240300), (DATE '2025-06-01', 'EUR', 'PLN', 4.22199000), (DATE '2025-06-01', 'USD', 'PLN', 3.75370000), (DATE '2025-06-01', 'PLN', 'USD', 0.25860800),
        (DATE '2025-07-01', 'EUR', 'USD', 1.17296200), (DATE '2025-07-01', 'EUR', 'PLN', 4.25373100), (DATE '2025-07-01', 'USD', 'PLN', 3.61640000), (DATE '2025-07-01', 'PLN', 'USD', 0.25860800),
        (DATE '2025-08-01', 'EUR', 'USD', 1.14504700), (DATE '2025-08-01', 'EUR', 'PLN', 4.25373100), (DATE '2025-08-01', 'USD', 'PLN', 3.72570000), (DATE '2025-08-01', 'PLN', 'USD', 0.25860800),
        (DATE '2025-09-01', 'EUR', 'USD', 1.16753700), (DATE '2025-09-01', 'EUR', 'PLN', 4.25373100), (DATE '2025-09-01', 'USD', 'PLN', 3.65590000), (DATE '2025-09-01', 'PLN', 'USD', 0.25860800),
        (DATE '2025-10-01', 'EUR', 'USD', 1.17560200), (DATE '2025-10-01', 'EUR', 'PLN', 4.25373100), (DATE '2025-10-01', 'USD', 'PLN', 3.63150000), (DATE '2025-10-01', 'PLN', 'USD', 0.25860800),
        (DATE '2025-11-01', 'EUR', 'USD', 1.15760100), (DATE '2025-11-01', 'EUR', 'PLN', 4.25373100), (DATE '2025-11-01', 'USD', 'PLN', 3.67510000), (DATE '2025-11-01', 'PLN', 'USD', 0.25860800),
        (DATE '2025-12-01', 'EUR', 'USD', 1.15686400), (DATE '2025-12-01', 'EUR', 'PLN', 4.25373100), (DATE '2025-12-01', 'USD', 'PLN', 3.66240000), (DATE '2025-12-01', 'PLN', 'USD', 0.25860800),
        (DATE '2025-12-31', 'EUR', 'USD', 1.17356200), (DATE '2025-12-31', 'EUR', 'PLN', 4.2267008992), (DATE '2025-12-31', 'USD', 'PLN', 3.60160000), (DATE '2025-12-31', 'PLN', 'USD', 0.27765434),
        (DATE '2026-01-01', 'EUR', 'USD', 1.17356200), (DATE '2026-01-01', 'EUR', 'PLN', 4.25373100), (DATE '2026-01-01', 'USD', 'PLN', 3.60160000), (DATE '2026-01-01', 'PLN', 'USD', 0.27703500),
        (DATE '2026-02-01', 'EUR', 'USD', 1.19084800), (DATE '2026-02-01', 'EUR', 'PLN', 4.18726300), (DATE '2026-02-01', 'USD', 'PLN', 3.53790000), (DATE '2026-02-01', 'PLN', 'USD', 0.27633400),
        (DATE '2026-03-01', 'EUR', 'USD', 1.17956100), (DATE '2026-03-01', 'EUR', 'PLN', 4.18726300), (DATE '2026-03-01', 'USD', 'PLN', 3.58040000), (DATE '2026-03-01', 'PLN', 'USD', 0.27633400),
        (DATE '2026-04-01', 'EUR', 'USD', 1.14665300), (DATE '2026-04-01', 'EUR', 'PLN', 4.25313400), (DATE '2026-04-01', 'USD', 'PLN', 3.74080000), (DATE '2026-04-01', 'PLN', 'USD', 0.26849000),
        (DATE '2026-05-01', 'EUR', 'USD', 1.16810200), (DATE '2026-05-01', 'EUR', 'PLN', 4.25313400), (DATE '2026-05-01', 'USD', 'PLN', 3.64600000), (DATE '2026-05-01', 'PLN', 'USD', 0.26849000),
        (DATE '2026-06-01', 'EUR', 'USD', 1.16285200), (DATE '2026-06-01', 'EUR', 'PLN', 4.25313400), (DATE '2026-06-01', 'USD', 'PLN', 3.63950000), (DATE '2026-06-01', 'PLN', 'USD', 0.26849000),
        (DATE '2026-07-01', 'EUR', 'USD', 1.13936000), (DATE '2026-07-01', 'EUR', 'PLN', 4.25313400), (DATE '2026-07-01', 'USD', 'PLN', 3.77080000), (DATE '2026-07-01', 'PLN', 'USD', 0.26849000),
        (DATE '2026-08-01', 'EUR', 'USD', 1.15238500), (DATE '2026-08-01', 'EUR', 'PLN', 4.25313400), (DATE '2026-08-01', 'USD', 'PLN', 3.74250000), (DATE '2026-08-01', 'PLN', 'USD', 0.26849000)
), all_rates AS (
    SELECT rate_date, base, to_currency, rate FROM fixture_anchors
    UNION ALL
    SELECT rate_date, 'USD', 'EUR', 1 / rate
    FROM fixture_anchors WHERE base = 'EUR' AND to_currency = 'USD'
    UNION ALL
    SELECT rate_date, 'PLN', 'EUR', 1 / rate
    FROM fixture_anchors WHERE base = 'EUR' AND to_currency = 'PLN'
)
INSERT INTO investory.exchange_rates(
    rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, source_reference)
SELECT rate_date, base, to_currency, rate, 'VALUATION', 'DB60_INITIAL', 'OBSERVED',
       rate_date, 'V01.003:DB60:' || base || ':' || to_currency
FROM all_rates
ON CONFLICT (rate_date, base, to_currency) WHERE purpose = 'VALUATION' DO NOTHING;


INSERT INTO investory.assets (name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, active, isin, exclude_from_import)
VALUES
    ('Apple Inc.', 'AAPL.US', 'AAPL', 'AAPL', NULL, 'US', 'USD', 'EQUITY', true, NULL, false),
    ('Allegro.eu S.A.', 'ALE.PL', 'ALE', 'ALE', NULL, 'PL', 'PLN', 'EQUITY', false, NULL, true),
    ('Amazon.com, Inc.', 'AMZN.US', 'AMZN', 'AMZN', NULL, 'US', 'USD', 'EQUITY', true, NULL, false),
    ('iShares Core MSCI Emerging Markets IMI UCITS ETF (Acc)', 'EMIM.UK', 'EMIM', 'EMIM', 'EMIM.L', 'UK', 'USD', 'ETF', false, NULL, false),
    ('Beta ETF WIG20TR', 'ETFBW20TR.PL', 'ETFBW20TR', 'ETFBW20TR', NULL, 'PL', 'PLN', 'ETF', true, NULL, false),
    ('Alphabet Inc.', 'GOOGL.US', 'GOOGL', 'GOOGL', NULL, 'US', 'USD', 'EQUITY', true, NULL, false),
    ('HSBC FTSE EPRA NAREIT Developed UCITS ETF', 'HPRD.UK', 'HPRD', 'HPRD', 'HPRD.L', 'UK', 'USD', 'ETF', false, NULL, false),
    ('JPMorgan Equity Premium Income ETF', 'JGPI.DE', 'JGPI', 'JGPI', 'JGPI.DE', 'DE', 'EUR', 'ETF', true, NULL, false),
    ('Meta Platforms Inc Class A', 'META.US', 'META', 'META', NULL, 'US', 'USD', 'EQUITY', true, NULL, false),
    ('Microsoft Corp.', 'MSFT.US', 'MSFT', 'MSFT', NULL, 'US', 'USD', 'EQUITY', true, NULL, false),
    ('NATGAS', 'NATGAS', 'NATGAS', 'NATGAS', NULL, 'US', 'USD', 'COMMODITY', false, NULL, false),
    ('WisdomTree Uranium and Nuclear Energy UCITS ETF USD Acc', 'NCLR.UK', 'NCLR', 'NCLR', 'NCLR.L', 'UK', 'USD', 'ETF', false, NULL, false),
    ('VanEck Uranium and Nuclear Technologies UCITS ETF', 'NUCL.UK', 'NUCL', 'NUCL', 'NUCL.L', 'UK', 'USD', 'ETF', false, NULL, false),
    ('NVIDIA Corporation', 'NVDA.US', 'NVDA', 'NVDA', NULL, 'US', 'USD', 'EQUITY', true, NULL, false),
    ('Realty Income Corporation', 'O.US', 'O', 'O', NULL, 'US', 'USD', 'EQUITY', false, NULL, false),
    ('abrdn Physical Palladium Shares ETF', 'PALL.US', 'PALL', 'PALL', NULL, 'US', 'USD', 'ETF', false, NULL, false),
    ('ORLEN S.A.', 'PKN.PL', 'PKN', 'PKN', NULL, 'PL', 'PLN', 'EQUITY', false, NULL, false),
    ('PKO Bank Polski S.A.', 'PKO.PL', 'PKO', 'PKO', NULL, 'PL', 'PLN', 'EQUITY', false, NULL, false),
    ('Powszechny Zakład Ubezpieczeń Spółka Akcyjna', 'PZU.PL', 'PZU', 'PZU', NULL, 'PL', 'PLN', 'EQUITY', false, NULL, false),
    ('SPDR S&P Euro Dividend Aristocrats UCITS ETF (Dist)', 'SPYW.DE', 'SPYW', 'SPYW', NULL, 'DE', 'EUR', 'ETF', false, NULL, false),
    ('Tesla, Inc.', 'TSLA.US', 'TSLA', 'TSLA', NULL, 'US', 'USD', 'EQUITY', false, NULL, false),
    ('Vanguard FTSE All-World High Dividend Yield UCITS ETF (USD) Distributing', 'VHYL.UK', 'VHYL', 'VHYL', 'VHYL.L', 'UK', 'USD', 'ETF', false, NULL, false),
    ('Vanguard Funds Public Limited Company - Vanguard FTSE All-World High Dividend Yield UCITS ETF', 'VHYD.UK', 'VHYD', 'VHYD', 'VHYD.L', 'UK', 'USD', 'ETF', true, NULL, false),
    ('Vanguard FTSE All-World UCITS ETF (USD) Accumulating', 'VWRA.UK', 'VWRA', 'VWRA', 'VWRA.L', 'UK', 'USD', 'ETF', true, NULL, false),
    ('United States Treasury 4 5/8 02/28/26','US91282CKB62', 'US91282CKB62', 'T458022826', NULL,'US', 'USD', 'BOND', false, 'US91282CKB62', false),
    ('United States Treasury 4 3/8 07/31/33','US91282CRC72', 'US91282CRC72', 'T438073133', NULL, 'US', 'USD', 'BOND', true, 'US91282CRC72', false)
ON CONFLICT (symbol) DO UPDATE SET
    name = EXCLUDED.name,
    ticker = EXCLUDED.ticker,
    ibkr = EXCLUDED.ibkr,
    yahoo = EXCLUDED.yahoo,
    country = EXCLUDED.country,
    currency = EXCLUDED.currency,
    asset_type = EXCLUDED.asset_type,
    active = EXCLUDED.active,
    isin = EXCLUDED.isin,
    exclude_from_import = EXCLUDED.exclude_from_import;
