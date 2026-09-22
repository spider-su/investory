CREATE TABLE IF NOT EXISTS investory.ryczalt_invoice_payment_match (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    invoice_id BIGINT NOT NULL REFERENCES investory.ryczalt_invoice(id) ON DELETE CASCADE,
    transaction_id BIGINT NOT NULL REFERENCES investory.ryczalt_transaction(id) ON DELETE CASCADE,
    matched_amount NUMERIC(19,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ryczalt_invoice_payment_match UNIQUE (invoice_id, transaction_id),
    CONSTRAINT chk_ryczalt_invoice_payment_match_amount CHECK (matched_amount > 0)
);

CREATE INDEX ix_ryczalt_invoice_payment_match_transaction
    ON investory.ryczalt_invoice_payment_match(profile_id, transaction_id);
