# Production API and administrator security gate

## Required production configuration

Set `SPRING_PROFILES_ACTIVE=prod`. The production profile intentionally refuses plain HTTP requests and requires secure session cookies. It expects TLS to terminate at the trusted reverse proxy/load balancer, with `X-Forwarded-Proto: https` handled by Tomcat's native trusted-proxy configuration.

Configure these values in the backend hosting provider's secret/environment settings, not in Git:

- `JWT_SECRET`: Base64-encoded random key of at least 32 bytes.
- `AUTH_COOKIE_ENCRYPTION_KEY`: a separate Base64-encoded random key of at least 32 bytes. Do not reuse `JWT_SECRET`.
- `AUTH_COOKIE_SECURE=true` and `AUTH_COOKIE_SAME_SITE=Strict`.
- `CORS_ALLOWED_ORIGINS`: exact storefront origin(s), comma-separated; never use `*` with credentials.
- `ADMIN_GATE_PASSWORD_HASH`: BCrypt hash of a dedicated, high-entropy second administrator passphrase, generated with BCrypt cost 12. Do not store the plaintext passphrase in environment configuration or source control.
- `TURNSTILE_SECRET_KEY`: Cloudflare Turnstile server-side secret.
- `TURNSTILE_HOSTNAME`: exact hostname allowed by the Turnstile site configuration.
- `NEXT_PUBLIC_TURNSTILE_SITE_KEY`: the corresponding public site key in the frontend/Vercel environment.
- The storefront login form must submit `adminGatePassword` and the one-time `captchaToken` when the account is an administrator; normal customer login does not need either field. The corresponding frontend change and an end-to-end browser test are still required before enabling this gate for live administrator users.
- `APP_TRUSTED_PROXIES`: exact trusted proxy IP literals, comma-separated, only when the deployment has fixed proxy addresses and the application is certain that only those addresses can connect directly.

The application fails to start if the production admin gate is enabled without its password hash, Turnstile secret, and expected hostname. The admin password is checked with BCrypt; CAPTCHA is verified server-to-server with Cloudflare and the expected action/hostname are checked. CAPTCHA verification fails closed when Cloudflare is unavailable.

## Lockout and rate limits

The login endpoint limits requests and temporarily blocks an IP after eight failed login/gate attempts within 15 minutes. The temporary block lasts 15 minutes and responses include `Retry-After`. All API paths also have a baseline per-IP request limit, with a tighter limit for admin API routes.

The current counters are process-local. They provide protection for a single backend instance, but do not coordinate across multiple instances or survive restarts. The admin API has a 120-request/minute per-IP ceiling and authentication/expensive routes have stricter endpoint limits. General catalog/product APIs should be protected by a production WAF/edge limit where the real client IP is known, rather than a blanket backend IP cap that could combine multiple customers behind one proxy. If the backend is scaled horizontally, move lockout counters to a shared atomic store (for example Redis) and keep edge/WAF limits enabled. Do not trust `X-Forwarded-For` unless the immediate peer is explicitly trusted; dynamic proxy networks should be rate-limited at the edge instead of adding broad unverified trust ranges.

## Payment safety

- The development mock provider is limited to explicit `dev` and `test` profiles.
- In `prod`, `UnavailablePaymentProvider` allows non-payment APIs to start while safely declining online charges without creating a fake transaction until a real gateway is configured. Cash on delivery remains separate.
- Enabling card/PayPal checkout requires a real, tested gateway integration; do not treat a declined/unconfigured payment as a successful order payment.

## Encryption and API access

- Require HTTPS in the production profile; HSTS is enabled and the session cookie is `Secure`, `HttpOnly`, and `SameSite=Strict`.
- Authentication cookies are protected with AES-GCM using a separate key, and JWTs are HMAC-signed with a minimum 256-bit key.
- User passwords and the additional admin passphrase are stored as BCrypt hashes, not reversibly encrypted values.
- CSRF protection is enabled for unsafe browser requests using cookies. The supplier shipment webhook is the only mutation route exempt by path and must rely on its independent HMAC/timestamp verification. Requests explicitly authenticated with a non-empty Bearer token are exempt from the cookie-CSRF flow by the existing security matcher.
- Admin-role authority is removed from JWTs that do not contain the signed admin-gate verification claim. Old tokens and OAuth-created admin tokens therefore cannot bypass the second gate; administrators must sign in through the gated password flow.

TLS is the encryption for API traffic in transit. It is not a substitute for database-at-rest encryption, key rotation, backups, or a shared rate-limit store, which remain separate operational controls.
