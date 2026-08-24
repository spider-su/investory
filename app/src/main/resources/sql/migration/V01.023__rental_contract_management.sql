SET search_path TO investory, public;

ALTER TABLE investory.long_term_asset_rental_contracts
    ADD COLUMN tenant_name varchar(200),
    ADD COLUMN tenant_email varchar(320),
    ADD COLUMN tenant_phone varchar(50);

ALTER TABLE investory.long_term_asset_rental_contracts
    ADD CONSTRAINT ck_rental_contract_tenant_name_length
        CHECK (tenant_name IS NULL OR char_length(tenant_name) <= 200),
    ADD CONSTRAINT ck_rental_contract_tenant_email_length
        CHECK (tenant_email IS NULL OR char_length(tenant_email) <= 320),
    ADD CONSTRAINT ck_rental_contract_tenant_phone_length
        CHECK (tenant_phone IS NULL OR char_length(tenant_phone) <= 50);

-- Released schemas allowed duplicate term types. Keep the oldest row deterministically
-- before enforcing the contract-level invariant.
WITH duplicate_terms AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY contract_id, cash_flow_type
               ORDER BY id
           ) AS duplicate_rank
    FROM investory.long_term_asset_rental_contract_terms
)
DELETE FROM investory.long_term_asset_rental_contract_terms term
USING duplicate_terms duplicate
WHERE term.id = duplicate.id
  AND duplicate.duplicate_rank > 1;

ALTER TABLE investory.long_term_asset_rental_contract_terms
    ADD CONSTRAINT uk_rental_contract_term_type
        UNIQUE (contract_id, cash_flow_type);

COMMENT ON COLUMN investory.long_term_asset_rental_contracts.tenant_name IS
    'Optional tenant display name owned by the rental contract.';
COMMENT ON COLUMN investory.long_term_asset_rental_contracts.tenant_email IS
    'Optional tenant email owned by the rental contract.';
COMMENT ON COLUMN investory.long_term_asset_rental_contracts.tenant_phone IS
    'Optional tenant phone owned by the rental contract.';
