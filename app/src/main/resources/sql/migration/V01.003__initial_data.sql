
SET search_path TO investory, public;

INSERT INTO investory.app_users (id, username, display_name, birth_date)
VALUES (1, 'sample.user', 'Sample User', DATE '1985-09-09')
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
    ('17959259', '17959259', 'USD', 'IBKR', 'Sample IBKR Account', 'Sample User', 1, false);

INSERT INTO investory.exchange_rates (rate_date, base, to_currency, rate) VALUES
    ('2024-07-31', 'EUR', 'USD', 1.082239),
    ('2024-08-30', 'EUR', 'USD', 1.107494),
    ('2024-09-30', 'EUR', 'USD', 1.120389),
    ('2024-10-31', 'EUR', 'USD', 1.086647),
    ('2024-11-29', 'EUR', 'USD', 1.055752),

    ('2024-12-31', 'EUR', 'USD', 1.041890),
    ('2025-01-31', 'EUR', 'USD', 1.038299),
    ('2025-02-28', 'EUR', 'USD', 1.039557),
    ('2025-03-31', 'EUR', 'USD', 1.082706),
    ('2025-04-30', 'EUR', 'USD', 1.137199),
    ('2025-05-30', 'EUR', 'USD', 1.132403),
    ('2025-06-30', 'EUR', 'USD', 1.172962),
    ('2025-07-31', 'EUR', 'USD', 1.145047),
    ('2025-08-29', 'EUR', 'USD', 1.167537),
    ('2025-09-30', 'EUR', 'USD', 1.175602),
    ('2025-10-31', 'EUR', 'USD', 1.157601),
    ('2025-11-28', 'EUR', 'USD', 1.156864),

    ('2025-12-31', 'EUR', 'USD', 1.173562),
    ('2026-01-30', 'EUR', 'USD', 1.190848),
    ('2026-02-27', 'EUR', 'USD', 1.179561),
    ('2026-03-31', 'EUR', 'USD', 1.146653),
    ('2026-04-30', 'EUR', 'USD', 1.168102),
    ('2026-05-29', 'EUR', 'USD', 1.162852),
    ('2026-06-30', 'EUR', 'USD', 1.139360),
    ('2026-07-31', 'EUR', 'USD', 1.152385);

INSERT INTO investory.exchange_rates (rate_date, base, to_currency, rate, source, method) VALUES
    ('2024-07-31', 'USD', 'PLN', 3.9689, 'NBP', 'HISTORICAL_MONTHLY'),
    ('2024-08-30', 'USD', 'PLN', 3.8644, 'NBP', 'HISTORICAL_MONTHLY'),
    ('2024-09-30', 'USD', 'PLN', 3.8193, 'NBP', 'HISTORICAL_MONTHLY'),
    ('2024-10-31', 'USD', 'PLN', 4.0059, 'NBP', 'HISTORICAL_MONTHLY'),
    ('2024-11-29', 'USD', 'PLN', 4.0770, 'NBP', 'HISTORICAL_MONTHLY'),
    ('2024-12-31', 'USD', 'PLN', 4.1012, 'NBP', 'HISTORICAL_MONTHLY'),
    ('2025-01-31', 'USD', 'PLN', 4.0576, 'NBP', 'HISTORICAL_MONTHLY'),
    ('2025-02-28', 'USD', 'PLN', 3.9993, 'NBP', 'HISTORICAL_MONTHLY'),
    ('2025-03-31', 'USD', 'PLN', 3.8643, 'NBP', 'HISTORICAL_MONTHLY'),
    ('2025-04-30', 'USD', 'PLN', 3.7617, 'NBP', 'HISTORICAL_MONTHLY'),
    ('2025-05-30', 'USD', 'PLN', 3.7537, 'NBP', 'HISTORICAL_MONTHLY'),
    ('2025-06-30', 'USD', 'PLN', 3.6164, 'NBP', 'HISTORICAL_MONTHLY'),
    ('2025-07-31', 'USD', 'PLN', 3.7257, 'NBP', 'HISTORICAL_MONTHLY'),
    ('2025-08-29', 'USD', 'PLN', 3.6559, 'NBP', 'HISTORICAL_MONTHLY'),
    ('2025-09-30', 'USD', 'PLN', 3.6315, 'NBP', 'HISTORICAL_MONTHLY'),
    ('2025-10-31', 'USD', 'PLN', 3.6751, 'NBP', 'HISTORICAL_MONTHLY'),
    ('2025-11-28', 'USD', 'PLN', 3.6624, 'NBP', 'HISTORICAL_MONTHLY'),
    ('2025-12-31', 'USD', 'PLN', 3.6016, 'NBP', 'HISTORICAL_MONTHLY'),
    ('2026-01-30', 'USD', 'PLN', 3.5379, 'NBP', 'HISTORICAL_MONTHLY'),
    ('2026-02-27', 'USD', 'PLN', 3.5804, 'NBP', 'HISTORICAL_MONTHLY'),
    ('2026-03-31', 'USD', 'PLN', 3.7408, 'NBP', 'HISTORICAL_MONTHLY'),
    ('2026-04-30', 'USD', 'PLN', 3.6460, 'NBP', 'HISTORICAL_MONTHLY'),
    ('2026-05-29', 'USD', 'PLN', 3.6395, 'NBP', 'HISTORICAL_MONTHLY'),
    ('2026-06-30', 'USD', 'PLN', 3.7708, 'NBP', 'HISTORICAL_MONTHLY'),
    ('2026-07-31', 'USD', 'PLN', 3.7425, 'NBP', 'HISTORICAL_MONTHLY')
ON CONFLICT (rate_date, base, to_currency, source, method, COALESCE(source_reference, ''))
    DO UPDATE SET rate = EXCLUDED.rate;


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
