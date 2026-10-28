# Administrator login and release safeguards

## Production environment variables

The prod profile now intentionally requires the following settings. Set them in the deployment platform; do not commit their values or place secrets in frontend environment variables.

- **CORS_ALLOWED_ORIGINS**: exact deployed frontend origins, comma-separated if there is more than one, for example https://shop.example.com. Use origins only (scheme, hostname and optional port), without paths or trailing slashes.
- **ADMIN_GATE_PASSWORD_HASH**: a BCrypt hash for a separate administrator gate password. The backend uses BCryptPasswordEncoder. Generate the hash in a trusted local environment and store only the resulting hash as the server environment variable. Do not reuse the administrator's primary account password.
- **CLOUDFLARE_TURNSTILE_SECRET_KEY**: server-side Cloudflare Turnstile secret. Never expose this value to the browser.
- **TURNSTILE_EXPECTED_HOSTNAME**: recommended production hostname, for example shop.example.com. When configured, successful siteverify responses for a different hostname are rejected.

A safe way to generate the BCrypt value without printing the plaintext password is to run a small trusted Java program using new BCryptPasswordEncoder().encode(new String(System.console().readPassword())) and copy only its output into the server environment. Confirm that the value is accepted by the same PasswordEncoder used by this application.

## Frontend contract required

The backend accepts two additional JSON fields on POST /api/v1/auth/login:

- **adminGatePassword**: the separate administrator password.
- **turnstileToken**: the one-time response token returned by the Cloudflare Turnstile widget.

They are enforced only after the primary email/password credentials identify ROLE_ADMIN; normal customer login remains unchanged. The backend verifies the Turnstile token server-side, with short connection/read timeouts, and returns the same generic authentication failure for a rejected factor. The token and gate password are excluded from LoginRequest.toString().

The frontend must render Turnstile on the administrator login path, provide the public site key to the widget, and send both fields. This backend branch does not modify the frontend repository. Google OAuth remains blocked for ROLE_ADMIN; it must not be used to bypass the administrator gate.

## Late payment reconciliation behavior

Reconciliation locks the order before the payment row, matching the order-cancellation lock order. If a provider later confirms a real card charge for an order already marked CANCELLED, the backend creates or reuses the cancellation refund intent with the stable key order:<orderId>:cancellation. It does not move the cancelled order back into fulfillment. Refund processing still depends on the configured payment provider supporting refunds and on the refund worker/reconciliation completing.

## Protecting main in GitHub

The GitHub API connection used for this task could not read or change repository branch-protection/ruleset settings (the branch-protection endpoint returned 403 for the integration; rulesets access was also denied). This is therefore an owner action, not a completed code change.

In repository settings, create a ruleset targeting main and require pull requests before merging, at least one approving review (where available), and successful status checks from ALI STORE Backend CI / Maven unit/integration suite and ALI STORE Backend CI / Real PostgreSQL concurrency suite. Block force pushes and deletion. Review the separate test-and-merge workflow before enabling auto-merge; it is hard-coded to one integration branch, so its scope should be intentional. Verify the resulting rule in GitHub Settings after saving.
