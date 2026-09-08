SET search_path TO investory, public;

CREATE TABLE IF NOT EXISTS investory.profile_memberships (
    user_id    bigint NOT NULL REFERENCES investory.app_users(id) ON DELETE CASCADE,
    profile_id bigint NOT NULL REFERENCES investory.portfolios(id) ON DELETE CASCADE,
    role       varchar(16) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_profile_memberships PRIMARY KEY (user_id, profile_id),
    CONSTRAINT chk_profile_memberships_role CHECK (role IN ('OWNER', 'USER'))
);

CREATE INDEX IF NOT EXISTS ix_profile_memberships_profile
    ON investory.profile_memberships(profile_id, role);

-- Preserve write access for every existing portfolio owner.
INSERT INTO investory.profile_memberships (user_id, profile_id, role)
SELECT p.user_id, p.id, 'OWNER'
FROM investory.portfolios p
ON CONFLICT (user_id, profile_id) DO UPDATE SET role = 'OWNER';

-- Keep existing non-admin identities eligible for owner-only HTTP routes while
-- profile_memberships remains the authority for the actual profile scope.
UPDATE investory.app_users
SET role = 'PROFILE_OWNER', updated_at = now()
WHERE role IS NULL OR role NOT IN ('ADMIN', 'PROFILE_OWNER');

COMMENT ON TABLE investory.profile_memberships IS
    'Profile-scoped authorization. ADMIN remains global in app_users.role; OWNER and USER are profile roles.';
