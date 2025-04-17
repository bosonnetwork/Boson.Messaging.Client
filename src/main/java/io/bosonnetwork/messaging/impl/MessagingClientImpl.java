package io.bosonnetwork.messaging.impl;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import io.bosonnetwork.CryptoContext;
import io.bosonnetwork.Id;
import io.bosonnetwork.Identity;
import io.bosonnetwork.crypto.CryptoBox.Nonce;
import io.bosonnetwork.crypto.CryptoException;
import io.bosonnetwork.crypto.CryptoIdentity;
import io.bosonnetwork.crypto.Hash;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.messaging.Channel;
import io.bosonnetwork.messaging.ClientDevice;
import io.bosonnetwork.messaging.Contact;
import io.bosonnetwork.messaging.ForbiddenException;
import io.bosonnetwork.messaging.InviteTicket;
import io.bosonnetwork.messaging.Message;
import io.bosonnetwork.messaging.Message.Builder;
import io.bosonnetwork.messaging.MessagingClient;
import io.bosonnetwork.messaging.MessagingException;
import io.bosonnetwork.messaging.MessagingPeerInfo;
import io.bosonnetwork.messaging.Profile;
import io.bosonnetwork.messaging.RepositoryException;
import io.bosonnetwork.messaging.TimeoutException;
import io.bosonnetwork.messaging.UnknownRecipient;
import io.bosonnetwork.messaging.UserAgent;
import io.bosonnetwork.messaging.impl.APIClient.MessagingServiceInfo;
import io.bosonnetwork.messaging.rpc.RPCMethod;
import io.bosonnetwork.messaging.rpc.RPCParameters;
import io.bosonnetwork.messaging.rpc.RPCParameters.ChannelCreate;
import io.bosonnetwork.messaging.rpc.RPCParameters.ChannelMemberRole;
import io.bosonnetwork.messaging.rpc.RPCRequest;
import io.bosonnetwork.messaging.rpc.RPCResponse;
import io.bosonnetwork.utils.Base58;
import io.bosonnetwork.utils.vertx.VertxFuture;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubAckMessage;

public class MessagingClientImpl implements Verticle, MessagingClient {
	// since Jan 01 2020 00:00:00 GMT+0000
	private static final long EPOCH_BOSON = 1577836800000L;

	private static final int CALL_TIMEOUT = 60_000; // 60 seconds
	private static final String broadcast = "broadcast";

	/**
	 * Reference to the Vert.x instance that deployed this verticle
	 */
	private Vertx vertx;

	/**
	 * Reference to the vertxContext of the verticle
	 */
	private Context vertxContext;

	private final UserAgent userAgent;

	private final MessagingPeerInfo peer;
	private final CryptoIdentity user;
	private final Identity device;
	private final String clientId;

	private final String inbox;
	private final String outbox;

	private final long baseIndex;
	private final long baseNano;

	private MessagingServiceInfo serviceInfo;

	private CryptoContext serverContext;
	private CryptoContext selfContext;

	private int failures;
	private boolean disconnect;

	private APIClient apiClient;

	private MqttClientOptions mqttClientOptions;
	private URI mqttURI;
	private MqttClient mqttClient;
	private Promise<Void> connectPromise;

	// packetId -> message, tracking the MQTT message delivery
	private Map<Integer, MessageImpl> pendingMessages;
	// message serial number -> message, tracking the self-sent messages
	private Map<Long, MessageImpl> sendingMessages;

	private Map<Long, RPCRequest<?, ?>> pendingCalls;

	private static final Logger log = LoggerFactory.getLogger(MessagingClientImpl.class);

	public MessagingClientImpl(UserAgent userAgent) {
		Objects.requireNonNull(userAgent);
		if (!userAgent.isConfigured())
			throw new IllegalStateException("UserAgent is not configured");

		if (userAgent instanceof DefaultUserAgent dua)
			dua.harden();

		this.peer = userAgent.getMessagingPeerInfo();
		this.user = ((UserProfileImpl)userAgent.getUser()).getIdentity();
		this.device = ((DeviceProfileImpl)userAgent.getDevice()).getIdentity();
		this.userAgent = userAgent;

		this.clientId = getClientId(device.getId());

		inbox = "inbox/" + user.getId().toBase58String();
		outbox = "outbox/" + user.getId().toBase58String();

		pendingMessages = new HashMap<>();
		sendingMessages = new HashMap<>();
		pendingCalls = new HashMap<>();

		// base index: last 4 bytes of device id as unsigned int +
		//				microseconds since 2020-01-01T00:00:00z
		ByteBuffer bb = ByteBuffer.wrap(device.getId().bytes());
		baseIndex = Integer.toUnsignedLong(bb.getInt(Id.BYTES - Integer.BYTES)) +
				(System.currentTimeMillis() - EPOCH_BOSON) * 1000;

		baseNano = System.nanoTime();

	}

	@Override
	public UserAgent getUserAgent() {
		return userAgent;
	}

	@Override
	public Id getUserId() {
		return user.getId();
	}

	private String loadAccessToken() {
		try {
			Map<String, Object> config = userAgent.getProperties("api");
			if (config != null && !config.isEmpty())
				return (String)config.get("accessToken");
		} catch (Exception e) {
			log.error("Load API client config failed: {}", e.getMessage(), e);
			throw new IllegalStateException("config: invalid API client config", e);
		}

		return null;
	}

	private void updateAccessToken(String token) {
		Map<String, Object> config = Map.of("accessToken", token);

		try {
			userAgent.putProperties("api", config);
		} catch (RepositoryException e) {
			log.error("Save API client config failed: ", e.getMessage(), e);
		}
	}

	@Override
	public void init(Vertx vertx, Context context) {
		this.vertx = vertx;
		this.vertxContext = context;
	}

	@Override
	public Vertx getVertx() {
		return vertx;
	}

	@Override
	public void start(Promise<Void> startPromise) {
		loadAccessToken();

		apiClient = new APIClient(vertx, peer.getPeerId(), peer.getApiURL());
		apiClient.setUserIdentity(user);
		apiClient.setDeviceIdentity(device);
		apiClient.setAccessToken(loadAccessToken());
		apiClient.setAccessTokenRefreshHandler(this::updateAccessToken);

		selfContext = user.createCryptoContext(user.getId());
		vertxContext.putLocal("SelfEncryptionContext", selfContext);

		String currentVersionId = null;
		try {
			currentVersionId = userAgent.getContactsVersion();
		} catch (RepositoryException e) {
			log.warn("Fectching all contacts due to failed to get contacts version: {},", e.getMessage(), e);
		}

		// TODO: fetch contacts update just if needed
		Future<Void> fetchFuture = Future.succeededFuture();
		if (currentVersionId == null) {
			// first time start
			String versionId = currentVersionId;
			fetchFuture = apiClient.fetchContactsUpdate(versionId).map(update -> {
				if (!Objects.equals(update.getVersionId(), versionId))
					try {
						userAgent.putContactsUpdate(update.getVersionId(), update.getContacts());
					} catch (RepositoryException e) {
						log.warn("Failed to save contacts update: {}", e.getMessage(), e);
					}

				return (Void)null;
			});
		}

		fetchFuture.compose(res -> {
			return apiClient.getServiceInfo().map(info -> {
				serviceInfo = info;

				serverContext = user.createCryptoContext(peer.getPeerId());

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

				return (Void)null;
			});
		}).onComplete(startPromise);
	}

	@Override
	public void stop(Promise<Void> stopPromise) throws Exception {
		doDisconnect().onComplete((ar) -> {
			serverContext.close();
			serverContext = null;
			mqttClientOptions = null;
			mqttClient = null;
			stopPromise.complete();
		});
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

	protected long getNextIndex() {
		long offset = (System.nanoTime() - baseNano) / 1000;
		return baseIndex + offset;
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
			userAgent.onDisconnected();
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
			userAgent.onConnected();
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

		// Sent message failed, remove it from the sending messages
		sendingMessages.remove(message.getSerialNumber());
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
			message.setEncrypted(true);

			log.trace("\uD83D\uDFE2 Decrypted message from {} ",  message.getFrom());

			if (topic.equals(inbox)) {
				processIncomingMessage(message);
			} else if (topic.equals(outbox))
				processOutgoingMessage(message);
			else if (topic.equals(broadcast))
				processBroadcast(message);
		} catch (Exception e) {
			log.error("Failed to process the MQTT message", e);
		}
	}

	private void processIncomingMessage(MessageImpl message) throws CryptoException, RepositoryException {
		int type = message.getMessageType();

		byte[] body = message.getBody();
		if (body != null && body.length > 0) {
			if (message.getFrom().equals(peer.getPeerId())) {
				// service RPC response or user notification: servicePeer -> me
				// body is encrypted with the message envelope
				message.setEncrypted(false);
			} else {
				if (type == Message.Types.MESSAGE) {
					// - Message: sender -> me
					//   The body is encrypted using the sender's private key
					//   and the session public key associated with that sender.
					// - Message: sender -> channel
					//   The body is encrypted using the sender's private key
					//   and the session public key of channel.

					CryptoContext ctx = null;
					if (isMe(message.getTo())) {
						Contact sender = userAgent.getContact(message.getFrom());
						if (sender != null && sender.hasSessionKey())
							ctx = sender.getRxCryptoContext();
					} else {
						Channel channel = userAgent.getChannel(message.getTo());
						if (channel != null && channel.hasSessionKey())
							ctx = channel.getRxCryptoContext(message.getFrom());
					}

					if (ctx != null)
						message.decryptBody(ctx);
					else
						log.warn("Message from unknow sender {}, keep in encrypted", message.getFrom());
				} else if (type == Message.Types.CALL) {
					// Channel RPC response: channel -> me
					// The body is encrypted using sender's private key and my public key.
					CryptoContext ctx = user.createCryptoContext(message.getFrom());
					message.decryptBody(ctx);
				} else if (type == Message.Types.NOTIFICATION) {
					// Channel notification: channel -> channel
					// The body is encrypted using the channel's private key
					// and the channel session's public key
					Channel channel = userAgent.getChannel(message.getFrom());
					if (channel != null && channel.hasSessionKey()) {
						CryptoContext ctx = channel.getRxCryptoContext();
						message.decryptBody(ctx);
					} else {
						log.warn("Notification from unknow sender {}, keep in encrypted", message.getFrom());
					}
				}

				// Ignore unknown message types here. Errors will be logged below.
			}
		} else {
			message.setEncrypted(false);
		}

		if (type == Message.Types.MESSAGE)
			onMessage(message);
		else if (type == Message.Types.CALL)
			processRpcResponse(message);
		else if (type == Message.Types.NOTIFICATION)
			processNotification(message);
		else
			log.warn("Unknown incoming message type {} from {}, ignore", type, message.getFrom());
	}

	private void processOutgoingMessage(MessageImpl message) throws CryptoException, RepositoryException {
		int type = message.getMessageType();

		long sn = message.getSerialNumber();
		MessageImpl selfSent = sendingMessages.remove(sn);
		if (selfSent != null) {
			// message sent from this client device
			// just use the original message
			log.trace("\uD83D\uDCE8 using the original message");
			message = selfSent;
		} else {
			log.trace("\uD83D\uDCE7 using the parsed message");
			// message sent from other client device
			byte[] body = message.getBody();
			if (body != null && body.length > 0) {
				if (message.getTo().equals(peer.getPeerId())) {
					// service RPC requests: me -> servicePeer
					// body is encrypted with the message envelope
					message.setEncrypted(false);
				} else {
					if (type == Message.Types.MESSAGE) {
						// The body is encrypted using my private key and the recipient's session public key.
						Contact contact = userAgent.getContact(message.getTo());
						if (contact != null && contact.hasSessionKey()) {
							CryptoContext ctx = contact.getTxCryptoContext(() -> {
								return user.createCryptoContext(contact.getSessionId());
							});
							message.decryptBody(ctx);
						} else {
							log.warn("Message to unknow contact {}, keep in encrypted", message.getTo());
						}
					} else if (type == Message.Types.CALL) {
						// The body is encrypted using my private key and the recipient's public key.
						// TODO: CHEKME - need cache the RPC crypto vertxContext?
						CryptoContext ctx = user.createCryptoContext(message.getTo());
						message.decryptBody(ctx);
					}

					// Ignore other message types(notification) here. Errors will be logged below.
				}
			} else {
				message.setEncrypted(false);
			}
		}

		if (type == Message.Types.MESSAGE)
			onSent(message);
		else if (type == Message.Types.CALL)
			processRpcRequest(message);
		else
			log.error("INTERNAL ERROR: unexpected outgoing message type {}", type);
	}

	private void processNotification(Message message) {
		try {
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
			case Notification.Events.USER_PROFILE: {
				Notification<Profile> notification = preparsed.map(Profile.class);
				Profile profile = notification.getData();
				if (!isMe(profile.getId()) && !profile.isGenuine()) {
					log.warn("User updated its profile, but the Profile invalid");
					return;
				} else {
					log.info("User updated its profile");
				}

				userAgent.onUserProfileChanged(profile.getName(), profile.hasAvatar());
				break;
			}

			case Notification.Events.CHANNEL_DELETED: {
				Channel channel = userAgent.getChannel(message.getTo());
				userAgent.onChannelDeleted(channel);
				break;
			}

			case Notification.Events.CHANNEL_JOINED: {
				Channel channel = userAgent.getChannel(message.getTo());
				Notification<Channel.Member> notification = preparsed.map(Channel.Member.class);
				userAgent.onChannelMemberJoined(channel, notification.getData());
				break;
			}

			case Notification.Events.CHANNEL_LEFT: {
				Channel channel = userAgent.getChannel(message.getTo());
				Channel.Member member = channel.getMember(preparsed.getOperator());
				if (member == null)
					member = Channel.Member.unknown(preparsed.getOperator());
				userAgent.onChannelMemberLeft(channel, member);
				break;
			}

			case Notification.Events.CHANNEL_PROFILE: {
				Channel channel = userAgent.getChannel(message.getTo());
				Notification<Channel> notification = preparsed.map(Channel.class);
				channel.update(notification.getData());
				userAgent.onChannelUpdated(channel);
				break;
			}

			case Notification.Events.CHANNEL_ROLE: {
				Channel channel = userAgent.getChannel(message.getTo());
				Notification<Notification.ChannelRole.Data> notification = preparsed.map(
						Notification.ChannelRole.Data.class);
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
				Channel channel = userAgent.getChannel(message.getTo());
				Notification<List<Id>> notification = preparsed.map(new TypeReference<List<Id>>() {});
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
				Channel channel = userAgent.getChannel(message.getTo());
				Notification<List<Id>> notification = preparsed.map(new TypeReference<List<Id>>() {});
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
				Channel channel = userAgent.getChannel(message.getTo());
				Notification<List<Id>> notification = preparsed.map(new TypeReference<List<Id>>() {});
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

	private void processBroadcast(MessageImpl message) {
		// Broadcast notifications from the service peer.
		// Message body is encrypted with the message envelope,
		// it was already decrypted here
		message.setEncrypted(false);
		userAgent.onBroadcast(message);
	}

	private void onMessage(Message message) {
		boolean isChannelMessage = !isMe(message.getTo());
		Id convId = isChannelMessage ? message.getTo() : message.getFrom();

		vertxContext.runOnContext(v -> {
			userAgent.onMessage(message);

			// check if the contact need to be update
			try {
				Contact contact = userAgent.getContact(convId);
				if (contact == null || contact.isStaled())
					tryRefreshProfile(convId).onSuccess(profile -> {
						userAgent.onContactProfile(convId, profile);
					});
			} catch (RepositoryException e) {
				log.error("Failed to get and update the contact profile: ", convId, e);
			}
		});
	}

	private void onSent(Message message) {
		vertxContext.runOnContext(v -> {
			userAgent.onSent(message);

			// check if the contact need to be update
			try {
				Id convId = message.getTo();
				Contact contact = userAgent.getContact(convId);
				if (contact != null && contact.isStaled())
					if (contact == null || contact.isStaled())
						tryRefreshProfile(convId).onSuccess(profile -> {
							userAgent.onContactProfile(convId, profile);
						});
			} catch (RepositoryException e) {
				log.error("Failed to get the contact: ", message.getTo(), e);
			}
		});
	}

	private Future<Void> doConnect() {
		if (mqttClient != null && mqttClient.isConnected())
			return Future.succeededFuture();

		log.info("Connecting ...");

		disconnect = false;
		userAgent.onConnecting();

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
				userAgent.onDisconnected();
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
					this.failures++;
					int delay = getRetryInterval();

					log.warn("Failed to connect to the messaging server {} @ {}, will retry in {} seconds", peer.getPeerId(), mqttURI, delay);
					vertx.setTimer(TimeUnit.SECONDS.toMillis(delay), (tid) -> reconnect());
				}
			}).mapEmpty();
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

		vertxContext.runOnContext((v) -> {
			doConnect()
				.onSuccess(na -> future.complete(null))
				.onFailure(e -> future.completeExceptionally(e));
		});

		return future;
	}

	@Override
	public CompletableFuture<Void> disconnect() {
		CompletableFuture<Void> future = new CompletableFuture<>();

		vertxContext.runOnContext((v) -> {
			doDisconnect()
				.onSuccess(na -> future.complete(null))
				.onFailure(e -> future.completeExceptionally(e));
		});

		return future;
	}

	@Override
	public CompletableFuture<Void> close() {
		return vertx.undeploy(vertxContext.deploymentID())
				.toCompletionStage()
				.toCompletableFuture();
	}

	@Override
	public Builder message() {
		return new MessageBuilderImpl(this, Message.Types.MESSAGE);
	}

	private byte[] selfEncrypt(byte[] data) {
		return selfContext.encrypt(data);
	}

	private byte[] selfDecrypt(byte[] data) {
		try {
			return selfContext.decrypt(data);
		} catch (CryptoException e) {
			log.error("INTERNAL ERROR!!! This should never heppen.");
			throw new IllegalStateException(e);
		}
	}

	private void processRpcRequest(MessageImpl message) {
		if (message.getBody() == null || message.getBody().length == 0) {
			log.error("Empty RPC request received, ignored");
			return;
		}

		if (message.hasOriginalBody()) {
			RPCRequest<?, ?> original = (RPCRequest<?, ?>)message.getOriginalBody();
			if (!pendingCalls.containsKey(original.getId())) {
				log.error("INTERNAL ERROR: RPC request not found, ignored");
			}

			// this call was initiated by self, do nothing
			// log.info("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
			return;
		}

		// intermediate request object with generic JsonNode result
		RPCRequest<JsonNode, ?> preparsed = null;
		try {
			preparsed = message.getBodyAs(new TypeReference<RPCRequest<JsonNode, ?>>() {});
		} catch (IOException e) {
			log.error("Malformed RPC request from {}, parse failed", message.getFrom(), e);
			return;
		}

		switch (preparsed.getMethod()) {

		case DEVICE_LIST: {
			// ignore
			// RPCRequest<Void, List<ClientDevice>> call = preparsed.map();
			// pendingCalls.put(call.getId(), call);
			break;
		}

		case DEVICE_REVOKE: {
			// ignore
			// RPCRequest<Id, Boolean> call = preparsed.map();
			// pendingCalls.put(call.getId(), call);
			break;
		}

		case CONTACT_PUSH: {
			RPCRequest<ContactsUpdate, String> call = preparsed.map(ContactsUpdate.class);
			pendingCalls.put(call.getId(), call);
			break;
		}

		case CONTACT_CLEAR: {
			RPCRequest<Void, Boolean> call = preparsed.cast();
			pendingCalls.put(call.getId(), call);
			break;
		}

		case CHANNEL_CREATE: {
			RPCRequest<ChannelCreate, Channel> call = preparsed.map(ChannelCreate.class);
			pendingCalls.put(call.getId(), call);
			break;
		}

		case CHANNEL_DELETE: {
			RPCRequest<Void, Boolean> call = preparsed.cast();
			pendingCalls.put(call.getId(), call);
			break;
		}

		case CHANNEL_JOIN: {
			RPCRequest<InviteTicket, Channel> call = preparsed.map(InviteTicket.class);
			pendingCalls.put(call.getId(), call);
			break;
		}

		case CHANNEL_LEAVE: {
			RPCRequest<Void, Boolean> call = preparsed.cast();
			pendingCalls.put(call.getId(), call);
			break;
		}

		case CHANNEL_INFO: {
			RPCRequest<Void, Channel> call = preparsed.cast();
			pendingCalls.put(call.getId(), call);
			break;
		}

		case CHANNEL_MEMBERS: {
			RPCRequest<Void, List<Channel.Member>> call = preparsed.cast();
			pendingCalls.put(call.getId(), call);
			break;
		}

		case CHANNEL_OWNER: {
			RPCRequest<Id, Boolean> call = preparsed.map(Id.class);
			pendingCalls.put(call.getId(), call);
			break;
		}

		case CHANNEL_PERMISSION: {
			RPCRequest<Channel.Permission, Boolean> call = preparsed.map(Channel.Permission.class);
			pendingCalls.put(call.getId(), call);
			break;
		}

		case CHANNEL_NAME:
		case CHANNEL_NOTICE: {
			RPCRequest<String, Boolean> call = preparsed.map(String.class);
			pendingCalls.put(call.getId(), call);
			break;
		}

		case CHANNEL_ROLE: {
			RPCRequest<ChannelMemberRole, Boolean> call = preparsed.map(ChannelMemberRole.class);
			pendingCalls.put(call.getId(), call);
			break;
		}

		case CHANNEL_BAN:
		case CHANNEL_UNBAN:
		case CHANNEL_REMOVE: {
			RPCRequest<List<Id>, Boolean> call = preparsed.map(new TypeReference<List<Id>>() {});
			pendingCalls.put(call.getId(), call);
			break;
		}

		default:
			log.error("INTERNAL ERROR: invalid RPC call method {}", preparsed.getMethod().value());
		}
	}

	private void processRpcResponse(MessageImpl message) {
		if (message.getBody() == null || message.getBody().length == 0) {
			log.error("Empty RPC response from {}, ignored", message.getFrom());
			return;
		}

		// intermediate response object with generic JsonNode result
		final RPCResponse<JsonNode> preparsed;
		try {
			preparsed = message.getBodyAs(new TypeReference<RPCResponse<JsonNode>>() {});
		} catch (IOException e) {
			log.error("Malformed RPC response from {}, parse failed", message.getFrom(), e);
			return;
		}

		RPCRequest<?, ?> request = pendingCalls.remove(preparsed.getId());
		if (request == null) {
			log.warn("RPC call {} not exist, ignore the response", preparsed.getId());
			return;
		}

		if (preparsed.failed()) {
			log.error("RPC call {} failed: {}", preparsed.getId(), preparsed.getError());
			vertxContext.runOnContext(v -> {
				request.complete(preparsed.cast());
			});
			return;
		}

		switch (request.getMethod()) {
		case DEVICE_LIST: {
			RPCRequest<Void, List<ClientDevice>> call = request.cast();
			RPCResponse<List<ClientDevice>> response = preparsed.map(new TypeReference<List<ClientDevice>>() {});
			vertxContext.runOnContext(v -> {
				call.complete(response);
			});
			break;
		}

		case DEVICE_REVOKE: {
			RPCRequest<Id, Boolean> call = request.cast();
			RPCResponse<Boolean> response = preparsed.map(Boolean.class);
			vertxContext.runOnContext(v -> {
				call.complete(response);
			});
			break;
		}

		case CONTACT_PUSH: {
			RPCRequest<ContactsUpdate, String> call = request.cast();
			RPCResponse<String> response = preparsed.map(String.class);
			String baseVersionId = call.getParameters().getVersionId();
			String newVersionId = response.getResult();
			List<Contact> contacts = call.getParameters().getContacts();

			vertxContext.runOnContext(v -> {
				userAgent.onContactsUpdated(baseVersionId, newVersionId, contacts);
				call.complete(response);
			});
			break;
		}

		case CONTACT_CLEAR: {
			RPCRequest<Void, Boolean> call = request.cast();
			RPCResponse<Boolean> response = preparsed.map(Boolean.class);
			// always success
			vertxContext.runOnContext(v -> {
				userAgent.onContactsCleared();
				call.complete(response);
			});
			break;
		}

		case CHANNEL_CREATE: {
			// No notification to the channel creator
			// so save the channel here
			RPCRequest<ChannelCreate, Channel> call = request.cast();
			RPCResponse<Channel> response = preparsed.map(Channel.class);
			ChannelImpl channel = (ChannelImpl)response.getResult();
			channel.setSessionKey(call.getCookie(this::selfDecrypt));

			// fetch the channel members
			if (request.isInitiator())
				getChannelMembers(channel.getId());

			vertxContext.runOnContext(v -> {
				userAgent.onJoinedChannel(channel);
				call.complete(response);
			});
			break;
		}

		case CHANNEL_DELETE: {
			RPCRequest<Void, Boolean> call = request.cast();
			RPCResponse<Boolean> response = preparsed.map(Boolean.class);
			vertxContext.runOnContext(v -> {
				call.complete(response);
			});
			break;
		}

		case CHANNEL_JOIN: {
			// No notification to the new joined member
			// so save the channel here
			RPCRequest<InviteTicket, Channel> call = request.cast();
			RPCResponse<Channel> response = preparsed.map(Channel.class);
			ChannelImpl channel = (ChannelImpl)response.getResult();
			channel.setSessionKey(call.getCookie(this::selfDecrypt));

			// fetch the channel members
			if (request.isInitiator())
				getChannelMembers(channel.getId());

			vertxContext.runOnContext(v -> {
				userAgent.onJoinedChannel(channel);
				call.complete(response);
			});
			break;
		}

		case CHANNEL_LEAVE: {
			RPCRequest<Void, Boolean> call = request.cast();

			ChannelImpl channel = null;
			try {
				channel = (ChannelImpl)userAgent.getChannel(message.getFrom());
			} catch (Exception e) {
				log.error("INTERNAL ERROR: Failed to retrieve the channel from the user agent.", e);
			}

			// Create a dummy channel object to notify the listener
			// if the channel is not found or can not be retrieved
			Channel ch = channel != null ? channel : ChannelImpl.auto(message.getFrom());
			vertxContext.runOnContext(v -> {
				userAgent.onLeftChannel(ch);
				call.complete(preparsed.map(Boolean.class));
			});
			break;
		}

		case CHANNEL_INFO: {
			RPCRequest<Void, Channel> call = request.cast();
			RPCResponse<ChannelImpl> response = preparsed.map(ChannelImpl.class);
			ChannelImpl channel = null;
			try {
				channel = (ChannelImpl)userAgent.getChannel(message.getFrom());
			} catch (Exception e) {
				log.error("INTERNAL ERROR: Failed to retrieve the channel from the user agent.", e);
			}

			if (channel != null)
				channel.update(response.getResult());
			else
				channel = response.getResult();

			Channel ch = channel; // just make compiler happy
			vertxContext.runOnContext(v -> {
				userAgent.onChannelUpdated(ch);
				call.complete(response.revised(ch));
			});
		}

		case CHANNEL_MEMBERS: {
			RPCRequest<Void, List<Channel.Member>> call = request.cast();
			RPCResponse<List<Channel.Member>> reponse = preparsed.map(new TypeReference<List<Channel.Member>>() {});
			List<Channel.Member> members = reponse.getResult();

			ChannelImpl channel = null;
			try {
				channel = (ChannelImpl)userAgent.getChannel(message.getFrom());
			} catch (Exception e) {
				log.error("INTERNAL ERROR: Failed to retrieve the channel from the user agent.", e);
			}

			Channel ch = channel != null ? channel : ChannelImpl.auto(message.getFrom());
			vertxContext.runOnContext(v -> {
				userAgent.onChannelMembers(ch, members);
				call.complete(reponse);
			});
			break;
		}

		case CHANNEL_OWNER: {
			RPCRequest<Id, Boolean> call = request.cast();
			RPCResponse<Boolean> response = preparsed.map(Boolean.class);

			ChannelImpl channel = null;
			try {
				channel = (ChannelImpl)userAgent.getChannel(message.getFrom());
				channel.setOwner(call.getParameters());
			} catch (RepositoryException e) {
				log.error("INTERNAL ERROR: Failed to retrieve the channel from the user agent.", e);
			}

			final Channel ch = channel; // just make compiler happy
			vertxContext.runOnContext(v -> {
				userAgent.onChannelUpdated(ch);
				call.complete(response);
			});
			break;
		}

		case CHANNEL_PERMISSION: {
			RPCRequest<Channel.Permission, Boolean> call = request.cast();
			RPCResponse<Boolean> response = preparsed.map(Boolean.class);

			ChannelImpl channel = null;
			try {
				channel = (ChannelImpl)userAgent.getChannel(message.getFrom());
				channel.setPermission(call.getParameters());
			} catch (RepositoryException e) {
				log.error("INTERNAL ERROR: Failed to retrieve the channel from the user agent.", e);
			}

			final Channel ch = channel; // just make compiler happy
			vertxContext.runOnContext(v -> {
				userAgent.onChannelUpdated(ch);
				call.complete(response);
			});
			break;
		}

		case CHANNEL_NAME: {
			RPCRequest<String, Boolean> call = request.cast();
			RPCResponse<Boolean> response = preparsed.map(Boolean.class);

			ChannelImpl channel = null;
			try {
				channel = (ChannelImpl)userAgent.getChannel(message.getFrom());
				channel.setName(call.getParameters());
			} catch (RepositoryException e) {
				log.error("INTERNAL ERROR: Failed to retrieve the channel from the user agent.", e);
			}

			final Channel ch = channel; // just make compiler happy
			vertxContext.runOnContext(v -> {
				userAgent.onChannelUpdated(ch);
				call.complete(response);
			});
			break;
		}

		case CHANNEL_NOTICE: {
			RPCRequest<String, Boolean> call = request.cast();
			RPCResponse<Boolean> response = preparsed.map(Boolean.class);

			ChannelImpl channel = null;
			try {
				channel = (ChannelImpl)userAgent.getChannel(message.getFrom());
				channel.setNotice(call.getParameters());
			} catch (RepositoryException e) {
				log.error("INTERNAL ERROR: Failed to retrieve the channel from the user agent.", e);
			}

			final Channel ch = channel; // just make compiler happy
			vertxContext.runOnContext(v -> {
				userAgent.onChannelUpdated(ch);
				call.complete(response);
			});
			break;
		}

		case CHANNEL_ROLE: {
			RPCRequest<ChannelMemberRole, Boolean> call = request.cast();
			RPCResponse<Boolean> response = preparsed.map(Boolean.class);

			ChannelImpl channel = null;
			try {
				channel = (ChannelImpl)userAgent.getChannel(message.getFrom());
			} catch (RepositoryException e) {
				log.error("INTERNAL ERROR: Failed to retrieve the channel from the user agent.", e);
			}

			Channel.Role role = call.getParameters().getRole();
			List<Channel.Member> changed = mapToMembers(channel, call.getParameters().getMembers());

			// Create a dummy channel object to notify the listener
			// if the channel is not found or can not be retrieved
			Channel ch = channel != null ? channel : ChannelImpl.auto(message.getFrom());
			vertxContext.runOnContext(v -> {
				userAgent.onChannelMembersRoleChanged(ch, changed, role);
				call.complete(response);
			});
			break;
		}

		case CHANNEL_BAN: {
			RPCRequest<List<Id>, Boolean> call = request.cast();
			RPCResponse<Boolean> response = preparsed.map(Boolean.class);

			ChannelImpl channel = null;
			try {
				channel = (ChannelImpl)userAgent.getChannel(message.getFrom());
			} catch (RepositoryException e) {
				log.error("INTERNAL ERROR: Failed to retrieve the channel from the user agent.", e);
			}

			List<Channel.Member> changed = mapToMembers(channel, call.getParameters());

			// Create a dummy channel object to notify the listener
			// if the channel is not found or can not be retrieved
			Channel ch = channel != null ? channel : ChannelImpl.auto(message.getFrom());
			vertxContext.runOnContext(v -> {
				userAgent.onChannelMembersBanned(ch, changed);
				call.complete(response);
			});
			break;
		}

		case CHANNEL_UNBAN: {
			RPCRequest<List<Id>, Boolean> call = request.cast();
			RPCResponse<Boolean> response = preparsed.map(Boolean.class);

			ChannelImpl channel = null;
			try {
				channel = (ChannelImpl)userAgent.getChannel(message.getFrom());
			} catch (RepositoryException e) {
				log.error("INTERNAL ERROR: Failed to retrieve the channel from the user agent.", e);
			}

			List<Channel.Member> changed = mapToMembers(channel, call.getParameters());

			// Create a dummy channel object to notify the listener
			// if the channel is not found or can not be retrieved
			Channel ch = channel != null ? channel : ChannelImpl.auto(message.getFrom());
			vertxContext.runOnContext(v -> {
				userAgent.onChannelMembersUnbanned(ch, changed);
				call.complete(response);
			});
			break;
		}

		case CHANNEL_REMOVE: {
			RPCRequest<List<Id>, Boolean> call = request.cast();
			RPCResponse<Boolean> response = preparsed.map(Boolean.class);

			ChannelImpl channel = null;
			try {
				channel = (ChannelImpl)userAgent.getChannel(message.getFrom());
			} catch (RepositoryException e) {
				log.error("INTERNAL ERROR: Failed to retrieve the channel from the user agent.", e);
			}

			List<Channel.Member> removed = mapToMembers(channel, call.getParameters());

			// Create a dummy channel object to notify the listener
			// if the channel is not found or can not be retrieved
			Channel ch = channel != null ? channel : ChannelImpl.auto(message.getFrom());
			vertxContext.runOnContext(v -> {
				userAgent.onChannelMembersRemoved(ch, removed);
				call.complete(response);
			});
			break;
		}

		default:
			log.error("INTERNAL ERROR: invalid RPC call method {}", request.getMethod().value());
		}
	}

	private List<Channel.Member> mapToMembers(ChannelImpl channel, List<Id> ids) {
		return ids.stream()
				.map(id -> {
					Channel.Member member = null;
					if (channel != null) {
						member = channel.getMember(id);
					}

					return member != null ? member : Channel.Member.unknown(id);
				}).collect(Collectors.toList());
	}

	private Future<Integer> sendRpcRequest(Id recipient, RPCRequest<?, ?> request) {
		MessageBuilderImpl builder = new MessageBuilderImpl(this, Message.Types.CALL);
		builder.to(recipient).body(request);
		MessageImpl message = (MessageImpl)builder.build();

		return sendMessageInternal(message)
			.andThen((ar) -> {
				if (ar.succeeded()) {
					pendingCalls.put(request.getId(), request);
					vertx.setTimer(CALL_TIMEOUT, (tid) -> {
						pendingCalls.remove(request.getId());
						request.failed(new TimeoutException());
					});
				} else {
					request.failed(ar.cause());
				}
			});
	}

	@Override
	public CompletableFuture<Void> updateProfile(String name, boolean avatar) {
		String newName = name == null ? null : Normalizer.normalize(name, Normalizer.Form.NFC);

		Future<Void> future = apiClient.updateProfile(newName, avatar);
		return VertxFuture.of(future);
	}

	@Override
	public CompletableFuture<String> uploadAvatar(String contentType, byte[] avatar) {
		Future<String> future = apiClient.uploadUserAvatar(contentType, null, avatar);
		return VertxFuture.of(future);
	}

	@Override
	public CompletableFuture<String> uploadAvatar(String contentType, String fileName) {
		Future<String> future = apiClient.uploadUserAvatar(contentType, fileName);
		return VertxFuture.of(future);
	}

	@Override
	public CompletableFuture<List<ClientDevice>> getDevices() {
		RPCRequest<Void, List<ClientDevice>> request = new RPCRequest<>(getNextIndex(), RPCMethod.DEVICE_LIST, null);

		vertxContext.runOnContext((v) -> {
			// NOTICE: Send to the device private outbox
			sendRpcRequest(peer.getPeerId(), request);
		});

		return VertxFuture.of(request.getFuture());
	}

	@Override
	public CompletableFuture<Boolean> revokeDevice(Id deviceId) {
		if (deviceId == null)
			return VertxFuture.failedFuture(new NullPointerException("deviceId"));

		RPCRequest<Id, Boolean> request = new RPCRequest<>(
				getNextIndex(), RPCMethod.DEVICE_REVOKE, deviceId);

		vertxContext.runOnContext((v) -> {
			// NOTICE: Send to the device private outbox
			sendRpcRequest(peer.getPeerId(), request);
		});

		return VertxFuture.of(request.getFuture());
	}

	@Override
	public CompletableFuture<Channel> createChannel(Channel.Permission permission, String name, String notice) {
		if (permission == null)
			permission = Channel.Permission.OWNER_INVITE;

		Signature.KeyPair sessionKeyPair = Signature.KeyPair.random();
		Id sessionId = Id.of(sessionKeyPair.publicKey().bytes());
		RPCParameters.ChannelCreate params = new RPCParameters.ChannelCreate(sessionId, permission, name, notice);

		RPCRequest<RPCParameters.ChannelCreate, Channel> request = new RPCRequest<>(
				getNextIndex(), RPCMethod.CHANNEL_CREATE, params);
		request.setCookie(sessionKeyPair, (kp) -> selfEncrypt(kp.privateKey().bytes()));

		vertxContext.runOnContext((v) -> {
			sendRpcRequest(peer.getPeerId(), request);
		});

		return VertxFuture.of(request.getFuture());
	}

	@Override
	public CompletableFuture<Boolean> removeChannel(Id channelId) {
		if (channelId == null)
			return VertxFuture.failedFuture(new NullPointerException("channelId"));

		RPCRequest<Void, Boolean> request = new RPCRequest<>(
				getNextIndex(), RPCMethod.CHANNEL_REMOVE, null);

		vertxContext.runOnContext((v) -> {
			sendRpcRequest(channelId, request);
		});

		return VertxFuture.of(request.getFuture());
	}

	@Override
	public CompletableFuture<Channel> joinChannel(InviteTicket ticket) {
		if (ticket == null)
			return VertxFuture.failedFuture(new NullPointerException("ticket"));

		if (ticket.getSessionKey() == null)
			return VertxFuture.failedFuture(new IllegalArgumentException("ticket not include the session key"));

		if (ticket.isExpired())
			return VertxFuture.failedFuture(new IllegalArgumentException("ticket expired"));

		if (!ticket.isValid(getUserId()))
			return VertxFuture.failedFuture(new IllegalArgumentException("ticket is not valid"));

		byte[] sessionKey = null;
		try {
			// check the private key
			if (ticket.isPublic()) {
				sessionKey = ticket.getSessionKey();
			} else {
				sessionKey = user.decrypt(ticket.getInviter(), ticket.getSessionKey());
			}

			Signature.KeyPair.fromPrivateKey(sessionKey);
		} catch (Exception e) {
			return VertxFuture.failedFuture(new IllegalArgumentException("invalid member private key"));
		}

		RPCRequest<InviteTicket, Channel> request = new RPCRequest<>(
				getNextIndex(), RPCMethod.CHANNEL_JOIN, ticket.proof());
		request.setCookie(sessionKey, this::selfEncrypt);

		vertxContext.runOnContext((v) -> {
			sendRpcRequest(ticket.getChannelId(), request);
		});

		return VertxFuture.of(request.getFuture());
	}

	@Override
	public CompletableFuture<Boolean> leaveChannel(Id channelId) {
		if (channelId == null)
			return VertxFuture.failedFuture(new NullPointerException("channelId"));

		RPCRequest<Void, Boolean> request = new RPCRequest<>(
				getNextIndex(), RPCMethod.CHANNEL_LEAVE, null);

		vertxContext.runOnContext((v) -> {
			sendRpcRequest(channelId, request);
		});

		return VertxFuture.of(request.getFuture());
	}

	@Override
	public CompletableFuture<InviteTicket> createInviteTicket(Id channelId) {
		if (channelId == null)
			return VertxFuture.failedFuture(new NullPointerException("channelId"));

		try {
			InviteTicket ticket = signInviteTicket(channelId, null);
			return VertxFuture.succeededFuture(ticket);
		} catch (Exception e) {
			return VertxFuture.failedFuture(e);
		}

	}

	@Override
	public CompletableFuture<InviteTicket> createInviteTicket(Id channelId, Id invitee) {
		if (channelId == null)
			return VertxFuture.failedFuture(new NullPointerException("channelId"));

		if (invitee == null)
			return VertxFuture.failedFuture(new NullPointerException("invitee"));

		try {
			InviteTicket ticket = signInviteTicket(channelId, invitee);
			return VertxFuture.succeededFuture(ticket);
		} catch (Exception e) {
			return VertxFuture.failedFuture(e);
		}
	}

	private InviteTicket signInviteTicket(Id channelId, Id invitee) throws RepositoryException {
		ChannelImpl channel = (ChannelImpl)userAgent.getChannel(channelId);
		if (channel == null)
			throw new IllegalArgumentException("channel not exists");

		long expire = System.currentTimeMillis() + InviteTicket.DEFAULT_EXPIRATION;

		MessageDigest shasum = Hash.sha256();
		shasum.update(channelId.bytes());
		shasum.update(user.getId().bytes());
		if (invitee == null)
			shasum.update(Id.MAX_ID.bytes());
		else
			shasum.update(invitee.bytes());
		shasum.update(ByteBuffer.allocate(Long.BYTES).putLong(expire).array());

		byte[] sig = user.sign(shasum.digest());

		byte[] sk = channel.getSessionKeyPair().privateKey().bytes();
		if (invitee != null)
			sk = user.encrypt(invitee, sk);

		return new InviteTicket(channelId, user.getId(), invitee == null, expire, sig, sk);
	}

	private Future<Channel> getChannelInfo(Id channelId) {
		if (channelId == null)
			return Future.failedFuture(new NullPointerException("channelId"));

		RPCRequest<Void, Channel> request = new RPCRequest<>(
				getNextIndex(), RPCMethod.CHANNEL_INFO, null);

		vertxContext.runOnContext((v) -> {
			sendRpcRequest(channelId, request);
		});

		return request.getFuture();
	}

	private Future<List<Channel.Member>> getChannelMembers(Id channelId) {
		if (channelId == null)
			return Future.failedFuture(new NullPointerException("channelId"));

		RPCRequest<Void, List<Channel.Member>> request = new RPCRequest<>(
				getNextIndex(), RPCMethod.CHANNEL_MEMBERS, null);

		vertxContext.runOnContext((v) -> {
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
				getNextIndex(), RPCMethod.CHANNEL_OWNER, newOwner);

		vertxContext.runOnContext((v) -> {
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
				getNextIndex(), RPCMethod.CHANNEL_PERMISSION, permission);

		vertxContext.runOnContext((v) -> {
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
				getNextIndex(), RPCMethod.CHANNEL_NAME, name);

		vertxContext.runOnContext((v) -> {
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
				getNextIndex(), RPCMethod.CHANNEL_NOTICE, notice);

		vertxContext.runOnContext((v) -> {
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
				getNextIndex(), RPCMethod.CHANNEL_ROLE, new RPCParameters.ChannelMemberRole(members, role));

		vertxContext.runOnContext((v) -> {
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
				getNextIndex(), RPCMethod.CHANNEL_BAN, members);

		vertxContext.runOnContext((v) -> {
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
				getNextIndex(), RPCMethod.CHANNEL_UNBAN, members);

		vertxContext.runOnContext((v) -> {
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
				getNextIndex(), RPCMethod.CHANNEL_REMOVE, members);

		vertxContext.runOnContext((v) -> {
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
				getNextIndex(), RPCMethod.CONTACT_PUT, contacts);

		vertxContext.runOnContext((v) -> {
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
				getNextIndex(), RPCMethod.CONTACT_REMOVE, contacts);

		vertxContext.runOnContext((v) -> {
			sendRpcRequest(outbox, request);
		});

		return request.getFuture();
	}

	protected CompletableFuture<Void> syncContacts() {
		RPCRequest<Void, List<Contact>> request = new RPCRequest<>(
				getNextIndex(), RPCMethod.CONTACT_SYNC, null);

		vertxContext.runOnContext((v) -> {
			sendRpcRequest(outbox, request);
		});

		return request.getFuture();
	}

	protected CompletableFuture<Void> clearContacts() {
		RPCRequest<Void, String> request = new RPCRequest<>(
				getNextIndex(), RPCMethod.CONTACT_CLEAR, null);

		vertxContext.runOnContext((v) -> {
			sendRpcRequest(outbox, request);
		});

		return request.getFuture();
	}
	*/

	private Future<Integer> sendMessageInternal(MessageImpl message) {
		try {
			int type = message.getMessageType();
			byte[] body = message.getBody();

			MessageImpl encryptedMessage;

			if (body != null && body.length > 0 && !message.getTo().equals(peer.getPeerId())) {
				byte[] encryptedBody;

				if (type == Message.Types.MESSAGE) {
					// The body is encrypted using my private key and the recipient's session public key.
					Contact recipient = userAgent.getContact(message.getTo());
					if (recipient != null && recipient.hasSessionKey()) {
						CryptoContext ctx = recipient.getTxCryptoContext(() -> {
							return user.createCryptoContext(recipient.getSessionId());
						});
						encryptedBody = ctx.encrypt(body);
					} else {
						log.error("Failed to send message to unknow recipient {}", message.getTo());
						throw new UnknownRecipient(message.getTo().toString());
					}
				} else if (type == Message.Types.CALL) {
					// The body is encrypted using my private key and the recipient's public key.
					// TODO: CHEKME - need cache the RPC crypto vertxContext?
					CryptoContext ctx = user.createCryptoContext(message.getTo());
					encryptedBody = ctx.encrypt(body);
				} else {
					log.error("INTERNAL ERROR: Failed to send unsupported message type {}", type);
					throw new  MessagingException("INTERNAL ERROR");
				}

				encryptedMessage = message.dup(encryptedBody);
			} else {
				encryptedMessage = message;
			}

			byte[] payload = serverContext.encrypt(encryptedMessage.serialize());

			return mqttClient.publish(outbox, Buffer.buffer(payload), MqttQoS.AT_LEAST_ONCE, false, false)
				.andThen((ar) -> {
					if (ar.succeeded()) {
						// waiting for PUBACK
						pendingMessages.put(ar.result(), message);
						// Waiting for receive from the outbox
						sendingMessages.put(message.getSerialNumber(), message);
						log.debug("Sent message to {}", message.getTo());
					} else {
						message.failed(ar.cause());
						log.error("Sent message to {} failed", message.getTo(), ar.cause());
					}
				});
		} catch (Exception e) {
			log.error("Send message to {} failed", message.getTo(), e);
			message.failed(e);
			return Future.failedFuture(e);
		}
	}

	protected CompletableFuture<Message> sendMessage(Message message) {
		MessageImpl msg = (MessageImpl)message;
		Promise<Message> promise = msg.initSendPromise();

		vertxContext.runOnContext((v) -> {
			userAgent.onSending(message);
			sendMessageInternal(msg);
		});

		return VertxFuture.of(promise.future());
	}

	private Future<String> pushContactsUpdate(List<Contact> updatedContacts) {
		try {
			String currentVersion = userAgent.getContactsVersion();

			RPCRequest<ContactsUpdate, String> request = new RPCRequest<>(
					getNextIndex(), RPCMethod.CONTACT_PUSH,
					new ContactsUpdate(currentVersion, updatedContacts));

			vertxContext.runOnContext((v) -> {
				userAgent.onContactsUpdating(currentVersion, updatedContacts);
				sendRpcRequest(peer.getPeerId(), request);
			});

			return request.getFuture();
		} catch (Exception e) {
			return Future.failedFuture(e);
		}
	}

	@Override
	public CompletableFuture<Contact> addContact(Id id, Id homePeerId, byte[] sessionKey, String remark) {
		if (id == null)
			return VertxFuture.failedFuture(new NullPointerException("id"));

		if (sessionKey == null)
			return VertxFuture.failedFuture(new NullPointerException("sessionKey"));

		// check the session key is valid
		try {
			Signature.KeyPair.fromPrivateKey(sessionKey);
		} catch (Exception e) {
			return VertxFuture.failedFuture(new IllegalArgumentException("invalid s private key"));
		}

		Contact contact = ContactImpl.create(id, homePeerId, sessionKey, remark);
		return VertxFuture.of(pushContactsUpdate(Arrays.asList(contact)).map(v -> contact));
	}

	@Override
	public CompletableFuture<Contact> getContact(Id id) {
		if (id == null)
			return VertxFuture.failedFuture(new NullPointerException("id"));

		try {
			Contact contact = userAgent.getContact(id);
			return VertxFuture.succeededFuture(contact);
		} catch (RepositoryException e) {
			return VertxFuture.failedFuture(e);
		}
	}

	@Override
	public CompletableFuture<Channel> getChannel(Id id) {
		if (id == null)
			return VertxFuture.failedFuture(new NullPointerException("id"));

		try {
			Channel channel = userAgent.getChannel(id);
			return VertxFuture.succeededFuture(channel);
		} catch (RepositoryException e) {
			return VertxFuture.failedFuture(e);
		}
	}

	@Override
	public CompletableFuture<List<Contact>> getContacts() {
		try {
			List<Contact> Contact = userAgent.getUserContacts();
			return VertxFuture.succeededFuture(Contact);
		} catch (RepositoryException e) {
			return VertxFuture.failedFuture(e);
		}
	}

	@Override
	public CompletableFuture<Contact> updateContact(Contact contact) {
		if (contact == null)
			return VertxFuture.failedFuture(new NullPointerException("contact"));

		if (!contact.isModified())
			return VertxFuture.failedFuture(new IllegalStateException("not modified"));

		return VertxFuture.of(pushContactsUpdate(Arrays.asList(contact)).map(v -> contact));
	}

	@Override
	public CompletableFuture<Void> removeContact(Id id) {
		if (id == null)
			return VertxFuture.failedFuture(new NullPointerException("id"));

		try {
			Contact contact = userAgent.getContact(id);
			if (contact.isAuto()) {
				userAgent.removeContact(id);
				return VertxFuture.succeededFuture();
			}

			if (contact.isDeleted())
				return VertxFuture.succeededFuture();

			contact.setDeleted(true);
			return VertxFuture.of(pushContactsUpdate(Arrays.asList(contact)).map(v -> null));
		} catch (RepositoryException e) {
			return VertxFuture.failedFuture(e);
		}
	}

	@Override
	public CompletableFuture<Void> removeContacts(List<Id> ids) {
		if (ids == null)
			return VertxFuture.failedFuture(new NullPointerException("id"));

		if (ids.isEmpty())
			return VertxFuture.succeededFuture();

		try {
			List<Contact> contacts = userAgent.getContacts(ids);
			List<Id> removeLocally = new ArrayList<>();
			Iterator<Contact> iter = contacts.iterator();
			while (iter.hasNext()) {
				Contact c = iter.next();
				if (c.isAuto()) {
					removeLocally.add(c.getId());
					iter.remove();
				} else if (c.isDeleted()) {
					iter.remove();
				} else {
					c.setDeleted(true);
				}
			}

			if (!removeLocally.isEmpty())
				userAgent.removeContacts(removeLocally);

			return VertxFuture.of(pushContactsUpdate(contacts).map(v -> null));
		} catch (RepositoryException e) {
			return VertxFuture.failedFuture(e);
		}
	}

	private Future<Profile> tryRefreshProfile(Id id) {
		Promise<Profile> promise = Promise.promise();

		vertxContext.runOnContext(v -> {
			log.debug("Fetching the profile {} ...", id);

			apiClient.getProfile(id).onSuccess(profile -> {
				promise.complete(profile);
			}).onFailure(e -> {
				log.error("Failed to fetch the profile {}", id, e);
				promise.fail(e);
			});
		});

		return promise.future();
	}
}
