package io.bosonnetwork.messaging;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Hash;
import io.bosonnetwork.utils.Base58;

public class ClientDevice {
	@JsonProperty("id")
	private final Id id;

	private final String clientId; // MQTT client id

	@JsonProperty("n")
	private final String name;
	@JsonProperty("a")
	private final String app;

	@JsonProperty("c")
	private final long created;

	@JsonProperty("ls")
	private long lastSeen;
	@JsonProperty("la")
	private String lastAddress;

	/**
	 * Creates an ephemeral {@code Credential} object for client with
	 * specified public key and nonce.
	 *
	 * @param clientId the target MQTT client id.
	 * @param clientPk the client side session encryption public key(CryptoBox).
	 * @param nonce the session nonce.
	 */
	@JsonCreator
	ClientDevice(@JsonProperty(value = "id", required = true) Id id,
			@JsonProperty(value = "n", required = true) String deviceName,
			@JsonProperty(value = "a", required = true) String appName,
			@JsonProperty(value = "c") long created,
			@JsonProperty(value = "ls") long lastSeen,
			@JsonProperty(value = "la") String lastAddress)  {
		this.id = id;
		this.name = deviceName;
		this.app = appName;

		this.created = created;
		this.lastSeen = lastSeen;
		this.lastAddress = lastAddress;

		this.clientId = Base58.encode(Hash.md5().digest(id.bytes()));
	}

	/**
	 * Gets the MQTT client device id of this {@code Credential}.
	 *
	 * @return the MQTT client deivce id.
	 */
	public Id getId() {
		return id;
	}

	/**
	 * Gets the MQTT client id of this {@code Credential}.
	 *
	 * @return the MQTT client id.
	 */
	public String getClientId() {
		return clientId;
	}

	/**
	 * Gets the client device name of this {@code Credential}.
	 *
	 * @return the client device name.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Gets the client application name of this {@code Credential}.
	 *
	 * @return the client application name.
	 */
	public String getApp() {
		return app;
	}

	/**
	 * Gets the created time(epoch) in milliseconds.
	 *
	 * @return the created time.
	 */
	public long getCreated() {
		return created;
	}

	public long getLastSeen() {
		return lastSeen;
	}

	public String getLastAddress() {
		return lastAddress;
	}

	@Override
	public int hashCode() {
		return id.hashCode() + 0x0B030C0D;
	}

	public boolean is(ClientDevice device) {
		if (device == this)
			return true;

		return Objects.equals(this.id, device.id);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;

		if (o instanceof ClientDevice that) {
			return Objects.equals(this.id, that.id) &&
					Objects.equals(this.clientId, that.clientId) &&
					Objects.equals(this.name, that.name) &&
					Objects.equals(this.app, that.app) &&
					this.created == that.created &&
					this.lastSeen == that.lastSeen &&
					Objects.equals(this.lastAddress, that.lastAddress);
		}

		return false;
	}

	@Override
	public String toString() {
		StringBuilder repr = new StringBuilder(512);

		repr.append("Device: ")
			.append(id.toBase58String())
			.append("[clientId=").append(clientId);

		if (name != null)
			repr.append(", name=").append(name);

		if (app != null)
			repr.append(", app=").append(app);

		repr.append(", created=").append(Instant.ofEpochMilli(created).toString());

		if (lastSeen != 0)
			repr.append(", lastSeen=").append(Duration.ofMillis(System.currentTimeMillis() - lastSeen).toString())
				.append(", address=").append(lastAddress);

		repr.append("]");

		return repr.toString();
	}
}
