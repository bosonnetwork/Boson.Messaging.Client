package io.bosonnetwork.messaging.impl;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bosonnetwork.messaging.ClientBuilder;
import io.bosonnetwork.messaging.DeviceProfile;
import io.bosonnetwork.photonmessaging.MessagingClient;
import io.bosonnetwork.messaging.RepositoryException;
import io.bosonnetwork.messaging.UserAgent;
import io.bosonnetwork.messaging.UserProfile;
import io.bosonnetwork.messaging.impl.APIClient.MessagingServiceId;
import io.bosonnetwork.messaging.persistence.Database;
import io.bosonnetwork.vertx.VertxFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

public class ClientBuilderImpl extends ClientBuilder {
	private Vertx vertx;

	private static final Logger log = LoggerFactory.getLogger(ClientBuilderImpl.class);

	public ClientBuilderImpl() {
		super();
	}

	@Override
	protected Future<MessagingServiceId> getServiceIds(URL url) {
		return APIClient.getServiceIds(vertx, url);
	}

	private Future<UserAgent> setupUserAgent() {
		if (!userAgent.isConfigured())
			Future.failedFuture("user agent not configured");

		if (!connectionListeners.isEmpty())
			connectionListeners.forEach(userAgent::addConnectionListener);

		if (!messageListeners.isEmpty())
			messageListeners.forEach(userAgent::addMessageListener);

		if (!channelListeners.isEmpty())
			channelListeners.forEach(userAgent::addChannelListener);

		if (!contactListeners.isEmpty())
			contactListeners.forEach(userAgent::addContactListener);

		return Future.succeededFuture(userAgent);
	}

	private Future<UserAgent> buildDefaultUserAgent() {
		DefaultUserAgent agent = new DefaultUserAgent(vertx);

		return Future.succeededFuture().map(v -> {
			// Create default user agent
			try {
				if (repository == null)
					repository = Database.open(vertx, repositoryDb);

				agent.setMessagingRepository(repository);
			} catch (Exception e) {
				throw new IllegalStateException("Access the messaging repository failed", e);
			}

			if (user != null) {
				if (agent.getUser() == null)
					agent.setUser(user, userName);
				else
					log.warn("Messaging repository is configured, user profile will be ignored");
			}

			if (deviceNode != null && agent.getDevice() != null)
				agent.getDevice().setIdentity(deviceNode);

			if (device != null) {
				if (agent.getDevice() == null)
					agent.setDevice(device, deviceName, appName);
				else
					log.warn("Messaging repository is configured, device profile will be ignored");
			}

			if (!connectionListeners.isEmpty())
				agent.setConnectionListeners(connectionListeners);

			if (!profileListeners.isEmpty())
				agent.setProfileListeners(profileListeners);

			if (!messageListeners.isEmpty())
				agent.setMessageListeners(messageListeners);

			if (!channelListeners.isEmpty())
				agent.setChannelListeners(channelListeners);

			if (!contactListeners.isEmpty())
				agent.setContactListeners(contactListeners);

			return null;
		}).compose(v -> {
			// lookup and setup peer info if needed
			if (agent.getMessagingPeerInfo() != null)
				return Future.succeededFuture(agent);

			return Future.succeededFuture().compose(vv -> {
				if (apiURL != null) {
					return Future.succeededFuture(List.of(apiURL));
				} else {
					if (deviceNode != null)
						return lookupPeer();
					else
						return Future.failedFuture("Messaging peer not properly configured");
				}
			}).compose(urls -> {
				return attemptServiceCheck(urls).map(mpi -> {
					agent.setMessagingPeerInfo(mpi);
					return null;
				});
			}).map(agent);
		});
	}

	private Future<UserAgent> tryRegisterClient(UserAgent agent) {
		if (!registerUserAndDevice && !registerDevice)
			return Future.succeededFuture(agent);

		APIClient apiClient = new APIClient(vertx, peerId, agent.getMessagingPeerInfo().getApiURL());
		apiClient.setUserIdentity(user);
		apiClient.setDeviceIdentity(device);
		apiClient.setAccessTokenRefreshHandler(token -> {
			// same as in MessagingClientImpl::updateAccessToken
			Map<String, Object> config = Map.of("accessToken", token);

			try {
				agent.putProperties("api", config);
			} catch (RepositoryException e) {
				log.error("Save API client config failed: ", e.getMessage(), e);
			}
		});

		UserProfile user = agent.getUser();
		DeviceProfile device = agent.getDevice();

		if (registerUserAndDevice) {
			return apiClient.registerUserAndDevice(passphrase,
					user.getName(), device.getName(), device.getAppName()).map(agent);
		}

		if (registerDevice) {
			if (user != null) {
				return apiClient.registerDeviceWithUser(passphrase, device.getName(), device.getAppName()).map(cred -> {
					agent.onUserProfileAcquired(cred.user());
					return agent;
				});
			} else {
				return apiClient.registerDeviceRequest(deviceName, appName).compose(rid -> {
					Promise<String> promise = Promise.promise();
					vertx.setTimer(5000, tid -> {
						promise.complete(rid);
					});

					vertx.executeBlocking(() -> {
						return registrationRequestHandler.apply(rid);
					}).onSuccess(finished -> {
						if (finished)
							promise.complete(rid);
						else
							promise.fail("User cancelled");
					}).onFailure(e -> {
						promise.fail(e);
					});

					return promise.future();
				}).compose(rid -> {
					return apiClient.finishRegisterDeviceRequest(rid);
				}).map(cred -> {
					agent.onUserProfileAcquired(cred.user());
					return agent;
				});
			}
		}

		return Future.succeededFuture(agent);
	}

	@Override
	public CompletableFuture<MessagingClient> build() {
		try {
			eligibleCheck();
		} catch (Exception e) {
			return VertxFuture.failedFuture(e);
		}

		VertxOptions options = new VertxOptions();
		options.setPreferNativeTransport(nativeTransport);
		// options.setBlockedThreadCheckIntervalUnit(TimeUnit.SECONDS);
		// options.setBlockedThreadCheckInterval(300);
		this.vertx = Vertx.vertx(options);

		Future<MessagingClient> future = Future.succeededFuture().compose(v -> {
			if (userAgent != null)
				return setupUserAgent();
			else
				return buildDefaultUserAgent();
		}).compose(agent -> {
			return tryRegisterClient(agent);
		}).compose(agent -> {
			MessagingClientImpl client = new MessagingClientImpl(agent);
			return vertx.deployVerticle(client).map(id -> client);
		});

		return VertxFuture.of(future);
	}
}