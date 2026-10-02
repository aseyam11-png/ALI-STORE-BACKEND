-- V7: supplier price synchronization metadata.
-- The selling price is derived from supplier cost by the configured backend markup.
CREATE INDEX IF NOT EXISTS idx_products_supplier_cost
    ON products(supplier_id, supplier_cost);
