
SET search_path TO investory, public;

INSERT INTO investory.app_users (id, username, display_name)
VALUES (1, 'sample.user', 'Sample User')
ON CONFLICT (id) DO UPDATE
    SET username = EXCLUDED.username,
        display_name = EXCLUDED.display_name;

SELECT setval(
               pg_get_serial_sequence('investory.app_users', 'id'),
               COALESCE((SELECT max(id) FROM investory.app_users), 1),
               true
       );

INSERT INTO investory.portfolios (id, name, base_currency, owner, user_id) VALUES
    (1, 'Sample Portfolio', 'USD', 'Sample User', 1)
on conflict do nothing;

SELECT setval(
               pg_get_serial_sequence('investory.portfolios', 'id'),
               COALESCE((SELECT MAX(id) FROM investory.portfolios), 1),
               true
       );

insert into accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only) values
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
    ('2026-07-31', 'EUR', 'USD', 1.152385),


    -- USD -> PLN

    ('2024-08-01', 'USD', 'PLN', 3.9689), -- NBP 2024-07-31
    ('2024-09-01', 'USD', 'PLN', 3.8644), -- NBP 2024-08-30
    ('2024-10-01', 'USD', 'PLN', 3.8193), -- NBP 2024-09-30
    ('2024-11-01', 'USD', 'PLN', 4.0059), -- NBP 2024-10-31
    ('2024-12-01', 'USD', 'PLN', 4.0770), -- NBP 2024-11-29

    ('2025-01-01', 'USD', 'PLN', 4.1012), -- NBP 2024-12-31
    ('2025-02-01', 'USD', 'PLN', 4.0576), -- NBP 2025-01-31
    ('2025-03-01', 'USD', 'PLN', 3.9993), -- NBP 2025-02-28
    ('2025-04-01', 'USD', 'PLN', 3.8643), -- NBP 2025-03-31
    ('2025-05-01', 'USD', 'PLN', 3.7617), -- NBP 2025-04-30
    ('2025-06-01', 'USD', 'PLN', 3.7537), -- NBP 2025-05-30
    ('2025-07-01', 'USD', 'PLN', 3.6164), -- NBP 2025-06-30
    ('2025-08-01', 'USD', 'PLN', 3.7257), -- NBP 2025-07-31
    ('2025-09-01', 'USD', 'PLN', 3.6559), -- NBP 2025-08-29
    ('2025-10-01', 'USD', 'PLN', 3.6315), -- NBP 2025-09-30
    ('2025-11-01', 'USD', 'PLN', 3.6751), -- NBP 2025-10-31
    ('2025-12-01', 'USD', 'PLN', 3.6624), -- NBP 2025-11-28

    ('2026-01-01', 'USD', 'PLN', 3.6016), -- NBP 2025-12-31
    ('2026-02-01', 'USD', 'PLN', 3.5379), -- NBP 2026-01-30
    ('2026-03-01', 'USD', 'PLN', 3.5804), -- NBP 2026-02-27
    ('2026-04-01', 'USD', 'PLN', 3.7408), -- NBP 2026-03-31
    ('2026-05-01', 'USD', 'PLN', 3.6460), -- NBP 2026-04-30
    ('2026-06-01', 'USD', 'PLN', 3.6395), -- NBP 2026-05-29
    ('2026-07-01', 'USD', 'PLN', 3.7708), -- NBP 2026-06-30
    ('2026-08-01', 'USD', 'PLN', 3.7425)  -- NBP 2026-07-31
ON CONFLICT (rate_date, base, to_currency, source, method, COALESCE(source_reference, ''))
    DO UPDATE SET
    rate = EXCLUDED.rate;

UPDATE investory.exchange_rates
SET source = 'NBP',
    method = 'HISTORICAL_MONTHLY',
    rate_date = CASE rate_date
        WHEN DATE '2024-08-01' THEN DATE '2024-07-31'
        WHEN DATE '2024-09-01' THEN DATE '2024-08-30'
        WHEN DATE '2024-10-01' THEN DATE '2024-09-30'
        WHEN DATE '2024-11-01' THEN DATE '2024-10-31'
        WHEN DATE '2024-12-01' THEN DATE '2024-11-29'
        WHEN DATE '2025-01-01' THEN DATE '2024-12-31'
        WHEN DATE '2025-02-01' THEN DATE '2025-01-31'
        WHEN DATE '2025-03-01' THEN DATE '2025-02-28'
        WHEN DATE '2025-04-01' THEN DATE '2025-03-31'
        WHEN DATE '2025-05-01' THEN DATE '2025-04-30'
        WHEN DATE '2025-06-01' THEN DATE '2025-05-30'
        WHEN DATE '2025-07-01' THEN DATE '2025-06-30'
        WHEN DATE '2025-08-01' THEN DATE '2025-07-31'
        WHEN DATE '2025-09-01' THEN DATE '2025-08-29'
        WHEN DATE '2025-10-01' THEN DATE '2025-09-30'
        WHEN DATE '2025-11-01' THEN DATE '2025-10-31'
        WHEN DATE '2025-12-01' THEN DATE '2025-11-28'
        WHEN DATE '2026-01-01' THEN DATE '2025-12-31'
        WHEN DATE '2026-02-01' THEN DATE '2026-01-30'
        WHEN DATE '2026-03-01' THEN DATE '2026-02-27'
        WHEN DATE '2026-04-01' THEN DATE '2026-03-31'
        WHEN DATE '2026-05-01' THEN DATE '2026-04-30'
        WHEN DATE '2026-06-01' THEN DATE '2026-05-29'
        WHEN DATE '2026-07-01' THEN DATE '2026-06-30'
        WHEN DATE '2026-08-01' THEN DATE '2026-07-31'
        ELSE rate_date END
WHERE source = 'STATIC_BOOTSTRAP';

INSERT INTO investory.assets (name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, active)
VALUES
    ('Broadcom Inc.', '1YD.DE', '1YD', '1YD', '1YD.DE', 'DE', 'EUR', 'EQUITY', false),
    ('Xiaomi Corporation', '3CP.DE', '3CP', '3CP', '3CP.DE', 'DE', 'EUR', 'EQUITY', false),
    ('Apple Inc.', 'AAPL.US', 'AAPL', 'AAPL', 'AAPL.US', 'US', 'USD', 'EQUITY', true),
    ('ABB Ltd', 'ABB.SE', 'ABB', 'ABB', 'ABB.SE', 'SE', 'USD', 'EQUITY', false),
    ('AbbVie Inc.', 'ABBV.US', 'ABBV', 'ABBV', 'ABBV.US', 'US', 'USD', 'EQUITY', false),
    ('Alphabet Inc.', 'ABEA.DE', 'ABEA', 'ABEA', 'ABEA.DE', 'DE', 'EUR', 'EQUITY', false),
    ('Airbnb, Inc.', 'ABNB.US', 'ABNB', 'ABNB', 'ABNB.US', 'US', 'USD', 'EQUITY', false),
    ('Adobe Inc.', 'ADBE.US', 'ADBE', 'ADBE', 'ADBE.US', 'US', 'USD', 'EQUITY', false),
    ('Agree Realty Corporation', 'ADC.US', 'ADC', 'ADC', 'ADC.US', 'US', 'USD', 'REIT', false),
    ('Analog Devices, Inc.', 'ADI.US', 'ADI', 'ADI', 'ADI.US', 'US', 'USD', 'EQUITY', true),
    ('Archer-Daniels-Midland Co', 'ADM.US', 'ADM', 'ADM', 'ADM.US', 'US', 'USD', 'EQUITY', false),
    ('Aedas Homes, S.A.', 'AEDAS.ES', 'AEDAS', 'AEDAS', 'AEDAS.ES', 'ES', 'EUR', 'EQUITY', false),
    ('American Financial Group Inc.', 'AFG.US', 'AFG', 'AFG', 'AFG.US', 'US', 'USD', 'EQUITY', false),
    ('First Majestic Silver Corp', 'AG.US', 'AG', 'AG', 'AG.US', 'US', 'USD', 'EQUITY', false),
    ('iShares Core Global Aggregate Bond UCITS ETF USD (Dist)', 'AGGG.UK', 'AGGG', 'AGGG', 'AGGG.L', 'UK', 'USD', 'ETF', false),
    ('AGNC Investment Corp.', 'AGNC.US', 'AGNC', 'AGNC', 'AGNC.US', 'US', 'USD', 'REIT', true),
    ('Alibaba Group Holding Limited SP ADR', 'AHLA.DE', 'AHLA', 'AHLA', 'AHLA.DE', 'DE', 'EUR', 'EQUITY', false),
    ('WisdomTree Industrial Metals', 'AIGI.UK', 'AIGI', 'AIGI', 'AIGI.L', 'UK', 'USD', 'ETF', false),
    ('Airbus SE', 'AIR.DE', 'AIR', 'AIR', 'AIR.DE', 'DE', 'EUR', 'EQUITY', false),
    ('Allegro.eu S.A.', 'ALE.PL', 'ALE', 'ALE', 'ALE.PL', 'PL', 'PLN', 'EQUITY', false),
    ('Alior Bank S.A.', 'ALR.PL', 'ALR', 'ALR', 'ALR.PL', 'PL', 'PLN', 'EQUITY', false),
    ('Applied Materials, Inc.', 'AMAT.US', 'AMAT', 'AMAT', 'AMAT.US', 'US', 'USD', 'EQUITY', true),
    ('Advanced Micro Devices, Inc.', 'AMD.US', 'AMD', 'AMD', 'AMD.US', 'US', 'USD', 'EQUITY', true),
    ('Amgen Inc.', 'AMGN.US', 'AMGN', 'AMGN', 'AMGN.US', 'US', 'USD', 'EQUITY', false),
    ('American Tower Corporation', 'AMT.US', 'AMT', 'AMT', 'AMT.US', 'US', 'USD', 'REIT', false),
    ('Amazon.com, Inc.', 'AMZN.US', 'AMZN', 'AMZN', 'AMZN.US', 'US', 'USD', 'EQUITY', true),
    ('Arista Networks, Inc.', 'ANET.US', 'ANET', 'ANET', 'ANET.US', 'US', 'USD', 'EQUITY', true),
    ('Applied Digital Corporation', 'APLD.US', 'APLD', 'APLD', 'APLD.US', 'US', 'USD', 'EQUITY', true),
    ('AppLovin Corporation Class A Common Stock', 'APP.US', 'APP', 'APP', 'APP.US', 'US', 'USD', 'EQUITY', false),
    ('Ares Capital Corporation', 'ARCC.US', 'ARCC', 'ARCC', 'ARCC.US', 'US', 'USD', 'FUND', false),
    ('Alexandria Real Estate Equities, Inc.', 'ARE.US', 'ARE', 'ARE', 'ARE.US', 'US', 'USD', 'REIT', false),
    ('Arm Holdings plc American Depositary Receipt', 'ARM.US', 'ARM', 'ARM', 'ARM.US', 'US', 'USD', 'EQUITY', false),
    ('ASML Holding N.V. New York Registry Shares', 'ASML.US', 'ASML', 'ASML', 'ASML.US', 'US', 'USD', 'EQUITY', true),
    ('HANetf Future of Defence UCITS ETF', 'ASWC.DE', 'ASWC', 'ASWC', 'ASWC.DE', 'DE', 'EUR', 'ETF', false),
    ('Broadcom Inc.', 'AVGO.US', 'AVGO', 'AVGO', 'AVGO.US', 'US', 'USD', 'EQUITY', true),
    ('Avery Dennison Corporation', 'AVY.US', 'AVY', 'AVY', 'AVY.US', 'US', 'USD', 'EQUITY', false),
    ('BARRICK MINING CORP Common Stock (ABR0)', 'B.US', 'B', 'GOLD', 'B.US', 'US', 'USD', 'EQUITY', false),
    ('BAE Systems plc', 'BA.US', 'BA', 'BA', 'BA.L', 'UK', 'USD', 'EQUITY', false),
    ('Alibaba Group Holding Limited SP ADR', 'BABA.US', 'BABA', 'BABA', 'BABA.US', 'US', 'USD', 'EQUITY', true),
    ('Bank of America Corporation', 'BAC.US', 'BAC', 'BAC', 'BAC.US', 'US', 'USD', 'EQUITY', false),
    ('Benefit Systems S.A.', 'BFT.PL', 'BFT', 'BFT', 'BFT.PL', 'PL', 'PLN', 'EQUITY', false),
    ('Burke & Herbert Financial Services Corp.', 'BHRB.US', 'BHRB', 'BHRB', 'BHRB.US', 'US', 'USD', 'EQUITY', false),
    ('Bank Handlowy w Warszawie S.A.', 'BHW.PL', 'BHW', 'BHW', 'BHW.PL', 'PL', 'PLN', 'EQUITY', false),
    ('BITCOIN', 'BITCOIN', 'BITCOIN', 'BITCOIN', 'BITCOIN', 'US', 'USD', 'CRYPTOCURRENCY', false),
    ('BlackRock, Inc.', 'BLK.US', 'BLK', 'BLK', 'BLK.US', 'US', 'USD', 'EQUITY', false),
    ('Bristol-Myers Squibb Company', 'BMY.US', 'BMY', 'BMY', 'BMY.US', 'US', 'USD', 'EQUITY', false),
    ('Berkshire Hathaway Inc. Class B', 'BRKB.US', 'BRKB', 'BRKB', 'BRKB.US', 'US', 'USD', 'EQUITY', false),
    ('Anheuser-Busch InBev S.A. ADR', 'BUD.US', 'BUD', 'BUD', 'BUD.US', 'US', 'USD', 'EQUITY', false),
    ('BYD Company Limited', 'BY6.DE', 'BY6', 'BY6', 'BY6.DE', 'DE', 'EUR', 'EQUITY', false),
    ('Caterpillar Inc.', 'CAT.US', 'CAT', 'CAT', 'CAT.US', 'US', 'USD', 'EQUITY', true),
    ('Chubb Limited', 'CB.US', 'CB', 'CB', 'CB.US', 'US', 'USD', 'EQUITY', false),
    ('The Coca-Cola Company - CDR', 'CCC.PL', 'CCC', 'CCC', 'CCC.PL', 'PL', 'PLN', 'EQUITY', false),
    ('Cameco Corporation', 'CCJ.US', 'CCJ', 'CCJ', 'CCJ.US', 'US', 'USD', 'EQUITY', false),
    ('CD Projekt S.A.', 'CDR.PL', 'CDR', 'CDR', 'CDR.PL', 'PL', 'PLN', 'EQUITY', true),
    ('Church & Dwight Co., Inc.', 'CHD.US', 'CHD', 'CHD', 'CHD.US', 'US', 'USD', 'EQUITY', false),
    ('iShares MSCI Japan UCITS ETF USD (Acc)', 'CJPU.UK', 'CJPU', 'CJPU', 'CJPU.L', 'UK', 'USD', 'ETF', true),
    ('CME Group Inc.', 'CME.US', 'CME', 'CME', 'CME.US', 'US', 'USD', 'EQUITY', false),
    ('iShares NASDAQ 100 UCITS ETF USD (Acc)', 'CNDX.UK', 'CNDX', 'CNDX', 'CNDX.L', 'UK', 'USD', 'ETF', false),
    ('COCOA', 'COCOA', 'COCOA', 'COCOA', 'COCOA', 'US', 'USD', 'COMMODITY', false),
    ('ConocoPhillips', 'COP.US', 'COP', 'COP', 'COP.US', 'US', 'USD', 'EQUITY', false),
    ('Themes Copper Miners ETF', 'COPA.UK', 'COPA', 'COPA', 'COPA.L', 'UK', 'USD', 'ETF', false),
    ('Costco Wholesale Corporation', 'COST.US', 'COST', 'COST', 'COST.US', 'US', 'USD', 'EQUITY', false),
    ('Copart Inc.', 'CPRT.US', 'CPRT', 'CPRT', 'CPRT.US', 'US', 'USD', 'EQUITY', false),
    ('Circle Internet Group, Inc.', 'CRCL.US', 'CRCL', 'CRCL', 'CRCL.US', 'US', 'USD', 'EQUITY', false),
    ('Credo Technology Group Holding Ltd.', 'CRDO.US', 'CRDO', 'CRDO', 'CRDO.US', 'US', 'USD', 'EQUITY', false),
    ('Salesforce Inc', 'CRM.US', 'CRM', 'CRM', 'CRM.US', 'US', 'USD', 'EQUITY', true),
    ('CoreWeave, Inc.', 'CRWV.US', 'CRWV', 'CRWV', 'CRWV.US', 'US', 'USD', 'EQUITY', false),
    ('Cisco Systems, Inc.', 'CSCO.US', 'CSCO', 'CSCO', 'CSCO.US', 'US', 'USD', 'EQUITY', false),
    ('iShares MSCI Korea UCITS ETF USD (Acc)', 'CSKR.UK', 'CSKR', 'CSKR', 'CSKR.L', 'UK', 'USD', 'ETF', true),
    ('iShares Core S&P 500 UCITS ETF USD', 'CSPX.UK', 'CSPX', 'CSPX', 'CSPX.L', 'UK', 'USD', 'ETF', false),
    ('Carvana Co.', 'CVNA.US', 'CVNA', 'CVNA', 'CVNA.US', 'US', 'USD', 'EQUITY', false),
    ('Chevron Corporation', 'CVX.US', 'CVX', 'CVX', 'CVX.US', 'US', 'USD', 'EQUITY', false),
    ('Deutsche Bank AG', 'DBK.DE', 'DBK', 'DBK', 'DBK.DE', 'DE', 'EUR', 'EQUITY', false),
    ('Dakota Gold Corp', 'DC.US', 'DC', 'DC', 'DC.US', 'US', 'USD', 'EQUITY', false),
    ('Datadog, Inc. Class A Common Stock', 'DDOG.US', 'DDOG', 'DDOG', 'DDOG.US', 'US', 'USD', 'EQUITY', false),
    ('VanEck Defense ETF', 'DFEN.DE', 'DFEN', 'DFEN', 'DFEN.DE', 'DE', 'EUR', 'ETF', false),
    ('VanEck Defense ETF', 'DFNS.UK', 'DFNS', 'DFNS', 'DFNS.L', 'UK', 'USD', 'ETF', false),
    ('DLocal Limited', 'DLO.US', 'DLO', 'DLO', 'DLO.US', 'US', 'USD', 'EQUITY', false),
    ('Digital Realty Trust, Inc.', 'DLR.US', 'DLR', 'DLR', 'DLR.US', 'US', 'USD', 'REIT', false),
    ('Dollar Tree, Inc.', 'DLTR.US', 'DLTR', 'DLTR', 'DLTR.US', 'US', 'USD', 'EQUITY', false),
    ('Dino Polska S.A.', 'DNP.PL', 'DNP', 'DNP', 'DNP.PL', 'PL', 'PLN', 'EQUITY', false),
    ('DOGECOIN', 'DOGECOIN', 'DOGECOIN', 'DOGECOIN', 'DOGECOIN', 'US', 'USD', 'CRYPTOCURRENCY', false),
    ('Domino''s Pizza Inc.', 'DPZ.US', 'DPZ', 'DPZ', 'DPZ.US', 'US', 'USD', 'EQUITY', false),
    ('iShares USD Treasury Bond 20+yr UCITS ETF USD (Acc)', 'DTLA.UK', 'DTLA', 'DTLA', 'DTLA.L', 'UK', 'USD', 'ETF', false),
    ('Duolingo, Inc.', 'DUOL.US', 'DUOL', 'DUOL', 'DUOL.US', 'US', 'USD', 'EQUITY', false),
    ('DexCom, Inc.', 'DXCM.US', 'DXCM', 'DXCM', 'DXCM.US', 'US', 'USD', 'EQUITY', false),
    ('iShares Core MSCI Emerging Markets IMI UCITS ETF (Acc)', 'EIMI.UK', 'EIMI', 'EIMI', 'EIMI.L', 'UK', 'USD', 'ETF', false),
    ('iShares Core MSCI Emerging Markets IMI UCITS ETF (Acc)', 'EMIM.UK', 'EMIM', 'EMIM', 'EMIM.L', 'UK', 'USD', 'ETF', false),
    ('Epam Systems Inc.', 'EPAM.US', 'EPAM', 'EPAM', 'EPAM.US', 'US', 'USD', 'EQUITY', false),
    ('Epsilon Energy Ltd.', 'EPSN.US', 'EPSN', 'EPSN', 'EPSN.US', 'US', 'USD', 'EQUITY', false),
    ('Equinix, Inc.', 'EQIX.US', 'EQIX', 'EQIX', 'EQIX.US', 'US', 'USD', 'REIT', false),
    ('Beta ETF Dywidenda Plus Portfelowy Fundusz Inwestycyjny Zamknięty', 'ETFBDIVPL.PL', 'ETFBDIVPL', 'ETFBDIVPL', 'ETFBDIVPL.PL', 'PL', 'PLN', 'ETF', false),
    ('Beta ETF MWIG40TR', 'ETFBM40TR.PL', 'ETFBM40TR', 'ETFBM40TR', 'ETFBM40TR.PL', 'PL', 'PLN', 'ETF', false),
    ('Beta ETF WIG20TR', 'ETFBW20TR.PL', 'ETFBW20TR', 'ETFBW20TR', 'ETFBW20TR.PL', 'PL', 'PLN', 'ETF', true),
    ('ETHEREUM', 'ETHEREUM', 'ETHEREUM', 'ETHEREUM', 'ETHEREUM', 'US', 'USD', 'CRYPTOCURRENCY', false),
    ('Ford Motor Company', 'F.US', 'F', 'F', 'F.US', 'US', 'USD', 'EQUITY', false),
    ('FactSet Research Systems Inc.', 'FDS.US', 'FDS', 'FDS', 'FDS.US', 'US', 'USD', 'EQUITY', false),
    ('Fidelity Global Quality Income UCITS ETF INC-USD', 'FGEQ.DE', 'FGEQ', 'FGEQ', 'FGEQ.DE', 'DE', 'EUR', 'ETF', false),
    ('Fair Isaac Corporation', 'FICO.US', 'FICO', 'FICO', 'FICO.US', 'US', 'USD', 'EQUITY', false),
    ('Flowers Foods, Inc.', 'FLO.US', 'FLO', 'FLO', 'FLO.US', 'US', 'USD', 'EQUITY', false),
    ('iShares China Large-Cap ETF', 'FXI.US', 'FXI', 'FXI', 'FXI.US', 'US', 'USD', 'ETF', false),
    ('Galiano Gold Inc.', 'GAU.US', 'GAU', 'GAU', 'GAU.US', 'US', 'USD', 'EQUITY', false),
    ('General Electric Company', 'GE.US', 'GE', 'GE', 'GE.US', 'US', 'USD', 'EQUITY', false),
    ('General Mills, Inc.', 'GIS.US', 'GIS', 'GIS', 'GIS.US', 'US', 'USD', 'EQUITY', false),
    ('Gamestop Corp.', 'GME.US', 'GME', 'GME', 'GME.US', 'US', 'USD', 'EQUITY', false),
    ('GOOGC', 'GOOGC.US', 'GOOGC', 'GOOGC', 'GOOGC.US', 'US', 'USD', 'EQUITY', false),
    ('Alphabet Inc.', 'GOOGL.US', 'GOOGL', 'GOOGL', 'GOOGL.US', 'US', 'USD', 'EQUITY', true),
    ('Genuine Parts Company', 'GPC.US', 'GPC', 'GPC', 'GPC.US', 'US', 'USD', 'EQUITY', false),
    ('GitLab Inc.', 'GTLB.US', 'GTLB', 'GTLB', 'GTLB.US', 'US', 'USD', 'EQUITY', false),
    ('HSBC FTSE EPRA NAREIT Developed UCITS ETF', 'H4ZL.DE', 'H4ZL', 'H4ZL', 'H4ZL.DE', 'DE', 'EUR', 'ETF', false),
    ('Halliburton Company', 'HAL.US', 'HAL', 'HAL', 'HAL.US', 'US', 'USD', 'EQUITY', false),
    ('The Home Depot, Inc.', 'HD.US', 'HD', 'HD', 'HD.US', 'US', 'USD', 'EQUITY', false),
    ('HabibMetro Bank Ltd.', 'HMB.SE', 'HMB', 'HMB', 'HMB.SE', 'SE', 'USD', 'EQUITY', false),
    ('Thales S.A.', 'HO.FR', 'HO', 'HO', 'HO.FR', 'FR', 'EUR', 'EQUITY', false),
    ('Honeywell International Inc.', 'HON.US', 'HON', 'HON', 'HON.US', 'US', 'USD', 'EQUITY', false),
    ('Robinhood Markets, Inc.', 'HOOD.US', 'HOOD', 'HOOD', 'HOOD.US', 'US', 'USD', 'EQUITY', false),
    ('HSBC FTSE EPRA NAREIT Developed UCITS ETF', 'HPRD.UK', 'HPRD', 'HPRD', 'HPRD.L', 'UK', 'USD', 'ETF', false),
    ('Host Hotels & Resorts Inc.', 'HST.US', 'HST', 'HST', 'HST.US', 'US', 'USD', 'EQUITY', false),
    ('Hertz Global Holdings Inc.', 'HTZ1.US', 'HTZ1', 'HTZ1', 'HTZ1.US', 'US', 'USD', 'EQUITY', false),
    ('iShares Gold Producers UCITS ETF USD (Acc)', 'IAUP.UK', 'IAUP', 'IAUP', 'IAUP.L', 'UK', 'USD', 'ETF', false),
    ('iShares Core MSCI EM IMI UCITS ETF USD (Dist)', 'IBC3.DE', 'IBC3', 'IBC3', 'IBC3.DE', 'DE', 'EUR', 'ETF', false),
    ('International Business Machines Corporation', 'IBM.US', 'IBM', 'IBM', 'IBM.US', 'US', 'USD', 'EQUITY', false),
    ('iShares Core S&P 500 UCITS ETF USD (Dist)', 'IDUS.UK', 'IDUS', 'IDUS', 'IDUS.L', 'UK', 'USD', 'ETF', false),
    ('iShares Physical Gold ETC', 'IGLN.UK', 'IGLN', 'IGLN', 'IGLN.L', 'UK', 'USD', 'ETF', false),
    ('ING Bank Slaski S.A.', 'ING.PL', 'ING', 'ING', 'ING.PL', 'PL', 'PLN', 'EQUITY', false),
    ('Intel Corporation', 'INTC.US', 'INTC', 'INTC', 'INTC.US', 'US', 'USD', 'EQUITY', false),
    ('iShares Oil & Gas Exploration & Production UCITS ETF', 'IOGP.UK', 'IOGP', 'IOGP', 'IOGP.L', 'UK', 'USD', 'ETF', false),
    ('IonQ, Inc.', 'IONQ.US', 'IONQ', 'IONQ', 'IONQ.US', 'US', 'USD', 'EQUITY', false),
    ('iShares Physical Silver ETC', 'ISLN.UK', 'ISLN', 'ISLN', 'ISLN.L', 'UK', 'USD', 'ETF', false),
    ('Intuitive Surgical, Inc.', 'ISRG.US', 'ISRG', 'ISRG', 'ISRG.US', 'US', 'USD', 'EQUITY', false),
    ('iShares S&P 500 Consumer Staples Sector UCITS ETF', 'IUCS.UK', 'IUCS', 'IUCS', 'IUCS.L', 'UK', 'USD', 'ETF', false),
    ('iShares Edge MSCI USA Value Factor UCITS ETF', 'IUVL.UK', 'IUVL', 'IUVL', 'IUVL.L', 'UK', 'USD', 'ETF', true),
    ('Invesco Mortgage Capital Inc.', 'IVR.US', 'IVR', 'IVR', 'IVR.US', 'US', 'USD', 'EQUITY', false),
    ('JPMorgan Equity Premium Income ETF', 'JEPG.UK', 'JEPG', 'JEPG', 'JEPG.L', 'UK', 'USD', 'ETF', false),
    ('JPMorgan Equity Premium Income ETF', 'JGPI.DE', 'JGPI', 'JGPI', 'JGPI.DE', 'DE', 'EUR', 'ETF', true),
    ('Johnson & Johnson', 'JNJ.US', 'JNJ', 'JNJ', 'JNJ.US', 'US', 'USD', 'EQUITY', false),
    ('JPMorgan Chase & Co.', 'JPM.US', 'JPM', 'JPM', 'JPM.US', 'US', 'USD', 'EQUITY', false),
    ('Keurig Dr Pepper Inc.', 'KDP.US', 'KDP', 'KDP', 'KDP.US', 'US', 'USD', 'EQUITY', false),
    ('KGHM Polska Miedź S.A.', 'KGH.PL', 'KGH', 'KGH', 'KGH.PL', 'PL', 'PLN', 'EQUITY', false),
    ('Kinder Morgan, Inc.', 'KMI.US', 'KMI', 'KMI', 'KMI.US', 'US', 'USD', 'EQUITY', false),
    ('The Coca-Cola Company', 'KO.US', 'KO', 'KO', 'KO.US', 'US', 'USD', 'EQUITY', false),
    ('KRUK S.A.', 'KRU.PL', 'KRU', 'KRU', 'KRU.PL', 'PL', 'PLN', 'EQUITY', false),
    ('Leonardo S.p.A.', 'LDO.IT', 'LDO', 'LDO', 'LDO.IT', 'IT', 'EUR', 'EQUITY', false),
    ('Deutsche Lufthansa AG', 'LHA.DE', 'LHA', 'LHA', 'LHA.DE', 'DE', 'EUR', 'EQUITY', false),
    ('Eli Lilly and Company', 'LLY.US', 'LLY', 'LLY', 'LLY.US', 'US', 'USD', 'EQUITY', false),
    ('Lockheed Martin Corporation', 'LMT.US', 'LMT', 'LMT', 'LMT.US', 'US', 'USD', 'EQUITY', false),
    ('Lowe''s Companies Inc.', 'LOW.US', 'LOW', 'LOW', 'LOW.US', 'US', 'USD', 'EQUITY', false),
    ('LPP S.A.', 'LPP.PL', 'LPP', 'LPP', 'LPP.PL', 'PL', 'PLN', 'EQUITY', false),
    ('iShares $ Corp Bond UCITS ETF USD (Acc)', 'LQDA.UK', 'LQDA', 'LQDA', 'LQDA.L', 'UK', 'USD', 'ETF', false),
    ('LiveOne, Inc.', 'LVO.US', 'LVO', 'LVO', 'LVO.US', 'US', 'USD', 'EQUITY', false),
    ('Las Vegas Sands Corp.', 'LVS.US', 'LVS', 'LVS', 'LVS.US', 'US', 'USD', 'EQUITY', false),
    ('LyondellBasell Industries N.V.', 'LYB.US', 'LYB', 'LYB', 'LYB.US', 'US', 'USD', 'EQUITY', true),
    ('Mastercard Incorporated', 'MA.US', 'MA', 'MA', 'MA.US', 'US', 'USD', 'EQUITY', false),
    ('Main Street Capital Corporation', 'MAIN.US', 'MAIN', 'MAIN', 'MAIN.US', 'US', 'USD', 'EQUITY', false),
    ('ManpowerGroup Inc.', 'MAN.US', 'MAN', 'MAN', 'MAN.US', 'US', 'USD', 'EQUITY', false),
    ('McDonald''s Corporation', 'MCD.US', 'MCD', 'MCD', 'MCD.US', 'US', 'USD', 'EQUITY', false),
    ('MercadoLibre, Inc.', 'MELI.US', 'MELI', 'MELI', 'MELI.US', 'US', 'USD', 'EQUITY', false),
    ('Meta Platforms Inc Class A', 'META.US', 'META', 'META', 'META.US', 'US', 'USD', 'EQUITY', true),
    ('3M Company', 'MMM.US', 'MMM', 'MMM', 'MMM.US', 'US', 'USD', 'EQUITY', false),
    ('Altria Group, Inc.', 'MO.US', 'MO', 'MO', 'MO.US', 'US', 'USD', 'EQUITY', true),
    ('MOGA', 'MOGA.US', 'MOGA', 'MOGA', 'MOGA.US', 'US', 'USD', 'EQUITY', false),
    ('Molina Healthcare Inc.', 'MOH.US', 'MOH', 'MOH', 'MOH.US', 'US', 'USD', 'EQUITY', false),
    ('MP Materials Corp.', 'MP.US', 'MP', 'MP', 'MP.US', 'US', 'USD', 'EQUITY', false),
    ('Merck & Co., Inc.', 'MRK.US', 'MRK', 'MRK', 'MRK.US', 'US', 'USD', 'EQUITY', false),
    ('Marvell Technology, Inc.', 'MRVL.US', 'MRVL', 'MRVL', 'MRVL.US', 'US', 'USD', 'EQUITY', true),
    ('Microsoft Corp.', 'MSF.DE', 'MSF', 'MSF', 'MSF.DE', 'DE', 'EUR', 'EQUITY', false),
    ('Microsoft Corp.', 'MSFT.US', 'MSFT', 'MSFT', 'MSFT.US', 'US', 'USD', 'EQUITY', true),
    ('Micron Technology, Inc.', 'MU.US', 'MU', 'MU', 'MU.US', 'US', 'USD', 'EQUITY', true),
    ('NATGAS', 'NATGAS', 'NATGAS', 'NATGAS', 'NATGAS', 'US', 'USD', 'COMMODITY', false),
    ('WisdomTree Uranium and Nuclear Energy UCITS ETF USD Acc', 'NCLR.UK', 'NCLR', 'NCLR', 'NCLR.L', 'UK', 'USD', 'ETF', false),
    ('Aurubis AG', 'NDA.FI', 'NDA', 'NDA', 'NDA.FI', 'FI', 'EUR', 'EQUITY', false),
    ('NextEra Energy, Inc.', 'NEE.US', 'NEE', 'NEE', 'NEE.US', 'US', 'USD', 'EQUITY', false),
    ('Newmont Corporation', 'NEM.US', 'NEM', 'NEM', 'NEM.US', 'US', 'USD', 'EQUITY', true),
    ('Cloudflare Inc.', 'NET.US', 'NET', 'NET', 'NET.US', 'US', 'USD', 'EQUITY', false),
    ('Netflix, Inc.', 'NFLX.US', 'NFLX', 'NFLX', 'NFLX.US', 'US', 'USD', 'EQUITY', true),
    ('Nice Ltd. Sponsored ADR', 'NICE.US', 'NICE', 'NICE', 'NICE.US', 'US', 'USD', 'EQUITY', false),
    ('Nio Inc.', 'NIO.US', 'NIO', 'NIO', 'NIO.US', 'US', 'USD', 'EQUITY', false),
    ('Nike, Inc. - Class B', 'NKE.US', 'NKE', 'NKE', 'NKE.US', 'US', 'USD', 'EQUITY', false),
    ('Nektar Therapeutics Inc.', 'NKTR.US', 'NKTR', 'NKTR', 'NKTR.US', 'US', 'USD', 'EQUITY', false),
    ('NNN REIT, Inc.', 'NNN.US', 'NNN', 'NNN', 'NNN.US', 'US', 'USD', 'EQUITY', false),
    ('Novo Nordisk A/S', 'NOV.DE', 'NOV', 'NOV', 'NOV.DE', 'DE', 'EUR', 'EQUITY', false),
    ('NOVOB', 'NOVOB.DK', 'NOVOB', 'NOVOB', 'NOVOB.DK', 'DK', 'USD', 'EQUITY', false),
    ('ServiceNow, Inc.', 'NOW.US', 'NOW', 'NOW', 'NOW.US', 'US', 'USD', 'EQUITY', false),
    ('NRG Energy, Inc.', 'NRG.US', 'NRG', 'NRG', 'NRG.US', 'US', 'USD', 'EQUITY', false),
    ('Nu Holdings Ltd.', 'NU.US', 'NU', 'NU', 'NU.US', 'US', 'USD', 'EQUITY', false),
    ('VanEck Uranium and Nuclear Technologies UCITS ETF', 'NUCL.UK', 'NUCL', 'NUCL', 'NUCL.L', 'UK', 'USD', 'ETF', false),
    ('NVIDIA Corporation', 'NVD.DE', 'NVD', 'NVD', 'NVD.DE', 'DE', 'EUR', 'EQUITY', false),
    ('NVIDIA Corporation', 'NVDA.US', 'NVDA', 'NVDA', 'NVDA.US', 'US', 'USD', 'EQUITY', true),
    ('Novo Nordisk A/S Sponsored ADR', 'NVO.US', 'NVO', 'NVO', 'NVO.US', 'US', 'USD', 'EQUITY', false),
    ('Newag S.A.', 'NWG.PL', 'NWG', 'NWG', 'NWG.PL', 'PL', 'PLN', 'EQUITY', false),
    ('Realty Income Corporation', 'O.US', 'O', 'O', 'O.US', 'US', 'USD', 'EQUITY', false),
    ('Blue Owl Capital Corporation', 'OBDC.US', 'OBDC', 'OBDC', 'OBDC.US', 'US', 'USD', 'EQUITY', false),
    ('Onyx Spot Return Crude Oil', 'OIL.UK', 'OIL', 'OIL', 'OIL.L', 'UK', 'USD', 'ETF', false),
    ('ONEOK, Inc.', 'OKE.US', 'OKE', 'OKE', 'OKE.US', 'US', 'USD', 'EQUITY', false),
    ('Okta Inc.', 'OKTA.US', 'OKTA', 'OKTA', 'OKTA.US', 'US', 'USD', 'EQUITY', false),
    ('OneMain Holdings Inc.', 'OMF.US', 'OMF', 'OMF', 'OMF.US', 'US', 'USD', 'EQUITY', true),
    ('Oracle Corporation', 'ORCL.US', 'ORCL', 'ORCL', 'ORCL.US', 'US', 'USD', 'EQUITY', true),
    ('Oscar Health, Inc.', 'OSCR.US', 'OSCR', 'OSCR', 'OSCR.US', 'US', 'USD', 'EQUITY', false),
    ('Oshkosh Corporation', 'OSK.US', 'OSK', 'OSK', 'OSK.US', 'US', 'USD', 'EQUITY', true),
    ('Occidental Petroleum Corporation', 'OXY.US', 'OXY', 'OXY', 'OXY.US', 'US', 'USD', 'EQUITY', false),
    ('abrdn Physical Palladium Shares ETF', 'PALL.US', 'PALL', 'PALL', 'PALL.US', 'US', 'USD', 'ETF', false),
    ('Palo Alto Networks, Inc.', 'PANW.US', 'PANW', 'PANW', 'PANW.US', 'US', 'USD', 'EQUITY', false),
    ('PDD Holdings Inc. American Depositary Shares', 'PDD.US', 'PDD', 'PDD', 'PDD.US', 'US', 'USD', 'EQUITY', false),
    ('Bank Polska Kasa Opieki S.A.', 'PEO.PL', 'PEO', 'PEO', 'PEO.PL', 'PL', 'PLN', 'EQUITY', false),
    ('PepsiCo, Inc.', 'PEP.US', 'PEP', 'PEP', 'PEP.US', 'US', 'USD', 'EQUITY', false),
    ('Pfizer Inc.', 'PFE.US', 'PFE', 'PFE', 'PFE.US', 'US', 'USD', 'EQUITY', false),
    ('Procter & Gamble Co', 'PG.US', 'PG', 'PG', 'PG.US', 'US', 'USD', 'EQUITY', false),
    ('PGE Polska Grupa Energetyczna S.A.', 'PGE.PL', 'PGE', 'PGE', 'PGE.PL', 'PL', 'PLN', 'EQUITY', false),
    ('ORLEN S.A.', 'PKN.PL', 'PKN', 'PKN', 'PKN.PL', 'PL', 'PLN', 'EQUITY', false),
    ('PKO Bank Polski S.A.', 'PKO.PL', 'PKO', 'PKO', 'PKO.PL', 'PL', 'PLN', 'EQUITY', false),
    ('Prologis Inc.', 'PLD.US', 'PLD', 'PLD', 'PLD.US', 'US', 'USD', 'EQUITY', false),
    ('Palantir Technologies Inc.', 'PLTR.US', 'PLTR', 'PLTR', 'PLTR.US', 'US', 'USD', 'EQUITY', false),
    ('PlayWay S.A.', 'PLW.PL', 'PLW', 'PLW', 'PLW.PL', 'PL', 'PLN', 'EQUITY', false),
    ('Philip Morris International Inc.', 'PM.US', 'PM', 'PM', 'PM.US', 'US', 'USD', 'EQUITY', false),
    ('Power Solutions International, Inc.', 'PSIX.US', 'PSIX', 'PSIX', 'PSIX.US', 'US', 'USD', 'EQUITY', true),
    ('ProShares Short QQQ', 'PSQ.US', 'PSQ', 'PSQ', 'PSQ.US', 'US', 'USD', 'ETF', false),
    ('PayPal Holdings Inc', 'PYPL.US', 'PYPL', 'PYPL', 'PYPL.US', 'US', 'USD', 'EQUITY', false),
    ('Powszechny Zakład Ubezpieczeń Spółka Akcyjna', 'PZU.PL', 'PZU', 'PZU', 'PZU.PL', 'PL', 'PLN', 'EQUITY', false),
    ('D-Wave Quantum Inc.', 'QBTS.US', 'QBTS', 'QBTS', 'QBTS.US', 'US', 'USD', 'EQUITY', false),
    ('QUALCOMM Incorporated', 'QCOM.US', 'QCOM', 'QCOM', 'QCOM.US', 'US', 'USD', 'EQUITY', false),
    ('VanEck Quantum Computing UCITS ETF', 'QUTM.DE', 'QUTM', 'QUTM', 'QUTM.DE', 'DE', 'EUR', 'ETF', false),
    ('Ferrari N.V.', 'RACE.US', 'RACE', 'RACE', 'RACE.US', 'US', 'USD', 'EQUITY', false),
    ('Roblox Corporation', 'RBLX.US', 'RBLX', 'RBLX', 'RBLX.US', 'US', 'USD', 'EQUITY', true),
    ('Red Cat Holdings, Inc.', 'RCAT.US', 'RCAT', 'RCAT', 'RCAT.US', 'US', 'USD', 'EQUITY', true),
    ('VanEck Rare Earth and Strategic Metals UCITS ETF', 'REMX.UK', 'REMX', 'REMX', 'REMX.L', 'UK', 'USD', 'ETF', false),
    ('Riley Exploration Permian, Inc.', 'REPX.US', 'REPX', 'REPX', 'REPX.US', 'US', 'USD', 'EQUITY', false),
    ('Rigetti Computing Inc', 'RGTI.US', 'RGTI', 'RGTI', 'RGTI.US', 'US', 'USD', 'EQUITY', false),
    ('Rheinmetall AG', 'RHM.DE', 'RHM', 'RHM', 'RHM.DE', 'DE', 'EUR', 'EQUITY', false),
    ('RTX Corporation', 'RTX.US', 'RTX', 'RTX', 'RTX.US', 'US', 'USD', 'EQUITY', false),
    ('SAABB', 'SAABB.SE', 'SAABB', 'SAABB', 'SAABB.SE', 'SE', 'USD', 'EQUITY', false),
    ('Safran SA', 'SAF.FR', 'SAF', 'SAF', 'SAF.FR', 'FR', 'EUR', 'EQUITY', false),
    ('Sharplink Gaming Inc.', 'SBET.US', 'SBET', 'SBET', 'SBET.US', 'US', 'USD', 'EQUITY', true),
    ('Starbucks Corporation', 'SBUX.US', 'SBUX', 'SBUX', 'SBUX.US', 'US', 'USD', 'EQUITY', false),
    ('Scanway S.A.', 'SCW.PL', 'SCW', 'SCW', 'SCW.PL', 'PL', 'PLN', 'EQUITY', false),
    ('SandRidge Energy Inc.', 'SD.US', 'SD', 'SD', 'SD.US', 'US', 'USD', 'EQUITY', false),
    ('Invesco Physical Gold ETC', 'SGLD.UK', 'SGLD', 'SGLD', 'SGLD.L', 'UK', 'USD', 'ETF', false),
    ('ProShares Short S&P500', 'SH.US', 'SH', 'SH', 'SH.US', 'US', 'USD', 'ETF', false),
    ('The J.M. Smucker Company', 'SJM.US', 'SJM', 'SJM', 'SJM.US', 'US', 'USD', 'EQUITY', false),
    ('Samsung Electronics Co., Ltd. Global Depositary Receipt (Reg S)', 'SMSN.UK', 'SMSN', 'SMSN', 'SMSN.L', 'UK', 'USD', 'EQUITY', false),
    ('SanDisk Corporation', 'SNDK.US', 'SNDK', 'SNDK', 'SNDK.US', 'US', 'USD', 'EQUITY', true),
    ('Snowflake Inc.', 'SNOW.US', 'SNOW', 'SNOW', 'SNOW.US', 'US', 'USD', 'EQUITY', false),
    ('SoFi Technologies, Inc.', 'SOFI.US', 'SOFI', 'SOFI', 'SOFI.US', 'US', 'USD', 'EQUITY', false),
    ('Space Exploration Technologies Corp. Class A', 'SPCX.US', 'SPCX', 'SPCX', 'SPCX.US', 'US', 'USD', 'EQUITY', true),
    ('SPDR Portfolio S&P 500 ETF', 'SPLG.US', 'SPLG', 'SPLG', 'SPLG.US', 'US', 'USD', 'ETF', false),
    ('Spotify Technology S.A.', 'SPOT.US', 'SPOT', 'SPOT', 'SPOT.US', 'US', 'USD', 'EQUITY', false),
    ('SPDR Dow Jones Global Real Estate UCITS ETF (Acc)', 'SPY2.DE', 'SPY2', 'SPY2', 'SPY2.DE', 'DE', 'EUR', 'ETF', false),
    ('SPDR S&P Euro Dividend Aristocrats UCITS ETF (Dist)', 'SPYW.DE', 'SPYW', 'SPYW', 'SPYW.DE', 'DE', 'EUR', 'ETF', false),
    ('Seagate Technology Holdings plc', 'STX.US', 'STX', 'STX', 'STX.US', 'US', 'USD', 'EQUITY', true),
    ('iShares Nikkei 225 UCITS ETF (Acc)', 'SXRZ.DE', 'SXRZ', 'SXRZ', 'SXRZ.DE', 'DE', 'EUR', 'ETF', false),
    ('Synaptics Incorporated', 'SYNA.US', 'SYNA', 'SYNA', 'SYNA.US', 'US', 'USD', 'EQUITY', false),
    ('AT&T Inc.', 'T.US', 'T', 'T', 'T.US', 'US', 'USD', 'EQUITY', true),
    ('Ten Square Games S.A.', 'TEN.PL', 'TEN', 'TEN', 'TEN.PL', 'PL', 'PLN', 'EQUITY', false),
    ('Target Corporation', 'TGT.US', 'TGT', 'TGT', 'TGT.US', 'US', 'USD', 'EQUITY', false),
    ('Hanover Insurance Group, Inc.', 'THG.US', 'THG', 'THG', 'THG.US', 'US', 'USD', 'EQUITY', false),
    ('UP Fintech Holding Ltd', 'TIGR.US', 'TIGR', 'TIGR', 'TIGR.US', 'US', 'USD', 'EQUITY', false),
    ('Tauron Polska Energia S.A.', 'TPE.PL', 'TPE', 'TPE', 'TPE.PL', 'PL', 'PLN', 'EQUITY', false),
    ('Trinity Capital Inc.', 'TRIN.US', 'TRIN', 'TRIN', 'TRIN.US', 'US', 'USD', 'EQUITY', true),
    ('T. Rowe Price Group, Inc.', 'TROW.US', 'TROW', 'TROW', 'TROW.US', 'US', 'USD', 'EQUITY', false),
    ('Tesla, Inc.', 'TSLA.US', 'TSLA', 'TSLA', 'TSLA.US', 'US', 'USD', 'EQUITY', false),
    ('Taiwan Semiconductor Manufacturing Co. Ltd. ADR', 'TSM.US', 'TSM', 'TSM', 'TSM.US', 'US', 'USD', 'EQUITY', true),
    ('The Trade Desk, Inc. Class A', 'TTD.US', 'TTD', 'TTD', 'TTD.US', 'US', 'USD', 'EQUITY', false),
    ('Texas Instruments Inc', 'TXN.US', 'TXN', 'TXN', 'TXN.US', 'US', 'USD', 'EQUITY', false),
    ('Text S.A.', 'TXT.PL', 'TXT', 'TXT', 'TXT.PL', 'PL', 'PLN', 'EQUITY', false),
    ('Sprott Junior Uranium Miners UCITS ETF USD Accumulating', 'U8NJ.DE', 'U8NJ', 'U8NJ', 'U8NJ.DE', 'DE', 'EUR', 'ETF', false),
    ('Uber Technologies, Inc.', 'UBER.US', 'UBER', 'UBER', 'UBER.US', 'US', 'USD', 'EQUITY', true),
    ('UnitedHealth Group Incorporated', 'UNH.US', 'UNH', 'UNH', 'UNH.US', 'US', 'USD', 'EQUITY', false),
    ('Union Pacific Corporation', 'UNP.US', 'UNP', 'UNP', 'UNP.US', 'US', 'USD', 'EQUITY', false),
    ('United Parcel Service, Inc.', 'UPS.US', 'UPS', 'UPS', 'UPS.US', 'US', 'USD', 'EQUITY', false),
    ('Global X Uranium ETF', 'URA.US', 'URA', 'URA', 'URA.US', 'US', 'USD', 'ETF', false),
    ('Visa Inc. Class A', 'V.US', 'V', 'V', 'V.US', 'US', 'USD', 'EQUITY', false),
    ('Vanguard FTSE All-World High Dividend Yield UCITS ETF (USD) Distributing', 'VGWD.DE', 'VGWD', 'VGWD', 'VGWD.DE', 'DE', 'EUR', 'ETF', false),
    ('Vanguard FTSE All-World High Dividend Yield UCITS ETF (USD) Distributing', 'VHYL.UK', 'VHYL', 'VHYL', 'VHYL.L', 'UK', 'USD', 'ETF', false),
    ('Vanguard Funds Public Limited Company - Vanguard FTSE All-World High Dividend Yield UCITS ETF', 'VHYD.UK', 'VHYD', 'VHYD', 'VHYD.L', 'UK', 'USD', 'ETF', true),
    ('Vici Properties Inc.', 'VICI.US', 'VICI', 'VICI', 'VICI.US', 'US', 'USD', 'EQUITY', true),
    ('VIX', 'VIX', 'VIX', 'VIX', 'VIX', 'US', 'USD', 'INDEX', false),
    ('Valero Energy Corporation', 'VLO.US', 'VLO', 'VLO', 'VLO.US', 'US', 'USD', 'EQUITY', false),
    ('VOLCARB', 'VOLCARB.SE', 'VOLCARB', 'VOLCARB', 'VOLCARB.SE', 'SE', 'USD', 'EQUITY', false),
    ('VOW1', 'VOW1.DE', 'VOW1', 'VOW1', 'VOW1.DE', 'DE', 'EUR', 'EQUITY', false),
    ('Vertiv Holdings Co', 'VRT.US', 'VRT', 'VRT', 'VRT.US', 'US', 'USD', 'EQUITY', true),
    ('Vanguard FTSE All-World UCITS ETF (USD) Accumulating', 'VWCE.DE', 'VWCE', 'VWCE', 'VWCE.DE', 'DE', 'EUR', 'ETF', false),
    ('Vanguard FTSE All-World UCITS ETF (USD) Accumulating', 'VWRA.UK', 'VWRA', 'VWRA', 'VWRA.L', 'UK', 'USD', 'ETF', true),
    ('Vanguard FTSE All-World UCITS ETF (USD) Distributing', 'VWRD.UK', 'VWRD', 'VWRD', 'VWRD.L', 'UK', 'USD', 'ETF', false),
    ('Vanguard FTSE All-World UCITS ETF (USD) Distributing', 'VWRL.NL', 'VWRL', 'VWRL', 'VWRL.NL', 'NL', 'EUR', 'ETF', false),
    ('Verizon Communications Inc.', 'VZ.US', 'VZ', 'VZ', 'VZ.US', 'US', 'USD', 'EQUITY', false),
    ('Warner Bros. Discovery, Inc.', 'WBD.US', 'WBD', 'WBD', 'WBD.US', 'US', 'USD', 'EQUITY', false),
    ('Waste Connections Inc.', 'WCN.US', 'WCN', 'WCN', 'WCN.US', 'US', 'USD', 'EQUITY', false),
    ('Western Digital Corporation', 'WDC.US', 'WDC', 'WDC', 'WDC.US', 'US', 'USD', 'EQUITY', true),
    ('Waste Management, Inc.', 'WM.US', 'WM', 'WM', 'WM.US', 'US', 'USD', 'EQUITY', false),
    ('Walmart Inc.', 'WMT.US', 'WMT', 'WMT', 'WMT.US', 'US', 'USD', 'EQUITY', false),
    ('Western Union Co.', 'WU.US', 'WU', 'WU', 'WU.US', 'US', 'USD', 'EQUITY', false),
    ('Wilh. Wilhelmsen Holding ASA', 'WWI.NO', 'WWI', 'WWI', 'WWI.NO', 'NO', 'USD', 'EQUITY', false),
    ('Wynn Resorts, Limited', 'WYNN.US', 'WYNN', 'WYNN', 'WYNN.US', 'US', 'USD', 'EQUITY', false),
    ('Xtrackers MSCI World UCITS ETF 1D', 'XDWL.DE', 'XDWL', 'XDWL', 'XDWL.DE', 'DE', 'EUR', 'ETF', false),
    ('Xtrackers MSCI World Information Technology UCITS ETF 1C', 'XDWT.DE', 'XDWT', 'XDWT', 'XDWT.DE', 'DE', 'EUR', 'ETF', false),
    ('Consumer Staples Select Sector SPDR Fund', 'XLP.US', 'XLP', 'XLP', 'XLP.US', 'US', 'USD', 'ETF', false),
    ('ExxonMobil Holdings Corporation', 'XOM.US', 'XOM', 'XOM', 'XOM.US', 'US', 'USD', 'EQUITY', false),
    ('Xpeng Inc.', 'XPEV.US', 'XPEV', 'XPEV', 'XPEV.US', 'US', 'USD', 'EQUITY', false),
    ('X-Trade Brokers Dom Maklerski S.A.', 'XTB.PL', 'XTB', 'XTB', 'XTB.PL', 'PL', 'PLN', 'EQUITY', false),
    ('Haleon Plc - ADR', 'HLN.US', 'HLN', 'HLN', 'HLN.US','US', 'USD', 'EQUITY', false),
    ('Intuit Inc.','INTU.US', 'INTU', 'INTU', 'INTU.US','US', 'USD', 'EQUITY', false),
    ('iShares Physical Palladium ETC','IPDM.UK', 'IPDM', 'IPDM', 'IPDM.L','UK', 'USD', 'ETF', false),
    ('iShares Physical Platinum ETC',     'IPLT.UK', 'IPLT', 'IPLT', 'IPLT.L','UK', 'USD', 'ETF', false),
    ('iShares Russell 2000 ETF','IWM.US', 'IWM', 'IWM', 'IWM.US','US', 'USD', 'ETF', false),
    ('iShares MSCI India UCITS ETF USD (Acc)','NDIA.UK', 'NDIA', 'NDIA', 'NDIA.L','UK', 'USD', 'ETF', false),
    ('VanEck Semiconductor UCITS ETF','SMH.UK', 'SMH', 'SMH', 'SMH.L','UK', 'USD', 'ETF', false),
    ('United States Treasury 4 5/8 02/28/26','T458022826.US', 'T458022826', 'T458022826', 'T458022826.US','US', 'USD', 'BOND', false),
    ('W. P. Carey Inc.','WPC.US', 'WPC', 'WPC', 'WPC.US','US', 'USD', 'EQUITY', false)
ON CONFLICT (symbol) DO UPDATE SET
    name = EXCLUDED.name,
    ticker = EXCLUDED.ticker,
    ibkr = EXCLUDED.ibkr,
    yahoo = EXCLUDED.yahoo,
    country = EXCLUDED.country,
    currency = EXCLUDED.currency,
    asset_type = EXCLUDED.asset_type,
    active = EXCLUDED.active;

UPDATE investory.assets
SET isin = 'US91282CKB62'
WHERE symbol = 'T458022826.US';

UPDATE investory.assets
SET exclude_from_import = true
WHERE symbol IN (
    '3CP.DE', 'ABB.SE', 'ADC.US', 'ADM.US', 'AFG.US', 'AGGG.UK', 'AIGI.UK',
    'ALE.PL', 'AMD.DE', 'AMT.US', 'APP.US', 'ARM.US', 'AVY.US', 'BA.US',
    'BHRB.US', 'CB.US', 'CCJ.US', 'CCC.PL', 'CHD.US', 'CPRT.US', 'CRDO.US',
    'DC.US', 'DDOG.US', 'DLO.US', 'DLR.US', 'DOGECOIN', 'DUOL.US', 'EIMI.UK',
    'EMIM.UK', 'EPAM.US', 'EPSN.US', 'EQIX.US', 'ETFBDIVPL.PL', 'FDS.US',
    'FICO.US', 'FLO.US', 'GE.US', 'GIS.US', 'GOOGC.US', 'GTLB.US', 'H4ZL.DE',
    'HAL.US', 'HMB.SE', 'HON.US', 'HTZ1.US', 'IBC3.DE', 'IGLN.UK', 'IOGP.UK',
    'ISRG.US', 'IUCS.UK', 'KDP.US', 'KRU.PL', 'LMT.US', 'LOW.US', 'LQDA.UK',
    'LVO.US', 'LVS.US', 'MOGA.US', 'NDA.FI', 'NEE.US', 'NET.US', 'NIO.US',
    'NKE.US', 'NKTR.US', 'OKTA.US', 'OSCR.US', 'PDD.US', 'PEO.PL', 'PLD.US',
    'PM.US', 'QCOM.US', 'QUTM.DE', 'RACE.US', 'REPX.US', 'RTX.US', 'SBET.US',
    'SJM.US', 'SMSN.UK', 'SNOW.US', 'SOFI.US', 'SPY2.DE', 'SPLG.US', 'SPYW.DE',
    'TEN.PL', 'THG.US', 'TIGR.US', 'TTD.US', 'VOLCARB.SE', 'VGWD.DE', 'WBD.US',
    'WCN.US', 'WM.US', 'WPC.US', 'WU.US', 'WWI.NO', 'WYNN.US', 'XLP.US',
    'XPEV.US', 'AHLA.DE', 'IAUP.UK', 'ETHEREUM'
);
