CREATE TABLE IF NOT EXISTS investory.ryczalt_onboarding (
    profile_id BIGINT PRIMARY KEY REFERENCES investory.portfolios(id),
    state VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED',
    nip VARCHAR(10),
    company_name VARCHAR(255),
    regon VARCHAR(14),
    legal_form VARCHAR(32),
    business_status VARCHAR(32),
    business_start_date DATE,
    vat_status VARCHAR(32),
    registered_address VARCHAR(500),
    company_source VARCHAR(64),
    lookup_status VARCHAR(32),
    lookup_warnings TEXT,
    company_lookup_retrieved_at TIMESTAMPTZ,
    company_confirmed_at TIMESTAMPTZ,
    accounting_confirmed_at TIMESTAMPTZ,
    taxation_method VARCHAR(32),
    ryczalt_rate NUMERIC(5, 4),
    pit_frequency VARCHAR(16),
    vat_frequency VARCHAR(16),
    accounting_start_date DATE,
    jdg_active BOOLEAN,
    qualifying_uop BOOLEAN,
    zus_regime VARCHAR(32),
    voluntary_sickness BOOLEAN,
    zus_health_method VARCHAR(64),
    zus_full_jdg_social NUMERIC(12, 2),
    ksef_state VARCHAR(32) NOT NULL DEFAULT 'NOT_CONNECTED',
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ryczalt_onboarding_state_ck CHECK (state IN ('NOT_STARTED', 'COMPANY_CONFIRMED', 'ACCOUNTING_CONFIRMED', 'COMPLETED')),
    CONSTRAINT ryczalt_onboarding_ksef_state_ck CHECK (ksef_state IN ('NOT_CONNECTED', 'SKIPPED', 'CONNECTED')),
    CONSTRAINT ryczalt_onboarding_legal_form_ck CHECK (legal_form IS NULL OR legal_form = 'JDG'),
    CONSTRAINT ryczalt_onboarding_zus_regime_ck CHECK (zus_regime IS NULL OR zus_regime IN ('JDG'))
);

-- Existing profiles have already passed the old profile setup. Do not interrupt them.
INSERT INTO investory.ryczalt_onboarding (
    profile_id, state, nip, company_name, legal_form, business_status, vat_status, company_source,
    taxation_method, ryczalt_rate, pit_frequency, vat_frequency, accounting_start_date,
    jdg_active, qualifying_uop, zus_regime, voluntary_sickness, ksef_state, completed_at)
SELECT p.id, 'COMPLETED', r.nip, COALESCE(r.full_name, p.name), 'JDG', 'ACTIVE', 'ACTIVE', 'LEGACY_PROFILE',
       'RYCZALT', 0.12, 'MONTHLY', 'MONTHLY',
       COALESCE((SELECT MIN(make_date(period_year, period_month, 1))
                   FROM investory.ryczalt_period rp WHERE rp.profile_id = p.id), CURRENT_DATE),
       TRUE, r.has_uop, 'JDG', FALSE, 'NOT_CONNECTED', CURRENT_TIMESTAMP
FROM investory.portfolios p
JOIN investory.ryczalt_profile r ON r.profile_id = p.id
ON CONFLICT (profile_id) DO NOTHING;
