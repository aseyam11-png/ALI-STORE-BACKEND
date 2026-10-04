ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS reservation_expires_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_orders_pending_reservation_expiry
    ON orders(status, reservation_expires_at)
    WHERE status = 'PENDING';
