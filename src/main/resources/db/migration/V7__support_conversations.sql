CREATE TABLE support_conversations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subject VARCHAR(200) NOT NULL,
    category VARCHAR(20) NOT NULL CHECK (category IN ('ORDER','SHIPPING','PRODUCT','PAYMENT','OTHER')),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN','PENDING','RESOLVED','CLOSED')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_support_conversations_user_id
    ON support_conversations(user_id);

CREATE INDEX idx_support_conversations_status
    ON support_conversations(status);

CREATE TABLE support_messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES support_conversations(id) ON DELETE CASCADE,
    sender_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sender_name VARCHAR(200) NOT NULL,
    sender_role VARCHAR(30) NOT NULL CHECK (sender_role IN ('CUSTOMER','ADMIN')),
    body VARCHAR(4000) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_support_messages_conversation_id
    ON support_messages(conversation_id);
