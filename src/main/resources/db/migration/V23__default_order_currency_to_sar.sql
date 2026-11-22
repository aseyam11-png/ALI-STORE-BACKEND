-- Keep the default for newly created orders aligned with ALI STORE's SAR-only storefront.
-- Existing order currency values are historical financial records and are intentionally untouched.
ALTER TABLE orders
    ALTER COLUMN currency SET DEFAULT 'SAR';
