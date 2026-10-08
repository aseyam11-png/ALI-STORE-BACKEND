CREATE TABLE product_variants (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    sku VARCHAR(120) NOT NULL UNIQUE,
    option_name VARCHAR(80) NOT NULL,
    option_value VARCHAR(160) NOT NULL,
    price NUMERIC(10,2),
    available_quantity INTEGER NOT NULL DEFAULT 0 CHECK (available_quantity >= 0),
    image_url VARCHAR(2048),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_product_variant_option UNIQUE (product_id, option_name, option_value)
);
CREATE INDEX idx_product_variants_product_id ON product_variants(product_id, active);
ALTER TABLE cart_items ADD COLUMN IF NOT EXISTS variant_id BIGINT REFERENCES product_variants(id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_cart_items_product_variant
    ON cart_items (cart_id, product_id, COALESCE(variant_id, 0));
ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS variant_id BIGINT REFERENCES product_variants(id),
    ADD COLUMN IF NOT EXISTS variant_sku VARCHAR(120),
    ADD COLUMN IF NOT EXISTS variant_option_name VARCHAR(80),
    ADD COLUMN IF NOT EXISTS variant_option_value VARCHAR(160);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS variant_reservation_active BOOLEAN NOT NULL DEFAULT FALSE;
