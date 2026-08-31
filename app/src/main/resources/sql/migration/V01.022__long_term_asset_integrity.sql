SET search_path TO investory, public;

-- Released data may contain open deposits. Keep them deterministic before making the
-- subtype workflow complete: an omitted maturity is no longer valid for new or existing rows.
UPDATE investory.long_term_asset_deposit_details d
SET maturity_date = COALESCE(a.acquisition_date, DATE '2099-12-31')
FROM investory.long_term_assets a
WHERE a.id = d.asset_id
  AND d.maturity_date IS NULL;

ALTER TABLE investory.long_term_asset_deposit_details
    ALTER COLUMN maturity_date SET NOT NULL;

ALTER TABLE investory.long_term_asset_valuation_periods
    ADD CONSTRAINT ck_long_term_asset_valuation_growth
    CHECK (expected_annual_growth_rate >= -1 AND expected_annual_growth_rate <= 1)
    NOT VALID;
ALTER TABLE investory.long_term_asset_valuation_periods
    VALIDATE CONSTRAINT ck_long_term_asset_valuation_growth;

CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE investory.long_term_asset_valuation_periods
    ADD CONSTRAINT ex_long_term_asset_valuation_periods_no_overlap
    EXCLUDE USING gist (
      asset_id WITH =,
      daterange(valid_from, COALESCE(valid_to + 1, 'infinity'::date), '[)') WITH &&
    );
ALTER TABLE investory.long_term_asset_bond_rate_periods
    ADD CONSTRAINT ex_long_term_asset_bond_rate_periods_no_overlap
    EXCLUDE USING gist (
      asset_id WITH =,
      daterange(valid_from, COALESCE(valid_to + 1, 'infinity'::date), '[)') WITH &&
    );

CREATE OR REPLACE FUNCTION investory.assert_long_term_subtype_consistency()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE actual_type varchar(32);
BEGIN
  SELECT asset_type INTO actual_type FROM investory.long_term_assets WHERE id = NEW.asset_id;
  IF actual_type IS NULL OR (TG_TABLE_NAME = 'long_term_asset_bond_details' AND actual_type <> 'BOND')
     OR (TG_TABLE_NAME = 'long_term_asset_deposit_details' AND actual_type <> 'DEPOSIT') THEN
    RAISE EXCEPTION 'Subtype details do not match asset type';
  END IF;
  RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS tr_long_term_bond_details_type ON investory.long_term_asset_bond_details;
CREATE TRIGGER tr_long_term_bond_details_type
BEFORE INSERT OR UPDATE ON investory.long_term_asset_bond_details
FOR EACH ROW EXECUTE FUNCTION investory.assert_long_term_subtype_consistency();
DROP TRIGGER IF EXISTS tr_long_term_deposit_details_type ON investory.long_term_asset_deposit_details;
CREATE TRIGGER tr_long_term_deposit_details_type
BEFORE INSERT OR UPDATE ON investory.long_term_asset_deposit_details
FOR EACH ROW EXECUTE FUNCTION investory.assert_long_term_subtype_consistency();

COMMENT ON TABLE investory.long_term_asset_deposit_details IS
  'Complete deposit subtype state. Maturity is required and deposit creation persists this row atomically with the asset.';
