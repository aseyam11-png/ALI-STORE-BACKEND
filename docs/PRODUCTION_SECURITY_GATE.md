# Production administrator security gate

## Required production configuration

Activate `SPRING_PROFILES_ACTIVE=prod`. Put secrets in the hosting provider's secret manager, never in Git:

- `JWT_SECRET`: Base64-encoded, at least 32 random bytes.
- `AUTH_COOKIE_ENCRYPTION_KEY`: a separate Base64 key of at least 32 random bytes.
- `AUTH_OTP_HASH_SECRET`: a separate Base64 key of at least 32 random bytes.
- `AUTH_COOKIE_SECURE=true`; choose `AUTH_COOKIE_SAME_SITE` for the actual storefront/API topology.
- `CORS_ALLOWED_ORIGINS`: exact storefront origin(s), comma-separated; never `*` with credentials.
- `ADMIN_GATE_PASSWORD_HASH`: BCrypt cost-12 hash of a unique administrator second passphrase; never store its plaintext in Git.
- `TURNSTILE_SECRET_KEY` and `TURNSTILE_HOSTNAME`: the server-side Cloudflare secret and exact expected hostname.
- `NEXT_PUBLIC_TURNSTILE_SITE_KEY`: the matching public site key in the frontend hosting environment.
- `APP_TRUSTED_PROXIES`: only verified immediate proxy IPs when stable proxy addresses are known.

The production profile requires HTTPS, enables the admin gate, and deliberately has no localhost CORS fallback. Missing or invalid gate configuration prevents startup.

## Administrator login flow

1. Validate the account email and password.
2. For `ROLE_ADMIN`, verify the second administrator passphrase and Turnstile server-side.
3. If email/login OTP is required, persist `admin_gate_verified` on the challenge and carry it through verification and resend.
4. Issue a JWT with signed `admin_gate_verified=true` only after successful completion of the required flow.
5. While the production gate is enabled, remove `ROLE_ADMIN` from legacy or OAuth-issued JWTs without that signed claim.

Turnstile verification fails closed for provider/network errors and checks success, action `admin_login`, and hostname. Login errors are normalized so callers are not told which credential or check failed.

## Limits and constraints

- Admin API routes have an IP-based request ceiling. Failed login/gate checks trigger a process-local 15-minute IP block after eight failures in fifteen minutes.
- The existing database-backed per-account lockout remains in place; the IP lockout complements it.
- These IP counters do not coordinate across replicas and reset on restart. Multi-instance production needs shared atomic storage (for example Redis) and an edge/WAF limit.
- The frontend login form must render Turnstile and send `adminGatePassword` and `captchaToken` for administrators. Backend changes alone do not finish the UI.
- Before production rollout, test regular customer login, gated admin login, failed/expired/replayed CAPTCHA, OTP issue/resend/verification, legacy admin JWT stripping, CSRF, OAuth admin behavior, and the production database migration in staging.
