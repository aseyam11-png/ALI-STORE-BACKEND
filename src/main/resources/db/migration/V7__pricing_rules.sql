CREATE TABLE pricing_rules (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    scope VARCHAR(20) NOT NULL,
    rule_type VARCHAR(20) NOT NULL,
    markup_value NUMERIC(12,2) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    product_id BIGINT REFERENCES products(id) ON DELETE CASCADE,
    category_id BIGINT REFERENCES categories(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pricing_rules_active_priority
    ON pricing_rules(active, priority DESC);

CREATE INDEX idx_pricing_rules_product
    ON pricing_rules(product_id);

CREATE INDEX idx_pricing_rules_category
    ON pricing_rules(category_id);

ALTER TABLE pricing_rules
    ADD CONSTRAINT chk_pricing_rule_target
    CHECK (
        (scope = 'PRODUCT' AND product_id IS NOT NULL AND category_id IS NULL)
        OR
        (scope = 'CATEGORY' AND category_id IS NOT NULL AND product_id IS NULL)
    );

ALTER TABLE pricing_rules
    ADD CONSTRAINT chk_pricing_rule_value
    CHECK (markup_value >= 0);
