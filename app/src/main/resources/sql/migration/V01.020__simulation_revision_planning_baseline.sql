SET search_path TO investory, public;

ALTER TABLE investory.simulation_plan_revisions
  ADD COLUMN baseline_as_of_year integer,
  ADD COLUMN baseline_reserve numeric(30,12),
  ADD COLUMN baseline_investment_capital numeric(30,12),
  ADD COLUMN baseline_long_term_capital numeric(30,12),
  ADD COLUMN baseline_rental_income numeric(30,12),
  ADD COLUMN baseline_long_term_income numeric(30,12),
  ADD COLUMN baseline_long_term_state text;

COMMENT ON COLUMN investory.simulation_plan_revisions.baseline_as_of_year IS
  'As-of year for the frozen economic planning baseline; null means legacy revision.';
