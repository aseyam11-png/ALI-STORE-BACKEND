-- Commerce domain tables (Phase 1 stabilization, part 2).
-- V2's own header noted that cart, order, payment, wishlist, shipping, notification,
-- coupon, and review entities existed in Java but had no backing tables, so none of
-- those modules could function against a real PostgreSQL database (ddl-auto is "none",
-- so Hibernate never creates them). This migration creates exactly those tables.
--
-- Intentionally NOT covered here: rfqs/quotations/sellers/audit_logs/warehouses.
-- Those modules are either legacy/deferred and out of the B2C single-merchant scope
-- (b2b, seller - see RFQController/SellerService/QuotationController doc comments)
-- or unwired scaffolding with no service/controller using them yet (audit_logs,
-- warehouses). Documented as technical debt rather than migrated speculatively.

-- Permissions + role/permission join table (Role.permissions is a @ManyToMany).
CREATE TABLE permissions (
                              id BIGSERIAL PRIMARY KEY,
                              name VARCHAR(100) NOT NULL UNIQUE,
                              description VARCHAR(255),
                              created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                              updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE role_permissions (
                                   role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
                                   permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
                                   PRIMARY KEY (role_id, permission_id)
);

-- Cart: one row per user (Cart.user is a unique OneToOne).
CREATE TABLE carts (
                        id BIGSERIAL PRIMARY KEY,
                        user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
                        updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE cart_items (
                             id BIGSERIAL PRIMARY KEY,
                             cart_id BIGINT NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
                             product_id BIGINT NOT NULL REFERENCES products(id),
                             quantity INT NOT NULL CHECK (quantity >= 1)
);

CREATE INDEX idx_cart_items_cart_id ON cart_items(cart_id);

-- Orders.
CREATE TABLE orders (
                         id BIGSERIAL PRIMARY KEY,
                         user_id BIGINT NOT NULL REFERENCES users(id),
                         status VARCHAR(20) NOT NULL
                             CHECK (status IN ('PENDING', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
                         total_amount NUMERIC(10, 2) NOT NULL CHECK (total_amount >= 0),
                         coupon_code VARCHAR(50),
                         discount_amount NUMERIC(10, 2) CHECK (discount_amount IS NULL OR discount_amount >= 0),
                         shipping_address VARCHAR(1000) NOT NULL,
                         created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);

CREATE TABLE order_items (
                              id BIGSERIAL PRIMARY KEY,
                              order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
                              product_id BIGINT NOT NULL REFERENCES products(id),
                              quantity INT NOT NULL CHECK (quantity >= 1),
                              unit_price NUMERIC(10, 2) NOT NULL CHECK (unit_price >= 0),
                              subtotal NUMERIC(10, 2) NOT NULL CHECK (subtotal >= 0)
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);

-- Payments: one payment row per order (Payment.orderId is unique).
CREATE TABLE payments (
                           id BIGSERIAL PRIMARY KEY,
                           order_id BIGINT NOT NULL UNIQUE REFERENCES orders(id),
                           amount NUMERIC(10, 2) NOT NULL CHECK (amount >= 0),
                           method VARCHAR(20) NOT NULL CHECK (method IN ('CREDIT_CARD', 'PAYPAL', 'CASH_ON_DELIVERY')),
                           status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED')),
                           transaction_id VARCHAR(255),
                           created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                           updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Wishlist.
CREATE TABLE wishlists (
                            id BIGSERIAL PRIMARY KEY,
                            user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                            product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
                            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                            CONSTRAINT uq_wishlists_user_product UNIQUE (user_id, product_id)
);

CREATE INDEX idx_wishlists_user_id ON wishlists(user_id);

-- Notifications.
CREATE TABLE notifications (
                                id BIGSERIAL PRIMARY KEY,
                                user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                title VARCHAR(255) NOT NULL,
                                message VARCHAR(1000) NOT NULL,
                                is_read BOOLEAN NOT NULL DEFAULT FALSE,
                                created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id);

-- Coupons.
CREATE TABLE coupons (
                          id BIGSERIAL PRIMARY KEY,
                          code VARCHAR(50) NOT NULL UNIQUE,
                          discount_percentage NUMERIC(5, 2) NOT NULL CHECK (discount_percentage > 0 AND discount_percentage <= 100),
                          expiration_date TIMESTAMP WITH TIME ZONE NOT NULL,
                          is_active BOOLEAN NOT NULL DEFAULT TRUE,
                          usage_limit INT CHECK (usage_limit IS NULL OR usage_limit > 0),
                          times_used INT NOT NULL DEFAULT 0 CHECK (times_used >= 0),
                          minimum_order_amount NUMERIC(10, 2) CHECK (minimum_order_amount IS NULL OR minimum_order_amount >= 0),
                          created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                          CONSTRAINT chk_coupons_usage_within_limit CHECK (usage_limit IS NULL OR times_used <= usage_limit)
);

-- Reviews.
CREATE TABLE reviews (
                          id BIGSERIAL PRIMARY KEY,
                          product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
                          user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                          rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
                          comment VARCHAR(1000),
                          created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                          CONSTRAINT uq_reviews_product_user UNIQUE (product_id, user_id)
);

CREATE INDEX idx_reviews_product_id ON reviews(product_id);

-- Shipping addresses.
CREATE TABLE shipping_addresses (
                                     id BIGSERIAL PRIMARY KEY,
                                     user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                     street VARCHAR(255) NOT NULL,
                                     city VARCHAR(100) NOT NULL,
                                     state VARCHAR(100),
                                     country VARCHAR(100) NOT NULL,
                                     postal_code VARCHAR(20),
                                     is_default BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_shipping_addresses_user_id ON shipping_addresses(user_id);

-- Shipments: one shipment per order (Shipment.orderId is unique).
CREATE TABLE shipments (
                            id BIGSERIAL PRIMARY KEY,
                            order_id BIGINT NOT NULL UNIQUE REFERENCES orders(id),
                            tracking_number VARCHAR(100) UNIQUE,
                            carrier VARCHAR(100),
                            shipping_address_id BIGINT NOT NULL REFERENCES shipping_addresses(id),
                            status VARCHAR(20) NOT NULL
                                CHECK (status IN ('PENDING', 'SHIPPED', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED')),
                            estimated_delivery_date TIMESTAMP WITH TIME ZONE,
                            shipped_at TIMESTAMP WITH TIME ZONE,
                            delivered_at TIMESTAMP WITH TIME ZONE,
                            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
