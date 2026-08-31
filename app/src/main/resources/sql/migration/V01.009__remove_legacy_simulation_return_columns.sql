-- Retire return assumptions that are no longer part of the deterministic bucket model.
ALTER TABLE investory.simulation_plan_revisions
    DROP COLUMN cash_return_rate,
    DROP COLUMN real_estate_return_rate,
    DROP COLUMN other_return_rate;
