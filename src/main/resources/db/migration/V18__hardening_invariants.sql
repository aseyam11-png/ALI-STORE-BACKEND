-- Order, inventory reservation, fulfillment, and catalog invariants.
-- Authentication and account-lockout columns are created by V17__authentication_lockout_and_version.sql.

CREATE TABLE IF NOT EXISTS inventory_reservations (
    id BIGSERIAL PRIMARY KEY,
    order_item_id BIGINT NOT NULL REFERENCES order_items(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    quantity INTEGER NOT NULL CHECK (quantity >= 1),
    status VARCHAR(24) NOT NULL CHECK (status IN ('RESERVED', 'COMMITTED', 'RELEASED')),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_inventory_reservations_order_item UNIQUE (order_item_id)
);

CREATE INDEX IF NOT EXISTS idx_inventory_reservations_product_status
    ON inventory_reservations(product_id, status);

-- Preserve ownership for active orders that predate this reservation ledger.
INSERT INTO inventory_reservations(order_item_id, product_id, quantity, status)
SELECT oi.id, oi.product_id, oi.quantity, 'RESERVED'
FROM order_items oi
JOIN orders o ON o.id = oi.order_id
WHERE o.status IN ('PENDING', 'PROCESSING')
  AND NOT EXISTS (
      SELECT 1
      FROM shipments s
      JOIN fulfillment_tasks t ON t.shipment_id = s.id
      WHERE s.order_id = o.id
        AND s.supplier_id = oi.supplier_id
        AND t.status = 'SUCCEEDED'
  );

CREATE INDEX IF NOT EXISTS idx_orders_pending_reservation_expiry
    ON orders(status, reservation_expires_at)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_order_items_product_id
    ON order_items(product_id);

CREATE INDEX IF NOT EXISTS idx_products_status_category_id
    ON products(status, category_id, id);

CREATE INDEX IF NOT EXISTS idx_products_status_brand_price
    ON products(status, brand_id, price);

CREATE INDEX IF NOT EXISTS idx_email_otp_user_purpose_created
    ON email_otp_challenges(user_id, purpose, created_at DESC);

ALTER TABLE fulfillment_tasks
    DROP CONSTRAINT IF EXISTS fulfillment_tasks_status_check;

ALTER TABLE fulfillment_tasks
    ADD CONSTRAINT fulfillment_tasks_status_check
    CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED',
                      'CANCELLED', 'RECONCILIATION_REQUIRED'));

CREATE INDEX IF NOT EXISTS idx_fulfillment_tasks_status_next_attempt_id
    ON fulfillment_tasks(status, next_attempt_at, id);
