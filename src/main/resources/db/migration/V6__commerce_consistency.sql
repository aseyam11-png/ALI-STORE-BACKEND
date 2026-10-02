-- Keep the customer-facing catalog strictly publishable and make cart quantity
-- changes explicit. The Java layer enforces the same rules server-side.

CREATE INDEX IF NOT EXISTS idx_products_status_category ON products(status, category_id);
CREATE INDEX IF NOT EXISTS idx_products_status_brand ON products(status, brand_id);

ALTER TABLE products
    DROP CONSTRAINT IF EXISTS chk_products_price_nonnegative;

ALTER TABLE products
    ADD CONSTRAINT chk_products_price_nonnegative
    CHECK (price >= 0);
