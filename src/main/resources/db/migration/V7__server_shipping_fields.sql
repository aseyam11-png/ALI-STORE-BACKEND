ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS shipping_amount NUMERIC(10, 2) NOT NULL DEFAULT 0 CHECK (shipping_amount >= 0),
    ADD COLUMN IF NOT EXISTS shipping_method VARCHAR(20) NOT NULL DEFAULT 'STANDARD'
        CHECK (shipping_method IN ('STANDARD', 'EXPRESS'));