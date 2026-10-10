-- Final anonymized sample portfolio and public FX seed data.
-- Consolidated final schema; historical repair steps are not replayed.

SET search_path TO investory, public;

INSERT INTO investory.app_users (id, username, display_name, birth_date, active, created_at, updated_at, password_hash, role, email, google_subject) VALUES (1, 'sample.user', 'Sample User', '1985-09-09', true, '2026-10-10 18:11:46.823514+00', '2026-10-10 18:11:47.864536+00', NULL, 'PROFILE_OWNER', NULL, NULL);

INSERT INTO investory.app_users (id, username, display_name, birth_date, active, created_at, updated_at, password_hash, role, email, google_subject) VALUES (2, 'happy.investor', 'Happy Investor', '1984-01-01', true, '2026-10-10 18:11:46.82435+00', '2026-10-10 18:11:47.864536+00', NULL, 'PROFILE_OWNER', NULL, NULL);

INSERT INTO investory.portfolios (id, name, base_currency, local_currency, owner, user_id, created_at) VALUES (1, 'Sample Portfolio', 'USD', 'PLN', 'Sample User', 1, '2026-10-10 18:11:46.825115+00');

INSERT INTO investory.portfolios (id, name, base_currency, local_currency, owner, user_id, created_at) VALUES (2, 'Happy Investor Portfolio', 'PLN', 'PLN', 'Happy Investor', 2, '2026-10-10 18:11:46.825681+00');

INSERT INTO investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only, created_at) VALUES (90000003, '90000003', 'PLN', 'XTB', 'Sample PLN Account', 'Sample User', 1, false, '2026-10-10 18:11:46.826319+00');

INSERT INTO investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only, created_at) VALUES (90000004, '90000004', 'USD', 'XTB', 'Sample USD Account', 'Sample User', 1, false, '2026-10-10 18:11:46.826319+00');

INSERT INTO investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only, created_at) VALUES (90000005, '90000005', 'EUR', 'XTB', 'Sample EUR Account', 'Sample User', 1, true, '2026-10-10 18:11:46.826319+00');

INSERT INTO investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only, created_at) VALUES (90000006, '90000006', 'USD', 'XTB', 'Sample Metals Account', 'Sample User', 1, false, '2026-10-10 18:11:46.826319+00');

INSERT INTO investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only, created_at) VALUES (90000007, '90000007', 'PLN', 'XTB', 'Sample Retirement Account', 'Sample User', 1, false, '2026-10-10 18:11:46.826319+00');

INSERT INTO investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only, created_at) VALUES (90000008, '90000008', 'PLN', 'XTB', 'Sample PLN Cash Account', 'Sample User', 1, true, '2026-10-10 18:11:46.826319+00');

INSERT INTO investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only, created_at) VALUES (90000002, '90000002', 'USD', 'XTB', 'Sample USD Trading Account', 'Sample User', 1, false, '2026-10-10 18:11:46.826319+00');

INSERT INTO investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only, created_at) VALUES (90000009, '90000009', 'EUR', 'XTB', 'Sample EUR Cash Account', 'Sample User', 1, true, '2026-10-10 18:11:46.826319+00');

INSERT INTO investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only, created_at) VALUES (90000010, '90000010', 'USD', 'XTB', 'Sample Income Account', 'Sample User', 1, false, '2026-10-10 18:11:46.826319+00');

INSERT INTO investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only, created_at) VALUES (90000011, '90000011', 'PLN', 'XTB', 'Sample PLN Reserve Account', 'Sample User', 1, true, '2026-10-10 18:11:46.826319+00');

INSERT INTO investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only, created_at) VALUES (90000001, '90000001', 'USD', 'IBKR', 'Sample IBKR Account', 'Sample User', 1, false, '2026-10-10 18:11:46.826319+00');

INSERT INTO investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only, created_at) VALUES (91000003, '90000003', 'PLN', 'XTB', 'XTB PLN investment account', 'Happy Investor', 2, false, '2026-10-10 18:11:46.826319+00');

INSERT INTO investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only, created_at) VALUES (91000005, '90000004', 'USD', 'XTB', 'XTB USD reserve account', 'Happy Investor', 2, false, '2026-10-10 18:11:46.826319+00');

INSERT INTO investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only, created_at) VALUES (91000006, '90000005', 'EUR', 'XTB', 'XTB EUR cash account', 'Happy Investor', 2, true, '2026-10-10 18:11:46.826319+00');

INSERT INTO investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only, created_at) VALUES (91000007, '90000006', 'USD', 'XTB', 'XTB metals account', 'Happy Investor', 2, false, '2026-10-10 18:11:46.826319+00');

INSERT INTO investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only, created_at) VALUES (91000008, '90000007', 'PLN', 'XTB', 'XTB retirement account', 'Happy Investor', 2, false, '2026-10-10 18:11:46.826319+00');

INSERT INTO investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only, created_at) VALUES (91000009, '90000008', 'PLN', 'XTB', 'XTB cash account', 'Happy Investor', 2, true, '2026-10-10 18:11:46.826319+00');

INSERT INTO investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only, created_at) VALUES (91000002, '90000002', 'USD', 'XTB', 'XTB USD investment account', 'Happy Investor', 2, false, '2026-10-10 18:11:46.826319+00');

INSERT INTO investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only, created_at) VALUES (91000004, '90000009', 'EUR', 'XTB', 'XTB EUR reserve account', 'Happy Investor', 2, true, '2026-10-10 18:11:46.826319+00');

INSERT INTO investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only, created_at) VALUES (91000010, '90000010', 'USD', 'XTB', 'XTB income account', 'Happy Investor', 2, false, '2026-10-10 18:11:46.826319+00');

INSERT INTO investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only, created_at) VALUES (91000011, '90000011', 'PLN', 'XTB', 'XTB PLN reserve account', 'Happy Investor', 2, true, '2026-10-10 18:11:46.826319+00');

INSERT INTO investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only, created_at) VALUES (91000001, '90000001', 'USD', 'IBKR', 'IBKR USD investment account', 'Happy Investor', 2, false, '2026-10-10 18:11:46.826319+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (1, '2024-07-31', 'EUR', 'USD', 1.08223900, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2024-07-31', NULL, 'V01.003:DB60:EUR:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (2, '2024-07-31', 'EUR', 'PLN', 4.29529837, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2024-07-31', NULL, 'V01.003:DB60:EUR:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (3, '2024-07-31', 'USD', 'PLN', 3.96890000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2024-07-31', NULL, 'V01.003:DB60:USD:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (4, '2024-07-31', 'PLN', 'USD', 0.25195898, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2024-07-31', NULL, 'V01.003:DB60:PLN:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (5, '2025-03-01', 'EUR', 'USD', 1.03955700, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-03-01', NULL, 'V01.003:DB60:EUR:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (6, '2025-03-01', 'EUR', 'PLN', 4.20964008, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-03-01', NULL, 'V01.003:DB60:EUR:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (7, '2025-03-01', 'USD', 'PLN', 3.99930000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-03-01', NULL, 'V01.003:DB60:USD:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (8, '2025-03-01', 'PLN', 'USD', 0.25099000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-03-01', NULL, 'V01.003:DB60:PLN:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (9, '2025-04-01', 'EUR', 'USD', 1.08270600, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-04-01', NULL, 'V01.003:DB60:EUR:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (10, '2025-04-01', 'EUR', 'PLN', 4.20964008, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-04-01', NULL, 'V01.003:DB60:EUR:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (11, '2025-04-01', 'USD', 'PLN', 3.86430000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-04-01', NULL, 'V01.003:DB60:USD:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (12, '2025-04-01', 'PLN', 'USD', 0.25860800, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-04-01', NULL, 'V01.003:DB60:PLN:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (13, '2025-05-01', 'EUR', 'USD', 1.13719900, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-05-01', NULL, 'V01.003:DB60:EUR:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (14, '2025-05-01', 'EUR', 'PLN', 4.20964008, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-05-01', NULL, 'V01.003:DB60:EUR:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (15, '2025-05-01', 'USD', 'PLN', 3.76170000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-05-01', NULL, 'V01.003:DB60:USD:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (16, '2025-05-01', 'PLN', 'USD', 0.25860800, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-05-01', NULL, 'V01.003:DB60:PLN:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (17, '2025-06-01', 'EUR', 'USD', 1.13240300, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-06-01', NULL, 'V01.003:DB60:EUR:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (18, '2025-06-01', 'EUR', 'PLN', 4.22199000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-06-01', NULL, 'V01.003:DB60:EUR:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (19, '2025-06-01', 'USD', 'PLN', 3.75370000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-06-01', NULL, 'V01.003:DB60:USD:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (20, '2025-06-01', 'PLN', 'USD', 0.25860800, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-06-01', NULL, 'V01.003:DB60:PLN:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (21, '2025-07-01', 'EUR', 'USD', 1.17296200, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-07-01', NULL, 'V01.003:DB60:EUR:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (22, '2025-07-01', 'EUR', 'PLN', 4.25373100, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-07-01', NULL, 'V01.003:DB60:EUR:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (23, '2025-07-01', 'USD', 'PLN', 3.61640000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-07-01', NULL, 'V01.003:DB60:USD:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (24, '2025-07-01', 'PLN', 'USD', 0.25860800, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-07-01', NULL, 'V01.003:DB60:PLN:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (25, '2025-08-01', 'EUR', 'USD', 1.14504700, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-08-01', NULL, 'V01.003:DB60:EUR:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (26, '2025-08-01', 'EUR', 'PLN', 4.25373100, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-08-01', NULL, 'V01.003:DB60:EUR:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (27, '2025-08-01', 'USD', 'PLN', 3.72570000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-08-01', NULL, 'V01.003:DB60:USD:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (28, '2025-08-01', 'PLN', 'USD', 0.25860800, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-08-01', NULL, 'V01.003:DB60:PLN:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (29, '2025-09-01', 'EUR', 'USD', 1.16753700, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-09-01', NULL, 'V01.003:DB60:EUR:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (30, '2025-09-01', 'EUR', 'PLN', 4.25373100, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-09-01', NULL, 'V01.003:DB60:EUR:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (31, '2025-09-01', 'USD', 'PLN', 3.65590000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-09-01', NULL, 'V01.003:DB60:USD:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (32, '2025-09-01', 'PLN', 'USD', 0.25860800, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-09-01', NULL, 'V01.003:DB60:PLN:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (33, '2025-10-01', 'EUR', 'USD', 1.17560200, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-10-01', NULL, 'V01.003:DB60:EUR:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (34, '2025-10-01', 'EUR', 'PLN', 4.25373100, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-10-01', NULL, 'V01.003:DB60:EUR:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (35, '2025-10-01', 'USD', 'PLN', 3.63150000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-10-01', NULL, 'V01.003:DB60:USD:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (36, '2025-10-01', 'PLN', 'USD', 0.25860800, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-10-01', NULL, 'V01.003:DB60:PLN:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (37, '2025-11-01', 'EUR', 'USD', 1.15760100, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-11-01', NULL, 'V01.003:DB60:EUR:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (38, '2025-11-01', 'EUR', 'PLN', 4.25373100, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-11-01', NULL, 'V01.003:DB60:EUR:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (39, '2025-11-01', 'USD', 'PLN', 3.67510000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-11-01', NULL, 'V01.003:DB60:USD:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (40, '2025-11-01', 'PLN', 'USD', 0.25860800, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-11-01', NULL, 'V01.003:DB60:PLN:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (41, '2025-12-01', 'EUR', 'USD', 1.15686400, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-12-01', NULL, 'V01.003:DB60:EUR:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (42, '2025-12-01', 'EUR', 'PLN', 4.25373100, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-12-01', NULL, 'V01.003:DB60:EUR:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (43, '2025-12-01', 'USD', 'PLN', 3.66240000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-12-01', NULL, 'V01.003:DB60:USD:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (44, '2025-12-01', 'PLN', 'USD', 0.25860800, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-12-01', NULL, 'V01.003:DB60:PLN:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (45, '2025-12-31', 'EUR', 'USD', 1.17356200, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-12-31', NULL, 'V01.003:DB60:EUR:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (46, '2025-12-31', 'EUR', 'PLN', 4.22670090, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-12-31', NULL, 'V01.003:DB60:EUR:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (47, '2025-12-31', 'USD', 'PLN', 3.60160000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-12-31', NULL, 'V01.003:DB60:USD:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (48, '2025-12-31', 'PLN', 'USD', 0.27765434, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-12-31', NULL, 'V01.003:DB60:PLN:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (49, '2026-01-01', 'EUR', 'USD', 1.17356200, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-01-01', NULL, 'V01.003:DB60:EUR:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (50, '2026-01-01', 'EUR', 'PLN', 4.25373100, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-01-01', NULL, 'V01.003:DB60:EUR:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (51, '2026-01-01', 'USD', 'PLN', 3.60160000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-01-01', NULL, 'V01.003:DB60:USD:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (52, '2026-01-01', 'PLN', 'USD', 0.27703500, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-01-01', NULL, 'V01.003:DB60:PLN:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (53, '2026-02-01', 'EUR', 'USD', 1.19084800, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-02-01', NULL, 'V01.003:DB60:EUR:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (54, '2026-02-01', 'EUR', 'PLN', 4.18726300, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-02-01', NULL, 'V01.003:DB60:EUR:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (55, '2026-02-01', 'USD', 'PLN', 3.53790000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-02-01', NULL, 'V01.003:DB60:USD:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (56, '2026-02-01', 'PLN', 'USD', 0.27633400, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-02-01', NULL, 'V01.003:DB60:PLN:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (57, '2026-03-01', 'EUR', 'USD', 1.17956100, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-03-01', NULL, 'V01.003:DB60:EUR:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (58, '2026-03-01', 'EUR', 'PLN', 4.18726300, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-03-01', NULL, 'V01.003:DB60:EUR:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (59, '2026-03-01', 'USD', 'PLN', 3.58040000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-03-01', NULL, 'V01.003:DB60:USD:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (60, '2026-03-01', 'PLN', 'USD', 0.27633400, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-03-01', NULL, 'V01.003:DB60:PLN:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (61, '2026-04-01', 'EUR', 'USD', 1.14665300, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-04-01', NULL, 'V01.003:DB60:EUR:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (62, '2026-04-01', 'EUR', 'PLN', 4.25313400, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-04-01', NULL, 'V01.003:DB60:EUR:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (63, '2026-04-01', 'USD', 'PLN', 3.74080000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-04-01', NULL, 'V01.003:DB60:USD:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (64, '2026-04-01', 'PLN', 'USD', 0.26849000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-04-01', NULL, 'V01.003:DB60:PLN:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (65, '2026-05-01', 'EUR', 'USD', 1.16810200, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-05-01', NULL, 'V01.003:DB60:EUR:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (66, '2026-05-01', 'EUR', 'PLN', 4.25313400, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-05-01', NULL, 'V01.003:DB60:EUR:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (67, '2026-05-01', 'USD', 'PLN', 3.64600000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-05-01', NULL, 'V01.003:DB60:USD:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (68, '2026-05-01', 'PLN', 'USD', 0.26849000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-05-01', NULL, 'V01.003:DB60:PLN:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (69, '2026-06-01', 'EUR', 'USD', 1.16285200, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-06-01', NULL, 'V01.003:DB60:EUR:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (70, '2026-06-01', 'EUR', 'PLN', 4.25313400, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-06-01', NULL, 'V01.003:DB60:EUR:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (71, '2026-06-01', 'USD', 'PLN', 3.63950000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-06-01', NULL, 'V01.003:DB60:USD:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (72, '2026-06-01', 'PLN', 'USD', 0.26849000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-06-01', NULL, 'V01.003:DB60:PLN:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (73, '2026-07-01', 'EUR', 'USD', 1.13936000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-07-01', NULL, 'V01.003:DB60:EUR:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (74, '2026-07-01', 'EUR', 'PLN', 4.25313400, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-07-01', NULL, 'V01.003:DB60:EUR:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (75, '2026-07-01', 'USD', 'PLN', 3.77080000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-07-01', NULL, 'V01.003:DB60:USD:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (76, '2026-07-01', 'PLN', 'USD', 0.26849000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-07-01', NULL, 'V01.003:DB60:PLN:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (77, '2026-08-01', 'EUR', 'USD', 1.15238500, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-08-01', NULL, 'V01.003:DB60:EUR:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (78, '2026-08-01', 'EUR', 'PLN', 4.25313400, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-08-01', NULL, 'V01.003:DB60:EUR:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (79, '2026-08-01', 'USD', 'PLN', 3.74250000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-08-01', NULL, 'V01.003:DB60:USD:PLN', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (80, '2026-08-01', 'PLN', 'USD', 0.26849000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-08-01', NULL, 'V01.003:DB60:PLN:USD', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (81, '2024-07-31', 'USD', 'EUR', 0.92401032, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2024-07-31', NULL, 'V01.003:DB60:USD:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (82, '2025-03-01', 'USD', 'EUR', 0.96194821, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-03-01', NULL, 'V01.003:DB60:USD:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (83, '2025-04-01', 'USD', 'EUR', 0.92361177, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-04-01', NULL, 'V01.003:DB60:USD:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (84, '2025-05-01', 'USD', 'EUR', 0.87935357, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-05-01', NULL, 'V01.003:DB60:USD:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (85, '2025-06-01', 'USD', 'EUR', 0.88307784, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-06-01', NULL, 'V01.003:DB60:USD:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (86, '2025-07-01', 'USD', 'EUR', 0.85254254, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-07-01', NULL, 'V01.003:DB60:USD:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (87, '2025-08-01', 'USD', 'EUR', 0.87332660, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-08-01', NULL, 'V01.003:DB60:USD:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (88, '2025-09-01', 'USD', 'EUR', 0.85650391, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-09-01', NULL, 'V01.003:DB60:USD:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (89, '2025-10-01', 'USD', 'EUR', 0.85062802, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-10-01', NULL, 'V01.003:DB60:USD:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (90, '2025-11-01', 'USD', 'EUR', 0.86385551, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-11-01', NULL, 'V01.003:DB60:USD:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (91, '2025-12-01', 'USD', 'EUR', 0.86440584, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-12-01', NULL, 'V01.003:DB60:USD:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (92, '2025-12-31', 'USD', 'EUR', 0.85210666, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-12-31', NULL, 'V01.003:DB60:USD:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (93, '2026-01-01', 'USD', 'EUR', 0.85210666, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-01-01', NULL, 'V01.003:DB60:USD:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (94, '2026-02-01', 'USD', 'EUR', 0.83973773, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-02-01', NULL, 'V01.003:DB60:USD:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (95, '2026-03-01', 'USD', 'EUR', 0.84777303, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-03-01', NULL, 'V01.003:DB60:USD:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (96, '2026-04-01', 'USD', 'EUR', 0.87210342, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-04-01', NULL, 'V01.003:DB60:USD:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (97, '2026-05-01', 'USD', 'EUR', 0.85608962, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-05-01', NULL, 'V01.003:DB60:USD:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (98, '2026-06-01', 'USD', 'EUR', 0.85995466, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-06-01', NULL, 'V01.003:DB60:USD:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (99, '2026-07-01', 'USD', 'EUR', 0.87768572, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-07-01', NULL, 'V01.003:DB60:USD:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (100, '2026-08-01', 'USD', 'EUR', 0.86776555, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-08-01', NULL, 'V01.003:DB60:USD:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (101, '2024-07-31', 'PLN', 'EUR', 0.23281270, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2024-07-31', NULL, 'V01.003:DB60:PLN:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (102, '2025-03-01', 'PLN', 'EUR', 0.23755000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-03-01', NULL, 'V01.003:DB60:PLN:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (103, '2025-04-01', 'PLN', 'EUR', 0.23755000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-04-01', NULL, 'V01.003:DB60:PLN:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (104, '2025-05-01', 'PLN', 'EUR', 0.23755000, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-05-01', NULL, 'V01.003:DB60:PLN:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (105, '2025-06-01', 'PLN', 'EUR', 0.23685513, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-06-01', NULL, 'V01.003:DB60:PLN:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (106, '2025-07-01', 'PLN', 'EUR', 0.23508774, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-07-01', NULL, 'V01.003:DB60:PLN:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (107, '2025-08-01', 'PLN', 'EUR', 0.23508774, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-08-01', NULL, 'V01.003:DB60:PLN:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (108, '2025-09-01', 'PLN', 'EUR', 0.23508774, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-09-01', NULL, 'V01.003:DB60:PLN:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (109, '2025-10-01', 'PLN', 'EUR', 0.23508774, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-10-01', NULL, 'V01.003:DB60:PLN:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (110, '2025-11-01', 'PLN', 'EUR', 0.23508774, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-11-01', NULL, 'V01.003:DB60:PLN:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (111, '2025-12-01', 'PLN', 'EUR', 0.23508774, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-12-01', NULL, 'V01.003:DB60:PLN:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (112, '2025-12-31', 'PLN', 'EUR', 0.23659114, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2025-12-31', NULL, 'V01.003:DB60:PLN:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (113, '2026-01-01', 'PLN', 'EUR', 0.23508774, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-01-01', NULL, 'V01.003:DB60:PLN:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (114, '2026-02-01', 'PLN', 'EUR', 0.23881949, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-02-01', NULL, 'V01.003:DB60:PLN:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (115, '2026-03-01', 'PLN', 'EUR', 0.23881949, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-03-01', NULL, 'V01.003:DB60:PLN:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (116, '2026-04-01', 'PLN', 'EUR', 0.23512074, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-04-01', NULL, 'V01.003:DB60:PLN:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (117, '2026-05-01', 'PLN', 'EUR', 0.23512074, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-05-01', NULL, 'V01.003:DB60:PLN:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (118, '2026-06-01', 'PLN', 'EUR', 0.23512074, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-06-01', NULL, 'V01.003:DB60:PLN:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (119, '2026-07-01', 'PLN', 'EUR', 0.23512074, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-07-01', NULL, 'V01.003:DB60:PLN:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.exchange_rates (id, rate_date, base, to_currency, rate, purpose, source, method, source_rate_date, observed_at, source_reference, imported_at) VALUES (120, '2026-08-01', 'PLN', 'EUR', 0.23512074, 'VALUATION', 'DB60_INITIAL', 'OBSERVED', '2026-08-01', NULL, 'V01.003:DB60:PLN:EUR', '2026-10-10 18:11:46.827407+00');

INSERT INTO investory.assets (id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, isin, figi, exchange_mic, active, exclude_from_import, market_price, market_price_usd, price_source, price_updated_at) VALUES (1, 'Apple Inc.', 'AAPL.US', 'AAPL', 'AAPL', NULL, 'US', 'USD', 'EQUITY', NULL, NULL, NULL, true, false, NULL, NULL, NULL, NULL);

INSERT INTO investory.assets (id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, isin, figi, exchange_mic, active, exclude_from_import, market_price, market_price_usd, price_source, price_updated_at) VALUES (51, 'Allegro.eu S.A.', 'ALE.PL', 'ALE', 'ALE', NULL, 'PL', 'PLN', 'EQUITY', NULL, NULL, NULL, false, true, NULL, NULL, NULL, NULL);

INSERT INTO investory.assets (id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, isin, figi, exchange_mic, active, exclude_from_import, market_price, market_price_usd, price_source, price_updated_at) VALUES (101, 'Amazon.com, Inc.', 'AMZN.US', 'AMZN', 'AMZN', NULL, 'US', 'USD', 'EQUITY', NULL, NULL, NULL, true, false, NULL, NULL, NULL, NULL);

INSERT INTO investory.assets (id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, isin, figi, exchange_mic, active, exclude_from_import, market_price, market_price_usd, price_source, price_updated_at) VALUES (151, 'iShares Core MSCI Emerging Markets IMI UCITS ETF (Acc)', 'EMIM.UK', 'EMIM', 'EMIM', 'EMIM.L', 'UK', 'USD', 'ETF', NULL, NULL, NULL, false, false, NULL, NULL, NULL, NULL);

INSERT INTO investory.assets (id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, isin, figi, exchange_mic, active, exclude_from_import, market_price, market_price_usd, price_source, price_updated_at) VALUES (201, 'Beta ETF WIG20TR', 'ETFBW20TR.PL', 'ETFBW20TR', 'ETFBW20TR', NULL, 'PL', 'PLN', 'ETF', NULL, NULL, NULL, true, false, NULL, NULL, NULL, NULL);

INSERT INTO investory.assets (id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, isin, figi, exchange_mic, active, exclude_from_import, market_price, market_price_usd, price_source, price_updated_at) VALUES (251, 'Alphabet Inc.', 'GOOGL.US', 'GOOGL', 'GOOGL', NULL, 'US', 'USD', 'EQUITY', NULL, NULL, NULL, true, false, NULL, NULL, NULL, NULL);

INSERT INTO investory.assets (id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, isin, figi, exchange_mic, active, exclude_from_import, market_price, market_price_usd, price_source, price_updated_at) VALUES (301, 'HSBC FTSE EPRA NAREIT Developed UCITS ETF', 'HPRD.UK', 'HPRD', 'HPRD', 'HPRD.L', 'UK', 'USD', 'ETF', NULL, NULL, NULL, false, false, NULL, NULL, NULL, NULL);

INSERT INTO investory.assets (id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, isin, figi, exchange_mic, active, exclude_from_import, market_price, market_price_usd, price_source, price_updated_at) VALUES (351, 'JPMorgan Equity Premium Income ETF', 'JGPI.DE', 'JGPI', 'JGPI', 'JGPI.DE', 'DE', 'EUR', 'ETF', NULL, NULL, NULL, true, false, NULL, NULL, NULL, NULL);

INSERT INTO investory.assets (id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, isin, figi, exchange_mic, active, exclude_from_import, market_price, market_price_usd, price_source, price_updated_at) VALUES (401, 'Meta Platforms Inc Class A', 'META.US', 'META', 'META', NULL, 'US', 'USD', 'EQUITY', NULL, NULL, NULL, true, false, NULL, NULL, NULL, NULL);

INSERT INTO investory.assets (id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, isin, figi, exchange_mic, active, exclude_from_import, market_price, market_price_usd, price_source, price_updated_at) VALUES (451, 'Microsoft Corp.', 'MSFT.US', 'MSFT', 'MSFT', NULL, 'US', 'USD', 'EQUITY', NULL, NULL, NULL, true, false, NULL, NULL, NULL, NULL);

INSERT INTO investory.assets (id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, isin, figi, exchange_mic, active, exclude_from_import, market_price, market_price_usd, price_source, price_updated_at) VALUES (501, 'NATGAS', 'NATGAS', 'NATGAS', 'NATGAS', NULL, 'US', 'USD', 'COMMODITY', NULL, NULL, NULL, false, false, NULL, NULL, NULL, NULL);

INSERT INTO investory.assets (id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, isin, figi, exchange_mic, active, exclude_from_import, market_price, market_price_usd, price_source, price_updated_at) VALUES (551, 'WisdomTree Uranium and Nuclear Energy UCITS ETF USD Acc', 'NCLR.UK', 'NCLR', 'NCLR', 'NCLR.L', 'UK', 'USD', 'ETF', NULL, NULL, NULL, false, false, NULL, NULL, NULL, NULL);

INSERT INTO investory.assets (id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, isin, figi, exchange_mic, active, exclude_from_import, market_price, market_price_usd, price_source, price_updated_at) VALUES (601, 'VanEck Uranium and Nuclear Technologies UCITS ETF', 'NUCL.UK', 'NUCL', 'NUCL', 'NUCL.L', 'UK', 'USD', 'ETF', NULL, NULL, NULL, false, false, NULL, NULL, NULL, NULL);

INSERT INTO investory.assets (id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, isin, figi, exchange_mic, active, exclude_from_import, market_price, market_price_usd, price_source, price_updated_at) VALUES (651, 'NVIDIA Corporation', 'NVDA.US', 'NVDA', 'NVDA', NULL, 'US', 'USD', 'EQUITY', NULL, NULL, NULL, true, false, NULL, NULL, NULL, NULL);

INSERT INTO investory.assets (id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, isin, figi, exchange_mic, active, exclude_from_import, market_price, market_price_usd, price_source, price_updated_at) VALUES (701, 'Realty Income Corporation', 'O.US', 'O', 'O', NULL, 'US', 'USD', 'EQUITY', NULL, NULL, NULL, false, false, NULL, NULL, NULL, NULL);

INSERT INTO investory.assets (id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, isin, figi, exchange_mic, active, exclude_from_import, market_price, market_price_usd, price_source, price_updated_at) VALUES (751, 'abrdn Physical Palladium Shares ETF', 'PALL.US', 'PALL', 'PALL', NULL, 'US', 'USD', 'ETF', NULL, NULL, NULL, false, false, NULL, NULL, NULL, NULL);

INSERT INTO investory.assets (id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, isin, figi, exchange_mic, active, exclude_from_import, market_price, market_price_usd, price_source, price_updated_at) VALUES (801, 'ORLEN S.A.', 'PKN.PL', 'PKN', 'PKN', NULL, 'PL', 'PLN', 'EQUITY', NULL, NULL, NULL, false, false, NULL, NULL, NULL, NULL);

INSERT INTO investory.assets (id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, isin, figi, exchange_mic, active, exclude_from_import, market_price, market_price_usd, price_source, price_updated_at) VALUES (851, 'PKO Bank Polski S.A.', 'PKO.PL', 'PKO', 'PKO', NULL, 'PL', 'PLN', 'EQUITY', NULL, NULL, NULL, false, false, NULL, NULL, NULL, NULL);

INSERT INTO investory.assets (id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, isin, figi, exchange_mic, active, exclude_from_import, market_price, market_price_usd, price_source, price_updated_at) VALUES (901, 'Powszechny Zakład Ubezpieczeń Spółka Akcyjna', 'PZU.PL', 'PZU', 'PZU', NULL, 'PL', 'PLN', 'EQUITY', NULL, NULL, NULL, false, false, NULL, NULL, NULL, NULL);

INSERT INTO investory.assets (id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, isin, figi, exchange_mic, active, exclude_from_import, market_price, market_price_usd, price_source, price_updated_at) VALUES (951, 'SPDR S&P Euro Dividend Aristocrats UCITS ETF (Dist)', 'SPYW.DE', 'SPYW', 'SPYW', NULL, 'DE', 'EUR', 'ETF', NULL, NULL, NULL, false, false, NULL, NULL, NULL, NULL);

INSERT INTO investory.assets (id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, isin, figi, exchange_mic, active, exclude_from_import, market_price, market_price_usd, price_source, price_updated_at) VALUES (1001, 'Tesla, Inc.', 'TSLA.US', 'TSLA', 'TSLA', NULL, 'US', 'USD', 'EQUITY', NULL, NULL, NULL, false, false, NULL, NULL, NULL, NULL);

INSERT INTO investory.assets (id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, isin, figi, exchange_mic, active, exclude_from_import, market_price, market_price_usd, price_source, price_updated_at) VALUES (1051, 'Vanguard FTSE All-World High Dividend Yield UCITS ETF (USD) Distributing', 'VHYL.UK', 'VHYL', 'VHYL', 'VHYL.L', 'UK', 'USD', 'ETF', NULL, NULL, NULL, false, false, NULL, NULL, NULL, NULL);

INSERT INTO investory.assets (id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, isin, figi, exchange_mic, active, exclude_from_import, market_price, market_price_usd, price_source, price_updated_at) VALUES (1101, 'Vanguard Funds Public Limited Company - Vanguard FTSE All-World High Dividend Yield UCITS ETF', 'VHYD.UK', 'VHYD', 'VHYD', 'VHYD.L', 'UK', 'USD', 'ETF', NULL, NULL, NULL, true, false, NULL, NULL, NULL, NULL);

INSERT INTO investory.assets (id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, isin, figi, exchange_mic, active, exclude_from_import, market_price, market_price_usd, price_source, price_updated_at) VALUES (1151, 'Vanguard FTSE All-World UCITS ETF (USD) Accumulating', 'VWRA.UK', 'VWRA', 'VWRA', 'VWRA.L', 'UK', 'USD', 'ETF', NULL, NULL, NULL, true, false, NULL, NULL, NULL, NULL);

INSERT INTO investory.assets (id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, isin, figi, exchange_mic, active, exclude_from_import, market_price, market_price_usd, price_source, price_updated_at) VALUES (1201, 'United States Treasury 4 5/8 02/28/26', 'US91282CKB62', 'US91282CKB62', 'T458022826', NULL, 'US', 'USD', 'BOND', 'US91282CKB62', NULL, NULL, false, false, NULL, NULL, NULL, NULL);

INSERT INTO investory.assets (id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type, isin, figi, exchange_mic, active, exclude_from_import, market_price, market_price_usd, price_source, price_updated_at) VALUES (1251, 'United States Treasury 4 3/8 07/31/33', 'US91282CRC72', 'US91282CRC72', 'T438073133', NULL, 'US', 'USD', 'BOND', 'US91282CRC72', NULL, NULL, true, false, NULL, NULL, NULL, NULL);

INSERT INTO investory.profile_memberships (user_id, profile_id, role, created_at) VALUES (1, 1, 'OWNER', '2026-10-10 18:11:47.863782+00');

INSERT INTO investory.profile_memberships (user_id, profile_id, role, created_at) VALUES (2, 2, 'OWNER', '2026-10-10 18:11:47.863782+00');
