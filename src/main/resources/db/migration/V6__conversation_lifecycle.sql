CREATE TABLE qva_groups (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    owner_user_id INTEGER NOT NULL REFERENCES users(id),
    created_ms INTEGER NOT NULL
);
CREATE TABLE qva_group_members (
    group_id TEXT NOT NULL REFERENCES qva_groups(id),
    user_id INTEGER NOT NULL REFERENCES users(id),
    after_sequence INTEGER NOT NULL,
    PRIMARY KEY (group_id,user_id)
);
CREATE INDEX idx_qva_group_member_user ON qva_group_members(user_id,group_id);
CREATE TABLE qva_group_envelopes (
    sequence INTEGER PRIMARY KEY AUTOINCREMENT,
    conversation_id TEXT NOT NULL REFERENCES qva_groups(id),
    sender_user_id INTEGER NOT NULL REFERENCES users(id),
    sender_device_id TEXT NOT NULL REFERENCES qva_devices(id),
    request_id TEXT NOT NULL,
    protocol_version INTEGER NOT NULL,
    envelope BLOB NOT NULL CHECK(length(envelope) BETWEEN 1 AND 16384),
    accepted_ms INTEGER NOT NULL,
    UNIQUE (sender_device_id,request_id)
);
CREATE INDEX idx_qva_group_history ON qva_group_envelopes(conversation_id,sequence);
CREATE INDEX idx_qva_direct_expiry ON qva_message_envelopes(accepted_ms);
CREATE INDEX idx_qva_group_expiry ON qva_group_envelopes(accepted_ms);
CREATE VIEW qva_transport_history AS
    SELECT * FROM qva_message_envelopes
    UNION ALL SELECT * FROM qva_group_envelopes;
CREATE TABLE qva_message_receipts (
    conversation_id TEXT NOT NULL,
    sequence INTEGER NOT NULL,
    recipient_user_id INTEGER NOT NULL REFERENCES users(id),
    delivered_ms INTEGER NOT NULL,
    read_ms INTEGER,
    PRIMARY KEY (conversation_id,sequence,recipient_user_id)
);
CREATE TABLE qva_retired_requests (
    sender_device_id TEXT NOT NULL REFERENCES qva_devices(id),
    request_id TEXT NOT NULL,
    expires_ms INTEGER NOT NULL,
    PRIMARY KEY (sender_device_id,request_id)
);
CREATE INDEX idx_qva_retired_expiry ON qva_retired_requests(expires_ms);
