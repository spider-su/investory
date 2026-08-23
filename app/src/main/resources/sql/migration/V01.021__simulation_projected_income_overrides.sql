SET search_path TO investory, public;

ALTER TABLE investory.simulation_plans
    ADD COLUMN rental_income_mode varchar(16) NOT NULL DEFAULT 'SOURCE',
    ADD COLUMN manual_rental_income numeric(30,12),
    ADD COLUMN bond_cash_income_mode varchar(16) NOT NULL DEFAULT 'SOURCE',
    ADD COLUMN manual_bond_cash_income numeric(30,12);

ALTER TABLE investory.simulation_plan_revisions
    ADD COLUMN rental_income_mode varchar(16) NOT NULL DEFAULT 'SOURCE',
    ADD COLUMN manual_rental_income numeric(30,12),
    ADD COLUMN bond_cash_income_mode varchar(16) NOT NULL DEFAULT 'SOURCE',
    ADD COLUMN manual_bond_cash_income numeric(30,12);

ALTER TABLE investory.simulation_plans
    ADD CONSTRAINT ck_simulation_plans_rental_income_mode
      CHECK (rental_income_mode IN ('SOURCE', 'MANUAL')),
    ADD CONSTRAINT ck_simulation_plans_bond_cash_income_mode
      CHECK (bond_cash_income_mode IN ('SOURCE', 'MANUAL'));

ALTER TABLE investory.simulation_plan_revisions
    ADD CONSTRAINT ck_simulation_plan_revisions_rental_income_mode
      CHECK (rental_income_mode IN ('SOURCE', 'MANUAL')),
    ADD CONSTRAINT ck_simulation_plan_revisions_bond_cash_income_mode
      CHECK (bond_cash_income_mode IN ('SOURCE', 'MANUAL'));
