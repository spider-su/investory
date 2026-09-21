ALTER TABLE investory.ryczalt_invoice_candidate
    DROP CONSTRAINT IF EXISTS ryczalt_invoice_candidate_counterparty_id_fkey,
    ADD CONSTRAINT fk_ryczalt_candidate_profile_counterparty
        FOREIGN KEY (profile_id, counterparty_id)
        REFERENCES investory.ryczalt_counterparty (profile_id, id);

ALTER TABLE investory.ryczalt_invoice_candidate
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
