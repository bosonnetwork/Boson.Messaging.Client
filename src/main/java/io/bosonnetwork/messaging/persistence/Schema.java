package io.bosonnetwork.messaging.persistence;

import org.jdbi.v3.sqlobject.statement.SqlScript;

public interface Schema {
	@SqlScript("""
			CREATE TABLE IF NOT EXISTS configuration(
				key VARCHAR(128) NOT NULL PRIMARY KEY,
				value BLOB NOT NULL)
			""")
	@SqlScript("""
			CREATE TABLE IF NOT EXISTS messages(
				sid INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
				conversationId BLOB NOT NULL,
				version SMALLINT NOT NULL,
				"from" BLOB NOT NULL,
				"to" BLOB NOT NULL,
				serialNumber BIGINT NOT NULL,
				created BIGINT NOT NULL,
				messageType SMALLINT NOT NULL DEFAULT 0,
				properties BLOB,
				contentType VARCHAR(128),
				contentDisposition VARCHAR(128),
				body BOLB,
				"timestamp" BIGINT NOT NULL DEFAULT -1)
			""")
	@SqlScript("""
			CREATE INDEX IF NOT EXISTS messages_idx_cid
				ON messages("conversationId")
			""")
	@SqlScript("""
			CREATE TABLE IF NOT EXISTS contacts(
				id BLOB NOT NULL PRIMARY KEY,
				type SMALLINT NOT NULL,
				auto BOOLEAN NOT NULL,
				homePeerId BLOB,
				privateKey BLOB,
				name VARCHAR(128),
				avatar BOOLEAN DEFAULT FALSE,
				notice VARCHAR(1024),
				owner BLOB,
				permission SMALLINT,
				remark VARCHAR(128),
				tags VARCHAR(128),
				muted BOOLEAN DEFAULT FALSE,
				blocked BOOLEAN DEFAULT FALSE,
				created BIGINT,
				lastModified BIGINT,
				lastUpdated BIGINT DEFAULT -1)
			""")
	@SqlScript("""
			CREATE TABLE IF NOT EXISTS channel_members(
				channelId BLOB NOT NULL,
				memberId BLOB NOT NULL,
				role SMALLINT NOT NULL,
				joined BIGINT NOT NULL,
				PRIMARY KEY(channelId, memberId))
			""")
	void create();
}
