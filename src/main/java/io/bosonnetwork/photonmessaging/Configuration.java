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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.photonmessaging.impl.Database;
import io.bosonnetwork.utils.Base58;
import io.bosonnetwork.utils.ConfigMap;
import io.bosonnetwork.utils.Hex;

public class Configuration {
	private static final String DEFAULT_SCHEME = "mqtts://";

	private Id servicePeerId;
	private URI serviceEndpoint; // optional

	private Signature.KeyPair userKey;
	private Signature.KeyPair deviceKey;

	private String databaseUri;
	private int databasePoolSize;
	private String databaseSchemaName;

	private Configuration() {
	}

	public static Configuration fromMap(Map<String, Object> map) throws IllegalArgumentException {
		ConfigMap cm = new ConfigMap(map);
		Configuration config = new Configuration();

		ConfigMap service = cm.getObject("service");
		if (service == null || service.isEmpty())
			throw new IllegalArgumentException("Missing service");

		config.servicePeerId = service.getId("peerId");
		// optional
		String endpoint = service.getString("endpoint", null);
		if (endpoint != null) {
			URI uri = URI.create(endpoint);
			if (!uri.isAbsolute() || uri.getHost() == null || uri.getScheme() == null ||
					uri.getPort() <= 0 || uri.getPort() > 65535 ||
					(!uri.getScheme().equals("mqtt") && !uri.getScheme().equals("mqtts")))
				throw new IllegalArgumentException("Invalid endpoint: " + endpoint);

			config.serviceEndpoint = uri;
		}

		ConfigMap client = cm.getObject("client");
		String sk = client.getString("userPrivateKey", null);
		if (sk == null || sk.isEmpty())
			throw new IllegalArgumentException("Missing client userPrivateKey");

		try {
			config.userKey = Signature.KeyPair.fromPrivateKey(sk.startsWith("0x") ?
					Hex.decode(sk, 2, sk.length() - 2) :
					Base58.decode(sk));
		} catch (Exception e) {
			throw new IllegalArgumentException("config error, invalid client userPrivateKey", e);
		}

		sk = client.getString("devicePrivateKey", null);
		if (sk == null || sk.isEmpty())
			throw new IllegalArgumentException("Missing client devicePrivateKey");

		try {
			config.deviceKey = Signature.KeyPair.fromPrivateKey(sk.startsWith("0x") ?
					Hex.decode(sk, 2, sk.length() - 2) :
					Base58.decode(sk));
		} catch (Exception e) {
			throw new IllegalArgumentException("config error, invalid client devicePrivateKey", e);
		}

		// Database
		ConfigMap database = cm.getObject("database");
		if (database == null || database.isEmpty())
			throw new IllegalArgumentException("Missing database configuration");

		config.databaseUri = database.getString("uri");
		if (!Database.supports(config.databaseUri))
			throw new IllegalArgumentException("Unsupported database URI: " + config.databaseUri);

		config.databasePoolSize = database.getInteger("poolSize", config.databasePoolSize);
		if (config.databasePoolSize < 0)
			throw new IllegalArgumentException("Invalid database poolSize: " + config.databasePoolSize);

		String schemaName = database.getString("schema", config.databaseSchemaName);
		if (schemaName != null && !schemaName.isEmpty()) {
			if (!schemaName.matches("[a-z][a-z0-9_]{0,31}"))
				throw new IllegalArgumentException("Invalid database schema name: " + schemaName);
			config.databaseSchemaName = schemaName;
		} else {
			config.databaseSchemaName = null;
		}

		return config;
	}

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

		Map<String, Object> database = new LinkedHashMap<>();
		database.put("uri", databaseUri);
		if (databasePoolSize != 0)
			database.put("poolSize", databasePoolSize);
		if (databaseSchemaName != null)
			database.put("schema", databaseSchemaName);
		map.put("database", database);

		return map;
	}

	public Id getServicePeerId() {
		return servicePeerId;
	}

	public URI getServiceEndpoint() {
		return serviceEndpoint;
	}

	public Signature.KeyPair getUserKey() {
		return userKey;
	}

	public Signature.KeyPair getDeviceKey() {
		return deviceKey;
	}

	public String getDatabaseUri() {
		return databaseUri;
	}

	public int getDatabasePoolSize() {
		return databasePoolSize;
	}

	public String getDatabaseSchemaName() {
		return databaseSchemaName;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private Configuration config;

		private Builder() {
			config = new Configuration();
		}

		private Configuration config() {
			return config == null ? config = new Configuration() : config;
		}

		public Builder service(Id peerId, String endpoint) {
			servicePeerId(peerId);
			serviceEndpoint(endpoint);
			return this;
		}

		public Builder service(Id peerId, URI endpoint) {
			servicePeerId(peerId);
			serviceEndpoint(endpoint);
			return this;
		}

		public Builder servicePeerId(Id peerId) {
			Objects.requireNonNull(peerId, "peerId");
			config().servicePeerId = peerId;
			return this;
		}

		public Builder serviceEndpoint(String endpoint) {
			Objects.requireNonNull(endpoint, "endpoint");
			serviceEndpoint(URI.create(endpoint));
			return this;
		}

		public Builder serviceEndpoint(URI endpoint) {
			Objects.requireNonNull(endpoint);
			if (!endpoint.isAbsolute() || endpoint.getHost() == null || endpoint.getScheme() == null ||
					endpoint.getPort() <= 0 || endpoint.getPort() > 65535 ||
					(!endpoint.getScheme().equals("mqtt") && !endpoint.getScheme().equals("mqtts")))
				throw new IllegalArgumentException("Invalid endpoint");

			config().serviceEndpoint = endpoint;
			return this;
		}

		public Builder userKey(Signature.KeyPair userKey) {
			Objects.requireNonNull(userKey, "userKey");
			config().userKey = userKey;
			return this;
		}

		public Builder generateUserKey() {
			return userKey(Signature.KeyPair.random());
		}

		public Builder userKey(byte[] userKey) {
			Objects.requireNonNull(userKey, "userKey");
			if (userKey.length != Signature.PrivateKey.BYTES)
				throw new IllegalArgumentException("Invalid private key");

			return userKey(Signature.KeyPair.fromPrivateKey(userKey));
		}

		public Builder userKey(String userKey) {
			Objects.requireNonNull(userKey, "userKey");
			byte[] sk = userKey.startsWith("0x") ?
					Hex.decode(userKey, 2, userKey.length() - 2) :
					Base58.decode(userKey);
			return userKey(sk);
		}

		public Builder deviceKey(Signature.KeyPair deviceKey) {
			Objects.requireNonNull(deviceKey, "deviceKey");
			config().deviceKey = deviceKey;
			return this;
		}

		public Builder generateDeviceKey() {
			return deviceKey(Signature.KeyPair.random());
		}

		public Builder deviceKey(byte[] deviceKey) {
			Objects.requireNonNull(deviceKey, "deviceKey");
			if (deviceKey.length != Signature.PrivateKey.BYTES)
				throw new IllegalArgumentException("Invalid private key");

			return deviceKey(Signature.KeyPair.fromPrivateKey(deviceKey));
		}

		public Builder deviceKey(String deviceKey) {
			Objects.requireNonNull(deviceKey, "deviceKey");
			byte[] sk = deviceKey.startsWith("0x") ?
					Hex.decode(deviceKey, 2, deviceKey.length() - 2) :
					Base58.decode(deviceKey);
			return deviceKey(sk);
		}

		public Builder database(String databaseUri, int databasePoolSize) {
			databaseUri(databaseUri);
			databasePoolSize(databasePoolSize);
			return this;
		}

		public Builder databaseUri(String databaseUri) {
			Objects.requireNonNull(databaseUri, "databaseUri");
			config().databaseUri = databaseUri;
			return this;
		}

		public Builder databasePoolSize(int databasePoolSize) {
			if (databasePoolSize < 0)
				throw new IllegalArgumentException("Invalid databasePoolSize");
			config().databasePoolSize = databasePoolSize;
			return this;
		}

		public Builder databaseSchemaName(String schema) {
			if (schema != null && !schema.isEmpty()) {
				if (!schema.matches("[a-z][a-z0-9_]{0,31}"))
					throw new IllegalArgumentException("Invalid database schema name: " + schema);
				config().databaseSchemaName = schema;
			} else {
				config().databaseSchemaName = null;
			}

			return this;
		}

		private boolean verify() {
			return config().servicePeerId != null && config().userKey != null && config().deviceKey != null
					&& config().databaseUri != null;
		}

		public Configuration build() {
			if (!verify())
				throw new IllegalStateException("Incomplete configuration");

			Configuration c = config();
			config = null;
			return c;
		}
	}
}