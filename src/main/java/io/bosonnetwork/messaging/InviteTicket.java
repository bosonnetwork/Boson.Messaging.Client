package io.bosonnetwork.messaging;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Hash;
import io.bosonnetwork.utils.Json;

public class InviteTicket {
	@JsonProperty(value = "c", required = true)
	private Id channelId;

	@JsonProperty(value = "i", required = true)
	private Id inviter;

	@JsonProperty(value = "p")
	@JsonInclude(Include.NON_EMPTY)
	private boolean isPublic;

	@JsonProperty(value = "e")
	@JsonInclude(Include.NON_EMPTY)
	private long expire;

	@JsonProperty(value = "s", required = true)
	private byte[] sig;

	@JsonCreator
	protected InviteTicket() {
	}

	public InviteTicket(Id channelId, Id inviter, boolean isPublic, long expire, byte[] sig) {
		this.channelId = channelId;
		this.inviter = inviter;
		this.isPublic = isPublic;
		this.expire = expire;
		this.sig = sig;
	}

	public Id getChannelId() {
		return channelId;
	}

	public Id getInviter() {
		return inviter;
	}

	public boolean isPublic() {
		return isPublic;
	}

	public boolean isExpired() {
		return expire < System.currentTimeMillis();
	}

	public boolean isValid(Id invitee) {
		MessageDigest shasum = Hash.sha256();
		shasum.reset();
		shasum.update(channelId.bytes());
		shasum.update(inviter.bytes());
		if (isPublic)
			shasum.update(Id.MAX_ID.bytes());
		else
			shasum.update(invitee.bytes());
		shasum.update(ByteBuffer.allocate(Long.BYTES).putLong(expire).array());

		return inviter.toSignatureKey().verify(shasum.digest(), sig);
	}

	public static InviteTicket deserialize(byte[] data) {
		Objects.requireNonNull(data, "data");

		try {
			return Json.cborMapper().readValue(data, InviteTicket.class);
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public byte[] serialize() {
		try {
			return Json.cborMapper().writeValueAsBytes(this);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public String toString() {
		try {
			return Json.objectMapper().writeValueAsString(this);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
	}
}
