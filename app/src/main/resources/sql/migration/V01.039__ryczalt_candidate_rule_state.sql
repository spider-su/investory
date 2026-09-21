ALTER TABLE investory.ryczalt_invoice_candidate
    ADD COLUMN rule_match_status VARCHAR(16) NOT NULL DEFAULT 'NO_MATCH';

ALTER TABLE investory.ryczalt_invoice_candidate
    ADD CONSTRAINT chk_ryczalt_candidate_rule_match_status
        CHECK (rule_match_status IN ('MATCHED', 'NO_MATCH', 'AMBIGUOUS'));
