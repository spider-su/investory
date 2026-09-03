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

INSERT INTO rental_tax_policies (portfolio_id, valid_from, valid_to, rate)
VALUES (1, DATE '2024-08-01', NULL, 0.085)
ON CONFLICT (portfolio_id, valid_from) DO UPDATE
SET valid_to = EXCLUDED.valid_to,
    rate = EXCLUDED.rate;

INSERT INTO long_term_assets (
    id, portfolio_id, name, asset_type, currency, acquisition_date,
    acquisition_value, current_value, rental_tax_paid_by_tenant, active, notes
)
VALUES
    (9401, 1, 'Cash reserve', 'CASH_RESERVE', 'PLN', DATE '2024-08-01', 50000, 50000, false, true, 'Happy Investor canonical profile'),
    (9402, 1, 'Apartment A', 'REAL_ESTATE', 'PLN', DATE '2024-08-01', 400000, 400000, false, true, 'Happy Investor canonical profile'),
    (9403, 1, 'Apartment B', 'REAL_ESTATE', 'PLN', DATE '2024-08-01', 500000, 500000, false, true, 'Happy Investor canonical profile'),
    (9404, 1, 'Family Car', 'OTHER', 'PLN', DATE '2024-08-01', 10000, 10000, false, true, 'Happy Investor canonical profile'),
    (9405, 1, 'Treasury 2026', 'BOND', 'PLN', DATE '2024-07-31', 10000, 10000, false, true, 'Happy Investor canonical fixed income'),
    (9406, 1, 'Reserve deposit', 'DEPOSIT', 'PLN', DATE '2024-08-01', 50000, 50000, false, true, 'Happy Investor canonical fixed income')
ON CONFLICT (id) DO UPDATE
SET portfolio_id = EXCLUDED.portfolio_id,
    name = EXCLUDED.name,
    asset_type = EXCLUDED.asset_type,
    currency = EXCLUDED.currency,
    acquisition_date = EXCLUDED.acquisition_date,
    acquisition_value = EXCLUDED.acquisition_value,
    current_value = EXCLUDED.current_value,
    rental_tax_paid_by_tenant = EXCLUDED.rental_tax_paid_by_tenant,
    active = EXCLUDED.active,
    notes = EXCLUDED.notes;

INSERT INTO long_term_asset_lifecycle_periods (asset_id, active_from, active_to)
VALUES
    (9401, DATE '2024-08-01', NULL),
    (9402, DATE '2024-08-01', NULL),
    (9403, DATE '2024-08-01', NULL),
    (9404, DATE '2024-08-01', NULL),
    (9405, DATE '2024-07-31', NULL),
    (9406, DATE '2024-08-01', NULL)
ON CONFLICT DO NOTHING;

INSERT INTO long_term_asset_real_estate_details (asset_id)
VALUES (9402), (9403)
ON CONFLICT (asset_id) DO NOTHING;

INSERT INTO long_term_asset_valuation_periods
    (asset_id, valid_from, valid_to, expected_annual_growth_rate)
VALUES
    (9401, DATE '2024-08-01', NULL, 0.025),
    (9402, DATE '2024-08-01', NULL, 0.025),
    (9403, DATE '2024-08-01', NULL, 0.025),
    (9404, DATE '2024-08-01', NULL, 0.025),
    (9405, DATE '2024-07-31', DATE '2026-02-28', 0),
    (9406, DATE '2024-08-01', DATE '2027-08-01', 0)
ON CONFLICT DO NOTHING;

INSERT INTO long_term_asset_bond_details
    (asset_id, maturity_date, interest_treatment, tax_rate, redemption_value)
VALUES (9405, DATE '2026-02-28', 'PAY_OUT', 0.19, 10000)
ON CONFLICT (asset_id) DO NOTHING;

INSERT INTO long_term_asset_bond_rate_periods
    (id, asset_id, valid_from, valid_to, annual_interest_rate)
VALUES (9601, 9405, DATE '2024-07-31', DATE '2026-02-28', 0.04625)
ON CONFLICT (id) DO NOTHING;

INSERT INTO long_term_asset_deposit_details
    (asset_id, maturity_date, interest_treatment, annual_interest_rate, tax_rate)
VALUES (9406, DATE '2027-08-01', 'CAPITALIZE', 0.04, 0.19)
ON CONFLICT (asset_id) DO NOTHING;

INSERT INTO long_term_asset_rental_contracts
    (id, asset_id, start_date, end_date, rental_tax_paid_by_tenant, notes)
VALUES
    (9501, 9402, DATE '2024-08-01', NULL, false, 'Happy Investor canonical profile'),
    (9502, 9403, DATE '2024-08-01', DATE '2025-06-30', false, 'Happy Investor canonical profile B1'),
    (9503, 9403, DATE '2025-07-01', NULL, false, 'Happy Investor canonical profile B2')
ON CONFLICT (id) DO UPDATE
SET asset_id = EXCLUDED.asset_id,
    start_date = EXCLUDED.start_date,
    end_date = EXCLUDED.end_date,
    rental_tax_paid_by_tenant = EXCLUDED.rental_tax_paid_by_tenant,
    notes = EXCLUDED.notes;

INSERT INTO long_term_asset_rental_contract_terms
    (contract_id, cash_flow_type, amount, frequency, paid_by_tenant)
VALUES
    (9501, 'RENT', 3200, 'MONTHLY', false),
    (9502, 'RENT', 2800, 'MONTHLY', false),
    (9503, 'RENT', 3000, 'MONTHLY', false)
ON CONFLICT (contract_id, cash_flow_type) DO UPDATE
SET amount = EXCLUDED.amount,
    frequency = EXCLUDED.frequency,
    paid_by_tenant = EXCLUDED.paid_by_tenant;

WITH plan AS (
    INSERT INTO simulation_plans (id, portfolio_id, name, current_revision_id, archived)
    VALUES (9201, 1, 'Happy Investor Plan', 9202, false)
    ON CONFLICT (id) DO UPDATE
    SET portfolio_id = EXCLUDED.portfolio_id,
        name = EXCLUDED.name,
        current_revision_id = EXCLUDED.current_revision_id,
        archived = EXCLUDED.archived
    RETURNING id
)
INSERT INTO simulation_plan_revisions (
    id, simulation_plan_id, revision_number, current_age, start_year, end_age,
    retirement_age, annual_employment_income, annual_pre_retirement_contribution,
    annual_living_expenses, annual_discretionary_expenses, inflation_rate,
    rental_income_growth_rate, spending_growth_rate, funding_strategy,
    funding_order, safe_reserve_years, equity_harvest_minimum_return_rate,
    equity_gain_harvest_rate, allow_emergency_equity_withdrawal,
    fixed_income_return_rate, equity_return_rate, pension_start_age, annual_pension,
    capital_gain_tax_rate,
    baseline_as_of_year, baseline_reserve, baseline_investment_capital,
    baseline_long_term_capital, baseline_rental_income, baseline_long_term_income,
    baseline_long_term_state_version
)
SELECT
    9202, id, 1, 40, 2024, 85, 60, 90000, 12000,
    36000, 6000, 0.025, 0.025, 0.025, 'SIMPLE_WATERFALL',
    'CASH,BONDS,STOCKS', 2, 0.05, 0.25, true,
    0.04, 0.07, 67, 24000, 0.19,
    2025, 50000, 159307.015664, 970000, 74400, 74400, 1
FROM plan
ON CONFLICT (id) DO UPDATE
SET simulation_plan_id = EXCLUDED.simulation_plan_id,
    revision_number = EXCLUDED.revision_number,
    current_age = EXCLUDED.current_age,
    start_year = EXCLUDED.start_year,
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
    baseline_long_term_state_version = EXCLUDED.baseline_long_term_state_version;

UPDATE simulation_plans
SET current_revision_id = 9202
WHERE id = 9201;

INSERT INTO planning_years
    (id, portfolio_id, planning_year, status, baseline_plan_id, baseline_revision_id)
VALUES (9301, 1, 2025, 'DRAFT', 9201, 9202)
ON CONFLICT (id) DO UPDATE
SET portfolio_id = EXCLUDED.portfolio_id,
    planning_year = EXCLUDED.planning_year,
    status = EXCLUDED.status,
    baseline_plan_id = EXCLUDED.baseline_plan_id,
    baseline_revision_id = EXCLUDED.baseline_revision_id;

INSERT INTO planning_year_values
    (id, planning_year_id, value_kind, metric, derived_value, approved_value, source_type, note)
VALUES
    (9311, 9301, 'ACTUAL', 'NET_WORTH', 1179307.015664, NULL, 'PORTFOLIO_DERIVED', 'Happy Investor canonical profile: investment baseline plus whole-wealth assets'),
    (9312, 9301, 'ACTUAL', 'CORE_SPENDING', NULL, 36000, 'USER_ENTERED', 'Happy Investor canonical profile'),
    (9313, 9301, 'ACTUAL', 'DISCRETIONARY_SPENDING', NULL, 6000, 'USER_ENTERED', 'Happy Investor canonical profile')
ON CONFLICT (id) DO UPDATE
SET planning_year_id = EXCLUDED.planning_year_id,
    value_kind = EXCLUDED.value_kind,
    metric = EXCLUDED.metric,
    derived_value = EXCLUDED.derived_value,
    approved_value = EXCLUDED.approved_value,
    source_type = EXCLUDED.source_type,
    note = EXCLUDED.note;

-- Current read-model cache is pinned to the canonical 2025-01-01 historical close.
UPDATE assets
SET market_price = CASE id WHEN 1 THEN 249.059 WHEN 1001 THEN 403.840 END,
    market_price_usd = CASE id WHEN 1 THEN 249.059 WHEN 1001 THEN 403.840 END,
    price_source = 'STOOQ',
    price_updated_at = TIMESTAMPTZ '2025-01-01 12:00:00 Europe/Warsaw'
WHERE id IN (1, 1001);

