-- Staging must retain every reviewed fact required for safe canonical promotion.
ALTER TABLE investory.accounting_tmp_invoice
    ADD COLUMN category VARCHAR(64),
    ADD COLUMN source_quality VARCHAR(32),
    ADD COLUMN corrects_document_reference VARCHAR(128);
