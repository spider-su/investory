SET search_path TO investory, public;

-- Native taxpayer identity used by Ryczalt exports and filings.  The legacy
-- accounting table is intentionally kept; this table is the new runtime source.
CREATE TABLE investory.ryczalt_profile (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL UNIQUE REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    has_uop BOOLEAN NOT NULL DEFAULT TRUE,
    nip VARCHAR(10),
    full_name VARCHAR(240),
    tax_office_code VARCHAR(4),
    email VARCHAR(255),
    vat_payment_account VARCHAR(34),
    ryczalt_payment_account VARCHAR(34),
    zus_payment_account VARCHAR(34),
    first_name VARCHAR(120),
    surname VARCHAR(120),
    date_of_birth DATE,
    auto_approve_known_counterparties BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE investory.ryczalt_profile IS
    'Profile-scoped taxpayer identity and payment configuration used by native Ryczalt exports.';

-- Preserve the existing taxpayer configuration when the historical table is present.
DO $$
BEGIN
    IF to_regclass('investory.accounting_poc_profile') IS NOT NULL THEN
        INSERT INTO investory.ryczalt_profile (
            profile_id, has_uop, nip, full_name, tax_office_code, email,
            vat_payment_account, ryczalt_payment_account, zus_payment_account,
            first_name, surname, date_of_birth, auto_approve_known_counterparties)
        SELECT profile_id, has_uop, nip, full_name, tax_office_code, email,
               vat_payment_account, ryczalt_payment_account, zus_payment_account,
               first_name, surname, date_of_birth, auto_approve_known_counterparties
          FROM investory.accounting_poc_profile
        ON CONFLICT (profile_id) DO NOTHING;
    END IF;
END $$;
