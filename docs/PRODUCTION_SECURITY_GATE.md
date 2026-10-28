# Production API and administrator security gate

## Required production configuration

Set `SPRING_PROFILES_ACTIVE=prod`. Production requires HTTPS and secure session cookies. TLS is expected to terminate at the trusted reverse proxy/load balancer, with forwarded headers handled only from trusted proxies.

Configure these in the backend host's secret/environment settings, not in Git:

- `JWT_SECRET`: Base64-encoded random key of at least 32 bytes.
- `AUTH_COOKIE_ENCRYPTION_KEY`: a separate Base64-encoded random key of at least 32 bytes; do not reuse `JWT_SECRET`.
- `AUTH_COOKIE_SECURE=true` and `AUTH_COOKIE_SAME_SITE=Strict`, unless the frontend/API site relationship requires a documented alternative.
- `CORS_ALLOWED_ORIGINS`: exact storefront origin(s), comma-separated; never use `*` with credentials.
- `ADMIN_GATE_PASSWORD_HASH`: BCrypt cost-12 hash of a dedicated second administrator passphrase. Do not store plaintext in environment variables or source control.
- `TURNSTILE_SECRET_KEY`: Cloudflare Turnstile server-side secret.
- `TURNSTILE_HOSTNAME`: exact hostname registered for the Turnstile site.
- The frontend needs the corresponding public Turnstile site key and must send `adminGatePassword` and `captchaToken` in administrator login requests. The gate is enabled in the production profile, so administrators cannot sign in through the old password-only form.
- `APP_TRUSTED_PROXIES`: exact proxy IP literals only when the backend can verify that only those peers connect directly. Otherwise use the platform's edge/WAF rate limiting and keep forwarded headers untrusted.

The application fails startup if the production administrator gate is enabled without a valid BCrypt cost-12 hash, Turnstile secret, and expected hostname. CAPTCHA verification is server-to-server and checks success, the `admin_login` action, and exact hostname. Provider failures fail closed.

## Login controls and limitations

- Primary account credentials are checked before the secondary administrator passphrase and CAPTCHA.
- The second gate proof is stored on the email OTP challenge, preserved during resend, and copied into the signed JWT only after OTP completion. The JWT filter strips `ROLE_ADMIN` from old or OAuth-issued tokens without the signed gate claim.
- Login failure handling uses the existing database-backed per-account login lockout plus a process-local per-IP guard. The admin API has a shared per-IP limit of 120 requests per minute across admin endpoints.
- Process-local limits do not coordinate across replicas or survive restarts. For multiple backend replicas, move IP/account counters to shared atomic storage and keep edge/WAF limits enabled.

## Migrations and rollout

The production main branch already uses migrations V17 through V20. The admin-gate OTP column is therefore introduced as `V21__admin_gate_verified_otp_and_security.sql` to avoid changing an existing migration identifier. Existing OTP challenges receive `admin_gate_verified=false` so they cannot be used to mint administrator-gate tokens.

Apply migrations in staging first, then test administrator login from the actual storefront: primary password, Turnstile action/hostname verification, OTP, session cookie, and protected admin API authorization. Do not treat unit tests as proof of live provider configuration or frontend compatibility.
