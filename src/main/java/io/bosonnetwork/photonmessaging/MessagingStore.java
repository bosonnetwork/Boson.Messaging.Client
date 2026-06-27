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

package io.bosonnetwork.photonmessaging;

import java.util.Collection;
import java.util.List;

import io.vertx.core.Future;
import org.jspecify.annotations.Nullable;

import io.bosonnetwork.Id;

/**
 * Public persistence contract for the Photon messaging client, expressed entirely in plain data
 * records that mirror the storage columns. It exists so a platform can supply its own storage backend
 * (for example a native Android androidx.sqlite/Room implementation) without depending on the client's
 * internal types.
 *
 * <p>A {@code MessagingStore} is supplied through {@link Configuration} (see
 * {@code Configuration.Builder.store}). When present, the client uses it instead of the built-in
 * JDBC/Vert.x SQL backend; the client adapts between these records and its internal model.
 *
 * <p>All operations are asynchronous and return a Vert.x {@link Future}. Enum-like fields (contact
 * {@code type}, message {@code type}, channel {@code permission}, member {@code role}) are carried as
 * their stable integer values. The {@code rid} of a message is assigned by the store (an
 * auto-incrementing sequence) and is the canonical ordering key for messages.
 *
 * <p>Several operations are required to be atomic: contact writes that also bump the contacts revision,
 * channel ownership transfer, and {@link #refillChannelMembers}. Implementations should run those in a
 * single transaction.
 */
public interface MessagingStore {
	/**
	 * Opens the store and prepares its schema. Called once during client start, before any other
	 * operation. A native implementation creates/migrates its own schema here.
	 *
	 * @return a future completing when the store is ready
	 */
	Future<Void> open();

	/**
	 * Closes the store and releases its resources.
	 *
	 * @return a future completing when closed
	 */
	Future<Void> close();

	// ------------------------------------------------------------------------
	// Records (mirror the storage columns)
	// ------------------------------------------------------------------------

	/** A persisted message row. {@code rid} is assigned by the store. */
	record StoredMessage(
			long rid,
			Id id,
			Id conversationId,
			int version,
			Id recipient,
			int type,
			@Nullable Id from,
			long createdAt,
			@Nullable String contentType,
			@Nullable String contentDisposition,
			byte @Nullable [] payload,
			long sentAt,
			long receivedAt) {
	}

	/** A persisted channel row (present only when a contact is a channel). */
	record StoredChannel(
			Id id,
			Id owner,
			int permission,
			@Nullable String notice,
			boolean announce) {
	}

	/** A persisted contact row; {@code channel} is non-null when the contact is a channel. */
	record StoredContact(
			Id id,
			int type,
			byte @Nullable [] sessionKey,
			@Nullable String name,
			@Nullable String avatar,
			@Nullable String remark,
			@Nullable String tags,
			boolean muted,
			boolean blocked,
			int revision,
			long createdAt,
			long updatedAt,
			@Nullable StoredChannel channel) {
	}

	/** A persisted channel-member row. */
	record StoredChannelMember(
			Id id,
			Id channelId,
			int role,
			long joined) {
	}

	/** A persisted friend-request row. */
	record StoredFriendRequest(
			Id id,
			Id initiator,
			@Nullable String hello,
			long createdAt,
			long updatedAt,
			boolean accepted,
			long acceptedAt) {
	}

	/**
	 * A derived conversation: a contact that has at least one message, together with the latest message
	 * (the row with the greatest {@code rid}). {@code lastMessage} is never null for results returned by
	 * the store, but is modeled nullable for callers that build conversations incrementally.
	 */
	record StoredConversation(
			StoredContact contact,
			@Nullable StoredMessage lastMessage) {
	}

	// ------------------------------------------------------------------------
	// Messages
	// ------------------------------------------------------------------------

	/**
	 * Inserts a message and returns the assigned {@code rid}.
	 *
	 * @param message the message to store (its {@code rid} field is ignored on input)
	 * @return a future with the assigned rid
	 */
	Future<Long> putMessage(StoredMessage message);

	/** Updates the sent time of a message identified by its message id. */
	Future<Void> updateMessageSentTime(Id messageId, long sentAt);

	/** Messages of a conversation with {@code begin <= createdAt < end}, ordered by rid ascending. */
	Future<List<StoredMessage>> getMessagesInRange(Id conversationId, long begin, long end);

	/**
	 * Page of messages of a conversation with {@code createdAt <= until} (inclusive), newest first,
	 * applying {@code limit} and {@code offset}.
	 */
	Future<List<StoredMessage>> getMessagesBefore(Id conversationId, long until, int limit, int offset);

	/** Removes messages by their rids. */
	Future<Boolean> removeMessages(Collection<Long> rids);

	/** Removes all messages of a conversation. */
	Future<Boolean> removeMessagesByConversation(Id conversationId);

	/** Removes all messages. */
	Future<Void> clearMessages();

	// ------------------------------------------------------------------------
	// Friend requests
	// ------------------------------------------------------------------------

	Future<Void> putFriendRequest(StoredFriendRequest friendRequest);

	Future<@Nullable StoredFriendRequest> getFriendRequest(Id userId);

	Future<List<StoredFriendRequest>> getFriendRequests();

	Future<Boolean> removeFriendRequest(Id userId);

	Future<Boolean> removeFriendRequests(Collection<Id> userIds);

	Future<Void> clearFriendRequests();

	// ------------------------------------------------------------------------
	// Conversations (derived)
	// ------------------------------------------------------------------------

	Future<@Nullable StoredConversation> getConversation(Id conversationId);

	Future<List<StoredConversation>> getAllConversations();

	Future<Boolean> removeConversation(Id conversationId);

	Future<Boolean> removeConversations(Collection<Id> conversationIds);

	// ------------------------------------------------------------------------
	// Contacts (revision-aware writes must be atomic)
	// ------------------------------------------------------------------------

	/** Current local contacts revision. */
	Future<Integer> getContactsRevision();

	/** Upserts a contact (and its channel row when present) without changing the revision. */
	Future<Void> putContactLocally(StoredContact contact);

	/** Removes a contact (and its messages) without changing the revision. */
	Future<Boolean> removeContactLocally(Id contactId);

	/** Atomically upserts a contact and sets the revision. */
	Future<Void> putContact(int revision, StoredContact contact);

	/** Atomically upserts contacts and sets the revision. */
	Future<Void> putContacts(int revision, Collection<StoredContact> contacts);

	/** Atomically removes a contact and sets the revision. */
	Future<Boolean> removeContacts(int revision, Id contactId);

	/** Atomically removes contacts and sets the revision. */
	Future<Boolean> removeContacts(int revision, Collection<Id> contactIds);

	/** Atomically clears all contacts and sets the revision. */
	Future<Void> clearContacts(int revision);

	Future<@Nullable StoredContact> getContact(Id contactId);

	Future<List<StoredContact>> getContacts(Collection<Id> contactIds);

	Future<List<StoredContact>> getAllContacts();

	// ------------------------------------------------------------------------
	// Channel members
	// ------------------------------------------------------------------------

	/**
	 * Atomically transfers channel ownership: sets the channel owner, demotes the old owner to member
	 * role, and promotes the new owner to owner role.
	 */
	Future<Void> updateChannelOwnership(Id channelId, Id oldOwnerId, Id newOwnerId);

	Future<Void> putChannelMembers(Id channelId, Collection<StoredChannelMember> members);

	/** Atomically replaces all members of a channel with the given collection. */
	Future<Void> refillChannelMembers(Id channelId, Collection<StoredChannelMember> members);

	Future<@Nullable StoredChannelMember> getChannelMember(Id channelId, Id memberId);

	Future<List<StoredChannelMember>> getChannelMembers(Id channelId, Collection<Id> memberIds);

	Future<List<StoredChannelMember>> getAllChannelMembers(Id channelId);

	Future<Boolean> updateChannelMembersRole(Id channelId, Collection<Id> memberIds, int role);

	Future<Boolean> removeChannelMembers(Id channelId, Collection<Id> memberIds);
}
