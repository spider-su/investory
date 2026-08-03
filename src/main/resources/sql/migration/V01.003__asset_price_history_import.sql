-- Generated historical asset price import.
-- Generated at: 2026-07-19T11:20:00+00:00
-- Stooq folder: src/main/resources/stooq
-- Covered period: 2024-07-02..2026-07-02
-- Mapping rows: 234
-- Price rows: 117812
-- Generator version: 2026-07-safe-matching-v2
-- Source precedence: exact Stooq observed > scaled/alternate observed > XTB trade observations > interpolated estimates.
-- This bulk load is kept in Flyway temporarily because the database is recreated from scratch.
-- It should eventually move into a dedicated bulk import process outside normal schema migration.

insert into investory.assets (name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, active) values
('Costco', 'COST.US', 'COST', 'COST', 'COST.US', 'US', 'USD', 'EQUITY', true),
('Walmart', 'WMT.US', 'WMT', 'WMT', 'WMT.US', 'US', 'USD', 'EQUITY', true),
('Cloudflare', 'NET.US', 'NET', 'NET', 'NET.US', 'US', 'USD', 'EQUITY', true),
('Vanguard FTSE All-World UCITS ETF', 'VWRA', 'VWRA', 'VWRA', 'VWRA', 'US', 'USD', 'ETF', true),
('Oshkosh', 'OSK.US', 'OSK', 'OSK', 'OSK.US', 'US', 'USD', 'EQUITY', true),
('iShares USD Treasury Bond 1-3yr UCITS ETF', 'IUVL', 'IUVL', 'IUVL', 'IUVL', 'US', 'USD', 'ETF', true),
('OMF', 'OMF.US', 'OMF', 'OMF', 'OMF.US', 'US', 'USD', 'EQUITY', true),
('MO', 'MO.US', 'MO', 'MO', 'MO.US', 'US', 'USD', 'EQUITY', true),
('Oracle', 'ORCL.US', 'ORCL', 'ORCL', 'ORCL.US', 'US', 'USD', 'EQUITY', true),
('Uber', 'UBER.US', 'UBER', 'UBER', 'UBER.US', 'US', 'USD', 'EQUITY', true),
('AGNC Investment Corp', 'AGNC.US', 'AGNC', 'AGNC', 'AGNC.US', 'US', 'USD', 'EQUITY', true),
('Applied Materials', 'AMAT.US', 'AMAT', 'AMAT', 'AMAT.US', 'US', 'USD', 'EQUITY', true),
('JPMorgan Global Premium Income UCITS ETF', 'JGPI', 'JGPI', 'JGPI', 'JGPI', 'US', 'USD', 'ETF', true),
('Alphabet', 'GOOGL.US', 'GOOGL', 'GOOGL', 'GOOGL.US', 'US', 'USD', 'EQUITY', true),
('Meta Platforms', 'META.US', 'META', 'META', 'META.US', 'US', 'USD', 'EQUITY', true),
('Newmont', 'NEM.US', 'NEM', 'NEM', 'NEM.US', 'US', 'USD', 'EQUITY', true),
('Amazon', 'AMZN.US', 'AMZN', 'AMZN', 'AMZN.US', 'US', 'USD', 'EQUITY', true),
('Nvidia', 'NVDA.US', 'NVDA', 'NVDA', 'NVDA.US', 'US', 'USD', 'EQUITY', true),
('Western Digital', 'WDC.US', 'WDC', 'WDC', 'WDC.US', 'US', 'USD', 'EQUITY', true),
('ASML', 'ASML.US', 'ASML', 'ASML', 'ASML.US', 'US', 'USD', 'EQUITY', true)
on conflict (symbol) do nothing;
