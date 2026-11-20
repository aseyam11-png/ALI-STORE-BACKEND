ALTER TABLE users
    ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS login_locked_until TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS last_failed_login_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS auth_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE users
    ADD CONSTRAINT chk_users_failed_login_attempts_non_negative
        CHECK (failed_login_attempts >= 0),
    ADD CONSTRAINT chk_users_auth_version_non_negative
        CHECK (auth_version >= 0);
