INSERT INTO coupons (
    code, discount_percentage, expiration_date, is_active,
    usage_limit, times_used, minimum_order_amount
)
VALUES
    ('WELCOME10', 10, '2027-12-31T23:59:59', TRUE, 10000, 0, NULL),
    ('SAVE15', 15, '2027-12-31T23:59:59', TRUE, 5000, 0, 150),
    ('VIP20', 20, '2027-12-31T23:59:59', TRUE, 1000, 0, 300)
ON CONFLICT (code) DO NOTHING;