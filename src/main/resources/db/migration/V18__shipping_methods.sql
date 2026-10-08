CREATE TABLE shipping_methods (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(500),
    price NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (price >= 0),
    currency VARCHAR(3) NOT NULL,
    estimated_min_days INTEGER NOT NULL CHECK (estimated_min_days >= 0),
    estimated_max_days INTEGER NOT NULL CHECK (estimated_max_days >= estimated_min_days),
    country_code VARCHAR(2),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    priority INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_shipping_method_scope UNIQUE (code, country_code)
);
CREATE INDEX idx_shipping_methods_public ON shipping_methods(active, country_code, priority);
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS shipping_method_code VARCHAR(60),
    ADD COLUMN IF NOT EXISTS shipping_amount NUMERIC(10,2) NOT NULL DEFAULT 0;
