-- V6: canonical ALI STORE B2C seed + pricing rules.
-- This migration gives a fresh development database a real catalog, one external
-- fulfillment supplier, inventory records and authoritative category pricing.
-- Production environments may replace these records through the secured admin APIs.

INSERT INTO categories (name, slug, description, active)
VALUES
 ('Fashion', 'fashion', 'Clothing, shoes and fashion essentials.', TRUE),
 ('Accessories', 'accessories', 'Watches, bags, jewelry and everyday accessories.', TRUE),
 ('Beauty & Care', 'beauty-care', 'Skincare, makeup, haircare and fragrance.', TRUE)
ON CONFLICT (slug) DO NOTHING;

INSERT INTO brands (name, description)
VALUES ('ALI STORE', 'ALI STORE retail catalog brand')
ON CONFLICT (name) DO NOTHING;

INSERT INTO suppliers (
    external_code, name, api_base_url, status,
    fulfillment_enabled, inventory_sync_enabled
)
VALUES (
    'ALI-DEFAULT-SUPPLIER',
    'ALI STORE External Fulfillment Supplier',
    NULL,
    'ACTIVE',
    TRUE,
    TRUE
)
ON CONFLICT (external_code) DO NOTHING;

INSERT INTO products (
    name, slug, description, price, currency, sku, status,
    category_id, brand_id, supplier_id, supplier_sku, supplier_cost
)
SELECT v.name, v.slug, v.description, v.price, 'SAR', v.sku, 'ACTIVE',
       c.id, b.id, s.id, v.supplier_sku, v.supplier_cost
FROM (
    VALUES
      ('فستان سهرة أحمر طويل', 'red-evening-dress', 'فستان سهرة أنيق للاستخدامات الخاصة.', 349.00, 'ALI-FASHION-001', 'SUP-DRESS-001', 268.46, 'fashion'),
      ('قميص رجالي قطني أبيض', 'white-cotton-shirt', 'قميص قطني يومي خفيف ومريح.', 79.00, 'ALI-FASHION-002', 'SUP-SHIRT-002', 60.77, 'fashion'),
      ('حذاء رياضي أحمر', 'red-sneaker', 'حذاء رياضي يومي للاستخدام النشط.', 259.00, 'ALI-FASHION-003', 'SUP-SHOE-003', 199.23, 'fashion'),
      ('قبعة كاجوال كلاسيكية', 'classic-casual-cap', 'قبعة عملية للاستخدام اليومي.', 65.00, 'ALI-FASHION-004', 'SUP-CAP-004', 50.00, 'fashion'),
      ('حقيبة يد جلدية حمراء', 'red-leather-handbag', 'حقيبة يد أنيقة للاستخدام اليومي.', 389.00, 'ALI-ACCESS-001', 'SUP-BAG-001', 311.20, 'accessories'),
      ('ساعة يد جلدية أنيقة', 'leather-watch', 'ساعة يد بتصميم كلاسيكي.', 459.00, 'ALI-ACCESS-002', 'SUP-WATCH-002', 367.20, 'accessories'),
      ('نظارة شمسية كلاسيكية', 'classic-sunglasses', 'نظارة شمسية بتصميم عملي.', 189.00, 'ALI-ACCESS-003', 'SUP-GLASS-003', 151.20, 'accessories'),
      ('عقد لؤلؤ أنيق', 'pearl-necklace', 'عقد لؤلؤ بتصميم بسيط وأنيق.', 229.00, 'ALI-ACCESS-004', 'SUP-PEARL-004', 183.20, 'accessories'),
      ('حقيبة ظهر عملية', 'practical-backpack', 'حقيبة ظهر مناسبة للعمل والتنقل.', 159.00, 'ALI-ACCESS-005', 'SUP-BACK-005', 127.20, 'accessories'),
      ('عطر فاخر للنساء', 'women-luxury-fragrance', 'عطر نسائي بتصميم فاخر.', 299.00, 'ALI-BEAUTY-001', 'SUP-PERF-001', 221.48, 'beauty-care'),
      ('مجموعة مكياج احترافية', 'professional-makeup-set', 'مجموعة مكياج للاستخدام اليومي والمناسبات.', 179.00, 'ALI-BEAUTY-002', 'SUP-MAKEUP-002', 132.59, 'beauty-care'),
      ('كريم عناية بالبشرة', 'skin-care-cream', 'كريم يومي للعناية بالبشرة.', 99.00, 'ALI-BEAUTY-003', 'SUP-CREAM-003', 73.33, 'beauty-care')
) AS v(name, slug, description, price, sku, supplier_sku, supplier_cost, category_slug)
JOIN categories c ON c.slug = v.category_slug
JOIN brands b ON b.name = 'ALI STORE'
JOIN suppliers s ON s.external_code = 'ALI-DEFAULT-SUPPLIER'
WHERE NOT EXISTS (
    SELECT 1 FROM products p WHERE p.slug = v.slug
);

INSERT INTO inventories (
    product_id, quantity, reserved_quantity, low_stock_threshold,
    source, last_synced_at
)
SELECT p.id, 100, 0, 5, 'MANUAL', CURRENT_TIMESTAMP
FROM products p
WHERE p.supplier_id = (SELECT id FROM suppliers WHERE external_code = 'ALI-DEFAULT-SUPPLIER')
  AND NOT EXISTS (
      SELECT 1 FROM inventories i WHERE i.product_id = p.id
  );

CREATE TABLE IF NOT EXISTS pricing_rules (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    product_id BIGINT REFERENCES products(id) ON DELETE CASCADE,
    category_id BIGINT REFERENCES categories(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL CHECK (type IN ('FIXED', 'PERCENTAGE')),
    rule_value NUMERIC(12,2) NOT NULL CHECK (rule_value > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    priority INT NOT NULL DEFAULT 0 CHECK (priority >= 0),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_pricing_rule_scope CHECK (NOT (product_id IS NOT NULL AND category_id IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS idx_pricing_rules_product ON pricing_rules(product_id, active, priority);
CREATE INDEX IF NOT EXISTS idx_pricing_rules_category ON pricing_rules(category_id, active, priority);

INSERT INTO pricing_rules (name, category_id, type, rule_value, active, priority)
SELECT 'Fashion supplier markup', id, 'PERCENTAGE', 30, TRUE, 10
FROM categories WHERE slug = 'fashion'
  AND NOT EXISTS (SELECT 1 FROM pricing_rules r WHERE r.category_id = categories.id);

INSERT INTO pricing_rules (name, category_id, type, rule_value, active, priority)
SELECT 'Accessories supplier markup', id, 'PERCENTAGE', 25, TRUE, 10
FROM categories WHERE slug = 'accessories'
  AND NOT EXISTS (SELECT 1 FROM pricing_rules r WHERE r.category_id = categories.id);

INSERT INTO pricing_rules (name, category_id, type, rule_value, active, priority)
SELECT 'Beauty & Care supplier markup', id, 'PERCENTAGE', 35, TRUE, 10
FROM categories WHERE slug = 'beauty-care'
  AND NOT EXISTS (SELECT 1 FROM pricing_rules r WHERE r.category_id = categories.id);

INSERT INTO product_images (product_id, url, alt_text, sort_order, is_primary)
SELECT p.id, v.url, p.name, 0, TRUE
FROM (
    VALUES
      ('ALI-FASHION-001', 'https://images.unsplash.com/photo-1595777457583-95e059d581b8?auto=format&fit=crop&w=1200&q=75'),
      ('ALI-FASHION-002', 'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?auto=format&fit=crop&w=1200&q=75'),
      ('ALI-FASHION-003', 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=1200&q=75'),
      ('ALI-FASHION-004', 'https://images.unsplash.com/photo-1521369909029-2afed882baee?auto=format&fit=crop&w=1200&q=75'),
      ('ALI-ACCESS-001', 'https://images.unsplash.com/photo-1584917865442-de89df76afd3?auto=format&fit=crop&w=1200&q=75'),
      ('ALI-ACCESS-002', 'https://images.unsplash.com/photo-1524805444758-089113d48a6d?auto=format&fit=crop&w=1200&q=75'),
      ('ALI-ACCESS-003', 'https://images.unsplash.com/photo-1572635196237-14b3f281503f?auto=format&fit=crop&w=1200&q=75'),
      ('ALI-ACCESS-004', 'https://images.unsplash.com/photo-1515562141207-7a88fb7ce338?auto=format&fit=crop&w=1200&q=75'),
      ('ALI-ACCESS-005', 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&w=1200&q=75'),
      ('ALI-BEAUTY-001', 'https://images.unsplash.com/photo-1541643600914-78b084683601?auto=format&fit=crop&w=1200&q=75'),
      ('ALI-BEAUTY-002', 'https://images.unsplash.com/photo-1596462502278-27bfdc403348?auto=format&fit=crop&w=1200&q=75'),
      ('ALI-BEAUTY-003', 'https://images.unsplash.com/photo-1556228720-195a672e8a03?auto=format&fit=crop&w=1200&q=75')
) AS v(sku, url)
JOIN products p ON p.sku = v.sku
WHERE NOT EXISTS (
    SELECT 1 FROM product_images pi WHERE pi.product_id = p.id
);