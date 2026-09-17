ALTER TABLE investory.accounting_known_counterparty
    ADD COLUMN IF NOT EXISTS alias VARCHAR(128);

COMMENT ON COLUMN investory.accounting_known_counterparty.alias IS
    'Optional user-facing name; canonical_name remains the stable source identity.';
