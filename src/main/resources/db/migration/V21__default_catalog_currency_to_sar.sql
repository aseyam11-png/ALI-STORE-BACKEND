-- ALI STORE's canonical storefront currency is SAR.
-- Existing product amounts and currencies are intentionally untouched here;
-- legacy non-SAR products require an explicit, reviewed reconciliation.
ALTER TABLE products
    ALTER COLUMN currency SET DEFAULT 'SAR';
