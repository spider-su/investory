ALTER TABLE investory.ryczalt_counterparty
    ADD CONSTRAINT uq_ryczalt_counterparty_profile_id UNIQUE (profile_id, id);

ALTER TABLE investory.ryczalt_counterparty_rule
    DROP CONSTRAINT IF EXISTS ryczalt_counterparty_rule_counterparty_id_fkey,
    ADD CONSTRAINT fk_ryczalt_rule_profile_counterparty
        FOREIGN KEY (profile_id, counterparty_id)
        REFERENCES investory.ryczalt_counterparty (profile_id, id);

ALTER TABLE investory.ryczalt_invoice
    DROP CONSTRAINT IF EXISTS ryczalt_invoice_counterparty_id_fkey,
    ADD CONSTRAINT fk_ryczalt_invoice_profile_counterparty
        FOREIGN KEY (profile_id, counterparty_id)
        REFERENCES investory.ryczalt_counterparty (profile_id, id);

ALTER TABLE investory.ryczalt_invoice
    ADD CONSTRAINT chk_ryczalt_invoice_payment_status
        CHECK (payment_status IN ('MATCHED', 'PARTIALLY_MATCHED', 'UNMATCHED', 'NOT_REQUIRED'));
