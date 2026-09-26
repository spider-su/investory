SET search_path TO investory, public;

CREATE TABLE investory.ryczalt_fx_resolution (
    id BIGSERIAL PRIMARY KEY,
    currency VARCHAR(3) NOT NULL,
    requested_date DATE NOT NULL,
    effective_date DATE NOT NULL,
    rate NUMERIC(19,8) NOT NULL CHECK (rate > 0),
    provider VARCHAR(64) NOT NULL,
    provider_reference VARCHAR(256),
    resolved_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ryczalt_fx_resolution UNIQUE (provider, currency, requested_date)
);
CREATE INDEX ix_ryczalt_fx_resolution_lookup
    ON investory.ryczalt_fx_resolution(currency, requested_date);

ALTER TABLE investory.ryczalt_invoice
    ADD COLUMN booked_vat_pln NUMERIC(19,4),
    ADD COLUMN fx_rate NUMERIC(19,8),
    ADD COLUMN fx_effective_date DATE,
    ADD COLUMN fx_provider VARCHAR(64),
    ADD COLUMN fx_provider_reference VARCHAR(256);
