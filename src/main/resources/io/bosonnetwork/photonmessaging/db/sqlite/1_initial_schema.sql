-- Initial SQLite schema for Photon Messaging Client

CREATE TABLE IF NOT EXISTS contacts_revision (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    revision INTEGER NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS contacts (
    id BLOB NOT NULL PRIMARY KEY,
    type SMALLINT NOT NULL,
    session_key BLOB,
    name VARCHAR(256),
    remark VARCHAR(256),
    tags VARCHAR(256),
    muted BOOLEAN NOT NULL DEFAULT FALSE,
    blocked BOOLEAN NOT NULL DEFAULT FALSE,
    revision INTEGER NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_contacts_name ON contacts (name);
CREATE INDEX IF NOT EXISTS idx_contacts_remark ON contacts (remark);

CREATE TABLE IF NOT EXISTS channels (
    id BLOB NOT NULL PRIMARY KEY,
    owner BLOB NOT NULL,
    permission SMALLINT NOT NULL CHECK(permission IN(0, 1, 2, 3)),
    notice VARCHAR(1024),
    announce BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (id) REFERENCES contacts (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS channel_members (
   id BLOB NOT NULL,
   channel_id BLOB NOT NULL,
   role SMALLINT NOT NULL CHECK(role IN(-1, 0, 1, 2)),
   joined BIGINT NOT NULL DEFAULT 0,
   PRIMARY KEY (id, channel_id),
   FOREIGN KEY (channel_id) REFERENCES channels (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_channel_members_channel_id_joined ON channel_members (channel_id, joined ASC);

CREATE TABLE IF NOT EXISTS friend_requests (
    id BLOB NOT NULL PRIMARY KEY,
    initiator BLOB NOT NULL,
    hello VARCHAR(512),
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    accepted BOOLEAN NOT NULL DEFAULT FALSE,
    accepted_at BIGINT DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_friend_requests_created_at ON friend_requests (created_at);

-- Use rid (repository_id) as the formal basis for sorting messages by time,
-- because other time-related columns lack a unified time reference and cannot guarantee correct order.
CREATE TABLE IF NOT EXISTS messages (
    rid INTEGER PRIMARY KEY AUTOINCREMENT,
    id BLOB NOT NULL UNIQUE,
    conversation_id BLOB NOT NULL,
    version SMALLINT NOT NULL,
    recipient BLOB NOT NULL,
    type SMALLINT NOT NULL,
    from_id BLOB,
    created_at BIGINT NOT NULL,
    content_type VARCHAR(128) DEFAULT NULL,
    content_disposition VARCHAR(512) DEFAULT NULL,
    payload BLOB DEFAULT NULL,
    sent_at BIGINT DEFAULT 0,
    received_at BIGINT DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_messages_conversation_id ON messages (conversation_id);