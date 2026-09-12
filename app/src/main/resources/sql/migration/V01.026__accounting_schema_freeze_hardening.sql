-- Operational taxpayer identity belongs to the existing portfolio/profile owner.
ALTER TABLE investory.portfolios
    ADD COLUMN IF NOT EXISTS taxpayer_nip VARCHAR(10),
    ADD COLUMN IF NOT EXISTS taxpayer_full_name VARCHAR(240),
    ADD COLUMN IF NOT EXISTS taxpayer_first_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS taxpayer_surname VARCHAR(160),
    ADD COLUMN IF NOT EXISTS taxpayer_date_of_birth DATE,
    ADD COLUMN IF NOT EXISTS taxpayer_tax_office_code VARCHAR(4),
    ADD COLUMN IF NOT EXISTS taxpayer_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS tax_micro_account VARCHAR(34),
    ADD COLUMN IF NOT EXISTS zus_payment_account VARCHAR(34);

UPDATE investory.portfolios p
   SET taxpayer_nip = COALESCE(p.taxpayer_nip, legacy.nip),
       taxpayer_full_name = COALESCE(p.taxpayer_full_name, legacy.full_name),
       taxpayer_first_name = COALESCE(p.taxpayer_first_name, legacy.first_name),
       taxpayer_surname = COALESCE(p.taxpayer_surname, legacy.surname),
       taxpayer_date_of_birth = COALESCE(p.taxpayer_date_of_birth, legacy.date_of_birth),
       taxpayer_tax_office_code = COALESCE(p.taxpayer_tax_office_code, legacy.tax_office_code),
       taxpayer_email = COALESCE(p.taxpayer_email, legacy.email),
       tax_micro_account = COALESCE(p.tax_micro_account, legacy.vat_payment_account, legacy.ryczalt_payment_account),
       zus_payment_account = COALESCE(p.zus_payment_account, legacy.zus_payment_account)
  FROM investory.accounting_poc_profile legacy
 WHERE p.id = 1 AND legacy.id = 1;

COMMENT ON COLUMN investory.portfolios.tax_micro_account IS
    'Operational taxpayer tax micro-account used for VAT and ryczalt/PPE payments.';
COMMENT ON COLUMN investory.portfolios.zus_payment_account IS
    'Operational taxpayer ZUS/NRS payment account.';

-- Operational state/evidence is attributable without changing the single-profile POC shape.
ALTER TABLE investory.accounting_poc_period_state
    ADD COLUMN IF NOT EXISTS profile_id BIGINT REFERENCES investory.portfolios(id);
ALTER TABLE investory.accounting_filing_artifact
    ADD COLUMN IF NOT EXISTS profile_id BIGINT REFERENCES investory.portfolios(id),
    ADD COLUMN IF NOT EXISTS calculation_hash VARCHAR(64);
ALTER TABLE investory.accounting_authority_confirmation
    ADD COLUMN IF NOT EXISTS profile_id BIGINT REFERENCES investory.portfolios(id);
ALTER TABLE investory.accounting_vat_transaction
    ADD COLUMN IF NOT EXISTS profile_id BIGINT REFERENCES investory.portfolios(id),
    ADD COLUMN IF NOT EXISTS source_id BIGINT REFERENCES investory.accounting_source_evidence(id),
    ADD COLUMN IF NOT EXISTS invoice_id BIGINT REFERENCES investory.accounting_poc_invoice(id),
    ADD COLUMN IF NOT EXISTS expense_invoice_id BIGINT REFERENCES investory.accounting_poc_expense_invoice(id);

-- Existing free-text source_document_id remains as historical display/audit text. New normalized
-- rows can use the nullable relational links; at least one provenance identity is always required.
ALTER TABLE investory.accounting_vat_transaction
    ADD CONSTRAINT chk_accounting_vat_transaction_provenance
    CHECK (source_id IS NOT NULL OR source_document_id IS NOT NULL);

ALTER TABLE investory.accounting_vat_transaction
    ADD CONSTRAINT chk_accounting_vat_transaction_direction
    CHECK (direction IN ('SALE', 'PURCHASE'));
ALTER TABLE investory.accounting_vat_transaction
    ADD CONSTRAINT chk_accounting_vat_transaction_treatment
    CHECK (treatment IN ('DOMESTIC_VAT', 'EU_B2B_REVERSE_CHARGE', 'NON_EU_B2B_OUTSIDE_POLAND',
                         'VAT_EXEMPT', 'DOMESTIC_PURCHASE', 'IMPORT_OF_SERVICES_EU',
                         'IMPORT_OF_SERVICES_NON_EU'));
ALTER TABLE investory.accounting_vat_transaction
    ADD CONSTRAINT chk_accounting_vat_transaction_identifier_type
    CHECK (identifier_type IS NULL OR identifier_type IN ('NIP', 'VAT_EU', 'NONE'));

-- Inclusive Java date ranges are represented as half-open PostgreSQL ranges by adding one day.
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE investory.accounting_tax_profile_period
    ADD CONSTRAINT ex_accounting_tax_profile_period_no_overlap
    EXCLUDE USING gist (
        profile_id WITH =,
        daterange(valid_from, COALESCE(valid_to + 1, 'infinity'::date), '[)') WITH &&
    );
ALTER TABLE investory.employment_period
    ADD CONSTRAINT ex_employment_period_same_type_no_overlap
    EXCLUDE USING gist (
        profile_id WITH =,
        employment_type WITH =,
        daterange(date_from, COALESCE(date_to + 1, 'infinity'::date), '[)') WITH &&
    );

-- Raw evidence can change processing state, but payload and identity cannot be deleted or altered.
CREATE OR REPLACE FUNCTION investory.prevent_accounting_source_delete()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Accounting source evidence cannot be deleted';
END;
$$;
CREATE TRIGGER trg_accounting_source_no_delete
BEFORE DELETE ON investory.accounting_source_evidence
FOR EACH ROW EXECUTE FUNCTION investory.prevent_accounting_source_delete();

ALTER TABLE investory.accounting_authority_confirmation
    ADD CONSTRAINT uq_accounting_authority_confirmation_identity
    UNIQUE (authority, tax_period, confirmation_type, external_reference);
ALTER TABLE investory.accounting_authority_confirmation
    ADD CONSTRAINT fk_accounting_authority_confirmation_source
    FOREIGN KEY (source_document_id) REFERENCES investory.accounting_source_evidence(id);

ALTER TABLE investory.accounting_filing_artifact
    ADD CONSTRAINT chk_accounting_filing_artifact_status
    CHECK (status IN ('DRAFT', 'VALID', 'SUBMITTED', 'ACCEPTED', 'REJECTED'));
ALTER TABLE investory.accounting_filing_artifact
    ADD CONSTRAINT chk_accounting_filing_artifact_hash
    CHECK (length(payload_hash) BETWEEN 1 AND 64);
ALTER TABLE investory.accounting_poc_period_state
    ADD CONSTRAINT chk_accounting_period_lifecycle_status
    CHECK (lifecycle_status IN ('OPEN', 'SOURCES_INCOMPLETE', 'READY_FOR_REVIEW', 'ISSUES',
                                'CONFIRMED', 'FILED', 'PAID', 'SETTLED', 'LOCKED'));
ALTER TABLE investory.accounting_poc_period_state
    ADD CONSTRAINT chk_accounting_period_reopen_reason
    CHECK (reopened_at IS NULL OR (reopen_reason IS NOT NULL AND length(btrim(reopen_reason)) > 0));

COMMENT ON TABLE investory.accounting_source_evidence IS
    'Operational immutable source payloads; processing status may change, raw identity and deletion may not.';
COMMENT ON TABLE investory.accounting_poc_profile IS
    'Historical compatibility assumptions only; operational taxpayer identity is portfolio-owned.';
COMMENT ON TABLE investory.accounting_poc_tax_input IS
    'Historical/golden calculation inputs retained for compatibility, not new operational ingestion.';
COMMENT ON TABLE investory.accounting_poc_obligation IS
    'Historical/golden obligation rows retained for compatibility, not new operational ingestion.';
COMMENT ON TABLE investory.accounting_filing_artifact IS
    'Operational filing output audit record linked to its calculation fingerprint and authority evidence.';
COMMENT ON TABLE investory.accounting_authority_confirmation IS
    'Operational imported authority evidence with idempotent external identity.';
