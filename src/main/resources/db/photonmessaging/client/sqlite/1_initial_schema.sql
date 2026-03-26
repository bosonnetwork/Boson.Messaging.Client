-- Initial SQLite schema for Photon Messaging Client


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

CREATE INDEX idx_contacts_name ON contacts (name);
CREATE INDEX idx_contacts_remark ON contacts (remark);

CREATE TABLE IF NOT EXISTS channels (
    id BLOB NOT NULL PRIMARY KEY,
    owner BLOB NOT NULL,
    permission SMALLINT NOT NULL CHECK(permission IN(0, 1, 2, 3)),
    notice VARCHAR(1024),
    announce BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (id) REFERENCES contacts (id)
);

CREATE TABLE IF NOT EXISTS channel_members (
   id BLOB NOT NULL,
   channel_id BLOB NOT NULL,
   role SMALLINT NOT NULL CHECK(role IN(-1, 0, 1, 2)),
   joined BIGINT NOT NULL DEFAULT 0,
   PRIMARY KEY (id, channel_id),
   FOREIGN KEY (channel_id) REFERENCES channels (id)
);

CREATE INDEX idx_channel_members_channel_id_joined ON channel_members (channel_id, joined ASC);