SET search_path TO investory, public;

-- V01.023 was released once with the legacy constraint name. Existing databases may
-- already have that migration applied before its checksum was corrected.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'investory.long_term_asset_rental_contract_terms'::regclass
          AND conname = 'uk_rental_contract_term_type'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'investory.long_term_asset_rental_contract_terms'::regclass
          AND conname = 'ux_rental_contract_term_type'
    ) THEN
        ALTER TABLE investory.long_term_asset_rental_contract_terms
            RENAME CONSTRAINT uk_rental_contract_term_type TO ux_rental_contract_term_type;
    END IF;
END
$$;
