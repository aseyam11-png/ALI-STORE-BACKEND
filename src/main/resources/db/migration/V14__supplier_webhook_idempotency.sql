CREATE TABLE IF NOT EXISTS supplier_webhook_events (
    id BIGSERIAL PRIMARY KEY,
    supplier_code VARCHAR(80) NOT NULL,
    event_id VARCHAR(150) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_supplier_webhook_event UNIQUE (supplier_code, event_id)
);

CREATE INDEX IF NOT EXISTS idx_supplier_webhook_events_received_at
    ON supplier_webhook_events(received_at);
