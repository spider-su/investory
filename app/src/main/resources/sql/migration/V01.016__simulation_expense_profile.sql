ALTER TABLE simulation_plans
    ADD COLUMN expense_profile VARCHAR(512);

ALTER TABLE simulation_plan_revisions
    ADD COLUMN expense_profile VARCHAR(512);
