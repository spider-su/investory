-- Squashed accounting DDL from V01.010 through V01.024. Statement text preserved.

CREATE TABLE investory.accounting_poc_fact (
    id BIGSERIAL PRIMARY KEY,
    fact_date DATE,
    fact_type VARCHAR(64) NOT NULL,
    reference VARCHAR(128),
    counterparty_alias VARCHAR(128),
    currency CHAR(3) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    tax_rate NUMERIC(7, 4),
    note VARCHAR(512)
);


CREATE INDEX idx_accounting_poc_fact_date
    ON investory.accounting_poc_fact (fact_date DESC, id DESC);


COMMENT ON TABLE investory.accounting_poc_fact IS
    'POC-only anonymized accounting facts. Values are historical fixtures, not calculated tax advice.';


CREATE TABLE investory.accounting_poc_invoice (
    id BIGSERIAL PRIMARY KEY,
    tax_period DATE NOT NULL,
    issue_date DATE,
    sale_date DATE,
    fx_rate_date DATE,
    reference VARCHAR(128) NOT NULL UNIQUE,
    customer_alias VARCHAR(128) NOT NULL,
    invoice_kind VARCHAR(32) NOT NULL,
    currency CHAR(3) NOT NULL,
    net_amount NUMERIC(19, 4) NOT NULL,
    vat_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    gross_amount NUMERIC(19, 4) NOT NULL,
    correction_gross_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    correction_net_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    correction_vat_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    expected_receivable NUMERIC(19, 4) NOT NULL,
    booked_net_pln NUMERIC(19, 4),
    ryczalt_rate NUMERIC(7, 4),
    note VARCHAR(512)
);


CREATE INDEX idx_accounting_poc_invoice_period
    ON investory.accounting_poc_invoice (tax_period, id);


COMMENT ON TABLE investory.accounting_poc_invoice IS
    'POC-only anonymized invoice fixtures used for deterministic reconciliation.';


CREATE TABLE investory.accounting_poc_bank_transaction (
    id BIGSERIAL PRIMARY KEY,
    booking_date DATE NOT NULL,
    related_period DATE,
    reference VARCHAR(128),
    counterparty_alias VARCHAR(128),
    currency CHAR(3) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    scope VARCHAR(32) NOT NULL,
    note VARCHAR(512)
);

CREATE INDEX idx_accounting_poc_bank_related_period ON investory.accounting_poc_bank_transaction (related_period, booking_date, id);

COMMENT ON TABLE investory.accounting_poc_bank_transaction IS 'POC-only anonymized bank fixtures. Private/internal movements are retained but explicitly scoped out.';


CREATE TABLE investory.accounting_poc_obligation (
    id BIGSERIAL PRIMARY KEY,
    tax_period DATE NOT NULL,
    obligation_type VARCHAR(32) NOT NULL,
    due_date DATE,
    expected_amount NUMERIC(19, 4) NOT NULL,
    paid_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    payment_date DATE,
    status VARCHAR(32) NOT NULL,
    note VARCHAR(512),
    UNIQUE (tax_period, obligation_type)
);

CREATE INDEX idx_accounting_poc_obligation_period ON investory.accounting_poc_obligation (tax_period, obligation_type);

COMMENT ON TABLE investory.accounting_poc_obligation IS 'POC-only golden tax/ZUS outputs reconstructed from wFirma and bank evidence; not tax advice.';


CREATE TABLE investory.accounting_poc_tax_input (
    id BIGSERIAL PRIMARY KEY,
    tax_period DATE NOT NULL,
    input_type VARCHAR(64) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    note VARCHAR(512),
    UNIQUE (tax_period, input_type)
);

COMMENT ON TABLE investory.accounting_poc_tax_input IS 'POC-only explicit calculation inputs. Values remain traceable golden/source facts and are not hidden balancing adjustments.';


CREATE TABLE investory.accounting_poc_expense_invoice (
    id BIGSERIAL PRIMARY KEY,
    tax_period DATE NOT NULL,
    invoice_date DATE,
    reference VARCHAR(128) NOT NULL UNIQUE,
    supplier_alias VARCHAR(128) NOT NULL,
    category VARCHAR(64) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'PLN',
    net_amount NUMERIC(19,4) NOT NULL,
    vat_amount NUMERIC(19,4) NOT NULL,
    gross_amount NUMERIC(19,4) NOT NULL,
    vat_deduction_ratio NUMERIC(3,2) NOT NULL,
    source_quality VARCHAR(32) NOT NULL,
    note VARCHAR(512),
    CONSTRAINT chk_accounting_poc_expense_ratio CHECK (vat_deduction_ratio IN (0.00, 0.50, 1.00))
);

CREATE INDEX idx_accounting_poc_expense_period ON investory.accounting_poc_expense_invoice (tax_period, invoice_date, id);

COMMENT ON TABLE investory.accounting_poc_expense_invoice IS 'POC-only anonymized expense documents. Deductible VAT is calculated per document as VAT x configured ratio.';


-- Squashed from app/src/main/resources/sql/migration/V01.011__accounting_poc_uop_zus.sql
CREATE TABLE investory.accounting_poc_profile (
    id SMALLINT PRIMARY KEY,
    has_uop BOOLEAN NOT NULL,
    CONSTRAINT chk_accounting_poc_profile_singleton CHECK (id = 1)
);


COMMENT ON TABLE investory.accounting_poc_profile IS
    'POC-only JDG accounting assumptions shared by all represented months.';

COMMENT ON COLUMN investory.accounting_poc_profile.has_uop IS
    'True means an active UoP meeting the minimum-remuneration condition for exemption from compulsory JDG social contributions.';



-- Squashed from app/src/main/resources/sql/migration/V01.012__accounting_source_evidence.sql
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



-- Squashed from app/src/main/resources/sql/migration/V01.013__accounting_source_provenance.sql
ALTER TABLE investory.accounting_poc_invoice
    ADD COLUMN source_id BIGINT REFERENCES investory.accounting_source_evidence (id);


ALTER TABLE investory.accounting_poc_expense_invoice
    ADD COLUMN source_id BIGINT REFERENCES investory.accounting_source_evidence (id);


CREATE INDEX idx_accounting_poc_invoice_source_id
    ON investory.accounting_poc_invoice (source_id);


CREATE INDEX idx_accounting_poc_expense_source_id
    ON investory.accounting_poc_expense_invoice (source_id);



-- Squashed from app/src/main/resources/sql/migration/V01.014__accounting_bank_ingestion.sql
ALTER TABLE investory.accounting_poc_bank_transaction
    ADD COLUMN source_id BIGINT REFERENCES investory.accounting_source_evidence (id),
    ADD COLUMN source_row_identity VARCHAR(256);


CREATE UNIQUE INDEX uq_accounting_poc_bank_source_row
    ON investory.accounting_poc_bank_transaction (source_row_identity)
    WHERE source_row_identity IS NOT NULL;


ALTER TABLE investory.accounting_source_evidence
    DROP CONSTRAINT chk_accounting_source_type;


ALTER TABLE investory.accounting_source_evidence
    ADD CONSTRAINT chk_accounting_source_type
    CHECK (source_type IN ('KSEF', 'UPLOAD', 'BANK'));


CREATE INDEX idx_accounting_poc_bank_source_id
    ON investory.accounting_poc_bank_transaction (source_id);



-- Squashed from app/src/main/resources/sql/migration/V01.015__accounting_filing_output.sql
ALTER TABLE investory.accounting_poc_profile
    ADD COLUMN nip VARCHAR(10),
    ADD COLUMN full_name VARCHAR(240),
    ADD COLUMN tax_office_code VARCHAR(4),
    ADD COLUMN email VARCHAR(255),
    ADD COLUMN vat_payment_account VARCHAR(34),
    ADD COLUMN ryczalt_payment_account VARCHAR(34),
    ADD COLUMN zus_payment_account VARCHAR(34);


CREATE TABLE investory.accounting_poc_period_state (
    tax_period DATE PRIMARY KEY,
    confirmed_at TIMESTAMPTZ,
    confirmed_calculation_hash VARCHAR(64)
);



-- Squashed from app/src/main/resources/sql/migration/V01.016__accounting_natural_person_filing_identity.sql
ALTER TABLE investory.accounting_poc_profile
    ADD COLUMN first_name VARCHAR(120),
    ADD COLUMN surname VARCHAR(160),
    ADD COLUMN date_of_birth DATE;



-- Squashed from app/src/main/resources/sql/migration/V01.017__employment_periods.sql
CREATE TABLE IF NOT EXISTS investory.employment_period (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    employment_type VARCHAR(8) NOT NULL,
    date_from DATE NOT NULL,
    date_to DATE,
    CONSTRAINT chk_employment_period_type CHECK (employment_type IN ('UOP', 'JDG')),
    CONSTRAINT chk_employment_period_dates CHECK (date_to IS NULL OR date_to >= date_from)
);


CREATE INDEX IF NOT EXISTS ix_employment_period_profile_dates
    ON investory.employment_period(profile_id, date_from, date_to);



-- Squashed from app/src/main/resources/sql/migration/V01.018__accounting_filing_provenance.sql
ALTER TABLE investory.accounting_poc_invoice
    ADD COLUMN counterparty_tax_identifier VARCHAR(32),
    ADD COLUMN counterparty_country VARCHAR(2),
    ADD COLUMN ksef_number VARCHAR(256),
    ADD COLUMN filing_evidence VARCHAR(8);


ALTER TABLE investory.accounting_poc_expense_invoice
    ADD COLUMN counterparty_tax_identifier VARCHAR(32),
    ADD COLUMN counterparty_country VARCHAR(2),
    ADD COLUMN ksef_number VARCHAR(256),
    ADD COLUMN filing_evidence VARCHAR(8);



-- Squashed from app/src/main/resources/sql/migration/V01.019__accounting_tax_profile_periods.sql
CREATE TABLE investory.accounting_tax_profile_period (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    valid_from DATE NOT NULL,
    valid_to DATE,
    jdg_active BOOLEAN NOT NULL,
    ryczalt_rate NUMERIC(8, 5),
    vat_registered BOOLEAN NOT NULL,
    vat_eu_registered BOOLEAN NOT NULL,
    zus_regime VARCHAR(32),
    voluntary_sickness BOOLEAN NOT NULL,
    CONSTRAINT chk_accounting_tax_profile_period_dates CHECK (valid_to IS NULL OR valid_to >= valid_from)
);


CREATE INDEX ix_accounting_tax_profile_period_profile_dates
    ON investory.accounting_tax_profile_period(profile_id, valid_from, valid_to);



-- Squashed from app/src/main/resources/sql/migration/V01.020__accounting_filing_authority_evidence.sql
CREATE TABLE investory.accounting_filing_artifact (
    id BIGSERIAL PRIMARY KEY,
    artifact_type VARCHAR(40) NOT NULL,
    tax_period DATE NOT NULL,
    schema_version VARCHAR(40) NOT NULL,
    payload BYTEA NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL,
    CONSTRAINT uq_accounting_filing_artifact_hash UNIQUE (artifact_type, tax_period, payload_hash)
);


CREATE TABLE investory.accounting_authority_confirmation (
    id BIGSERIAL PRIMARY KEY,
    authority VARCHAR(32) NOT NULL,
    obligation_or_artifact_type VARCHAR(40) NOT NULL,
    tax_period DATE NOT NULL,
    external_reference VARCHAR(256) NOT NULL,
    confirmation_type VARCHAR(40) NOT NULL,
    status VARCHAR(16) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    source_document_id BIGINT,
    note VARCHAR(1000)
);


CREATE INDEX ix_accounting_authority_confirmation_period
    ON investory.accounting_authority_confirmation(tax_period, obligation_or_artifact_type);



-- Squashed from app/src/main/resources/sql/migration/V01.021__accounting_period_lifecycle.sql
ALTER TABLE investory.accounting_poc_period_state
    ADD COLUMN lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'OPEN';



-- Squashed from app/src/main/resources/sql/migration/V01.022__accounting_vat_transactions.sql
CREATE TABLE investory.accounting_vat_transaction (
    id BIGSERIAL PRIMARY KEY,
    tax_period DATE NOT NULL,
    tax_date DATE NOT NULL,
    source_document_id VARCHAR(256) NOT NULL,
    reference VARCHAR(256) NOT NULL,
    direction VARCHAR(16) NOT NULL,
    treatment VARCHAR(48) NOT NULL,
    counterparty_country VARCHAR(2),
    counterparty_tax_identifier VARCHAR(64),
    identifier_type VARCHAR(16),
    vat_eu_number VARCHAR(64),
    vies_verified_at DATE,
    vies_status VARCHAR(24),
    net_amount NUMERIC(18, 2) NOT NULL,
    vat_amount NUMERIC(18, 2) NOT NULL,
    deductible_vat NUMERIC(18, 2) NOT NULL,
    evidence VARCHAR(256) NOT NULL
);


CREATE INDEX ix_accounting_vat_transaction_period
    ON investory.accounting_vat_transaction(tax_period, id);



-- Squashed from app/src/main/resources/sql/migration/V01.023__accounting_period_reopen.sql
ALTER TABLE investory.accounting_poc_period_state
    ADD COLUMN reopened_at TIMESTAMPTZ,
    ADD COLUMN reopen_reason VARCHAR(1000);



-- Squashed from app/src/main/resources/sql/migration/V01.024__provider_neutral_bank_identity.sql
ALTER TABLE investory.accounting_poc_bank_transaction
    ADD COLUMN provider VARCHAR(32),
    ADD COLUMN external_account_id VARCHAR(256),
    ADD COLUMN external_transaction_id VARCHAR(256),
    ADD COLUMN source_payload_hash VARCHAR(128);


ALTER TABLE investory.accounting_poc_bank_transaction
    ALTER COLUMN provider SET NOT NULL,
    ALTER COLUMN external_account_id SET NOT NULL,
    ALTER COLUMN external_transaction_id SET NOT NULL;


CREATE UNIQUE INDEX uq_accounting_poc_bank_external_transaction
    ON investory.accounting_poc_bank_transaction
       (provider, external_account_id, external_transaction_id);


COMMENT ON COLUMN investory.accounting_poc_bank_transaction.provider IS
    'Neutral bank data provider identity; classification remains Accounting-owned.';

COMMENT ON COLUMN investory.accounting_poc_bank_transaction.external_transaction_id IS
    'Provider or deterministic adapter transaction identity used for idempotent ingestion.';



-- Squashed from app/src/main/resources/sql/migration/V01.025__accounting_authority_posting_amount.sql
ALTER TABLE investory.accounting_authority_confirmation
    ADD COLUMN amount NUMERIC(19, 2);


COMMENT ON COLUMN investory.accounting_authority_confirmation.amount IS
    'Authority-posted obligation amount used for settlement reconciliation; null for non-monetary confirmations.';



-- Squashed from app/src/main/resources/sql/migration/V01.026__accounting_schema_freeze_hardening.sql
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

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conrelid = 'investory.employment_period'::regclass
           AND conname = 'ex_employment_period_same_type_no_overlap'
    ) THEN
        ALTER TABLE investory.employment_period
            ADD CONSTRAINT ex_employment_period_same_type_no_overlap
            EXCLUDE USING gist (
                profile_id WITH =,
                employment_type WITH =,
                daterange(date_from, COALESCE(date_to + 1, 'infinity'::date), '[)') WITH &&
            );
    END IF;
END $$;


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



-- Authority evidence is valid only for the calculation that produced the filing.
ALTER TABLE investory.accounting_authority_confirmation
    ADD COLUMN IF NOT EXISTS calculation_hash VARCHAR(64);


CREATE INDEX IF NOT EXISTS ix_accounting_authority_confirmation_calculation
    ON investory.accounting_authority_confirmation(tax_period, confirmation_type, calculation_hash);
-- Canonical Accounting facts are owned by the portfolio that may reconcile them.
-- Existing POC fixtures belong to the original singleton portfolio (1).
ALTER TABLE investory.accounting_poc_invoice
    ADD COLUMN profile_id BIGINT NOT NULL DEFAULT 1
        REFERENCES investory.portfolios (id);


ALTER TABLE investory.accounting_poc_expense_invoice
    ADD COLUMN profile_id BIGINT NOT NULL DEFAULT 1
        REFERENCES investory.portfolios (id);


ALTER TABLE investory.accounting_poc_bank_transaction
    ADD COLUMN profile_id BIGINT NOT NULL DEFAULT 1
        REFERENCES investory.portfolios (id);


ALTER TABLE investory.accounting_poc_invoice
    DROP CONSTRAINT accounting_poc_invoice_reference_key;


ALTER TABLE investory.accounting_poc_expense_invoice
    DROP CONSTRAINT accounting_poc_expense_invoice_reference_key;


DROP INDEX investory.uq_accounting_poc_bank_source_row;

DROP INDEX investory.uq_accounting_poc_bank_external_transaction;


CREATE UNIQUE INDEX uq_accounting_poc_invoice_profile_reference
    ON investory.accounting_poc_invoice (profile_id, reference);


CREATE UNIQUE INDEX uq_accounting_poc_invoice_profile_ksef
    ON investory.accounting_poc_invoice (profile_id, ksef_number)
    WHERE ksef_number IS NOT NULL;


CREATE UNIQUE INDEX uq_accounting_poc_expense_profile_reference
    ON investory.accounting_poc_expense_invoice (profile_id, reference);


CREATE UNIQUE INDEX uq_accounting_poc_expense_profile_ksef
    ON investory.accounting_poc_expense_invoice (profile_id, ksef_number)
    WHERE ksef_number IS NOT NULL;


CREATE UNIQUE INDEX uq_accounting_poc_bank_profile_source_row
    ON investory.accounting_poc_bank_transaction (profile_id, source_row_identity)
    WHERE source_row_identity IS NOT NULL;


CREATE UNIQUE INDEX uq_accounting_poc_bank_profile_external_transaction
    ON investory.accounting_poc_bank_transaction
       (profile_id, provider, external_account_id, external_transaction_id);


CREATE INDEX ix_accounting_poc_invoice_profile_reference
    ON investory.accounting_poc_invoice (profile_id, reference);


CREATE INDEX ix_accounting_poc_expense_profile_reference
    ON investory.accounting_poc_expense_invoice (profile_id, reference);


CREATE INDEX ix_accounting_poc_bank_profile_match
    ON investory.accounting_poc_bank_transaction
       (profile_id, booking_date, amount, currency);


-- Accounting data is owned by the application profile (the existing portfolio
-- identity used by profile_memberships).  This migration keeps trusted legacy
-- rows attached to profile 1 and makes every operational Accounting row tenant
-- explicit.  Reference/golden tables remain separate and are not used for
-- operational calculations.

ALTER TABLE investory.accounting_poc_fact
    ADD COLUMN IF NOT EXISTS profile_id BIGINT REFERENCES investory.portfolios(id);

ALTER TABLE investory.accounting_poc_profile
    ADD COLUMN IF NOT EXISTS profile_id BIGINT REFERENCES investory.portfolios(id);

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




CREATE INDEX IF NOT EXISTS ix_accounting_source_evidence_profile_period
    ON investory.accounting_source_evidence(profile_id, document_date, processing_status, id);

CREATE INDEX IF NOT EXISTS ix_accounting_poc_fact_profile_date
    ON investory.accounting_poc_fact(profile_id, fact_date, id);

CREATE INDEX IF NOT EXISTS ix_accounting_vat_transaction_profile_period
    ON investory.accounting_vat_transaction(profile_id, tax_period, id);
-- Never silently attach a future Accounting write to profile 1.
ALTER TABLE investory.accounting_poc_invoice ALTER COLUMN profile_id DROP DEFAULT;

ALTER TABLE investory.accounting_poc_expense_invoice ALTER COLUMN profile_id DROP DEFAULT;

ALTER TABLE investory.accounting_poc_bank_transaction ALTER COLUMN profile_id DROP DEFAULT;

ALTER TABLE investory.accounting_poc_fact ALTER COLUMN profile_id DROP DEFAULT;

ALTER TABLE investory.accounting_source_evidence ALTER COLUMN profile_id DROP DEFAULT;

ALTER TABLE investory.accounting_poc_obligation ALTER COLUMN profile_id DROP DEFAULT;

ALTER TABLE investory.accounting_poc_tax_input ALTER COLUMN profile_id DROP DEFAULT;

ALTER TABLE investory.accounting_poc_period_state ALTER COLUMN profile_id DROP DEFAULT;

ALTER TABLE investory.accounting_filing_artifact ALTER COLUMN profile_id DROP DEFAULT;

ALTER TABLE investory.accounting_authority_confirmation ALTER COLUMN profile_id DROP DEFAULT;

ALTER TABLE investory.accounting_vat_transaction ALTER COLUMN profile_id DROP DEFAULT;

-- Preserve the optional due date supplied during reviewed-document intake.
ALTER TABLE investory.accounting_poc_invoice
    ADD COLUMN IF NOT EXISTS due_date DATE;


ALTER TABLE investory.accounting_poc_expense_invoice
    ADD COLUMN IF NOT EXISTS due_date DATE;


CREATE TABLE investory.accounting_known_counterparty (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    tax_identifier VARCHAR(64) NOT NULL,
    country VARCHAR(2) NOT NULL,
    canonical_name VARCHAR(256) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT accounting_known_counterparty_key UNIQUE (profile_id, country, tax_identifier)
);


CREATE INDEX ix_accounting_known_counterparty_lookup
    ON investory.accounting_known_counterparty (profile_id, country, tax_identifier);
ALTER TABLE investory.accounting_vat_transaction
    ADD COLUMN vat_rate NUMERIC(5,2);




COMMENT ON COLUMN investory.accounting_vat_transaction.vat_rate IS
    'Explicit normalized domestic VAT rate; never reconstructed from net and VAT amounts.';


-- Canonical reviewed documents. Staging tables remain separate: an ambiguous import is not a
-- valid accounting document and must not share this aggregate's invariants.
CREATE TABLE investory.accounting_document (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    direction VARCHAR(16) NOT NULL,
    document_kind VARCHAR(32) NOT NULL,
    corrects_document_id BIGINT REFERENCES investory.accounting_document(id),
    tax_period DATE NOT NULL,
    issue_date DATE,
    supply_date DATE,
    due_date DATE,
    reference VARCHAR(128) NOT NULL,
    counterparty_id BIGINT REFERENCES investory.accounting_known_counterparty(id),
    counterparty_name VARCHAR(256) NOT NULL,
    counterparty_tax_identifier VARCHAR(64),
    counterparty_country VARCHAR(2),
    currency CHAR(3) NOT NULL,
    net_amount NUMERIC(19,4) NOT NULL,
    vat_amount NUMERIC(19,4) NOT NULL,
    gross_amount NUMERIC(19,4) NOT NULL,
    fx_rate_date DATE,
    booked_net_pln NUMERIC(19,4),
    ryczalt_rate NUMERIC(8,5),
    category VARCHAR(64),
    vat_deduction_ratio NUMERIC(3,2),
    source_quality VARCHAR(32),
    source_id BIGINT REFERENCES investory.accounting_source_evidence(id),
    ksef_number VARCHAR(256),
    filing_evidence VARCHAR(8),
    note VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_accounting_document_direction CHECK (direction IN ('SALE', 'PURCHASE')),
    CONSTRAINT chk_accounting_document_kind CHECK (document_kind IN ('INVOICE', 'CREDIT_NOTE')),
    CONSTRAINT chk_accounting_document_amounts CHECK (net_amount + vat_amount = gross_amount),
    CONSTRAINT chk_accounting_document_sale_fields
        CHECK (direction = 'SALE' OR (fx_rate_date IS NULL AND booked_net_pln IS NULL AND ryczalt_rate IS NULL)),
    CONSTRAINT chk_accounting_document_purchase_fields
        CHECK (direction = 'PURCHASE' OR (category IS NULL AND vat_deduction_ratio IS NULL)),
    CONSTRAINT chk_accounting_document_deduction_ratio
        CHECK (vat_deduction_ratio IS NULL OR vat_deduction_ratio IN (0.00, 0.50, 1.00)),
    CONSTRAINT chk_accounting_document_correction_direction
        CHECK (corrects_document_id IS NULL OR document_kind = 'CREDIT_NOTE'),
    CONSTRAINT uq_accounting_document_profile_direction_reference
        UNIQUE (profile_id, direction, reference)
);

CREATE INDEX ix_accounting_document_profile_period
    ON investory.accounting_document(profile_id, tax_period, id);

CREATE INDEX ix_accounting_document_source
    ON investory.accounting_document(source_id);

CREATE INDEX ix_accounting_document_correction
    ON investory.accounting_document(corrects_document_id)
    WHERE corrects_document_id IS NOT NULL;

CREATE TABLE investory.accounting_document_vat_bucket (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES investory.accounting_document(id) ON DELETE CASCADE,
    treatment VARCHAR(48) NOT NULL,
    vat_rate NUMERIC(5,2),
    net_amount NUMERIC(19,4) NOT NULL,
    vat_amount NUMERIC(19,4) NOT NULL,
    deductible_vat NUMERIC(19,4) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_accounting_document_vat_bucket_treatment
        CHECK (treatment IN ('DOMESTIC_VAT', 'EU_B2B_REVERSE_CHARGE',
                             'NON_EU_B2B_OUTSIDE_POLAND', 'VAT_EXEMPT',
                             'DOMESTIC_PURCHASE', 'IMPORT_OF_SERVICES_EU',
                             'IMPORT_OF_SERVICES_NON_EU')),
    CONSTRAINT chk_accounting_document_vat_bucket_rate
        CHECK (vat_rate IS NULL OR vat_rate IN (0, 5, 8, 23)),
    CONSTRAINT chk_accounting_document_vat_bucket_deductible
        CHECK (deductible_vat >= 0 AND deductible_vat <= GREATEST(vat_amount, 0)),
    CONSTRAINT uq_accounting_document_vat_bucket
        UNIQUE (document_id, treatment, vat_rate)
);

CREATE INDEX ix_accounting_document_vat_bucket_document
    ON investory.accounting_document_vat_bucket(document_id, id);

ALTER TABLE investory.accounting_known_counterparty
    ADD COLUMN IF NOT EXISTS identifier_type VARCHAR(16),
    ADD COLUMN IF NOT EXISTS vat_eu_number VARCHAR(64),
    ADD COLUMN IF NOT EXISTS vies_status VARCHAR(24),
    ADD COLUMN IF NOT EXISTS vies_verified_at DATE;

ALTER TABLE investory.accounting_known_counterparty
    ADD CONSTRAINT chk_accounting_known_counterparty_identifier_type
    CHECK (identifier_type IS NULL OR identifier_type IN ('NIP', 'VAT_EU', 'NONE'));

-- Employment type and dates alone do not establish primary social-insurance eligibility.
ALTER TABLE investory.employment_period
    ADD COLUMN IF NOT EXISTS qualifies_as_primary_social_insurance BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE investory.employment_period
    ALTER COLUMN qualifies_as_primary_social_insurance DROP DEFAULT;

-- Final POC contract hardening. Periods are represented by the first day of their month.
ALTER TABLE investory.accounting_poc_invoice
    ADD CONSTRAINT chk_accounting_poc_invoice_period_month_start
    CHECK (EXTRACT(DAY FROM tax_period) = 1);

ALTER TABLE investory.accounting_poc_expense_invoice
    ADD CONSTRAINT chk_accounting_poc_expense_period_month_start
    CHECK (EXTRACT(DAY FROM tax_period) = 1);

ALTER TABLE investory.accounting_poc_bank_transaction
    ADD CONSTRAINT chk_accounting_poc_bank_related_period_month_start
    CHECK (related_period IS NULL OR EXTRACT(DAY FROM related_period) = 1);

ALTER TABLE investory.accounting_poc_obligation
    ADD CONSTRAINT chk_accounting_poc_obligation_period_month_start
    CHECK (EXTRACT(DAY FROM tax_period) = 1);

ALTER TABLE investory.accounting_poc_tax_input
    ADD CONSTRAINT chk_accounting_poc_tax_input_period_month_start
    CHECK (EXTRACT(DAY FROM tax_period) = 1);

ALTER TABLE investory.accounting_poc_period_state
    ADD CONSTRAINT chk_accounting_poc_period_state_month_start
    CHECK (EXTRACT(DAY FROM tax_period) = 1);

ALTER TABLE investory.accounting_tax_profile_period
    ADD CONSTRAINT chk_accounting_tax_profile_period_month_start
    CHECK (EXTRACT(DAY FROM valid_from) = 1 AND (valid_to IS NULL OR EXTRACT(DAY FROM valid_to) = 1));

ALTER TABLE investory.accounting_document
    ADD CONSTRAINT chk_accounting_document_period_month_start
    CHECK (EXTRACT(DAY FROM tax_period) = 1);

ALTER TABLE investory.accounting_vat_transaction
    ADD CONSTRAINT chk_accounting_vat_transaction_period_month_start
    CHECK (EXTRACT(DAY FROM tax_period) = 1);

ALTER TABLE investory.accounting_filing_artifact
    ADD CONSTRAINT chk_accounting_filing_artifact_period_month_start
    CHECK (EXTRACT(DAY FROM tax_period) = 1);

ALTER TABLE investory.accounting_authority_confirmation
    ADD CONSTRAINT chk_accounting_authority_confirmation_period_month_start
    CHECK (EXTRACT(DAY FROM tax_period) = 1);

ALTER TABLE investory.accounting_poc_invoice
    ADD CONSTRAINT chk_accounting_poc_invoice_amounts
    CHECK (net_amount + vat_amount = gross_amount);

ALTER TABLE investory.accounting_poc_expense_invoice
    ADD CONSTRAINT chk_accounting_poc_expense_amounts
    CHECK (net_amount + vat_amount = gross_amount);

-- Source identity must remain in the same application profile as the normalized row.
CREATE UNIQUE INDEX uq_accounting_source_evidence_profile_id
    ON investory.accounting_source_evidence (profile_id, id);

CREATE UNIQUE INDEX uq_accounting_poc_invoice_profile_id
    ON investory.accounting_poc_invoice (profile_id, id);

CREATE UNIQUE INDEX uq_accounting_poc_expense_profile_id
    ON investory.accounting_poc_expense_invoice (profile_id, id);

ALTER TABLE investory.accounting_poc_invoice
    DROP CONSTRAINT IF EXISTS accounting_poc_invoice_source_id_fkey;

ALTER TABLE investory.accounting_poc_invoice
    ADD CONSTRAINT fk_accounting_poc_invoice_profile_source
    FOREIGN KEY (profile_id, source_id)
    REFERENCES investory.accounting_source_evidence (profile_id, id);

ALTER TABLE investory.accounting_poc_expense_invoice
    DROP CONSTRAINT IF EXISTS accounting_poc_expense_invoice_source_id_fkey;

ALTER TABLE investory.accounting_poc_expense_invoice
    ADD CONSTRAINT fk_accounting_poc_expense_profile_source
    FOREIGN KEY (profile_id, source_id)
    REFERENCES investory.accounting_source_evidence (profile_id, id);

ALTER TABLE investory.accounting_poc_bank_transaction
    DROP CONSTRAINT IF EXISTS accounting_poc_bank_transaction_source_id_fkey;

ALTER TABLE investory.accounting_poc_bank_transaction
    ADD CONSTRAINT fk_accounting_poc_bank_profile_source
    FOREIGN KEY (profile_id, source_id)
    REFERENCES investory.accounting_source_evidence (profile_id, id);

-- The original trigger predates profile scoping. Recreate it so ownership is immutable too.
CREATE OR REPLACE FUNCTION investory.prevent_accounting_source_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.profile_id IS DISTINCT FROM OLD.profile_id
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

-- All relational accounting references must stay inside the owning profile.
CREATE UNIQUE INDEX uq_accounting_known_counterparty_profile_id
    ON investory.accounting_known_counterparty (profile_id, id);

CREATE UNIQUE INDEX uq_accounting_document_profile_id
    ON investory.accounting_document (profile_id, id);

ALTER TABLE investory.accounting_document
    DROP CONSTRAINT IF EXISTS accounting_document_source_id_fkey,
    DROP CONSTRAINT IF EXISTS accounting_document_counterparty_id_fkey;

ALTER TABLE investory.accounting_document
    ADD CONSTRAINT fk_accounting_document_profile_source
    FOREIGN KEY (profile_id, source_id)
    REFERENCES investory.accounting_source_evidence (profile_id, id),
    ADD CONSTRAINT fk_accounting_document_profile_counterparty
    FOREIGN KEY (profile_id, counterparty_id)
    REFERENCES investory.accounting_known_counterparty (profile_id, id);

ALTER TABLE investory.accounting_vat_transaction
    DROP CONSTRAINT IF EXISTS accounting_vat_transaction_source_id_fkey,
    DROP CONSTRAINT IF EXISTS accounting_vat_transaction_invoice_id_fkey,
    DROP CONSTRAINT IF EXISTS accounting_vat_transaction_expense_invoice_id_fkey;

ALTER TABLE investory.accounting_vat_transaction
    ADD CONSTRAINT fk_accounting_vat_profile_source
    FOREIGN KEY (profile_id, source_id)
    REFERENCES investory.accounting_source_evidence (profile_id, id),
    ADD CONSTRAINT fk_accounting_vat_profile_invoice
    FOREIGN KEY (profile_id, invoice_id)
    REFERENCES investory.accounting_poc_invoice (profile_id, id),
    ADD CONSTRAINT fk_accounting_vat_profile_expense
    FOREIGN KEY (profile_id, expense_invoice_id)
    REFERENCES investory.accounting_poc_expense_invoice (profile_id, id);

ALTER TABLE investory.accounting_authority_confirmation
    DROP CONSTRAINT IF EXISTS fk_accounting_authority_confirmation_source;

ALTER TABLE investory.accounting_authority_confirmation
    ADD CONSTRAINT fk_accounting_authority_profile_source
    FOREIGN KEY (profile_id, source_document_id)
    REFERENCES investory.accounting_source_evidence (profile_id, id);

CREATE OR REPLACE FUNCTION investory.validate_accounting_document_correction()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    target_profile BIGINT;
    target_direction VARCHAR(16);
BEGIN
    IF NEW.corrects_document_id IS NULL THEN
        RETURN NEW;
    END IF;
    SELECT profile_id, direction
      INTO target_profile, target_direction
      FROM investory.accounting_document
     WHERE id = NEW.corrects_document_id;
    IF target_profile IS NULL
       OR target_profile IS DISTINCT FROM NEW.profile_id
       OR target_direction IS DISTINCT FROM NEW.direction
       OR NEW.document_kind <> 'CREDIT_NOTE'
    THEN
        RAISE EXCEPTION 'Accounting correction must target a same-profile, same-direction document';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_accounting_document_correction_profile
BEFORE INSERT OR UPDATE OF profile_id, direction, document_kind, corrects_document_id
ON investory.accounting_document
FOR EACH ROW EXECUTE FUNCTION investory.validate_accounting_document_correction();


-- Squashed DDL from V01.011 staging and reference tables.
-- Temporary POC/test data injection squashed from V01.010 through V01.019.


-- Squashed from app/src/main/resources/sql/migration/V01.027__accounting_staging_reconciliation.sql
CREATE TABLE investory.accounting_tmp_invoice (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    tax_period DATE NOT NULL,
    source_id BIGINT NOT NULL REFERENCES investory.accounting_source_evidence(id),
    source_type VARCHAR(16) NOT NULL,
    source_reference VARCHAR(256),
    source_hash BYTEA,
    document_kind VARCHAR(16) NOT NULL,
    document_date DATE,
    reference VARCHAR(128) NOT NULL,
    counterparty_name VARCHAR(256) NOT NULL,
    counterparty_tax_identifier VARCHAR(64),
    counterparty_country VARCHAR(2),
    currency CHAR(3) NOT NULL,
    net_amount NUMERIC(19,4) NOT NULL,
    vat_amount NUMERIC(19,4) NOT NULL,
    gross_amount NUMERIC(19,4) NOT NULL,
    vat_deduction_ratio NUMERIC(3,2),
    deductible_vat NUMERIC(19,4),
    vat_treatment VARCHAR(48),
    ksef_number VARCHAR(256),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reconciliation_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reconciliation_reason_codes VARCHAR(64)[] NOT NULL DEFAULT '{}',
    reconciliation_message VARCHAR(1000),
    promoted_at TIMESTAMPTZ,
    canonical_id BIGINT,
    canonical_type VARCHAR(32),
    CONSTRAINT chk_accounting_tmp_invoice_status CHECK (reconciliation_status IN ('PENDING','MATCH','NEW','MISMATCH','AMBIGUOUS','PROMOTED')),
    CONSTRAINT uq_accounting_tmp_invoice_source_reference UNIQUE (source_id, reference)
);


CREATE INDEX ix_accounting_tmp_invoice_period ON investory.accounting_tmp_invoice(profile_id, tax_period, reconciliation_status, id);


CREATE TABLE investory.accounting_tmp_bank_transaction (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    tax_period DATE NOT NULL,
    source_id BIGINT NOT NULL REFERENCES investory.accounting_source_evidence(id),
    source_type VARCHAR(16) NOT NULL,
    source_reference VARCHAR(256),
    source_hash BYTEA,
    provider VARCHAR(64) NOT NULL,
    external_account_id VARCHAR(256),
    external_transaction_id VARCHAR(256),
    booking_date DATE NOT NULL,
    value_date DATE,
    amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    counterparty_name VARCHAR(256),
    counterparty_account VARCHAR(256),
    remittance_information VARCHAR(1000),
    source_payload_hash VARCHAR(256),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reconciliation_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reconciliation_reason_codes VARCHAR(64)[] NOT NULL DEFAULT '{}',
    reconciliation_message VARCHAR(1000),
    promoted_at TIMESTAMPTZ,
    canonical_id BIGINT,
    CONSTRAINT chk_accounting_tmp_bank_status CHECK (reconciliation_status IN ('PENDING','MATCH','NEW','MISMATCH','AMBIGUOUS','PROMOTED')),
    CONSTRAINT uq_accounting_tmp_bank_identity UNIQUE (source_id, external_transaction_id)
);


CREATE INDEX ix_accounting_tmp_bank_period ON investory.accounting_tmp_bank_transaction(profile_id, tax_period, reconciliation_status, id);


CREATE TABLE investory.accounting_tmp_vat_transaction (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    tax_period DATE NOT NULL,
    source_id BIGINT NOT NULL REFERENCES investory.accounting_source_evidence(id),
    direction VARCHAR(16) NOT NULL,
    treatment VARCHAR(48) NOT NULL,
    tax_date DATE NOT NULL,
    counterparty_country VARCHAR(2),
    counterparty_tax_identifier VARCHAR(64),
    net_amount NUMERIC(19,4) NOT NULL,
    vat_amount NUMERIC(19,4) NOT NULL,
    deductible_vat NUMERIC(19,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reconciliation_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reconciliation_reason_codes VARCHAR(64)[] NOT NULL DEFAULT '{}',
    reconciliation_message VARCHAR(1000),
    promoted_at TIMESTAMPTZ,
    canonical_id BIGINT,
    CONSTRAINT chk_accounting_tmp_vat_status CHECK (reconciliation_status IN ('PENDING','MATCH','NEW','MISMATCH','AMBIGUOUS','PROMOTED'))
);


CREATE INDEX ix_accounting_tmp_vat_period ON investory.accounting_tmp_vat_transaction(profile_id, tax_period, reconciliation_status, id);
-- Immutable Jan-Aug 2026 verification oracle. It is deliberately separate from
-- operational Accounting tables and is never read by the calculation path.
CREATE TABLE investory.accounting_reference_invoice (
    id BIGINT PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    tax_period DATE NOT NULL,
    issue_date DATE,
    sale_date DATE,
    fx_rate_date DATE,
    reference VARCHAR(128) NOT NULL,
    counterparty_alias VARCHAR(128) NOT NULL,
    invoice_kind VARCHAR(32) NOT NULL,
    currency CHAR(3) NOT NULL,
    net_amount NUMERIC(19,4) NOT NULL,
    vat_amount NUMERIC(19,4) NOT NULL,
    gross_amount NUMERIC(19,4) NOT NULL,
    correction_net_amount NUMERIC(19,4) NOT NULL,
    correction_vat_amount NUMERIC(19,4) NOT NULL,
    correction_gross_amount NUMERIC(19,4) NOT NULL,
    expected_receivable NUMERIC(19,4) NOT NULL,
    booked_net_pln NUMERIC(19,4),
    ryczalt_rate NUMERIC(7,4),
    note VARCHAR(512),
    source_id BIGINT,
    counterparty_tax_identifier VARCHAR(32),
    counterparty_country VARCHAR(2),
    ksef_number VARCHAR(256),
    filing_evidence VARCHAR(8),
    UNIQUE (profile_id, reference)
);


CREATE TABLE investory.accounting_reference_expense_invoice (
    id BIGINT PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    tax_period DATE NOT NULL,
    invoice_date DATE,
    reference VARCHAR(128) NOT NULL,
    supplier_alias VARCHAR(128) NOT NULL,
    category VARCHAR(64) NOT NULL,
    currency CHAR(3) NOT NULL,
    net_amount NUMERIC(19,4) NOT NULL,
    vat_amount NUMERIC(19,4) NOT NULL,
    gross_amount NUMERIC(19,4) NOT NULL,
    vat_deduction_ratio NUMERIC(3,2) NOT NULL,
    source_quality VARCHAR(32) NOT NULL,
    note VARCHAR(512),
    source_id BIGINT,
    counterparty_tax_identifier VARCHAR(32),
    counterparty_country VARCHAR(2),
    ksef_number VARCHAR(256),
    filing_evidence VARCHAR(8),
    UNIQUE (profile_id, reference)
);


CREATE TABLE investory.accounting_reference_bank_transaction (
    id BIGINT PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    booking_date DATE NOT NULL,
    related_period DATE,
    reference VARCHAR(128),
    counterparty_alias VARCHAR(128),
    currency CHAR(3) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    scope VARCHAR(32) NOT NULL,
    note VARCHAR(512),
    source_id BIGINT,
    source_row_identity VARCHAR(256),
    provider VARCHAR(32) NOT NULL,
    external_account_id VARCHAR(256) NOT NULL,
    external_transaction_id VARCHAR(256) NOT NULL,
    source_payload_hash VARCHAR(128)
);


CREATE TABLE investory.accounting_reference_obligation (
    id BIGINT PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    tax_period DATE NOT NULL,
    obligation_type VARCHAR(32) NOT NULL,
    due_date DATE,
    expected_amount NUMERIC(19,4) NOT NULL,
    paid_amount NUMERIC(19,4),
    payment_date DATE,
    status VARCHAR(32) NOT NULL,
    note VARCHAR(512)
);


CREATE TABLE investory.accounting_reference_tax_input (
    id BIGINT PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    tax_period DATE NOT NULL,
    input_type VARCHAR(64) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    note VARCHAR(512)
);


CREATE TABLE investory.accounting_reference_month (
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    tax_period DATE NOT NULL,
    revenue NUMERIC(19,4) NOT NULL,
    expenses NUMERIC(19,4) NOT NULL,
    output_vat NUMERIC(19,4) NOT NULL,
    deductible_input_vat NUMERIC(19,4) NOT NULL,
    vat_payable NUMERIC(19,4) NOT NULL,
    ryczalt NUMERIC(19,4),
    zus NUMERIC(19,4),
    document_count INTEGER NOT NULL,
    bank_count INTEGER NOT NULL,
    filing_status VARCHAR(32),
    PRIMARY KEY (profile_id, tax_period),
    CONSTRAINT chk_accounting_reference_month_range
        CHECK (tax_period >= DATE '2026-01-01' AND tax_period < DATE '2026-09-01')
);


COMMENT ON TABLE investory.accounting_reference_month IS
    'Immutable Jan-Aug 2026 verification oracle. Never used as operational calculation input.';

-- Branch oracle: a second, non-UoP taxpayer case. This is intentionally
-- separate from accounting_poc_profile, whose singleton is the live POC
-- profile. Each month carries both policy variants and the date edge cases.
CREATE TABLE investory.accounting_reference_zus_branch (
    case_key VARCHAR(32) NOT NULL,
    tax_period DATE NOT NULL,
    has_uop BOOLEAN NOT NULL,
    voluntary_sickness BOOLEAN NOT NULL,
    ytd_revenue NUMERIC(19,4) NOT NULL,
    paid_social NUMERIC(19,4) NOT NULL,
    expected_health_band VARCHAR(16) NOT NULL,
    expected_social NUMERIC(19,4) NOT NULL,
    expected_deductible_social NUMERIC(19,4) NOT NULL,
    expected_health NUMERIC(19,4) NOT NULL,
    correction_sale_date DATE NOT NULL,
    correction_issue_date DATE NOT NULL,
    expected_correction_period DATE NOT NULL,
    foreign_document_date DATE NOT NULL,
    expected_fx_rate_date DATE NOT NULL,
    PRIMARY KEY (case_key, tax_period),
    CONSTRAINT chk_accounting_reference_zus_branch_period
        CHECK (tax_period >= DATE '2026-01-01' AND tax_period < DATE '2026-09-01'),
    CONSTRAINT chk_accounting_reference_zus_branch_band
        CHECK (expected_health_band IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT chk_accounting_reference_zus_branch_dates
        CHECK (expected_correction_period = DATE_TRUNC('month', correction_issue_date)::date)
);

COMMENT ON TABLE investory.accounting_reference_zus_branch IS
    'Immutable monthly ZUS branch oracle, including the non-UoP social path. Never used by calculations.';
-- A provider transaction is one operational staging row per profile, even when
-- the same transaction is present in overlapping exports.
CREATE UNIQUE INDEX uq_accounting_tmp_bank_profile_external_transaction
    ON investory.accounting_tmp_bank_transaction
       (profile_id, provider, external_account_id, external_transaction_id);
ALTER TABLE investory.accounting_tmp_invoice
    DROP CONSTRAINT IF EXISTS uq_accounting_tmp_invoice_source_reference;

CREATE UNIQUE INDEX IF NOT EXISTS uq_accounting_tmp_invoice_profile_source_reference
    ON investory.accounting_tmp_invoice(profile_id, source_id, reference);

ALTER TABLE investory.accounting_tmp_bank_transaction
    DROP CONSTRAINT IF EXISTS uq_accounting_tmp_bank_identity;

CREATE UNIQUE INDEX IF NOT EXISTS uq_accounting_tmp_bank_profile_identity
    ON investory.accounting_tmp_bank_transaction(profile_id, source_id, external_transaction_id);

ALTER TABLE investory.accounting_tmp_invoice
    ADD CONSTRAINT chk_accounting_tmp_invoice_period_month_start
    CHECK (EXTRACT(DAY FROM tax_period) = 1);

ALTER TABLE investory.accounting_tmp_bank_transaction
    ADD CONSTRAINT chk_accounting_tmp_bank_period_month_start
    CHECK (EXTRACT(DAY FROM tax_period) = 1);

ALTER TABLE investory.accounting_tmp_vat_transaction
    ADD CONSTRAINT chk_accounting_tmp_vat_period_month_start
    CHECK (EXTRACT(DAY FROM tax_period) = 1);

ALTER TABLE investory.accounting_reference_invoice
    ADD CONSTRAINT chk_accounting_reference_invoice_period_month_start
    CHECK (EXTRACT(DAY FROM tax_period) = 1);

ALTER TABLE investory.accounting_reference_expense_invoice
    ADD CONSTRAINT chk_accounting_reference_expense_period_month_start
    CHECK (EXTRACT(DAY FROM tax_period) = 1);

ALTER TABLE investory.accounting_reference_bank_transaction
    ADD CONSTRAINT chk_accounting_reference_bank_related_period_month_start
    CHECK (related_period IS NULL OR EXTRACT(DAY FROM related_period) = 1);

ALTER TABLE investory.accounting_reference_obligation
    ADD CONSTRAINT chk_accounting_reference_obligation_period_month_start
    CHECK (EXTRACT(DAY FROM tax_period) = 1);

ALTER TABLE investory.accounting_reference_tax_input
    ADD CONSTRAINT chk_accounting_reference_tax_input_period_month_start
    CHECK (EXTRACT(DAY FROM tax_period) = 1);

ALTER TABLE investory.accounting_reference_month
    ADD CONSTRAINT chk_accounting_reference_month_month_start
    CHECK (EXTRACT(DAY FROM tax_period) = 1);

ALTER TABLE investory.accounting_tmp_invoice
    DROP CONSTRAINT IF EXISTS accounting_tmp_invoice_source_id_fkey;

ALTER TABLE investory.accounting_tmp_invoice
    ADD CONSTRAINT fk_accounting_tmp_invoice_profile_source
    FOREIGN KEY (profile_id, source_id)
    REFERENCES investory.accounting_source_evidence (profile_id, id);

ALTER TABLE investory.accounting_tmp_bank_transaction
    DROP CONSTRAINT IF EXISTS accounting_tmp_bank_transaction_source_id_fkey;

ALTER TABLE investory.accounting_tmp_bank_transaction
    ADD CONSTRAINT fk_accounting_tmp_bank_profile_source
    FOREIGN KEY (profile_id, source_id)
    REFERENCES investory.accounting_source_evidence (profile_id, id);

ALTER TABLE investory.accounting_tmp_vat_transaction
    DROP CONSTRAINT IF EXISTS accounting_tmp_vat_transaction_source_id_fkey;

ALTER TABLE investory.accounting_tmp_vat_transaction
    ADD CONSTRAINT fk_accounting_tmp_vat_profile_source
    FOREIGN KEY (profile_id, source_id)
    REFERENCES investory.accounting_source_evidence (profile_id, id);


ALTER TABLE investory.accounting_tmp_invoice ALTER COLUMN profile_id DROP DEFAULT;

ALTER TABLE investory.accounting_tmp_bank_transaction ALTER COLUMN profile_id DROP DEFAULT;

ALTER TABLE investory.accounting_tmp_vat_transaction ALTER COLUMN profile_id DROP DEFAULT;

-- Preserve the source bank row's obligation period separately from the month used
-- to display and navigate the staging queue.
ALTER TABLE investory.accounting_tmp_bank_transaction
    ADD COLUMN IF NOT EXISTS related_period DATE;
ALTER TABLE investory.accounting_tmp_invoice
    ADD COLUMN IF NOT EXISTS due_date DATE;
ALTER TABLE investory.accounting_tmp_invoice
    ADD COLUMN vat_rate NUMERIC(5,2);





-- Squashed DDL from V01.013__accounting_counterparty_auto_approval.sql.
ALTER TABLE investory.accounting_poc_profile
    ADD COLUMN auto_approve_known_counterparties BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE investory.accounting_trusted_counterparty_treatment (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    counterparty_id BIGINT NOT NULL REFERENCES investory.accounting_known_counterparty(id),
    source_document_id BIGINT NOT NULL REFERENCES investory.accounting_document(id),
    direction VARCHAR(16) NOT NULL,
    document_kind VARCHAR(32) NOT NULL,
    category VARCHAR(64),
    vat_treatment VARCHAR(40) NOT NULL,
    vat_deduction_ratio NUMERIC(3,2),
    vat_rate NUMERIC(5,2),
    jpk_evidence VARCHAR(32),
    confirmed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_accounting_trusted_treatment_source UNIQUE (profile_id, source_document_id),
    CONSTRAINT chk_accounting_trusted_treatment_direction CHECK (direction IN ('SALE', 'PURCHASE')),
    CONSTRAINT chk_accounting_trusted_treatment_kind CHECK (document_kind IN ('INVOICE', 'CREDIT_NOTE'))
);

CREATE INDEX ix_accounting_trusted_treatment_lookup
    ON investory.accounting_trusted_counterparty_treatment(profile_id, counterparty_id, direction);


-- Squashed DDL from V01.015__accounting_2025_reference_months.sql.
-- Reconstruct the missing 2025 reference-month rows from the immutable
-- obligation evidence and staged bank evidence, falling back to operational
-- facts where a reference component is not available.

ALTER TABLE investory.accounting_reference_month
    DROP CONSTRAINT IF EXISTS chk_accounting_reference_month_range;

ALTER TABLE investory.accounting_reference_month
    ADD CONSTRAINT chk_accounting_reference_month_range
    CHECK (tax_period >= DATE '2025-01-01' AND tax_period < DATE '2026-09-01');


-- Squashed DDL from V01.017__accounting_counterparty_alias.sql.
ALTER TABLE investory.accounting_known_counterparty
    ADD COLUMN IF NOT EXISTS alias VARCHAR(128);

COMMENT ON COLUMN investory.accounting_known_counterparty.alias IS
    'Optional user-facing name; canonical_name remains the stable source identity.';


-- Squashed DDL from V01.020__accounting_vat_adjustment_facts.sql.
CREATE TABLE IF NOT EXISTS investory.accounting_vat_adjustment (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    tax_period DATE NOT NULL,
    adjustment_type TEXT NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    source_system TEXT NOT NULL,
    source_reference TEXT NOT NULL,
    affects TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_accounting_vat_adjustment_period_month_start
        CHECK (tax_period = date_trunc('month', tax_period)::date),
    CONSTRAINT chk_accounting_vat_adjustment_affects
        CHECK (affects IN ('OUTPUT_VAT', 'INPUT_VAT', 'PAYABLE_VAT')),
    CONSTRAINT uq_accounting_vat_adjustment_source
        UNIQUE (profile_id, tax_period, adjustment_type, source_system, source_reference)
);

COMMENT ON TABLE investory.accounting_vat_adjustment IS
    'Traceable historical VAT adjustment facts; amounts are signed and added to VAT payable.';


-- Squashed DDL from V01.021__accounting_auto_approval_policy.sql.
CREATE TABLE investory.accounting_auto_approval_policy (
    profile_id BIGINT PRIMARY KEY REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    max_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    trusted_categories TEXT[] NOT NULL DEFAULT '{}',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_accounting_auto_approval_max_amount_non_negative CHECK (max_amount >= 0)
);

COMMENT ON TABLE investory.accounting_auto_approval_policy IS
    'Profile-scoped editor settings for future backend auto-approval decisions; settings alone do not approve documents.';


-- Squashed DDL from V01.024__accounting_staging_document_semantics.sql.
-- Staging must retain every reviewed fact required for safe canonical promotion.
ALTER TABLE investory.accounting_tmp_invoice
    ADD COLUMN category VARCHAR(64),
    ADD COLUMN source_quality VARCHAR(32),
    ADD COLUMN corrects_document_reference VARCHAR(128);
