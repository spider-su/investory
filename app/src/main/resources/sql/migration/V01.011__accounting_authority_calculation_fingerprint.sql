-- Authority evidence is valid only for the calculation that produced the filing.
ALTER TABLE investory.accounting_authority_confirmation
    ADD COLUMN IF NOT EXISTS calculation_hash VARCHAR(64);

CREATE INDEX IF NOT EXISTS ix_accounting_authority_confirmation_calculation
    ON investory.accounting_authority_confirmation(tax_period, confirmation_type, calculation_hash);
