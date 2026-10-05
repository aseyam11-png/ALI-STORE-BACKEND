-- V7: enforce one cart line per product at the database boundary.
-- CartService serializes cart mutations with the cart row lock; this unique
-- constraint remains a second invariant guard against duplicate cart-item rows.
ALTER TABLE cart_items
    ADD CONSTRAINT uq_cart_items_cart_product UNIQUE (cart_id, product_id);
