-- A fresh installation has no HOST grant. Provisioning is an operator decision.
CREATE TABLE qva_host_grants (
    slot INTEGER PRIMARY KEY CHECK (slot = 1),
    user_id INTEGER NOT NULL UNIQUE REFERENCES users(id),
    grant_id TEXT NOT NULL UNIQUE
);

CREATE TABLE qva_host_grant_audit (
    id TEXT PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id),
    action TEXT NOT NULL CHECK (action IN ('GRANT', 'REVOKE')),
    operator TEXT NOT NULL,
    occurred_ms INTEGER NOT NULL
);

CREATE TABLE qva_devices (
    id TEXT PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id),
    name TEXT NOT NULL CHECK (length(name) BETWEEN 1 AND 60),
    credential_digest TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REVOKED')),
    registered_ms INTEGER NOT NULL,
    last_seen_ms INTEGER,
    approved_ms INTEGER,
    revoked_ms INTEGER
);

CREATE INDEX idx_qva_devices_owner ON qva_devices(user_id, registered_ms);
