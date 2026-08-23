SET search_path TO investory, public;

CREATE TABLE investory.long_term_assets (
    id bigserial PRIMARY KEY,
    portfolio_id bigint NOT NULL REFERENCES investory.portfolios(id),
    name varchar(255) NOT NULL,
    asset_type varchar(32) NOT NULL CHECK (asset_type IN ('REAL_ESTATE', 'BOND', 'DEPOSIT', 'CASH_RESERVE', 'OTHER')),
    currency varchar(3) NOT NULL REFERENCES investory.currencies(id),
    external_key varchar(128),
    acquisition_date date,
    acquisition_value numeric(30,12),
    current_value numeric(30,12) NOT NULL CHECK (current_value >= 0),
    tax_base numeric(30,12),
    active boolean NOT NULL DEFAULT true,
    notes text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (acquisition_value IS NULL OR acquisition_value >= 0),
    CHECK (tax_base IS NULL OR tax_base >= 0),
    CHECK (btrim(name) <> '')
);
COMMENT ON COLUMN investory.long_term_assets.tax_base IS
    'Optional annual real-estate rental-tax base. NULL means no rental tax is estimated.';
COMMENT ON COLUMN investory.long_term_assets.asset_type IS
    'Planning-only asset type. CASH_RESERVE is immediately spendable; DEPOSIT remains contractual.';
COMMENT ON COLUMN investory.long_term_assets.external_key IS
    'Optional stable identity used by explicit long-term asset bootstrap imports; not an accounting identifier.';
CREATE INDEX ix_long_term_assets_portfolio_active ON investory.long_term_assets(portfolio_id, active);
CREATE UNIQUE INDEX ux_long_term_assets_portfolio_external_key
    ON investory.long_term_assets(portfolio_id, external_key)
    WHERE external_key IS NOT NULL;

CREATE TABLE investory.long_term_asset_cash_flows (
    id bigserial PRIMARY KEY,
    asset_id bigint NOT NULL REFERENCES investory.long_term_assets(id) ON DELETE CASCADE,
    cash_flow_type varchar(32) NOT NULL CHECK (cash_flow_type IN ('RENT','PARKING_RENT','ADMIN_FEE','UTILITIES','PROPERTY_TAX','INSURANCE','OTHER_INCOME','OTHER_EXPENSE')),
    amount numeric(30,12) NOT NULL CHECK (amount >= 0),
    frequency varchar(16) NOT NULL CHECK (frequency IN ('MONTHLY','ANNUAL')),
    valid_from date NOT NULL,
    valid_to date,
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
CREATE INDEX ix_long_term_asset_cash_flows_asset_dates ON investory.long_term_asset_cash_flows(asset_id, valid_from, valid_to);

CREATE TABLE investory.long_term_asset_valuation_periods (
    id bigserial PRIMARY KEY,
    asset_id bigint NOT NULL REFERENCES investory.long_term_assets(id) ON DELETE CASCADE,
    valid_from date NOT NULL,
    valid_to date,
    expected_annual_growth_rate numeric(20,12) NOT NULL,
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE TABLE investory.long_term_asset_bond_rate_periods (
    id bigserial PRIMARY KEY,
    asset_id bigint NOT NULL REFERENCES investory.long_term_assets(id) ON DELETE CASCADE,
    valid_from date NOT NULL,
    valid_to date,
    annual_interest_rate numeric(20,12) NOT NULL,
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE TABLE investory.long_term_asset_real_estate_details (
    asset_id bigint PRIMARY KEY REFERENCES investory.long_term_assets(id) ON DELETE CASCADE
);

CREATE TABLE investory.long_term_asset_bond_details (
    asset_id bigint PRIMARY KEY REFERENCES investory.long_term_assets(id) ON DELETE CASCADE,
    maturity_date date NOT NULL,
    interest_treatment varchar(16) NOT NULL CHECK (interest_treatment IN ('PAY_OUT','CAPITALIZE')),
    tax_rate numeric(20,12) NOT NULL DEFAULT 0.19 CHECK (tax_rate >= 0 AND tax_rate <= 1),
    redemption_value numeric(30,12) CHECK (redemption_value IS NULL OR redemption_value >= 0)
);

CREATE TABLE investory.long_term_asset_deposit_details (
    asset_id bigint PRIMARY KEY REFERENCES investory.long_term_assets(id) ON DELETE CASCADE,
    maturity_date date,
    interest_treatment varchar(16) NOT NULL CHECK (interest_treatment IN ('PAY_OUT','CAPITALIZE')),
    annual_interest_rate numeric(20,12) NOT NULL DEFAULT 0 CHECK (annual_interest_rate >= 0),
    tax_rate numeric(20,12) NOT NULL DEFAULT 0.19 CHECK (tax_rate >= 0 AND tax_rate <= 1)
);

CREATE TABLE investory.rental_tax_policies (
    id bigserial PRIMARY KEY,
    portfolio_id bigint NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    valid_from date NOT NULL,
    valid_to date,
    rate numeric(20,12) NOT NULL CHECK (rate >= 0 AND rate <= 1),
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    UNIQUE (portfolio_id, valid_from)
);

SET search_path TO investory, public;

CREATE TABLE investory.simulation_plans (
    id bigserial PRIMARY KEY,
    portfolio_id bigint NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    name varchar(255) NOT NULL,
    current_age integer NOT NULL CHECK (current_age >= 0),
    start_year integer NOT NULL CHECK (start_year BETWEEN 1900 AND 9999),
    end_age integer NOT NULL CHECK (end_age >= current_age),
    retirement_age integer,
    annual_employment_income numeric(30,12),
    annual_pre_retirement_contribution numeric(30,12),
    annual_living_expenses numeric(30,12) NOT NULL CHECK (annual_living_expenses >= 0),
    annual_discretionary_expenses numeric(30,12) NOT NULL DEFAULT 0 CHECK (annual_discretionary_expenses >= 0),
    inflation_rate numeric(20,12) NOT NULL,
    rental_income_growth_rate numeric(20,12) NOT NULL DEFAULT 0.020000000000,
    spending_growth_rate numeric(20,12) NOT NULL,
    funding_strategy varchar(32),
    safe_reserve_years numeric(20,12),
    equity_harvest_minimum_return_rate numeric(20,12),
    equity_gain_harvest_rate numeric(20,12),
    allow_emergency_equity_withdrawal boolean,
    cash_return_rate numeric(20,12) NOT NULL,
    fixed_income_return_rate numeric(20,12) NOT NULL,
    equity_return_rate numeric(20,12) NOT NULL,
    real_estate_return_rate numeric(20,12) NOT NULL,
    other_return_rate numeric(20,12) NOT NULL,
    pension_start_age integer NOT NULL,
    annual_pension numeric(30,12) NOT NULL CHECK (annual_pension >= 0),
    capital_gain_tax_rate numeric(20,12) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (btrim(name) <> ''),
    UNIQUE (portfolio_id, name)
);
CREATE INDEX ix_simulation_plans_portfolio ON investory.simulation_plans(portfolio_id);
COMMENT ON COLUMN investory.simulation_plans.rental_income_growth_rate IS
    'Annual decimal growth assumption for simulated RENT, PARKING_RENT, and OTHER_INCOME cash flows.';
COMMENT ON COLUMN investory.simulation_plans.spending_growth_rate IS
    'Annual decimal growth assumption for planned recurring core and discretionary spending; independent from inflation.';
COMMENT ON COLUMN investory.simulation_plans.funding_strategy IS
    'Simulation-only annual funding policy. Null legacy plans load as SIMPLE_WATERFALL.';
COMMENT ON COLUMN investory.simulation_plans.start_year IS
    'Stable calendar-year anchor for current_age; never recalculated when a saved plan is reopened.';

CREATE TABLE investory.simulation_plan_events (
    id                  bigserial PRIMARY KEY,
    simulation_plan_id  bigint NOT NULL REFERENCES investory.simulation_plans(id) ON DELETE CASCADE,
    event_year          integer NOT NULL CHECK (event_year BETWEEN 1900 AND 9999),
    name                varchar(255) NOT NULL CHECK (btrim(name) <> ''),
    amount              numeric(30,12) NOT NULL CHECK (amount >= 0),
    event_type          varchar(32) NOT NULL CHECK (event_type IN ('ONE_OFF_EXPENSE', 'ONE_OFF_INCOME')),
    notes               varchar(1023),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_simulation_plan_events_plan_year
    ON investory.simulation_plan_events(simulation_plan_id, event_year, id);

COMMENT ON TABLE investory.simulation_plan_events IS
    'Plan assumptions for deterministic annual one-off income and expense events. These never alter accounting data.';
COMMENT ON COLUMN investory.simulation_plan_events.amount IS
    'Positive nominal amount. event_type determines whether it is income or expense; no additional event inflation is applied.';

CREATE TABLE investory.planning_years (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    portfolio_id bigint NOT NULL,
    planning_year integer NOT NULL,
    status varchar(16) NOT NULL,
    baseline_plan_id bigint NULL,
    baseline_created_at timestamptz NULL,
    closed_at timestamptz NULL,
    reopened_at timestamptz NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_planning_years_portfolio_year UNIQUE (portfolio_id, planning_year)
);

CREATE TABLE investory.planning_year_values (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    planning_year_id bigint NOT NULL REFERENCES investory.planning_years(id) ON DELETE CASCADE,
    value_kind varchar(16) NOT NULL,
    metric varchar(48) NOT NULL,
    derived_value numeric(30,12) NULL,
    approved_value numeric(30,12) NULL,
    source_type varchar(32) NOT NULL,
    note varchar(1000) NULL,
    captured_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_planning_year_values_key UNIQUE (planning_year_id, value_kind, metric)
);

COMMENT ON TABLE investory.planning_years IS
  'Planning-only annual lifecycle and expectation baseline. It is downstream from accounting.';
COMMENT ON TABLE investory.planning_year_values IS
  'Planning-only derived, approved, and baseline values. Never an accounting fact table.';
