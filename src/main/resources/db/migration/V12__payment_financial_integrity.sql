ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255),
    ADD COLUMN IF NOT EXISTS collection_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUIRED',
    ADD COLUMN IF NOT EXISTS provider_error VARCHAR(1000);

UPDATE payments
SET idempotency_key = 'order-' || order_id
WHERE idempotency_key IS NULL;

UPDATE payments
SET collection_status = 'PENDING'
WHERE method = 'CASH_ON_DELIVERY'
  AND status <> 'REFUNDED';

ALTER TABLE payments
    DROP CONSTRAINT IF EXISTS payments_status_check;

ALTER TABLE payments
    ADD CONSTRAINT payments_status_check
    CHECK (status IN (
        'PENDING','COMPLETED','FAILED',
        'REFUND_PENDING','REFUNDED','REQUIRES_RECONCILIATION'
    ));

ALTER TABLE payments
    ADD CONSTRAINT payments_collection_status_check
    CHECK (collection_status IN ('NOT_REQUIRED','PENDING','COLLECTED'));

ALTER TABLE payments
    ALTER COLUMN idempotency_key SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_payments_idempotency_key
    ON payments(idempotency_key);

CREATE TABLE IF NOT EXISTS refunds (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES payments(id),
    amount NUMERIC(10,2) NOT NULL CHECK (amount > 0),
    status VARCHAR(30) NOT NULL CHECK (
        status IN ('PENDING','SUCCEEDED','FAILED','REQUIRES_RECONCILIATION')
    ),
    reason VARCHAR(500),
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    provider_refund_id VARCHAR(255) UNIQUE,
    last_error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_refunds_payment_id ON refunds(payment_id);
CREATE INDEX IF NOT EXISTS idx_refunds_status ON refunds(status);
