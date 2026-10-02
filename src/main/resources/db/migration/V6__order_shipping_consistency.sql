-- V6: server-owned shipping values.
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS shipping_amount NUMERIC(10, 2) NOT NULL DEFAULT 0
        CHECK (shipping_amount >= 0);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS shipping_method VARCHAR(30) NOT NULL DEFAULT 'STANDARD';

CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at DESC);
