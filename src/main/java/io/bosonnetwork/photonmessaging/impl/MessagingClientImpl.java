package io.bosonnetwork.photonmessaging.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.mqtt.MqttClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bosonnetwork.Id;
import io.bosonnetwork.Node;
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
import io.bosonnetwork.photonmessaging.MessagingClient;
import io.bosonnetwork.photonmessaging.SessionInfo;
import io.bosonnetwork.vertx.BosonVerticle;
import io.bosonnetwork.vertx.VertxFuture;

public class MessagingClientImpl extends BosonVerticle implements MessagingClient {
	private final Vertx providedVertx;
	private final Node node;
	private final Configuration config;

	private final CryptoIdentity userIdentity;
	private final CryptoIdentity deviceIdentity;
	private final Id homePeerId;

	private String serviceHost;
	private int servicePort;
	private boolean ssl;

	private MqttClient mqttClient;

	private MessagingRepository repository;

	private ConcurrentMap<Id, ConversationImpl> conversations;
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

		connected = false;
		running = false;
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
	public boolean addConnectionListener(ConnectionListener connectionListener) {
		return false;
	}

	@Override
	public boolean removeConnectionListener(ConnectionListener connectionListener) {
		return false;
	}

	@Override
	public boolean addMessageListener(MessageListener messageListener) {
		return false;
	}

	@Override
	public boolean removeMessageListener(MessageListener messageListener) {
		return false;
	}

	@Override
	public boolean addChannelListener(ChannelListener channelListener) {
		return false;
	}

	@Override
	public boolean removeChannelListener(ChannelListener channelListener) {
		return false;
	}

	@Override
	public boolean addContactListener(ContactListener contactListener) {
		return false;
	}

	@Override
	public boolean removeContactListener(ContactListener contactListener) {
		return false;
	}

	private Future<Void> resolvePeer() {
		if (config.getServiceHost() == null || config.getServicePort() == 0) {
			log.info("Looking up service peer {} ...", config.getServicePeerId());
			return Future.fromCompletionStage(node.findPeer(config.getServicePeerId())).compose(peer -> {
				if (peer == null) {
					log.error("Service peer not found {}", config.getServicePeerId());
					return Future.failedFuture("Service peer not found: " + config.getServicePeerId());
				}

				URI uri = URI.create(peer.getEndpoint());
				if (uri.getPort() <= 0) {
					log.error("Service peer endpoint {} is invalid", peer.getEndpoint());
					return Future.failedFuture("Service peer endpoint is invalid: " + peer.getEndpoint());
				}

				if (uri.getScheme().equals("mqtt")) {
					ssl = false;
				} else if (uri.getScheme().equals("mqtts")) {
					ssl = true;
				} else {
					log.error("Service peer endpoint {} is invalid", peer.getEndpoint());
					return Future.failedFuture("Service peer endpoint is invalid: " + peer.getEndpoint());
				}

				serviceHost = uri.getHost();
				servicePort = uri.getPort();
				return Future.succeededFuture();
			});
		} else {
			return Future.succeededFuture();
		}
	}

	@Override
	public VertxFuture<Void> start() {
		Future<Void> deployFuture = resolvePeer().compose(v ->
				providedVertx.deployVerticle(this).andThen(ar -> {
					if (ar.failed())
						close();
				}).mapEmpty()
		);

		return VertxFuture.of(deployFuture);
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
		runningCheck();
		return VertxFuture.succeededFuture(conversations.get(conversationId));
	}

	@Override
	public VertxFuture<List<Conversation>> getConversations() {
		runningCheck();
		return VertxFuture.succeededFuture(new ArrayList<>(conversations.values()));
	}

	@Override
	public VertxFuture<Boolean> removeConversations(Collection<Id> conversationIds) {
		runningCheck();
		return VertxFuture.of(repository.removeConversations(conversationIds).onSuccess(v -> {
			conversationIds.forEach(conversations::remove);
		}));
	}

	@Override
	public VertxFuture<List<Message>> getMessages(Id conversationId, long since, int limit, int offset) {
		runningCheck();
		return VertxFuture.of(repository.getMessages(conversationId, since, limit, offset));
	}

	@Override
	public VertxFuture<List<Message>> getMessages(Id conversationId, long begin, long end) {
		runningCheck();
		return VertxFuture.of(repository.getMessages(conversationId, begin, end));
	}

	@Override
	public VertxFuture<Boolean> removeMessages(Collection<Long> messageIds) {
		runningCheck();
		return VertxFuture.of(repository.removeMessages(messageIds));
	}

	@Override
	public VertxFuture<Boolean> removeMessages(Id conversionId) {
		runningCheck();
		return VertxFuture.of(repository.removeMessages(conversionId));
	}

	@Override
	public VertxFuture<List<SessionInfo>> getSessions() {
		runningCheck();
		Promise<List<SessionInfo>> promise = Promise.promise();
		runOnContext(v -> {

		});

		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Boolean> revokeSession(Id deviceId) {
		runningCheck();
		Promise<Boolean> promise = Promise.promise();
		runOnContext(v -> {

		});

		return VertxFuture.of(promise.future());
	}

	@Override
	public VertxFuture<Contact> addFriend(Id id, byte[] sessionKey, String remark) {
		return null;
	}

	@Override
	public VertxFuture<Channel> createChannel(Channel.Permission permission, String name, String notice) {
		return null;
	}

	@Override
	public VertxFuture<Boolean> removeChannel(Id channeId) {
		return null;
	}

	@Override
	public VertxFuture<Channel> joinChannel(InviteTicket ticket) {
		return null;
	}

	@Override
	public VertxFuture<Boolean> leaveChannel(Id channeId) {
		return null;
	}

	@Override
	public VertxFuture<InviteTicket> createInviteTicket(Id channelId) {
		return null;
	}

	@Override
	public VertxFuture<InviteTicket> createInviteTicket(Id channelId, Id invitee) {
		return null;
	}

	@Override
	public VertxFuture<Boolean> transferChannelOwnership(Id channelId, Id newOwner) {
		return null;
	}

	@Override
	public VertxFuture<Boolean> rotateChannelSessionKey(Id channelId, Signature.KeyPair sessionKey) {
		return null;
	}

	@Override
	public VertxFuture<Boolean> updateChannelInfo(Channel channel) {
		return null;
	}

	@Override
	public VertxFuture<Boolean> setChannelMembersRole(Id channelId, List<Id> members, Channel.Role role) {
		return null;
	}

	@Override
	public VertxFuture<Boolean> banChannelMembers(Id channelId, List<Id> members) {
		return null;
	}

	@Override
	public VertxFuture<Boolean> unbanChannelMembers(Id channelId, List<Id> members) {
		return null;
	}

	@Override
	public VertxFuture<Boolean> removeChannelMembers(Id channelId, List<Id> members) {
		return null;
	}

	@Override
	public VertxFuture<Contact> getContact(Id id) {
		return null;
	}

	@Override
	public VertxFuture<List<Contact>> getContacts() {
		return null;
	}

	@Override
	public VertxFuture<Boolean> updateContact(Contact contact) {
		return null;
	}

	@Override
	public VertxFuture<Boolean> removeContacts(List<Id> ids) {
		return null;
	}

	@Override
	public VertxFuture<Boolean> clearContacts() {
		return null;
	}

	private void close() {}

	@Override
	protected Future<Void> deploy() {
		return null;
	}

	@Override
	protected Future<Void> undeploy() {
		return null;
	}
}