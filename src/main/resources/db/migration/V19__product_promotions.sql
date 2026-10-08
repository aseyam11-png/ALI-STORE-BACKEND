CREATE TABLE promotions (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    title VARCHAR(160) NOT NULL,
    promotion_type VARCHAR(30) NOT NULL,
    discount_value NUMERIC(10,2) NOT NULL CHECK (discount_value > 0),
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    flash_sale BOOLEAN NOT NULL DEFAULT FALSE,
    priority INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_promotion_dates CHECK (ends_at > starts_at),
    CONSTRAINT ck_promotion_type CHECK (promotion_type IN ('PERCENTAGE','FIXED_AMOUNT'))
);
CREATE INDEX idx_promotions_product_active_window
    ON promotions(product_id, active, starts_at, ends_at, priority);
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS promotion_discount_amount NUMERIC(10,2) NOT NULL DEFAULT 0;
