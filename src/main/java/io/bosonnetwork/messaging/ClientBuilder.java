package io.bosonnetwork.messaging;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bosonnetwork.BosonException;
import io.bosonnetwork.Id;
import io.bosonnetwork.Identity;
import io.bosonnetwork.LookupOption;
import io.bosonnetwork.Network;
import io.bosonnetwork.Node;
import io.bosonnetwork.NodeInfo;
import io.bosonnetwork.PeerInfo;
import io.bosonnetwork.crypto.CryptoBox.Nonce;
import io.bosonnetwork.crypto.CryptoIdentity;
import io.bosonnetwork.crypto.Signature.KeyPair;
import io.bosonnetwork.utils.Base58;
import io.bosonnetwork.utils.Hex;
import io.bosonnetwork.utils.ThreadLocals;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

public class ClientBuilder {
	private static final Charset UTF8 = Charset.forName("UTF-8");

	private Vertx vertx;

	// Messaging API service URL
	private URL apiURL;

	private Node node;

	// Messaging peer info
	private Id peerId;
	private int tcpPort;
	private int sslPort;
	private String sslCert;

	// Device(node) and user identities
	private	Identity user;
	private Identity device;

	// Messaging client listener
	private MessageListener listener;

	private boolean registerDevice;
	private String deviceName;
	private String appName;
	private Function<String, CompletableFuture<String>> registrationRequestHandler;

	private Map<String, Object> exportConfig;

	private WebClient webClient;

	private static final Logger log = LoggerFactory.getLogger(ClientBuilder.class);

	protected ClientBuilder() {
		VertxOptions options = new VertxOptions();
		options.setPreferNativeTransport(true);
		// options.setBlockedThreadCheckIntervalUnit(TimeUnit.SECONDS);
		// options.setBlockedThreadCheckInterval(300);
		this.vertx = Vertx.vertx(options);
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

	public ClientBuilder randomUserKey() {
		return userKey(KeyPair.random());
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

	public ClientBuilder randomDeviceKey() {
		return deviceKey(KeyPair.random());
	}

	public ClientBuilder deviceNode(Node node) {
		Objects.requireNonNull(node, "node");
		this.node = node;
		this.device = node;
		return this;
	}

	public ClientBuilder peerId(Id peerId) {
		Objects.requireNonNull(peerId, "peerId");
		this.peerId = peerId;
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

	public ClientBuilder regiesterDevice(String deviceName, String appName) {
		Objects.requireNonNull(deviceName, "deviceName");
		Objects.requireNonNull(appName, "appName");

		this.deviceName = Normalizer.normalize(deviceName, Normalizer.Form.NFC);
		this.appName = Normalizer.normalize(appName, Normalizer.Form.NFC);
		this.registerDevice = true;
		return this;
	}

	// The function will get the registration id, and return a {@code CompletableFuture}
	// to notify the {@code ClientBuilder} the registration request already handled by the user
	public ClientBuilder regiesterationRequestHandler(Function<String, CompletableFuture<String>> handler) {
		Objects.requireNonNull(registrationRequestHandler, "registrationRequestHandler");
		this.registrationRequestHandler = handler;
		return this;
	}

	// export config if the build the messaging client successfully
	public ClientBuilder exportConfig(Map<String, Object> config) {
		Objects.requireNonNull(config, "config");
		this.exportConfig = config;
		return this;
	}

	public ClientBuilder config(Map<String, Object> config) {
		Objects.requireNonNull(config);
		JsonObject json = new JsonObject(config);

		try {
			String v = json.getString("userPrivateKey");
			if (v == null)
				throw new IllegalArgumentException("Missing user key");
			KeyPair keyPair = KeyPair.fromPrivateKey(v.startsWith("0x") ? Hex.decode(v) : Base58.decode(v));
			user = new CryptoIdentity(keyPair);

			v = json.getString("devicePrivateKey");
			if (v != null) {
				keyPair = KeyPair.fromPrivateKey(v.startsWith("0x") ? Hex.decode(v) : Base58.decode(v));
				device = new CryptoIdentity(keyPair);
			}

			v = json.getString("apiURL");
			if (v == null)
				throw new IllegalArgumentException("Missing API service URL");
			this.apiURL = new URL(v);

			v = json.getString("peerId");
			if (v == null)
				throw new IllegalArgumentException("Missing peer id");
			this.peerId = Id.of(v);

			this.sslPort = json.getInteger("sslPort", 0);
			this.tcpPort = json.getInteger("tcpPort", 0);
			if (this.sslPort == 0 && this.tcpPort == 0)
				throw new IllegalArgumentException("Missing messaging service port ");

			this.sslCert = json.getString("sslCert");
		} catch (Exception e) {
			throw new IllegalArgumentException("config", e);
		}

		return this;
	}

	private void saveConfig() {
		if (exportConfig == null)
			return;

		exportConfig.put("userPrivateKey", Base58.encode(
				((CryptoIdentity)user).getKeyPair().privateKey().bytes()));

		if (device instanceof CryptoIdentity)
			exportConfig.put("devicePrivateKey", Base58.encode(
					((CryptoIdentity)device).getKeyPair().privateKey().bytes()));

		exportConfig.put("apiURL", apiURL.toString());
		exportConfig.put("peerId", peerId.toBase58String());
		exportConfig.put("sslPort", sslPort);
		exportConfig.put("tcpPort",tcpPort);
		if (sslCert != null)
			exportConfig.put("sslCert", sslCert);
	}

	public ClientBuilder listener(MessageListener listener) {
		this.listener = listener;
		return this;
	}

	private Future<List<URL>> lookupPeer(Id peerId) {
		return Future.fromCompletionStage(node.findPeer(peerId, 0, LookupOption.ARBITRARY))
				.map((pil) -> {
					if (pil.isEmpty()) {
						log.error("Can not found peer {}", peerId);
						throw new CompletionException("Can not find peerId for federation: " + peerId, null);
					}

					PeerInfo pi = pil.get(0);
					return pi;
				})
				.compose((pi) -> {
					return Future.fromCompletionStage(node.findNode(pi.getNodeId(), LookupOption.ARBITRARY))
							.map((r) -> {
								NodeInfo ni = r.get(Network.IPv4);
								if (ni == null) {
									log.error("Can not found node {}", pi.getNodeId());
									throw new CompletionException("Can not found node " + pi.getNodeId(), null);
								}

								List<URL> urls = new ArrayList<>(3);

								try {
									if (pi.hasAlternativeURL())
										urls.add(new URL(pi.getAlternativeURL()));

									urls.add(new URL("https", ni.getAddress().getHostString(), pi.getPort(), "/"));
									urls.add(new URL("http",  ni.getAddress().getHostString(), pi.getPort(), "/"));
								} catch (MalformedURLException e) {
									throw new CompletionException("Invalid messaging peer info", e);
								}

								return urls;
							});

				});
	}

	private WebClient getWebClient() {
		if (webClient == null) {
			WebClientOptions opts = new WebClientOptions()
					.setSsl(false)
					.setDefaultHost(apiURL.getHost())
					.setDefaultPort(apiURL.getPort() > 0 ? apiURL.getPort() : apiURL.getDefaultPort())
					.setProtocolVersion(HttpVersion.HTTP_1_1);

			webClient = WebClient.create(vertx, opts);
		}

		return webClient;
	}

	private String generateAuthorization(Identity identity, Function<byte[], byte[]> checksum) {
		Nonce nonce = Nonce.random();
		byte[] digest = checksum.apply(nonce.bytes());
		byte[] sig = identity.sign(digest);

		return "Bearer " + Base58.encode(identity.getId().bytes()) + ":" + Base58.encode(nonce.bytes()) +
				":" + Base58.encode(sig);
	}

	private void parseServiceInfo(JsonObject json) {
		try {
			String v = json.getString("peerId");
			if (v == null)
				throw new IllegalArgumentException("missing peer id");
			this.peerId = Id.of(v);

			this.sslPort = json.getInteger("sslPort", 0);
			this.tcpPort = json.getInteger("tcpPort", 0);
			if (this.sslPort == 0 && this.tcpPort == 0)
				throw new IllegalArgumentException("Missing messaging service port ");

			this.sslCert = json.getString("sslCert");
		} catch (Exception e) {
			log.error("Got invalid service info.");
			throw new CompletionException("Invalid service info", e);
		}
	}

	private Future<URL> attemptServiceCheck(List<URL> urls, int index) {
		if (index >= urls.size())
            return Future.failedFuture("No more candidate URLs to attempt");

		Promise<URL> promise = Promise.promise();

		URL url = urls.get(index);
		WebClientOptions opts = new WebClientOptions()
				.setSsl(url.getProtocol().equals("https"))
				.setDefaultHost(url.getHost())
				.setDefaultPort(url.getPort() > 0 ? url.getPort() : url.getDefaultPort())
				.setProtocolVersion(HttpVersion.HTTP_1_1);

		WebClient webClient = WebClient.create(vertx, opts);
		webClient.get("/id").send()
			.andThen((ar) -> {
				if (ar.succeeded()) {
					HttpResponse<Buffer> res = ar.result();
					if (res.statusCode() == 200) {
						try {
							JsonObject json = res.bodyAsJsonObject();
							Id pid = Id.of(json.getString("peer"));
							if (!pid.equals(peerId))
								promise.fail("Mismatched peer id");
							else
								promise.complete(url);
						} catch (Exception e) {
							promise.fail(e);
						}
					} else {
						promise.fail("HTTP status: " + res.statusCode());
					}
				} else {
					attemptServiceCheck(urls, index + 1).onComplete(promise);
				}

				webClient.close();
			});

		return promise.future();
	}

	private Future<Void> registerDevice() {
		String authHeader = generateAuthorization(user, (nonce) -> {
			var shasum = ThreadLocals.sha256();
			shasum.reset();
			shasum.update(nonce);
			shasum.update(device.getId().bytes());
			shasum.update(deviceName.getBytes(UTF8));
			shasum.update(appName.getBytes(UTF8));
			return shasum.digest();
		});

		var body = JsonObject.of(
				"deviceId", device.getId().toBase58String(),
				"deviceName", deviceName,
				"appName", appName);

		log.info("Registering deivce {} with user {} ...",
				device.getId(), user.getId());

		return getWebClient().post("/devices")
			.putHeader("content-type", "application/json")
			.putHeader("authorization", authHeader)
			.sendJsonObject(body)
			.map((res) -> {
				if (res.statusCode() == 201) {
					var resBody = res.bodyAsJsonObject();
					JsonObject serviceInfo = resBody.getJsonObject("serviceInfo");

					log.info("Got service info: {}", serviceInfo);
					parseServiceInfo(serviceInfo);
					log.info("Registered device {}", device.getId());
					return null;
				} else {
					log.error("Register device failed, HTTP status: {}", res.statusCode());
					throw new CompletionException("HTTP error, status " + res.statusCode(), null);
				}
			});
	}

	private Future<String> registerDeviceRequest() {
		String authHeader = generateAuthorization(device, (nonce) -> {
			var shasum = ThreadLocals.sha256();
			shasum.reset();
			shasum.update(nonce);
			shasum.update(deviceName.getBytes(UTF8));
			shasum.update(appName.getBytes(UTF8));
			return shasum.digest();
		});

		var body = JsonObject.of(
				"deviceName", deviceName,
				"appName", appName);

		log.info("Registering deivce {} ...", device.getId());

		return getWebClient().post("/registrations")
			.putHeader("content-type", "application/json")
			.putHeader("authorization", authHeader)
			.sendJsonObject(body)
			.map((res) -> {
				if (res.statusCode() == 201) {
					JsonObject resBody = res.bodyAsJsonObject();
					String registrationId = resBody.getString("registrationId");
					log.info("Registration id: {}", registrationId);
					return registrationId;
				} else {
					log.error("Register device request failed, HTTP status: {}", res.statusCode());
					throw new CompletionException("HTTP error, status " + res.statusCode(), null);
				}
			});
	}

	private void parseUserKey(JsonObject json) {
		try {
			Id userId = Id.of(json.getString("userId"));
			byte[] sk = Base58.decode(json.getString("userPrivateKey"));
			sk = device.decrypt(userId, sk);

			KeyPair keyPair = KeyPair.fromPrivateKey(sk);
			if (!Arrays.equals(userId.bytes(), keyPair.publicKey().bytes()))
				throw new IllegalArgumentException("Invalid register confirmation, user id and private key mismatch");

			this.user = new CryptoIdentity(keyPair);
		} catch (BosonException e) {
			log.error("Invalid confirmation for registration, wrong private key");
			throw new CompletionException("Invalid register confirmation, wrong private key", e);
		}
	}

	private Future<Void> registerDeviceWithRegistrationId(String registrationId) {
		String authHeader = generateAuthorization(device, (nonce) -> {
			var shasum = ThreadLocals.sha256();
			shasum.reset();
			shasum.update(nonce);
			shasum.update(registrationId.getBytes(UTF8));
			return shasum.digest();
		});

		var body = JsonObject.of("registrationId", registrationId);

		log.info("Registering deivce {} with registration id {} ...",
				device.getClass(), registrationId);

		return getWebClient().post("/devices")
			.putHeader("content-type", "application/json")
			.putHeader("authorization", authHeader)
			.sendJsonObject(body)
			.map((res) -> {
				if (res.statusCode() == 201) {
					var resBody = res.bodyAsJsonObject();
					log.debug("Registration got confirmed: {}", resBody);

					parseUserKey(resBody);
					JsonObject serviceInfo = resBody.getJsonObject("serviceInfo");
					log.info("Got service info: {}", serviceInfo);
					parseServiceInfo(serviceInfo);
					log.info("Registered device {}", device.getId());
					return null;
				} else {
					log.error("Register device(registraionId:{}) failed, HTTP status: {}", registrationId, res.statusCode());
					throw new CompletionException("HTTP error, status " + res.statusCode(), null);
				}
			});
	}

	private CompletableFuture<Void> getServerInfo() {
		var future = new CompletableFuture<Void>();
		var httpClient = getWebClient();

		String authHeader = generateAuthorization(device, (nonce) -> {
			var shasum = ThreadLocals.sha256();
			shasum.reset();
			shasum.update(nonce);
			return shasum.digest();
		});

		httpClient.get("/service/info")
			.putHeader("authorization", authHeader)
			.send()
			.onSuccess(res -> {
				if (res.statusCode() == 200) {
					JsonObject body = res.bodyAsJsonObject();
					JsonObject serviceInfo = body.getJsonObject("serviceInfo");
					log.info("Got service info: {}", serviceInfo);
					future.complete(null);
				} else {
					throw new CompletionException("HTTP error, status " + res.statusCode(), null);
				}
			}).onFailure(e -> {
				log.error("Failed to get server endpoints", e);
				future.completeExceptionally(e);
			});

		return future;
	}

	private void eligibleCheck() {
		if (device == null)
			throw new IllegalStateException("missing the device key or device node");

		if (registerDevice) {
			if (node != null) {
				if (peerId == null)
					throw new IllegalStateException("missing the peer id");
			} else {
				if (apiURL == null)
					throw new IllegalStateException("missing the API service URL");
			}

			if (user == null && registrationRequestHandler == null)
				throw new IllegalStateException("missing user key or registration request handler");

			if (user != null && registrationRequestHandler != null)
				log.warn("The registration request handler will be ignored, already has user key");
		} else {
			if (user == null)
				throw new IllegalStateException("missing the user key");

			if (peerId == null || (tcpPort == 0 && sslPort == 0)) // missing the service info
				throw new IllegalStateException("missing service info");
		}
	}

	public CompletableFuture<MessagingClient> build() {
		try {
			eligibleCheck();
		} catch (Exception e) {
			return CompletableFuture.failedFuture(e);
		}

		Future<Void> future;
		if (registerDevice) {
			Future<List<URL>> lookup;
			if (node != null)
				lookup = lookupPeer(peerId);
			else
				lookup = Future.succeededFuture(List.of(apiURL));

			future = lookup.compose((urls) -> {
				return attemptServiceCheck(urls, 0);
			}).map((url) -> {
				this.apiURL = url;
				return null;
			});

			if (user != null) {
				future = future.compose((v) -> registerDevice());
			} else {
				future = future.compose((v) -> registerDeviceRequest())
					.compose((rid) -> {
						// Convert CompletableFuture to Vert.x Future
						Promise<String> promise = Promise.promise();
						registrationRequestHandler
							.apply(rid)
							.thenAccept((v) -> promise.complete(rid))
							.exceptionally((e) -> {
								promise.fail(e);
								return null;
							});

						return promise.future();
					})
					.compose(this::registerDeviceWithRegistrationId);
			}
		} else {
			future = Future.succeededFuture();
		}

		CompletableFuture<MessagingClient> f = new CompletableFuture<>();
		future.map((v) -> {
			try {
				MessagingClientImpl client = new MessagingClientImpl(vertx, user, device, peerId,
						apiURL.getHost(), tcpPort, sslPort, sslCert, listener);
				saveConfig();
				return client;
			} catch (MessagingException e) {
				log.error("Failed to create the messaging client", e);
				throw new CompletionException("Failed to create the messaging client", e);
			}
		})
		.compose((client) -> {
			return vertx.deployVerticle(client).map(id -> client);
		})
		.onSuccess((c) -> f.complete(c))
		.onFailure((e) -> f.completeExceptionally(e));

		return f;
	}
}
