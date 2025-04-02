package io.bosonnetwork.messaging.impl;

import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bosonnetwork.Id;
import io.bosonnetwork.Identity;
import io.bosonnetwork.crypto.CryptoBox.Nonce;
import io.bosonnetwork.crypto.CryptoIdentity;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.messaging.Profile;
import io.bosonnetwork.messaging.UserProfile;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonObject;

public class APIClient {
	private final Vertx vertx;
	private final Id homePeerId;
	private final URL apiURL;
	private final String baseURI;
	private final HttpClient httpClient;
	private Nonce nonce;

	private CryptoIdentity user;
	private Identity device;
	private String accessToken;

	private Consumer<String> accessTokenRefreshHandler;

	protected APIClient(Vertx vertx, Id peerId, URL apiURL) {
		this.vertx = vertx;
		this.homePeerId = peerId;
		this.apiURL = apiURL;
		this.baseURI = this.apiURL.getPath().endsWith("/") ?
				apiURL.getPath().substring(0, apiURL.getPath().length() - 1) : apiURL.getPath();

		this.httpClient = this.vertx.createHttpClient(new HttpClientOptions()
				.setSsl(apiURL.getProtocol().equals("https"))
				.setTrustAll(true)
				.setVerifyHost(false)
				.setMaxPoolSize(128)
				.setKeepAlive(true)
				.setConnectTimeout(10000)
				.setKeepAliveTimeout(120)
				.setIdleTimeoutUnit(TimeUnit.SECONDS)
				.setIdleTimeout(300)
				.setProtocolVersion(HttpVersion.HTTP_1_1)
				.setDefaultHost(apiURL.getHost())
				.setDefaultPort(apiURL.getPort() > 0 ? apiURL.getPort() : apiURL.getDefaultPort()));
	}

	protected void setUserIdentity(CryptoIdentity user) {
		this.user = user;
	}

	protected void setDeviceIdentity(Identity device) {
		this.device = device;
	}

	protected void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}

	protected void setAccessTokenRefreshHandler(Consumer<String> handler) {
		this.accessTokenRefreshHandler = handler;
	}

	private CryptoIdentity getUser() {
		if (user == null)
			throw new IllegalStateException("No user identity supplier has been set");

		return user;
	}

	private Identity getDevice() {
		if (device == null)
			throw new IllegalStateException("No device identity supplier has been set");

		return device;
	}

	private String getAccessToken() {
		if (accessToken == null)
			throw new IllegalStateException("No access token supplier has been set");

		// TODO: call auth to acquire a new access token if is null;

		return accessToken;
	}

	private Nonce nonce() {
		if (nonce == null)
			nonce = Nonce.random();
		else
			nonce = nonce.increment();

		return nonce;
	}

	private RequestOptions requestOptions(HttpMethod method, String uri) {
		return new RequestOptions()
				.setFollowRedirects(true)
				.setMethod(method)
				.setURI(baseURI + uri);
	}

	private Future<JsonObject> httpRequest(HttpMethod method, String uri,
			JsonObject body, int expectedStatus) {
		return httpRequest(method, uri, null, 0, body, expectedStatus);
	}

	private Future<JsonObject> httpRequest(HttpMethod method, String uri,
			long timeout, JsonObject body, int expectedStatus) {
		return httpRequest(method, uri, null, timeout, body, expectedStatus);
	}

	private Future<JsonObject> httpRequest(HttpMethod method, String uri,
			String accessToken, JsonObject body, int expectedStatus) {
		return httpRequest(method, uri, accessToken, 0, body, expectedStatus);
	}

	private Future<String> refreshAccessToken() {
		byte[] nonce = nonce().bytes();

		Identity user = getUser();
		Identity device = getDevice();

		var body = JsonObject.of(
				"userId", user.getId(),
				"deviceId",	device.getId(),
				"nonce", nonce,
				"userSig", user.sign(nonce),
				"deviceSig", device.sign(nonce));

		return httpRequest(HttpMethod.POST, "/api/v1/auth", body, 201).map(json -> {
			String accessToken = json.getString("token");
			if (accessToken == null || accessToken.isEmpty())
				throw new IllegalStateException("HTTP error: Invalid server response, missing access token");

			return accessToken;
		});
	}

	private Future<JsonObject> httpRequest(HttpMethod method, String uri,
			String accessToken, long timeout, JsonObject body, int expectedStatus) {
		RequestOptions opts = requestOptions(method, uri)
				.addHeader("Accept", "application/json");

		if (body != null)
				opts.addHeader("Content-Type", "application/json");

		if (accessToken != null)
			opts.addHeader("Authorization", "Bearer " + accessToken);

		if (timeout >= 0)
			opts.setTimeout(timeout);

		return httpClient.request(opts).compose(req -> {
			if (body != null)
				return req.send(body.toBuffer());
			else
				return req.send();
		}).compose(res -> {
			if (res.statusCode() == 401) { // Token expired? refresh token then retry
				return refreshAccessToken().compose(newToken -> {
					this.accessToken = newToken;
					if (accessTokenRefreshHandler != null)
						accessTokenRefreshHandler.accept(newToken);

					// replay the request with the newly acquired token
					return httpRequest(method, uri, newToken, timeout, body, expectedStatus);
				});
			}

			if (res.statusCode() != expectedStatus)
				return Future.failedFuture("HTTP error, status: " + res.statusCode());

			String contentType = res.getHeader("Content-Type");
			if (contentType == null)
				return Future.failedFuture("HTTP error: Missing Content-Type header in the response");

			int paramIdx = contentType.indexOf(';');
			String mediaType = paramIdx != -1 ? contentType.substring(0, paramIdx) : contentType;
			if (!mediaType.equalsIgnoreCase("application/json"))
				return Future.failedFuture("HTTP error: unexpected Content-Type header in the response");

			return res.body().map(buffer -> {
				JsonObject json = buffer.toJsonObject();
				return json;
			});
		});
	}

	public static record MessagingServiceId(Id peerId, Id nodeId) {};

	public Future<MessagingServiceId> getServiceIds() {
		return httpRequest(HttpMethod.GET, "/api/v1/service/id", null, 200).map(json -> {
			if (!json.containsKey("peerId") || !json.containsKey("nodeId"))
				throw new IllegalStateException("HTTP error: Invalid server response, missing peer id or node id");

			Id peerId = Id.of(json.getString("peerId"));
			Id nodeId = Id.of(json.getString("nodeId"));

			return new MessagingServiceId(peerId, nodeId);
		});
	}

	public static Future<MessagingServiceId> getServiceIds(Vertx vertx, URL apiURL) {
		String baseURI = apiURL.getPath().endsWith("/") ?
				apiURL.getPath().substring(0, apiURL.getPath().length() - 1) : apiURL.getPath();

		RequestOptions opts = new RequestOptions()
				.setFollowRedirects(true)
				.setMethod(HttpMethod.GET)
				.setURI(baseURI + "/api/v1/service/id")
				.addHeader("Accept", "application/json");

		HttpClient httpClient = vertx.createHttpClient(new HttpClientOptions()
				.setSsl(apiURL.getProtocol().equals("https"))
				.setTrustAll(true)
				.setVerifyHost(false)
				.setConnectTimeout(10000)
				.setKeepAliveTimeout(120)
				.setIdleTimeoutUnit(TimeUnit.SECONDS)
				.setIdleTimeout(300)
				.setProtocolVersion(HttpVersion.HTTP_1_1)
				.setDefaultHost(apiURL.getHost())
				.setDefaultPort(apiURL.getPort() > 0 ? apiURL.getPort() : apiURL.getDefaultPort()));

		return httpClient.request(opts).compose(req -> {
			return req.send();
		}).compose(res -> {
			if (res.statusCode() != 200)
				return Future.failedFuture("HTTP error, status: " + res.statusCode());

			String contentType = res.getHeader("Content-Type");
			if (contentType == null)
				return Future.failedFuture("HTTP error: Missing Content-Type header in the response");

			int paramIdx = contentType.indexOf(';');
			String mediaType = paramIdx != -1 ? contentType.substring(0, paramIdx) : contentType;
			if (!mediaType.equalsIgnoreCase("application/json"))
				return Future.failedFuture("HTTP error: unexpected Content-Type header in the response");

			return res.body().map(buffer -> {
				JsonObject json = buffer.toJsonObject();

				if (!json.containsKey("peerId") || !json.containsKey("nodeId"))
					throw new IllegalStateException("HTTP error: Invalid server response, missing peer id or node id");

				Id peerId = Id.of(json.getString("peerId"));
				Id nodeId = Id.of(json.getString("nodeId"));

				return new MessagingServiceId(peerId, nodeId);
			});
		});
	}

	public Future<String> registerUserAndDevice(String passphrase, String userName, String deviceName, String appName) {
		byte[] nonce = nonce().bytes();

		Identity user = getUser();
		Identity device = getDevice();

		byte[] profileDigest = Profile.digest(user.getId(), homePeerId, userName, false, null);

		var body = JsonObject.of(
				"userId", user.getId(),
				"userName", userName,
				"passphrase", passphrase,
				"deviceId",	device.getId(),
				"deviceName", deviceName,
				"appName", appName,
				"nonce", nonce,
				"userSig", user.sign(nonce),
				"deviceSig", device.sign(nonce),
				"profileSig", user.sign(profileDigest));

		return httpRequest(HttpMethod.POST, "/api/v1/users", body, 201).map(json -> {
			String accessToken = json.getString("token");
			if (accessToken == null || accessToken.isEmpty())
				throw new IllegalStateException("HTTP error: Invalid server response, missing access token");

			this.accessToken = accessToken;
			if (accessTokenRefreshHandler != null)
				accessTokenRefreshHandler.accept(accessToken);

			return accessToken;
		});
	}

	public Future<UserCredential> registerDeviceWithUser(String passphrase, String deviceName, String appName) {
		byte[] nonce = nonce().bytes();

		CryptoIdentity user = getUser();
		Identity device = getDevice();

		var body = JsonObject.of(
				"userId", user.getId(),
				"passphrase", passphrase,
				"deviceId",	device.getId(),
				"deviceName", deviceName,
				"appName", appName,
				"nonce", nonce,
				"userSig", user.sign(nonce),
				"deviceSig", device.sign(nonce));

		return httpRequest(HttpMethod.POST, "/api/v1/devices", body, 201).map(json -> {
			String accessToken = json.getString("token");
			if (accessToken == null || accessToken.isEmpty())
				throw new IllegalStateException("HTTP error: Invalid server response, missing access token");

			String userName = json.getString("userName");
			boolean avatar = json.getBoolean("avatar");

			this.accessToken = accessToken;
			if (accessTokenRefreshHandler != null)
				accessTokenRefreshHandler.accept(accessToken);

			return new UserCredential(new UserProfileImpl(user, userName, avatar), accessToken);
		});
	}

	public Future<String> registerDeviceRequest(String deviceName, String appName) {
		byte[] nonce = nonce().bytes();

		Identity device = getDevice();

		var body = JsonObject.of(
				"deviceId",	device.getId(),
				"deviceName", deviceName,
				"appName", appName,
				"nonce", nonce,
				"sig", device.sign(nonce));

		return httpRequest(HttpMethod.POST, "/api/v1/devices/registrations", body, 201).map(json -> {
			String registrationId = json.getString("registrationId");
			if (registrationId == null || registrationId.isEmpty())
				throw new IllegalStateException("HTTP error: Invalid server response, missing registration id");

			return registrationId;
		});
	}

	public static record UserCredential(UserProfile user, String accessToken) {};

	public Future<UserCredential> finishRegisterDeviceRequest(String registrationId) {
		return finishRegisterDeviceRequest(registrationId, 0);
	}

	public Future<UserCredential> finishRegisterDeviceRequest(String registrationId, long timeout) {
		byte[] nonce = nonce().bytes();

		Identity device = getDevice();

		var body = JsonObject.of(
				"deviceId",	device.getId(),
				"nonce", nonce,
				"sig", device.sign(nonce));

		return httpRequest(HttpMethod.POST, "/api/v1/devices/registrations/" + registrationId, timeout, body, 201).map(json -> {
			String uid = json.getString("userId");
			if (uid == null || uid.isEmpty())
				throw new IllegalStateException("HTTP error: Invalid server response, missing user id");

			byte[] sk =json.getBinary("userPrivateKey");
			if (sk == null || sk.length != Signature.PrivateKey.BYTES)
				throw new IllegalStateException("HTTP error: Invalid server response, missing or invalid user private key");

			String accessToken = json.getString("token");
			if (accessToken == null || accessToken.isEmpty())
				throw new IllegalStateException("HTTP error: Invalid server response, missing access token");

			String userName = json.getString("userName");
			boolean avatar = json.getBoolean("avatar");

			Id userId;
			UserProfileImpl user;

			try {
				userId = Id.of(uid);
				sk = device.decrypt(userId, sk);
				user = new UserProfileImpl(sk, userName, avatar);
			} catch (Exception e) {
				throw new IllegalStateException("HTTP Error: Invalid server response", e);
			}

			if (!user.getId().equals(userId))
				throw new IllegalStateException("HTTP error: User id and private key do not match");

			this.accessToken = accessToken;
			if (accessTokenRefreshHandler != null)
				accessTokenRefreshHandler.accept(accessToken);

			return new UserCredential(user, accessToken);
		});
	}

	public static class MessagingServiceInfo {
		@JsonProperty(value = "peerId", required = true)
		private final Id peerId;
		@JsonProperty(value = "nodeId", required = true)
		private final Id nodeId;

		@JsonProperty(value = "version", required = true)
		private final String version;

		@JsonProperty(value = "endpoints", required = true)
		private final Map<String, String> endpoints;

		@JsonProperty(value = "sslCert", required = true)
		private final String sslCert;

		@JsonProperty(value = "features", required = true)
		private final Map<String, Object> features;

		private final List<URI> mqttEndpoints;

		@JsonCreator
		protected MessagingServiceInfo(
				@JsonProperty(value = "peerId", required = true) Id peerId,
				@JsonProperty(value = "nodeId", required = true) Id nodeId,
				@JsonProperty(value = "version", required = true) String version,
				@JsonProperty(value = "endpoints", required = true) Map<String, String> endpoints,
				@JsonProperty(value = "sslCert", required = true) String sslCert,
				@JsonProperty(value = "features", required = true) Map<String, Object> features) {
			if (peerId == null)
				throw new IllegalArgumentException("Missing messaging service peer id");

			this.peerId = peerId;

			if (nodeId == null)
				throw new IllegalArgumentException("Missing messaging service node id");

			this.nodeId = nodeId;

			if (version.isEmpty() || !version.equals("1.0"))
				throw new IllegalArgumentException("Unsupported messaging service version");

			this.version = version;

			if (endpoints == null || endpoints.isEmpty())
				throw new IllegalArgumentException("Missing messaging service endpoints");

			this.endpoints = endpoints;

			URI sslEndpoint = endpoints.containsKey("ssl") ?
					URI.create(endpoints.get("ssl")) : null;

			URI tcpEndpoint = endpoints.containsKey("tcp") ?
					URI.create(endpoints.get("tcp")) : null;

			if (sslEndpoint == null && tcpEndpoint == null)
				throw new IllegalArgumentException("No available/compatible endpoints");

			// Optimized immutable list: ImmutableCollections.List12
			if (sslEndpoint != null && tcpEndpoint != null)
				this.mqttEndpoints = List.of(sslEndpoint, tcpEndpoint);
			else
				this.mqttEndpoints = List.of(sslEndpoint != null ? sslEndpoint : tcpEndpoint);

			this.sslCert = sslCert;

			this.features = features == null || features.isEmpty() ?
					Collections.emptyMap() : Collections.unmodifiableMap(features);
		}

		public Id getPeerId() {
			return peerId;
		}

		public Id getNodeId() {
			return nodeId;
		}

		public String getVersion() {
			return version;
		}

		public List<URI> getMqttEndpoints() {
			return mqttEndpoints;
		}

		public String getSslCert() {
			return sslCert;
		}

		public Map<String, Object> getFeatures() {
			return features;
		}
	}

	public Future<MessagingServiceInfo> getServiceInfo() {
		return httpRequest(HttpMethod.GET, "/api/v1/service/info", getAccessToken(), null, 200).map(json -> {
			return json.mapTo(MessagingServiceInfo.class);
		});
	}

	public Future<Profile> getProfile(Id id) {
		return httpRequest(HttpMethod.GET, "/api/v1/profile/" + id.toBase58String(), null, 200).map(json -> {
			return json.mapTo(Profile.class);
		});
	}

	public Future<ContactsUpdate> fetchContactsUpdate(String versionId) {
		String uri = "/api/v1/contacts" + (versionId == null ? "" : ("/" + versionId));
		return httpRequest(HttpMethod.GET, uri, getAccessToken(), null, 200).map(json -> {
			return json.mapTo(ContactsUpdate.class);
		});
	}
}
