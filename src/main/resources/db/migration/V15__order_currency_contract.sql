ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3);

UPDATE orders o
SET currency = COALESCE(
    (
        SELECT UPPER(BTRIM(p.currency))
        FROM order_items oi
        JOIN products p ON p.id = oi.product_id
        WHERE oi.order_id = o.id
        ORDER BY oi.id
        LIMIT 1
    ),
    'USD'
)
WHERE currency IS NULL OR BTRIM(currency) = '';

ALTER TABLE orders
    ALTER COLUMN currency SET DEFAULT 'USD';

ALTER TABLE orders
    ALTER COLUMN currency SET NOT NULL;
