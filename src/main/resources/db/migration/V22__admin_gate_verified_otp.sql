-- Persist proof that the administrator's secondary password and Turnstile check succeeded.
-- V21 is already used on current main for the canonical SAR catalog currency default.
ALTER TABLE email_otp_challenges
    ADD COLUMN IF NOT EXISTS admin_gate_verified BOOLEAN NOT NULL DEFAULT FALSE;
