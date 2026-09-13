-- Preserve the source bank row's obligation period separately from the month used
-- to display and navigate the staging queue.
ALTER TABLE investory.accounting_tmp_bank_transaction
    ADD COLUMN IF NOT EXISTS related_period DATE;
