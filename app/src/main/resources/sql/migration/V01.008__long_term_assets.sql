SET search_path TO investory, public;

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE IF NOT EXISTS investory.long_term_assets (
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
    rental_tax_paid_by_tenant boolean NOT NULL DEFAULT false,
    active boolean NOT NULL DEFAULT true,
    archived_at date,
    notes text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (acquisition_value IS NULL OR acquisition_value >= 0),
    CHECK (tax_base IS NULL OR tax_base >= 0),
    CHECK (btrim(name) <> '')
);

COMMENT ON COLUMN investory.long_term_assets.tax_base IS
    'Optional monthly real-estate rental-tax base. Annual rental tax is tax_base * 12 * effective rate.';
COMMENT ON COLUMN investory.long_term_assets.rental_tax_paid_by_tenant IS
    'When true, configured rental tax is excluded from landlord net rental income.';
COMMENT ON COLUMN investory.long_term_assets.asset_type IS
    'Planning-only asset type. CASH_RESERVE is immediately spendable; DEPOSIT remains contractual.';
COMMENT ON COLUMN investory.long_term_assets.external_key IS
    'Optional stable identity used by explicit long-term asset bootstrap imports; not an accounting identifier.';

CREATE INDEX IF NOT EXISTS ix_long_term_assets_portfolio_active ON investory.long_term_assets(portfolio_id, active);
CREATE UNIQUE INDEX IF NOT EXISTS ux_long_term_assets_portfolio_external_key
    ON investory.long_term_assets(portfolio_id, external_key)
    WHERE external_key IS NOT NULL;

CREATE TABLE IF NOT EXISTS investory.long_term_asset_valuation_periods (
    id bigserial PRIMARY KEY,
    asset_id bigint NOT NULL REFERENCES investory.long_term_assets(id) ON DELETE CASCADE,
    valid_from date NOT NULL,
    valid_to date,
    expected_annual_growth_rate numeric(20,12) NOT NULL,
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT ck_long_term_asset_valuation_growth
        CHECK (expected_annual_growth_rate >= -1 AND expected_annual_growth_rate <= 1),
    CONSTRAINT ex_long_term_asset_valuation_periods_no_overlap
        EXCLUDE USING gist (
            asset_id WITH =,
            daterange(valid_from, COALESCE(valid_to + 1, 'infinity'::date), '[)') WITH &&
        )
);

CREATE INDEX IF NOT EXISTS ix_long_term_asset_valuation_periods_asset_dates
    ON investory.long_term_asset_valuation_periods(asset_id, valid_from, valid_to);

CREATE TABLE IF NOT EXISTS investory.long_term_asset_bond_rate_periods (
    id bigserial PRIMARY KEY,
    asset_id bigint NOT NULL REFERENCES investory.long_term_assets(id) ON DELETE CASCADE,
    valid_from date NOT NULL,
    valid_to date,
    annual_interest_rate numeric(20,12) NOT NULL,
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT ex_long_term_asset_bond_rate_periods_no_overlap
        EXCLUDE USING gist (
            asset_id WITH =,
            daterange(valid_from, COALESCE(valid_to + 1, 'infinity'::date), '[)') WITH &&
        )
);

CREATE INDEX IF NOT EXISTS ix_long_term_asset_bond_rate_periods_asset_dates
    ON investory.long_term_asset_bond_rate_periods(asset_id, valid_from, valid_to);

CREATE TABLE IF NOT EXISTS investory.long_term_asset_real_estate_details (
    asset_id bigint PRIMARY KEY REFERENCES investory.long_term_assets(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS investory.long_term_asset_bond_details (
    asset_id bigint PRIMARY KEY REFERENCES investory.long_term_assets(id) ON DELETE CASCADE,
    maturity_date date NOT NULL,
    interest_treatment varchar(16) NOT NULL CHECK (interest_treatment IN ('PAY_OUT','CAPITALIZE')),
    tax_rate numeric(20,12) NOT NULL DEFAULT 0.19 CHECK (tax_rate >= 0 AND tax_rate <= 1),
    redemption_value numeric(30,12) CHECK (redemption_value IS NULL OR redemption_value >= 0)
);

CREATE TABLE IF NOT EXISTS investory.long_term_asset_deposit_details (
    asset_id bigint PRIMARY KEY REFERENCES investory.long_term_assets(id) ON DELETE CASCADE,
    maturity_date date NOT NULL,
    interest_treatment varchar(16) NOT NULL CHECK (interest_treatment IN ('PAY_OUT','CAPITALIZE')),
    annual_interest_rate numeric(20,12) NOT NULL DEFAULT 0 CHECK (annual_interest_rate >= 0),
    tax_rate numeric(20,12) NOT NULL DEFAULT 0.19 CHECK (tax_rate >= 0 AND tax_rate <= 1)
);

COMMENT ON TABLE investory.long_term_asset_deposit_details IS
    'Complete deposit subtype state. Maturity is required and deposit creation persists this row atomically with the asset.';

CREATE TABLE IF NOT EXISTS investory.rental_tax_policies (
    id bigserial PRIMARY KEY,
    portfolio_id bigint NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    valid_from date NOT NULL,
    valid_to date,
    rate numeric(20,12) NOT NULL CHECK (rate >= 0 AND rate <= 1),
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    UNIQUE (portfolio_id, valid_from),
    CONSTRAINT ex_rental_tax_policy_period
        EXCLUDE USING gist (
            portfolio_id WITH =,
            daterange(valid_from, COALESCE(valid_to, 'infinity'::date), '[]') WITH &&
        )
);

CREATE INDEX IF NOT EXISTS ix_rental_tax_policies_portfolio_dates
    ON investory.rental_tax_policies(portfolio_id, valid_from, valid_to);

INSERT INTO investory.reconciliation_parameters(parameter_name, numeric_value, description)
VALUES ('long_term_default_rental_tax_rate', 0.085,
        'Fallback annual rental-tax rate when no portfolio policy is effective.')
ON CONFLICT (parameter_name) DO NOTHING;

CREATE TABLE IF NOT EXISTS investory.long_term_asset_lifecycle_periods (
    id bigserial PRIMARY KEY,
    asset_id bigint NOT NULL REFERENCES investory.long_term_assets(id) ON DELETE CASCADE,
    active_from date NOT NULL,
    active_to date,
    CHECK (active_to IS NULL OR active_to >= active_from),
    CONSTRAINT ex_long_term_asset_lifecycle_period
        EXCLUDE USING gist (
            asset_id WITH =,
            daterange(active_from, COALESCE(active_to, 'infinity'::date), '[]') WITH &&
        )
);

CREATE INDEX IF NOT EXISTS ix_long_term_asset_lifecycle_periods_asset_dates
    ON investory.long_term_asset_lifecycle_periods(asset_id, active_from, active_to);

CREATE TABLE IF NOT EXISTS investory.long_term_asset_rental_contracts (
    id bigserial PRIMARY KEY,
    asset_id bigint NOT NULL REFERENCES investory.long_term_assets(id) ON DELETE CASCADE,
    start_date date NOT NULL,
    end_date date,
    terminated_date date,
    rental_tax_paid_by_tenant boolean,
    monthly_tax_base numeric(20,2),
    bootstrap_managed boolean NOT NULL DEFAULT false,
    tenant_name varchar(200),
    tenant_email varchar(320),
    tenant_phone varchar(50),
    notes text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_rental_contract_dates
        CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_rental_contract_termination
        CHECK (terminated_date IS NULL OR terminated_date >= start_date),
    CONSTRAINT ck_rental_contract_termination_within_term
        CHECK (terminated_date IS NULL OR end_date IS NULL OR terminated_date <= end_date),
    CONSTRAINT ck_rental_contract_monthly_tax_base_non_negative
        CHECK (monthly_tax_base IS NULL OR monthly_tax_base >= 0),
    CONSTRAINT ck_rental_contract_tenant_name_length
        CHECK (tenant_name IS NULL OR char_length(tenant_name) <= 200),
    CONSTRAINT ck_rental_contract_tenant_email_length
        CHECK (tenant_email IS NULL OR char_length(tenant_email) <= 320),
    CONSTRAINT ck_rental_contract_tenant_phone_length
        CHECK (tenant_phone IS NULL OR char_length(tenant_phone) <= 50),
    CONSTRAINT ex_rental_contracts_asset_period
        EXCLUDE USING gist (
            asset_id WITH =,
            daterange(start_date, COALESCE(LEAST(end_date, terminated_date), 'infinity'::date), '[]') WITH &&
        )
);

CREATE INDEX IF NOT EXISTS ix_rental_contracts_asset_dates
    ON investory.long_term_asset_rental_contracts(asset_id, start_date, end_date);
CREATE INDEX IF NOT EXISTS ix_rental_contracts_asset_start
    ON investory.long_term_asset_rental_contracts(asset_id, start_date DESC);

COMMENT ON COLUMN investory.long_term_asset_rental_contracts.monthly_tax_base IS
    'Monthly rental-tax base captured for this contract. Prevents later property-default edits from rewriting historical tax.';
COMMENT ON COLUMN investory.long_term_asset_rental_contracts.rental_tax_paid_by_tenant IS
    'Rental-tax ownership captured for this contract. NULL is retained only for legacy compatibility.';
COMMENT ON COLUMN investory.long_term_asset_rental_contracts.bootstrap_managed IS
    'True only for contracts created and replaced by the Long-Term bootstrap importer.';
COMMENT ON COLUMN investory.long_term_asset_rental_contracts.tenant_name IS
    'Optional tenant display name owned by the rental contract.';
COMMENT ON COLUMN investory.long_term_asset_rental_contracts.tenant_email IS
    'Optional tenant email owned by the rental contract.';
COMMENT ON COLUMN investory.long_term_asset_rental_contracts.tenant_phone IS
    'Optional tenant phone owned by the rental contract.';

CREATE TABLE IF NOT EXISTS investory.long_term_asset_rental_contract_terms (
    id bigserial PRIMARY KEY,
    contract_id bigint NOT NULL REFERENCES investory.long_term_asset_rental_contracts(id) ON DELETE CASCADE,
    cash_flow_type varchar(32) NOT NULL CHECK (cash_flow_type IN ('RENT', 'PARKING_RENT', 'ADMIN_FEE', 'UTILITIES', 'PROPERTY_TAX', 'INSURANCE', 'OTHER_INCOME', 'OTHER_EXPENSE')),
    amount numeric(30,12) NOT NULL CHECK (amount >= 0),
    frequency varchar(16) NOT NULL CHECK (frequency IN ('MONTHLY', 'ANNUAL')),
    paid_by_tenant boolean NOT NULL DEFAULT false,
    CONSTRAINT ux_rental_contract_term_type UNIQUE (contract_id, cash_flow_type)
);

CREATE INDEX IF NOT EXISTS ix_rental_contract_terms_contract
    ON investory.long_term_asset_rental_contract_terms(contract_id, cash_flow_type);

CREATE TABLE IF NOT EXISTS investory.simulation_plans (
    id bigserial PRIMARY KEY,
    portfolio_id bigint NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    name varchar(255) NOT NULL,
    current_revision_id bigint,
    archived boolean NOT NULL DEFAULT false,
    sandbox boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (btrim(name) <> ''),
    UNIQUE (portfolio_id, name)
);

CREATE INDEX IF NOT EXISTS ix_simulation_plans_portfolio ON investory.simulation_plans(portfolio_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_simulation_plans_one_sandbox_per_portfolio
    ON investory.simulation_plans (portfolio_id)
    WHERE sandbox AND NOT archived;

CREATE TABLE IF NOT EXISTS investory.simulation_plan_revisions (
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
    funding_order varchar(64) NOT NULL DEFAULT 'RESERVE,LONG_TERM,INVESTMENT',
    expense_profile varchar(512),
    safe_reserve_years numeric(20,12) DEFAULT 5,
    equity_harvest_minimum_return_rate numeric(20,12) DEFAULT 0.07,
    equity_gain_harvest_rate numeric(20,12) DEFAULT 0.75,
    allow_emergency_equity_withdrawal boolean DEFAULT false,
    fixed_income_return_rate numeric(20,12) NOT NULL,
    equity_return_rate numeric(20,12) NOT NULL,
    pension_start_age integer NOT NULL,
    annual_pension numeric(30,12) NOT NULL,
    capital_gain_tax_rate numeric(20,12) NOT NULL,
    baseline_as_of_year integer,
    baseline_reserve numeric(30,12),
    baseline_investment_capital numeric(30,12),
    baseline_long_term_capital numeric(30,12),
    baseline_rental_income numeric(30,12),
    baseline_long_term_income numeric(30,12),
    baseline_long_term_state text,
    baseline_long_term_state_version integer,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_simulation_plan_revisions_plan_number
        UNIQUE (simulation_plan_id, revision_number),
    CONSTRAINT uq_simulation_plan_revisions_id_plan
        UNIQUE (id, simulation_plan_id)
);

COMMENT ON TABLE investory.simulation_plan_revisions IS
    'Immutable Base assumption snapshots. Editing a logical plan creates a new revision.';
COMMENT ON COLUMN investory.simulation_plan_revisions.baseline_as_of_year IS
    'As-of year for the frozen economic planning baseline; null means legacy revision.';

ALTER TABLE investory.simulation_plans
    ADD CONSTRAINT fk_simulation_plans_current_revision
        FOREIGN KEY (current_revision_id, id)
        REFERENCES investory.simulation_plan_revisions (id, simulation_plan_id);

CREATE TABLE IF NOT EXISTS investory.simulation_plan_revision_events (
    id bigserial PRIMARY KEY,
    logical_event_id bigint,
    revision_id bigint NOT NULL REFERENCES investory.simulation_plan_revisions(id) ON DELETE RESTRICT,
    event_year integer NOT NULL,
    name varchar(255) NOT NULL,
    amount numeric(30,12) NOT NULL,
    event_type varchar(32) NOT NULL,
    notes varchar(1023),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_simulation_plan_revision_events_revision_year
    ON investory.simulation_plan_revision_events(revision_id, event_year, id);
CREATE INDEX IF NOT EXISTS ix_simulation_plan_revision_events_logical_id
    ON investory.simulation_plan_revision_events(logical_event_id);

COMMENT ON TABLE investory.simulation_plan_revision_events IS
    'Immutable life-event snapshots owned by a simulation plan revision.';
COMMENT ON COLUMN investory.simulation_plan_revision_events.logical_event_id IS
    'Stable logical event identity copied across immutable plan revisions; null only for legacy rows.';

CREATE TABLE IF NOT EXISTS investory.planning_years (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    portfolio_id bigint NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    planning_year integer NOT NULL,
    status varchar(16) NOT NULL,
    baseline_plan_id bigint,
    baseline_revision_id bigint,
    baseline_created_at timestamptz,
    closed_at timestamptz,
    reopened_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_planning_years_portfolio_year UNIQUE (portfolio_id, planning_year),
    CONSTRAINT ck_planning_years_baseline_revision_pair
        CHECK ((baseline_plan_id IS NULL) = (baseline_revision_id IS NULL)),
    CONSTRAINT fk_planning_years_baseline_revision
        FOREIGN KEY (baseline_revision_id, baseline_plan_id)
        REFERENCES investory.simulation_plan_revisions (id, simulation_plan_id)
);

CREATE TABLE IF NOT EXISTS investory.planning_year_values (
    id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    planning_year_id bigint NOT NULL REFERENCES investory.planning_years(id) ON DELETE CASCADE,
    value_kind varchar(16) NOT NULL,
    metric varchar(48) NOT NULL,
    derived_value numeric(30,12),
    approved_value numeric(30,12),
    source_type varchar(32) NOT NULL,
    note varchar(1000),
    captured_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_planning_year_values_key UNIQUE (planning_year_id, value_kind, metric)
);

COMMENT ON TABLE investory.planning_years IS
    'Planning-only annual lifecycle and expectation baseline. It is downstream from accounting.';
COMMENT ON COLUMN investory.planning_years.baseline_revision_id IS
    'Exact immutable plan revision used to create this baseline; null only for legacy/unmapped data.';
COMMENT ON TABLE investory.planning_year_values IS
    'Planning-only derived, approved, and baseline values. Never an accounting fact table.';

CREATE OR REPLACE FUNCTION investory.longterm_fn_assert_subtype_consistency()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    actual_type varchar(32);
BEGIN
    SELECT asset_type INTO actual_type
    FROM investory.long_term_assets
    WHERE id = NEW.asset_id;

    IF actual_type IS NULL
       OR (TG_TABLE_NAME = 'long_term_asset_bond_details' AND actual_type <> 'BOND')
       OR (TG_TABLE_NAME = 'long_term_asset_deposit_details' AND actual_type <> 'DEPOSIT')
       OR (TG_TABLE_NAME = 'long_term_asset_rental_contracts' AND actual_type <> 'REAL_ESTATE')
       OR (TG_TABLE_NAME = 'long_term_asset_real_estate_details' AND actual_type <> 'REAL_ESTATE') THEN
        RAISE EXCEPTION 'Subtype details do not match asset type';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER longterm_trg_bond_details_type
BEFORE INSERT OR UPDATE ON investory.long_term_asset_bond_details
FOR EACH ROW EXECUTE FUNCTION investory.longterm_fn_assert_subtype_consistency();

CREATE TRIGGER longterm_trg_deposit_details_type
BEFORE INSERT OR UPDATE ON investory.long_term_asset_deposit_details
FOR EACH ROW EXECUTE FUNCTION investory.longterm_fn_assert_subtype_consistency();

CREATE TRIGGER longterm_trg_rental_contract_type
BEFORE INSERT OR UPDATE ON investory.long_term_asset_rental_contracts
FOR EACH ROW EXECUTE FUNCTION investory.longterm_fn_assert_subtype_consistency();

CREATE TRIGGER longterm_trg_real_estate_details_type
BEFORE INSERT OR UPDATE ON investory.long_term_asset_real_estate_details
FOR EACH ROW EXECUTE FUNCTION investory.longterm_fn_assert_subtype_consistency();

CREATE OR REPLACE FUNCTION investory.longterm_fn_assert_parent_type_consistency()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.asset_type IS DISTINCT FROM NEW.asset_type THEN
        IF NEW.asset_type <> 'BOND'
           AND EXISTS (SELECT 1 FROM investory.long_term_asset_bond_details d WHERE d.asset_id = NEW.id) THEN
            RAISE EXCEPTION 'Asset type cannot change while bond details exist';
        END IF;
        IF NEW.asset_type <> 'DEPOSIT'
           AND EXISTS (SELECT 1 FROM investory.long_term_asset_deposit_details d WHERE d.asset_id = NEW.id) THEN
            RAISE EXCEPTION 'Asset type cannot change while deposit details exist';
        END IF;
        IF NEW.asset_type <> 'REAL_ESTATE'
           AND EXISTS (SELECT 1 FROM investory.long_term_asset_real_estate_details d WHERE d.asset_id = NEW.id) THEN
            RAISE EXCEPTION 'Asset type cannot change while real-estate details exist';
        END IF;
        IF NEW.asset_type <> 'REAL_ESTATE'
           AND EXISTS (SELECT 1 FROM investory.long_term_asset_rental_contracts c WHERE c.asset_id = NEW.id) THEN
            RAISE EXCEPTION 'Asset type cannot change while rental contracts exist';
        END IF;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER longterm_trg_asset_type_consistency
BEFORE UPDATE OF asset_type ON investory.long_term_assets
FOR EACH ROW EXECUTE FUNCTION investory.longterm_fn_assert_parent_type_consistency();
