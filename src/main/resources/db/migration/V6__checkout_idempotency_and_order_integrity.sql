-- V6: durable checkout idempotency for the B2C customer checkout flow.
-- The key is scoped to the authenticated customer so retries are safe without
-- allowing one customer to probe or claim another customer's order key.

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uk_orders_user_idempotency_key
    ON orders(user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_orders_user_created_at
    ON orders(user_id, created_at DESC);
