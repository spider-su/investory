ALTER TABLE investory.app_users
    ADD COLUMN email varchar(320),
    ADD COLUMN google_subject varchar(255);

CREATE UNIQUE INDEX uq_app_users_email_ci
    ON investory.app_users (lower(email))
    WHERE email IS NOT NULL;

CREATE UNIQUE INDEX uq_app_users_google_subject
    ON investory.app_users (google_subject)
    WHERE google_subject IS NOT NULL;

COMMENT ON COLUMN investory.app_users.email IS
    'Explicitly linked login email. Google login does not provision users automatically.';

COMMENT ON COLUMN investory.app_users.google_subject IS
    'Immutable Google subject claim linked to this application user.';
