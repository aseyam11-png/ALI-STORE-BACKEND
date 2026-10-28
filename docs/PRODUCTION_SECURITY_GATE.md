# Production administrator login gate

## Behavior
- A normal customer keeps the existing account-password and email-OTP flow.
- An administrator must pass the account password, a dedicated administrator passphrase, and Cloudflare Turnstile.
- Turnstile is verified server-to-server and accepted only for action `admin_login` and the configured hostname.
- Gate proof is stored on any email OTP challenge created after the gate, and copied into the signed JWT only after OTP completion.
- When enabled, JWTs without the signed proof do not keep `ROLE_ADMIN`, including legacy and OAuth-issued tokens.
- Production requires HTTPS and fails startup if the gate configuration is absent or malformed.

## Required environment
Set these as deployment secrets, never in Git:
- `SPRING_PROFILES_ACTIVE=prod`
- `ADMIN_GATE_PASSWORD_HASH`: BCrypt cost-12 hash for a separate passphrase; raw passphrase length must be 16–72 UTF-8 bytes.
- `TURNSTILE_SECRET_KEY` and `TURNSTILE_HOSTNAME`
- `NEXT_PUBLIC_TURNSTILE_SITE_KEY` in the frontend deployment
- Exact `CORS_ALLOWED_ORIGINS`, `AUTH_COOKIE_SECURE=true`, and the correct `AUTH_COOKIE_SAME_SITE`
- Existing strong JWT/OTP keys, PostgreSQL, SMTP, and payment-provider configuration.

The frontend must send `adminGatePassword` and `captchaToken` for administrator login. This backend PR does not implement the widget or the second-password field in the frontend, so production enablement requires a matching frontend change.

## Rate limits and boundaries
- Eight failed login/gate attempts in 15 minutes block the resolved IP for 15 minutes.
- Admin API calls share a 120-request/minute in-process per-IP ceiling.
- Existing database-backed per-account lockout and endpoint-specific rate limits remain in place.
- IP counters are process-local and reset on restart; multiple replicas need a shared atomic store and an edge/WAF limit.
- Trust forwarded addresses only from configured proxies and document the deployment's network topology.

## Release checks
Do not enable the gate in production until backend Maven/PostgreSQL CI passes; the frontend submits the gate fields; staging proves a valid admin login through OTP and rejects legacy/OAuth admin tokens without gate proof; and migration V21 is applied successfully.
