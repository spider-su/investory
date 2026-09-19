ALTER TABLE investory.ryczalt_calculation
    DROP CONSTRAINT uq_ryczalt_calculation_period_type;

ALTER TABLE investory.ryczalt_calculation
    ADD COLUMN revision INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN is_current BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE investory.ryczalt_calculation
    DROP CONSTRAINT chk_ryczalt_calculation_status;

ALTER TABLE investory.ryczalt_calculation
    ADD CONSTRAINT chk_ryczalt_calculation_status
    CHECK (status IN ('CURRENT', 'DIRTY', 'STALE', 'FROZEN', 'CALCULATED'));

CREATE UNIQUE INDEX uq_ryczalt_calculation_current
    ON investory.ryczalt_calculation(profile_id, period_id, calculation_type)
    WHERE is_current;

CREATE UNIQUE INDEX uq_ryczalt_calculation_revision
    ON investory.ryczalt_calculation(profile_id, period_id, calculation_type, revision);

ALTER TABLE investory.ryczalt_period
    ADD COLUMN reopened_at TIMESTAMPTZ,
    ADD COLUMN reopen_reason VARCHAR(1000);

ALTER TABLE investory.ryczalt_period
    ADD CONSTRAINT chk_ryczalt_period_reopen_reason
    CHECK (reopened_at IS NULL OR length(btrim(reopen_reason)) > 0);

CREATE TABLE investory.ryczalt_correction (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    original_period_id BIGINT NOT NULL REFERENCES investory.ryczalt_period(id),
    affected_entity_type VARCHAR(32) NOT NULL,
    affected_entity_id BIGINT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correction_period_id BIGINT REFERENCES investory.ryczalt_period(id),
    CONSTRAINT chk_ryczalt_correction_reason CHECK (length(btrim(reason)) > 0)
);

CREATE INDEX ix_ryczalt_correction_original_period
    ON investory.ryczalt_correction(profile_id, original_period_id, requested_at);

CREATE TABLE investory.ryczalt_audit_event (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    period_id BIGINT REFERENCES investory.ryczalt_period(id),
    event_type VARCHAR(32) NOT NULL,
    reason VARCHAR(1000),
    actor VARCHAR(256),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB
);

CREATE INDEX ix_ryczalt_audit_period ON investory.ryczalt_audit_event(profile_id, period_id, occurred_at);
