ALTER TABLE investory.accounting_poc_period_state
    ADD COLUMN lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'OPEN';
