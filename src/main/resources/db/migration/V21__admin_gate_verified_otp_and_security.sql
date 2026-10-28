ALTER TABLE email_otp_challenges
    ADD COLUMN IF NOT EXISTS admin_gate_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Existing active challenges were issued before the second admin gate existed.
-- Their default FALSE ensures they cannot mint a gate-verified admin JWT.
