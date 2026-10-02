-- V8: close remaining production correctness gaps without changing the single-merchant dropshipping model.

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3);

UPDATE orders o
SET currency = COALESCE(
    (
        SELECT p.currency
        FROM order_items oi
        JOIN products p ON p.id = oi.product_id
        WHERE oi.order_id = o.id
        ORDER BY oi.id
        LIMIT 1
    ),
    'USD'
)
WHERE o.currency IS NULL;

ALTER TABLE orders
    ALTER COLUMN currency SET DEFAULT 'USD',
    ALTER COLUMN currency SET NOT NULL;

ALTER TABLE fulfillment_tasks
    DROP CONSTRAINT IF EXISTS fulfillment_tasks_status_check;

ALTER TABLE fulfillment_tasks
    ADD CONSTRAINT fulfillment_tasks_status_check
    CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELLED'));

CREATE TABLE IF NOT EXISTS supplier_webhook_events (
    id BIGSERIAL PRIMARY KEY,
    supplier_code VARCHAR(80) NOT NULL,
    event_id VARCHAR(150) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_supplier_webhook_event UNIQUE (supplier_code, event_id)
);

CREATE INDEX IF NOT EXISTS idx_supplier_webhook_events_received_at
    ON supplier_webhook_events(received_at);

CREATE TABLE IF NOT EXISTS revoked_tokens (
    id BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_revoked_tokens_expires_at
    ON revoked_tokens(expires_at);

CREATE UNIQUE INDEX IF NOT EXISTS ux_payments_transaction_id
    ON payments(transaction_id)
    WHERE transaction_id IS NOT NULL;
