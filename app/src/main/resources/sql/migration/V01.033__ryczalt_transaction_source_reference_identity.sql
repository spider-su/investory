-- Native Ryczalt bank acquisition requires that distinct source transactions sharing a
-- human-visible reference remain distinct canonical rows. The provider identity lives in
-- ryczalt_source_reference (UNIQUE profile_id, entity_type, source, external_id), which is the
-- real idempotency boundary. Drop the reference-based uniqueness and keep a plain lookup index.
ALTER TABLE investory.ryczalt_transaction
    DROP CONSTRAINT IF EXISTS uq_ryczalt_transaction_source;

CREATE INDEX IF NOT EXISTS ix_ryczalt_transaction_reference
    ON investory.ryczalt_transaction(profile_id, reference);
