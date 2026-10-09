ALTER TABLE product_variants
    ADD COLUMN IF NOT EXISTS supplier_sku VARCHAR(120),
    ADD COLUMN IF NOT EXISTS supplier_cost NUMERIC(10,2);

CREATE INDEX IF NOT EXISTS idx_product_variants_supplier_sku
    ON product_variants(supplier_sku);
