# Production payment availability

## Current policy

No production electronic payment gateway is integrated yet. The `prod` Spring profile therefore provides `DisabledProductionPaymentProvider` instead of the development/test `MockPaymentProvider`.

- `CREDIT_CARD` and `PAYPAL` are not supported in Production.
- `PaymentService` checks provider capability after the CASH_ON_DELIVERY branch and before reserving/reopening a payment attempt.
- An unsupported electronic-payment request returns HTTP 503 through `ServiceUnavailableException`.
- Rejected electronic-payment requests do not create a `Payment` row, call a gateway, or change the order status.
- `CASH_ON_DELIVERY` remains available and does not call `PaymentProvider`.
- The production provider's defensive `charge` implementation throws; it never returns a successful payment result.

This is a fail-closed runtime policy, not a real payment integration. Do not advertise credit-card or PayPal checkout as available in Production until a real provider is implemented and verified.

## Requirements before enabling an electronic payment method

A production provider must:

1. Declare only payment methods it actually supports through `PaymentProvider.supports`.
2. Implement charge, refund, and charge-status reconciliation against the provider's authoritative API.
3. Enforce provider-side idempotency using the stable key supplied by `PaymentService`.
4. Distinguish definitive declines from unknown outcomes; unknown outcomes must remain in reconciliation.
5. Verify webhook signatures, event idempotency, amount/currency/order binding, and replay behavior.
6. Include contract tests and sandbox integration tests for success, decline, timeout-before-charge, timeout-after-charge, refund, and reconciliation.
7. Be enabled in Production only after review of credentials, callback URLs, monitoring, and operational recovery procedures.

## Release boundary

This change only allows Spring to construct the Production payment services while keeping electronic payments disabled. It does not:
- configure or run Flyway against Production;
- populate the currently empty Production schema;
- enable a real payment gateway;
- deploy or promote a Vercel Production deployment;
- make the overall store launch-ready.

Production release remains blocked until the database migration workflow, runtime smoke tests, authentication/OTP release gates, fulfillment/payment scope, and deployment configuration are separately verified.
