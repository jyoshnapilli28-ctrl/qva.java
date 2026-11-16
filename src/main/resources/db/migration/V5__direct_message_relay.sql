CREATE TABLE qva_direct_conversations (
    id TEXT PRIMARY KEY,
    lower_user_id INTEGER NOT NULL REFERENCES users(id),
    upper_user_id INTEGER NOT NULL REFERENCES users(id),
    created_ms INTEGER NOT NULL,
    CHECK (lower_user_id < upper_user_id),
    UNIQUE (lower_user_id, upper_user_id)
);

CREATE INDEX idx_qva_direct_upper ON qva_direct_conversations (upper_user_id, created_ms);

CREATE TABLE qva_message_envelopes (
    sequence INTEGER PRIMARY KEY AUTOINCREMENT,
    conversation_id TEXT NOT NULL REFERENCES qva_direct_conversations(id),
    sender_user_id INTEGER NOT NULL REFERENCES users(id),
    sender_device_id TEXT NOT NULL REFERENCES qva_devices(id),
    request_id TEXT NOT NULL,
    protocol_version INTEGER NOT NULL,
    envelope BLOB NOT NULL CHECK (length(envelope) BETWEEN 1 AND 16384),
    accepted_ms INTEGER NOT NULL,
    UNIQUE (sender_device_id, request_id)
);

CREATE INDEX idx_qva_envelope_history ON qva_message_envelopes (conversation_id, sequence);
