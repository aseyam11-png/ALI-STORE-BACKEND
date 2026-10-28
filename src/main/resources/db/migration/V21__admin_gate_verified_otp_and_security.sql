ALTER TABLE email_otp_challenges
    ADD COLUMN IF NOT EXISTS admin_gate_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Pre-existing challenges cannot mint an admin-gate-verified JWT.
