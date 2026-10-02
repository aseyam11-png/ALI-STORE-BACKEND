-- Canonical Catalog / Inventory foundation (Phase 1 stabilization).
-- Covers exactly the tables backing the canonical User/Category/Product/Inventory model.
-- All other JPA entities in the codebase (cart, orders, payments, wishlists, shipments,
-- notifications, coupons, reviews, RFQs, quotations, sellers, audit log) are intentionally
-- NOT migrated here - those modules are deferred per Phase 24 and hibernate.ddl-auto is
-- "none", so they will not be usable against a real database until a follow-up migration
-- creates their tables explicitly.

-- Create Categories Table
CREATE TABLE categories (
                             id BIGSERIAL PRIMARY KEY,
                             name VARCHAR(150) NOT NULL UNIQUE,
                             slug VARCHAR(170) NOT NULL UNIQUE,
                             description VARCHAR(1000),
                             image_url VARCHAR(500),
                             active BOOLEAN NOT NULL DEFAULT TRUE,
                             parent_id BIGINT REFERENCES categories(id),
                             created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                             updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_categories_parent_id ON categories(parent_id);

-- Create Brands Table
CREATE TABLE brands (
                         id BIGSERIAL PRIMARY KEY,
                         name VARCHAR(150) NOT NULL UNIQUE,
                         description VARCHAR(1000),
                         logo_url VARCHAR(500),
                         created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create Products Table
CREATE TABLE products (
                           id BIGSERIAL PRIMARY KEY,
                           name VARCHAR(255) NOT NULL,
                           slug VARCHAR(280) NOT NULL UNIQUE,
                           description TEXT,
                           price NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
                           currency VARCHAR(3) NOT NULL DEFAULT 'USD',
                           sku VARCHAR(100) UNIQUE,
                           status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
                           category_id BIGINT NOT NULL REFERENCES categories(id),
                           brand_id BIGINT REFERENCES brands(id),
                           created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                           updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_products_category_id ON products(category_id);
CREATE INDEX idx_products_brand_id ON products(brand_id);
CREATE INDEX idx_products_status ON products(status);

-- Create Product Images Table
CREATE TABLE product_images (
                                 id BIGSERIAL PRIMARY KEY,
                                 product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
                                 url VARCHAR(500) NOT NULL,
                                 alt_text VARCHAR(255),
                                 sort_order INT NOT NULL DEFAULT 0,
                                 is_primary BOOLEAN NOT NULL DEFAULT FALSE,
                                 created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                 updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_product_images_product_id ON product_images(product_id);

-- Create Inventories Table (single source of truth for stock; one row per product)
CREATE TABLE inventories (
                              id BIGSERIAL PRIMARY KEY,
                              product_id BIGINT NOT NULL UNIQUE REFERENCES products(id) ON DELETE CASCADE,
                              quantity INT NOT NULL DEFAULT 0 CHECK (quantity >= 0),
                              reserved_quantity INT NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
                              low_stock_threshold INT DEFAULT 5,
                              version BIGINT NOT NULL DEFAULT 0,
                              CONSTRAINT chk_reserved_not_exceed_quantity CHECK (reserved_quantity <= quantity),
                              created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                              updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
