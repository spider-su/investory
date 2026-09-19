ALTER TABLE investory.ryczalt_obligation
    DROP CONSTRAINT chk_ryczalt_obligation_status;

ALTER TABLE investory.ryczalt_obligation
    ADD CONSTRAINT chk_ryczalt_obligation_status
    CHECK (status IN ('OPEN', 'PARTIALLY_PAID', 'PAID', 'OVERPAID', 'FROZEN'));

ALTER TABLE investory.ryczalt_obligation
    ADD CONSTRAINT uq_ryczalt_obligation_profile_id UNIQUE (profile_id, id);

ALTER TABLE investory.ryczalt_transaction
    ADD CONSTRAINT uq_ryczalt_transaction_profile_id UNIQUE (profile_id, id);

CREATE TABLE investory.ryczalt_payment_match (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    obligation_id BIGINT NOT NULL,
    transaction_id BIGINT NOT NULL,
    matched_amount NUMERIC(19,4) NOT NULL,
    match_type VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ryczalt_payment_match_pair UNIQUE (profile_id, obligation_id, transaction_id),
    CONSTRAINT fk_ryczalt_payment_match_obligation
        FOREIGN KEY (profile_id, obligation_id)
        REFERENCES investory.ryczalt_obligation(profile_id, id),
    CONSTRAINT fk_ryczalt_payment_match_transaction
        FOREIGN KEY (profile_id, transaction_id)
        REFERENCES investory.ryczalt_transaction(profile_id, id),
    CONSTRAINT chk_ryczalt_payment_match_amount CHECK (matched_amount > 0),
    CONSTRAINT chk_ryczalt_payment_match_type CHECK (match_type IN ('AUTO', 'MANUAL'))
);

CREATE INDEX ix_ryczalt_payment_match_obligation
    ON investory.ryczalt_payment_match(profile_id, obligation_id, created_at);

CREATE INDEX ix_ryczalt_payment_match_transaction
    ON investory.ryczalt_payment_match(profile_id, transaction_id, created_at);
