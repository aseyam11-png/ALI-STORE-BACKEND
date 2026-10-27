-- Main already contains migrations V17-V20; this change must remain monotonic.
ALTER TABLE email_otp_challenges
    ADD COLUMN IF NOT EXISTS admin_gate_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Pre-gate OTP challenges cannot prove the second admin factor.
