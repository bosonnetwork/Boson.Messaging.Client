package io.bosonnetwork.messaging;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bosonnetwork.Id;
import io.bosonnetwork.Identity;
import io.bosonnetwork.LookupOption;
import io.bosonnetwork.Network;
import io.bosonnetwork.Node;
import io.bosonnetwork.NodeInfo;
import io.bosonnetwork.PeerInfo;
import io.bosonnetwork.crypto.CryptoIdentity;
import io.bosonnetwork.crypto.Signature.KeyPair;
import io.bosonnetwork.messaging.impl.APIClient.MessagingServiceId;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;

public abstract class ClientBuilder {
	// Vertx options
	protected boolean nativeTransport;

	// User
	protected CryptoIdentity user;	// optional, mandatory when need to register user
	protected String userName;		// optional
	protected String passphrase;	// optional, mandatory when need to register user

	// Device
	protected Identity device;
	protected Node deviceNode;		// either deviceNode or apiURL is mandatory
	protected String deviceName;	// optional, mandatory when need to register device
	protected String appName;		// optional, mandatory when need to register device

	protected boolean registerUserAndDevice;
	protected boolean registerDevice;

	// Handler for device registration(to acquires the user key)
	protected Function<String, Boolean> registrationRequestHandler;

	// Messaging peer information
	protected Id peerId; 					// optional
	protected Id nodeId;					// optional
	protected URL apiURL;					// either deviceNode or apiURL is mandatory

	protected MessagingRepository repository;
	protected Path repositoryDb;

	protected List<ConnectionListener> connectionListeners;
	protected List<ProfileListener> profileListeners;
	protected List<MessageListener> messageListeners;
	protected List<ChannelListener> channelListeners;
	protected List<ContactListener> contactListeners;

	protected UserAgent userAgent;

	private static final Logger log = LoggerFactory.getLogger(ClientBuilder.class);

	protected ClientBuilder() {
		this.connectionListeners = new ArrayList<>();
		this.profileListeners = new ArrayList<>();
		this.messageListeners = new ArrayList<>();
		this.channelListeners = new ArrayList<>();
		this.contactListeners = new ArrayList<>();
	}

	public ClientBuilder nativeTransport(boolean eanbled) {
		this.nativeTransport = eanbled;
		return this;
	}

	public ClientBuilder userKey(KeyPair userKeyPair) {
		Objects.requireNonNull(userKeyPair, "userKeyPair");
		this.user = new CryptoIdentity(userKeyPair);
		return this;
	}

	public ClientBuilder userKey(byte[] userPrivateKey) {
		Objects.requireNonNull(userPrivateKey, "userPrivateKey");
		return userKey(KeyPair.fromPrivateKey(userPrivateKey));
	}

	public ClientBuilder newUserKey() {
		return userKey(KeyPair.random());
	}

	public ClientBuilder userName(String userName) {
		if (userName != null)
			this.userName = Normalizer.normalize(userName, Normalizer.Form.NFC);
		else
			this.userName = null;

		return this;
	}

	public ClientBuilder deviceKey(KeyPair deviceKeyPair) {
		Objects.requireNonNull(deviceKeyPair, "deviceKeyPair");
		this.device = new CryptoIdentity(deviceKeyPair);
		return this;
	}

	public ClientBuilder deviceKey(byte[] devicePrivateKey) {
		Objects.requireNonNull(devicePrivateKey, "devicePrivateKey");
		return deviceKey(KeyPair.fromPrivateKey(devicePrivateKey));
	}

	public ClientBuilder newDeviceKey() {
		return deviceKey(KeyPair.random());
	}

	public ClientBuilder deviceNode(Node node) {
		Objects.requireNonNull(node, "node");
		this.deviceNode = node;
		this.device = node;
		return this;
	}

	public ClientBuilder deviceName(String deviceName) {
		Objects.requireNonNull(deviceName, "deviceName");
		this.deviceName = Normalizer.normalize(deviceName, Normalizer.Form.NFC);
		return this;
	}

	public ClientBuilder appName(String appName) {
		Objects.requireNonNull(appName, "appName");
		this.appName = Normalizer.normalize(appName, Normalizer.Form.NFC);
		return this;
	}

	public ClientBuilder registerUserAndDevice(String passphrase) {
		Objects.requireNonNull(passphrase, "passphrase");
		this.passphrase = Normalizer.normalize(passphrase, Normalizer.Form.NFC);;
		this.registerUserAndDevice = true;
		return this;
	}

	public ClientBuilder registerDevice(String passphrase) {
		Objects.requireNonNull(passphrase, "passphrase");
		this.passphrase = Normalizer.normalize(passphrase, Normalizer.Form.NFC);;
		this.registerDevice = true;
		return this;
	}

	public ClientBuilder regiesterDevice(Function<String, Boolean> registrationRequestHandler) {
		Objects.requireNonNull(registrationRequestHandler, "registrationRequestHandler");
		this.registrationRequestHandler = registrationRequestHandler;
		this.registerDevice = true;
		return this;
	}

	public ClientBuilder peerId(Id peerId) {
		Objects.requireNonNull(peerId, "peerId");
		this.peerId = peerId;
		return this;
	}

	public ClientBuilder nodeId(Id nodeId) {
		Objects.requireNonNull(nodeId, "nodeId");
		this.nodeId = nodeId;
		return this;
	}

	public ClientBuilder apiURL(String apiURL) {
		Objects.requireNonNull(apiURL, "apiURL");
		try {
			this.apiURL = new URL(apiURL);
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException(e);
		}

		return this;
	}

	public ClientBuilder apiURL(URL apiURL) {
		Objects.requireNonNull(apiURL, "apiURL");
		this.apiURL = apiURL;
		return this;
	}

	public ClientBuilder messagingRepository(MessagingRepository repository) {
		Objects.requireNonNull(repository, "repository");
		this.repository = repository;
		return this;
	}

	public ClientBuilder messagingRepository(Path repository) {
		Objects.requireNonNull(repository, "repository");
		this.repositoryDb = repository;
		return this;
	}

	public ClientBuilder connectionListeners(List<ConnectionListener> connectionListeners) {
		Objects.requireNonNull(connectionListeners, "connectionListeners");
		this.connectionListeners.addAll(connectionListeners);
		return this;
	}

	public ClientBuilder connectionListener(ConnectionListener connectionListener) {
		Objects.requireNonNull(connectionListener, "connectionListener");
		this.connectionListeners.add(connectionListener);
		return this;
	}

	public ClientBuilder profileListeners(List<ProfileListener> profileListeners) {
		Objects.requireNonNull(profileListeners, "profileListeners");
		this.profileListeners.addAll(profileListeners);
		return this;
	}

	public ClientBuilder profileListener(ProfileListener profileListener) {
		Objects.requireNonNull(profileListener, "profileListener");
		this.profileListeners.add(profileListener);
		return this;
	}

	public ClientBuilder messageListeners(List<MessageListener> messageListeners) {
		Objects.requireNonNull(messageListeners, "messageListeners");
		this.messageListeners.addAll(messageListeners);
		return this;
	}

	public ClientBuilder messageListener(MessageListener messageListener) {
		Objects.requireNonNull(messageListener, "messageListener");
		this.messageListeners.add(messageListener);
		return this;
	}

	public ClientBuilder channelListeners(List<ChannelListener> channelListeners) {
		Objects.requireNonNull(channelListeners, "channelListeners");
		this.channelListeners.addAll(channelListeners);
		return this;
	}

	public ClientBuilder channelListener(ChannelListener channelListener) {
		Objects.requireNonNull(channelListener, "channelListener");
		this.channelListeners.add(channelListener);
		return this;
	}

	public ClientBuilder contactListeners(List<ContactListener> contactListeners) {
		Objects.requireNonNull(contactListeners, "contactListeners");
		this.contactListeners.addAll(contactListeners);
		return this;
	}

	public ClientBuilder contactListener(ContactListener contactListener) {
		Objects.requireNonNull(contactListener, "contactListener");
		this.contactListeners.add(contactListener);
		return this;
	}

	public ClientBuilder userAgent(UserAgent userAgent) {
		this.userAgent = userAgent;
		return this;
	}

	// will update the nodeId is not set or check the nodeId if set
	protected Future<List<URL>> lookupPeer() {
		if (deviceNode == null || peerId == null)
			throw new IllegalStateException("node or peerId not set");

		log.info("Looking up peer {} ...", peerId);

		return Future.fromCompletionStage(deviceNode.findPeer(peerId, 0, LookupOption.ARBITRARY)).map((pil) -> {
			if (pil.isEmpty()) {
					log.error("Peer not found {}", peerId);
					throw new CompletionException("Peer not found: " + peerId, null);
				}

				PeerInfo pi = pil.get(0);
				if (nodeId == null)
					nodeId = pi.getNodeId();
				else {
					if (!nodeId.equals(pi.getNodeId())) {
						log.error("Peer node id does not match the expected node id.");
						throw new CompletionException("Peer node id does not match the expected node id.", null);
					}
				}

				log.info("Found peer {}, node id: {}", peerId, pi.getNodeId());
				return pi;
			}).compose((pi) -> {
				log.info("Looking up node {} ...", pi.getNodeId());

				return Future.fromCompletionStage(deviceNode.findNode(pi.getNodeId(), LookupOption.ARBITRARY)).map((r) -> {
					NodeInfo ni = r.get(Network.IPv4);
					if (ni == null) {
						log.error("Node not found {}", pi.getNodeId());
						throw new CompletionException("Node not found: " + pi.getNodeId(), null);
					}

					List<URL> urls = new ArrayList<>(3);
					try {
						if (pi.hasAlternativeURL())
							urls.add(new URL(pi.getAlternativeURL()));

						urls.add(new URL("https", ni.getAddress().getHostString(), pi.getPort(), "/"));
						urls.add(new URL("http",  ni.getAddress().getHostString(), pi.getPort(), "/"));
					} catch (MalformedURLException e) {
						log.error("Invalid messaging peer URL", e);
						throw new CompletionException("Invalid messaging peer URL", e);
					}

					log.info("Found node {}", pi.getNodeId());
					return urls;
				});
			});
	}

	protected abstract Future<MessagingServiceId> getServiceIds(URL url);

	// Should not return a failed Future.
	// If the attempt fails, it should return a Future completed with null.
	protected Future<MessagingServiceId> attemptServiceCheck(URL url) {
		log.info("Checking peer service {} at {} ...", peerId, url);

		return getServiceIds(url).otherwise(e -> {
			log.info("{} - Check the service error: {}", url, e.getMessage());
			return null;
		});
	}

	protected Future<MessagingPeerInfo> attemptServiceCheck(List<URL> urls) {
		List<Future<MessagingServiceId>> futures = urls.stream().map(url -> attemptServiceCheck(url))
				.collect(Collectors.toList());

		return Future.all(futures).map(ar -> {
			// always succeeded AsyncResult
			CompositeFuture cf = ar.result();

			for (int i = 0; i < cf.size(); i++) {
				MessagingServiceId ids = cf.resultAt(i);
				URL url = urls.get(i);
				if (ids != null) {
					if (!ids.peerId().equals(peerId)) {
						log.error("{} - Mismatched peer id, expected {}, got {}", url, peerId, ids.peerId());
						continue;
					}

					if (nodeId != null && !ids.nodeId().equals(nodeId)) {
						log.error("{} - Mismatched node id, expected {}, got {}", url, nodeId, ids.nodeId());
						continue;
					}

					log.info("Messaging service {} default api URL {}", peerId, url);

					return MessagingPeerInfo.of(ids.peerId(), ids.nodeId(), url);
				}
			}

			log.error("Messaging service {} not available, all attempts failed", peerId);
			throw new CompletionException(new PeerNotAvailable(peerId.toBase58String()));
		});
	}

	// Acceptable inputs:
	//
	// - Preconfigured client: user and device are registered, qualified messaging peer information
	//   1. UserAgent
	//   2. MessageingRepository instance or path, configuration information included
	//
	// - Build from scratch
	//   - User already registered, and have the user's private key
	//     3. userKey, deviceKey, deviceName, appName, peerId, apiURL, passphrase(registerDevice)
	//     4. userKey, deviceNode, deviceName, appName, peerId, apiURL[optional] passphrase(registerDevice)
	//
	//   - User already registered, and have another device with the user signed-in
	//     5. deviceKey, deviceName, appName, peerId, apiURL, registrationRequestHandler
	//     6. deviceNode, deviceName, appName, peerId, apiURL[optional], registrationRequestHandler
	//
	//   - New user and device
	//     7. userKey, userName[optional], deviceKey, deviceName, appName, peerId, apiURL, passphrase(registerUserAndDevice)
	//     8. userKey, userName[optional], deviceNode, deviceName, appName, peerId, apiURL[optional] passphrase(registerUserAndDevice)
	//
	// Build priority:
	// 1. the custom userAgent
	// 2. DefaultUserAgent with the custom MessagingRepository instance
	// 3. DefaultUserAgent with the default MessagingRepository implementation
	//
	protected void eligibleCheck() {
		if (userAgent != null)
			return; // assume the userAgent is configured

		if (repository == null && repositoryDb == null)
			throw new IllegalStateException("messaging repository is not configured");

		boolean deviceCheck = false;
		boolean peerCheck = false;

		if (registerUserAndDevice) {
			if (user == null)
				throw new IllegalStateException("user key is not configured");

			if (passphrase == null)
				throw new IllegalStateException("passphrase is not configured");

			deviceCheck = true;
			peerCheck = true;
		}

		if (registerDevice || deviceCheck) {
			if (device == null)
				throw new IllegalStateException("device key or node is not configured");

			if (deviceName == null)
				throw new IllegalStateException("device name is not configured");

			if (appName == null)
				throw new IllegalStateException("app name is not configured");

			if (user != null && passphrase == null)
				throw new IllegalStateException("passphrase is not configured");

			if (user == null && registrationRequestHandler == null)
				throw new IllegalStateException("registration request handler is not configured");

			peerCheck = true;
		}

		if (peerCheck) {
			// peer info is mandatory
			if (peerId == null)
				throw new IllegalStateException("peer id is not configured");

			if (deviceNode == null && apiURL == null)
				throw new IllegalStateException("api URL is not configured");
		} else {
			// peer info is optional
			if (peerId != null) {
				if (deviceNode == null && apiURL == null)
					throw new IllegalStateException("api URL is not configured");
			} else {
				if (apiURL != null)
					throw new IllegalStateException("peer id is not configured");
			}
		}
	}

	public abstract CompletableFuture<MessagingClient> build();
}
