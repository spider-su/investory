ALTER TABLE investory.accounting_poc_period_state
    ADD COLUMN reopened_at TIMESTAMPTZ,
    ADD COLUMN reopen_reason VARCHAR(1000);
