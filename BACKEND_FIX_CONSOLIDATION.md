# ALI STORE Backend — Consolidation and Verification Report

## Baseline and scope

- Repository: `aboodSeyam1/ALI-STORE-BACKEND` (private).
- Baseline branch: `integration/release-candidate-20261025`.
- Baseline commit: `82251c4da1a8ecb7d7566c52bed02696ecfca939`.
- Work branch: `integration/all-verified-fixes-20261025`.
- No changes were made to `main` or to the original fix branches. The work branch was created from the baseline commit and is kept separate.
- This work used GitHub's repository file/branch APIs. No local working tree was mounted in this session, so local unstaged/untracked changes could not be inspected. No destructive Git commands were used.

## Branch comparison and consolidation decisions

| Branch | Verified tip | Decision |
|---|---|---|
| `main` | `9fa5a7a146b23d8f5c28f994c95cda24051cac57` | Retained as-is; not a merge target. |
| `fix/backend-hardening-20261019` | `8c59dc20ab0d1ff029710e904a8bd07f146cd568` | Its tip is an ancestor of the release candidate; no blind replay. |
| `fix/backend-invariant-hardening-20261020` | `61029b5258cccc62fe010ce12f64acf8fa6cf951` | Five commits are unique to that side branch. The older reservation design keys reservations by order/product, while the candidate migration and entity model key them by order line. The five commits were not transplanted blindly because that would risk conflicting reservation ownership/integrity semantics. |
| `fix/auth-session-csrf-contracts-20261020` | `33feac3ff8edd8bf85320380bc5078cc4b46cda8` | Compared file-level changes. Applied focused compatible changes only; did not copy the whole branch because its controller/security configuration predates the candidate's cross-origin CSRF response-header contract. |
| `fix/cross-origin-csrf-response-header-20261021` | `bc70f69c5109b90ffe83456cf6f68ffa14e9227a` | Ancestor of the release candidate. Preserved the candidate's CSRF response header, no-store cache policy, and CORS exposure. |
| `integration/verified-hardening-20261020` | `53583416487ca1cba7d8b9aabac116134e204560` | Ancestor of the release candidate; no whole-branch merge needed. |

## Changes made on the independent work branch

1. `src/main/java/com/store/search/service/SearchService.java`
   - Replaced invalid escaped Unicode character comparisons with hexadecimal code-point values for Arabic-Indic digits, Persian digits, tatweel, and the existing diacritic range.
   - Added regression tests for Arabic-Indic and Persian digit normalization and short normalized queries.
2. `src/main/java/com/store/common/security/JwtAuthenticationFilter.java`
   - Added the missing import for the existing `AccountVersionAware` interface; no duplicate type was created.
3. `src/main/java/com/store/auth/service/JwtService.java`
   - Added the missing `io.jsonwebtoken.JwtException` import; JWT validation behavior was not changed.
4. `src/main/java/com/store/visualsearch/service/VisualSearchService.java`
   - Replaced the unsupported `Map.keys()` call with iteration through `keySet()` to select a cache key for eviction.
   - Removed a constructor that explicitly assigned `candidateService = null`, which could produce a null dereference when `search()` was called. Retained one constructor with the dependency required and updated affected unit-test construction.
5. `src/test/java/com/store/ai/AskAliServiceTest.java`
   - Changed `AiProductResponse` access from `getId()` to the record accessor `id()`.
6. `src/test/java/com/store/common/security/CookieCsrfProtectionTest.java` and `src/test/java/com/store/common/security/SecurityCorsContractTest.java`
   - Added the missing static imports for `assertEquals`; existing security assertions were retained.
7. `src/test/java/com/store/visualsearch/service/SecureImageFetcherTest.java`
   - Passed `minSimilarity=60`, which matches the documented constructor default and the validation range (0–100).
8. `src/main/java/com/store/shipping/repository/ShippingAddressRepository.java`
   - Made the default-address bulk update flush pending writes and clear the persistence context, reducing stale managed-entity risk after a JPQL bulk update.
9. `src/main/java/com/store/auth/repository/EmailOtpChallengeRepository.java`
   - Added an unlocked lookup for reading the saved OTP challenge by ID; retained the pessimistic lock in `findForVerification` for active verification flows.
10. `src/test/java/com/store/auth/AuthSecurityRegressionIntegrationTest.java` and `src/test/java/com/store/auth/AuthSecurityLegacyRegressionIntegrationTest.java`
    - Updated assertions that only inspect persisted OTP state to use the unlocked lookup rather than acquiring a verification lock.
11. `src/main/resources/application-test.yml`
    - Explicitly set `app.auth.cookie-same-site: Strict`, aligning the test configuration with the cookie contract already enforced by the service default.
12. `BACKEND_FIX_CONSOLIDATION.md`
    - Added this report of baseline, selected changes, verification limits, and outstanding risks.

## Verification performed

- Confirmed the candidate ref and selected branch refs via the GitHub API.
- Compared branch ancestry and per-file change summaries; examined relevant commit patches before choosing focused changes.
- Read the complete contents of migrations V1 through V20. This was static review only; no migrations were executed against any database.
- Read the primary Maven configuration, application profiles, CI workflow, and selected production/test files.
- Re-fetched selected files after creating the first two commits and verified that the intended edits are present in the work branch.

### Checks not run / evidence unavailable

- Maven `compile`, `test-compile`, targeted tests, `clean verify`, `git diff --check`, and Testcontainers/PostgreSQL tests were **not run in this session** because no local terminal or Maven runtime was available through the connected GitHub interface.
- Existing Actions runs for the candidate showed the Maven job failing and the PostgreSQL job skipped. Attempts to retrieve their logs returned GitHub `404 BlobNotFound`; the underlying CI failure message therefore remains unknown and is not attributed to a specific source error.
- Graphify was not run because the CLI/terminal is unavailable in this session. The source changes were reviewed statically instead.
- The candidate workflow's branch filters are configured for `main`; a Draft PR targeting the release-candidate branch may not automatically trigger that workflow unless the workflow configuration is extended or manually dispatched.

## Migrations reviewed

All migration files V1–V20 were read statically: initial roles/users, catalog, commerce tables, internal dropshipping supplier foundation, legacy marketplace cleanup, checkout idempotency, pricing rules, token revocation, email OTP, Google identities, supplier inventory discrepancies, payment/refund integrity, reservation expiry, supplier webhook idempotency, order currency, shipping addresses, authentication lockout/version, inventory-reservation invariants, OTP auth-version binding, and supplier product identifiers/store SKUs.

No migration file was modified or executed. Production-schema compatibility and the V18 reservation backfill remain unverified by a real PostgreSQL run.

## Known risks / remaining work

- The cause of the recent GitHub Actions failures remains unknown because downloadable job logs were unavailable.
- The branch still requires Maven compilation, complete test-suite execution, and the documented Testcontainers/PostgreSQL concurrency run before the fixes can be called verified.
- Graphify dependency tracing remains outstanding.
- The production profile intentionally has no real `PaymentProvider`; production startup needs a genuine gateway implementation/configuration. The mock provider is explicitly non-production and does not prove provider-side idempotency.
- `SecureImageFetcher` checks DNS-resolved addresses before the HTTP connection resolves/connects again. A DNS-rebinding/TOCTOU SSRF risk should be specifically reviewed and tested; this report does not claim a confirmed exploit.
- V2/V15 migrations default/backfill currency using USD. Confirm the canonical store currency contract before changing historical persisted amounts; no currency migration was altered here.
- The release-candidate-targeted Draft PR may not run CI because the existing workflow listens for PRs targeting `main` only.

## Final state

The changes are isolated to `integration/all-verified-fixes-20261025`. No original fix branch or `main` was updated. Do not treat this branch as release-ready until Maven, security-contract tests, migration execution, and real PostgreSQL concurrency checks produce recorded passing results.
