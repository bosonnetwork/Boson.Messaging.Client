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
	public static long DEFAULT_EXPIRATION = 7 * 24 * 60 * 60 * 1000; // 3 days

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

	@JsonProperty("sk")
	@JsonInclude(Include.NON_EMPTY)
	private byte[] sessionKey;

	@JsonCreator
	public InviteTicket(@JsonProperty(value = "c", required = true) Id channelId,
			@JsonProperty(value = "i", required = true) Id inviter,
			@JsonProperty(value = "p", defaultValue = "false") boolean isPublic,
			@JsonProperty(value = "e", required = true) long expire,
			@JsonProperty(value = "s", required = true) byte[] sig,
			@JsonProperty(value = "sk", required = true) byte[] sessionKey) {
		Objects.requireNonNull(channelId, "channelId");
		Objects.requireNonNull(inviter, "inviter");
		Objects.requireNonNull(sig, "sig");
		Objects.requireNonNull(sessionKey, "sessionKey");

		this.channelId = channelId;
		this.inviter = inviter;
		this.isPublic = isPublic;
		this.expire = expire;
		this.sig = sig;
		this.sessionKey = sessionKey;
	}

	private InviteTicket(Id channelId, Id inviter, boolean isPublic, long expire, byte[] sig) {
		Objects.requireNonNull(channelId, "channelId");
		Objects.requireNonNull(inviter, "inviter");
		Objects.requireNonNull(sig, "sig");

		this.channelId = channelId;
		this.inviter = inviter;
		this.isPublic = isPublic;
		this.expire = expire;
		this.sig = sig;
		this.sessionKey = null;
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

	public byte[] getSessionKey() {
		return sessionKey;
	}

	public boolean isValid(Id invitee) {
		MessageDigest shasum = Hash.sha256();
		shasum.update(channelId.bytes());
		shasum.update(inviter.bytes());
		if (isPublic)
			shasum.update(Id.MAX_ID.bytes());
		else
			shasum.update(invitee.bytes());
		shasum.update(ByteBuffer.allocate(Long.BYTES).putLong(expire).array());

		return inviter.toSignatureKey().verify(shasum.digest(), sig);
	}

	public InviteTicket proof() {
		return new InviteTicket(channelId, inviter, isPublic, expire, sig);
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
