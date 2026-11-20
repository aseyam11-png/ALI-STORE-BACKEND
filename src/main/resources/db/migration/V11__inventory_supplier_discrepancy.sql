ALTER TABLE inventories
    ADD COLUMN IF NOT EXISTS supplier_reported_quantity INTEGER,
    ADD COLUMN IF NOT EXISTS inventory_discrepancy BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE inventories
    DROP CONSTRAINT IF EXISTS inventories_supplier_reported_quantity_non_negative;

ALTER TABLE inventories
    ADD CONSTRAINT inventories_supplier_reported_quantity_non_negative
    CHECK (supplier_reported_quantity IS NULL OR supplier_reported_quantity >= 0);
