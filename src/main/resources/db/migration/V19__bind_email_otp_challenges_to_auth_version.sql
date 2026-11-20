ALTER TABLE email_otp_challenges
    ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;
