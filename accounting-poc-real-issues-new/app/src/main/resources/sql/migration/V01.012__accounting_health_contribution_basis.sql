CREATE TABLE investory.accounting_health_contribution_basis (
    id BIGSERIAL PRIMARY KEY,
    profile_id SMALLINT NOT NULL REFERENCES investory.accounting_poc_profile(id),
    valid_from DATE NOT NULL,
    valid_to DATE,
    basis VARCHAR(16) NOT NULL,
    CONSTRAINT chk_accounting_health_basis_range
        CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT chk_accounting_health_basis_value
        CHECK (basis IN ('YTD_REVENUE', 'LOW', 'MEDIUM', 'HIGH')),
    UNIQUE (profile_id, valid_from)
);

CREATE INDEX idx_accounting_health_basis_lookup
    ON investory.accounting_health_contribution_basis (profile_id, valid_from DESC);

COMMENT ON TABLE investory.accounting_health_contribution_basis IS
    'Effective-dated, accounting-profile-scoped health contribution basis. It selects source inputs; it does not store calculated tax output.';

INSERT INTO investory.accounting_health_contribution_basis
    (profile_id, valid_from, valid_to, basis)
VALUES
    (1, DATE '2026-01-01', NULL, 'HIGH');
