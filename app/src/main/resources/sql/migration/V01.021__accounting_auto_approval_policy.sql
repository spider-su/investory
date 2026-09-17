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
