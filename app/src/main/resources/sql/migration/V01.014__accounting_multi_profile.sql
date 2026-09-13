-- Accounting data is owned by the application profile (the existing portfolio
-- identity used by profile_memberships).  This migration keeps trusted legacy
-- rows attached to profile 1 and makes every operational Accounting row tenant
-- explicit.  Reference/golden tables remain separate and are not used for
-- operational calculations.

ALTER TABLE investory.accounting_poc_fact
    ADD COLUMN IF NOT EXISTS profile_id BIGINT REFERENCES investory.portfolios(id);
ALTER TABLE investory.accounting_poc_profile
    ADD COLUMN IF NOT EXISTS profile_id BIGINT REFERENCES investory.portfolios(id);
UPDATE investory.accounting_poc_profile SET profile_id = COALESCE(profile_id, 1);
ALTER TABLE investory.accounting_poc_profile ALTER COLUMN profile_id SET NOT NULL;
ALTER TABLE investory.accounting_source_evidence
    ADD COLUMN IF NOT EXISTS profile_id BIGINT REFERENCES investory.portfolios(id);
ALTER TABLE investory.accounting_poc_obligation
    ADD COLUMN IF NOT EXISTS profile_id BIGINT REFERENCES investory.portfolios(id);
ALTER TABLE investory.accounting_poc_tax_input
    ADD COLUMN IF NOT EXISTS profile_id BIGINT REFERENCES investory.portfolios(id);
ALTER TABLE investory.accounting_poc_period_state
    ADD COLUMN IF NOT EXISTS profile_id BIGINT REFERENCES investory.portfolios(id);
ALTER TABLE investory.accounting_filing_artifact
    ADD COLUMN IF NOT EXISTS profile_id BIGINT REFERENCES investory.portfolios(id);
ALTER TABLE investory.accounting_authority_confirmation
    ADD COLUMN IF NOT EXISTS profile_id BIGINT REFERENCES investory.portfolios(id);
ALTER TABLE investory.accounting_vat_transaction
    ADD COLUMN IF NOT EXISTS profile_id BIGINT REFERENCES investory.portfolios(id);

UPDATE investory.accounting_poc_fact SET profile_id = COALESCE(profile_id, 1);
UPDATE investory.accounting_source_evidence SET profile_id = COALESCE(profile_id, 1);
UPDATE investory.accounting_poc_obligation SET profile_id = COALESCE(profile_id, 1);
UPDATE investory.accounting_poc_tax_input SET profile_id = COALESCE(profile_id, 1);
UPDATE investory.accounting_poc_period_state SET profile_id = COALESCE(profile_id, 1);
UPDATE investory.accounting_filing_artifact SET profile_id = COALESCE(profile_id, 1);
UPDATE investory.accounting_authority_confirmation SET profile_id = COALESCE(profile_id, 1);
UPDATE investory.accounting_vat_transaction SET profile_id = COALESCE(profile_id, 1);

ALTER TABLE investory.accounting_poc_fact ALTER COLUMN profile_id SET NOT NULL;
ALTER TABLE investory.accounting_source_evidence ALTER COLUMN profile_id SET NOT NULL;
ALTER TABLE investory.accounting_poc_obligation ALTER COLUMN profile_id SET NOT NULL;
ALTER TABLE investory.accounting_poc_tax_input ALTER COLUMN profile_id SET NOT NULL;
ALTER TABLE investory.accounting_poc_period_state ALTER COLUMN profile_id SET NOT NULL;
ALTER TABLE investory.accounting_filing_artifact ALTER COLUMN profile_id SET NOT NULL;
ALTER TABLE investory.accounting_authority_confirmation ALTER COLUMN profile_id SET NOT NULL;
ALTER TABLE investory.accounting_vat_transaction ALTER COLUMN profile_id SET NOT NULL;

ALTER TABLE investory.accounting_source_evidence
    DROP CONSTRAINT IF EXISTS accounting_source_evidence_source_type_external_reference_key;
CREATE UNIQUE INDEX IF NOT EXISTS uq_accounting_source_evidence_profile_reference
    ON investory.accounting_source_evidence(profile_id, source_type, external_reference);

ALTER TABLE investory.accounting_poc_obligation
    DROP CONSTRAINT IF EXISTS accounting_poc_obligation_tax_period_obligation_type_key;
CREATE UNIQUE INDEX IF NOT EXISTS uq_accounting_poc_obligation_profile_period_type
    ON investory.accounting_poc_obligation(profile_id, tax_period, obligation_type);
ALTER TABLE investory.accounting_poc_tax_input
    DROP CONSTRAINT IF EXISTS accounting_poc_tax_input_tax_period_input_type_key;
CREATE UNIQUE INDEX IF NOT EXISTS uq_accounting_poc_tax_input_profile_period_type
    ON investory.accounting_poc_tax_input(profile_id, tax_period, input_type);
ALTER TABLE investory.accounting_poc_period_state
    DROP CONSTRAINT IF EXISTS accounting_poc_period_state_pkey;
ALTER TABLE investory.accounting_poc_period_state
    ADD CONSTRAINT pk_accounting_poc_period_state PRIMARY KEY (profile_id, tax_period);
ALTER TABLE investory.accounting_filing_artifact
    DROP CONSTRAINT IF EXISTS uq_accounting_filing_artifact_hash;
CREATE UNIQUE INDEX IF NOT EXISTS uq_accounting_filing_artifact_profile_hash
    ON investory.accounting_filing_artifact(profile_id, artifact_type, tax_period, payload_hash);
ALTER TABLE investory.accounting_authority_confirmation
    DROP CONSTRAINT IF EXISTS uq_accounting_authority_confirmation_identity;
CREATE UNIQUE INDEX IF NOT EXISTS uq_accounting_authority_confirmation_profile_identity
    ON investory.accounting_authority_confirmation(profile_id, authority, tax_period, confirmation_type, external_reference);

ALTER TABLE investory.accounting_tmp_invoice
    DROP CONSTRAINT IF EXISTS uq_accounting_tmp_invoice_source_reference;
CREATE UNIQUE INDEX IF NOT EXISTS uq_accounting_tmp_invoice_profile_source_reference
    ON investory.accounting_tmp_invoice(profile_id, source_id, reference);
ALTER TABLE investory.accounting_tmp_bank_transaction
    DROP CONSTRAINT IF EXISTS uq_accounting_tmp_bank_identity;
CREATE UNIQUE INDEX IF NOT EXISTS uq_accounting_tmp_bank_profile_identity
    ON investory.accounting_tmp_bank_transaction(profile_id, source_id, external_transaction_id);

CREATE INDEX IF NOT EXISTS ix_accounting_source_evidence_profile_period
    ON investory.accounting_source_evidence(profile_id, document_date, processing_status, id);
CREATE INDEX IF NOT EXISTS ix_accounting_poc_fact_profile_date
    ON investory.accounting_poc_fact(profile_id, fact_date, id);
CREATE INDEX IF NOT EXISTS ix_accounting_vat_transaction_profile_period
    ON investory.accounting_vat_transaction(profile_id, tax_period, id);
