package io.bosonnetwork.photonmessaging;

import java.io.InputStream;
import java.net.Inet4Address;
import java.net.URI;
import java.util.Map;
import java.util.Objects;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

import io.bosonnetwork.json.Json;
import io.bosonnetwork.utils.AddressUtils;

public class TestClientLauncher {
	private static Vertx vertx;
	private static MessagingClient client;

	private static Configuration loadConfig() throws Exception{
		try (InputStream s = TestClientLauncher.class.getClassLoader().getResourceAsStream("testConfig.yaml")) {
			Map<String, Object> map = Json.yamlMapper().readValue(s, Json.mapType());
			// fix the server host
			if (map.containsKey("service")) {
				@SuppressWarnings("unchecked")
				Map<String, Object> service = (Map<String, Object>) map.get("service");
				String endpoint = service.getOrDefault("endpoint", "").toString();
				if (!endpoint.isEmpty()) {
					String host = Objects.requireNonNull(AddressUtils.getDefaultRouteAddress(Inet4Address.class)).getHostAddress();
					URI uri = URI.create(endpoint);
					URI fixed = new URI(uri.getScheme(), uri.getUserInfo(), host, uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
					service.put("endpoint", fixed.toString());
				}
			}
			return Configuration.fromMap(map);
		} catch (Exception e) {
			System.err.println("Failed to load configuration file: " + e.getMessage());
			throw e;
		}
	}

	public static void main(String[] args) {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			if (client != null) {
				System.out.println("Shutting down the active proxy client...");
				client.stop().thenRun(() -> {
					System.out.println("Active proxy client stopped.");
				}).join();

				// Cannot chain vertx.close() to the above future because closing Vert.x will terminate its event loop,
				// preventing any pending future handlers from executing.
				System.out.print("Shutting down Vert.x gracefully...");
				vertx.close().toCompletionStage().toCompletableFuture().join();
				System.out.println("Done!");
			}
		}));

		vertx = Vertx.vertx(new VertxOptions()
				.setWorkerPoolSize(4)
				.setEventLoopPoolSize(4)
				.setPreferNativeTransport(true));

		try {
			// no node for the client. so the configuration should:
			// - provide the service peer address (to avoid the dht lookup)
			// - disable announce peer
			Configuration config = loadConfig();
			client = MessagingClient.create(vertx, null, config);
			client.addConnectionListener(new ConnectionListener() {
				@Override
				public void onConnected() {
					System.out.println("Connected to the messaging service: " + config.getServicePeerId());
				}

				@Override
				public void onContactSynced() {
					System.out.println("contact synced");
				}

				@Override
				public void onDisconnected() {
					System.out.println("Disconnected from the messaging service: " + config.getServicePeerId());
				}
			});

			System.out.println("Starting the messaging client...");
			client.start().thenRun(() ->
				System.out.println("Started the messaging client")
			).join();
		} catch (Exception e) {
			e.printStackTrace(System.err);
			vertx.close();
		}
	}
}