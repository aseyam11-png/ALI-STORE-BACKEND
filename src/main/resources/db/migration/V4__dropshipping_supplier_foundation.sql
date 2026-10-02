-- V4: align the persisted schema with ALI STORE's single-merchant dropshipping model.
-- B2B/RFQ/quotation and seller marketplace tables are removed if they exist.
-- Supplier identity belongs to ALI STORE's internal catalog/fulfillment domain.
-- Existing product/order/shipment rows remain migratable: new supplier mappings are nullable
-- at the database level so legacy rows can be reviewed before being published/fulfilled.

DROP TABLE IF EXISTS quotations CASCADE;
DROP TABLE IF EXISTS rfq_items CASCADE;
DROP TABLE IF EXISTS rfqs CASCADE;
DROP TABLE IF EXISTS sellers CASCADE;

CREATE TABLE IF NOT EXISTS suppliers (
    id BIGSERIAL PRIMARY KEY,
    external_code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    api_base_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    fulfillment_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    inventory_sync_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS supplier_id BIGINT REFERENCES suppliers(id),
    ADD COLUMN IF NOT EXISTS supplier_sku VARCHAR(100),
    ADD COLUMN IF NOT EXISTS supplier_cost NUMERIC(10, 2);

CREATE INDEX IF NOT EXISTS idx_products_supplier_id ON products(supplier_id);
CREATE INDEX IF NOT EXISTS idx_products_supplier_sku ON products(supplier_id, supplier_sku);
ALTER TABLE products
    ADD CONSTRAINT chk_products_supplier_cost_nonnegative
    CHECK (supplier_cost IS NULL OR supplier_cost > 0);

ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS supplier_id BIGINT REFERENCES suppliers(id),
    ADD COLUMN IF NOT EXISTS supplier_sku VARCHAR(100),
    ADD COLUMN IF NOT EXISTS supplier_unit_cost NUMERIC(10, 2);

CREATE INDEX IF NOT EXISTS idx_order_items_supplier_id ON order_items(supplier_id);
ALTER TABLE order_items
    ADD CONSTRAINT chk_order_items_supplier_unit_cost_positive
    CHECK (supplier_unit_cost IS NULL OR supplier_unit_cost > 0);

ALTER TABLE shipments
    DROP CONSTRAINT IF EXISTS shipments_order_id_key;

ALTER TABLE shipments
    ADD COLUMN IF NOT EXISTS supplier_id BIGINT REFERENCES suppliers(id),
    ADD COLUMN IF NOT EXISTS supplier_order_id VARCHAR(255) UNIQUE,
    ADD COLUMN IF NOT EXISTS shipping_address_snapshot VARCHAR(1000);

ALTER TABLE shipments
    ALTER COLUMN shipping_address_id DROP NOT NULL;

ALTER TABLE shipments
    DROP CONSTRAINT IF EXISTS shipments_status_check;

ALTER TABLE shipments
    ADD CONSTRAINT shipments_status_check
    CHECK (status IN ('PENDING', 'PROCESSING', 'SHIPPED', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED'));

CREATE INDEX IF NOT EXISTS idx_shipments_order_id ON shipments(order_id);
CREATE INDEX IF NOT EXISTS idx_shipments_supplier_id ON shipments(supplier_id);
CREATE INDEX IF NOT EXISTS idx_shipments_status ON shipments(status);

CREATE TABLE IF NOT EXISTS fulfillment_tasks (
    id BIGSERIAL PRIMARY KEY,
    shipment_id BIGINT NOT NULL UNIQUE REFERENCES shipments(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED')),
    attempts INT NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fulfillment_tasks_due
    ON fulfillment_tasks(status, next_attempt_at);


ALTER TABLE inventories
    ADD COLUMN IF NOT EXISTS source VARCHAR(30),
    ADD COLUMN IF NOT EXISTS last_synced_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE inventories
    DROP CONSTRAINT IF EXISTS chk_inventory_source;

ALTER TABLE inventories
    ADD CONSTRAINT chk_inventory_source
    CHECK (source IS NULL OR source IN ('SUPPLIER_SYNC', 'MANUAL'));

CREATE INDEX IF NOT EXISTS idx_inventories_last_synced_at ON inventories(last_synced_at);
