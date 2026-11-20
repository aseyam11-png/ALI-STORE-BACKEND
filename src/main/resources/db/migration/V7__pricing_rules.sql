-- V7: persistent, supplier-cost-driven store pricing policy.
-- Rules are owned by the store. They are never exposed to customers as supplier data.

CREATE TABLE pricing_rules (
    id BIGSERIAL PRIMARY KEY,
    scope VARCHAR(20) NOT NULL CHECK (scope IN ('GLOBAL', 'CATEGORY', 'PRODUCT')),
    type VARCHAR(30) NOT NULL CHECK (type IN ('PERCENTAGE_MARKUP', 'FIXED_MARKUP')),
    rule_value NUMERIC(10, 4) NOT NULL CHECK (rule_value >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    priority INT NOT NULL DEFAULT 0,
    category_id BIGINT REFERENCES categories(id) ON DELETE CASCADE,
    product_id BIGINT REFERENCES products(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_pricing_rule_target CHECK (
        (scope = 'GLOBAL' AND category_id IS NULL AND product_id IS NULL)
        OR
        (scope = 'CATEGORY' AND category_id IS NOT NULL AND product_id IS NULL)
        OR
        (scope = 'PRODUCT' AND product_id IS NOT NULL AND category_id IS NULL)
    )
);

CREATE INDEX idx_pricing_rules_effective_category
    ON pricing_rules(category_id, active, priority DESC, id DESC);

CREATE INDEX idx_pricing_rules_effective_product
    ON pricing_rules(product_id, active, priority DESC, id DESC);

CREATE INDEX idx_pricing_rules_global
    ON pricing_rules(active, priority DESC, id DESC);
