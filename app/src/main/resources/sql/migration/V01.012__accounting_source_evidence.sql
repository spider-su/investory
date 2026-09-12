CREATE TABLE investory.accounting_source_evidence (
    id BIGSERIAL PRIMARY KEY,
    source_type VARCHAR(16) NOT NULL,
    external_reference VARCHAR(256) NOT NULL,
    original_filename VARCHAR(512),
    content_type VARCHAR(128),
    received_at TIMESTAMPTZ NOT NULL,
    document_date DATE,
    content_hash BYTEA NOT NULL,
    payload BYTEA NOT NULL,
    processing_status VARCHAR(32) NOT NULL,
    processing_error VARCHAR(1000),
    UNIQUE (source_type, external_reference),
    CONSTRAINT chk_accounting_source_type CHECK (source_type IN ('KSEF', 'UPLOAD')),
    CONSTRAINT chk_accounting_source_status CHECK (processing_status IN ('RECEIVED', 'PARSED', 'REVIEW_REQUIRED', 'IMPORTED', 'FAILED'))
);

COMMENT ON TABLE investory.accounting_source_evidence IS
    'Immutable production source payloads retained separately from normalized accounting facts.';

CREATE FUNCTION investory.prevent_accounting_source_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.source_type IS DISTINCT FROM OLD.source_type
       OR NEW.external_reference IS DISTINCT FROM OLD.external_reference
       OR NEW.original_filename IS DISTINCT FROM OLD.original_filename
       OR NEW.content_type IS DISTINCT FROM OLD.content_type
       OR NEW.received_at IS DISTINCT FROM OLD.received_at
       OR NEW.document_date IS DISTINCT FROM OLD.document_date
       OR NEW.content_hash IS DISTINCT FROM OLD.content_hash
       OR NEW.payload IS DISTINCT FROM OLD.payload
    THEN
        RAISE EXCEPTION 'Accounting source evidence is immutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_accounting_source_immutable
BEFORE UPDATE ON investory.accounting_source_evidence
FOR EACH ROW EXECUTE FUNCTION investory.prevent_accounting_source_mutation();
