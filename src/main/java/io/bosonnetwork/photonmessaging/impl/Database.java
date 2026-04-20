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

package io.bosonnetwork.photonmessaging.impl;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.SqlResult;
import io.vertx.sqlclient.templates.SqlTemplate;
import org.slf4j.Logger;

import io.bosonnetwork.Id;
import io.bosonnetwork.database.CollectionParameter;
import io.bosonnetwork.database.VersionedSchema;
import io.bosonnetwork.database.VertxDatabase;
import io.bosonnetwork.photonmessaging.Channel;
import io.bosonnetwork.photonmessaging.Contact;
import io.bosonnetwork.photonmessaging.Conversation;
import io.bosonnetwork.photonmessaging.FriendRequest;
import io.bosonnetwork.photonmessaging.Message;
import io.bosonnetwork.photonmessaging.exceptions.RepositoryException;
import io.bosonnetwork.photonmessaging.impl.database.PostgresDatabase;
import io.bosonnetwork.photonmessaging.impl.database.SqlDialect;
import io.bosonnetwork.photonmessaging.impl.database.SqliteDatabase;

public abstract class Database implements VertxDatabase, MessagingRepository {
	private int schemaVersion;

	protected abstract Logger getLogger();

	protected abstract void init(Vertx vertx);

	protected String getSchema() {
		return null;
	}

	protected abstract Path getMigrationPath();

	public abstract SqlDialect getDialect();

	public static boolean supports(String uri) {
		return uri.startsWith(SqliteDatabase.CONNECTION_URI_PREFIX) ||
				uri.startsWith(PostgresDatabase.CONNECTION_URI_PREFIX);
	}

	public static Database create(String uri, int poolSize, String schema) {
		Objects.requireNonNull(uri, "uri");

		if (uri.startsWith(SqliteDatabase.CONNECTION_URI_PREFIX))
			return new SqliteDatabase(uri, poolSize);
		if (uri.startsWith(PostgresDatabase.CONNECTION_URI_PREFIX))
			return new PostgresDatabase(uri, poolSize, schema);

		throw new IllegalArgumentException("Unsupported database: " + uri);
	}

	public static Database create(String uri, int poolSize) {
		return create(uri, poolSize, null);
	}

	public static Database create(String uri) {
		return create(uri, 0, null);
	}

	public Future<Integer> initialize(Vertx vertx) {
		init(vertx);

		VersionedSchema schema = VersionedSchema.init(vertx, getClient(), getSchema(), getMigrationPath());
		return schema.migrate().andThen(ar -> {
					if (ar.succeeded()) {
						schemaVersion = schema.getCurrentVersion().version();
						getLogger().info("Database is ready, current schema version: {}", schemaVersion);
					} else {
						getLogger().error("Schema migration failed, current schema version: {}",
								schema.getCurrentVersion().version(), ar.cause());
					}
				})
				.map(v -> schema.getCurrentVersion().version())
				.recover(e -> Future.failedFuture(new RepositoryException("Database operation failed", e)));
	}

	public int getSchemaVersion() {
		return schemaVersion;
	}

	////////////////////////////////////////////////////////////////////////////
	// MessagingRepository - Contacts
	////////////////////////////////////////////////////////////////////////////

	@Override
	public Future<Integer> getContactsRevision() {
		return withConnection(c ->
				forQuery(c, getDialect().selectContactsRevision())
						.execute(Map.of())
						.map(this::findInteger)
		).recover(e -> {
			getLogger().error("Failed to get ContactsRevision", e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<Void> putContact(int revision, Contact contact) {
		return withTransaction(c -> {
			Future<?> future = forUpdate(c, getDialect().upsertContact())
						.execute(paramsFromContact(contact));

			if (contact instanceof Channel ch) {
				future = future.compose(v -> forUpdate(c, getDialect().upsertChannel())
							.execute(paramsFromChannel(ch)));
			}

			return future.compose(na ->
					forUpdate(c, getDialect().upsertContactsRevision())
							.execute(Map.of("revision", revision, "updatedAt", System.currentTimeMillis()))
			).<Void>mapEmpty();
		}).recover(e -> {
			getLogger().error("Failed to put contact for revision {}", revision, e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<Void> putContacts(int revision, Collection<Contact> updated) {
		return withTransaction(c -> {
			List<Future<?>> futures = new ArrayList<>();
			for (Contact contact : updated) {
				Future<?> future = forUpdate(c, getDialect().upsertContact())
						.execute(paramsFromContact(contact));

				if (contact instanceof Channel ch) {
					future = future.compose(v -> forUpdate(c, getDialect().upsertChannel())
							.execute(paramsFromChannel(ch)));
				}

				futures.add(future);
			}

			return Future.all(futures).compose(na ->
					forUpdate(c, getDialect().upsertContactsRevision())
							.execute(Map.of("revision", revision, "updatedAt", System.currentTimeMillis()))
			).<Void>mapEmpty();
		}).recover(e -> {
			getLogger().error("Failed to put contacts for revision {}", revision, e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<Void> putContactLocally(Contact contact) {
		return withTransaction(c -> {
			Future<?> future = forUpdate(c, getDialect().upsertContact())
					.execute(paramsFromContact(contact));

			if (contact instanceof Channel ci)
				future = future.compose(v -> forUpdate(c, getDialect().upsertChannel())
						.execute(paramsFromChannel(ci)));

			return future.<Void>mapEmpty();
		}).recover(e -> {
			getLogger().error("Failed to put contact locally {}", contact.getId(), e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<Boolean> removeContactLocally(Id contactId) {
		return withTransaction(c ->
				forUpdate(c, getDialect().deleteContact())
						.execute(Map.of("id", contactId.bytes()))
						.compose(v -> forUpdate(c, getDialect().deleteMessagesByConversation())
								.execute(Map.of("conversationId", contactId.bytes())))
						.map(this::hasAffectedRows)
		).recover(e -> {
			getLogger().error("Failed to remove contact locally {}", contactId, e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<Boolean> removeContacts(int revision, Id contactId) {
		return removeContacts(revision, List.of(contactId));
	}

	@Override
	public Future<Boolean> removeContacts(int revision, Collection<Id> contactIds) {
		return withTransaction(c -> {
			Future<?> future;
			if (contactIds.isEmpty())
				future = Future.succeededFuture();
			else {
				CollectionParameter<byte[]> idsParam = new CollectionParameter<>("id",
						contactIds.stream().map(Id::bytes).toList());
				future = forUpdate(c, getDialect().deleteContacts(idsParam))
						.execute(idsParam.getParams())
						.compose(v -> {
							CollectionParameter<byte[]> cidsParam = new CollectionParameter<>("cid",
									contactIds.stream().map(Id::bytes).toList());
							return forUpdate(c, getDialect().deleteMessagesByConversations(cidsParam))
									.execute(cidsParam.getParams());
						});
			}

			return future.compose(na -> forUpdate(c, getDialect().upsertContactsRevision())
							.execute(Map.of("revision", revision, "updatedAt", System.currentTimeMillis())))
					.map(true);
		}).recover(e -> {
			getLogger().error("Failed to remove contacts for revision {}", revision, e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<Void> clearContacts(int revision) {
		return withTransaction(c ->
				forUpdate(c, getDialect().clearContacts())
						.execute(Map.of())
						.compose(r -> forUpdate(c, getDialect().upsertContactsRevision())
								.execute(Map.of("revision", revision, "updatedAt", System.currentTimeMillis())))
						.<Void>mapEmpty()
		).recover(e -> {
			getLogger().error("Failed to clear contacts for revision {}", revision, e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<Contact> getContact(Id contactId) {
		return withConnection(c ->
				forQuery(c, getDialect().selectContact())
						.execute(Map.of("id", contactId.bytes()))
						.map(rs -> findUnique(rs, this::rowToContact))
		).recover(e -> {
			getLogger().error("Failed to get contact {}", contactId, e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<List<Contact>> getContacts(Collection<Id> contactIds) {
		if (contactIds.isEmpty())
			return Future.succeededFuture(List.of());

		CollectionParameter<byte[]> idsParam = new CollectionParameter<>("id",
				contactIds.stream().map(Id::bytes).toList());
		return withConnection(c ->
				forQuery(c, getDialect().selectContacts(idsParam))
						.execute(idsParam.getParams())
						.map(rs -> findMany(rs, this::rowToContact))
		).recover(e -> {
			getLogger().error("Failed to get contacts {}", contactIds, e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<List<Contact>> getAllContacts() {
		return withConnection(c ->
				forQuery(c, getDialect().selectAllContacts())
						.execute(Map.of())
						.map(rs -> findMany(rs, this::rowToContact))
		).recover(e -> {
			getLogger().error("Failed to get all contacts", e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	////////////////////////////////////////////////////////////////////////////
	// MessagingRepository - Messages
	////////////////////////////////////////////////////////////////////////////

	@Override
	public Future<Void> putMessage(PhotonMessage<MessageContent> message) {
		return withTransaction(c ->
				forQuery(c, getDialect().insertMessage())
						.execute(paramsFromMessage(message))
						.map(rs -> {
							long rid = findLong(rs);
							message.setRid(rid);
							return (Void) null;
						})
		).recover(e -> {
			getLogger().error("Failed to put message {}", message.getId(), e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<Void> updateMessageSentTime(PhotonMessage<MessageContent> message) {
		return withTransaction(c ->
				forUpdate(c, getDialect().updateMessageSentTime())
						.execute(Map.of("id", message.getId().bytes(), "sentAt", message.getSentAt()))
						.<Void>mapEmpty()
		).recover(e -> {
			getLogger().error("Failed to update message sent time for {}", message.getId(), e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<List<PhotonMessage<MessageContent>>> getMessages(Id conversationId, long begin, long end) {
		return withConnection(c ->
				forQuery(c, getDialect().selectMessagesByTimeRange())
						.execute(Map.of("conversationId", conversationId.bytes(),
								"begin", begin,
								"end", end))
						.map(rs -> findMany(rs, this::rowToMessage))
		).recover(e -> {
			getLogger().error("Failed to get messages for conversation {} in range [{}, {})", conversationId, begin, end, e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<List<PhotonMessage<MessageContent>>> getMessages(Id conversationId, long until, int limit, int offset) {
		return withConnection(c ->
				forQuery(c, getDialect().selectMessagesWithPagination())
						.execute(Map.of("conversationId", conversationId.bytes(),
								"until", until,
								"limit", limit,
								"offset", offset))
						.map(rs -> findMany(rs, this::rowToMessage))
		).recover(e -> {
			getLogger().error("Failed to get messages for conversation {} until {}, limit={}, offset={}", conversationId, until, limit, offset, e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<Boolean> removeMessages(Collection<Long> rids) {
		if (rids.isEmpty())
			return Future.succeededFuture(true);

		CollectionParameter<Long> ridsParam = new CollectionParameter<>("rid", rids);
		return withTransaction(c ->
				forUpdate(c, getDialect().deleteMessages(ridsParam))
						.execute(ridsParam.getParams())
						.map(this::hasAffectedRows)
		).recover(e -> {
			getLogger().error("Failed to remove messages by rids {}", rids, e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<Boolean> removeMessages(Id conversationId) {
		return withTransaction(c ->
				forUpdate(c, getDialect().deleteMessagesByConversation())
						.execute(Map.of("conversationId", conversationId.bytes()))
						.map(this::hasAffectedRows)
		).recover(e -> {
			getLogger().error("Failed to remove messages for conversation {}", conversationId, e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	public Future<Boolean> removeMessagesByConversations(Collection<Id> conversationIds) {
		if (conversationIds.isEmpty())
			return Future.succeededFuture(true);

		CollectionParameter<byte[]> cidsParam = new CollectionParameter<>("cid",
				conversationIds.stream().map(Id::bytes).toList());

		return withTransaction(c ->
				forUpdate(c, getDialect().deleteMessagesByConversations(cidsParam))
						.execute(cidsParam.getParams())
						.map(this::hasAffectedRows)
		).recover(e -> {
			getLogger().error("Failed to remove messages for conversations {}", conversationIds, e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<Void> clearMessages() {
		return withTransaction(c ->
				forUpdate(c, getDialect().clearMessages())
						.execute(Map.of())
						.<Void>mapEmpty()
		).recover(e -> {
			getLogger().error("Failed to clear messages", e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	////////////////////////////////////////////////////////////////////////////
	// MessagingRepository - Friend Requests
	////////////////////////////////////////////////////////////////////////////

	@Override
	public Future<Void> putFriendRequest(FriendRequest friendRequest) {
		return withTransaction(c ->
				forUpdate(c, getDialect().upsertFriendRequest())
						.execute(paramsFromFriendRequest((PhotonFriendRequest) friendRequest))
						.<Void>mapEmpty()
		).recover(e -> {
			getLogger().error("Failed to put friend request for {}", friendRequest.getUserId(), e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<FriendRequest> getFriendRequest(Id userId) {
		return withConnection(c ->
				forQuery(c, getDialect().selectFriendRequest())
						.execute(Map.of("id", userId.bytes()))
						.map(rs -> findUnique(rs, this::rowToFriendRequest))
		).recover(e -> {
			getLogger().error("Failed to get friend request for {}", userId, e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<List<FriendRequest>> getFriendRequests() {
		return withConnection(c ->
				forQuery(c, getDialect().selectAllFriendRequests())
						.execute(Map.of())
						.map(rs -> findMany(rs, this::rowToFriendRequest))
		).recover(e -> {
			getLogger().error("Failed to get all friend requests", e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<Boolean> removeFriendRequest(Id userId) {
		return withTransaction(c ->
				forUpdate(c, getDialect().deleteFriendRequest())
						.execute(Map.of("id", userId.bytes()))
						.map(this::hasAffectedRows)
		).recover(e -> {
			getLogger().error("Failed to remove friend request for {}", userId, e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<Boolean> removeFriendRequests(Collection<Id> userIds) {
		if (userIds.isEmpty())
			return Future.succeededFuture(true);

		CollectionParameter<byte[]> idsParam = new CollectionParameter<>("id", userIds.stream().map(Id::bytes).toList());
		return withTransaction(c ->
				forUpdate(c, getDialect().deleteFriendRequests(idsParam))
						.execute(idsParam.getParams())
						.map(this::hasAffectedRows)
		).recover(e -> {
			getLogger().error("Failed to remove friend requests for {}", userIds, e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<Void> clearFriendRequests() {
		return withTransaction(c ->
				forUpdate(c, getDialect().clearFriendRequests())
						.execute(Map.of())
						.<Void>mapEmpty()
		).recover(e -> {
			getLogger().error("Failed to clear friend requests", e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	////////////////////////////////////////////////////////////////////////////
	// MessagingRepository - Conversations
	////////////////////////////////////////////////////////////////////////////

	@Override
	public Future<Conversation> getConversation(Id conversationId) {
		return withConnection(c ->
				forQuery(c, getDialect().selectConversation())
						.execute(Map.of("contactId", conversationId.bytes()))
						.map(rs -> findUnique(rs, this::rowToConversation))
		).recover(e -> {
			getLogger().error("Failed to get conversation {}", conversationId, e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<List<Conversation>> getAllConversations() {
		return withConnection(c ->
				forQuery(c, getDialect().selectAllConversations())
						.execute(Map.of())
						.map(rs -> findMany(rs, this::rowToConversation))
		).recover(e -> {
			getLogger().error("Failed to get all conversations", e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<Boolean> removeConversation(Id conversationId) {
		return removeMessages(conversationId);
	}

	@Override
	public Future<Boolean> removeConversations(Collection<Id> conversationIds) {
		return removeMessagesByConversations(conversationIds);
	}

	////////////////////////////////////////////////////////////////////////////
	// MessagingRepository - Channels
	////////////////////////////////////////////////////////////////////////////

	@Override
	public Future<Void> updateChannelOwnership(Id channelId, Id oldOwnerId, Id newOwnerId) {
		return withTransaction(c ->
				forUpdate(c, getDialect().updateChannelOwnership())
						.execute(Map.of("id", channelId.bytes(),
								"owner", newOwnerId.bytes()))
						.compose(v -> forUpdate(c, getDialect().updateChannelMemberRole())
								.execute(Map.of("channelId", channelId.bytes(),
										"id", oldOwnerId.bytes(),
										"role", Channel.Role.MEMBER.value())))
						.compose(v -> forUpdate(c, getDialect().updateChannelMemberRole())
								.execute(Map.of("channelId", channelId.bytes(),
										"id", newOwnerId.bytes(),
										"role", Channel.Role.OWNER.value())))
						.<Void>mapEmpty()
		).recover(e -> {
			getLogger().error("Failed to update channel ownership for {} from {} to {}", channelId, oldOwnerId, newOwnerId, e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<Void> putChannelMembers(Id channelId, Collection<Channel.Member> members) {
		if (members.isEmpty())
			return Future.succeededFuture();

		List<Map<String, Object>> batchParams = members.stream()
				.map(m -> paramsFromChannelMember(channelId, m))
				.toList();

		return withTransaction(c ->
				forUpdate(c, getDialect().upsertChannelMember())
						.executeBatch(batchParams)
						.<Void>mapEmpty()
		).recover(e -> {
			getLogger().error("Failed to put channel members for {}", channelId, e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<Void> refillChannelMembers(Id channelId, Collection<Channel.Member> members) {
		return withTransaction(c ->
				forUpdate(c, getDialect().clearChannelMembers())
						.execute(Map.of("channelId", channelId.bytes()))
						.compose(v -> {
							if (members.isEmpty())
								return Future.succeededFuture();

							List<Map<String, Object>> batchParams = members.stream()
									.map(m -> paramsFromChannelMember(channelId, m))
									.toList();

							return forUpdate(c, getDialect().upsertChannelMember()).executeBatch(batchParams);
						}).<Void>mapEmpty()
		).recover(e -> {
			getLogger().error("Failed to refill channel members for {}", channelId, e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<Channel.Member> getChannelMember(Id channelId, Id memberId) {
		return withConnection(c ->
				forQuery(c, getDialect().selectChannelMember())
						.execute(Map.of("channelId", channelId.bytes(), "id", memberId.bytes()))
						.map(rs -> findUnique(rs, this::rowToChannelMember))
		).recover(e -> {
			getLogger().error("Failed to get channel member {} for channel {}", memberId, channelId, e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<List<Channel.Member>> getChannelMembers(Id channelId, Collection<Id> memberId) {
		if (memberId == null || memberId.isEmpty())
			return Future.succeededFuture(Collections.emptyList());

		CollectionParameter<byte[]> idsParam = new CollectionParameter<>("id", memberId.stream().map(Id::bytes).toList());
		Map<String, Object> params = idsParam.getParams();
		params.put("channelId", channelId.bytes());

		return withConnection(c ->
				forQuery(c, getDialect().selectChannelMembers(idsParam))
						.execute(params)
						.map(rs -> findMany(rs, this::rowToChannelMember))
		).recover(e -> {
			getLogger().error("Failed to get channel members {} for channel {}", memberId, channelId, e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<List<Channel.Member>> getAllChannelMembers(Id channelId) {
		return withConnection(c ->
				forQuery(c, getDialect().selectAllChannelMembers())
						.execute(Map.of("channelId", channelId.bytes()))
						.map(rs -> findMany(rs, this::rowToChannelMember))
		).recover(e -> {
			getLogger().error("Failed to get all channel members for channel {}", channelId, e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<Boolean> updateChannelMembersRole(Id channelId, Collection<Id> memberIds, Channel.Role role) {
		if (memberIds.isEmpty())
			return Future.succeededFuture(true);

		CollectionParameter<byte[]> idsParam = new CollectionParameter<>("id", memberIds.stream().map(Id::bytes).toList());
		Map<String, Object> params = idsParam.getParams();
		params.put("channelId", channelId.bytes());
		params.put("role", role.value());

		return withTransaction(c ->
				forUpdate(c, getDialect().updateChannelMembersRole(idsParam))
						.execute(params)
						.map(this::hasAffectedRows)
		).recover(e -> {
			getLogger().error("Failed to update channel members role for channel {}", channelId, e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	@Override
	public Future<Boolean> removeChannelMembers(Id channelId, Collection<Id> memberIds) {
		if (memberIds.isEmpty())
			return Future.succeededFuture(true);

		CollectionParameter<byte[]> idsParam = new CollectionParameter<>("id", memberIds.stream().map(Id::bytes).toList());
		Map<String, Object> params = idsParam.getParams();
		params.put("channelId", channelId.bytes());

		return withTransaction(c ->
				forUpdate(c, getDialect().deleteChannelMembers(idsParam))
						.execute(params)
						.map(this::hasAffectedRows)
		).recover(e -> {
			getLogger().error("Failed to remove channel members for channel {}", channelId, e);
			return Future.failedFuture(new RepositoryException(e));
		});
	}

	////////////////////////////////////////////////////////////////////////////
	// Mapping & Parameter Helpers
	////////////////////////////////////////////////////////////////////////////

	private Map<String, Object> paramsFromMessage(PhotonMessage<MessageContent> message) {
		Map<String, Object> params = new HashMap<>();
		params.put("id", message.getId().bytes());
		params.put("conversationId", message.getConversationId().bytes());
		params.put("version", message.getVersion());
		params.put("recipient", message.getRecipient().bytes());
		params.put("type", message.getType().value());
		params.put("fromId", message.getFrom() != null ? message.getFrom().bytes() : null);
		params.put("createdAt", message.getCreatedAt());
		params.put("sentAt", message.getSentAt());
		params.put("receivedAt", message.getReceivedAt());

		MessageContent content = message.getPayload();
		params.put("contentType", content.getContentType());
		params.put("contentDisposition", content.getContentDisposition() != null ? content.getContentDisposition().getValue() : null);
		params.put("payload", content.serialize());
		return params;
	}

	private PhotonMessage<MessageContent> rowToMessage(Row row) {
		long rid = row.getLong("rid");
		Id conversationId = getId(row, "conversation_id");
		int version = row.getInteger("version");
		Id id = getId(row, "id");
		Id recipient = getId(row, "recipient");
		Message.Type type = Message.Type.valueOf(row.getInteger("type"));
		Id fromId = getId(row, "from_id");
		long createdAt = row.getLong("created_at");
		byte[] payloadBytes = getBytes(row, "payload");
		long sentAt = row.getLong("sent_at");
		long receivedAt = row.getLong("received_at");

		MessageContent content = MessageContent.parse(payloadBytes);
		return new PhotonMessage<>(rid, conversationId, version, id, recipient, type, fromId,
				createdAt, content, sentAt, receivedAt);
	}

	private Map<String, Object> paramsFromFriendRequest(PhotonFriendRequest request) {
		Map<String, Object> params = new HashMap<>();
		params.put("id", request.getUserId().bytes());
		params.put("initiator", request.getInitiatorId().bytes());
		params.put("hello", request.getHello());
		params.put("createdAt", request.getCreatedAt());
		params.put("updatedAt", request.getUpdatedAt());
		params.put("accepted", request.isAccepted());
		params.put("acceptedAt", request.getAcceptedAt());
		return params;
	}

	private FriendRequest rowToFriendRequest(Row row) {
		Id userId = getId(row, "id");
		Id initiatorId = getId(row, "initiator");
		String hello = row.getString("hello");
		long createdAt = row.getLong("created_at");
		long updatedAt = row.getLong("updated_at");
		boolean accepted = getBoolean(row, "accepted");
		long acceptedAt = row.getLong("accepted_at");

		return new PhotonFriendRequest(userId, initiatorId, hello, createdAt, updatedAt, accepted, acceptedAt);
	}

	private Map<String, Object> paramsFromContact(Contact contact) {
		Map<String, Object> params = new HashMap<>();
		params.put("id", contact.getId().bytes());
		params.put("type", contact.getType().value());
		params.put("sessionKey", contact instanceof PhotonContact ac ? ac.getSessionKey() : null);
		params.put("name", contact.getName());
		params.put("avatar", contact.getAvatar());
		params.put("remark", contact.getRemark());
		params.put("tags", contact.getTags());
		params.put("muted", contact.isMuted());
		params.put("blocked", contact.isBlocked());
		params.put("revision", contact.getRevision());
		params.put("createdAt", contact.getCreatedAt());
		params.put("updatedAt", contact.getUpdatedAt());
		return params;
	}

	private Contact rowToContact(Row row) {
		Id id = getId(row, "id");
		Contact.Type type = Contact.Type.of(row.getInteger("type"));
		byte[] sessionKey = getBytes(row, "session_key");
		String name = row.getString("name");
		String avatar = row.getString("avatar");
		String remark = row.getString("remark");
		String tags = row.getString("tags");
		boolean muted = getBoolean(row, "muted");
		boolean blocked = getBoolean(row, "blocked");
		long createdAt = row.getLong("created_at");
		long updatedAt = row.getLong("updated_at");
		int revision = row.getInteger("revision");

		return switch (type) {
			case FRIEND ->
					new Friend(id, sessionKey, name, avatar, remark, tags, muted, blocked, createdAt, updatedAt, revision);
			case CHANNEL -> {
				Id owner = getId(row, "owner");
				int permission = row.getInteger("permission");
				String notice = row.getString("notice");
				boolean announce = getBoolean(row, "announce");
				yield new PhotonChannel(id, sessionKey, owner, Channel.Permission.valueOf(permission),
						name, notice, announce, remark, tags, muted, blocked, createdAt, updatedAt, revision);
			}
			case AUTO -> new AutoContact(id, name, avatar, remark, tags, muted, blocked, createdAt, updatedAt);
		};
	}

	private Map<String, Object> paramsFromChannel(Channel ci) {
		Map<String, Object> params = new HashMap<>();
		params.put("id", ci.getId().bytes());
		params.put("owner", ci.getOwnerId().bytes());
		params.put("permission", ci.getPermission().value());
		params.put("notice", ci.getNotice());
		params.put("announce", ci.isAnnounce());
		return params;
	}

	private Map<String, Object> paramsFromChannelMember(Id channelId, Channel.Member member) {
		Map<String, Object> params = new HashMap<>();
		params.put("id", member.getId().bytes());
		params.put("channelId", channelId.bytes());
		params.put("role", member.getRole().value());
		params.put("joined", member.getJoined());
		return params;
	}

	private Channel.Member rowToChannelMember(Row row) {
		Id id = getId(row, "id");
		int role = row.getInteger("role");
		long joined = row.getLong("joined");
		return new ChannelMember(id, Channel.Role.valueOf(role), joined);
	}

	private Conversation rowToConversation(Row row) {
		PhotonContact contact = (PhotonContact) rowToContact(row);

		Long msgRid = row.getLong("last_message_rid");
		if (msgRid == null)
			return new PhotonConversation(contact, null);

		int msgVersion = row.getInteger("last_message_version");
		Id msgId = getId(row, "last_message_id");
		Id msgConversationId = getId(row, "last_message_conversation_id");
		Id msgRecipient = getId(row, "last_message_recipient");
		Message.Type msgType = Message.Type.valueOf(row.getInteger("last_message_type"));
		Id msgFromId = getId(row, "last_message_from_id");
		long msgCreatedAt = row.getLong("last_message_created_at");
		byte[] msgPayloadBytes = getBytes(row, "last_message_payload");
		long msgSentAt = row.getLong("last_message_sent_at");
		long msgReceivedAt = row.getLong("last_message_received_at");

		MessageContent content = MessageContent.parse(msgPayloadBytes);
		PhotonMessage<MessageContent> lastMessage = new PhotonMessage<>(msgRid, msgConversationId, msgVersion, msgId,
				msgRecipient, msgType, msgFromId, msgCreatedAt, content, msgSentAt, msgReceivedAt);

		return new PhotonConversation(contact, lastMessage);
	}

	////////////////////////////////////////////////////////////////////////////
	// VertxDatabase Helpers
	////////////////////////////////////////////////////////////////////////////

	protected static SqlTemplate<Map<String, Object>, SqlResult<Void>> forUpdate(SqlConnection connection, String template) {
		return SqlTemplate.forUpdate(connection, template);
	}

	protected static SqlTemplate<Map<String, Object>, RowSet<Row>> forQuery(SqlConnection connection, String template) {
		return SqlTemplate.forQuery(connection, template);
	}

	protected static Id getId(Row row, String column) {
		byte[] bytes = getBytes(row, column);
		return bytes == null ? null : Id.of(bytes);
	}

	protected static byte[] getBytes(Row row, String column) {
		Buffer buf = row.getBuffer(column);
		return buf == null ? null : buf.getBytes();
	}

	protected static boolean getBoolean(Row row, String column) {
		Object value = row.getValue(column);
		return value instanceof Boolean b ? b :
				(value instanceof Number n ? n.intValue() != 0 :
				 (value instanceof String s && Boolean.parseBoolean(s)));
	}
}