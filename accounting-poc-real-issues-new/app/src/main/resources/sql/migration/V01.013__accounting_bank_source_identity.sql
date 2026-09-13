ALTER TABLE investory.accounting_poc_bank_transaction
    ADD COLUMN external_transaction_id VARCHAR(128),
    ADD COLUMN source_row_identity VARCHAR(128);

CREATE OR REPLACE FUNCTION investory.accounting_poc_bank_source_identity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.source_row_identity := COALESCE(
        NEW.external_transaction_id,
        md5(format('%s|%s|%s|%s|%s|%s|%s|%s|%s',
            NEW.booking_date, NEW.related_period, NEW.reference,
            NEW.counterparty_alias, NEW.currency, NEW.amount,
            NEW.transaction_type, NEW.scope, NEW.note)));
    RETURN NEW;
END;
$$;

UPDATE investory.accounting_poc_bank_transaction
   SET source_row_identity = COALESCE(
       external_transaction_id,
       md5(format('%s|%s|%s|%s|%s|%s|%s|%s|%s',
           booking_date, related_period, reference, counterparty_alias,
           currency, amount, transaction_type, scope, note)));

CREATE TRIGGER trg_accounting_poc_bank_source_identity
    BEFORE INSERT OR UPDATE OF external_transaction_id, booking_date, related_period,
        reference, counterparty_alias, currency, amount, transaction_type, scope, note
    ON investory.accounting_poc_bank_transaction
    FOR EACH ROW EXECUTE FUNCTION investory.accounting_poc_bank_source_identity();

ALTER TABLE investory.accounting_poc_bank_transaction
    ALTER COLUMN source_row_identity SET NOT NULL;

CREATE UNIQUE INDEX uq_accounting_poc_bank_source_identity
    ON investory.accounting_poc_bank_transaction (source_row_identity);

COMMENT ON COLUMN investory.accounting_poc_bank_transaction.external_transaction_id IS
    'Provider transaction identifier when supplied; nullable because some bank exports omit it.';
COMMENT ON COLUMN investory.accounting_poc_bank_transaction.source_row_identity IS
    'Stable idempotency key: provider id when available, otherwise a deterministic hash of source fields.';
