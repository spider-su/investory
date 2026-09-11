SET search_path TO investory, public;

-- Happy Investor COMMON overlay: identity, whole-wealth (long-term) assets, tax, plan and
-- planning rows plus the pinned read-model price cache. This is broker-agnostic and is loaded for
-- every scenario, including the golden path (which then imports the broker layer from files).

INSERT INTO app_users (id, username, display_name, birth_date, active, role, updated_at)
VALUES (2, 'happy.investor', 'Happy Investor', DATE '1984-01-01', true, 'PROFILE_OWNER', now())
ON CONFLICT (id) DO UPDATE SET username = EXCLUDED.username,
    display_name = EXCLUDED.display_name, birth_date = EXCLUDED.birth_date,
    active = EXCLUDED.active, role = EXCLUDED.role, updated_at = EXCLUDED.updated_at;

SELECT setval(
    pg_get_serial_sequence('app_users', 'id'),
    (SELECT max(id) FROM app_users),
    true);

INSERT INTO portfolios (id, name, base_currency, local_currency, owner, user_id)
VALUES (2, 'Happy Investor Portfolio', 'PLN', 'PLN', 'Happy Investor', 2)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name,
    base_currency = EXCLUDED.base_currency, local_currency = EXCLUDED.local_currency,
    owner = EXCLUDED.owner, user_id = EXCLUDED.user_id;

INSERT INTO profile_memberships (user_id, profile_id, role)
VALUES (2, 2, 'OWNER')
ON CONFLICT (user_id, profile_id) DO UPDATE SET role = EXCLUDED.role;

INSERT INTO accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only)
VALUES
    (2017959259, '17959259', 'USD', 'IBKR', 'IBKR USD investment account', 'Happy Investor', 2, false),
    (2051499241, '51499241', 'USD', 'XTB', 'XTB USD investment account', 'Happy Investor', 2, false),
    (2051551301, '51551301', 'PLN', 'XTB', 'XTB PLN investment account', 'Happy Investor', 2, false),
    (2051548444, '51548444', 'EUR', 'XTB', 'XTB EUR cash-only account', 'Happy Investor', 2, true)
ON CONFLICT (id) DO UPDATE SET external_account_id = EXCLUDED.external_account_id,
    currency = EXCLUDED.currency, provider = EXCLUDED.provider, name = EXCLUDED.name,
    owner = EXCLUDED.owner, portfolio_id = EXCLUDED.portfolio_id, cash_only = EXCLUDED.cash_only;

INSERT INTO bond (id, portfolio_id, name, currency, value, acquisition_date, interest_rate,
                  maturity_date, archived_at, notes)
VALUES (9405, 2, 'Treasury 2026', 'PLN', 10000, DATE '2024-07-31', 0.04625,
        DATE '2026-02-28', NULL, 'Happy Investor canonical fixed income')
ON CONFLICT (id) DO UPDATE SET portfolio_id = EXCLUDED.portfolio_id, name = EXCLUDED.name,
    currency = EXCLUDED.currency, value = EXCLUDED.value, acquisition_date = EXCLUDED.acquisition_date,
    interest_rate = EXCLUDED.interest_rate, maturity_date = EXCLUDED.maturity_date,
    archived_at = EXCLUDED.archived_at, notes = EXCLUDED.notes;

-- The redeemed principal is reinvested on the next calendar day. The original row above is
-- retained as historical ownership and remains visible with zero income after maturity.
INSERT INTO bond (id, portfolio_id, name, currency, value, acquisition_date, interest_rate,
                  maturity_date, archived_at, notes)
VALUES (9407, 2, 'United States Treasury 4 3/8 07/31/33', 'PLN', 10000, DATE '2026-03-01', 0.04375,
        DATE '2033-07-31', NULL, 'Happy Investor reinvestment of Treasury 2026 principal')
ON CONFLICT (id) DO UPDATE SET portfolio_id = EXCLUDED.portfolio_id, name = EXCLUDED.name,
    currency = EXCLUDED.currency, value = EXCLUDED.value, acquisition_date = EXCLUDED.acquisition_date,
    interest_rate = EXCLUDED.interest_rate, maturity_date = EXCLUDED.maturity_date,
    archived_at = EXCLUDED.archived_at, notes = EXCLUDED.notes;

INSERT INTO cash_reserve (id, portfolio_id, name, currency, value, acquisition_date, interest_rate,
                          maturity_date, archived_at, notes)
VALUES (9406, 2, 'Term cash reserve', 'PLN', 25000, DATE '2024-08-01', 0.04,
        DATE '2027-08-01', NULL, 'Happy Investor interest-bearing cash reserve')
ON CONFLICT (id) DO UPDATE SET portfolio_id = EXCLUDED.portfolio_id, name = EXCLUDED.name,
    currency = EXCLUDED.currency, value = EXCLUDED.value, acquisition_date = EXCLUDED.acquisition_date,
    interest_rate = EXCLUDED.interest_rate, maturity_date = EXCLUDED.maturity_date,
    archived_at = EXCLUDED.archived_at, notes = EXCLUDED.notes;

INSERT INTO real_estate (id, portfolio_id, name, currency, value, tax_base, acquisition_date,
                         land_register_number, archived_at, notes)
VALUES
    (9402, 2, 'Apartment A', 'PLN', 400000, 3200, DATE '2024-08-01', 'KR1P/4322432/0', NULL, 'Happy Investor canonical profile'),
    (9403, 2, 'Apartment B', 'PLN', 500000, 3000, DATE '2024-08-01', NULL, NULL, 'Happy Investor canonical profile')
ON CONFLICT (id) DO UPDATE SET portfolio_id = EXCLUDED.portfolio_id, name = EXCLUDED.name,
    currency = EXCLUDED.currency, value = EXCLUDED.value, tax_base = EXCLUDED.tax_base, acquisition_date = EXCLUDED.acquisition_date,
    land_register_number = EXCLUDED.land_register_number, archived_at = EXCLUDED.archived_at,
    notes = EXCLUDED.notes;

INSERT INTO long_term_asset_history (asset_id, asset_type, complete)
VALUES (9402, 'REAL_ESTATE', true), (9403, 'REAL_ESTATE', true)
ON CONFLICT (asset_id) DO UPDATE SET asset_type = EXCLUDED.asset_type, complete = EXCLUDED.complete;

INSERT INTO cash_reserve (id, portfolio_id, name, currency, value, acquisition_date, archived_at, notes)
VALUES (9401, 2, 'Cash reserve', 'PLN', 25000, DATE '2024-08-01', NULL, 'Happy Investor canonical profile')
ON CONFLICT (id) DO UPDATE SET portfolio_id = EXCLUDED.portfolio_id, name = EXCLUDED.name,
    currency = EXCLUDED.currency, value = EXCLUDED.value, acquisition_date = EXCLUDED.acquisition_date,
    archived_at = EXCLUDED.archived_at, notes = EXCLUDED.notes;

INSERT INTO personal_asset (id, portfolio_id, name, category, currency, value, acquisition_date, archived_at, notes)
VALUES (9404, 2, 'Family Car', 'VEHICLE', 'PLN', 10000, DATE '2024-08-01', NULL, 'Happy Investor canonical profile')
ON CONFLICT (id) DO UPDATE SET portfolio_id = EXCLUDED.portfolio_id, name = EXCLUDED.name,
    category = EXCLUDED.category, currency = EXCLUDED.currency, value = EXCLUDED.value,
    acquisition_date = EXCLUDED.acquisition_date, archived_at = EXCLUDED.archived_at, notes = EXCLUDED.notes;

INSERT INTO rental_contract (id, real_estate_id, start_date, end_date, terminated_date, bootstrap_managed, notes)
VALUES
    (9501, 9402, DATE '2024-08-01', NULL, NULL, false, 'Happy Investor canonical profile'),
    (9502, 9403, DATE '2024-08-01', DATE '2025-06-30', NULL, false, 'Happy Investor canonical profile B1'),
    (9503, 9403, DATE '2025-07-01', NULL, NULL, false, 'Happy Investor canonical profile B2')
ON CONFLICT (id) DO UPDATE SET real_estate_id = EXCLUDED.real_estate_id, start_date = EXCLUDED.start_date,
    end_date = EXCLUDED.end_date, terminated_date = EXCLUDED.terminated_date,
    bootstrap_managed = EXCLUDED.bootstrap_managed, notes = EXCLUDED.notes;

INSERT INTO rental_contract_term (rental_contract_id, cash_flow_type, amount, frequency, paid_by_tenant)
VALUES
    (9501, 'RENT', 3200, 'MONTHLY', false),
    (9502, 'RENT', 2800, 'MONTHLY', false),
    (9503, 'RENT', 3000, 'MONTHLY', false)
ON CONFLICT (rental_contract_id, cash_flow_type) DO UPDATE SET amount = EXCLUDED.amount,
    frequency = EXCLUDED.frequency, paid_by_tenant = EXCLUDED.paid_by_tenant;

INSERT INTO retirement_plans (
    id, portfolio_id, name, birth_date, effective_year, end_age,
    retirement_age, annual_employment_income, annual_pre_retirement_contribution,
    annual_living_expenses, annual_discretionary_expenses, inflation_rate,
    rental_income_growth_rate, spending_growth_rate, funding_strategy,
    funding_order, safe_reserve_years, equity_harvest_minimum_return_rate,
    equity_gain_harvest_rate, allow_emergency_equity_withdrawal,
    fixed_income_return_rate, equity_return_rate, pension_start_age, annual_pension,
    capital_gain_tax_rate, archived, created_at, updated_at,
    baseline_as_of_year, baseline_reserve, baseline_investment_capital,
    baseline_long_term_capital, baseline_rental_income, baseline_long_term_income,
    baseline_long_term_state_version
)
VALUES
    (9201, 2, 'Happy Investor Plan', DATE '1984-01-01', 2024, 85, 60, 90000, 12000,
    36000, 6000, 0.025, 0.025, 0.035, 'SIMPLE_WATERFALL',
    'CASH,BONDS,STOCKS', 2, 0.05, 0.25, true,
    0.035, 0.07, 67, 24000, 0.19,
    false, TIMESTAMPTZ '2025-01-01 00:00:00+00', TIMESTAMPTZ '2025-01-01 00:00:00+00',
    2025, 50000, 159307.015664, 970000, 74400, 74400, 1
    )
ON CONFLICT (id) DO UPDATE
SET portfolio_id = EXCLUDED.portfolio_id,
    name = EXCLUDED.name,
    birth_date = EXCLUDED.birth_date,
    effective_year = EXCLUDED.effective_year,
    end_age = EXCLUDED.end_age,
    retirement_age = EXCLUDED.retirement_age,
    annual_employment_income = EXCLUDED.annual_employment_income,
    annual_pre_retirement_contribution = EXCLUDED.annual_pre_retirement_contribution,
    annual_living_expenses = EXCLUDED.annual_living_expenses,
    annual_discretionary_expenses = EXCLUDED.annual_discretionary_expenses,
    inflation_rate = EXCLUDED.inflation_rate,
    rental_income_growth_rate = EXCLUDED.rental_income_growth_rate,
    spending_growth_rate = EXCLUDED.spending_growth_rate,
    funding_strategy = EXCLUDED.funding_strategy,
    funding_order = EXCLUDED.funding_order,
    safe_reserve_years = EXCLUDED.safe_reserve_years,
    equity_harvest_minimum_return_rate = EXCLUDED.equity_harvest_minimum_return_rate,
    equity_gain_harvest_rate = EXCLUDED.equity_gain_harvest_rate,
    allow_emergency_equity_withdrawal = EXCLUDED.allow_emergency_equity_withdrawal,
    fixed_income_return_rate = EXCLUDED.fixed_income_return_rate,
    equity_return_rate = EXCLUDED.equity_return_rate,
    pension_start_age = EXCLUDED.pension_start_age,
    annual_pension = EXCLUDED.annual_pension,
    capital_gain_tax_rate = EXCLUDED.capital_gain_tax_rate,
    baseline_as_of_year = EXCLUDED.baseline_as_of_year,
    baseline_reserve = EXCLUDED.baseline_reserve,
    baseline_investment_capital = EXCLUDED.baseline_investment_capital,
    baseline_long_term_capital = EXCLUDED.baseline_long_term_capital,
    baseline_rental_income = EXCLUDED.baseline_rental_income,
    baseline_long_term_income = EXCLUDED.baseline_long_term_income,
    baseline_long_term_state = EXCLUDED.baseline_long_term_state,
    baseline_long_term_state_version = EXCLUDED.baseline_long_term_state_version,
    archived = EXCLUDED.archived,
    updated_at = EXCLUDED.updated_at;

INSERT INTO retirement_planning_years
    (id, portfolio_id, planning_year, status, state)
VALUES (
    9301,
    2,
    2025,
    'DRAFT',
    '{"values":{"ACTUAL":{"NET_WORTH":{"metric":"NET_WORTH","derivedValue":1179307.015664,"source":"PORTFOLIO_DERIVED","note":"Happy Investor canonical profile: investment baseline plus whole-wealth assets"},"CORE_SPENDING":{"metric":"CORE_SPENDING","approvedValue":36000,"source":"USER_ENTERED","note":"Happy Investor canonical profile"},"DISCRETIONARY_SPENDING":{"metric":"DISCRETIONARY_SPENDING","approvedValue":6000,"source":"USER_ENTERED","note":"Happy Investor canonical profile"}},"BASELINE":{}}}'::jsonb)
ON CONFLICT (id) DO UPDATE
SET portfolio_id = EXCLUDED.portfolio_id,
    planning_year = EXCLUDED.planning_year,
    status = EXCLUDED.status,
    state = EXCLUDED.state;

-- Current read-model cache is pinned to the last canonical close in the closed fixture.
-- Match by symbol: asset IDs are database-local and unrelated portfolios may have populated
-- the global asset catalog with different IDs or later prices.
UPDATE assets
SET market_price = CASE symbol
        WHEN 'AAPL.US' THEN 249.059
        WHEN 'VWRA.UK' THEN 139.340
        WHEN 'NVDA.US' THEN 134.290
        WHEN 'TSLA.US' THEN 403.840
        WHEN 'GOOGL.US' THEN 189.300
        WHEN 'MSFT.US' THEN 421.500
        -- Bond fallback quotes are normalized to currency amount per unit. The source quote is
        -- percent-of-par, so 1.00% and 98.81% become 0.01 and 0.9881 respectively.
        WHEN 'US91282CKB62' THEN 0.0100
        WHEN 'US91282CRC72' THEN 0.9881
    END,
    market_price_usd = CASE symbol
        WHEN 'AAPL.US' THEN 249.059
        WHEN 'VWRA.UK' THEN 139.340
        WHEN 'NVDA.US' THEN 134.290
        WHEN 'TSLA.US' THEN 403.840
        WHEN 'GOOGL.US' THEN 189.300
        WHEN 'MSFT.US' THEN 421.500
        WHEN 'US91282CKB62' THEN 0.0100
        WHEN 'US91282CRC72' THEN 0.9881
    END,
    price_source = 'STOOQ',
    price_updated_at = TIMESTAMPTZ '2025-01-01 12:00:00 Europe/Warsaw'
WHERE symbol IN (
    'AAPL.US', 'VWRA.UK', 'NVDA.US', 'TSLA.US', 'GOOGL.US', 'MSFT.US',
    'US91282CKB62', 'US91282CRC72');
