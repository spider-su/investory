SET search_path TO investory, public;

ALTER TABLE investory.long_term_assets
    ADD COLUMN rental_tax_paid_by_tenant boolean NOT NULL DEFAULT false,
    ADD COLUMN archived_at date;

ALTER TABLE investory.long_term_asset_cash_flows
    ADD COLUMN paid_by_tenant boolean;

UPDATE investory.long_term_asset_cash_flows
SET paid_by_tenant = CASE cash_flow_type
    WHEN 'ADMIN_FEE' THEN true
    WHEN 'UTILITIES' THEN true
    ELSE false
END
WHERE paid_by_tenant IS NULL;

ALTER TABLE investory.long_term_asset_cash_flows
    ALTER COLUMN paid_by_tenant SET DEFAULT false,
    ALTER COLUMN paid_by_tenant SET NOT NULL;

CREATE INDEX ix_long_term_asset_cash_flows_asset_type_dates
    ON investory.long_term_asset_cash_flows(asset_id, cash_flow_type, valid_from, valid_to);
CREATE INDEX ix_long_term_asset_valuation_periods_asset_dates
    ON investory.long_term_asset_valuation_periods(asset_id, valid_from, valid_to);
CREATE INDEX ix_long_term_asset_bond_rate_periods_asset_dates
    ON investory.long_term_asset_bond_rate_periods(asset_id, valid_from, valid_to);
CREATE INDEX ix_rental_tax_policies_portfolio_dates
    ON investory.rental_tax_policies(portfolio_id, valid_from, valid_to);

COMMENT ON COLUMN investory.long_term_assets.rental_tax_paid_by_tenant IS
    'When true, configured rental tax is excluded from landlord net rental income.';
COMMENT ON COLUMN investory.long_term_asset_cash_flows.paid_by_tenant IS
    'Whether an expense is paid by the tenant and therefore excluded from landlord expenses.';

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE investory.long_term_asset_cash_flows
    ADD CONSTRAINT ex_long_term_asset_cash_flows_period
    EXCLUDE USING gist (
        asset_id WITH =,
        cash_flow_type WITH =,
        daterange(valid_from, COALESCE(valid_to, 'infinity'::date), '[]') WITH &&
    );

ALTER TABLE investory.long_term_asset_valuation_periods
    ADD CONSTRAINT ex_long_term_asset_valuation_period
    EXCLUDE USING gist (
        asset_id WITH =,
        daterange(valid_from, COALESCE(valid_to, 'infinity'::date), '[]') WITH &&
    );

ALTER TABLE investory.long_term_asset_bond_rate_periods
    ADD CONSTRAINT ex_long_term_asset_bond_rate_period
    EXCLUDE USING gist (
        asset_id WITH =,
        daterange(valid_from, COALESCE(valid_to, 'infinity'::date), '[]') WITH &&
    );

ALTER TABLE investory.rental_tax_policies
    ADD CONSTRAINT ex_rental_tax_policy_period
    EXCLUDE USING gist (
        portfolio_id WITH =,
        daterange(valid_from, COALESCE(valid_to, 'infinity'::date), '[]') WITH &&
    );
