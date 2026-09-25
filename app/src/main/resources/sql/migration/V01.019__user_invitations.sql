CREATE TABLE IF NOT EXISTS investory.app_user_invitations (
    id             bigserial PRIMARY KEY,
    email          varchar(320) NOT NULL,
    display_name   varchar(255) NOT NULL,
    profile_id     bigint NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    profile_role   varchar(16) NOT NULL,
    token_hash     varchar(64) NOT NULL UNIQUE,
    expires_at     timestamptz NOT NULL,
    consumed_at    timestamptz,
    created_by     bigint REFERENCES investory.app_users(id),
    created_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_app_user_invitations_role CHECK (profile_role IN ('OWNER', 'USER')),
    CONSTRAINT chk_app_user_invitations_email_not_blank CHECK (btrim(email) <> ''),
    CONSTRAINT chk_app_user_invitations_display_name_not_blank CHECK (btrim(display_name) <> '')
);

CREATE INDEX IF NOT EXISTS ix_app_user_invitations_email
    ON investory.app_user_invitations (lower(email), expires_at)
    WHERE consumed_at IS NULL;

COMMENT ON TABLE investory.app_user_invitations IS
    'Single-use invitations that attach a new application identity to an existing accounting profile.';
