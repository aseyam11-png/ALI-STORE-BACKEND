# ALI STORE Production Deployment

## Backend requirements

Deploy the Docker image produced by `Dockerfile` with:

- Java 17 runtime
- PostgreSQL
- TLS terminated at the public ingress/reverse proxy
- `SPRING_PROFILES_ACTIVE=prod`

Required environment variables:

```text
DB_URL=jdbc:postgresql://<host>:5432/<database>
DB_USERNAME=<database-user>
DB_PASSWORD=<database-password>

CORS_ALLOWED_ORIGINS=https://<your-frontend-domain>

FRONTEND_AUTH_SUCCESS_REDIRECT=https://<your-frontend-domain>/buyer
FRONTEND_AUTH_FAILURE_REDIRECT=https://<your-frontend-domain>/login
FRONTEND_PUBLIC_URL=https://<your-frontend-domain>

AUTH_COOKIE_SECURE=true
AUTH_COOKIE_SAME_SITE=Strict

STRIPE_SECRET_KEY=sk_live_...
STRIPE_WEBHOOK_SECRET=whsec_...
```

Do not commit any of these values to Git.

## Public endpoints used by the storefront

```text
GET  /actuator/health
GET  /api/v1/products
GET  /api/v1/products/{id}
GET  /api/v1/products/slug/{slug}
GET  /api/v1/categories
GET  /api/v1/shipping/methods
GET  /api/v1/promotions/active
GET  /api/v1/auth/csrf
POST /api/v1/visual-search
POST /api/v1/ai/ask
POST /api/v1/ai/discover
```

Authenticated browser mutations use the CSRF contract and send `X-XSRF-TOKEN`.

## Stripe

Configure a Stripe Checkout webhook to:

```text
https://<your-backend-domain>/api/v1/integrations/payments/stripe/webhook
```

The webhook must be delivered over HTTPS and must use the signing secret configured as `STRIPE_WEBHOOK_SECRET`.

The production payment path is:

```text
ALI STORE checkout
    -> POST /api/v1/orders
    -> POST /api/v1/payments
    -> Stripe Checkout Session
    -> Stripe webhook
    -> payment COMPLETED
    -> order PROCESSING
```

A hosted card payment remains `PENDING` until the signed webhook confirms the final payment state.

## Frontend production variables

Set at the frontend deployment:

```text
BACKEND_INTERNAL_URL=https://<your-backend-domain>
NEXT_PUBLIC_API_BASE_URL=
NEXT_PUBLIC_MEDIA_BASE_URL=<optional-public-media-base>

NEXT_PUBLIC_ONLINE_PAYMENTS_ENABLED=true
```

Keep `NEXT_PUBLIC_ONLINE_PAYMENTS_ENABLED=false` until Stripe live keys, the public backend URL, and the signed webhook are configured.

Never point a production deployment at `http://localhost:8080`.

## Database

Flyway migrations are part of the backend startup lifecycle. The current production schema additions include:

- product variants
- cart/order variant references
- shipping methods and order shipping totals
- promotions and promotion discounts
- hosted payment checkout URL

The database must be persistent; do not use an ephemeral test database for production.

## Release gate

A production release is not considered certified until all of the following are true:

1. Backend CI executes Maven tests successfully.
2. PostgreSQL integration/concurrency tests execute successfully.
3. Frontend CI executes lint/type/build/release gates successfully.
4. Frontend deployment can reach the backend over public HTTPS.
5. Stripe live webhook delivery is verified against the deployed backend.
6. A real checkout reaches `COMPLETED` only after webhook confirmation.
