CREATE TABLE qva_device_identities (
    device_id TEXT NOT NULL REFERENCES qva_devices(id),
    version INTEGER NOT NULL CHECK (version > 0),
    signing_key BLOB NOT NULL UNIQUE CHECK (length(signing_key) = 44),
    fingerprint TEXT NOT NULL UNIQUE CHECK (length(fingerprint) = 64),
    registered_ms INTEGER NOT NULL,
    PRIMARY KEY (device_id, version)
);

CREATE VIEW qva_current_device_identities AS
    SELECT i.* FROM qva_device_identities i
    WHERE i.version = (SELECT MAX(previous.version) FROM qva_device_identities previous
                       WHERE previous.device_id = i.device_id);

CREATE TABLE qva_identity_verifications (
    verifier_device_id TEXT NOT NULL,
    verifier_version INTEGER NOT NULL,
    peer_device_id TEXT NOT NULL,
    peer_version INTEGER NOT NULL,
    verified_ms INTEGER NOT NULL,
    CHECK (verifier_device_id <> peer_device_id),
    PRIMARY KEY (verifier_device_id, verifier_version, peer_device_id, peer_version),
    FOREIGN KEY (verifier_device_id, verifier_version) REFERENCES qva_device_identities(device_id, version),
    FOREIGN KEY (peer_device_id, peer_version) REFERENCES qva_device_identities(device_id, version)
);

CREATE TABLE qva_device_key_packages (
    id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL,
    identity_version INTEGER NOT NULL,
    digest TEXT NOT NULL UNIQUE CHECK (length(digest) = 64),
    package_bytes BLOB CHECK (package_bytes IS NULL OR length(package_bytes) BETWEEN 1 AND 16384),
    published_ms INTEGER NOT NULL,
    expires_ms INTEGER NOT NULL CHECK (expires_ms > published_ms),
    claimed_by TEXT REFERENCES qva_devices(id),
    claim_id TEXT,
    claimed_ms INTEGER,
    CHECK ((claimed_by IS NULL AND claim_id IS NULL AND claimed_ms IS NULL)
        OR (claimed_by IS NOT NULL AND claim_id IS NOT NULL AND claimed_ms IS NOT NULL)),
    CHECK (claimed_by IS NULL OR claimed_by <> device_id),
    FOREIGN KEY (device_id, identity_version) REFERENCES qva_device_identities(device_id, version),
    UNIQUE (claimed_by, claim_id)
);

CREATE INDEX idx_qva_key_package_available
    ON qva_device_key_packages(device_id, identity_version, published_ms, id)
    WHERE claimed_ms IS NULL AND package_bytes IS NOT NULL;

CREATE VIEW qva_identity_conversation_members AS
    SELECT id AS conversation_id, lower_user_id AS user_id FROM qva_direct_conversations
    UNION ALL
    SELECT id, upper_user_id FROM qva_direct_conversations
    UNION ALL
    SELECT group_id, user_id FROM qva_group_members;
