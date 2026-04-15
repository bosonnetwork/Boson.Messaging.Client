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
import java.util.concurrent.CompletableFuture;

import io.vertx.core.Vertx;

import io.bosonnetwork.Id;
import io.bosonnetwork.Node;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.photonmessaging.impl.PhotonMessagingClient;

/**
 * The primary interface for the Boson Messaging Client.
 * <p>
 * This client provides comprehensive APIs for managing conversations, messages,
 * contacts, and channels within the Boson network.
 */
public interface MessagingClient {
	/**
	 * Default limit for the number of messages to retrieve in a single request.
	 */
	static int DEFAULT_MESSAGES_LIMIT = 100;

	////////////////////////////////////////////////////////////////////////////
	// Client identities
	////////////////////////////////////////////////////////////////////////////
	/**
	 * Retrieves the identifier of the local user.
	 *
	 * @return the user's {@link Id}.
	 */
	Id getUserId();

	/**
	 * Retrieves the identifier of the current device.
	 *
	 * @return the device's {@link Id}.
	 */
	Id getDeviceId();

	////////////////////////////////////////////////////////////////////////////
	// Listeners
	////////////////////////////////////////////////////////////////////////////
	/**
	 * Adds a listener for connection state changes.
	 *
	 * @param listener the {@link ConnectionListener} to add.
	 */
	void addConnectionListener(ConnectionListener listener);

	/**
	 * Removes a previously added connection state listener.
	 *
	 * @param listener the {@link ConnectionListener} to remove.
	 */
	void removeConnectionListener(ConnectionListener listener);

	/**
	 * Adds a listener for message-related events.
	 *
	 * @param listener the {@link MessageListener} to add.
	 */
	void addMessageListener(MessageListener listener);

	/**
	 * Removes a previously added message listener.
	 *
	 * @param listener the {@link MessageListener} to remove.
	 */
	void removeMessageListener(MessageListener listener);

	/**
	 * Adds a listener for channel-related events.
	 *
	 * @param listener the {@link ChannelListener} to add.
	 */
	void addChannelListener(ChannelListener listener);

	/**
	 * Removes a previously added channel listener.
	 *
	 * @param listener the {@link ChannelListener} to remove.
	 */
	void removeChannelListener(ChannelListener listener);

	/**
	 * Adds a listener for contact-related events.
	 *
	 * @param listener the {@link ContactListener} to add.
	 */
	void addContactListener(ContactListener listener);

	/**
	 * Removes a previously added contact listener.
	 *
	 * @param listener the {@link ContactListener} to remove.
	 */
	void removeContactListener(ContactListener listener);

	/**
	 * Adds a listener for handshake (friend request) events.
	 *
	 * @param listener the {@link HandshakeListener} to add.
	 */
	void addHandshakeListener(HandshakeListener listener);

	/**
	 * Removes a previously added handshake listener.
	 *
	 * @param listener the {@link HandshakeListener} to remove.
	 */
	void removeHandshakeListener(HandshakeListener listener);

	////////////////////////////////////////////////////////////////////////////
	// Start and stop, status check
	////////////////////////////////////////////////////////////////////////////
	/**
	 * Starts the messaging client, initiating the connection and synchronization process.
	 *
	 * @return a {@link CompletableFuture} that completes when the client has started.
	 */
	CompletableFuture<Void> start();

	/**
	 * Stops the messaging client and releases all associated resources.
	 * Once stopped, the client instance cannot be reused.
	 *
	 * @return a {@link CompletableFuture} that completes when the client has stopped.
	 */
	CompletableFuture<Void> stop();

	/**
	 * Checks if the messaging client is currently running.
	 *
	 * @return {@code true} if running; {@code false} otherwise.
	 */
	boolean isRunning();

	/**
	 * Checks if the messaging client is currently connected to the messaging service.
	 *
	 * @return {@code true} if connected; {@code false} otherwise.
	 */
	boolean isConnected();

	////////////////////////////////////////////////////////////////////////////
	// Message and conversation APIs
	////////////////////////////////////////////////////////////////////////////
	/**
	 * Creates a new {@link Message.Builder} for composing and sending a message.
	 *
	 * @return a new message builder.
	 */
	default Message.Builder message() {
		return message(null);
	}

	/**
	 * Creates a new {@link Message.Builder} for composing and sending a message to a specific recipient.
	 *
	 * @param recipient the identifier of the recipient.
	 * @return a new message builder.
	 */
	Message.Builder message(Id recipient);

	/**
	 * Retrieves a specific conversation by its identifier.
	 *
	 * @param conversationId the identifier of the conversation.
	 * @return a {@link CompletableFuture} that will be completed with the {@link Conversation}.
	 */
	CompletableFuture<Conversation> getConversation(Id conversationId);

	/**
	 * Retrieves all conversations for the local user.
	 *
	 * @return a {@link CompletableFuture} that will be completed with the list of {@link Conversation}s.
	 */
	CompletableFuture<List<Conversation>> getConversations();

	/**
	 * Removes a conversation and all its associated messages.
	 *
	 * @param conversationId the identifier of the conversation to remove.
	 * @return a {@link CompletableFuture} that will be completed with {@code true} if successful; {@code false} otherwise.
	 */
	default CompletableFuture<Boolean> removeConversation(Id conversationId) {
		return removeConversations(List.of(conversationId));
	}

	/**
	 * Removes multiple conversations and all their associated messages.
	 *
	 * @param conversationIds the identifiers of the conversations to remove.
	 * @return a {@link CompletableFuture} that will be completed with {@code true} if successful; {@code false} otherwise.
	 */
	CompletableFuture<Boolean> removeConversations(Collection<Id> conversationIds);

	/**
	 * Retrieves a list of messages for a specific conversation using default limits.
	 *
	 * @param conversationId the identifier of the conversation.
	 * @return a {@link CompletableFuture} that will be completed with the list of {@link Message}s.
	 */
	default CompletableFuture<List<Message>> getMessages(Id conversationId) {
		return getMessages(conversationId, System.currentTimeMillis(), DEFAULT_MESSAGES_LIMIT, 0);
	}

	/**
	 * Retrieves a list of messages for a specific conversation with pagination support.
	 *
	 * @param conversationId the identifier of the conversation.
	 * @param since the timestamp since which messages should be retrieved.
	 * @param limit the maximum number of messages to retrieve.
	 * @param offset the number of messages to skip.
	 * @return a {@link CompletableFuture} that will be completed with the list of {@link Message}s.
	 */
	CompletableFuture<List<Message>> getMessages(Id conversationId, long since, int limit, int offset);

	/**
	 * Retrieves a list of messages for a specific conversation within a time range.
	 *
	 * @param conversationId the identifier of the conversation.
	 * @param begin the start timestamp (inclusive).
	 * @param end the end timestamp (inclusive).
	 * @return a {@link CompletableFuture} that will be completed with the list of {@link Message}s.
	 */
	CompletableFuture<List<Message>> getMessages(Id conversationId, long begin, long end);

	/**
	 * Removes a specific message by its internal identifier.
	 *
	 * @param messageId the internal numeric identifier of the message.
	 * @return a {@link CompletableFuture} that will be completed with {@code true} if successful; {@code false} otherwise.
	 */
	default CompletableFuture<Boolean> removeMessage(long messageId) {
		return removeMessages(List.of(messageId));
	}

	/**
	 * Removes multiple specific messages by their internal identifiers.
	 *
	 * @param messageIds the internal numeric identifiers of the messages to remove.
	 * @return a {@link CompletableFuture} that will be completed with {@code true} if successful; {@code false} otherwise.
	 */
	CompletableFuture<Boolean> removeMessages(Collection<Long> messageIds);

	/**
	 * Removes all messages associated with a specific conversation.
	 *
	 * @param conversionId the identifier of the conversation.
	 * @return a {@link CompletableFuture} that will be completed with {@code true} if successful; {@code false} otherwise.
	 */
	CompletableFuture<Boolean> removeMessages(Id conversionId);

	////////////////////////////////////////////////////////////////////////////
	// Session APIs
	////////////////////////////////////////////////////////////////////////////
	/**
	 * Retrieves all active sessions for the current user across all devices.
	 *
	 * @return a {@link CompletableFuture} that will be completed with the list of {@link SessionInfo}.
	 */
	CompletableFuture<List<SessionInfo>> getSessions();

	/**
	 * Revokes a specific device session.
	 *
	 * @param deviceId the identifier of the device session to revoke.
	 * @return a {@link CompletableFuture} that completes when the session has been revoked.
	 */
	CompletableFuture<Void> revokeSession(Id deviceId);

	////////////////////////////////////////////////////////////////////////////
	// Friend APIs
	////////////////////////////////////////////////////////////////////////////
	/**
	 * Sends a friend request to a specific user.
	 *
	 * @param id the identifier of the user to send the request to.
	 * @param hello an optional greeting message.
	 * @return a {@link CompletableFuture} that completes when the request is sent.
	 */
	CompletableFuture<Void> friendRequest(Id id, String hello);

	/**
	 * Accepts an incoming friend request from a specific user.
	 *
	 * @param id the identifier of the user who sent the request.
	 * @return a {@link CompletableFuture} that completes when the request is accepted.
	 */
	CompletableFuture<Void> acceptFriendRequest(Id id);

	/**
	 * Retrieves the details of a specific friend request.
	 *
	 * @param id the identifier of the user associated with the request.
	 * @return a {@link CompletableFuture} that will be completed with the {@link FriendRequest}.
	 */
	CompletableFuture<FriendRequest> getFriendRequest(Id id);

	/**
	 * Retrieves all friend requests.
	 *
	 * @return a {@link CompletableFuture} that will be completed with the list of {@link FriendRequest}s.
	 */
	CompletableFuture<List<FriendRequest>> getFriendRequests();

	/**
	 * Removes a friend request.
	 *
	 * @param id the identifier of the user associated with the request to remove.
	 * @return a {@link CompletableFuture} that will be completed with {@code true} if successful; {@code false} otherwise.
	 */
	CompletableFuture<Boolean> removeFriendRequest(Id id);

	/**
	 * Removes multiple friend requests.
	 *
	 * @param ids the identifiers of the users associated with the requests to remove.
	 * @return a {@link CompletableFuture} that will be completed with {@code true} if successful; {@code false} otherwise.
	 */
	CompletableFuture<Boolean> removeFriendRequests(Collection<Id> ids);

	/**
	 * Clears all friend requests.
	 *
	 * @return a {@link CompletableFuture} that completes when all requests are cleared.
	 */
	CompletableFuture<Void> clearFriendRequests();

	/**
	 * Adds a user as a friend manually using their session key.
	 *
	 * @param id the identifier of the user.
	 * @param sessionKey the shared session key for the contact.
	 * @return a {@link CompletableFuture} that will be completed with the created {@link Contact}.
	 */
	default CompletableFuture<Contact> addFriend(Id id, byte[] sessionKey) {
		return addFriend(id, sessionKey, null);
	}

	/**
	 * Adds a user as a friend manually using their session key and an optional remark.
	 *
	 * @param id the identifier of the user.
	 * @param sessionKey the shared session key for the contact.
	 * @param remark an optional remark or alias for the friend.
	 * @return a {@link CompletableFuture} that will be completed with the created {@link Contact}.
	 */
	CompletableFuture<Contact> addFriend(Id id, byte[] sessionKey, String remark);

	////////////////////////////////////////////////////////////////////////////
	// channel APIs
	////////////////////////////////////////////////////////////////////////////
	/**
	 * Creates a new channel with default settings.
	 *
	 * @param name the name of the channel.
	 * @return a {@link CompletableFuture} that will be completed with the created {@link Channel}.
	 */
	default CompletableFuture<Channel> createChannel(String name) {
		return createChannel(Channel.Permission.OWNER_INVITE, name, null, false);
	}

	/**
	 * Creates a new channel with a specific name and notice.
	 *
	 * @param name the name of the channel.
	 * @param notice the channel notice or description.
	 * @return a {@link CompletableFuture} that will be completed with the created {@link Channel}.
	 */
	default CompletableFuture<Channel> createChannel(String name, String notice) {
		return createChannel(Channel.Permission.OWNER_INVITE, name, notice, false);
	}

	/**
	 * Creates a new channel with advanced configuration.
	 *
	 * @param permission the join permission level for the channel.
	 * @param name the name of the channel.
	 * @param notice the channel notice or description.
	 * @param announce whether to announce the channel to the network.
	 * @return a {@link CompletableFuture} that will be completed with the created {@link Channel}.
	 */
	CompletableFuture<Channel> createChannel(Channel.Permission permission, String name, String notice, boolean announce);

	/**
	 * Removes a channel. Only the owner can remove a channel.
	 *
	 * @param channelId the identifier of the channel to remove.
	 * @return a {@link CompletableFuture} that will be completed with {@code true} if successful; {@code false} otherwise.
	 */
	CompletableFuture<Boolean> removeChannel(Id channelId);

	/**
	 * Joins a channel using an invitation ticket.
	 *
	 * @param ticket the {@link InviteTicket} used to join the channel.
	 * @return a {@link CompletableFuture} that will be completed with the joined {@link Channel}.
	 */
	CompletableFuture<Channel> joinChannel(InviteTicket ticket);

	/**
	 * Leaves a channel.
	 *
	 * @param channelId the identifier of the channel to leave.
	 * @return a {@link CompletableFuture} that will be completed with {@code true} if successful; {@code false} otherwise.
	 */
	CompletableFuture<Boolean> leaveChannel(Id channelId);

	/**
	 * Creates an invitation ticket for a channel that can be used by anyone.
	 *
	 * @param channelId the identifier of the channel.
	 * @return a {@link CompletableFuture} that will be completed with the {@link InviteTicket}.
	 */
	default CompletableFuture<InviteTicket> createInviteTicket(Id channelId) {
		return createInviteTicket(channelId, null);
	}

	/**
	 * Creates an invitation ticket for a channel targeting a specific user.
	 *
	 * @param channelId the identifier of the channel.
	 * @param invitee the identifier of the target user.
	 * @return a {@link CompletableFuture} that will be completed with the {@link InviteTicket}.
	 */
	CompletableFuture<InviteTicket> createInviteTicket(Id channelId, Id invitee);

	/**
	 * Transfers the ownership of a channel to another user.
	 *
	 * @param channelId the identifier of the channel.
	 * @param newOwner the identifier of the new owner.
	 * @return a {@link CompletableFuture} that completes when the ownership has been transferred.
	 */
	CompletableFuture<Void> transferChannelOwnership(Id channelId, Id newOwner);

	/**
	 * Rotates the session key for a channel using a randomly generated key pair.
	 *
	 * @param channelId the identifier of the channel.
	 * @return a {@link CompletableFuture} that completes when the key has been rotated.
	 */
	default CompletableFuture<Void> rotateChannelSessionKey(Id channelId) {
		return rotateChannelSessionKey(channelId, Signature.KeyPair.random());
	}

	/**
	 * Rotates the session key for a channel using the specified key pair.
	 *
	 * @param channelId the identifier of the channel.
	 * @param sessionKeypair the new key pair to use for the channel session.
	 * @return a {@link CompletableFuture} that completes when the key has been rotated.
	 */
	CompletableFuture<Void> rotateChannelSessionKey(Id channelId, Signature.KeyPair sessionKeypair);

	/**
	 * Updates the channel metadata (e.g., name, notice).
	 *
	 * @param channel the {@link Channel} object containing updated information.
	 * @return a {@link CompletableFuture} that completes when the update is finished.
	 */
	CompletableFuture<Void> updateChannelInfo(Channel channel);

	/**
	 * Sets the role (e.g., MODERATOR, MEMBER) for specific members of a channel.
	 *
	 * @param channelId the identifier of the channel.
	 * @param members the list of member identifiers.
	 * @param role the new role to be assigned.
	 * @return a {@link CompletableFuture} that completes when the roles have been updated.
	 */
	CompletableFuture<Void> setChannelMembersRole(Id channelId, List<Id> members, Channel.Role role);

	/**
	 * Bans specific members from a channel.
	 *
	 * @param channelId the identifier of the channel.
	 * @param members the list of identifiers of members to ban.
	 * @return a {@link CompletableFuture} that completes when the members have been banned.
	 */
	CompletableFuture<Void> banChannelMembers(Id channelId, List<Id> members);

	/**
	 * Unbans specific members from a channel.
	 *
	 * @param channelId the identifier of the channel.
	 * @param members the list of identifiers of members to unban.
	 * @return a {@link CompletableFuture} that completes when the members have been unbanned.
	 */
	CompletableFuture<Void> unbanChannelMembers(Id channelId, List<Id> members);

	/**
	 * Removes (kicks) specific members from a channel.
	 *
	 * @param channelId the identifier of the channel.
	 * @param members the list of identifiers of members to remove.
	 * @return a {@link CompletableFuture} that completes when the members have been removed.
	 */
	CompletableFuture<Void> removeChannelMembers(Id channelId, List<Id> members);

	////////////////////////////////////////////////////////////////////////////
	// Generic contact APIs
	////////////////////////////////////////////////////////////////////////////
	/**
	 * Retrieves a generic contact (friend or channel) by its identifier.
	 *
	 * @param contactId the identifier of the contact.
	 * @return a {@link CompletableFuture} that will be completed with the {@link Contact}.
	 */
	CompletableFuture<Contact> getContact(Id contactId);

	/**
	 * Retrieves all generic contacts.
	 *
	 * @return a {@link CompletableFuture} that will be completed with the list of {@link Contact}s.
	 */
	CompletableFuture<List<Contact>> getContacts();

	/**
	 * Updates the settings of a generic contact (e.g., remark, muted status).
	 *
	 * @param contact the {@link Contact} object containing updated information.
	 * @return a {@link CompletableFuture} that completes when the update is finished.
	 */
	CompletableFuture<Void> updateContact(Contact contact);

	/**
	 * Removes a specific contact.
	 *
	 * @param contactId the identifier of the contact to remove.
	 * @return a {@link CompletableFuture} that will be completed with {@code true} if successful; {@code false} otherwise.
	 */
	default CompletableFuture<Boolean> removeContact(Id contactId) {
		return removeContacts(List.of(contactId));
	}

	/**
	 * Removes multiple contacts.
	 *
	 * @param contactIds the identifiers of the contacts to remove.
	 * @return a {@link CompletableFuture} that will be completed with {@code true} if successful; {@code false} otherwise.
	 */
	CompletableFuture<Boolean> removeContacts(List<Id> contactIds);

	/**
	 * Clears all generic contacts.
	 *
	 * @return a {@link CompletableFuture} that completes when all contacts are cleared.
	 */
	CompletableFuture<Void> clearContacts();

	/**
	 * Creates a new {@link MessagingClient} instance.
	 *
	 * @param vertx the {@link Vertx} instance to use for asynchronous operations.
	 * @param node the {@link Node} instance representing the local DHT node.
	 * @param config the {@link Configuration} settings for the client.
	 * @return a new {@link MessagingClient} instance.
	 */
	static MessagingClient create(Vertx vertx, Node node, Configuration config) {
		return new PhotonMessagingClient(vertx, node, config);
	}

	/**
	 * Creates a new {@link MessagingClient} instance without an external Vertx.
	 * An internal Vertx instance may be created by the implementation if needed.
	 *
	 * @param node the {@link Node} instance representing the local DHT node.
	 * @param config the {@link Configuration} settings for the client.
	 * @return a new {@link MessagingClient} instance.
	 */
	static MessagingClient create(Node node, Configuration config) {
		return new PhotonMessagingClient(null, node, config);
	}
}