ALTER TABLE simulation_plans
    ADD COLUMN funding_order VARCHAR(64);

UPDATE simulation_plans
SET funding_order = 'CASH,BONDS,STOCKS'
WHERE funding_order IS NULL;

ALTER TABLE simulation_plans
    ALTER COLUMN funding_order SET DEFAULT 'CASH,BONDS,STOCKS',
    ALTER COLUMN funding_order SET NOT NULL;

ALTER TABLE simulation_plan_revisions
    ADD COLUMN funding_order VARCHAR(64);

UPDATE simulation_plan_revisions
SET funding_order = 'CASH,BONDS,STOCKS'
WHERE funding_order IS NULL;

ALTER TABLE simulation_plan_revisions
    ALTER COLUMN funding_order SET DEFAULT 'CASH,BONDS,STOCKS',
    ALTER COLUMN funding_order SET NOT NULL;
