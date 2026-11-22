# Production payment availability

## Current policy

Until a real payment gateway is integrated and independently validated:

- Cash on delivery (COD) remains available through the dedicated COD path.
- Credit card and PayPal are unavailable in the production profile.
- Unsupported electronic methods are rejected with HTTP 503 before a payment attempt is reserved or reopened.
- Rejected attempts do not create a payment row or advance the order.
- The production provider never fabricates a successful charge or transaction identifier.
- The mock provider is restricted to non-production profiles and must never process real customer payments.

The frontend should hide or disable unavailable electronic methods based on an explicit backend capability/configuration contract. A frontend-only restriction is not a security control; the backend remains authoritative.

## Requirements before enabling electronic payments

A real provider integration must include:

1. Provider-side idempotency for charge and refund operations.
2. Authenticated webhook signature verification, replay protection, and event deduplication.
3. Explicit handling of unknown outcomes and reconciliation against provider state.
4. Refund confirmation and settlement reconciliation.
5. Sandbox and failure-path tests, including timeouts, retries, duplicate webhooks, and partial failures.
6. Operational alerting and auditable transaction references.

This document describes the current fail-closed policy. It is not a declaration that Production is launch-ready.
