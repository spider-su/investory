-- Consolidated Ryczalt schema additions. Safe to replay against databases which
-- already applied the former V01.013-V01.017 and V01.024 schema changes.

ALTER TABLE investory.ryczalt_transaction
    ADD COLUMN IF NOT EXISTS counterparty_account VARCHAR(64),
    ADD COLUMN IF NOT EXISTS excluded_from_payment_matching BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE investory.ryczalt_counterparty
    ADD COLUMN IF NOT EXISTS bank_account VARCHAR(64);

CREATE INDEX IF NOT EXISTS ix_ryczalt_transaction_counterparty_account
    ON investory.ryczalt_transaction(profile_id, counterparty_account)
    WHERE counterparty_account IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_ryczalt_counterparty_bank_account
    ON investory.ryczalt_counterparty(profile_id, bank_account)
    WHERE bank_account IS NOT NULL;

CREATE TABLE IF NOT EXISTS investory.ryczalt_payment_account_rule (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    obligation_type VARCHAR(32) NOT NULL,
    account_number VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ryczalt_payment_account_rule UNIQUE (profile_id, obligation_type, account_number),
    CONSTRAINT chk_ryczalt_payment_account_rule_type CHECK (obligation_type IN ('RYCZALT', 'VAT', 'ZUS')),
    CONSTRAINT chk_ryczalt_payment_account_rule_account CHECK (length(btrim(account_number)) > 0)
);

CREATE INDEX IF NOT EXISTS ix_ryczalt_payment_account_rule_profile
    ON investory.ryczalt_payment_account_rule(profile_id, obligation_type);

CREATE TABLE IF NOT EXISTS investory.ryczalt_native_month_input (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    tax_year INTEGER NOT NULL,
    tax_month INTEGER NOT NULL CHECK (tax_month BETWEEN 1 AND 12),
    jdg_active BOOLEAN NOT NULL,
    qualifying_uop BOOLEAN NOT NULL,
    zus_regime VARCHAR(32),
    voluntary_sickness BOOLEAN NOT NULL,
    ytd_ryczalt_revenue NUMERIC(19,4) NOT NULL DEFAULT 0,
    full_jdg_social NUMERIC(19,4),
    social_contribution_deduction NUMERIC(19,4),
    health_contribution_override NUMERIC(19,4),
    deductions_already_consumed NUMERIC(19,4) NOT NULL DEFAULT 0,
    sales_corrections NUMERIC(19,4) NOT NULL DEFAULT 0,
    explicit_vat_adjustments NUMERIC(19,4) NOT NULL DEFAULT 0,
    CONSTRAINT uq_ryczalt_native_month_input_period UNIQUE (profile_id, tax_year, tax_month)
);

ALTER TABLE investory.ryczalt_native_month_input
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS social_contribution_deduction NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS health_contribution_override NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS deductions_already_consumed NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS sales_corrections NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS explicit_vat_adjustments NUMERIC(19,4) NOT NULL DEFAULT 0;

ALTER TABLE investory.ryczalt_native_month_input
    DROP CONSTRAINT IF EXISTS chk_ryczalt_native_month_input_nonnegative;

ALTER TABLE investory.ryczalt_native_month_input
    ADD CONSTRAINT chk_ryczalt_native_month_input_nonnegative CHECK (
        ytd_ryczalt_revenue >= 0
        AND COALESCE(full_jdg_social, 0) >= 0
        AND COALESCE(social_contribution_deduction, 0) >= 0
        AND COALESCE(health_contribution_override, 0) >= 0
        AND deductions_already_consumed >= 0
    );

CREATE INDEX IF NOT EXISTS ix_ryczalt_native_month_input_profile_period
    ON investory.ryczalt_native_month_input(profile_id, tax_year, tax_month);

CREATE TABLE IF NOT EXISTS investory.ryczalt_obligation_reference (
    id BIGINT PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id),
    tax_period DATE NOT NULL,
    obligation_type VARCHAR(32) NOT NULL,
    due_date DATE,
    expected_amount NUMERIC(19,4) NOT NULL,
    paid_amount NUMERIC(19,4),
    payment_date DATE,
    status VARCHAR(32) NOT NULL,
    note VARCHAR(512),
    CONSTRAINT chk_ryczalt_obligation_reference_period_month_start
        CHECK (EXTRACT(DAY FROM tax_period) = 1)
);

CREATE INDEX IF NOT EXISTS idx_ryczalt_obligation_reference_profile_period
    ON investory.ryczalt_obligation_reference (profile_id, tax_period, id);

COMMENT ON TABLE investory.ryczalt_obligation_reference IS
    'Persisted reference obligations used to compare native Ryczalt calculations with supplied reference values.';

-- Backfill editable native monthly inputs from the existing tax-profile facts.
INSERT INTO investory.ryczalt_native_month_input (
    profile_id, tax_year, tax_month, jdg_active, qualifying_uop, zus_regime,
    voluntary_sickness, ytd_ryczalt_revenue, full_jdg_social,
    deductions_already_consumed, sales_corrections, explicit_vat_adjustments
)
SELECT
    period.profile_id,
    period.period_year,
    period.period_month,
    tax_profile.jdg_active,
    EXISTS (
        SELECT 1
        FROM investory.employment_period employment
        WHERE employment.profile_id = period.profile_id
          AND employment.employment_type = 'UOP'
          AND COALESCE(employment.qualifies_as_primary_social_insurance, TRUE)
          AND employment.date_from <= make_date(period.period_year, period.period_month, 1)
          AND (employment.date_to IS NULL OR employment.date_to >= make_date(period.period_year, period.period_month, 1))
    ),
    tax_profile.zus_regime,
    tax_profile.voluntary_sickness,
    COALESCE(SUM(invoice.booked_net_pln) FILTER (
        WHERE invoice.direction = 'INCOME'
          AND invoice.approval_status = 'APPROVED'
          AND invoice.currency = 'PLN'
          AND invoice.booked_net_pln IS NOT NULL
          AND invoice.accounting_date >= make_date(period.period_year, 1, 1)
          AND invoice.accounting_date < make_date(period.period_year, period.period_month, 1) + INTERVAL '1 month'
    ), 0),
    NULL,
    0,
    0,
    0
FROM investory.ryczalt_period period
JOIN investory.accounting_tax_profile_period tax_profile
  ON tax_profile.profile_id = period.profile_id
 AND tax_profile.valid_from <= make_date(period.period_year, period.period_month, 1)
 AND (tax_profile.valid_to IS NULL OR tax_profile.valid_to >= make_date(period.period_year, period.period_month, 1))
LEFT JOIN investory.ryczalt_invoice invoice
  ON invoice.profile_id = period.profile_id
GROUP BY period.profile_id, period.period_year, period.period_month,
         tax_profile.jdg_active, tax_profile.zus_regime, tax_profile.voluntary_sickness
ON CONFLICT (profile_id, tax_year, tax_month) DO NOTHING;
