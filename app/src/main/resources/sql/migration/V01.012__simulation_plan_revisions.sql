SET search_path TO investory, public;

CREATE TABLE investory.simulation_plan_revisions (
    id bigserial PRIMARY KEY,
    simulation_plan_id bigint NOT NULL REFERENCES investory.simulation_plans(id) ON DELETE RESTRICT,
    revision_number integer NOT NULL CHECK (revision_number > 0),
    current_age integer NOT NULL,
    start_year integer NOT NULL,
    end_age integer NOT NULL,
    retirement_age integer,
    annual_employment_income numeric(30,12),
    annual_pre_retirement_contribution numeric(30,12),
    annual_living_expenses numeric(30,12) NOT NULL,
    annual_discretionary_expenses numeric(30,12) NOT NULL,
    inflation_rate numeric(20,12) NOT NULL,
    rental_income_growth_rate numeric(20,12) NOT NULL,
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
    annual_pension numeric(30,12) NOT NULL,
    capital_gain_tax_rate numeric(20,12) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_simulation_plan_revisions_plan_number
      UNIQUE (simulation_plan_id, revision_number)
);

CREATE TABLE investory.simulation_plan_revision_events (
    id bigserial PRIMARY KEY,
    revision_id bigint NOT NULL REFERENCES investory.simulation_plan_revisions(id) ON DELETE RESTRICT,
    event_year integer NOT NULL,
    name varchar(255) NOT NULL,
    amount numeric(30,12) NOT NULL,
    event_type varchar(32) NOT NULL,
    notes varchar(1023),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_simulation_plan_revision_events_revision_year
  ON investory.simulation_plan_revision_events(revision_id, event_year, id);

ALTER TABLE investory.simulation_plans
  ADD COLUMN current_revision_id bigint NULL,
  ADD COLUMN archived boolean NOT NULL DEFAULT false;

INSERT INTO investory.simulation_plan_revisions (
    simulation_plan_id, revision_number, current_age, start_year, end_age, retirement_age,
    annual_employment_income, annual_pre_retirement_contribution, annual_living_expenses,
    annual_discretionary_expenses, inflation_rate, rental_income_growth_rate, spending_growth_rate,
    funding_strategy, safe_reserve_years, equity_harvest_minimum_return_rate,
    equity_gain_harvest_rate, allow_emergency_equity_withdrawal, cash_return_rate,
    fixed_income_return_rate, equity_return_rate, real_estate_return_rate, other_return_rate,
    pension_start_age, annual_pension, capital_gain_tax_rate
)
SELECT id, 1, current_age, start_year, end_age, retirement_age,
       annual_employment_income, annual_pre_retirement_contribution, annual_living_expenses,
       annual_discretionary_expenses, inflation_rate, rental_income_growth_rate, spending_growth_rate,
       funding_strategy, safe_reserve_years, equity_harvest_minimum_return_rate,
       equity_gain_harvest_rate, allow_emergency_equity_withdrawal, cash_return_rate,
       fixed_income_return_rate, equity_return_rate, real_estate_return_rate, other_return_rate,
       pension_start_age, annual_pension, capital_gain_tax_rate
FROM investory.simulation_plans;

UPDATE investory.simulation_plans p
SET current_revision_id = r.id
FROM investory.simulation_plan_revisions r
WHERE r.simulation_plan_id = p.id AND r.revision_number = 1;

ALTER TABLE investory.simulation_plans
  ADD CONSTRAINT fk_simulation_plans_current_revision
  FOREIGN KEY (current_revision_id) REFERENCES investory.simulation_plan_revisions(id);

ALTER TABLE investory.planning_years
  ADD COLUMN baseline_revision_id bigint NULL
  REFERENCES investory.simulation_plan_revisions(id);

UPDATE investory.planning_years y
SET baseline_revision_id = p.current_revision_id
FROM investory.simulation_plans p
WHERE y.baseline_plan_id = p.id;

INSERT INTO investory.simulation_plan_revision_events
    (revision_id, event_year, name, amount, event_type, notes)
SELECT r.id, e.event_year, e.name, e.amount, e.event_type, e.notes
FROM investory.simulation_plan_events e
JOIN investory.simulation_plan_revisions r
  ON r.simulation_plan_id = e.simulation_plan_id AND r.revision_number = 1;

COMMENT ON TABLE investory.simulation_plan_revisions IS
  'Immutable Base assumption snapshots. Editing a logical plan creates a new revision.';
COMMENT ON TABLE investory.simulation_plan_revision_events IS
  'Immutable life-event snapshots owned by a simulation plan revision.';
COMMENT ON COLUMN investory.planning_years.baseline_revision_id IS
  'Exact immutable plan revision used to create this baseline; null only for legacy/unmapped data.';
