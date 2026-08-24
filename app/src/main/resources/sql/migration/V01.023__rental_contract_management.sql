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

-- Released schemas allowed duplicate term types. Only economically compatible rows can be
-- normalized without losing their frequency or payer meaning.
DO $$
DECLARE
    incompatible_contract_id bigint;
    incompatible_type varchar(32);
BEGIN
    SELECT contract_id, cash_flow_type
    INTO incompatible_contract_id, incompatible_type
    FROM investory.long_term_asset_rental_contract_terms
    GROUP BY contract_id, cash_flow_type
    HAVING count(*) > 1
       AND (count(DISTINCT frequency) > 1 OR count(DISTINCT paid_by_tenant) > 1)
    ORDER BY contract_id, cash_flow_type
    LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION
            'Cannot normalize duplicate rental contract terms for contract % and type %: frequency or payer differs',
            incompatible_contract_id,
            incompatible_type;
    END IF;
END
$$;

WITH normalized_terms AS (
    SELECT min(id) AS retained_id,
           sum(amount) AS merged_amount
    FROM investory.long_term_asset_rental_contract_terms
    GROUP BY contract_id, cash_flow_type
    HAVING count(*) > 1
)
UPDATE investory.long_term_asset_rental_contract_terms term
SET amount = normalized.merged_amount
FROM normalized_terms normalized
WHERE term.id = normalized.retained_id;

WITH duplicate_terms AS (
    SELECT id,
           min(id) OVER (PARTITION BY contract_id, cash_flow_type) AS retained_id
    FROM investory.long_term_asset_rental_contract_terms
)
DELETE FROM investory.long_term_asset_rental_contract_terms term
USING duplicate_terms duplicate
WHERE term.id = duplicate.id
  AND duplicate.id <> duplicate.retained_id;

ALTER TABLE investory.long_term_asset_rental_contract_terms
    ADD CONSTRAINT ux_rental_contract_term_type
        UNIQUE (contract_id, cash_flow_type);

COMMENT ON COLUMN investory.long_term_asset_rental_contracts.tenant_name IS
    'Optional tenant display name owned by the rental contract.';
COMMENT ON COLUMN investory.long_term_asset_rental_contracts.tenant_email IS
    'Optional tenant email owned by the rental contract.';
COMMENT ON COLUMN investory.long_term_asset_rental_contracts.tenant_phone IS
    'Optional tenant phone owned by the rental contract.';
