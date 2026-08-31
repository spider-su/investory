-- Repairs databases where V01.018 was applied before the nullable tax-ownership column
-- was present. Do not edit V01.018: it is already released.
ALTER TABLE investory.long_term_asset_rental_contracts
    ADD COLUMN IF NOT EXISTS rental_tax_paid_by_tenant boolean;

ALTER TABLE investory.long_term_asset_rental_contracts
    DROP CONSTRAINT IF EXISTS ck_rental_contract_dates;

ALTER TABLE investory.long_term_asset_rental_contracts
    ADD CONSTRAINT ck_rental_contract_dates
    CHECK (end_date IS NULL OR end_date >= start_date)
    NOT VALID;

ALTER TABLE investory.long_term_asset_rental_contracts
    DROP CONSTRAINT IF EXISTS ck_rental_contract_termination;

ALTER TABLE investory.long_term_asset_rental_contracts
    ADD CONSTRAINT ck_rental_contract_termination
    CHECK (terminated_date IS NULL OR terminated_date >= start_date)
    NOT VALID;

CREATE INDEX IF NOT EXISTS ix_rental_contracts_asset_start
    ON investory.long_term_asset_rental_contracts (asset_id, start_date DESC);
