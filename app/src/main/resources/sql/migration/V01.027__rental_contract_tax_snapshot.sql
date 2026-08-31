ALTER TABLE investory.long_term_asset_rental_contracts
    ADD COLUMN monthly_tax_base NUMERIC(20, 2);

UPDATE investory.long_term_asset_rental_contracts c
SET monthly_tax_base = a.tax_base,
    rental_tax_paid_by_tenant = COALESCE(c.rental_tax_paid_by_tenant, a.rental_tax_paid_by_tenant)
FROM investory.long_term_assets a
WHERE a.id = c.asset_id;

ALTER TABLE investory.long_term_asset_rental_contracts
    ADD CONSTRAINT ck_rental_contract_monthly_tax_base_non_negative
        CHECK (monthly_tax_base IS NULL OR monthly_tax_base >= 0);

COMMENT ON COLUMN investory.long_term_asset_rental_contracts.monthly_tax_base IS
    'Monthly rental-tax base captured for this contract. Prevents later property-default edits from rewriting historical tax.';

COMMENT ON COLUMN investory.long_term_asset_rental_contracts.rental_tax_paid_by_tenant IS
    'Rental-tax ownership captured for this contract. NULL is retained only for legacy compatibility.';
