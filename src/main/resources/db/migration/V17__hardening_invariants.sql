-- Persisted controls and supporting indexes; this migration is additive.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP WITHOUT TIME ZONE;

ALTER TABLE users
    ADD CONSTRAINT chk_users_failed_login_attempts_nonnegative
    CHECK (failed_login_attempts >= 0);

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
