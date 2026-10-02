DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM investory.retirement_plans
        WHERE archived = false
        GROUP BY portfolio_id, lower(btrim(name))
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot create active retirement plan name index: duplicate active names exist';
    END IF;
END
$$;

ALTER TABLE investory.retirement_plans
    DROP CONSTRAINT IF EXISTS retirement_plans_portfolio_id_name_key;
ALTER TABLE investory.retirement_plans
    DROP CONSTRAINT IF EXISTS uq_retirement_plans_portfolio_name;

CREATE UNIQUE INDEX uq_retirement_plans_active_name
    ON investory.retirement_plans (portfolio_id, lower(btrim(name)))
    WHERE archived = false;
