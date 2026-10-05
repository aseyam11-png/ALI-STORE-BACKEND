-- V6: prevent pending checkout reservations from holding inventory forever.
-- Orders created from checkout reserve stock before payment; a pending order must
-- therefore have a finite reservation lifetime. The application releases the
-- reservation and transitions the order to CANCELLED after the deadline.

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS reservation_expires_at TIMESTAMP WITH TIME ZONE;

UPDATE orders
SET reservation_expires_at = created_at + INTERVAL '30 minutes'
WHERE reservation_expires_at IS NULL;

ALTER TABLE orders
    ALTER COLUMN reservation_expires_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_orders_pending_reservation_expiry
    ON orders(status, reservation_expires_at);
