CREATE TABLE qva_invitations (
    id TEXT PRIMARY KEY,
    token_hash TEXT NOT NULL UNIQUE CHECK (length(token_hash) = 64),
    created_by_user_id INTEGER NOT NULL REFERENCES users(id),
    created_ms INTEGER NOT NULL,
    expires_ms INTEGER NOT NULL CHECK (expires_ms > created_ms),
    revoked_ms INTEGER,
    redeemed_ms INTEGER,
    redeemed_user_id INTEGER REFERENCES users(id),
    CHECK ((redeemed_ms IS NULL AND redeemed_user_id IS NULL)
        OR (redeemed_ms IS NOT NULL AND redeemed_user_id IS NOT NULL)),
    CHECK (revoked_ms IS NULL OR redeemed_ms IS NULL)
);

CREATE INDEX idx_qva_invitations_pending
    ON qva_invitations(expires_ms, created_ms)
    WHERE revoked_ms IS NULL AND redeemed_ms IS NULL;

CREATE TABLE qva_invitation_audit (
    id TEXT PRIMARY KEY,
    invitation_id TEXT NOT NULL REFERENCES qva_invitations(id),
    action TEXT NOT NULL CHECK (action IN ('CREATED', 'REVOKED', 'REDEEMED')),
    actor_user_id INTEGER REFERENCES users(id),
    affected_user_id INTEGER REFERENCES users(id),
    occurred_ms INTEGER NOT NULL
);

CREATE INDEX idx_qva_invitation_audit_time
    ON qva_invitation_audit(occurred_ms DESC);
