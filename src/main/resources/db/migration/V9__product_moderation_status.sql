ALTER TABLE products
    ADD COLUMN IF NOT EXISTS moderation_status VARCHAR(30) NOT NULL DEFAULT 'PUBLISHED';

ALTER TABLE products
    DROP CONSTRAINT IF EXISTS chk_products_moderation_status;

ALTER TABLE products
    ADD CONSTRAINT chk_products_moderation_status
    CHECK (
        moderation_status IN (
            'DRAFT',
            'PENDING_REVIEW',
            'CHANGES_REQUESTED',
            'REJECTED',
            'PUBLISHED'
        )
    );

CREATE INDEX IF NOT EXISTS idx_products_moderation_status
    ON products(moderation_status);
