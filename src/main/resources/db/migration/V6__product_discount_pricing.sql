-- Customer-facing reference price for legitimate sale discounts.
-- NULL means the product currently has no reference/compare-at price.
ALTER TABLE products
    ADD COLUMN compare_at_price NUMERIC(10, 2);

ALTER TABLE products
    ADD CONSTRAINT chk_products_compare_at_price
    CHECK (compare_at_price IS NULL OR compare_at_price >= 0);
