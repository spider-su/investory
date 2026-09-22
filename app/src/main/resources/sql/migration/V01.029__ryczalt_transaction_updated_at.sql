ALTER TABLE investory.ryczalt_transaction
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

COMMENT ON COLUMN investory.ryczalt_transaction.updated_at IS
    'Last persistence update timestamp used by the canonical Ryczalt entity lifecycle.';
