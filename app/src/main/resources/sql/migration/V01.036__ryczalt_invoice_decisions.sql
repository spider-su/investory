ALTER TABLE investory.ryczalt_invoice
    ADD COLUMN IF NOT EXISTS classification VARCHAR(128),
    ADD COLUMN IF NOT EXISTS vat_treatment VARCHAR(128),
    ADD COLUMN IF NOT EXISTS vat_deduction_ratio NUMERIC(7,6),
    ADD COLUMN IF NOT EXISTS payment_status VARCHAR(16) NOT NULL DEFAULT 'UNMATCHED';
