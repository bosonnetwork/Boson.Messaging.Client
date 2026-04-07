/*
 * Copyright (c) 2023 -      bosonnetwork.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.bosonnetwork.photonmessaging.impl.database;

import io.bosonnetwork.database.CollectionParameter;

/**
 * SQL dialect for the Photon Messaging Client database.
 */
public class SqlDialect {
	// Contacts Revision
	public String selectContactsRevision() {
		return "SELECT revision FROM contacts_revision WHERE id = 1";
	}

	public String upsertContactsRevision() {
		return """
				INSERT INTO contacts_revision (id, revision, updated_at)
				VALUES (1, #{revision}, #{updatedAt})
				ON CONFLICT (id) DO UPDATE SET
					revision = EXCLUDED.revision,
					updated_at = EXCLUDED.updated_at
				""";
	}

	// Contacts
	public String upsertContact() {
		return """
				INSERT INTO contacts (id, type, session_key, name, avatar, remark, tags, muted, blocked, revision, created_at, updated_at)
				VALUES (#{id}, #{type}, #{sessionKey}, #{name}, #{avatar}, #{remark}, #{tags}, #{muted}, #{blocked}, #{revision}, #{createdAt}, #{updatedAt})
				ON CONFLICT (id) DO UPDATE SET
					type = EXCLUDED.type,
					session_key = EXCLUDED.session_key,
					name = EXCLUDED.name,
					avatar = EXCLUDED.avatar,
					remark = EXCLUDED.remark,
					tags = EXCLUDED.tags,
					muted = EXCLUDED.muted,
					blocked = EXCLUDED.blocked,
					revision = EXCLUDED.revision,
					updated_at = EXCLUDED.updated_at
				""";
	}

	public String deleteContactsLocally(CollectionParameter<byte[]> ids) {
		return "DELETE FROM contacts WHERE id IN " + ids.getTemplate();
	}

	public String clearContacts() {
		return "DELETE FROM contacts";
	}

	public String selectContact() {
		return "SELECT * FROM contacts WHERE id = #{id}";
	}

	public String selectContacts(CollectionParameter<byte[]> ids) {
		return "SELECT * FROM contacts WHERE id IN " + ids.getTemplate();
	}

	public String selectAllContacts() {
		return "SELECT * FROM contacts";
	}

	public String existsContact() {
		return "SELECT EXISTS(SELECT 1 FROM contacts WHERE id = #{id})";
	}

	// Channels
	public String upsertChannel() {
		return """
				INSERT INTO channels (id, owner, permission, notice, announce)
				VALUES (#{id}, #{owner}, #{permission}, #{notice}, #{announce})
				ON CONFLICT (id) DO UPDATE SET
					owner = EXCLUDED.owner,
					permission = EXCLUDED.permission,
					notice = EXCLUDED.notice,
					announce = EXCLUDED.announce
				""";
	}

	public String selectChannel() {
		return "SELECT * FROM channels WHERE id = #{id}";
	}

	public String updateChannelOwnership() {
		return "UPDATE channels SET owner = #{owner} WHERE id = #{id}";
	}

	// Channel Members
	public String upsertChannelMember() {
		return """
				INSERT INTO channel_members (id, channel_id, role, joined)
				VALUES (#{id}, #{channelId}, #{role}, #{joined})
				ON CONFLICT (id, channel_id) DO UPDATE SET
					role = EXCLUDED.role
				""";
	}

	public String deleteChannelMembers(CollectionParameter<byte[]> ids) {
		return "DELETE FROM channel_members WHERE channel_id = #{channelId} AND id IN " + ids.getTemplate();
	}

	public String clearChannelMembers() {
		return "DELETE FROM channel_members WHERE channel_id = #{channelId}";
	}

	public String selectChannelMember() {
		return "SELECT * FROM channel_members WHERE channel_id = #{channelId} AND id = #{id}";
	}

	public String selectChannelMembers(CollectionParameter<byte[]> ids) {
		return "SELECT * FROM channel_members WHERE channel_id = #{channelId} AND id IN " + ids.getTemplate();
	}

	public String selectAllChannelMembers() {
		return "SELECT * FROM channel_members WHERE channel_id = #{channelId} ORDER BY joined ASC";
	}

	public String updateChannelMemberRole() {
		return "UPDATE channel_members SET role = #{role} WHERE channel_id = #{channelId} AND id = #{id}";
	}

	public String updateChannelMembersRole(CollectionParameter<byte[]> ids) {
		return "UPDATE channel_members SET role = #{role} WHERE channel_id = #{channelId} AND id IN " + ids.getTemplate();
	}

	// Friend Requests
	public String upsertFriendRequest() {
		return """
				INSERT INTO friend_requests (id, initiator, hello, created_at, updated_at, accepted, accepted_at)
				VALUES (#{id}, #{initiator}, #{hello}, #{createdAt}, #{updatedAt}, #{accepted}, #{acceptedAt})
				ON CONFLICT (id) DO UPDATE SET
					initiator = EXCLUDED.initiator,
					hello = EXCLUDED.hello,
					updated_at = EXCLUDED.updated_at,
					accepted = EXCLUDED.accepted,
					accepted_at = EXCLUDED.accepted_at
				""";
	}

	public String selectFriendRequest() {
		return "SELECT * FROM friend_requests WHERE id = #{id}";
	}

	public String selectAllFriendRequests() {
		return "SELECT * FROM friend_requests ORDER BY created_at DESC";
	}

	public String deleteFriendRequests(CollectionParameter<byte[]> ids) {
		return "DELETE FROM friend_requests WHERE id IN " + ids.getTemplate();
	}

	public String clearFriendRequests() {
		return "DELETE FROM friend_requests";
	}

	// Messages
	public String insertMessage() {
		return """
				INSERT INTO messages (id, conversation_id, version, recipient, type, from_id, created_at, sent_at, received_at, content_type, content_disposition, headers, body)
				VALUES (#{id}, #{conversationId}, #{version}, #{recipient}, #{type}, #{fromId}, #{createdAt}, #{sentAt}, #{receivedAt}, #{contentType}, #{contentDisposition}, #{headers}, #{body})
				""";
	}

	public String updateMessageSentTime() {
		return "UPDATE messages SET sent_at = #{sentAt} WHERE id = #{id}";
	}

	public String selectMessagesByTimeRange() {
		return """
				SELECT * FROM messages
				WHERE conversation_id = #{conversationId} AND created_at >= #{begin} AND created_at < #{end}
				ORDER BY created_at ASC, rid ASC
				""";
	}

	public String selectMessagesWithPagination() {
		return """
				SELECT * FROM messages
				WHERE conversation_id = #{conversationId} AND created_at >= #{since}
				ORDER BY created_at ASC, rid ASC
				LIMIT #{limit} OFFSET #{offset}
				""";
	}

	public String deleteMessages(CollectionParameter<Long> rids) {
		return "DELETE FROM messages WHERE rid IN " + rids.getTemplate();
	}

	public String deleteMessagesByConversation() {
		return "DELETE FROM messages WHERE conversation_id = #{conversationId}";
	}

	public boolean isUniqueConstraintViolation(Throwable t) {
		String msg = t.getMessage() != null ? t.getMessage().toLowerCase() : "";
		return msg.contains("unique constraint") || msg.contains("primary key");
	}
}
