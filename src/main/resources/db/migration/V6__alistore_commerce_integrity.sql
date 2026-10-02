-- V6: close the remaining persistence gaps between the ALI STORE frontend contract
-- and the canonical single-merchant dropshipping domain.

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS discount_percentage NUMERIC(5, 2) NOT NULL DEFAULT 0;

ALTER TABLE products
    DROP CONSTRAINT IF EXISTS chk_products_discount_percentage;

ALTER TABLE products
    ADD CONSTRAINT chk_products_discount_percentage
    CHECK (discount_percentage >= 0 AND discount_percentage <= 100);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS subtotal_amount NUMERIC(10, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS shipping_amount NUMERIC(10, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS shipping_method VARCHAR(30) NOT NULL DEFAULT 'STANDARD';

ALTER TABLE orders
    DROP CONSTRAINT IF EXISTS chk_orders_subtotal_amount;

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_subtotal_amount
    CHECK (subtotal_amount >= 0);

ALTER TABLE orders
    DROP CONSTRAINT IF EXISTS chk_orders_shipping_amount;

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_shipping_amount
    CHECK (shipping_amount >= 0);

ALTER TABLE orders
    DROP CONSTRAINT IF EXISTS chk_orders_shipping_method;

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_shipping_method
    CHECK (shipping_method IN ('STANDARD', 'EXPRESS'));

UPDATE orders
SET subtotal_amount = GREATEST(total_amount + COALESCE(discount_amount, 0), 0)
WHERE subtotal_amount = 0
  AND total_amount + COALESCE(discount_amount, 0) > 0;
