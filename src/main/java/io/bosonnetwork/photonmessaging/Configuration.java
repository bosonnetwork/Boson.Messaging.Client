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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.utils.Base58;
import io.bosonnetwork.utils.ConfigMap;
import io.bosonnetwork.utils.Hex;

public class Configuration {
	private static final String DEFAULT_SCHEME = "mqtts://";

	private Id servicePeerId;
	private String serviceHost; // optional
	private int servicePort;	// optional

	private Signature.KeyPair userKey;
	private Signature.KeyPair deviceKey;

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
		config.serviceHost = service.getString("host", null);
		config.servicePort = service.getPort("port", 0);

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

		return config;
	}

	public Map<String, Object> toMap() {
		Map<String, Object> map = new LinkedHashMap<>();

		Map<String, Object> subMap = new LinkedHashMap<>();
		subMap.put("peerId", servicePeerId.toString());
		if (serviceHost != null)
			subMap.put("host", serviceHost);
		if (servicePort > 0)
			subMap.put("port", servicePort);
		map.put("service", subMap);

		subMap = new LinkedHashMap<>();
		subMap.put("userPrivateKey", Base58.encode(userKey.privateKey().bytes()));
		subMap.put("devicePrivateKey", Base58.encode(deviceKey.privateKey().bytes()));
		map.put("client", subMap);

		return map;
	}

	public Id getServicePeerId() {
		return servicePeerId;
	}

	public String getServiceHost() {
		return serviceHost;
	}

	public int getServicePort() {
		return servicePort;
	}

	public Signature.KeyPair getUserKey() {
		return userKey;
	}

	public Signature.KeyPair getDeviceKey() {
		return deviceKey;
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

		public Builder service(Id peerId, String host, int port) {
			servicePeerId(peerId);
			serviceHost(host);
			servicePort(port);
			return this;
		}

		public Builder servicePeerId(Id servicePeerId) {
			Objects.requireNonNull(servicePeerId, "servicePeerId");
			config().servicePeerId = servicePeerId;
			return this;
		}

		public Builder serviceHost(String serviceHost) {
			Objects.requireNonNull(serviceHost, "serviceHost");
			config().serviceHost = serviceHost;
			return this;
		}

		public Builder servicePort(int servicePort) {
			if (servicePort <= 0 || servicePort > 65535)
				throw new IllegalArgumentException("Invalid servicePort");

			config().servicePort = servicePort;
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

		private boolean verify() {
			return config().servicePeerId != null && config().userKey != null && config().deviceKey != null;
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