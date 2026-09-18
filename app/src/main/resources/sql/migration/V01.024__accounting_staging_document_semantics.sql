-- Staging must retain every reviewed fact required for safe canonical promotion.
ALTER TABLE investory.accounting_tmp_invoice
    ADD COLUMN IF NOT EXISTS category VARCHAR(64),
    ADD COLUMN IF NOT EXISTS source_quality VARCHAR(32),
    ADD COLUMN IF NOT EXISTS corrects_document_reference VARCHAR(128);
