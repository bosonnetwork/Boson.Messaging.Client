package io.bosonnetwork.photonmessaging.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
import io.bosonnetwork.photonmessaging.Channel;
import io.bosonnetwork.photonmessaging.ChannelListener;
import io.bosonnetwork.photonmessaging.Configuration;
import io.bosonnetwork.photonmessaging.ConnectionListener;
import io.bosonnetwork.photonmessaging.Contact;
import io.bosonnetwork.photonmessaging.ContactListener;
import io.bosonnetwork.photonmessaging.Conversation;
import io.bosonnetwork.photonmessaging.InviteTicket;
import io.bosonnetwork.photonmessaging.Message;
import io.bosonnetwork.photonmessaging.MessageListener;
import io.bosonnetwork.photonmessaging.MessageTimeoutException;
import io.bosonnetwork.photonmessaging.MessagingClient;
import io.bosonnetwork.photonmessaging.SessionInfo;
import io.bosonnetwork.photonmessaging.impl.rpc.GenericRpcResponse;
import io.bosonnetwork.photonmessaging.impl.rpc.RpcCall;
import io.bosonnetwork.photonmessaging.impl.rpc.RpcMethod;
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
	private AsyncLoadingCache<Id, AbstractContact> contactCache;
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

		Promise<Conversation> promise = Promise.promise();
		runOnContext(v -> {
			conversationCache.get(conversationId).whenComplete((c, e) -> {
				if (e != null)
					promise.tryFail(e);
				else
					promise.tryComplete(c);
			});
		});

		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<List<Conversation>> getConversations() {
		runningCheck();

		Promise<List<Conversation>> promise = Promise.promise();

		runOnContext(v -> {
			repository.getAllConversations().map(convs -> {
				// keep the order, overlay the cached conversations
				Map<Id, Conversation> conversations = new LinkedHashMap<>();
				for (Conversation c : convs) {
					ConversationImpl cached = conversationCache.synchronous().asMap()
							.compute(c.getId(), (k, cc) -> cc != null ? cc : (ConversationImpl) c);
					conversations.put(c.getId(), cached);
				}

				// Return a **mutable** list
				return new ArrayList<>(conversations.values());
			}).onComplete(promise);
		});

		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Boolean> removeConversations(Collection<Id> conversationIds) {
		Objects.requireNonNull(conversationIds, "conversationIds");
		if (conversationIds.isEmpty())
			return VertxFuture.succeededFuture(true);

		runningCheck();
		Promise<Boolean> promise = Promise.promise();
		runOnContext(v -> {
			repository.removeConversations(conversationIds).onSuccess(removed -> {
				conversationIds.forEach(id -> conversationCache.synchronous().invalidate(id));
			}).onComplete(promise);
		});

		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<List<Message>> getMessages(Id conversationId, long since, int limit, int offset) {
		Objects.requireNonNull(conversationId, "conversationId");
		if (limit <= 0 || offset < 0)
			throw new IllegalArgumentException("limit and offset must be positive");

		runningCheck();
		return VertxFuture.of(repository.getMessages(conversationId, since, limit, offset));
	}

	@Override
	public VertxFuture<List<Message>> getMessages(Id conversationId, long begin, long end) {
		Objects.requireNonNull(conversationId, "conversationId");
		if (begin > end)
			throw new IllegalArgumentException("begin must be less than or equal to end");

		runningCheck();
		return VertxFuture.of(repository.getMessages(conversationId, begin, end));
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

		RpcCall<Void, List<SessionInfo>> call = new RpcCall<>(RpcMethod.SESSION_LIST);
		call.resultType(new TypeReference<>() {});

		Promise<List<SessionInfo>> promise = Promise.promise();
		runOnContext(v -> {
			sendRpcCall(homePeerId, call).onComplete(promise);
		});
		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Boolean> revokeSession(Id deviceId) {
		Objects.requireNonNull(deviceId, "deviceId");
		runningCheck();

		RpcCall<Id, Boolean> call = new RpcCall<>(RpcMethod.SESSION_REVOKE, deviceId);
		call.resultType(new TypeReference<>() {});

		Promise<Boolean> promise = Promise.promise();
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

		long now = System.currentTimeMillis();
		Notification<String> fr = new Notification<>(Notification.generateId(deviceIdentity.getId(), now),
				Notification.Event.FRIEND_REQUEST, userIdentity.getId(), now, hello);
		FriendRequestImpl request = new FriendRequestImpl(userId, userIdentity.getId(), hello);

		Promise<Void> promise = Promise.promise();
		runOnContext(v -> {
			repository.putFriendRequest(request).compose(added -> {
				MessageImpl<Notification<String>> message = new MessageImpl<>(Message.Type.STATE_MESSAGE, userId, fr);
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
	public VertxFuture<Contact> addFriend(Id id, byte[] sessionKey, String remark) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(sessionKey, "sessionKey");
		Objects.requireNonNull(remark, "remark");

		Friend friend = new Friend(id, sessionKey, remark);
		Promise<Contact> promise = Promise.promise();
		runOnContext(v -> {
			ContactMutation<Contact> mutation = new ContactMutation<>(contactsRevision, ContactMutation.Op.ADD, friend);
			RpcCall<ContactMutation<Contact>, Integer> call = new RpcCall<>(RpcMethod.CONTACT_MUTATE, mutation);
			call.resultType(new TypeReference<>() {});
			sendRpcCall(homePeerId, call).compose(revision -> {
				friend.setRevision(revision);
				return repository.putContacts(revision, List.of(friend)).map(updated -> {
					contactsRevision = revision;
					return friend;
				});
			}).onComplete(promise);
		});
		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Channel> createChannel(Channel.Permission permission, String name, String notice, boolean announce) {
		Objects.requireNonNull(permission, "permission");
		Objects.requireNonNull(name, "name");

		Signature.KeyPair sessionKeypair = Signature.KeyPair.random();
		Id sessionId = Id.of(sessionKeypair.publicKey().bytes());
		byte[] sessionKey = selfContext.encrypt(sessionKeypair.privateKey().bytes());

		RpcPrototypes.CreateChannelParams params = new RpcPrototypes.CreateChannelParams(sessionId, sessionKey,
				permission, name, notice, announce);

		Promise<Channel> promise = Promise.promise();
		runOnContext(v -> {
			RpcCall<RpcPrototypes.CreateChannelParams, Channel> createChannelCall = new RpcCall<>(RpcMethod.CHANNEL_CREATE, params);
			createChannelCall.resultType(new TypeReference<>() {});
			sendRpcCall(homePeerId, createChannelCall).compose(channel ->
					repository.putContactLocally(channel).map(updated -> (ChannelImpl) channel)
			).compose(channel -> {
				ContactMutation<Contact> mutation = new ContactMutation<>(contactsRevision, ContactMutation.Op.ADD, channel);
				RpcCall<ContactMutation<Contact>, Integer> addContactCall = new RpcCall<>(RpcMethod.CONTACT_MUTATE, mutation);
				addContactCall.resultType(new TypeReference<>() {
				});
				return sendRpcCall(homePeerId, addContactCall).compose(revision -> {
					channel.setRevision(revision);
					return repository.putContacts(revision, List.of(channel)).map(updated -> {
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

		Promise<Boolean> promise = Promise.promise();
		runOnContext(v -> {
			Future.fromCompletionStage(contactCache.get(channelId)).compose(contact -> {
				if (contact == null)
					return Future.failedFuture("Channel not exists");

				if (!(contact instanceof ChannelImpl channel))
					return Future.failedFuture("Channel not exists");

				if (!channel.getOwner().equals(getUserId()))
					return Future.failedFuture("Only the owner can delete the channel");

				RpcCall<Void, Boolean> deleteChannelCall = new RpcCall<>(RpcMethod.CHANNEL_DELETE);
				deleteChannelCall.resultType(new TypeReference<>() {});
				return sendRpcCall(channelId, deleteChannelCall).compose(deleted -> {
					ContactMutation<List<Id>> mutation = new ContactMutation<>(contactsRevision, ContactMutation.Op.REMOVE, List.of(channelId));
					RpcCall<ContactMutation<List<Id>>, Integer> removeContactCall = new RpcCall<>(RpcMethod.CONTACT_MUTATE, mutation);
					removeContactCall.resultType(new TypeReference<>() {});
					return sendRpcCall(homePeerId, removeContactCall).compose(revision -> {
						return repository.removeContacts(revision, List.of(channelId)).map(updated -> {
							contactsRevision = revision;
							return true;
						});
					});
				});
			}).onComplete(promise);
		});

		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Channel> joinChannel(InviteTicket ticket) {
		Objects.requireNonNull(ticket, "ticket");
		if (ticket.isValid())
			return VertxFuture.failedFuture("Invalid ticket");
		if (ticket.isExpired())
			return VertxFuture.failedFuture("Ticket expired");
		if (ticket.isNamedTicket() && !ticket.getInvitee().equals(getUserId()))
			return VertxFuture.failedFuture("Only the invitee can join the channel");

		final InviteTicket revisedTicket;
		try {
			byte[] sk = userIdentity.decrypt(ticket.getInviter(), ticket.getSessionKey());
			Signature.KeyPair sessionKeypair = Signature.KeyPair.fromPrivateKey(sk);
			Id sessionId = Id.of(sessionKeypair.publicKey().bytes());
			if (!ticket.getSessionId().equals(sessionId))
				return VertxFuture.failedFuture("Invalid session key");

			byte[] sessionKey = selfContext.encrypt(sessionKeypair.privateKey().bytes());
			revisedTicket = ticket.revise(sessionKey);
		} catch (CryptoException e) {
			return VertxFuture.failedFuture("Invalid session key");
		}

		Promise<Channel> promise = Promise.promise();
		runOnContext(v -> {
			RpcCall<InviteTicket, Channel> joinChannelCall = new RpcCall<>(RpcMethod.CHANNEL_JOIN, revisedTicket);
			joinChannelCall.resultType(new TypeReference<>() {});
			sendRpcCall(ticket.getChannelId(), joinChannelCall).compose(ch -> {
				ChannelImpl channel = (ChannelImpl) ch;
				channel.setSessionKey(ticket.getSessionKey());
				return repository.putContactLocally(channel).map(updated -> channel);
			}).compose(channel -> {
				ContactMutation<Contact> mutation = new ContactMutation<>(contactsRevision, ContactMutation.Op.ADD, channel);
				RpcCall<ContactMutation<Contact>, Integer> addContactCall = new RpcCall<>(RpcMethod.CONTACT_MUTATE, mutation);
				addContactCall.resultType(new TypeReference<>() {
				});
				return sendRpcCall(homePeerId, addContactCall).compose(revision -> {
					channel.setRevision(revision);
					return repository.putContacts(revision, List.of(channel)).map(updated -> {
						contactsRevision = revision;
						return channel;
					});
				});
			}).onComplete(promise);
		});

		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Boolean> leaveChannel(Id channelId) {
		Promise<Boolean> promise = Promise.promise();
		runOnContext(v -> {
			Future.fromCompletionStage(contactCache.get(channelId)).compose(contact -> {
				if (contact == null)
					return Future.failedFuture("Channel not exists");

				if (!(contact instanceof ChannelImpl channel))
					return Future.failedFuture("Channel not exists");

				RpcCall<Void, Boolean> leaveChannelCall = new RpcCall<>(RpcMethod.CHANNEL_DELETE);
				leaveChannelCall.resultType(new TypeReference<>() {});
				return sendRpcCall(channelId, leaveChannelCall).compose(deleted -> {
					ContactMutation<List<Id>> mutation = new ContactMutation<>(contactsRevision, ContactMutation.Op.REMOVE, List.of(channelId));
					RpcCall<ContactMutation<List<Id>>, Integer> removeContactCall = new RpcCall<>(RpcMethod.CONTACT_MUTATE, mutation);
					removeContactCall.resultType(new TypeReference<>() {});
					return sendRpcCall(homePeerId, removeContactCall).compose(revision -> {
						return repository.removeContacts(revision, List.of(channelId)).map(updated -> {
							contactsRevision = revision;
							return true;
						});
					});
				});
			}).onComplete(promise);
		});

		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<InviteTicket> createInviteTicket(Id channelId, Id invitee) {
		Objects.requireNonNull(channelId, "channelId");

		Promise<InviteTicket> promise = Promise.promise();
		runOnContext(v -> {
			Future.fromCompletionStage(contactCache.get(channelId)).compose(contact -> {
				if (contact == null)
					return Future.failedFuture("Channel not exists");

				if (!(contact instanceof ChannelImpl channel))
					return Future.failedFuture("Channel not exists");

				ChannelMember member = channel.getMember(getUserId());
				Channel.Permission permission = channel.getPermission();
				switch (permission) {
					case PUBLIC, MEMBER_INVITE -> {
						if (member.isBanned())
							return Future.failedFuture("You are banned from the channel");
					}

					case MODERATOR_INVITE -> {
						if (!member.isModerator() && !member.isOwner())
							return Future.failedFuture("You are not a moderator of the channel");
					}

					case OWNER_INVITE -> {
						if (!member.isOwner())
							return Future.failedFuture("You are not the owner of the channel");
					}
				}

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
			}).onComplete(promise);
		});

		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Boolean> transferChannelOwnership(Id channelId, Id newOwner) {
		Objects.requireNonNull(channelId, "channelId");
		Objects.requireNonNull(newOwner, "newOwner");

		Promise<Boolean> promise = Promise.promise();
		runOnContext(v -> {
			Future.fromCompletionStage(contactCache.get(channelId)).compose(contact -> {
				if (contact == null)
					return Future.failedFuture("Channel not exists");

				if (!(contact instanceof ChannelImpl channel))
					return Future.failedFuture("Channel not exists");

				if (!channel.getOwner().equals(getUserId()))
					return Future.failedFuture("Only the owner can transfer the channel ownership");

				ChannelMember member = channel.getMember(newOwner);
				if (member == null)
					return Future.failedFuture("New owner is not a member of the channel");
				if (member.isBanned())
					return Future.failedFuture("New owner is banned from the channel");

				RpcCall<Id, Boolean> call = new RpcCall<>(RpcMethod.CHANNEL_TRANSFER_OWNERSHIP, newOwner);
				return sendRpcCall(channelId, call).compose(transferred -> {
					if (transferred) {
						channel.setOwner(newOwner);
						return repository.putContactLocally(channel).map(updated -> true);
					} else {
						return Future.succeededFuture(false);
					}
				});
			}).onComplete(promise);
		});

		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Boolean> rotateChannelSessionKey(Id channelId, Signature.KeyPair sessionKeypair) {
		Objects.requireNonNull(channelId, "channelId");
		final Signature.KeyPair keypair = sessionKeypair != null ? sessionKeypair : Signature.KeyPair.random();

		Promise<Boolean> promise = Promise.promise();
		runOnContext(v -> {
			Future.fromCompletionStage(contactCache.get(channelId)).compose(contact -> {
				if (contact == null)
					return Future.failedFuture("Channel not exists");

				if (!(contact instanceof ChannelImpl channel))
					return Future.failedFuture("Channel not exists");

				if (!channel.getOwner().equals(getUserId()))
					return Future.failedFuture("Only the owner can transfer the channel ownership");

				final byte[] sessionKey;
				try {
					sessionKey = userIdentity.encrypt(channel.getSessionId(), keypair.privateKey().bytes());
				} catch (CryptoException e) {
					return Future.failedFuture("Failed to encrypt session key");
				}

				Id sessionId = Id.of(keypair.publicKey().bytes());
				RpcPrototypes.ChannelSessionKeyRotationParams params =
						new RpcPrototypes.ChannelSessionKeyRotationParams(sessionId, sessionKey);

				RpcCall<RpcPrototypes.ChannelSessionKeyRotationParams, Boolean> call =
						new RpcCall<>(RpcMethod.CHANNEL_ROTATE_SESSION_KEY, params);
				return sendRpcCall(channelId, call).compose(rotated -> {
					if (rotated) {
						channel.setSessionKey(sessionKey);
						return repository.putContactLocally(channel).map(updated -> true);
					} else {
						return Future.succeededFuture(false);
					}
				});
			}).onComplete(promise);
		});

		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Boolean> updateChannelInfo(Channel channel) {
		// TODO: how to update channel info?
		return null;
	}

	@Override
	public VertxFuture<Boolean> setChannelMembersRole(Id channelId, List<Id> members, Channel.Role role) {
		Objects.requireNonNull(channelId, "channelId");
		Objects.requireNonNull(members, "members");
		Objects.requireNonNull(role, "role");
		if (members.isEmpty())
			throw new IllegalArgumentException("No members specified");
		if (role == Channel.Role.OWNER || role == Channel.Role.BANNED)
			throw new IllegalArgumentException("Role " + role + " is not allowed");

		Promise<Boolean> promise = Promise.promise();
		runOnContext(v -> {
			Future.fromCompletionStage(contactCache.get(channelId)).compose(contact -> {
				if (contact == null)
					return Future.failedFuture("Channel not exists");

				if (!(contact instanceof ChannelImpl channel))
					return Future.failedFuture("Channel not exists");

				ChannelMember member = channel.getMember(getUserId());
				if (member == null)
					return Future.failedFuture("You are not a member of the channel");
				if (!member.isOwner() && !member.isModerator())
					return Future.failedFuture("You are not an owner or a moderator of the channel");

				RpcPrototypes.ChannelMembersRoleParams params =
						new RpcPrototypes.ChannelMembersRoleParams(members, role);

				RpcCall<RpcPrototypes.ChannelMembersRoleParams, Boolean> call =
						new RpcCall<>(RpcMethod.CHANNEL_UPDATE_MEMBERS_ROLE, params);
				return sendRpcCall(channelId, call).compose(changed -> {
					if (changed) {
						channel.invalidateMembers();
						return repository.updateChannelMembersRole(channelId, members, role).map(updated -> true);
					} else {
						return Future.succeededFuture(false);
					}
				});
			}).onComplete(promise);
		});

		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Boolean> banChannelMembers(Id channelId, List<Id> members) {
		Objects.requireNonNull(channelId, "channelId");
		Objects.requireNonNull(members, "members");
		if (members.isEmpty())
			throw new IllegalArgumentException("No members specified");

		Promise<Boolean> promise = Promise.promise();
		runOnContext(v -> {
			Future.fromCompletionStage(contactCache.get(channelId)).compose(contact -> {
				if (contact == null)
					return Future.failedFuture("Channel not exists");

				if (!(contact instanceof ChannelImpl channel))
					return Future.failedFuture("Channel not exists");

				ChannelMember member = channel.getMember(getUserId());
				if (member == null)
					return Future.failedFuture("You are not a member of the channel");
				if (!member.isOwner() && !member.isModerator())
					return Future.failedFuture("You are not an owner or a moderator of the channel");

				RpcCall<List<Id>, Boolean> call = new RpcCall<>(RpcMethod.CHANNEL_BAN_MEMBERS, members);
				return sendRpcCall(channelId, call).compose(banned -> {
					if (banned) {
						channel.invalidateMembers();
						return repository.updateChannelMembersRole(channelId, members, Channel.Role.BANNED).map(updated -> true);
					} else {
						return Future.succeededFuture(false);
					}
				});
			}).onComplete(promise);
		});

		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Boolean> unbanChannelMembers(Id channelId, List<Id> members) {
		Objects.requireNonNull(channelId, "channelId");
		Objects.requireNonNull(members, "members");
		if (members.isEmpty())
			throw new IllegalArgumentException("No members specified");

		Promise<Boolean> promise = Promise.promise();
		runOnContext(v -> {
			Future.fromCompletionStage(contactCache.get(channelId)).compose(contact -> {
				if (contact == null)
					return Future.failedFuture("Channel not exists");

				if (!(contact instanceof ChannelImpl channel))
					return Future.failedFuture("Channel not exists");

				ChannelMember member = channel.getMember(getUserId());
				if (member == null)
					return Future.failedFuture("You are not a member of the channel");
				if (!member.isOwner() && !member.isModerator())
					return Future.failedFuture("You are not an owner or a moderator of the channel");

				RpcCall<List<Id>, Boolean> call = new RpcCall<>(RpcMethod.CHANNEL_UNBAN_MEMBERS, members);
				return sendRpcCall(channelId, call).compose(unbanned -> {
					if (unbanned) {
						channel.invalidateMembers();
						return repository.updateChannelMembersRole(channelId, members, Channel.Role.MEMBER).map(updated -> true);
					} else {
						return Future.succeededFuture(false);
					}
				});
			}).onComplete(promise);
		});

		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Boolean> removeChannelMembers(Id channelId, List<Id> members) {
		Objects.requireNonNull(channelId, "channelId");
		Objects.requireNonNull(members, "members");
		if (members.isEmpty())
			throw new IllegalArgumentException("No members specified");

		Promise<Boolean> promise = Promise.promise();
		runOnContext(v -> {
			Future.fromCompletionStage(contactCache.get(channelId)).compose(contact -> {
				if (contact == null)
					return Future.failedFuture("Channel not exists");

				if (!(contact instanceof ChannelImpl channel))
					return Future.failedFuture("Channel not exists");

				ChannelMember member = channel.getMember(getUserId());
				if (member == null)
					return Future.failedFuture("You are not a member of the channel");
				if (!member.isOwner() && !member.isModerator())
					return Future.failedFuture("You are not an owner or a moderator of the channel");

				RpcCall<List<Id>, Boolean> call = new RpcCall<>(RpcMethod.CHANNEL_REMOVE_MEMBERS, members);
				return sendRpcCall(channelId, call).compose(removed -> {
					if (removed) {
						channel.invalidateMembers();
						return repository.removeChannelMembers(channelId, members).map(updated -> true);
					} else {
						return Future.succeededFuture(false);
					}
				});
			}).onComplete(promise);
		});

		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Contact> getContact(Id contactId) {
		Objects.requireNonNull(contactId, "contactId");

		Promise<Contact> promise = Promise.promise();
		runOnContext(v -> {
			contactCache.get(contactId).whenComplete((contact, error) -> {
				if (error != null)
					promise.fail(error);
				else
					promise.complete(contact);
			});
		});
		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<List<Contact>> getContacts() {
		Promise<List<Contact>> promise = Promise.promise();
		runOnContext(v -> {
			repository.getAllContacts().map(contacts -> {
				Map<Id, Contact> result = new LinkedHashMap<>();
				for (Contact c : contacts) {
					Contact cached = contactCache.synchronous().asMap()
							.compute(c.getId(), (k, cc) -> cc != null ? cc : (AbstractContact) c);
					result.put(c.getId(), cached);
				}

				// Return a **mutable** list
				return new ArrayList<>(result.values());
			}).onComplete(promise);
		});
		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Boolean> updateContact(Contact contact) {
		// TODO: how to update contact info?
		return null;
	}

	@Override
	public VertxFuture<Boolean> removeContacts(List<Id> contactIds) {
		Objects.requireNonNull(contactIds, "contactIds");
		if (contactIds.isEmpty())
			throw new IllegalArgumentException("No contacts specified");

		Promise<Boolean> promise = Promise.promise();
		runOnContext(v -> {
			ContactMutation<List<Id>> mutation = new ContactMutation<>(contactsRevision, ContactMutation.Op.REMOVE, contactIds);
			RpcCall<ContactMutation<List<Id>>, Integer> call = new RpcCall<>(RpcMethod.CONTACT_MUTATE, mutation);
			call.resultType(new TypeReference<>() {});
			sendRpcCall(homePeerId, call).compose(revision -> {
				return repository.removeContacts(revision, contactIds).map(updated -> {
					contactsRevision = revision;
					return updated;
				});
			}).onComplete(promise);
		});
		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Boolean> clearContacts() {
		Promise<Boolean> promise = Promise.promise();
		runOnContext(v -> {
			ContactMutation<Void> mutation = new ContactMutation<>(contactsRevision, ContactMutation.Op.CLEAR, null);
			RpcCall<ContactMutation<Void>, Integer> call = new RpcCall<>(RpcMethod.CONTACT_MUTATE, mutation);
			call.resultType(new TypeReference<>() {});
			sendRpcCall(homePeerId, call).compose(revision -> {
				return repository.clearContacts(revision).map(updated -> {
					contactsRevision = revision;
					return updated;
				});
			}).onComplete(promise);
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
						VertxFuture.of(repository.getContact(id).map(c -> (AbstractContact) c)));

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

	private <P, R> Future<R> sendRpcCall(Id recipient, RpcCall<P, R> call) {
		inflightRpcCalls.put(call.getId(), call);
		MessageImpl<RpcRequest<P>> message = new MessageImpl<>(Message.Type.CONTROL_MESSAGE, recipient, call.getRequest());
		log.debug("Sending RPC call {}:{} to {} ...", call.getId(), call.getMethod(), recipient);
		return sendMessageInternal(message).compose(packageId -> call.getFuture(), error -> {
			inflightRpcCalls.remove(call.getId());
			log.error("Send RPC call {} failed", call.getId(), error);
			return Future.failedFuture(error);
		});
	}

	protected Future<Message> sendMessage(Message message) {
		MessageImpl<?> msg = (MessageImpl<?>)message;

		Promise<Void> promise = Promise.promise();
		vertxContext.runOnContext((v) -> {
			sendMessageInternal(msg).compose(packetId -> msg.getFuture().onComplete(promise));
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

	private Future<AbstractContact> contact(Id id) {
		// TODO:
		return Future.succeededFuture();
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
		inflightMessages.remove(message.getSerialNumber());
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
			else if (tt == Topics.Type.DEVICE_INBOX)
				processControlMessage(message);
			else
				log.error("Received a {} message from unknown topic: {}, ignored", mt, topic);
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
			if (message.getFrom().equals(getUserId())) {
				// notification to the user: e.g.; friend request
				DefaultContent<JsonNode> content = DefaultContent.parse(message.getPayload());
				MessageImpl<Message.Content> contentMessage = message.dup(content);
				if (messageListener != null)
					messageListener.onNotification(contentMessage);

				return;
			}

			Notification.GenericNotification gn = Notification.parse(message.getPayload());
			if (gn.getSource().equals(userIdentity.getId()) && gn.isAssociated(deviceIdentity.getId())) {
				// This notification was triggered by an RPC request from this device.
				// Since the local state was already updated via the RPC response,
				// this redundant notification can be safely ignored.
				return;
			}

			if (message.getFrom().equals(homePeerId)) {
				// notification from home peer
				processHomePeerNotification(gn);
				return;
			}

			// channel notification
			contact(message.getRecipient()).compose(contact -> {
				if (contact == null) {
					log.warn("Received a {} from unknown channel {}, ignored", type, message.getRecipient());
					return Future.succeededFuture();
				}

				if (contact instanceof ChannelImpl channel) {
					return processChannelNotification(channel, gn);
				} else {
					log.warn("Received an invalid {} from {}, ignored", type, message.getRecipient());
					return Future.succeededFuture();
				}
			});
		}

		log.warn("Received a {} message from user inbox, ignored", type);
	}

	private void processingOutgoingMessage(BytesMessage message) {

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

	private Future<Void> processHomePeerNotification(Notification.GenericNotification gn) {
		// notification from home peer
		return switch (gn.getEvent()) {
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
				yield repository.putContactLocally(channel).mapEmpty();
			}

			default -> {
				log.warn("Received an unknown notification from home peer, ignored");
				yield Future.succeededFuture();
			}
		};
	}

	private Future<Void> processChannelNotification(ChannelImpl channel, Notification.GenericNotification gn) {
		return switch (gn.getEvent()) {
			case CHANNEL_DELETE -> repository.removeContactLocally(channel.getId()).map(deleted -> {
					if (deleted && channelListener != null)
						channelListener.onChannelDeleted(channel);
					return null;
				});

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
				yield repository.putContactLocally(channel).map(added -> {
					if (added && channelListener != null)
						channelListener.onJoinedChannel(ch);
					return null;
				});
			}

			case CHANNEL_LEAVE -> {
				yield repository.removeContactLocally(channel.getId()).map(removed -> {
					if (removed && channelListener != null)
						channelListener.onLeftChannel(channel);
					return null;
				});
			}

			case CHANNEL_TRANSFER_OWNERSHIP -> {
				Id newOwnerId = gn.getContentAs(Id.class);
				Id oldOwnerId = channel.getOwner();
				ChannelMember oldOwner = channel.getMember(channel.getOwner());
				ChannelMember newOwner = channel.getMember(newOwnerId);
				if (oldOwner == null || newOwner == null || !oldOwner.isOwner()) {
					// not up-to-date?
					yield refeshChannel(channel.getId()).mapEmpty();
				} else {
					yield repository.updateChannelOwnership(channel.getId(), channel.getOwner(), newOwnerId).map(updated -> {
						if (updated) {
							channel.setOwner(newOwnerId);
							channel.invalidateMembers();
							if (channelListener != null)
								channelListener.onChannelOwnershipTransferred(channel, oldOwnerId, newOwnerId);
						}

						return null;
					});
				}
			}

			case CHANNEL_ROTATE_SESSION_KEY -> {
				ChannelImpl updatedChannel = channel.dup();
				updatedChannel.setSessionKey(gn.getContentAs(byte[].class));
				yield repository.putContactLocally(updatedChannel).map(updated -> {
					if (updated) {
						channel.setSessionKey(updatedChannel.getSessionKey());
						if (channelListener != null)
							channelListener.onChannelSessionKeyRotated(channel);
					}
					return null;
				});
			}

			case CHANNEL_UPDATE_INFO -> {
				ChannelImpl updatedChannel = channel.dup();
				updatedChannel.patch(gn.getContent());
				yield repository.putContactLocally(updatedChannel).map(updated -> {
					if (updated) {
						channel.patch(gn.getContent());
						if (channelListener != null)
							channelListener.onChannelUpdated(channel);
					}
					return null;
				});
			}

			case CHANNEL_UPDATE_MEMBERS_ROLE -> {
				RpcPrototypes.ChannelMembersRoleParams content = gn.getContentAs(RpcPrototypes.ChannelMembersRoleParams.class);
				yield repository.updateChannelMembersRole(channel.getId(), content.memberIds(), content.role()).map(updated -> {
					if (updated) {
						channel.invalidateMembers();
						if (channelListener != null) {
							repository.getChannelMembers(channel.getId(), content.memberIds()).onSuccess(members -> {
								if (channelListener != null)
									channelListener.onChannelMembersRoleChanged(channel, members, content.role());
							});
						}
					}
					return null;
				});
			}

			case CHANNEL_BAN_MEMBERS -> {
				List<Id> memberIds = gn.getContentAsListOf(Id.class);
				yield repository.updateChannelMembersRole(channel.getId(), memberIds, Channel.Role.BANNED).map(updated -> {
					if (updated) {
						channel.invalidateMembers();
						if (channelListener != null) {
							repository.getChannelMembers(channel.getId(), memberIds).onSuccess(members -> {
								if (channelListener != null)
									channelListener.onChannelMembersBanned(channel, members);
							});
						}
					}
					return null;
				});
			}

			case CHANNEL_UNBAN_MEMBERS -> {
				List<Id> memberIds = gn.getContentAsListOf(Id.class);
				yield repository.updateChannelMembersRole(channel.getId(), memberIds, Channel.Role.MEMBER).map(updated -> {
					if (updated) {
						channel.invalidateMembers();
						if (channelListener != null) {
							repository.getChannelMembers(channel.getId(), memberIds).onSuccess(members -> {
								if (channelListener != null)
									channelListener.onChannelMembersUnbanned(channel, members);
							});
						}
					}
					return null;
				});
			}

			case CHANNEL_REMOVE_MEMBERS -> {
				List<Id> memberIds = gn.getContentAsListOf(Id.class);
				yield repository.getChannelMembers(channel.getId(), memberIds).compose(members -> {
					if (members.isEmpty())
						return Future.succeededFuture();

					return repository.removeChannelMembers(channel.getId(), memberIds).map(removed -> {
						if (removed) {
							channel.invalidateMembers();
							if (channelListener != null)
								channelListener.onChannelMembersRemoved(channel, members);
						}
						return null;
					});
				});
			}

			case CHANNEL_MEMBER_JOIN -> {
				ChannelMember member = gn.getContentAs(ChannelMember.class);
				yield repository.putChannelMember(channel.getId(), member).map(added -> {
					if (added) {
						channel.invalidateMembers();
						if (channelListener != null)
							channelListener.onChannelMemberJoined(channel, member);
					}

					return null;
				});
			}

			case CHANNEL_MEMBER_LEAVE -> {
				Id memberId = gn.getContentAs(Id.class);
				yield repository.getChannelMember(channel.getId(), memberId).compose(member -> {
					if (member == null)
						return Future.succeededFuture();

					return repository.removeChannelMember(channel.getId(), memberId).map(removed -> {
						if (removed) {
							channel.invalidateMembers();
							if (channelListener != null)
								channelListener.onChannelMemberLeft(channel, member);
						}

						return null;
					});
				});
			}

			default -> {
				log.warn("Received an unknown notification from channel {}, ignored", channel.getId());
				yield Future.succeededFuture();
			}
		};
	}

	private Future<Void> applyContactMutation(ContactMutation.GenericContactMutation mutation) {
		final int revision = mutation.getRevision();
		return switch (mutation.getOp()) {
			case ADD -> {
				Contact contact = mutation.getDataAs(AbstractContact.class);
				yield repository.putContacts(revision, List.of(contact)).map(added -> {
					if (added && contactListener != null)
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

					AbstractContact updatedContact = contact.dup();
					updatedContact.patch(changes);
					return repository.putContacts(revision, List.of(updatedContact)).map(updated -> {
						contact.patch(changes);
						if(updated && contactListener != null)
							contactListener.onContactsUpdated(List.of(contact));
						return null;
					});
				}).mapEmpty();
			}

			case REMOVE -> {
				List<Id> contactIds = mutation.getDataAsListOf(Id.class);
				yield repository.removeContacts(revision, contactIds).map(removed -> {
					if (removed && contactListener != null)
						contactListener.onContactRemoved(contactIds);
					return null;
				});
			}

			case CLEAR -> repository.clearContacts(revision).map(cleared -> {
				if (cleared && contactListener != null)
						contactListener.onContactsCleared();
				return null;
			});
		};
	}

	private Future<Channel> refeshChannel(Id channelId) {
		return Future.succeededFuture();
	}
}