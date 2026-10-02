ALTER TABLE products
    ADD COLUMN IF NOT EXISTS discount_percentage NUMERIC(5, 2) DEFAULT 0
        CHECK (discount_percentage >= 0 AND discount_percentage <= 100);

UPDATE products SET discount_percentage = 20 WHERE sku = 'ALI-FASHION-001';
UPDATE products SET discount_percentage = 12 WHERE sku = 'ALI-ACCESS-001';
UPDATE products SET discount_percentage = 10 WHERE sku = 'ALI-ACCESS-002';
UPDATE products SET discount_percentage = 15 WHERE sku = 'ALI-FASHION-003';
UPDATE products SET discount_percentage = 18 WHERE sku = 'ALI-BEAUTY-001';
UPDATE products SET discount_percentage = 8 WHERE sku = 'ALI-BEAUTY-003';