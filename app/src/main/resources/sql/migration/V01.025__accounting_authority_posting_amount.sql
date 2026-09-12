ALTER TABLE investory.accounting_authority_confirmation
    ADD COLUMN amount NUMERIC(19, 2);

COMMENT ON COLUMN investory.accounting_authority_confirmation.amount IS
    'Authority-posted obligation amount used for settlement reconciliation; null for non-monetary confirmations.';
