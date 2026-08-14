SET search_path TO investory, public;

ALTER TABLE investory.simulation_plan_revisions
  ADD CONSTRAINT uq_simulation_plan_revisions_id_plan
  UNIQUE (id, simulation_plan_id);

ALTER TABLE investory.simulation_plans
  DROP CONSTRAINT fk_simulation_plans_current_revision,
  ADD CONSTRAINT fk_simulation_plans_current_revision
  FOREIGN KEY (current_revision_id, id)
  REFERENCES investory.simulation_plan_revisions (id, simulation_plan_id);

-- Backfill legacy baseline rows where the logical plan has a current revision.
-- Rows without a resolvable revision remain explicitly unmapped legacy history.
UPDATE investory.planning_years y
SET baseline_revision_id = p.current_revision_id
FROM investory.simulation_plans p
WHERE y.baseline_plan_id = p.id
  AND y.baseline_revision_id IS NULL
  AND p.current_revision_id IS NOT NULL;

UPDATE investory.planning_years y
SET baseline_plan_id = NULL
WHERE y.baseline_plan_id IS NOT NULL
  AND y.baseline_revision_id IS NULL;

ALTER TABLE investory.planning_years
  ADD CONSTRAINT ck_planning_years_baseline_revision_pair
  CHECK ((baseline_plan_id IS NULL) = (baseline_revision_id IS NULL)),
  ADD CONSTRAINT fk_planning_years_baseline_revision
  FOREIGN KEY (baseline_revision_id, baseline_plan_id)
  REFERENCES investory.simulation_plan_revisions (id, simulation_plan_id);
