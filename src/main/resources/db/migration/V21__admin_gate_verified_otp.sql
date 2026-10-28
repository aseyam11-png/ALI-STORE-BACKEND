ALTER TABLE email_otp_challenges
    ADD COLUMN IF NOT EXISTS admin_gate_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Challenges created before the admin gate existed are not proof of successful
-- second-factor verification; their default remains FALSE.
