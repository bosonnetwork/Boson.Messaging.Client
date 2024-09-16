package io.bosonnetwork.messaging.impl;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.bosonnetwork.crypto.Random;
import io.bosonnetwork.utils.Hex;

public class ContactSequence {
	@JsonProperty("id")
	private String id;

	@JsonProperty("t")
	private long timestamp;

	protected ContactSequence() {
		this.id = genId();
		this.timestamp = System.currentTimeMillis();
	}

	protected ContactSequence(String id, long timestamp) {
		this.id = id;
		this.timestamp = timestamp;
	}

	public String getId() {
		return id;
	}

	public long getTimestamp() {
		return timestamp;
	}

	protected static String genId() {
		byte[] binId = new byte[16];
		Random.secureRandom().nextBytes(binId);
		return Hex.encode(binId);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;

		if (o instanceof ContactSequence that)
			return Objects.equals(this.id, that.id) &&
					this.timestamp == that.timestamp;

		return false;
	}

	@Override
	public String toString() {
		return "Seq: " + id + "/" + timestamp;
	}

	// workaround for internal access
	protected static class AccessHelper {
		public static ContactSequence create(String id, long timestamp) {
			return new ContactSequence(id, timestamp);
		}
	}
}

