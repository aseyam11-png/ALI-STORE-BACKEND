-- Keep the supplier's stable product identifier separate from the supplier SKU and ALI STORE SKU.
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS supplier_product_id VARCHAR(150);

-- Backfill legacy products without a store SKU. Preserve all existing non-empty SKUs.
DO $$
DECLARE
    product_row RECORD;
    candidate_sku TEXT;
    suffix BIGINT;
BEGIN
    FOR product_row IN
        SELECT id FROM products WHERE sku IS NULL OR BTRIM(sku) = '' ORDER BY id
    LOOP
        candidate_sku := 'ALI-' || CASE
            WHEN LENGTH(product_row.id::TEXT) < 8 THEN LPAD(product_row.id::TEXT, 8, '0')
            ELSE product_row.id::TEXT
        END;
        IF EXISTS (
            SELECT 1 FROM products existing
            WHERE existing.sku = candidate_sku AND existing.id <> product_row.id
        ) THEN
            candidate_sku := 'ALI-P-' || product_row.id::TEXT;
            suffix := 1;
            WHILE EXISTS (
                SELECT 1 FROM products existing
                WHERE existing.sku = candidate_sku AND existing.id <> product_row.id
            ) LOOP
                candidate_sku := 'ALI-P-' || product_row.id::TEXT || '-' || suffix::TEXT;
                suffix := suffix + 1;
            END LOOP;
        END IF;
        UPDATE products SET sku = candidate_sku WHERE id = product_row.id;
    END LOOP;
END $$;

-- A supplier's IDs are unique only within that supplier, not across the whole catalog.
CREATE UNIQUE INDEX IF NOT EXISTS uq_products_supplier_product_id
    ON products (supplier_id, supplier_product_id)
    WHERE supplier_id IS NOT NULL AND supplier_product_id IS NOT NULL;
