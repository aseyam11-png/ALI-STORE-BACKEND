CREATE TABLE pricing_rules (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    product_id BIGINT NULL REFERENCES products(id) ON DELETE CASCADE,
    category_id BIGINT NULL REFERENCES categories(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL CHECK (type IN ('FIXED','PERCENTAGE')),
    value NUMERIC(10, 2) NOT NULL CHECK (value >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    priority INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CHECK (NOT (product_id IS NOT NULL AND category_id IS NOT NULL))
);

CREATE INDEX idx_pricing_rules_product ON pricing_rules(product_id);
CREATE INDEX idx_pricing_rules_category ON pricing_rules(category_id);
CREATE INDEX idx_pricing_rules_active_priority ON pricing_rules(active, priority DESC);
