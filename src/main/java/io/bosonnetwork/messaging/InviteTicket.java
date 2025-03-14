package io.bosonnetwork.messaging;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Hash;

public class InviteTicket {
	@JsonProperty("c")
	private Id channelId;

	@JsonProperty("i")
	private Id inviter;

	@JsonProperty("p")
	@JsonInclude(Include.NON_EMPTY)
	private boolean isPublic;

	@JsonProperty("e")
	@JsonInclude(Include.NON_EMPTY)
	private long expire;

	@JsonProperty("s")
	private byte[] sig;

	@JsonCreator
	public InviteTicket(@JsonProperty(value = "c", required = true) Id channelId,
			@JsonProperty(value = "i", required = true) Id inviter,
			@JsonProperty(value = "p", defaultValue = "false") boolean isPublic,
			@JsonProperty(value = "e", required = true) long expire,
			@JsonProperty(value = "s", required = true) byte[] sig) {
		Objects.requireNonNull(channelId, "channelId");
		Objects.requireNonNull(inviter, "inviter");
		Objects.requireNonNull(sig, "sig");

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

	@Override
	public String toString() {
		StringBuilder repr = new StringBuilder(256);
		repr.append("InviteTicket[channel=").append(channelId.toString())
			.append(", invitor=").append(inviter.toString())
			.append(isPublic ? ", public" : "")
			.append(", expiration=").append(Instant.ofEpochMilli(expire))
			.append(isExpired() ? ", expired" : "")
			.append(']');

		return repr.toString();
	}
}
