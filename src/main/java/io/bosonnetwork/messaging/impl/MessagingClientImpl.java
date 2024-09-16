package io.bosonnetwork.messaging.impl;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.LoadingCache;

import io.bosonnetwork.CryptoContext;
import io.bosonnetwork.Id;
import io.bosonnetwork.Identity;
import io.bosonnetwork.crypto.CryptoBox.Nonce;
import io.bosonnetwork.crypto.CryptoException;
import io.bosonnetwork.crypto.CryptoIdentity;
import io.bosonnetwork.crypto.Hash;
import io.bosonnetwork.crypto.Random;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.messaging.Channel;
import io.bosonnetwork.messaging.ClientDevice;
import io.bosonnetwork.messaging.Contact;
import io.bosonnetwork.messaging.ForbiddenException;
import io.bosonnetwork.messaging.InviteTicket;
import io.bosonnetwork.messaging.Message;
import io.bosonnetwork.messaging.Message.Builder;
import io.bosonnetwork.messaging.MessagingClient;
import io.bosonnetwork.messaging.MessagingPeerInfo;
import io.bosonnetwork.messaging.RepositoryException;
import io.bosonnetwork.messaging.TimeoutException;
import io.bosonnetwork.messaging.UserAgent;
import io.bosonnetwork.messaging.impl.APIClient.MessagingServiceInfo;
import io.bosonnetwork.messaging.rpc.RPCMethod;
import io.bosonnetwork.messaging.rpc.RPCParameters;
import io.bosonnetwork.messaging.rpc.RPCParameters.ChannelCreate;
import io.bosonnetwork.messaging.rpc.RPCParameters.ChannelMemberRole;
import io.bosonnetwork.messaging.rpc.RPCParameters.ContactPut;
import io.bosonnetwork.messaging.rpc.RPCParameters.ContactRemove;
import io.bosonnetwork.messaging.rpc.RPCParameters.UserProfile;
import io.bosonnetwork.messaging.rpc.RPCRequest;
import io.bosonnetwork.messaging.rpc.RPCResponse;
import io.bosonnetwork.utils.Base58;
import io.bosonnetwork.utils.vertx.VertxBackedCaffeine;
import io.bosonnetwork.utils.vertx.VertxFuture;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubAckMessage;

public class MessagingClientImpl extends AbstractVerticle implements MessagingClient {
	private static final int CALL_TIMEOUT = 60_000; // 60 seconds
	private static final String broadcast = "broadcast";

	private final UserAgent userAgent;

	private final MessagingPeerInfo peer;
	private final CryptoIdentity user;
	private final Identity device;
	private final String clientId;

	private final String inbox;
	private final String outbox;

	private final AtomicLong nextMessageIndex;
	private final AtomicLong nextRpcCallId;

	private MessagingServiceInfo serviceInfo;

	private CryptoContext serverContext;

	private int failures;
	private boolean disconnect;

	private APIClient apiClient;

	private MqttClientOptions mqttClientOptions;
	private URI mqttURI;
	private MqttClient mqttClient;
	private Promise<Void> connectPromise;

	private Map<Integer, MessageImpl> pendingMessages;
	private Map<Long, RPCRequest<?, ?>> pendingCalls;
	private LoadingCache<IdPath, CryptoContext> cryptoContexts;

	private static final Logger log = LoggerFactory.getLogger(MessagingClientImpl.class);

	public MessagingClientImpl(UserAgent userAgent) {
		Objects.requireNonNull(userAgent);
		if (!userAgent.isConfigured())
			throw new IllegalStateException("UserAgent is not configured");

		((DefaultUserAgent)userAgent).harden();

		this.peer = userAgent.getMessagingPeerInfo();
		this.user = ((UserProfileImpl)userAgent.getUser()).getIdentity();
		this.device = ((DeviceProfileImpl)userAgent.getDevice()).getIdentity();
		this.userAgent = userAgent;

		this.clientId = getClientId(device.getId());

		inbox = "inbox/" + user.getId().toBase58String();
		outbox = "outbox/" + user.getId().toBase58String();

		pendingMessages = new HashMap<>();
		pendingCalls = new HashMap<>();

		long idx = Random.secureRandom().nextInt() & 0x7FFFFFFFFFFFFFFFL;
		nextMessageIndex = new AtomicLong(idx);
		idx = Random.secureRandom().nextInt() & 0x7FFFFFFFFFFFFFFFL;
		nextRpcCallId = new AtomicLong(idx);
	}

	@Override
	public void start(Promise<Void> startPromise) {
		apiClient = new APIClient(vertx, peer.getPeerId(), peer.getApiURL());
		apiClient.setUserIdentity(user);
		apiClient.setDeviceIdentity(device);
		apiClient.setAccessToken(((DefaultUserAgent)userAgent).getAccessToken());
		apiClient.setAccessTokenRefreshHandler((accessToken) -> {
			((DefaultUserAgent)userAgent).setAccessToken(accessToken);
		});

		apiClient.getServiceInfo().map(info -> {
			serviceInfo = info;

			serverContext = user.createCryptoContext(peer.getPeerId());

			cryptoContexts = VertxBackedCaffeine.newBuilder(vertx)
					.expireAfterAccess(10, TimeUnit.MINUTES)
					.removalListener((k, v, r) -> { if (v != null) ((CryptoContext)v).close(); })
					.build((path) -> {
						if (isMe(path.from())) {
							// message sent from self to an user or a group
							Channel identity = userAgent.getChannel(path.to());
							Id recipient = identity == null ? path.to() : identity.getMemberPublicKey();
							return user.createCryptoContext(recipient);
						} else {
							// message sent to me(self or group message)
							Identity identity = isMe(path.to()) ? user : userAgent.getChannel(path.to());
							return identity == null ? null : identity.createCryptoContext(path.from());
						}
					});

			// MqttClientOptions acts like a static data bean/POJO and does not support dynamic options.
			// Additionally, the MqttClient creates a copy of the options when the instance is created.
			// Therefore, all options are set statically here.
			mqttClientOptions = new MqttClientOptions()
					.setAutoGeneratedClientId(false)
					.setClientId(clientId)
					.setUsername(user.getId().toBase58String())
					.setPassword(getPassword(user, device))
					.setMaxMessageSize(16 * 1024)
					.setReceiveBufferSize(18 * 1024)
					.setKeepAliveInterval(60)
					.setHostnameVerificationAlgorithm("")
					.setCleanSession(false);

			startPromise.complete();
			return null;
		});
	}

	@Override
	public void stop(Promise<Void> stopPromise) throws Exception {
		doDisconnect().onComplete((ar) -> {
			serverContext.close();
			serverContext = null;
			mqttClientOptions = null;
			mqttClient = null;
			cryptoContexts.invalidateAll();
			stopPromise.complete();
		});
	}

	@Override
	public UserAgent getUserAgent() {
		return userAgent;
	}

	@Override
	public Id getUserId() {
		return user.getId();
	}

	private static String getClientId(Id deviceId) {
		return Base58.encode(Hash.md5().digest(deviceId.bytes()));
	}

	private static String getPassword(Identity user, Identity device) {
		byte[] nonce = Nonce.random().bytes();
		byte[] userSig = user.sign(nonce);
		byte[] deviceSig = device.sign(nonce);

		byte[] password = new byte[nonce.length + userSig.length + deviceSig.length];
		System.arraycopy(nonce, 0, password, 0, nonce.length);
		System.arraycopy(userSig, 0, password, nonce.length, userSig.length);
		System.arraycopy(deviceSig, 0, password, nonce.length + userSig.length, deviceSig.length);
		return Base58.encode(password);
	}

	private Future<Void> attemptConnect(List<URI> uris, int index) {
		if (disconnect)
			Future.failedFuture("Stopped");

		if (index >= uris.size())
            return Future.failedFuture("No more candidate URIs to attempt");

		Promise<Void> promise = Promise.promise();

		URI uri = uris.get(index);

		log.info("Trying to connect to the messaging server {} @ {}", peer.getPeerId(), uri);

		if (uri.getScheme().toLowerCase().equals("ssl")) {
			mqttClientOptions.setSsl(true);
			String sslCert = serviceInfo.getSslCert();
			if (sslCert != null) {
				mqttClientOptions.setTrustOptions(new PemTrustOptions().addCertValue(Buffer.buffer(sslCert)));
				mqttClientOptions.setTrustAll(false);
			} else {
				mqttClientOptions.setTrustAll(true);
			}
		} else {
			mqttClientOptions.setSsl(false);
		}

		MqttClient client = MqttClient.create(vertx, mqttClientOptions);

		client.closeHandler((v) -> onClose())
			.subscribeCompletionHandler(this::onSubscribeCompletion)
			.publishHandler(this::onMqttMessage)
			.publishCompletionHandler(this::onPublishCompletion)
			.publishCompletionExpirationHandler(this::onPublishCompletionExpiration)
			.publishCompletionUnknownPacketIdHandler(this::onPublishCompletionUnknownPacketId)
			.unsubscribeCompletionHandler(this::onUnsubscribeCompletion)
			.exceptionHandler((e) -> log.error("Messaging client error", e));

		client.connect(uri.getPort(), uri.getHost())
			.onSuccess((ack) -> {
				log.info("Connected to the messaging server {} @ {}", peer.getPeerId(), uri);
				this.mqttURI = uri;
				this.mqttClient = client;
				promise.complete(null);
			}).onFailure((e) -> {
				log.warn("Failed to connect to the messaging server {} @ {}", peer.getPeerId(), uri);
				attemptConnect(uris, index + 1).onComplete(promise);
			});

		return promise.future();
	}

	private MqttClient getMqttClient() {
		return mqttClient;
	}

	protected long getNextMessageIndex() {
		return nextMessageIndex.getAndIncrement();
	}


	protected long getNextRpcCallId() {
		return nextRpcCallId.getAndIncrement();
	}

	private boolean isMe(Id id) {
		return user.getId().equals(id);
	}

	@Override
	public boolean isConnected() {
		return mqttClient != null && mqttClient.isConnected();
	}

	private void onClose() {
		if (disconnect) {
			onDisonnected();
			log.info("Disconnected");
			return;
		} else {
			this.failures++;
			int delay = getRetryInterval();
			log.warn("Connection lost, will try to reconnect in {} seconds", delay);
			vertx.setTimer(TimeUnit.SECONDS.toMillis(delay), (tid) -> reconnect());
		}
	}

	private void onSubscribeCompletion(MqttSubAckMessage m) {
		List<Integer> grantedQoSLevels = m.grantedQoSLevels();
		log.debug("Subscription complete:\n\t{} - {}\n\t{} - {}\n\t{} - {}\n\t{} - {}",
				inbox, grantedQoSLevels.get(0),
				outbox, grantedQoSLevels.get(1),
				broadcast, grantedQoSLevels.get(2));

		if (connectPromise != null) {
			log.info("Subscribe topics success");
			onConnected();
			connectPromise.complete(null);
		}
	}

	private void onUnsubscribeCompletion(int packetId) {
		// TODO:
	}

	private void onPublishCompletion(int packetId) {
		MessageImpl message = pendingMessages.remove(packetId);
		if (message == null) {
			log.error("INTERNAL ERROR: no message associated with packet {}", packetId);
			return;
		}

		message.sent();
	}

	private void onPublishCompletionExpiration(int packetId) {
		MessageImpl message = pendingMessages.remove(packetId);
		if (message == null) {
			log.error("INTERNAL ERROR: no message associated with packet {}", packetId);
			return;
		}

		message.failed(new TimeoutException("Get message ACK timeout"));
	}

	private void onPublishCompletionUnknownPacketId(int packetId) {
		log.warn("INTERNAL WARN: unknown packet {}", packetId);
		// check is existing message associated with the unknown packet id
		MessageImpl message = pendingMessages.remove(packetId);
		if (message != null)
			message.sent();
	}

	private void onMqttMessage(MqttPublishMessage mm) {
		String topic = mm.topicName();
		log.debug("{} got message at topic: {}", user.getId(), topic);

		try {
			// decrypt the message envelope
			byte[] payload = serverContext.decrypt(mm.payload().getBytes());
			MessageImpl message = MessageImpl.parse(payload);
			if (!message.getFrom().equals(peer.getPeerId())) {
				// message not sent from the server peer
				// decrypt the message body if exists
				byte[] body = message.getBody();
				if (body != null && body.length != 0) {
					CryptoContext context = cryptoContexts.get(IdPath.of(message.getFrom(), message.getTo()));
					if (context == null) {
						// Got an unknown group message, drop it due to we cannot decrypt the content
						log.warn("Unknown group message {}->{}", message.getFrom(), message.getTo());
						return;
					}

					body = context.decrypt(body);
					message = message.dup(body);
				}
			}

			if (topic.equals(inbox))
				processIncomingMessage(topic, message);
			else if (topic.equals(outbox))
				processOutgoingMessage(topic, message);
			else if (topic.equals(broadcast))
				onBroadcase(message);
		} catch (Exception e) {
			log.error("Failed to process the MQTT message", e);
		}
	}

	private void processIncomingMessage(String topic, Message message) {
		int type = message.getMessageType();
		if (type == Message.Types.MESSAGE)
			onMessage(message);
		else if (type == Message.Types.CALL)
			processRpcResponse(message);
		else
			log.warn("Unknown message type {} from {}, ignore",
					type, message.getFrom());
	}

	private void processOutgoingMessage(String topic, Message message) {
		int type = message.getMessageType();
		if (type ==  Message.Types.MESSAGE)
			onSent(message);
		else if (type == Message.Types.CALL)
			processRpcRequest(message);
		else
			log.warn("Unknown message type {}, ignore", type);
	}

	private void onConnecting() {
		userAgent.onConnecting();
	}

	private void onConnected() {
		userAgent.onConnected();
	}

	private void onDisonnected() {
		userAgent.onDisconnected();
	}

	private void onMessage(Message message) {
		boolean isChannelMessage = !isMe(message.getTo());
		Id convId = isChannelMessage ? message.getTo() : message.getFrom();

		// check the contact
		try {
			Contact contact = userAgent.getContact(convId);

			if (contact == null) {
				contact = isChannelMessage ? ChannelImpl.auto(convId) : ContactImpl.auto(convId);
				userAgent.putContact(contact);
			}

			if (contact.isStaled())
				tryRefreshProfile(contact);
		} catch (RepositoryException e) {
			log.error("Failed to get and update the contact profile: ", convId, e);
		}

		if (isChannelMessage && message.getMessageType() == Message.Types.NOTIFICATION)
			processNotification(message);

		userAgent.onMessage(message);
	}

	private void onSent(Message message) {
		// check the contact
		try {
			Contact contact = userAgent.getContact(message.getTo());

			if (contact != null && contact.isStaled())
				tryRefreshProfile(contact);
		} catch (RepositoryException e) {
			log.error("Failed to get the contact: ", message.getTo(), e);
		}

		userAgent.onSent(message);
	}

	private void onBroadcase(Message message) {
		// check the contact
		try {
			// messaging service peer
			Contact contact = userAgent.getContact(message.getFrom());

			// TODO: how to update?
		} catch (RepositoryException e) {
			log.error("Failed to get the contact: ", message.getFrom(), e);
		}

		userAgent.onBroadcast(message);
	}

	private void processNotification(Message message) {
		try {
			Channel channel = userAgent.getChannel(message.getTo());

			Notification<JsonNode> preparsed = message.getBodyAs(
					new TypeReference<Notification<JsonNode>>() {});

			// ((MessageImpl)message).setBody(notification);

			// No need to check if the notification is self-triggered.
			// The messaging service will exclude the operator from receiving it.
			/*
			if (isMe(notification.getOperator()))
				return;
			*/

			switch (preparsed.getEvent()) {
			case Notification.Events.CHANNEL_DELETED:
				userAgent.onChannelDeleted(channel);
				break;

			case Notification.Events.CHANNEL_JOINED: {
				Notification<Channel.Member> notification = preparsed.map();
				userAgent.onChannelMemberJoined(channel, notification.getData());
				break;
			}

			case Notification.Events.CHANNEL_LEFT: {
				Channel.Member member = channel.getMember(preparsed.getOperator());
				if (member == null)
					member = Channel.Member.unknown(preparsed.getOperator());
				userAgent.onChannelMemberLeft(channel, member);
				break;
			}

			case Notification.Events.CHANNEL_PROFILE: {
				Notification<Channel> notification = preparsed.map();
				channel.update(notification.getData());
				userAgent.onChannelUpdated(channel);
				break;
			}

			case Notification.Events.CHANNEL_ROLE: {
				Notification<Notification.ChannelRole.Data> notification = preparsed.map();
				Notification.ChannelRole.Data data = notification.getData();
				Channel.Role role = data.getRole();
				List<Channel.Member> members = data.getMemberIds().stream().map(id -> {
					Channel.Member member = channel.getMember(id);
					if (member == null)
						member = Channel.Member.unknown(id);

					return member;
				}).collect(Collectors.toList());
				userAgent.onChannelMembersRoleChanged(channel, members, role);
				break;
			}

			case Notification.Events.CHANNEL_BANNED: {
				Notification<List<Id>> notification = preparsed.map();
				List<Channel.Member> members = notification.getData().stream().map(id -> {
					Channel.Member member = channel.getMember(id);
					if (member == null)
						member = Channel.Member.unknown(id);

					return member;
				}).collect(Collectors.toList());
				userAgent.onChannelMembersBanned(channel, members);
				break;
			}

			case Notification.Events.CHANNEL_UNBANNED: {
				Notification<List<Id>> notification = preparsed.map();
				List<Channel.Member> members = notification.getData().stream().map(id -> {
					Channel.Member member = channel.getMember(id);
					if (member == null)
						member = Channel.Member.unknown(id);

					return member;
				}).collect(Collectors.toList());
				userAgent.onChannelMembersUnbanned(channel, members);
				break;
			}

			case Notification.Events.CHANNEL_REMOVED: {
				Notification<List<Id>> notification = preparsed.map();
				List<Channel.Member> members = notification.getData().stream().map(id -> {
					Channel.Member member = channel.getMember(id);
					if (member == null)
						member = Channel.Member.unknown(id);

					return member;
				}).collect(Collectors.toList());
				userAgent.onChannelMembersRemoved(channel, members);
				break;
			}

			default:
				log.error("INTERNAL ERROR: unknown channel notification {}", preparsed.getEvent());
			}
		} catch (RepositoryException e) {
			log.error("storage", e);
		} catch (IOException e) {
			log.error("INTERNAL ERROR: message parsing error", e);
		} catch (Exception e) {
			log.error("INTERNAL ERROR: unexpected error", e);
		}
	}

	private Future<Void> doConnect() {
		if (mqttClient != null && mqttClient.isConnected())
			return Future.succeededFuture();

		log.info("Connecting ...");

		disconnect = false;
		onConnecting();

		Map<String, Integer> topics = Map.of(inbox, MqttQoS.AT_LEAST_ONCE.value(),
				outbox, MqttQoS.AT_LEAST_ONCE.value(),
				broadcast, MqttQoS.AT_LEAST_ONCE.value());

		connectPromise = Promise.promise();
		attemptConnect(serviceInfo.getMqttEndpoints(), 0)
			.compose((v) -> getMqttClient().subscribe(topics))
			.onSuccess((v) -> {
				log.info("Subscribing the messages...");
			})
			.onFailure((e) -> {
				log.error("Failed to connect to the messaging service", e);
				onDisonnected();
				connectPromise.fail(e);
			});

		return connectPromise.future();
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

	private Future<Void> reconnect() {
		if (disconnect)
			Future.failedFuture("Stopped");

		log.info("Reconnecting ...");

		return mqttClient.connect(mqttURI.getPort(), mqttURI.getHost())
			.andThen((ar) -> {
				if (ar.succeeded())
					log.info("Connected to the messaging server {} @ {}", peer.getPeerId(), mqttURI);
				else {
					log.warn("Failed to connect to the messaging server {} @ {}", peer.getPeerId(), mqttURI);

					this.failures++;
					int delay = getRetryInterval();
					vertx.setTimer(delay, (tid) -> reconnect());
				}
			}).map(null);
	}

	private Future<Void> doDisconnect() {
		if (mqttClient == null || !mqttClient.isConnected())
			return Future.succeededFuture();

		disconnect = true;

		return mqttClient.disconnect();
	}

	@Override
	public CompletableFuture<Void> connect() {
		CompletableFuture<Void> future = new CompletableFuture<>();

		vertx.runOnContext((v) -> {
			doConnect()
				.onSuccess(na -> future.complete(null))
				.onFailure(e -> future.completeExceptionally(e));
		});

		return future;
	}

	@Override
	public CompletableFuture<Void> disconnect() {
		CompletableFuture<Void> future = new CompletableFuture<>();

		vertx.runOnContext((v) -> {
			doDisconnect()
				.onSuccess(na -> future.complete(null))
				.onFailure(e -> future.completeExceptionally(e));
		});

		return future;
	}

	@Override
	public CompletableFuture<Void> close() {
		return vertx.undeploy(this.deploymentID()).toCompletionStage().toCompletableFuture();
	}

	private byte[] selfEncrypt(byte[] data) {
		CryptoContext ctx = cryptoContexts.get(IdPath.of(user.getId(), user.getId()));
		return ctx.encrypt(data);
	}

	private byte[] selfDecrypt(byte[] data) {
		CryptoContext ctx = cryptoContexts.get(IdPath.of(user.getId(), user.getId()));
		try {
			return ctx.decrypt(data);
		} catch (CryptoException e) {
			log.error("INTERNAL ERROR!!! This should never heppen.");
			throw new IllegalStateException(e);
		}
	}

	private void processRpcRequest(Message message) {
		if (message.getBody() == null || message.getBody().length == 0) {
			log.error("Empty RPC request received, ignored");
			return;
		}

		// intermediate request object with generic JsonNode result
		RPCRequest<JsonNode, ?> preparsed = null;
		try {
			preparsed = message.getBodyAs(new TypeReference<RPCRequest<JsonNode, ?>>() {});
		} catch (IOException e) {
			log.error("Malformed RPC response from {}, parse failed", message.getFrom());
			return;
		}

		if (pendingCalls.containsKey(preparsed.getId())) {
			// this call was initiated by self, do nothing
			return;
		}

		switch (preparsed.getMethod()) {
		case USER_PROFILE: {
			RPCRequest<UserProfile, Boolean> call = preparsed.map();
			pendingCalls.put(call.getId(), call);
			break;
		}

		/*
		case DEVICE_LIST: {
			RPCRequest<Void, List<ClientDevice>> call = preparsed.map();
			pendingCalls.put(call.getId(), call);
			break;
		}

		case DEVICE_REVOKE: {
			RPCRequest<Id, Boolean> call = preparsed.map();
			pendingCalls.put(call.getId(), call);
			break;
		}

		case CONTACT_PUT: {
			RPCRequest<ContactPut, String> call = preparsed.map();
			pendingCalls.put(call.getId(), call);
			break;
		}

		case CONTACT_REMOVE: {
			RPCRequest<ContactRemove, String> call = preparsed.map();
			pendingCalls.put(call.getId(), call);
			break;
		}

		case CONTACT_SYNC: {
			RPCRequest<String, ContactSyncResult> call = preparsed.map();
			pendingCalls.put(call.getId(), call);
			break;
		}

		case CONTACT_CLEAR: {
			RPCRequest<Void, String> call = preparsed.map();
			pendingCalls.put(call.getId(), call);
			break;
		}
		*/

		case CHANNEL_CREATE: {
			RPCRequest<ChannelCreate, Channel> call = preparsed.map();
			pendingCalls.put(call.getId(), call);
			break;
		}

		case CHANNEL_DELETE: {
			RPCRequest<Void, Boolean> call = preparsed.map();
			pendingCalls.put(call.getId(), call);
			break;
		}

		case CHANNEL_JOIN: {
			RPCRequest<InviteTicket, Channel> call = preparsed.map();
			pendingCalls.put(call.getId(), call);
			break;
		}

		case CHANNEL_LEAVE: {
			RPCRequest<Void, Boolean> call = preparsed.map();
			pendingCalls.put(call.getId(), call);
			break;
		}

		case CHANNEL_INFO: {
			RPCRequest<Void, Channel> call = preparsed.map();
			pendingCalls.put(call.getId(), call);
			break;
		}

		case CHANNEL_MEMBERS: {
			RPCRequest<Void, List<Channel.Member>> call = preparsed.map();
			pendingCalls.put(call.getId(), call);
			break;
		}

		case CHANNEL_OWNER: {
			RPCRequest<Id, Boolean> call = preparsed.map();
			pendingCalls.put(call.getId(), call);
			break;
		}

		case CHANNEL_PERMISSION: {
			RPCRequest<Channel.Permission, Boolean> call = preparsed.map();
			pendingCalls.put(call.getId(), call);
			break;
		}

		case CHANNEL_NAME:
		case CHANNEL_NOTICE: {
			RPCRequest<String, Boolean> call = preparsed.map();
			pendingCalls.put(call.getId(), call);
			break;
		}

		case CHANNEL_ROLE: {
			RPCRequest<ChannelMemberRole, Boolean> call = preparsed.map();
			pendingCalls.put(call.getId(), call);
			break;
		}

		case CHANNEL_BAN:
		case CHANNEL_UNBAN:
		case CHANNEL_REMOVE: {
			RPCRequest<List<Id>, Boolean> call = preparsed.map();
			pendingCalls.put(call.getId(), call);
			break;
		}

		default:
			log.error("INTERNAL ERROR: invalid RPC call method {}", preparsed.getMethod().value());
		}
	}

	private void processRpcResponse(Message message) {
		if (message.getBody() == null || message.getBody().length == 0) {
			log.error("Empty RPC response from {}, ignored", message.getFrom());
			return;
		}

		// intermediate response object with generic JsonNode result
		RPCResponse<JsonNode> preparsed = null;
		try {
			preparsed = message.getBodyAs(new TypeReference<RPCResponse<JsonNode>>() {});
		} catch (IOException e) {
			log.error("Malformed RPC response from {}, parse failed", message.getFrom());
			return;
		}

		RPCRequest<?, ?> request = pendingCalls.remove(preparsed.getId());
		if (request == null) {
			log.warn("RPC call {} not exist, ignore the response", preparsed.getId());
			return;
		}

		if (preparsed.failed()) {
			log.error("RPC call {} failed: {}", preparsed.getId(), preparsed.getError());
			request.complete(preparsed.cast());
			return;
		}

		switch (request.getMethod()) {
		case USER_PROFILE: {
			// TODO: How to store and update user information
			RPCRequest<UserProfile, Boolean> call = request.cast();
			call.complete(preparsed.map());
			break;
		}

		case DEVICE_LIST: {
			RPCRequest<Void, List<ClientDevice>> call = request.cast();
			call.complete(preparsed.map());
			break;
		}

		case DEVICE_REVOKE: {
			RPCRequest<Id, Boolean> call = request.cast();
			call.complete(preparsed.map());
			break;
		}

		case CONTACT_PUT: {
			RPCRequest<ContactPut, String> call = request.cast();
			RPCResponse<String> response = preparsed.map();
			String sequenceId = response.getResult();
			List<Contact> contacts = call.getParameters().getContacts();
			userAgent.onContactsUpdated(sequenceId, contacts);
			call.complete(response);
			break;
		}

		case CONTACT_REMOVE: {
			RPCRequest<ContactRemove, String> call = request.cast();
			RPCResponse<String> response = preparsed.map();
			String sequenceId = response.getResult();
			List<Id> contacts = call.getParameters().getContacts();
			userAgent.onContactsRemoved(sequenceId, contacts);
			call.complete(response);
			break;
		}

		case CONTACT_SYNC: {
			RPCRequest<String, ContactSyncResult> call = request.cast();
			RPCResponse<ContactSyncResult> response = preparsed.map();
			String sequenceId = response.getResult().getLastSequence().getId();
			List<Contact> contacts = response.getResult().getContacts();
			userAgent.onContactsSynced(sequenceId, contacts);
			call.complete(response);
			break;
		}

		case CONTACT_CLEAR: {
			RPCRequest<Void, String> call = request.cast();
			RPCResponse<String> response = preparsed.map();
			String sequenceId = response.getResult();
			userAgent.onContactsSynced(sequenceId, Collections.emptyList());
			call.complete(response);
			break;
		}

		case CHANNEL_CREATE: {
			// No notification to the channel creator
			// so save the channel here
			RPCRequest<ChannelCreate, Channel> call = request.cast();
			RPCResponse<Channel> response = preparsed.map();
			ChannelImpl channel = (ChannelImpl)response.getResult();
			channel.setMemberPrivateKey(call.getCookie(this::selfDecrypt));

			// fetch the channel members
			getChannelMembers(channel.getId()).onSuccess(members -> {
				userAgent.onChannelMembers(channel, members);
			}).onFailure(e -> {
				log.error("Failed to get the channel {} members", channel.getId(), e);
			});

			call.complete(response.revised(channel));
			vertx.runOnContext(v -> userAgent.onJoinedChannel(channel));
			break;
		}

		case CHANNEL_DELETE: {
			RPCRequest<Void, Boolean> call = request.cast();
			call.complete(preparsed.map());
			break;
		}

		case CHANNEL_JOIN: {
			// No notification to the new joined member
			// so save the channel here
			RPCRequest<InviteTicket, Channel> call = request.cast();
			RPCResponse<Channel> response = preparsed.map();
			ChannelImpl channel = (ChannelImpl)response.getResult();
			channel.setMemberPrivateKey(call.getCookie(this::selfDecrypt));

			// fetch the channel members
			getChannelMembers(channel.getId()).onSuccess(members -> {
				userAgent.onChannelMembers(channel, members);
			}).onFailure(e -> {
				log.error("Failed to get the channel {} members", channel.getId(), e);
			});

			call.complete(response.revised(channel));
			vertx.runOnContext(v -> userAgent.onJoinedChannel(channel));
			break;
		}

		case CHANNEL_LEAVE: {
			RPCRequest<Void, Boolean> call = request.cast();

			// Create a dummy channel object to notify the listener if the channel is not found.
			ChannelImpl channel = null;
			try {
				channel = (ChannelImpl)userAgent.getChannel(message.getFrom());
			} catch (Exception e) {
				log.error("Failed to get the channel from user agent", e);
			}

			call.complete(preparsed.map());
			Channel ch = channel != null ? channel : ChannelImpl.auto(message.getFrom());
			vertx.runOnContext(v -> userAgent.onLeftChannel(ch));
			break;
		}

		case CHANNEL_INFO: {
			RPCRequest<Void, Channel> call = request.cast();
			RPCResponse<Channel> response = preparsed.map();
			ChannelImpl channel;
			try {
				channel = (ChannelImpl)userAgent.getChannel(message.getFrom());
				if (channel != null) {
					channel.update(response.getResult());
				} else {
					log.error("INTERNAL ERROR: try to update non-exists channel");
					channel = (ChannelImpl)response.getResult();
				}
			} catch (Exception e) {
				log.error("INTERNAL ERROR: Failed to get the channel from user agent", e);
				channel = (ChannelImpl)response.getResult();
			}

			call.complete(response.revised(channel));
			Channel ch = channel;
			vertx.runOnContext(v -> userAgent.onChannelUpdated(ch));
		}

		case CHANNEL_MEMBERS: {
			RPCRequest<Void, List<Channel.Member>> call = request.cast();
			RPCResponse<List<Channel.Member>> reponse = preparsed.map();
			List<Channel.Member> members = reponse.getResult();

			ChannelImpl channel = null;
			try {
				channel = (ChannelImpl)userAgent.getChannel(message.getFrom());
			} catch (Exception e) {
				log.error("INTERNAL ERROR: Failed to get the channel from user agent", e);
			}

			call.complete(reponse);
			Channel ch = channel != null ? channel : ChannelImpl.auto(message.getFrom());
			vertx.runOnContext(v -> userAgent.onChannelMembers(ch, members));
			break;
		}

		case CHANNEL_OWNER: {
			RPCRequest<Id, Boolean> call = request.cast();
			call.complete(preparsed.map());
			break;
		}

		case CHANNEL_PERMISSION: {
			RPCRequest<Channel.Permission, Boolean> call = request.cast();
			call.complete(preparsed.map());
			break;
		}

		case CHANNEL_NAME:
		case CHANNEL_NOTICE: {
			RPCRequest<String, Boolean> call = request.cast();
			call.complete(preparsed.map());
			break;
		}

		case CHANNEL_ROLE: {
			RPCRequest<ChannelMemberRole, Boolean> call = request.cast();
			call.complete(preparsed.map());
			break;
		}

		case CHANNEL_BAN:
		case CHANNEL_UNBAN:
		case CHANNEL_REMOVE: {
			RPCRequest<List<Id>, Boolean> call = request.cast();
			call.complete(preparsed.map());
			break;
		}

		default:
			log.error("INTERNAL ERROR: invalid RPC call method {}", request.getMethod().value());
		}
	}

	private MessageImpl rpcCall(Id recipient, RPCRequest<?, ?> request) {
		MessageBuilderImpl builder = new MessageBuilderImpl(this, Message.Types.CALL);
		builder.to(recipient).body(request);
		return (MessageImpl)builder.build();
	}

	private Future<Integer> sendRpcRequest(Id recipient, RPCRequest<?, ?> request) {
		MessageImpl message = rpcCall(recipient, request);

		return sendMessage(outbox, message)
			.andThen((ar) -> {
				if (ar.succeeded()) {
					pendingCalls.put(request.getId(), request);
					vertx.setTimer(CALL_TIMEOUT, (tid) -> {
						pendingCalls.remove(request.getId());
						request.failed(new TimeoutException());
					});
				} else {
					pendingCalls.remove(request.getId());
					request.failed(ar.cause());
				}
			});
	}

	private Future<Integer> sendRpcRequest(RPCRequest<?, ?> request) {
		return sendRpcRequest(peer.getPeerId(), request);
	}

	@Override
	public CompletableFuture<List<ClientDevice>> listDevices() {
		RPCRequest<Void, List<ClientDevice>> request = new RPCRequest<>(getNextRpcCallId(), RPCMethod.DEVICE_LIST, null);

		vertx.runOnContext((v) -> {
			// NOTICE: Send to the device private outbox
			sendRpcRequest(request);
		});

		return VertxFuture.of(request.getFuture());
	}

	@Override
	public CompletableFuture<Boolean> revokeDevice(Id deviceId) {
		if (deviceId == null)
			return VertxFuture.failedFuture(new NullPointerException("deviceId"));

		RPCRequest<Id, Boolean> request = new RPCRequest<>(
				getNextRpcCallId(), RPCMethod.DEVICE_REVOKE, deviceId);

		vertx.runOnContext((v) -> {
			// NOTICE: Send to the device private outbox
			sendRpcRequest(request);
		});

		return VertxFuture.of(request.getFuture());
	}

	@Override
	public CompletableFuture<Channel> createChannel(Channel.Permission permission, String name, String notice) {
		if (permission == null)
			permission = Channel.Permission.OWNER_INVITE;

		Signature.KeyPair memberKeyPair = Signature.KeyPair.random();
		RPCParameters.ChannelCreate params = new RPCParameters.ChannelCreate(permission, name, notice);

		RPCRequest<RPCParameters.ChannelCreate, Channel> request = new RPCRequest<>(
				getNextRpcCallId(), RPCMethod.CHANNEL_CREATE, params);
		request.setCookie(memberKeyPair, (kp) -> selfEncrypt(kp.privateKey().bytes()));

		vertx.runOnContext((v) -> {
			sendRpcRequest(request);
		});

		return VertxFuture.of(request.getFuture());
	}

	@Override
	public CompletableFuture<Boolean> removeChannel(Id channelId) {
		if (channelId == null)
			return VertxFuture.failedFuture(new NullPointerException("channelId"));

		RPCRequest<Void, Boolean> request = new RPCRequest<>(
				getNextRpcCallId(), RPCMethod.CHANNEL_REMOVE, null);

		vertx.runOnContext((v) -> {
			sendRpcRequest(channelId, request);
		});

		return VertxFuture.of(request.getFuture());
	}

	@Override
	public CompletableFuture<Channel> joinChannel(InviteTicket ticket, byte[] memberPrivateKey) {
		if (ticket == null)
			return VertxFuture.failedFuture(new NullPointerException("ticket"));

		if (memberPrivateKey == null)
			return VertxFuture.failedFuture(new NullPointerException("groupPrivateKey"));

		if (ticket.isExpired())
			return VertxFuture.failedFuture(new IllegalArgumentException("Ticket expired"));

		if (!ticket.isValid(getUserId()))
			return VertxFuture.failedFuture(new IllegalArgumentException("Ticket is not valid"));

		try {
			// check the private key
			Signature.KeyPair.fromPrivateKey(memberPrivateKey);
		} catch (Exception e) {
			return VertxFuture.failedFuture(new IllegalArgumentException("Invalid member private key"));
		}

		RPCRequest<InviteTicket, Channel> request = new RPCRequest<>(
				getNextRpcCallId(), RPCMethod.CHANNEL_JOIN, ticket);
		request.setCookie(memberPrivateKey, this::selfEncrypt);

		vertx.runOnContext((v) -> {
			sendRpcRequest(ticket.getChannelId(), request);
		});

		return VertxFuture.of(request.getFuture());
	}

	@Override
	public CompletableFuture<Boolean> leaveChannel(Id channelId) {
		if (channelId == null)
			return VertxFuture.failedFuture(new NullPointerException("channelId"));

		RPCRequest<Void, Boolean> request = new RPCRequest<>(
				getNextRpcCallId(), RPCMethod.CHANNEL_LEAVE, null);

		vertx.runOnContext((v) -> {
			sendRpcRequest(channelId, request);
		});

		return VertxFuture.of(request.getFuture());
	}

	private Future<Channel> getChannelInfo(Id channelId) {
		if (channelId == null)
			return Future.failedFuture(new NullPointerException("channelId"));

		RPCRequest<Void, Channel> request = new RPCRequest<>(
				getNextRpcCallId(), RPCMethod.CHANNEL_INFO, null);

		vertx.runOnContext((v) -> {
			sendRpcRequest(channelId, request);
		});

		return request.getFuture();
	}

	private Future<List<Channel.Member>> getChannelMembers(Id channelId) {
		if (channelId == null)
			return Future.failedFuture(new NullPointerException("channelId"));

		RPCRequest<Void, List<Channel.Member>> request = new RPCRequest<>(
				getNextRpcCallId(), RPCMethod.CHANNEL_MEMBERS, null);

		vertx.runOnContext((v) -> {
			sendRpcRequest(channelId, request);
		});

		return request.getFuture();
	}

	@Override
	public CompletableFuture<Boolean> setChannelOwner(Id channelId, Id newOwner) {
		if (channelId == null)
			return VertxFuture.failedFuture(new NullPointerException("channelId"));

		if (newOwner == null)
			return VertxFuture.failedFuture(new NullPointerException("newOwner"));

		Channel channel;
		try {
			channel = userAgent.getChannel(channelId);
		} catch (RepositoryException e) {
			return VertxFuture.failedFuture(e);
		}

		if (channel == null)
			return VertxFuture.failedFuture(new IllegalArgumentException("Channel not found"));

		if (!channel.isOwner(user.getId()))
			return VertxFuture.failedFuture(new ForbiddenException("Not channel owner"));

		if (!channel.isMember(newOwner))
			return VertxFuture.failedFuture(new IllegalArgumentException("New owner not exists in the channel"));

		RPCRequest<Id, Boolean> request = new RPCRequest<>(
				getNextRpcCallId(), RPCMethod.CHANNEL_OWNER, newOwner);

		vertx.runOnContext((v) -> {
			sendRpcRequest(channelId, request);
		});

		return VertxFuture.of(request.getFuture());
	}

	@Override
	public CompletableFuture<Boolean> setChannelPermission(Id channelId, Channel.Permission permission) {
		if (channelId == null)
			return VertxFuture.failedFuture(new NullPointerException("channelId"));

		if (permission == null)
			return VertxFuture.failedFuture(new NullPointerException("permission"));

		Channel channel;
		try {
			channel = userAgent.getChannel(channelId);
		} catch (RepositoryException e) {
			return VertxFuture.failedFuture(e);
		}

		if (!channel.isOwner(user.getId()))
			return VertxFuture.failedFuture(new ForbiddenException("Not channel owner"));

		RPCRequest<Channel.Permission, Boolean> request = new RPCRequest<>(
				getNextRpcCallId(), RPCMethod.CHANNEL_PERMISSION, permission);

		vertx.runOnContext((v) -> {
			sendRpcRequest(channelId, request);
		});

		return VertxFuture.of(request.getFuture());
	}

	@Override
	public CompletableFuture<Boolean> setChannelName(Id channelId, String name) {
		if (channelId == null)
			return VertxFuture.failedFuture(new NullPointerException("channelId"));

		Channel channel;
		try {
			channel = userAgent.getChannel(channelId);
		} catch (RepositoryException e) {
			return VertxFuture.failedFuture(e);
		}

		if (!channel.isOwner(user.getId()) && !channel.isModerator(channelId))
			return VertxFuture.failedFuture(new ForbiddenException("Not channel owner or moderator"));

		if (name != null && name.isEmpty())
			name = null;

		RPCRequest<String, Boolean> request = new RPCRequest<>(
				getNextRpcCallId(), RPCMethod.CHANNEL_NAME, name);

		vertx.runOnContext((v) -> {
			sendRpcRequest(channelId, request);
		});

		return VertxFuture.of(request.getFuture());
	}

	@Override
	public CompletableFuture<Boolean> setChannelNotice(Id channelId, String notice) {
		if (channelId == null)
			return VertxFuture.failedFuture(new NullPointerException("channelId"));

		Channel channel;
		try {
			channel = userAgent.getChannel(channelId);
		} catch (RepositoryException e) {
			return VertxFuture.failedFuture(e);
		}

		if (!channel.isOwner(user.getId()) && !channel.isModerator(channelId))
			return VertxFuture.failedFuture(new ForbiddenException("Not channel owner or moderator"));

		if (notice != null && notice.isEmpty())
			notice = null;

		RPCRequest<String, Boolean> request = new RPCRequest<>(
				getNextRpcCallId(), RPCMethod.CHANNEL_NOTICE, notice);

		vertx.runOnContext((v) -> {
			sendRpcRequest(channelId, request);
		});

		return VertxFuture.of(request.getFuture());
	}

	@Override
	public CompletableFuture<Boolean> setChannelMembersRole(Id channelId, List<Id> members, Channel.Role role) {
		if (channelId == null)
			return VertxFuture.failedFuture(new NullPointerException("channelId"));

		if (members == null)
			return VertxFuture.failedFuture(new NullPointerException("members"));

		if (role == null)
			return VertxFuture.failedFuture(new NullPointerException("role"));

		if (members.isEmpty())
			return CompletableFuture.completedFuture(true);

		Channel channel;
		try {
			channel = userAgent.getChannel(channelId);
		} catch (RepositoryException e) {
			return VertxFuture.failedFuture(e);
		}

		if (!channel.isOwner(user.getId()) && !channel.isModerator(channelId))
			return VertxFuture.failedFuture(new ForbiddenException("Not channel owner or moderator"));

		RPCRequest<RPCParameters.ChannelMemberRole, Boolean> request = new RPCRequest<>(
				getNextRpcCallId(), RPCMethod.CHANNEL_ROLE, new RPCParameters.ChannelMemberRole(members, role));

		vertx.runOnContext((v) -> {
			sendRpcRequest(channelId, request);
		});

		return VertxFuture.of(request.getFuture());
	}

	@Override
	public CompletableFuture<Boolean> banChannelMembers(Id channelId, List<Id> members) {
		if (channelId == null)
			return VertxFuture.failedFuture(new NullPointerException("channelId"));

		if (members == null)
			return VertxFuture.failedFuture(new NullPointerException("member"));

		if (members.isEmpty())
			return VertxFuture.completedFuture(true);

		Channel channel;
		try {
			channel = userAgent.getChannel(channelId);
		} catch (RepositoryException e) {
			return VertxFuture.failedFuture(e);
		}

		if (!channel.isOwner(user.getId()) && !channel.isModerator(channelId))
			return VertxFuture.failedFuture(new ForbiddenException("Not channel owner or moderator"));

		RPCRequest<List<Id>, Boolean> request = new RPCRequest<>(
				getNextRpcCallId(), RPCMethod.CHANNEL_BAN, members);

		vertx.runOnContext((v) -> {
			sendRpcRequest(channelId, request);
		});

		return VertxFuture.of(request.getFuture());
	}

	@Override
	public CompletableFuture<Boolean> unbanChannelMembers(Id channelId, List<Id> members) {
		if (channelId == null)
			return VertxFuture.failedFuture(new NullPointerException("channelId"));

		if (members == null)
			return VertxFuture.failedFuture(new NullPointerException("member"));

		if (members.isEmpty())
			return VertxFuture.completedFuture(true);

		Channel channel;
		try {
			channel = userAgent.getChannel(channelId);
		} catch (RepositoryException e) {
			return VertxFuture.failedFuture(e);
		}

		if (!channel.isOwner(user.getId()) && !channel.isModerator(channelId))
			return VertxFuture.failedFuture(new ForbiddenException("Not channel owner or moderator"));

		RPCRequest<List<Id>, Boolean> request = new RPCRequest<>(
				getNextRpcCallId(), RPCMethod.CHANNEL_UNBAN, members);

		vertx.runOnContext((v) -> {
			sendRpcRequest(channelId, request);
		});

		return VertxFuture.of(request.getFuture());
	}

	@Override
	public CompletableFuture<Boolean> removeChannelMembers(Id channelId, List<Id> members) {
		if (channelId == null)
			return VertxFuture.failedFuture(new NullPointerException("channelId"));

		if (members == null)
			return VertxFuture.failedFuture(new NullPointerException("member"));

		if (members.isEmpty())
			return VertxFuture.completedFuture(true);

		Channel channel;
		try {
			channel = userAgent.getChannel(channelId);
		} catch (RepositoryException e) {
			return VertxFuture.failedFuture(e);
		}

		if (!channel.isOwner(user.getId()) && !channel.isModerator(channelId))
			return VertxFuture.failedFuture(new ForbiddenException("Not channel owner or moderator"));

		RPCRequest<List<Id>, Boolean> request = new RPCRequest<>(
				getNextRpcCallId(), RPCMethod.CHANNEL_REMOVE, members);

		vertx.runOnContext((v) -> {
			sendRpcRequest(channelId, request);
		});

		return VertxFuture.of(request.getFuture());
	}



	// TODO:
	/*
	protected CompletableFuture<Boolean> putContacts(List<Contact> contacts) {
		if (contacts == null)
			return CompletableFuture.failedFuture(new NullPointerException("contacts"));

		if (contacts.isEmpty())
			return CompletableFuture.completedFuture(true);

		RPCRequest<List<Contact>, Boolean> request = new RPCRequest<>(
				getNextRpcCallId(), RPCMethod.CONTACT_PUT, contacts);

		vertx.runOnContext((v) -> {
			sendRpcRequest(outbox, request);
		});

		return request.getFuture();
	}

	protected CompletableFuture<Boolean> removeContacts(List<Id> contacts) {
		if (contacts == null)
			return CompletableFuture.failedFuture(new NullPointerException("contacts"));

		if (contacts.isEmpty())
			return CompletableFuture.completedFuture(true);

		RPCRequest<List<Id>, Boolean> request = new RPCRequest<>(
				getNextRpcCallId(), RPCMethod.CONTACT_REMOVE, contacts);

		vertx.runOnContext((v) -> {
			sendRpcRequest(outbox, request);
		});

		return request.getFuture();
	}

	protected CompletableFuture<Void> syncContacts() {
		RPCRequest<Void, List<Contact>> request = new RPCRequest<>(
				getNextRpcCallId(), RPCMethod.CONTACT_SYNC, null);

		vertx.runOnContext((v) -> {
			sendRpcRequest(outbox, request);
		});

		return request.getFuture();
	}

	protected CompletableFuture<Void> clearContacts() {
		RPCRequest<Void, String> request = new RPCRequest<>(
				getNextRpcCallId(), RPCMethod.CONTACT_CLEAR, null);

		vertx.runOnContext((v) -> {
			sendRpcRequest(outbox, request);
		});

		return request.getFuture();
	}
	*/


	private Future<Integer> sendMessage(String topic, MessageImpl message) {
		// message send to home peer no need to encrypt the body
		Message encryptedMsg = message;
		byte[] body = message.getBody();
		if (!message.getTo().equals(peer.getPeerId()) && body != null && body.length > 0) {
			// encrypt the message body
			CryptoContext context = cryptoContexts.get(IdPath.of(message.getFrom(), message.getTo()));
			body = context.encrypt(message.getBody());
			encryptedMsg = message.dup(body);
		}

		byte[] payload = serverContext.encrypt(encryptedMsg.serialize());

		return mqttClient.publish(topic, Buffer.buffer(payload), MqttQoS.AT_LEAST_ONCE, false, false)
			.andThen((ar) -> {
				if (ar.succeeded()) {
					log.debug("Sent message to {}", message.getTo());
				} else {
					log.error("Sent message to {} failed", message.getTo(), ar.cause());
				}
			});
	}

	protected CompletableFuture<Message> sendMessage(Message message) {
		MessageImpl msg = (MessageImpl)message;
		Promise<Message> promise = msg.initSendPromise();

		vertx.runOnContext((v) -> {
			sendMessage(outbox, msg)
				.onSuccess(packetId -> {
					pendingMessages.put(packetId, msg);
				}).onFailure(e -> {
					msg.failed(e);
				});
		});

		return VertxFuture.of(promise.future());
	}

	@Override
	public Builder message() {
		return new MessageBuilderImpl(this, Message.Types.MESSAGE);
	}

	@Override
	public CompletableFuture<Void> syncContact() {
		// TODO:
		return null;
	}

	private Future<Contact> tryRefreshProfile(Contact contact) {
		Promise<Contact> promise = Promise.promise();

		vertx.runOnContext(v -> {
			log.debug("Fetching the profile {} ...", contact.getId());

			apiClient.getProfile(contact.getId()).onSuccess(profile -> {
				try {
					contact.update(profile);
				} catch (Exception e) {
					log.error("Failed to update the profile {}: {}", contact.getId(), e.getMessage());
				}

				try {
					userAgent.putContact(contact);
				} catch (Exception e) {
					log.error("Failed to save the contact {} after update its profile", contact.getId(), e);
				}

				promise.complete(contact);
			}).onFailure(e -> {
				log.error("Failed to fetch the profile {}", contact.getId(), e);
				promise.fail(e);
			});
		});

		return promise.future();
	}
}
