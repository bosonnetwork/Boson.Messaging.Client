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

package io.bosonnetwork.photonmessaging;

import java.net.URI;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.database.SqlSafety;
import io.bosonnetwork.photonmessaging.impl.Database;
import io.bosonnetwork.utils.Base58;
import io.bosonnetwork.utils.ConfigMap;
import io.bosonnetwork.utils.FileUtils;
import io.bosonnetwork.utils.Hex;

/**
 * This class represents the configuration for the application.
 * It encapsulates various settings related to service identification,
 * endpoints, cryptographic keys, data directories, and database configurations.
 */
public class Configuration {
	private final Id servicePeerId;
	private final @Nullable URI serviceEndpoint;

	private final Signature.KeyPair userKey;
	private final Signature.KeyPair deviceKey;

	private final Path dataDir;

	private final String databaseUri;
	private final int databasePoolSize;
	private final @Nullable String databaseSchemaName;
	private final @Nullable MessagingStore store;

	private Configuration(Builder builder) {
		this.servicePeerId = Objects.requireNonNull(builder.servicePeerId, "servicePeerId must be set");
		this.serviceEndpoint = Objects.requireNonNull(builder.serviceEndpoint, "serviceEndpoint must be set");
		this.userKey = Objects.requireNonNull(builder.userKey, "userKey must be set");
		this.deviceKey = Objects.requireNonNull(builder.deviceKey, "deviceKey must be set");
		this.dataDir = Objects.requireNonNull(builder.dataDir, "dataDir must be set");
		this.databaseUri = Objects.requireNonNull(builder.databaseUri, "databaseUri must be set");
		this.databasePoolSize = builder.databasePoolSize;
		this.databaseSchemaName = builder.databaseSchemaName;
		this.store = builder.store;
	}

	/**
	 * Creates a {@link Configuration} instance from the provided map.
	 * The map is expected to contain the necessary configuration parameters, and invalid or missing parameters
	 * will result in specific exceptions being thrown.
	 *
	 * @param map a {@link Map} containing key-value pairs representing the configuration.
	 *            Keys should include "service", "client", "database", and other optional fields such as "dataDir".
	 *            Values for these keys must conform to the expected format and constraints.
	 * @return a {@link Configuration} object populated with the data from the map.
	 * @throws IllegalArgumentException if the map is missing required keys, contains invalid values,
	 *                                  or has improperly formatted data.
	 */
	public static Configuration fromMap(Map<String, Object> map) throws IllegalArgumentException {
		ConfigMap cm = new ConfigMap(map);
		Builder builder = new Builder();

		ConfigMap service = cm.getObject("service");
		if (service == null || service.isEmpty())
			throw new IllegalArgumentException("Missing service");

		builder.servicePeerId(service.getId("peerId"));
		// optional
		String endpoint = service.getString("endpoint", null);
		if (endpoint != null)
			builder.serviceEndpoint(endpoint);

		ConfigMap client = cm.getObject("client");
		if (client == null || client.isEmpty())
			throw new IllegalArgumentException("Missing client");

		String sk = client.getString("userPrivateKey", null);
		builder.userKey(sk);

		sk = client.getString("devicePrivateKey", null);
		builder.deviceKey(sk);

		// Data directory
		Path dataDir = cm.getPath("dataDir", null);
		if (dataDir != null)
			builder.dataDir(dataDir);

		// Database
		ConfigMap database = cm.getObject("database");
		if (database == null || database.isEmpty())
			throw new IllegalArgumentException("Missing database configuration");

		builder.databaseUri(database.getString("uri"));

		builder.databasePoolSize(database.getInteger("poolSize", 0));

		builder.databaseSchemaName(database.getString("schema", null));

		return builder.build();
	}

	/**
	 * Converts the configuration object into a map representation.
	 * The resulting map contains key-value pairs corresponding to the configuration fields,
	 * organized under specific categories such as "service", "client", and "database".
	 *
	 * @return a {@code Map<String, Object>} where the keys are configuration field names
	 *         and the values represent their respective settings.
	 */
	public Map<String, Object> toMap() {
		Map<String, Object> map = new LinkedHashMap<>();

		Map<String, Object> subMap = new LinkedHashMap<>();
		subMap.put("peerId", servicePeerId.toString());
		if (serviceEndpoint != null)
			subMap.put("endpoint", serviceEndpoint.toString());
		map.put("service", subMap);

		subMap = new LinkedHashMap<>();
		subMap.put("userPrivateKey", Base58.encode(userKey.privateKey().bytes()));
		subMap.put("devicePrivateKey", Base58.encode(deviceKey.privateKey().bytes()));
		map.put("client", subMap);

		map.put("dataDir", dataDir.toString());

		Map<String, Object> database = new LinkedHashMap<>();
		database.put("uri", databaseUri);
		if (databasePoolSize != 0)
			database.put("poolSize", databasePoolSize);
		if (databaseSchemaName != null)
			database.put("schema", databaseSchemaName);
		map.put("database", database);

		return map;
	}

	/**
	 * Retrieves the service peer ID associated with this configuration.
	 *
	 * @return the service peer ID as an instance of {@link Id}.
	 */
	public Id getServicePeerId() {
		return servicePeerId;
	}

	/**
	 * Retrieves the service endpoint URI associated with this configuration.
	 *
	 * @return the service endpoint as an instance of {@link URI}.
	 */
	public @Nullable URI getServiceEndpoint() {
		return serviceEndpoint;
	}

	/**
	 * Retrieves the user's cryptographic key pair.
	 *
	 * @return the user's {@link Signature.KeyPair}.
	 */
	public Signature.KeyPair getUserKey() {
		return userKey;
	}

	/**
	 * Retrieves the device's cryptographic key pair.
	 *
	 * @return the device's {@link Signature.KeyPair}.
	 */
	public Signature.KeyPair getDeviceKey() {
		return deviceKey;
	}

	/**
	 * Retrieves the path to the data directory.
	 *
	 * @return the {@link Path} to the data directory.
	 */
	public Path getDataDir() {
		return dataDir;
	}

	/**
	 * Retrieves the database connection URI.
	 *
	 * @return the database URI as a string.
	 */
	public String getDatabaseUri() {
		return databaseUri;
	}

	/**
	 * Retrieves the maximum size of the database connection pool.
	 *
	 * @return the maximum pool size.
	 */
	public int getDatabasePoolSize() {
		return databasePoolSize;
	}

	/**
	 * Retrieves the optional database schema name.
	 *
	 * @return the database schema name, or {@code null} if not specified.
	 */
	public @Nullable String getDatabaseSchemaName() {
		return databaseSchemaName;
	}

	/**
	 * Retrieves the platform-supplied persistence backend, if any. When set, the client uses this
	 * store instead of the built-in JDBC/Vert.x SQL backend (and the database URI is ignored).
	 *
	 * @return the {@link MessagingStore}, or {@code null} to use the built-in backend.
	 */
	public @Nullable MessagingStore getStore() {
		return store;
	}

	/**
	 * Creates a new {@link Builder} instance for constructing a {@link Configuration}.
	 *
	 * @return a new {@link Builder} instance.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link Configuration} instances.
	 */
	@NullUnmarked
	public static class Builder {
		private Id servicePeerId;
		private URI serviceEndpoint; // optional

		private Signature.KeyPair userKey;
		private Signature.KeyPair deviceKey;

		private Path dataDir;

		private String databaseUri;
		private int databasePoolSize;
		private String databaseSchemaName;
		private @Nullable MessagingStore store;

		private Builder() {
			dataDir = FileUtils.getUserDataDir().resolve( "boson/client/photon-messaging");
			databaseUri = "jdbc:sqlite:photonmessaging.db";
		}

		/**
		 * Configures the service identification and endpoint.
		 *
		 * @param peerId the unique identifier of the service peer.
		 * @param endpoint the service endpoint URI string.
		 * @return this builder instance.
		 */
		public Builder service(Id peerId, String endpoint) {
			servicePeerId(peerId);
			serviceEndpoint(endpoint);
			return this;
		}

		/**
		 * Configures the service identification and endpoint.
		 *
		 * @param peerId the unique identifier of the service peer.
		 * @param endpoint the service endpoint {@link URI}.
		 * @return this builder instance.
		 */
		public Builder service(Id peerId, URI endpoint) {
			servicePeerId(peerId);
			serviceEndpoint(endpoint);
			return this;
		}

		/**
		 * Sets the service peer identifier.
		 *
		 * @param peerId the unique identifier of the service peer.
		 * @return this builder instance.
		 */
		public Builder servicePeerId(Id peerId) {
			Objects.requireNonNull(peerId, "peerId");
			this.servicePeerId = peerId;
			return this;
		}

		/**
		 * Validates a service endpoint URI. A valid endpoint is an absolute URI with a host,
		 * a port in the range 1-65535, and an {@code mqtt} or {@code mqtts} scheme.
		 *
		 * @param endpoint the endpoint URI to validate
		 * @return the same endpoint URI, if valid
		 * @throws IllegalArgumentException if the endpoint is invalid or uses an unsupported scheme
		 */
		private static URI validateEndpoint(URI endpoint) {
			if (!endpoint.isAbsolute() || endpoint.getHost() == null || endpoint.getScheme() == null ||
					endpoint.getPort() <= 0 || endpoint.getPort() > 65535 ||
					(!endpoint.getScheme().equals("mqtt") && !endpoint.getScheme().equals("mqtts")))
				throw new IllegalArgumentException("Invalid endpoint: " + endpoint);

			return endpoint;
		}

		/**
		 * Sets the service endpoint URI.
		 *
		 * @param endpoint the service endpoint URI string.
		 * @return this builder instance.
		 */
		public Builder serviceEndpoint(String endpoint) {
			Objects.requireNonNull(endpoint, "endpoint");
			serviceEndpoint(URI.create(endpoint));
			return this;
		}

		/**
		 * Sets the service endpoint {@link URI}.
		 *
		 * @param endpoint the service endpoint {@link URI}.
		 * @return this builder instance.
		 * @throws IllegalArgumentException if the URI is invalid or uses an unsupported scheme.
		 */
		public Builder serviceEndpoint(URI endpoint) {
			Objects.requireNonNull(endpoint);
			this.serviceEndpoint = validateEndpoint(endpoint);
			return this;
		}

		/**
		 * Sets the user's cryptographic key pair.
		 *
		 * @param userKey the user key pair.
		 * @return this builder instance.
		 */
		public Builder userKey(Signature.KeyPair userKey) {
			Objects.requireNonNull(userKey, "userKey");
			this.userKey = userKey;
			return this;
		}

		/**
		 * Generates a random cryptographic key pair for the user.
		 *
		 * @return this builder instance.
		 */
		public Builder generateUserKey() {
			return userKey(Signature.KeyPair.random());
		}

		/**
		 * Sets the user's private key from a byte array.
		 *
		 * @param userKey the user private key bytes.
		 * @return this builder instance.
		 * @throws IllegalArgumentException if the private key length is invalid.
		 */
		public Builder userKey(byte[] userKey) {
			Objects.requireNonNull(userKey, "userKey");
			if (userKey.length != Signature.PrivateKey.BYTES)
				throw new IllegalArgumentException("Invalid private key");

			return userKey(Signature.KeyPair.fromPrivateKey(userKey));
		}

		/**
		 * Sets the user's private key from a Base58 or Hex encoded string.
		 *
		 * @param userKey the encoded user private key string.
		 * @return this builder instance.
		 */
		public Builder userKey(String userKey) {
			Objects.requireNonNull(userKey, "userKey");
			byte[] sk = userKey.startsWith("0x") ?
					Hex.decode(userKey, 2, userKey.length() - 2) :
					Base58.decode(userKey);
			return userKey(sk);
		}

		/**
		 * Sets the device's cryptographic key pair.
		 *
		 * @param deviceKey the device key pair.
		 * @return this builder instance.
		 */
		public Builder deviceKey(Signature.KeyPair deviceKey) {
			Objects.requireNonNull(deviceKey, "deviceKey");
			this.deviceKey = deviceKey;
			return this;
		}

		/**
		 * Generates a random cryptographic key pair for the device.
		 *
		 * @return this builder instance.
		 */
		public Builder generateDeviceKey() {
			return deviceKey(Signature.KeyPair.random());
		}

		/**
		 * Sets the device's private key from a byte array.
		 *
		 * @param deviceKey the device private key bytes.
		 * @return this builder instance.
		 * @throws IllegalArgumentException if the private key length is invalid.
		 */
		public Builder deviceKey(byte[] deviceKey) {
			Objects.requireNonNull(deviceKey, "deviceKey");
			if (deviceKey.length != Signature.PrivateKey.BYTES)
				throw new IllegalArgumentException("Invalid private key");

			return deviceKey(Signature.KeyPair.fromPrivateKey(deviceKey));
		}

		/**
		 * Sets the device's private key from a Base58 or Hex encoded string.
		 *
		 * @param deviceKey the encoded device private key string.
		 * @return this builder instance.
		 */
		public Builder deviceKey(String deviceKey) {
			Objects.requireNonNull(deviceKey, "deviceKey");
			byte[] sk = deviceKey.startsWith("0x") ?
					Hex.decode(deviceKey, 2, deviceKey.length() - 2) :
					Base58.decode(deviceKey);
			return deviceKey(sk);
		}

		/**
		 * Sets the data directory path.
		 *
		 * @param dataDir the path to the data directory.
		 * @return this builder instance.
		 */
		public Builder dataDir(Path dataDir) {
			Objects.requireNonNull(dataDir, "dataDir");
			this.dataDir = dataDir.normalize();
			return this;
		}

		/**
		 * Sets the data directory path from a string.
		 *
		 * @param dataDir the path string to the data directory.
		 * @return this builder instance.
		 * @throws IllegalArgumentException if the path string is invalid.
		 */
		public Builder dataDir(String dataDir) {
			Objects.requireNonNull(dataDir, "dataDir");
			try {
				return dataDir(Path.of(dataDir));
			} catch (InvalidPathException e) {
				throw new IllegalArgumentException("Invalid dataDir path", e);
			}
		}

		/**
		 * Configures the database connection URI and pool size.
		 *
		 * @param databaseUri the database connection URI string.
		 * @param databasePoolSize the maximum size of the database connection pool.
		 * @return this builder instance.
		 */
		public Builder database(String databaseUri, int databasePoolSize) {
			databaseUri(databaseUri);
			databasePoolSize(databasePoolSize);
			return this;
		}

		/**
		 * Sets the database connection URI.
		 *
		 * @param databaseUri the database connection URI string.
		 * @return this builder instance.
		 */
		public Builder databaseUri(String databaseUri) {
			Objects.requireNonNull(databaseUri, "databaseUri");
			if (!Database.supports(databaseUri))
				throw new IllegalArgumentException("Unsupported database URI: " + databaseUri);
			this.databaseUri = databaseUri;
			return this;
		}

		/**
		 * Sets the database connection pool size.
		 *
		 * @param databasePoolSize the maximum size of the database connection pool.
		 * @return this builder instance.
		 * @throws IllegalArgumentException if the pool size is negative.
		 */
		public Builder databasePoolSize(int databasePoolSize) {
			if (databasePoolSize < 0)
				throw new IllegalArgumentException("Invalid databasePoolSize");
			this.databasePoolSize = databasePoolSize;
			return this;
		}

		/**
		 * Sets the optional database schema name.
		 *
		 * @param schema the database schema name string.
		 * @return this builder instance.
		 * @throws IllegalArgumentException if the schema name is invalid.
		 */
		public Builder databaseSchemaName(String schema) {
			this.databaseSchemaName = SqlSafety.validateSchema(schema);
			return this;
		}

		/**
		 * Supplies a platform persistence backend. When set, the client uses this store instead of
		 * the built-in JDBC/Vert.x SQL backend, and the database URI is not required. Intended for
		 * platforms (such as Android) where the JDBC backend is unavailable.
		 *
		 * @param store the {@link MessagingStore} implementation.
		 * @return this builder instance.
		 */
		public Builder store(MessagingStore store) {
			this.store = Objects.requireNonNull(store, "store");
			return this;
		}

		/**
		 * Validates and constructs the {@link Configuration} instance.
		 *
		 * @return the constructed {@link Configuration}.
		 * @throws IllegalStateException if the configuration is incomplete.
		 */
		public Configuration build() {
			try {
				return new Configuration(this);
			} catch (NullPointerException | IllegalArgumentException e) {
				throw new IllegalStateException("Invalid configuration: " + e.getMessage(), e);
			}
		}
	}
}