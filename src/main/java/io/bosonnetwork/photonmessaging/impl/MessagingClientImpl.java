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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubAckMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bosonnetwork.CryptoContext;
import io.bosonnetwork.Id;
import io.bosonnetwork.Node;
import io.bosonnetwork.crypto.CryptoBox;
import io.bosonnetwork.crypto.CryptoException;
import io.bosonnetwork.crypto.CryptoIdentity;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.json.Json;
import io.bosonnetwork.photonmessaging.*;
import io.bosonnetwork.photonmessaging.impl.rpc.GenericRpcResponse;
import io.bosonnetwork.photonmessaging.impl.rpc.RpcRequest;
import io.bosonnetwork.utils.Base58;
import io.bosonnetwork.vertx.BosonVerticle;
import io.bosonnetwork.vertx.VertxCaffeine;
import io.bosonnetwork.vertx.VertxFuture;

public class MessagingClientImpl extends BosonVerticle implements MessagingClient {
	private final Vertx providedVertx;
	private final Node node;
	private final Configuration config;

	private final CryptoIdentity userIdentity;
	private final CryptoIdentity deviceIdentity;
	private final Id homePeerId;
	private final CryptoContext serviceContext;
	private final CryptoContext selfContext;

	private final Topics topics;

	private URI serviceEndpoint;
	private String sslCert;
	private MqttClient mqttClient;
	private int failures;

	private ConnectionListener connectionListener;
	private MessageListener messageListener;
	private ChannelListener channelListener;
	private ContactListener contactListener;

	private MessagingRepository repository;

	private int contactsRevision;
	private AsyncLoadingCache<Id, PhotonContact> contactCache;
	private AsyncLoadingCache<Id, ConversationImpl> conversationCache;
	private final Map<Integer, MessageImpl<?>> inflightMessages;
	private final Map<Long, RpcCall<?, ?>> inflightRpcCalls;

	private volatile boolean connected;
	private volatile boolean running;

	private static final Logger log = LoggerFactory.getLogger(MessagingClientImpl.class);

	public MessagingClientImpl(Vertx vertx, Node node, Configuration config) {
		this.providedVertx = vertx == null ? Vertx.vertx() : vertx;
		this.node = Objects.requireNonNull(node, "node");
		this.config = Objects.requireNonNull(config, "config");

		this.userIdentity = new CryptoIdentity(config.getUserKey());
		this.deviceIdentity = new CryptoIdentity(config.getDeviceKey());
		this.homePeerId = config.getServicePeerId();
		try {
			this.serviceContext = deviceIdentity.createCryptoContext(homePeerId);
			this.selfContext = userIdentity.createCryptoContext(userIdentity.getId());
		} catch (CryptoException e) {
			throw new IllegalStateException("Failed to create service encryption context", e);
		}

		this.topics = new Topics(userIdentity.getId(), deviceIdentity.getId());

		this.failures = 0;

		this.inflightMessages = new HashMap<>();
		this.inflightRpcCalls = new HashMap<>();

		this.connected = false;
		this.running = false;
	}

	@Override
	public Id getUserId() {
		return userIdentity.getId();
	}

	@Override
	public Id getDeviceId() {
		return deviceIdentity.getId();
	}

	@Override
	public void addConnectionListener(ConnectionListener listener) {
		Objects.requireNonNull(listener, "listener");
		if (this.connectionListener == null) {
			this.connectionListener = listener;
		} else {
			if (this.connectionListener instanceof ConnectionListenerArray listeners)
				listeners.add(listener);
			else
				this.connectionListener = new ConnectionListenerArray(this.connectionListener, listener);
		}
	}

	@Override
	public void removeConnectionListener(ConnectionListener listener) {
		ConnectionListener current = this.connectionListener;
		if (current == listener)
			this.connectionListener = null;
		else if (current instanceof ConnectionListenerArray listeners)
			listeners.remove(listener);
	}

	@Override
	public void addMessageListener(MessageListener listener) {
		Objects.requireNonNull(listener, "listener");
		if (this.messageListener == null) {
			this.messageListener = listener;
		} else {
			if (this.messageListener instanceof MessageListenerArray listeners)
				listeners.add(listener);
			else
				this.messageListener = new MessageListenerArray(this.messageListener, listener);
		}
	}

	@Override
	public void removeMessageListener(MessageListener listener) {
		MessageListener current = this.messageListener;
		if (current == listener)
			this.messageListener = null;
		else if (current instanceof MessageListenerArray listeners)
			listeners.remove(listener);
	}

	@Override
	public void addChannelListener(ChannelListener listener) {
		Objects.requireNonNull(listener, "listener");
		if (this.channelListener == null) {
			this.channelListener = listener;
		} else {
			if (this.channelListener instanceof ChannelListenerArray listeners)
				listeners.add(listener);
			else
				this.channelListener = new ChannelListenerArray(this.channelListener, listener);
		}
	}

	@Override
	public void removeChannelListener(ChannelListener listener) {
		ChannelListener current = this.channelListener;
		if (current == listener)
			this.channelListener = null;
		else if (current instanceof ChannelListenerArray listeners)
			listeners.remove(listener);
	}

	@Override
	public void addContactListener(ContactListener listener) {
		Objects.requireNonNull(listener, "listener");
		if (this.contactListener == null) {
			this.contactListener = listener;
		} else {
			if (this.contactListener instanceof ContactListenerArray listeners)
				listeners.add(listener);
			else
				this.contactListener = new ContactListenerArray(this.contactListener, listener);
		}
	}

	@Override
	public void removeContactListener(ContactListener listener) {
		ContactListener current = this.contactListener;
		if (current == listener)
			this.contactListener = null;
		else if (current instanceof ContactListenerArray listeners)
			listeners.remove(listener);
	}

	@Override
	public VertxFuture<Void> start() {
		if (running)
			return VertxFuture.succeededFuture();

		return VertxFuture.of(providedVertx.deployVerticle(this).mapEmpty());
	}

	@Override
	public VertxFuture<Void> stop() {
		if (!running)
			return VertxFuture.succeededFuture();

		return VertxFuture.of(vertx.undeploy(deploymentID()));
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	@Override
	public boolean isConnected() {
		return connected;
	}

	private void runningCheck() {
		if (!running)
			throw new IllegalStateException("Messaging client is not running");
	}

	@Override
	public Message.Builder message(Id recipient) {
		runningCheck();
		return new MessageBuilder(this, recipient);
	}

	@Override
	public VertxFuture<Conversation> getConversation(Id conversationId) {
		Objects.requireNonNull(conversationId, "conversationId");
		runningCheck();
		return VertxFuture.of(conversationCache.get(conversationId).thenApply(c -> c));
	}

	@Override
	public VertxFuture<List<Conversation>> getConversations() {
		runningCheck();

		Future<List<Conversation>> future = repository.getAllConversations().map(convs -> {
			// keep the order, overlay the cached conversations
			Map<Id, Conversation> conversations = new LinkedHashMap<>();
			for (Conversation c : convs) {
				ConversationImpl cached = conversationCache.synchronous().asMap()
						.compute(c.getId(), (k, cc) -> cc != null ? cc : (ConversationImpl) c);
				conversations.put(c.getId(), cached);
			}

			// Return a **mutable** list
			return new ArrayList<>(conversations.values());
		});

		return VertxFuture.of(future);
	}

	@Override
	public VertxFuture<Boolean> removeConversations(Collection<Id> conversationIds) {
		Objects.requireNonNull(conversationIds, "conversationIds");
		if (conversationIds.isEmpty())
			return VertxFuture.succeededFuture(true);
		runningCheck();

		Future<Boolean> future = repository.removeConversations(conversationIds).onSuccess(removed ->
				conversationIds.forEach(id -> conversationCache.synchronous().invalidate(id))
		);
		return VertxFuture.of(future);
	}

	@Override
	public VertxFuture<List<Message>> getMessages(Id conversationId, long since, int limit, int offset) {
		Objects.requireNonNull(conversationId, "conversationId");
		if (limit <= 0 || offset < 0)
			throw new IllegalArgumentException("limit and offset must be positive");
		runningCheck();
		return VertxFuture.of(repository.getMessages(conversationId, since, limit, offset).map(List::copyOf));
	}

	@Override
	public VertxFuture<List<Message>> getMessages(Id conversationId, long begin, long end) {
		Objects.requireNonNull(conversationId, "conversationId");
		if (begin > end)
			throw new IllegalArgumentException("begin must be less than or equal to end");
		runningCheck();
		return VertxFuture.of(repository.getMessages(conversationId, begin, end).map(List::copyOf));
	}

	@Override
	public VertxFuture<Boolean> removeMessages(Collection<Long> messageIds) {
		Objects.requireNonNull(messageIds, "messageIds");
		if (messageIds.isEmpty())
			return VertxFuture.succeededFuture(true);
		runningCheck();
		return VertxFuture.of(repository.removeMessages(messageIds));
	}

	@Override
	public VertxFuture<Boolean> removeMessages(Id conversionId) {
		Objects.requireNonNull(conversionId, "conversionId");
		runningCheck();
		return VertxFuture.of(repository.removeMessages(conversionId));
	}

	@Override
	public VertxFuture<List<SessionInfo>> getSessions() {
		runningCheck();

		RpcCall<Void, List<SessionInfo>> call = RpcCall.sessionList();
		Promise<List<SessionInfo>> promise = Promise.promise();
		runOnContext(v -> {
			sendRpcCall(homePeerId, call).onComplete(promise);
		});
		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Void> revokeSession(Id deviceId) {
		Objects.requireNonNull(deviceId, "deviceId");
		runningCheck();

		RpcCall<Id, Void> call = RpcCall.revokeSession(deviceId);
		Promise<Void> promise = Promise.promise();
		runOnContext(v -> {
			sendRpcCall(homePeerId, call).onComplete(promise);
		});
		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Void> friendRequest(Id userId, String hello) {
		Objects.requireNonNull(userId, "id");
		Objects.requireNonNull(hello, "hello");
		runningCheck();

		// friend request is a notification message to the target user
		long now = System.currentTimeMillis();
		Notification<String> fr = new Notification<>(Notification.generateId(deviceIdentity.getId(), now),
				Notification.Event.FRIEND_REQUEST, getUserId(), now, hello);
		FriendRequestImpl request = new FriendRequestImpl(userId, userIdentity.getId(), hello);

		Promise<Void> promise = Promise.promise();
		runOnContext(v -> {
			repository.putFriendRequest(request).compose(vv -> {
				Id messageId = MessageImpl.generateId(getDeviceId(), now);
				MessageImpl<Notification<String>> message = new MessageImpl<>(messageId,
						userId, Message.Type.STATE_MESSAGE, now, fr);
				return sendMessageInternal(message).compose(packetId -> Future.<Void>succeededFuture(),
						error -> {
							repository.removeFriendRequest(userId);
							return Future.failedFuture(error);
						});
			}).onComplete(promise);
		});
		return VertxFuture.of(promise.future());
	}

	@Override
	public CompletableFuture<Void> acceptFriendRequest(Id userId) {
		Objects.requireNonNull(userId, "userId");
		runningCheck();

		Future<Void> future = repository.getFriendRequest(userId).compose(fr -> {
			if (fr == null)
				return Future.failedFuture(new IllegalArgumentException("No friend request found"));

			if (fr.getInitiatorId().equals(getUserId()))
				return Future.failedFuture(new IllegalStateException("Cannot accept your own friend request"));

			if (fr.isAccepted())
				return Future.failedFuture(new IllegalStateException("Friend request has already been accepted"));

			if (fr.isExpired())
				return Future.failedFuture(new IllegalStateException("Friend request has expired"));

			FriendRequestImpl request = (FriendRequestImpl) fr;
			Signature.KeyPair sessionKeypair = Signature.KeyPair.random();
			byte[] sessionKey = sessionKeypair.privateKey().bytes();
			long now = System.currentTimeMillis();
			Notification<byte[]> accept = new Notification<>(Notification.generateId(deviceIdentity.getId(), now),
					Notification.Event.FRIEND_REQUEST_ACCEPT, getUserId(), now, sessionKey);

			Promise<Void> promise = Promise.promise();
			runOnContext(v -> {
				Id messageId = MessageImpl.generateId(getDeviceId(), now);
				MessageImpl<Notification<byte[]>> message = new MessageImpl<>(messageId,
							userId, Message.Type.STATE_MESSAGE, now, accept);
				sendMessageInternal(message).compose(packetId -> {
					request.accept();
					return repository.putFriendRequest(request).compose(vv ->
							addFriendInternal(userId, sessionKey, null).<Void>mapEmpty()
					);
				}).onComplete(promise);
			});

			return promise.future();
		});

		return VertxFuture.of(future);
	}

	@Override
	public VertxFuture<FriendRequest> getFriendRequest(Id userId) {
		Objects.requireNonNull(userId, "userId");
		runningCheck();
		return VertxFuture.of(repository.getFriendRequest(userId));
	}

	@Override
	public VertxFuture<List<FriendRequest>> getFriendRequests() {
		runningCheck();
		return VertxFuture.of(repository.getFriendRequests());
	}

	@Override
	public VertxFuture<Boolean> removeFriendRequest(Id userId) {
		Objects.requireNonNull(userId, "userId");
		runningCheck();
		return VertxFuture.of(repository.removeFriendRequest(userId));
	}

	@Override
	public VertxFuture<Boolean> removeFriendRequests(Collection<Id> userIds) {
		Objects.requireNonNull(userIds, "ids");
		runningCheck();

		if (userIds.isEmpty())
			return VertxFuture.succeededFuture(true);

		return VertxFuture.of(repository.removeFriendRequests(userIds));
	}

	@Override
	public VertxFuture<Void> clearFriendRequests() {
		runningCheck();
		return VertxFuture.of(repository.clearFriendRequests());
	}

	private Future<Contact> addFriendInternal(Id id, byte[] sessionKey, String remark) {
		Friend friend = new Friend(id, sessionKey, remark);
		ContactMutation<Contact> mutation = ContactMutation.add(contactsRevision, friend);
		RpcCall<ContactMutation<Contact>, Integer> call = RpcCall.contactMutate(mutation);
		return sendRpcCall(homePeerId, call).compose(revision -> {
			Contact contact = friend.edit().setRevision(revision).build();
			return repository.putContacts(revision, List.of(contact)).map(vv -> {
				contactsRevision = revision;
				return contact;
			});
		});
	}

	@Override
	public VertxFuture<Contact> addFriend(Id id, byte[] sessionKey, String remark) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(sessionKey, "sessionKey");
		Objects.requireNonNull(remark, "remark");
		runningCheck();


		Promise<Contact> promise = Promise.promise();
		runOnContext(v -> addFriendInternal(id, sessionKey, remark).onComplete(promise));
		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Channel> createChannel(Channel.Permission permission, String name, String notice, boolean announce) {
		Objects.requireNonNull(permission, "permission");
		Objects.requireNonNull(name, "name");
		runningCheck();

		Signature.KeyPair sessionKeypair = Signature.KeyPair.random();
		Id sessionId = Id.of(sessionKeypair.publicKey().bytes());
		byte[] sessionKey = selfContext.encrypt(sessionKeypair.privateKey().bytes());
		RpcPrototypes.CreateChannelParams params = new RpcPrototypes.CreateChannelParams(sessionId, sessionKey,
				permission, name, notice, announce);

		Promise<Channel> promise = Promise.promise();
		runOnContext(v -> {
			// 1. Request to create a channel
			RpcCall<RpcPrototypes.CreateChannelParams, RpcPrototypes.ChannelInfo> createChannelCall = RpcCall.createChannel(params);
			sendRpcCall(homePeerId, createChannelCall).compose(ci -> {
				// 2. Save the channel locally
				ChannelImpl channel = new ChannelImpl(ci.channelId(), ci.sessionKey(), ci.ownerId(), ci.permission(),
							ci.name(), ci.notice(), ci.announce(), ci.createdAt(), ci.updateAt());
				return repository.putContactLocally(channel).compose(vv -> {
					if (ci.members() != null && !ci.members().isEmpty())
						return repository.putChannelMembers(channel.getId(), ci.members()).map(channel);
					else
						return Future.succeededFuture(channel);
				});
			}).compose(ch -> {
				// 3. Add the channel as a new contact (sync cross all devices)
				ContactMutation<Contact> mutation = ContactMutation.add(contactsRevision, ch);
				RpcCall<ContactMutation<Contact>, Integer> addContactCall = RpcCall.contactMutate(mutation);
				return sendRpcCall(homePeerId, addContactCall).compose(revision -> {
					ChannelImpl channel = (ChannelImpl) ch.edit().setRevision(revision).build();
					return repository.putContacts(revision, List.of(channel)).map(vv -> {
						contactsRevision = revision;
						return channel;
					});
				});
			}).onComplete(promise);
		});
		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Boolean> removeChannel(Id channelId) {
		Objects.requireNonNull(channelId, "channelId");
		runningCheck();

		Promise<Boolean> promise = Promise.promise();
		runOnContext(v -> {
			channel(channelId).compose(channel -> {
				if (channel == null)
					return Future.failedFuture(new ChannelNotExistsException(channelId.toString()));

				if (!channel.getOwnerId().equals(getUserId()))
					return Future.failedFuture(new InsufficientPermissionException("Only the owner can delete the channel"));

				// 1. Request to delete the channel
				RpcCall<Void, Void> deleteChannelCall = RpcCall.deleteChannel();
				return sendRpcCall(channelId, deleteChannelCall).compose(vv -> {
					// 2. Remove the channel from the contact list (sync cross all devices)
					ContactMutation<List<Id>> mutation = ContactMutation.remove(contactsRevision, List.of(channelId));
					RpcCall<ContactMutation<List<Id>>, Integer> removeContactCall = RpcCall.contactMutate(mutation);
					return sendRpcCall(homePeerId, removeContactCall).compose(revision ->
							repository.removeContacts(revision, List.of(channelId)).map(ignored -> {
								contactsRevision = revision;
								return true;
							})
					);
				});
			}).onComplete(promise);
		});
		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Channel> joinChannel(InviteTicket ticket) {
		Objects.requireNonNull(ticket, "ticket");
		if (ticket.isValid())
			throw new IllegalArgumentException("Invalid ticket");
		if (ticket.isExpired())
			throw new IllegalArgumentException("Ticket has expired");
		if (ticket.isNamedTicket() && !ticket.getInvitee().equals(getUserId()))
			throw new IllegalArgumentException("Only the invitee can join the channel");

		runningCheck();

		// 1. check the session key is valid and re-encrypt with self-encryption context
		final InviteTicket revisedTicket;
		try {
			byte[] sk = userIdentity.decrypt(ticket.getInviter(), ticket.getSessionKey());
			Signature.KeyPair sessionKeypair = Signature.KeyPair.fromPrivateKey(sk);
			Id sessionId = Id.of(sessionKeypair.publicKey().bytes());
			if (!ticket.getSessionId().equals(sessionId))
				return VertxFuture.failedFuture("Invalid ticket: session key not valid");

			byte[] sessionKey = selfContext.encrypt(sessionKeypair.privateKey().bytes());
			revisedTicket = ticket.revise(sessionKey);
		} catch (CryptoException e) {
			return VertxFuture.failedFuture("Invalid session key");
		}

		Promise<Channel> promise = Promise.promise();
		runOnContext(v -> {
			// 2. check if joined the channel already
			channel(ticket.getChannelId()).compose(existing -> {
				if (existing != null)
					return Future.succeededFuture(existing);

				// 3. request to join the channel
				RpcCall<InviteTicket, RpcPrototypes.ChannelInfo> joinChannelCall = RpcCall.joinChannel(revisedTicket);
				return sendRpcCall(ticket.getChannelId(), joinChannelCall).compose(ci -> {
					ChannelImpl channel = new ChannelImpl(ci.channelId(), ci.sessionKey(), ci.ownerId(), ci.permission(),
							ci.name(), ci.notice(), ci.announce(), ci.createdAt(), ci.updateAt());
					return repository.putContactLocally(channel).compose(vv -> {
						if (ci.members() != null && !ci.members().isEmpty())
							return repository.putChannelMembers(channel.getId(), ci.members()).map(channel);
						else
							return Future.succeededFuture(channel);
					});
				}).compose(ch -> {
					// 4. add the channel as a new contact (sync cross all devices)
					ContactMutation<Contact> mutation = ContactMutation.add(contactsRevision, ch);
					RpcCall<ContactMutation<Contact>, Integer> addContactCall = RpcCall.contactMutate(mutation);
					return sendRpcCall(homePeerId, addContactCall).compose(revision -> {
						ChannelImpl channel = (ChannelImpl) ch.edit().setRevision(revision).build();
						return repository.putContacts(revision, List.of(channel)).map(vv -> {
							contactsRevision = revision;
							return channel;
						});
					});
				});
			}).onComplete(promise);
		});
		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Boolean> leaveChannel(Id channelId) {
		Objects.requireNonNull(channelId, "channelId");
		runningCheck();

		Promise<Boolean> promise = Promise.promise();
		runOnContext(v -> {
			channel(channelId).compose(channel -> {
				if (channel == null)
					return Future.failedFuture(new ChannelNotExistsException(channelId.toString()));

				if (channel.getOwnerId().equals(getUserId()))
					return Future.failedFuture(new InsufficientPermissionException("owner can not leave the channel"));

				RpcCall<Void, Void> leaveChannelCall = RpcCall.leaveChannel();
				return sendRpcCall(channelId, leaveChannelCall).compose(vv -> {
					ContactMutation<List<Id>> mutation = ContactMutation.remove(contactsRevision, List.of(channelId));
					RpcCall<ContactMutation<List<Id>>, Integer> removeContactCall = RpcCall.contactMutate(mutation);
					return sendRpcCall(homePeerId, removeContactCall).compose(revision ->
							repository.removeContacts(revision, List.of(channelId)).map(ignored -> {
								contactsRevision = revision;
								return true;
							})
					);
				});
			}).onComplete(promise);
		});
		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<InviteTicket> createInviteTicket(Id channelId, Id invitee) {
		Objects.requireNonNull(channelId, "channelId");
		runningCheck();

		Promise<InviteTicket> promise = Promise.promise();
		runOnContext(v -> {
			channel(channelId).compose(channel -> {
				if (channel == null)
					return Future.failedFuture(new ChannelNotExistsException(channelId.toString()));

				return channel.loadMembers().compose(vv -> {
					ChannelMember member = channel.getMember(getUserId());
					Channel.Permission permission = channel.getPermission();
					switch (permission) {
						case PUBLIC, MEMBER_INVITE -> {
							if (member.isBanned())
								return Future.failedFuture(new InsufficientPermissionException("You are banned from the channel"));
						}

						case MODERATOR_INVITE -> {
							if (!member.isModerator() && !member.isOwner())
								return Future.failedFuture(new InsufficientPermissionException("You are not a moderator of the channel"));
						}

						case OWNER_INVITE -> {
							if (!member.isOwner())
								return Future.failedFuture(new InsufficientPermissionException("You are not the owner of the channel"));
						}
					}

					if (invitee != null && channel.hasMember(invitee))
						return Future.failedFuture(new AlreadyMemberException("The invitee is already a member of the channel"));

					try {
						byte[] sk = selfContext.decrypt(channel.getSessionKey());
						if (invitee != null)
							sk = userIdentity.encrypt(invitee, sk);

						InviteTicket ticket = InviteTicket.create(userIdentity, channelId, channel.getSessionId(), invitee,
								System.currentTimeMillis() + InviteTicket.DEFAULT_EXPIRATION, sk);

						return Future.succeededFuture(ticket);
					} catch (CryptoException e) {
						return Future.failedFuture("Invalid session key");
					}
				});
			}).onComplete(promise);
		});
		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Void> transferChannelOwnership(Id channelId, Id newOwner) {
		Objects.requireNonNull(channelId, "channelId");
		Objects.requireNonNull(newOwner, "newOwner");
		runningCheck();

		Promise<Void> promise = Promise.promise();
		runOnContext(v -> {
			channel(channelId).compose(channel -> {
				if (channel == null)
					return Future.failedFuture(new ChannelNotExistsException(channelId.toString()));

				if (!channel.getOwnerId().equals(getUserId()))
					return Future.failedFuture(new InsufficientPermissionException("not owner"));

				return channel.loadMembers().compose(vv -> {
					ChannelMember member = channel.getMember(newOwner);
					if (member == null)
						return Future.failedFuture(new NotChannelMemberException("newOwner is not the member of channel"));
					if (member.isBanned())
						return Future.failedFuture(new NotChannelMemberException("newOwner is banned from the channel"));

					RpcCall<Id, Void> call = RpcCall.transferChannelOwnership(newOwner);
					return sendRpcCall(channelId, call).compose(vvv -> {
						Contact updatedChannel = channel.editChannel().setOwnerId(newOwner).build();
						return repository.putContactLocally(updatedChannel);
					});
				});
			}).onComplete(promise);
		});
		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Void> rotateChannelSessionKey(Id channelId, Signature.KeyPair sessionKeypair) {
		Objects.requireNonNull(channelId, "channelId");
		runningCheck();

		final Signature.KeyPair keypair = sessionKeypair != null ? sessionKeypair : Signature.KeyPair.random();

		Promise<Void> promise = Promise.promise();
		runOnContext(v -> {
			channel(channelId).compose(channel -> {
				if (channel == null)
					return Future.failedFuture(new ChannelNotExistsException(channelId.toString()));

				if (!channel.getOwnerId().equals(getUserId()))
					return Future.failedFuture(new InsufficientPermissionException("not owner"));

				final byte[] sessionKey;
				try {
					sessionKey = userIdentity.encrypt(channel.getSessionId(), keypair.privateKey().bytes());
				} catch (CryptoException e) {
					return Future.failedFuture(e);
				}

				Id sessionId = Id.of(keypair.publicKey().bytes());
				RpcPrototypes.ChannelSessionKeyRotationParams params =
						new RpcPrototypes.ChannelSessionKeyRotationParams(sessionId, sessionKey);
				RpcCall<RpcPrototypes.ChannelSessionKeyRotationParams, Void> call = RpcCall.rotateChannelSessionKey(params);
				return sendRpcCall(channelId, call).compose(vv -> {
					Contact updatedChannel = channel.edit().setSessionKey(sessionKey).build();
					return repository.putContactLocally(updatedChannel);
				});
			}).onComplete(promise);
		});
		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Void> updateChannelInfo(Channel channel) {
		Objects.requireNonNull(channel, "channel");
		runningCheck();

		Promise<Void> promise = Promise.promise();
		runOnContext(v -> {
			channel(channel.getId()).compose(origin -> {
				if (origin == null)
					return Future.failedFuture(new ChannelNotExistsException(channel.getId().toString()));

				if (channel == origin)
					return Future.failedFuture(new IllegalStateException("Channel info not changed"));

				if (!origin.getOwnerId().equals(getUserId()))
					return Future.failedFuture(new InsufficientPermissionException("not owner"));

				ObjectNode changes = Json.cborMapper().createObjectNode();
				if (channel.getPermission() != origin.getPermission())
					changes.put("p", channel.getPermission().value());
				if (!Objects.equals(channel.getName(), origin.getName()))
					changes.put("n", channel.getName());
				if (!Objects.equals(channel.getNotice(), origin.getNotice()))
					changes.put("nt", channel.getNotice());
				if (channel.isAnnounce() != origin.isAnnounce())
					changes.put("a", channel.isAnnounce());
				if (channel.getUpdatedAt() != origin.getUpdatedAt())
					changes.put("u", channel.getUpdatedAt());

				RpcCall<JsonNode, Void> call = RpcCall.updateChannelInfo(changes);
				return sendRpcCall(channel.getId(), call).compose(vv -> {
					contactCache.synchronous().invalidate(channel.getId());
					return repository.putContactLocally(channel);
				});
			});
		});
		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Void> setChannelMembersRole(Id channelId, List<Id> members, Channel.Role role) {
		Objects.requireNonNull(channelId, "channelId");
		Objects.requireNonNull(members, "members");
		Objects.requireNonNull(role, "role");
		if (members.isEmpty())
			throw new IllegalArgumentException("No members specified");
		if (role == Channel.Role.OWNER || role == Channel.Role.BANNED)
			throw new IllegalArgumentException("Role " + role + " is not allowed");
		runningCheck();

		Promise<Void> promise = Promise.promise();
		runOnContext(v -> {
			channel(channelId).compose(channel -> {
				if (channel == null)
					return Future.failedFuture(new ChannelNotExistsException(channelId.toString()));

				return channel.loadMembers().compose(na -> {
					ChannelMember member = channel.getMember(getUserId());
					if (member == null)
						return Future.failedFuture(new InsufficientPermissionException("Not a member of the channel"));
					if (!member.isOwner() && !member.isModerator())
						return Future.failedFuture(new InsufficientPermissionException("Not an owner or a moderator of the channel"));

					RpcPrototypes.ChannelMembersRoleParams params = new RpcPrototypes.ChannelMembersRoleParams(members, role);
					RpcCall<RpcPrototypes.ChannelMembersRoleParams, List<Id>> call = RpcCall.updateChannelMembersRole(params);
					return sendRpcCall(channelId, call).compose(ids -> {
						channel.invalidateMembers();
						return repository.updateChannelMembersRole(channelId, ids, role).<Void>mapEmpty();
					});
				});
			}).onComplete(promise);
		});
		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Void> banChannelMembers(Id channelId, List<Id> members) {
		Objects.requireNonNull(channelId, "channelId");
		Objects.requireNonNull(members, "members");
		if (members.isEmpty())
			throw new IllegalArgumentException("No members specified");
		runningCheck();

		Promise<Void> promise = Promise.promise();
		runOnContext(v -> {
			channel(channelId).compose(channel -> {
				if (channel == null)
					return Future.failedFuture(new ChannelNotExistsException(channelId.toString()));

				return channel.loadMembers().compose(na -> {
					ChannelMember member = channel.getMember(getUserId());
					if (member == null)
						return Future.failedFuture(new InsufficientPermissionException("Not a member of the channel"));
					if (!member.isOwner() && !member.isModerator())
						return Future.failedFuture(new InsufficientPermissionException("Not an owner or a moderator of the channel"));

					RpcCall<List<Id>, List<Id>> call = RpcCall.banChannelMembers(members);
					return sendRpcCall(channelId, call).compose(banned -> {
						if (!banned.isEmpty()) {
							channel.invalidateMembers();
							return repository.updateChannelMembersRole(channelId, members, Channel.Role.BANNED).<Void>mapEmpty();
						} else {
							return Future.succeededFuture();
						}
					});
				});
			}).onComplete(promise);
		});
		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Void> unbanChannelMembers(Id channelId, List<Id> members) {
		Objects.requireNonNull(channelId, "channelId");
		Objects.requireNonNull(members, "members");
		if (members.isEmpty())
			throw new IllegalArgumentException("No members specified");
		runningCheck();

		Promise<Void> promise = Promise.promise();
		runOnContext(v -> {
			channel(channelId).compose(channel -> {
				if (channel == null)
					return Future.failedFuture(new ChannelNotExistsException(channelId.toString()));

				return channel.loadMembers().compose(na -> {
					ChannelMember member = channel.getMember(getUserId());
					if (member == null)
						return Future.failedFuture(new InsufficientPermissionException("Not a member of the channel"));
					if (!member.isOwner() && !member.isModerator())
						return Future.failedFuture(new InsufficientPermissionException("Not an owner or a moderator of the channel"));

					RpcCall<List<Id>, List<Id>> call = RpcCall.unbanChannelMembers(members);
					return sendRpcCall(channelId, call).compose(unbanned -> {
						if (!unbanned.isEmpty()) {
							channel.invalidateMembers();
							return repository.updateChannelMembersRole(channelId, members, Channel.Role.MEMBER).<Void>mapEmpty();
						} else {
							return Future.succeededFuture();
						}
					});
				});
			}).onComplete(promise);
		});
		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Void> removeChannelMembers(Id channelId, List<Id> members) {
		Objects.requireNonNull(channelId, "channelId");
		Objects.requireNonNull(members, "members");
		if (members.isEmpty())
			throw new IllegalArgumentException("No members specified");
		runningCheck();

		Promise<Void> promise = Promise.promise();
		runOnContext(v -> {
			channel(channelId).compose(channel -> {
				if (channel == null)
					return Future.failedFuture(new ChannelNotExistsException(channelId.toString()));

				return channel.loadMembers().compose(na -> {
					ChannelMember member = channel.getMember(getUserId());
					if (member == null)
						return Future.failedFuture(new InsufficientPermissionException("Not a member of the channel"));
					if (!member.isOwner() && !member.isModerator())
						return Future.failedFuture(new InsufficientPermissionException("Not an owner or a moderator of the channel"));

					RpcCall<List<Id>, List<Id>> call = RpcCall.removeChannelMembers(members);
					return sendRpcCall(channelId, call).compose(removed -> {
						if (!removed.isEmpty()) {
							channel.invalidateMembers();
							return repository.removeChannelMembers(channelId, members).<Void>mapEmpty();
						} else {
							return Future.succeededFuture();
						}
					});
				});
			}).onComplete(promise);
		});
		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Contact> getContact(Id contactId) {
		Objects.requireNonNull(contactId, "contactId");
		runningCheck();
		return VertxFuture.of(contactCache.get(contactId).thenApply(c -> c));
	}

	@Override
	public VertxFuture<List<Contact>> getContacts() {
		runningCheck();

		Future<List<Contact>> future = repository.getAllContacts().map(contacts -> {
			Map<Id, Contact> result = new LinkedHashMap<>();
			for (Contact c : contacts) {
				PhotonContact cached = contactCache.synchronous().asMap()
						.compute(c.getId(), (k, cc) -> cc != null ? cc : (PhotonContact) c);
				result.put(c.getId(), cached);
			}

			// Return a **mutable** list
			return new ArrayList<>(result.values());
		});
		return VertxFuture.of(future);
	}

	@Override
	public VertxFuture<Void> updateContact(Contact contact) {
		Objects.requireNonNull(contact, "contact");
		runningCheck();

		Promise<Void> promise = Promise.promise();
		runOnContext(v -> {
			contact(contact.getId()).compose(origin -> {
				if (origin == null)
					return Future.failedFuture(new ContactNotExistsException(contact.getId().toString()));

				if (contact == origin)
					return Future.failedFuture(new IllegalStateException("Contact not changed"));

				if (contact.getRevision() < origin.getRevision())
					return Future.failedFuture(new RevisionNotMonotonicException("Contact revision is outdate"));

				ObjectNode changes = Json.cborMapper().createObjectNode();
				if (!Objects.equals(contact.getRemark(), origin.getRemark()))
					changes.put("r", contact.getRemark());
				if (!Objects.equals(contact.getTags(), origin.getTags()))
					changes.put("t", contact.getTags());
				if (contact.isMuted() != origin.isMuted())
					changes.put("m", contact.isMuted());
				if (contact.isBlocked() != origin.isBlocked())
					changes.put("b", contact.isBlocked());
				if (contact.getUpdatedAt() != origin.getUpdatedAt())
					changes.put("u", contact.getUpdatedAt());

				ContactMutation<JsonNode> mutation = ContactMutation.update(contactsRevision, changes);
				RpcCall<ContactMutation<JsonNode>, Integer> call = RpcCall.contactMutate(mutation);
				return sendRpcCall(contact.getId(), call).compose(revision -> {
					Contact updatedContact = ((ContactEditorImpl) contact.edit()).setRevision(revision).build();
					return repository.putContacts(revision, List.of(updatedContact)).onSuccess(vv ->
						contactCache.synchronous().invalidate(contact.getId())
					);
				});
			}).onComplete(promise);
		});
		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Boolean> removeContacts(List<Id> contactIds) {
		Objects.requireNonNull(contactIds, "contactIds");
		if (contactIds.isEmpty())
			throw new IllegalArgumentException("No contacts specified");
		runningCheck();

		Promise<Boolean> promise = Promise.promise();
		runOnContext(v -> {
			ContactMutation<List<Id>> mutation = ContactMutation.remove(contactsRevision, contactIds);
			RpcCall<ContactMutation<List<Id>>, Integer> call = RpcCall.contactMutate(mutation);
			sendRpcCall(homePeerId, call).compose(revision ->
					repository.removeContacts(revision, contactIds).map(ignored -> {
						contactsRevision = revision;
						return true;
					})
			).onComplete(promise);
		});
		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Void> clearContacts() {
		runningCheck();

		Promise<Void> promise = Promise.promise();
		runOnContext(v -> {
			ContactMutation<Void> mutation = ContactMutation.clear(contactsRevision);
			RpcCall<ContactMutation<Void>, Integer> call = RpcCall.contactMutate(mutation);
			sendRpcCall(homePeerId, call).<Void>compose(revision ->
					repository.clearContacts(revision).map(vv -> {
						contactsRevision = revision;
						return null;
					})
			).onComplete(promise);
		});
		return VertxFuture.of(promise.future());
	}

	@Override
	protected Future<Void> deploy() {
		this.running = true;

		// initialize the repository

		this.contactCache = VertxCaffeine.newBuilder(vertx)
				.maximumSize(512)
				.expireAfterAccess(5, TimeUnit.MINUTES)
				.buildAsync((id, executor) ->
						VertxFuture.of(repository.getContact(id).map(c -> (PhotonContact) c)));

		this.conversationCache = VertxCaffeine.newBuilder(vertx)
				.maximumSize(512)
				.expireAfterAccess(5, TimeUnit.MINUTES)
				.buildAsync((id, executor) ->
						VertxFuture.of(repository.getConversation(id).map(c -> (ConversationImpl) c)));

		return connect();
	}

	@Override
	protected Future<Void> undeploy() {
		this.running = false;
		return disconnect().compose(v -> {
			// close the repository
			return Future.succeededFuture();
		});
	}

	private Future<PhotonContact> contact(Id id) {
		return Future.fromCompletionStage(contactCache.get(id));
	}

	private Future<ChannelImpl> channel(Id id) {
		return Future.fromCompletionStage(contactCache.get(id)).map(contact ->
				contact instanceof ChannelImpl channel ? channel : null);
	}

	private Future<Void> refreshChannel(ChannelImpl channel) {
		final Id channelId = channel.getId();

		RpcCall<Void, RpcPrototypes.ChannelInfo> call = RpcCall.getChannelInfo();
		return sendRpcCall(channelId, call).compose(ci -> {
			ChannelImpl updatedChannel = new ChannelImpl(channelId, ci.sessionKey(), ci.ownerId(), ci.permission(),
					ci.name(), ci.notice(), ci.announce(), channel.getRemark(), channel.getTags(), channel.isMuted(),
					channel.isBlocked(), ci.createdAt(), ci.updateAt(), channel.getRevision());
			return repository.putContactLocally(updatedChannel).compose(vv ->
					repository.refillChannelMembers(channelId, ci.members()).andThen(ar ->
							contactCache.synchronous().invalidate(channelId)
					)
			);
		});
	}

	private <P, R> Future<R> sendRpcCall(Id recipient, RpcCall<P, R> call) {
		inflightRpcCalls.put(call.getId(), call);
		long now = System.currentTimeMillis();
		Id messageId = MessageImpl.generateId(getDeviceId(), now);
		MessageImpl<RpcRequest<P>> message = new MessageImpl<>(messageId, recipient, Message.Type.CONTROL_MESSAGE, now, call.getRequest());
		log.debug("Sending RPC call {}:{} to {} ...", call.getId(), call.getMethod(), recipient);
		return sendMessageInternal(message).compose(packageId -> call.getFuture(), error -> {
			inflightRpcCalls.remove(call.getId());
			log.error("Send RPC call {} failed", call.getId(), error);
			return Future.failedFuture(error);
		});
	}

	protected Future<Message> sendMessage(Message message) {
		@SuppressWarnings("unchecked")
		MessageImpl<DefaultContent<?>> msg = (MessageImpl<DefaultContent<?>>)message;

		Promise<Void> promise = Promise.promise();
		vertxContext.runOnContext((v) -> {
			repository.putMessage(msg).compose(vv ->
					sendMessageInternal(msg).compose(packetId ->
									msg.getFuture().onSuccess(vvv ->
											repository.updateMessageSentTime(msg)
									)
					)
			);
		});

		return promise.future().map(message);
	}

	private Future<Integer> sendMessageInternal(MessageImpl<?> message) {
		// the input message is immutable, so we should not modify it

		// encrypt message payload for the recipient
		return encryptMessage(message).compose(encrypted -> {
			// encrypt the whole message for transmission
			byte[] mqttPayload = serviceContext.encrypt(encrypted.serialize());
			Buffer buffer = Buffer.buffer(mqttPayload);
			message.prepareForSending();
			return mqttClient.publish(topics.deviceOutbox, buffer, MqttQoS.AT_LEAST_ONCE, false, false).andThen(ar -> {
				if (ar.succeeded()) {
					log.debug("Sending message to {} ...", message.getRecipient());
					inflightMessages.put(ar.result(), message);
				} else {
					log.error("Send message to {} failed", message.getRecipient(), ar.cause());
				}
			});
		});
	}

	private Future<BytesMessage> encryptMessage(MessageImpl<?> message) {
		final byte[] payload;
		try {
			payload = message.getPayloadAsBytes();
		} catch (Exception e) {
			return Future.failedFuture(e);
		}

		if (payload == null || payload.length == 0)
			return Future.succeededFuture(BytesMessage.dup(message, null));

		return switch (message.getType()) {
			case CONTENT_MESSAGE -> contact(message.getRecipient()).compose(contact -> {
				try {
					byte[] encryptedPayload = contact.getTxCryptoContext().encrypt(payload);
					return Future.succeededFuture(BytesMessage.dup(message, encryptedPayload));
				} catch (CryptoException e) {
					return Future.failedFuture(e);
				}
			});

			case CONTROL_MESSAGE -> {
				if (message.getRecipient().equals(homePeerId)) {
					// no need to encrypt the control message send to the home peer
					yield Future.succeededFuture(BytesMessage.dup(message, payload));
				} else {
					try {
						CryptoContext ctx = userIdentity.createCryptoContext(message.getRecipient());
						byte[] encryptedPayload = ctx.encrypt(payload);
						yield Future.succeededFuture(BytesMessage.dup(message, encryptedPayload));
					} catch (CryptoException e) {
						yield Future.failedFuture(e);
					}
				}
			}

			case STATE_MESSAGE -> {
				log.error("INTERNAL ERROR: trying to encrypt a STATE message");
				yield Future.failedFuture("INTERNAL ERROR");
			}
		};
	}

	private Future<BytesMessage> checkAndDecryptMessage(BytesMessage message) {
		final Id recipient = message.getRecipient();
		final Id from = message.getFrom();
		final boolean toMe = recipient.equals(getUserId());
		final Id conversationId = toMe ? from : recipient;
		message.setConversationId(conversationId);
		message.received();

		final byte[] payload = message.getPayload();
		if (payload == null || payload.length == 0)
			return Future.succeededFuture(message);

		Message.Type type = message.getType();
		return switch(type) {
			case CONTENT_MESSAGE -> contact(conversationId).compose(contact -> {
					if (contact == null) {
						log.error("Received a {} from unknown contact {}, ignored", type, from);
						return Future.failedFuture("Received a message from unknown contact");
					}

					if (contact.isBlocked()) {
						log.debug("Received a {}} from blocked contact {}, ignored", type, from);
						return Future.failedFuture("Received a message from blocked contact");
					}

					try {
						CryptoContext ctx = contact instanceof ChannelImpl channel ?
								channel.getRxCryptoContext(from) : contact.getRxCryptoContext();
						byte[] decryptedPayload = ctx.decrypt(payload);
						return Future.succeededFuture(BytesMessage.dup(message, decryptedPayload));
					} catch (CryptoException e) {
						log.error("Decrypt {} from {} failed", type, from, e);
						return Future.failedFuture(e);
					}
				});

			case CONTROL_MESSAGE, STATE_MESSAGE -> {
				if (toMe) {
					// from the home peer, the payload is not encrypted
					if (from.equals(homePeerId))
						yield Future.succeededFuture(message);

					if (type == Message.Type.CONTROL_MESSAGE) {
						yield Future.failedFuture("Received a CONTROL message from non-home peer to user");
					} else {
						try {
							CryptoContext ctx = userIdentity.createCryptoContext(from);
							byte[] decryptedPayload = ctx.decrypt(payload);
							yield Future.succeededFuture(BytesMessage.dup(message, decryptedPayload));
						} catch (CryptoException e) {
							log.error("Decrypt {} from {} failed", type, from, e);
							yield Future.failedFuture(e);
						}
					}
				} else {
					// from channel
					yield contact(recipient).compose(contact -> {
						if (contact == null) {
							log.error("Received a {} from unknown channel {}, ignored", type, recipient);
							return Future.failedFuture("Received a message from unknown channel");
						}

						if (contact.isBlocked()) {
							log.debug("Received a {} from blocked channel {}, ignored", type, recipient);
							return Future.failedFuture("Received a message from blocked channel");
						}

						if (!(contact instanceof ChannelImpl channel)) {
							log.error("Received a {} from non-channel contact {}, ignored", type, recipient);
							return Future.failedFuture("Received a message send to non-channel contact");
						}

						try {
							CryptoContext ctx = channel.getRxCryptoContext();
							byte[] decryptedPayload = ctx.decrypt(payload);
							return Future.succeededFuture(BytesMessage.dup(message, decryptedPayload));
						} catch (CryptoException e) {
							log.error("Decrypt {} from channel {} failed", type, recipient, e);
							return Future.failedFuture(e);
						}
					});
				}
			}
		};
	}

	private Future<Void> resolvePeer() {
		if (config.getServiceEndpoint() == null) {
			log.info("Looking up service peer {} ...", config.getServicePeerId());
			return Future.fromCompletionStage(node.findPeer(config.getServicePeerId())).compose(peer -> {
				if (peer == null) {
					log.error("Service peer not found {}", config.getServicePeerId());
					return Future.failedFuture("Service peer not found: " + config.getServicePeerId());
				}

				URI uri = URI.create(peer.getEndpoint());
				if (!uri.isAbsolute() || uri.getHost() == null || uri.getScheme() == null ||
						uri.getPort() <= 0 || uri.getPort() > 65535 ||
						(!uri.getScheme().equals("mqtt") && !uri.getScheme().equals("mqtts"))) {
					log.error("Service peer endpoint {} is invalid", peer.getEndpoint());
					return Future.failedFuture("Service peer endpoint is invalid: " + peer.getEndpoint());
				}

				serviceEndpoint = uri;
				return Future.succeededFuture();
			});
		} else {
			serviceEndpoint = config.getServiceEndpoint();
			return Future.succeededFuture();
		}
	}

	private String getPassword() {
		byte[] nonce = CryptoBox.Nonce.random().bytes();
		byte[] deviceSig = deviceIdentity.sign(nonce);

		byte[] password = new byte[nonce.length + deviceSig.length];
		System.arraycopy(nonce, 0, password, 0, nonce.length);
		System.arraycopy(deviceSig, 0, password, nonce.length, deviceSig.length);
		return Base58.encode(password);
	}

	private int getRetryInterval() {
		if (failures == 0)
			return 0;				// no delay
		else if (failures <= 6)
			return (1 << failures);	// 2 ~ 64 seconds retry interval
		else if (failures <= 20)
			return 300;				// 5 minutes retry interval
		else
			return 600;				// 10 minutes retry interval
	}

	private Future<Void> connect() {
		if (!running)
			return Future.succeededFuture();

		log.info("Connecting ...");

		if (connectionListener != null)
			connectionListener.connecting();

		return repository.getContactsRevision().compose(revision -> {
			MqttClientOptions options = new MqttClientOptions()
					.setAutoGeneratedClientId(false)
					.setClientId(getDeviceId().toBase58String())
					.setUsername(getUserId().toBase58String())
					.setPassword(getPassword() + "?contactsRevision=" + revision)
					.setMaxMessageSize(16 * 1024)
					.setReceiveBufferSize(18 * 1024)
					.setKeepAliveInterval(60)
					.setHostnameVerificationAlgorithm("")
					.setCleanSession(false);

			if (serviceEndpoint.getScheme().equals("mqtts")) {
				options.setSsl(true);
				if (sslCert != null) {
					options.setTrustOptions(new PemTrustOptions().addCertValue(Buffer.buffer(sslCert)));
					options.setTrustAll(false);
				} else {
					options.setTrustAll(true);
				}
			} else {
				options.setSsl(false);
			}

			MqttClient client = MqttClient.create(vertx, options);
			client.closeHandler((v) -> onClose())
					.subscribeCompletionHandler(this::onSubscribeCompletion)
					.publishHandler(this::onPublish)
					.publishCompletionHandler(this::onPublishCompletion)
					.publishCompletionExpirationHandler(this::onPublishCompletionExpiration)
					.publishCompletionUnknownPacketIdHandler(this::onPublishCompletionUnknownPacketId)
					.unsubscribeCompletionHandler(this::onUnsubscribeCompletion)
					.exceptionHandler((e) -> log.error("Messaging client error", e));

			return client.connect(serviceEndpoint.getPort(), serviceEndpoint.getHost()).compose(ack -> {
				log.info("Connected to the messaging server {} @ {}", homePeerId, serviceEndpoint);
				Map<String, Integer> topics = Map.of(
						this.topics.userInbox, MqttQoS.AT_LEAST_ONCE.value(),
						this.topics.userOutbox, MqttQoS.AT_LEAST_ONCE.value(),
						this.topics.deviceInbox, MqttQoS.AT_LEAST_ONCE.value());
				return mqttClient.subscribe(topics).compose(pid -> {
					log.info("Subscribing the messages...");
					this.failures = 0;
					this.mqttClient = client;
					return Future.succeededFuture();
				}, error -> {
					this.failures++;

					if (running) {
						long delay = getRetryInterval();
						log.warn("Failed to subscribe to the messaging server {} @ {}, will retry in {} seconds...",
								homePeerId, serviceEndpoint, delay);
						vertx.setTimer(delay * 1000L, (tid) -> connect());
					} else {
						log.warn("Failed to subscribe to the messaging server {} @ {}", homePeerId, serviceEndpoint);
					}

					return Future.succeededFuture();
				});
			}, error -> {
				this.failures++;

				if (running) {
					long delay = getRetryInterval();
					log.warn("Failed to connect to the messaging server {} @ {}, will retry in {} seconds...",
							homePeerId, serviceEndpoint, delay);
					vertx.setTimer(delay * 1000L, (tid) -> connect());
				} else {
					log.warn("Failed to connect to the messaging server {} @ {}", homePeerId, serviceEndpoint);
				}

				return Future.succeededFuture();
			});
		});
	}

	private Future<Void> disconnect() {
		if (mqttClient == null || !mqttClient.isConnected())
				return Future.succeededFuture();

		return mqttClient.disconnect();
	}

	private void onClose() {
		this.connected = false;
		if (connectionListener != null)
			connectionListener.disconnected();

		if (!running) {
			log.info("Disconnected");
		} else {
			this.failures++;
			int delay = getRetryInterval();
			log.warn("Connection lost, will try to reconnect in {} seconds...", delay);
			vertx.setTimer(delay * 1000L, (tid) -> connect());
		}
	}

	private void onSubscribeCompletion(MqttSubAckMessage m) {
		List<Integer> grantedQoSLevels = m.grantedQoSLevels();
		log.debug("Subscription complete:\n\t{} - {}\n\t{} - {}\n\t{} - {}",
				topics.userInbox, grantedQoSLevels.get(0),
				topics.userOutbox, grantedQoSLevels.get(1),
				topics.deviceInbox, grantedQoSLevels.get(2));

		log.info("Subscribe topics success");
		this.connected = true;
		if (connectionListener != null)
			connectionListener.connected();
	}

	private void onUnsubscribeCompletion(int packetId) {
		// nothing to do
	}

	private void onPublishCompletion(int packetId) {
		MessageImpl<?> message = inflightMessages.remove(packetId);
		if (message == null) {
			// noinspection LoggingSimilarMessage
			log.error("INTERNAL ERROR: no message associated with packet {}", packetId);
			return;
		}

		message.sent();
	}

	private void onPublishCompletionExpiration(int packetId) {
		MessageImpl<?> message = inflightMessages.remove(packetId);
		if (message == null) {
			// noinspection LoggingSimilarMessage
			log.error("INTERNAL ERROR: no message associated with packet {}", packetId);
			return;
		}

		// Sent message failed, remove it from the sending messages
		inflightMessages.remove(packetId);
		message.failed(new MessageTimeoutException("Get message ACK timeout"));
	}

	private void onPublishCompletionUnknownPacketId(int packetId) {
		log.warn("INTERNAL WARN: unknown packet {}", packetId);
		// check is existing message associated with the unknown packet id
		MessageImpl<?> message = inflightMessages.remove(packetId);
		if (message != null)
			message.sent();
	}

	private void onPublish(MqttPublishMessage mm) {
		final String topic = mm.topicName();
		final BytesMessage received;

		try {
			// decrypt the message envelope
			final byte[] mqttPayload = serviceContext.decrypt(mm.payload().getBytes());
			received = BytesMessage.parse(mqttPayload);
		} catch (Exception e) {
			log.error("Failed to process message from topic: {}", topic, e);
			return;
		}

		log.trace("Got message from topic: {}, sender: {}", topic, received.getFrom());

		final Message.Type mt = received.getType();
		final Topics.Type tt = topics.typeOf(topic);
		if (tt == Topics.Type.USER_INBOX) {
			if (mt == Message.Type.CONTROL_MESSAGE) {
				log.warn("Received a {} from user inbox, ignored", mt);
				return;
			}
		} else if (tt == Topics.Type.USER_OUTBOX) {
			if (mt == Message.Type.CONTROL_MESSAGE) {
				log.warn("Received a {} from user outbox, ignored", mt);
				return;
			}
		} else if (tt == Topics.Type.DEVICE_INBOX) {
			if (mt != Message.Type.CONTROL_MESSAGE) {
				log.warn("Received a {} from device inbox, ignored", mt);
				return;
			}
		} else {
			log.warn("Received a {} message from unknown topic: {}, ignored", mt, topic);
			return;
		}

		checkAndDecryptMessage(received).onSuccess(message -> {
			if (tt == Topics.Type.USER_INBOX)
				processIncomingMessage(message);
			else if (tt == Topics.Type.USER_OUTBOX)
				processingOutgoingMessage(message);
			else // if (tt == Topics.Type.DEVICE_INBOX)
				processControlMessage(message);
		}).onFailure(e -> {
			log.error("Failed to process message from topic: {}, sender: {}", topic, received.getFrom(), e);
		});
	}

	private void processIncomingMessage(BytesMessage message) {
		final Message.Type type = message.getType();
		if (type == Message.Type.CONTENT_MESSAGE) {
			DefaultContent<JsonNode> content = DefaultContent.parse(message.getPayload());
			MessageImpl<Message.Content> contentMessage = message.dup(content);
			if (messageListener != null)
				messageListener.onMessage(contentMessage);

			return;
		}

		if (type == Message.Type.STATE_MESSAGE) {
			Notification.GenericNotification gn = Notification.parse(message.getPayload());
			if (gn.getSource().equals(userIdentity.getId()) && gn.isAssociated(getDeviceId())) {
				// This notification was triggered by an RPC request from this device.
				// Since the local state was already updated via the RPC response,
				// this redundant notification can be safely ignored.
				return;
			}

			if (message.getFrom().equals(homePeerId)) {
				// Notification from home peer
				processHomePeerNotification(gn);
				return;
			}

			if (message.getRecipient().equals(getUserId())) {
				// Notification from the other users.
				// Ensure the notification source reflects the actual message sender.
				if (!gn.getSource().equals(message.getFrom())) {
					log.warn("Received a invalid notification from {} - source not matched the sender, ignored",
							message.getFrom());
					return;
				}
				processUserNotification(gn);
				return;
			}

			// Notification from channel?
			channel(message.getRecipient()).compose(channel -> {
				if (channel == null) {
					log.warn("Received a {} from unknown channel {}, ignored", type, message.getRecipient());
					return Future.succeededFuture();
				}
				return processChannelNotification(channel, gn);
			});
		}

		log.warn("Received a {} message from user inbox, ignored", type);
	}

	private void processingOutgoingMessage(BytesMessage message) {
		// message is sent by this device?
		if (message.isAssociated(getDeviceId()))
			return;

		message.setSentAt();
		switch (message.getType()) {
			case CONTENT_MESSAGE -> {
				DefaultContent<JsonNode> content = DefaultContent.parse(message.getPayload());
				MessageImpl<DefaultContent<?>> msg = message.dup(content);
				repository.putMessage(msg).andThen(ar -> {
					if (messageListener != null) {
						messageListener.onSent(msg);
					}
				});
			}

			case STATE_MESSAGE -> {
				Notification.GenericNotification gn = Notification.parse(message.getPayload());
				switch (gn.getEvent()) {
					case FRIEND_REQUEST -> {
						String hello = gn.getContentAs(String.class);
						FriendRequestImpl fr = new FriendRequestImpl(message.getRecipient(), gn.getSource(), hello,
								gn.getTimestamp(), System.currentTimeMillis());
						repository.putFriendRequest(fr);
					}

					case FRIEND_REQUEST_ACCEPT -> repository.getFriendRequest(message.getRecipient()).compose(fr -> {
						// Trying to update the existing friend request status.
						// **DO NOT** add the contact here; the accepting device is responsible for adding
						// the new contact and broadcasting the change via a CONTACT_MUTATE notification.
						if (fr == null) {
							log.warn("No friend request to {}, ignored", message.getRecipient());
							return Future.succeededFuture();
						}

						FriendRequestImpl request = (FriendRequestImpl) fr;
						request.accept(gn.getTimestamp());
						return repository.putFriendRequest(request);
					});

					default -> log.warn("Received an unknown notification from user {}, ignored", gn.getSource());
				};
			}

			case CONTROL_MESSAGE -> log.warn("Received a {} message from user outbox, ignored", message.getType());
		}
	}

	private void processControlMessage(BytesMessage message) {
		final Message.Type type = message.getType();
		if (type != Message.Type.CONTROL_MESSAGE) {
			log.warn("Received a {} message from device inbox, ignored", type);
			return;
		}

		final GenericRpcResponse response;
		try {
			response = GenericRpcResponse.parse(message.getPayload());
		} catch (Exception e) {
			log.error("Failed to parse RPC response from the message from device inbox", e);
			return;
		}

		@SuppressWarnings("unchecked")
		final RpcCall<?, ?> call = inflightRpcCalls.remove(response.getId());
		if (call == null) {
			log.warn("Received a RPC response but no matching call found, ignored");
			return;
		}

		if (response.failed())
			log.warn("RPC call returned an error: {}", response.getError());
		else
			log.debug("RPC call completed successfully");

		call.setResponse(response);
	}

	private Future<Void> processUserNotification(Notification.GenericNotification gn) {
		return switch (gn.getEvent()) {
			case FRIEND_REQUEST -> {
				String hello = gn.getContentAs(String.class);
				FriendRequestImpl fr = new FriendRequestImpl(gn.getSource(), gn.getSource(), hello,
						gn.getTimestamp(), System.currentTimeMillis());
				yield repository.putFriendRequest(fr).andThen(ar -> {
					if (messageListener != null)
						messageListener.onFriendRequest(fr.getUserId(), fr.getHello());
				});
			}

			case FRIEND_REQUEST_ACCEPT -> repository.getFriendRequest(gn.getSource()).compose(fr -> {
				if (fr == null || !fr.getInitiatorId().equals(gn.getSource())) {
					log.warn("Received a friend request accept notification without matched request from: {}, ignored", gn.getSource());
					return Future.succeededFuture();
				}

				byte[] sessionKey = gn.getContentAs(byte[].class);
				if (sessionKey == null || sessionKey.length != Signature.PrivateKey.BYTES) {
					log.warn("Received a friend request accept notification with invalid session key: {}, ignored", gn.getSource());
					return Future.succeededFuture();
				}

				FriendRequestImpl request = (FriendRequestImpl) fr;
				request.accept();

				// All user devices receive the 'accept' notification.
				// Since we are lacking a leader-election mechanism between devices, all devices attempt
				// to add the new contact upon receiving the 'accept' notification.
				// The contact synchronization service handles concurrency by ensuring only
				// the first received update is applied and synchronized to all devices.
				return repository.putFriendRequest(request).compose(v -> {
					Friend friend = new Friend(gn.getSource(), sessionKey, null);
					return repository.putContactLocally(friend).compose(vv ->
							addFriendInternal(gn.getSource(), sessionKey, null).map(contact -> {
								if (messageListener != null)
									messageListener.onFriendRequestAccepted(contact.getId());
								return null;
							})
					);
				});
			});

			default -> {
				log.warn("Received an unknown notification from user {}, ignored", gn.getSource());
				yield Future.succeededFuture();
			}
		};
	}

	private Future<Void> processHomePeerNotification(Notification.GenericNotification gn) {
		// notification from home peer
		return switch (gn.getEvent()) {
			case CONTACT_SYNC -> {
				ContactSync contactSync = gn.getContentAs(ContactSync.class);
				yield applyContactSync(contactSync);
			}

			case CONTACT_MUTATE -> {
				ContactMutation.GenericContactMutation mutation = gn.getContentAs(ContactMutation.GenericContactMutation.class);
				yield applyContactMutation(mutation);
			}

			case CHANNEL_CREATE -> {
				RpcPrototypes.ChannelInfo ci = gn.getContentAs(RpcPrototypes.ChannelInfo.class);
				// The session key is encrypted with the user's own key; no external decryption needed.
				ChannelImpl channel = new ChannelImpl(ci.channelId(), ci.sessionKey(), ci.ownerId(),
						ci.permission(), ci.name(), ci.notice(), ci.announce(), ci.createdAt(), ci.updateAt());

				// Save the channel to the local database immediately to provide a responsive UI.
				// We bypass the official 'contactsRevision' update here because this is a local-only
				// optimistic insert. The server will later broadcast a CONTACT_MUTATE notification
				// triggered by the device that initiated the original CHANNEL_CREATE request,
				// which will perform the formal, synchronized update of the contact list and revision state.
				yield repository.putContactLocally(channel).compose(v -> {
					if (!ci.members().isEmpty())
						return repository.putChannelMembers(channel.getId(), ci.members());
					else
						return Future.succeededFuture();
				});
			}

			default -> {
				log.warn("Received an unknown notification from home peer, ignored");
				yield Future.succeededFuture();
			}
		};
	}

	private Future<Void> processChannelNotification(ChannelImpl channel, Notification.GenericNotification gn) {
		return switch (gn.getEvent()) {
			case CHANNEL_DELETE -> repository.removeContactLocally(channel.getId()).andThen(ar -> {
					contactCache.synchronous().invalidate(channel.getId());

					if (ar.succeeded() && !ar.result()) // no local channel removed
						return;

					if (channelListener != null)
						channelListener.onChannelDeleted(channel);
				}).mapEmpty();

			case CHANNEL_JOIN -> {
				RpcPrototypes.ChannelInfo ci = gn.getContentAs(RpcPrototypes.ChannelInfo.class);
				// The session key is encrypted with the user's own key; no external decryption needed.
				ChannelImpl ch = new ChannelImpl(ci.channelId(), ci.sessionKey(), ci.ownerId(),
						ci.permission(), ci.name(), ci.notice(), ci.announce(), ci.createdAt(), ci.updateAt());

				// Save the channel to the local database immediately to provide a responsive UI.
				// We bypass the official 'contactsRevision' update here because this is a local-only
				// optimistic insert. The server will later broadcast a CONTACT_MUTATE notification
				// triggered by the device that initiated the original CHANNEL_CREATE request,
				// which will perform the formal, synchronized update of the contact list and revision state.
				yield repository.putContactLocally(channel).compose(v -> {
					if (!ci.members().isEmpty())
						return repository.putChannelMembers(channel.getId(), ci.members());
					else
						return Future.succeededFuture();
				}).andThen(ar -> {
					if (channelListener != null)
						channelListener.onJoinedChannel(ch);
				});
			}

			case CHANNEL_LEAVE -> {
				yield repository.removeContactLocally(channel.getId()).andThen(ar -> {
					contactCache.synchronous().invalidate(channel.getId());

					if (ar.succeeded() && !ar.result()) // no local channel removed
						return;

					if (channelListener != null)
						channelListener.onLeftChannel(channel);
				}).mapEmpty();
			}

			case CHANNEL_TRANSFER_OWNERSHIP -> {
				Id newOwnerId = gn.getContentAs(Id.class);
				Id oldOwnerId = channel.getOwnerId();
				ChannelMember oldOwner = channel.getMember(channel.getOwnerId());
				ChannelMember newOwner = channel.getMember(newOwnerId);
				if (oldOwner == null || newOwner == null) {
					// not up-to-date?
					yield refreshChannel(channel);
				} else {
					yield repository.updateChannelOwnership(channel.getId(), channel.getOwnerId(), newOwnerId).compose(v -> {
						ChannelImpl updatedChannel = channel.editChannel().setOwnerId(newOwnerId).build();
						repository.putContactLocally(updatedChannel);
						channel.invalidateMembers();
						contactCache.synchronous().invalidate(channel.getId());
						return Future.succeededFuture();
					}, error -> refreshChannel(channel)).andThen(ar -> {
						if (channelListener != null)
							channelListener.onChannelOwnershipTransferred(channel, oldOwnerId, newOwnerId);
					});
				}
			}

			case CHANNEL_ROTATE_SESSION_KEY -> {
				PhotonContact updatedChannel = channel.edit().setSessionKey(gn.getContentAs(byte[].class)).build();
				yield repository.putContactLocally(updatedChannel).andThen(ar -> {
					contactCache.synchronous().invalidate(channel.getId());
					if (channelListener != null)
						channelListener.onChannelSessionKeyRotated(channel);
				});
			}

			case CHANNEL_UPDATE_INFO -> {
				ChannelImpl updatedChannel = channel.editChannel().patch(gn.getContent()).build();
				yield repository.putContactLocally(updatedChannel).andThen(ar -> {
					contactCache.synchronous().invalidate(channel.getId());
					if (channelListener != null)
						channelListener.onChannelUpdated(channel);
				});
			}

			case CHANNEL_UPDATE_MEMBERS_ROLE -> {
				RpcPrototypes.ChannelMembersRoleParams content = gn.getContentAs(RpcPrototypes.ChannelMembersRoleParams.class);
				yield repository.updateChannelMembersRole(channel.getId(), content.memberIds(), content.role()).andThen(ar -> {
					channel.invalidateMembers();
					if (channelListener != null) {
						repository.getChannelMembers(channel.getId(), content.memberIds()).onSuccess(members -> {
							if (channelListener != null)
								channelListener.onChannelMembersRoleChanged(channel, members, content.role());
						});
					}
				}).mapEmpty();
			}

			case CHANNEL_BAN_MEMBERS -> {
				List<Id> memberIds = gn.getContentAsListOf(Id.class);
				yield repository.updateChannelMembersRole(channel.getId(), memberIds, Channel.Role.BANNED).andThen(ar -> {
					channel.invalidateMembers();
					if (channelListener != null) {
						repository.getChannelMembers(channel.getId(), memberIds).onSuccess(members -> {
							if (channelListener != null)
								channelListener.onChannelMembersBanned(channel, members);
						});
					}
				}).mapEmpty();
			}

			case CHANNEL_UNBAN_MEMBERS -> {
				List<Id> memberIds = gn.getContentAsListOf(Id.class);
				yield repository.updateChannelMembersRole(channel.getId(), memberIds, Channel.Role.MEMBER).andThen(ar -> {
					channel.invalidateMembers();
					if (channelListener != null) {
						repository.getChannelMembers(channel.getId(), memberIds).onSuccess(members -> {
							if (channelListener != null)
								channelListener.onChannelMembersUnbanned(channel, members);
						});
					}
				}).mapEmpty();
			}

			case CHANNEL_REMOVE_MEMBERS -> {
				List<Id> memberIds = gn.getContentAsListOf(Id.class);
				yield repository.getChannelMembers(channel.getId(), memberIds).compose(members -> {
					if (members.isEmpty())
						return Future.succeededFuture();

					return repository.removeChannelMembers(channel.getId(), memberIds).andThen(ar -> {
						channel.invalidateMembers();
						if (channelListener != null)
							channelListener.onChannelMembersRemoved(channel, members);
					}).mapEmpty();
				});
			}

			case CHANNEL_MEMBER_JOIN -> {
				ChannelMember member = gn.getContentAs(ChannelMember.class);
				yield repository.putChannelMember(channel.getId(), member).andThen(ar -> {
					channel.invalidateMembers();
					if (channelListener != null)
						channelListener.onChannelMemberJoined(channel, member);
				});
			}

			case CHANNEL_MEMBER_LEAVE -> {
				Id memberId = gn.getContentAs(Id.class);
				yield repository.getChannelMember(channel.getId(), memberId).compose(member -> {
					if (member == null)
						return Future.succeededFuture(new ChannelMember(memberId, Channel.Role.MEMBER, 0));
					else
						return repository.removeChannelMember(channel.getId(), memberId).map(member);
				}, error ->
						Future.succeededFuture(new ChannelMember(memberId, Channel.Role.MEMBER, 0))
				).map(member -> {
					channel.invalidateMembers();
					if (channelListener != null)
						channelListener.onChannelMemberLeft(channel, member);
					return null;
				});
			}

			default -> {
				log.warn("Received an unknown notification from channel {}, ignored", channel.getId());
				yield Future.succeededFuture();
			}
		};
	}

	private Future<Void> applyContactSync(ContactSync contactSync) {
		return switch (contactSync.getType()) {
			case UP_TO_DATE -> Future.succeededFuture();

			case DELTA -> {
				int revision = contactSync.getRevision();
				List<ContactMutation.GenericContactMutation> mutations = contactSync.getMutations();
				Future<Void> applyChain = Future.succeededFuture();
				for (ContactMutation.GenericContactMutation mutation : mutations) {
					applyChain = applyChain.compose(v -> applyContactMutation(mutation));
				}
				yield applyChain.compose(v -> {
					if (contactsRevision != revision) {
						log.error("the revision not up-to-data after applied the mutations, expected: {}, actual: {}",
								revision, contactsRevision);
						return Future.failedFuture(new IllegalStateException("the revision not up-to-data after applied the mutations"));
					}

					return Future.succeededFuture();
				});
			}

			case SNAPSHOT -> {
				int revision = contactSync.getRevision();
				List<Contact> contacts = contactSync.getContacts();
				yield repository.putContacts(revision, contacts).onSuccess(v -> {
					contactsRevision = revision;
				});
			}
		};
	}
	private Future<Void> applyContactMutation(ContactMutation.GenericContactMutation mutation) {
		final int revision = mutation.getRevision();
		return switch (mutation.getOp()) {
			case ADD -> {
				Contact contact = mutation.getDataAs(PhotonContact.class);
				yield repository.putContacts(revision, List.of(contact)).map(v -> {
					contactsRevision = revision;
					if (contactListener != null)
						contactListener.onContactAdded(contact);
					return null;
				});
			}

			case UPDATE -> {
				final JsonNode changes = mutation.getData();
				final Id contactId;
				try {
					contactId = Id.of(changes.get("id").binaryValue());
				} catch (Exception e) {
					// TODO: how to handle this case?
					log.warn("Received an invalid contact mutation, ignored");
					yield Future.failedFuture("Invalid contact mutation");
				}

				yield contact(contactId).compose(contact -> {
					if (contact == null) {
						// TODO: how to handle this case?
						log.warn("Received a non-exists contact mutation, ignored");
						return Future.failedFuture("Non-exists contact mutation");
					}

					PhotonContact updatedContact = contact.edit().patch(changes).build();
					return repository.putContacts(revision, List.of(updatedContact)).map(v -> {
						contactsRevision = revision;
						contactCache.synchronous().invalidate(contactId);
						if(contactListener != null)
							contactListener.onContactsUpdated(List.of(contact));
						return null;
					});
				}).mapEmpty();
			}

			case REMOVE -> {
				List<Id> contactIds = mutation.getDataAsListOf(Id.class);
				yield repository.removeContacts(revision, contactIds).map(removed -> {
					contactsRevision = revision;
					if (removed && contactListener != null)
						contactListener.onContactRemoved(contactIds);
					return null;
				});
			}

			case CLEAR -> repository.clearContacts(revision).map(v -> {
				contactsRevision = revision;
				if (contactListener != null)
						contactListener.onContactsCleared();
				return null;
			});
		};
	}
}