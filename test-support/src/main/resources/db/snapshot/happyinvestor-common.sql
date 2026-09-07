SET search_path TO investory, public;

-- Happy Investor COMMON overlay: identity, whole-wealth (long-term) assets, tax, plan and
-- planning rows plus the pinned read-model price cache. This is broker-agnostic and is loaded for
-- every scenario, including the golden path (which then imports the broker layer from files).

UPDATE app_users
SET display_name = 'Happy Investor'
WHERE id = 1;

UPDATE portfolios
SET name = 'Happy Investor Portfolio',
    base_currency = 'PLN',
    owner = 'Happy Investor'
WHERE id = 1;

UPDATE accounts
SET owner = 'Happy Investor',
    name = CASE id
      WHEN 17959259 THEN 'IBKR USD investment account'
      WHEN 51499241 THEN 'XTB USD investment account'
      WHEN 51551301 THEN 'XTB PLN investment account'
      WHEN 51548444 THEN 'XTB EUR cash-only account'
      ELSE name
    END
WHERE portfolio_id = 1
  AND id IN ('17959259', '51499241', '51551301', '51548444');

INSERT INTO bond (id, portfolio_id, name, currency, value, acquisition_date, interest_rate,
                  maturity_date, archived_at, notes)
VALUES (9405, 1, 'Treasury 2026', 'PLN', 10000, DATE '2024-07-31', 0.04625,
        DATE '2026-02-28', NULL, 'Happy Investor canonical fixed income')
ON CONFLICT (id) DO UPDATE SET portfolio_id = EXCLUDED.portfolio_id, name = EXCLUDED.name,
    currency = EXCLUDED.currency, value = EXCLUDED.value, acquisition_date = EXCLUDED.acquisition_date,
    interest_rate = EXCLUDED.interest_rate, maturity_date = EXCLUDED.maturity_date,
    archived_at = EXCLUDED.archived_at, notes = EXCLUDED.notes;

INSERT INTO cash_reserve (id, portfolio_id, name, currency, value, acquisition_date, interest_rate,
                          maturity_date, archived_at, notes)
VALUES (9406, 1, 'Term cash reserve', 'PLN', 25000, DATE '2024-08-01', 0.04,
        DATE '2027-08-01', NULL, 'Happy Investor interest-bearing cash reserve')
ON CONFLICT (id) DO UPDATE SET portfolio_id = EXCLUDED.portfolio_id, name = EXCLUDED.name,
    currency = EXCLUDED.currency, value = EXCLUDED.value, acquisition_date = EXCLUDED.acquisition_date,
    interest_rate = EXCLUDED.interest_rate, maturity_date = EXCLUDED.maturity_date,
    archived_at = EXCLUDED.archived_at, notes = EXCLUDED.notes;

INSERT INTO real_estate (id, portfolio_id, name, currency, value, tax_base, acquisition_date,
                         land_register_number, archived_at, notes)
VALUES
    (9402, 1, 'Apartment A', 'PLN', 400000, 3200, DATE '2024-08-01', 'KR1P/4322432/0', NULL, 'Happy Investor canonical profile'),
    (9403, 1, 'Apartment B', 'PLN', 500000, 3000, DATE '2024-08-01', NULL, NULL, 'Happy Investor canonical profile')
ON CONFLICT (id) DO UPDATE SET portfolio_id = EXCLUDED.portfolio_id, name = EXCLUDED.name,
    currency = EXCLUDED.currency, value = EXCLUDED.value, tax_base = EXCLUDED.tax_base, acquisition_date = EXCLUDED.acquisition_date,
    land_register_number = EXCLUDED.land_register_number, archived_at = EXCLUDED.archived_at,
    notes = EXCLUDED.notes;

INSERT INTO long_term_asset_history (asset_id, asset_type, complete)
VALUES (9402, 'REAL_ESTATE', true), (9403, 'REAL_ESTATE', true)
ON CONFLICT (asset_id) DO UPDATE SET asset_type = EXCLUDED.asset_type, complete = EXCLUDED.complete;

INSERT INTO cash_reserve (id, portfolio_id, name, currency, value, acquisition_date, archived_at, notes)
VALUES (9401, 1, 'Cash reserve', 'PLN', 25000, DATE '2024-08-01', NULL, 'Happy Investor canonical profile')
ON CONFLICT (id) DO UPDATE SET portfolio_id = EXCLUDED.portfolio_id, name = EXCLUDED.name,
    currency = EXCLUDED.currency, value = EXCLUDED.value, acquisition_date = EXCLUDED.acquisition_date,
    archived_at = EXCLUDED.archived_at, notes = EXCLUDED.notes;

INSERT INTO personal_asset (id, portfolio_id, name, category, currency, value, acquisition_date, archived_at, notes)
VALUES (9404, 1, 'Family Car', 'VEHICLE', 'PLN', 10000, DATE '2024-08-01', NULL, 'Happy Investor canonical profile')
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
    (9201, 1, 'Happy Investor Plan', DATE '1984-01-01', 2024, 85, 60, 90000, 12000,
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
    1,
    2025,
    'DRAFT',
    '{"values":{"ACTUAL":{"NET_WORTH":{"metric":"NET_WORTH","derivedValue":1179307.015664,"source":"PORTFOLIO_DERIVED","note":"Happy Investor canonical profile: investment baseline plus whole-wealth assets"},"CORE_SPENDING":{"metric":"CORE_SPENDING","approvedValue":36000,"source":"USER_ENTERED","note":"Happy Investor canonical profile"},"DISCRETIONARY_SPENDING":{"metric":"DISCRETIONARY_SPENDING","approvedValue":6000,"source":"USER_ENTERED","note":"Happy Investor canonical profile"}},"BASELINE":{}}}'::jsonb)
ON CONFLICT (id) DO UPDATE
SET portfolio_id = EXCLUDED.portfolio_id,
    planning_year = EXCLUDED.planning_year,
    status = EXCLUDED.status,
    state = EXCLUDED.state;

-- Current read-model cache is pinned to the canonical 2025-01-01 historical close.
UPDATE assets
SET market_price = CASE id WHEN 1 THEN 249.059 WHEN 1001 THEN 403.840 END,
    market_price_usd = CASE id WHEN 1 THEN 249.059 WHEN 1001 THEN 403.840 END,
    price_source = 'STOOQ',
    price_updated_at = TIMESTAMPTZ '2025-01-01 12:00:00 Europe/Warsaw'
WHERE id IN (1, 1001);
