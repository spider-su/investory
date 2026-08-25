INSERT INTO investory.long_term_assets
    (id, portfolio_id, name, asset_type, currency, acquisition_date, acquisition_value,
     current_value, tax_base, active, notes)
VALUES
    (9101, 1, 'UI Other Asset', 'OTHER', 'USD', DATE '2020-01-01', 10000, 12500, NULL, true, 'UI smoke fixture'),
    (9102, 1, 'UI Bond', 'BOND', 'USD', DATE '2024-01-01', 20000, 20500, NULL, true, 'UI smoke fixture'),
    (9103, 1, 'UI Cash Reserve', 'CASH_RESERVE', 'USD', DATE '2024-01-01', 15000, 15000, NULL, true, 'UI smoke fixture'),
    (9104, 1, 'UI Deposit', 'DEPOSIT', 'USD', DATE '2025-01-01', 8000, 8200, NULL, true, 'UI smoke fixture'),
    (9105, 1, 'UI Rental Home', 'REAL_ESTATE', 'USD', DATE '2020-06-01', 250000, 300000, 200000, true, 'UI smoke fixture')
ON CONFLICT (id) DO NOTHING;

INSERT INTO investory.long_term_asset_bond_details
    (asset_id, maturity_date, interest_treatment, tax_rate, redemption_value)
VALUES (9102, DATE '2030-01-01', 'PAY_OUT', 0.19, 20000)
ON CONFLICT (asset_id) DO NOTHING;

INSERT INTO investory.long_term_asset_bond_rate_periods
    (id, asset_id, valid_from, valid_to, annual_interest_rate)
VALUES (9112, 9102, DATE '2024-01-01', NULL, 0.05)
ON CONFLICT (id) DO NOTHING;

INSERT INTO investory.long_term_asset_deposit_details
    (asset_id, maturity_date, interest_treatment, annual_interest_rate, tax_rate)
VALUES (9104, DATE '2028-01-01', 'CAPITALIZE', 0.04, 0.19)
ON CONFLICT (asset_id) DO NOTHING;

INSERT INTO investory.long_term_asset_real_estate_details (asset_id)
VALUES (9105)
ON CONFLICT (asset_id) DO NOTHING;

INSERT INTO investory.long_term_asset_rental_contracts
    (id, asset_id, start_date, end_date, tenant_name, tenant_email, monthly_tax_base)
VALUES (9115, 9105, DATE '2026-01-01', DATE '2027-12-31',
        'UI Tenant', 'ui.tenant@example.test', 1500)
ON CONFLICT (id) DO NOTHING;

INSERT INTO investory.long_term_asset_rental_contract_terms
    (id, contract_id, cash_flow_type, amount, frequency, paid_by_tenant)
VALUES
    (9121, 9115, 'RENT', 1800, 'MONTHLY', false),
    (9122, 9115, 'ADMIN_FEE', 250, 'MONTHLY', false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO investory.simulation_plans
    (id, portfolio_id, name, current_age, start_year, end_age, retirement_age,
     annual_employment_income, annual_pre_retirement_contribution,
     annual_living_expenses, annual_discretionary_expenses, inflation_rate,
     rental_income_growth_rate, spending_growth_rate, funding_strategy,
     safe_reserve_years, equity_harvest_minimum_return_rate, equity_gain_harvest_rate,
     allow_emergency_equity_withdrawal, cash_return_rate, fixed_income_return_rate,
     equity_return_rate, real_estate_return_rate, other_return_rate,
     pension_start_age, annual_pension, capital_gain_tax_rate, archived,
     funding_order, rental_income_mode, bond_cash_income_mode)
VALUES
    (9201, 1, 'UI Smoke Plan', 40, 2026, 85, 60,
     90000, 12000, 36000, 6000, 0.025, 0.02, 0.025, 'SIMPLE_WATERFALL',
     2, 0.05, 0.25, true, 0.02, 0.04, 0.07, 0.04, 0.03,
     67, 24000, 0.19, false, 'CASH,BONDS,STOCKS', 'SOURCE', 'SOURCE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO investory.simulation_plan_revisions
    (id, simulation_plan_id, revision_number, current_age, start_year, end_age, retirement_age,
     annual_employment_income, annual_pre_retirement_contribution,
     annual_living_expenses, annual_discretionary_expenses, inflation_rate,
     rental_income_growth_rate, spending_growth_rate, funding_strategy,
     safe_reserve_years, equity_harvest_minimum_return_rate, equity_gain_harvest_rate,
     allow_emergency_equity_withdrawal, cash_return_rate, fixed_income_return_rate,
     equity_return_rate, real_estate_return_rate, other_return_rate,
     pension_start_age, annual_pension, capital_gain_tax_rate, funding_order,
     baseline_as_of_year, baseline_reserve, baseline_investment_capital,
     baseline_long_term_capital, baseline_rental_income, baseline_long_term_income,
     baseline_long_term_state_version, rental_income_mode, bond_cash_income_mode)
VALUES
    (9202, 9201, 1, 40, 2026, 85, 60,
     90000, 12000, 36000, 6000, 0.025, 0.02, 0.025, 'SIMPLE_WATERFALL',
     2, 0.05, 0.25, true, 0.02, 0.04, 0.07, 0.04, 0.03,
     67, 24000, 0.19, 'CASH,BONDS,STOCKS',
     2026, 15000, 0, 341700, 21600, 1000, 1, 'SOURCE', 'SOURCE')
ON CONFLICT (id) DO NOTHING;

UPDATE investory.simulation_plans SET current_revision_id = 9202 WHERE id = 9201;

INSERT INTO investory.planning_years
    (id, portfolio_id, planning_year, status, created_at, updated_at)
VALUES (9301, 1, 2025, 'DRAFT', now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO investory.planning_year_values
    (id, planning_year_id, value_kind, metric, derived_value, approved_value,
     source_type, note, captured_at)
VALUES
    (9311, 9301, 'ACTUAL', 'NET_WORTH', 320000, NULL, 'PORTFOLIO_DERIVED', 'UI smoke fixture', now()),
    (9312, 9301, 'ACTUAL', 'CORE_SPENDING', NULL, 35000, 'USER_ENTERED', 'UI smoke fixture', now()),
    (9313, 9301, 'ACTUAL', 'DISCRETIONARY_SPENDING', NULL, 5000, 'USER_ENTERED', 'UI smoke fixture', now())
ON CONFLICT (id) DO NOTHING;
