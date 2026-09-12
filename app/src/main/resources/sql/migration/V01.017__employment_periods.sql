CREATE TABLE investory.employment_period (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    employment_type VARCHAR(8) NOT NULL,
    date_from DATE NOT NULL,
    date_to DATE,
    CONSTRAINT chk_employment_period_type CHECK (employment_type IN ('UOP', 'JDG')),
    CONSTRAINT chk_employment_period_dates CHECK (date_to IS NULL OR date_to >= date_from)
);

CREATE INDEX ix_employment_period_profile_dates
    ON investory.employment_period(profile_id, date_from, date_to);
