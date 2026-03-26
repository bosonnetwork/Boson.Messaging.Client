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
 
package io.bosonnetwork.messaging;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Hash;
import io.bosonnetwork.utils.Hex;

/**
 * Represents an invitation ticket used to join a channel.
 * <p>
 * A ticket contains the channel identifier, inviter, optional invitee, expiration
 * time, signature, and an optional session key used by channel members.
 */
public class InviteTicket {
	/**
	 * Default ticket expiration duration in milliseconds.
	 * The current value corresponds to 7 days.
	 */
	public static long DEFAULT_EXPIRATION = 7 * 24 * 60 * 60 * 1000; // 7 days

	@JsonProperty(value = "c", required = true)
	private final Id channelId;
	@JsonProperty(value = "i", required = true)
	private final Id inviter;
	@JsonProperty(value = "ie")
	private final Id invitee;
	@JsonProperty(value = "e", required = true)
	@JsonInclude(JsonInclude.Include.NON_DEFAULT)
	private final long expiration;
	@JsonProperty(value = "s", required = true)
	private final byte[] sig;

	@JsonProperty(value = "sk")
	@JsonInclude(Include.NON_EMPTY)
	private final byte[] sessionKey;

	/**
	 * Creates an invite ticket for JSON deserialization.
	 *
	 * @param channelId the channel identifier
	 * @param inviter the inviter identifier
	 * @param invitee the invitee identifier, or {@code null} for a bearer ticket
	 * @param expiration the expiration time in milliseconds since the epoch
	 * @param sig the ticket signature
	 * @param sessionKey the optional session key associated with the ticket
	 */
	@JsonCreator
	public InviteTicket(@JsonProperty(value = "c", required = true) Id channelId,
			@JsonProperty(value = "i", required = true) Id inviter,
			@JsonProperty(value = "ie") Id invitee,
			@JsonProperty(value = "e") long expiration,
			@JsonProperty(value = "s", required = true) byte[] sig,
			@JsonProperty(value = "sk") byte[] sessionKey) {
		this.channelId = Objects.requireNonNull(channelId, "channelId");
		this.inviter = Objects.requireNonNull(inviter, "inviter");
		this.invitee = invitee;
		this.expiration = expiration;
		this.sig = Objects.requireNonNull(sig, "sig");
		this.sessionKey = sessionKey == null || sessionKey.length == 0 ? null : sessionKey;
	}

	/**
	 * Returns the channel identifier associated with this ticket.
	 *
	 * @return the channel identifier
	 */
	public Id getChannelId() {
		return channelId;
	}

	/**
	 * Returns the inviter identifier associated with this ticket.
	 *
	 * @return the inviter identifier
	 */
	public Id getInviter() {
		return inviter;
	}

	/**
	 * Returns the invitee identifier if this is a named ticket.
	 *
	 * @return the invitee identifier, or {@code null} for a bearer ticket
	 */
	public Id getInvitee() {
		return invitee;
	}

	/**
	 * Indicates whether this ticket is a named ticket.
	 *
	 * @return {@code true} if the ticket targets a specific invitee; otherwise {@code false}
	 */
	public boolean isNamedTicket() {
		return invitee != null;
	}

	/**
	 * Indicates whether this ticket is a bearer ticket.
	 *
	 * @return {@code true} if the ticket does not target a specific invitee; otherwise {@code false}
	 */
	public boolean isBearerTicket() {
		return invitee == null;
	}

	/**
	 * Returns the session key included with this ticket, if any.
	 *
	 * @return the session key, or {@code null} if none was provided
	 */
	public byte[] getSessionKey() {
		return sessionKey;
	}

	/**
	 * Indicates whether this ticket has expired.
	 *
	 * @return {@code true} if the expiration time is before the current system time; otherwise {@code false}
	 */
	public boolean isExpired() {
		return expiration < System.currentTimeMillis();
	}

	/**
	 * Verifies the ticket signature against the ticket contents.
	 *
	 * @return {@code true} if the signature is valid; otherwise {@code false}
	 */
	public boolean isValid() {
		MessageDigest sha256 = Hash.sha256();
		sha256.reset();
		sha256.update(channelId.bytes());
		sha256.update(inviter.bytes());
		if (invitee != null)
			sha256.update(invitee.bytes());
		sha256.update(ByteBuffer.allocate(Long.BYTES).putLong(expiration).array());

		return inviter.toSignatureKey().verify(sha256.digest(), sig);
	}

	/**
	 * Returns a stub version of this ticket without the session-key payload.
	 * If the session key is not present, it returns the current instance.
	 * Otherwise, creates a new InviteTicket instance with the same
	 * channel ID, inviter, invitee, expiration, and signature, but with
	 * a null session key.
	 *
	 * @return a stub version of the InviteTicket, or the current instance
	 *         if the session key is null
	 */
	public InviteTicket stub() {
		if (sessionKey == null)
			return this;

		return new InviteTicket(channelId, inviter, invitee, expiration, sig, null);
	}

	/**
	 * Returns a human-readable representation of this ticket.
	 *
	 * @return a string representation of this ticket
	 */
	@Override
	public String toString() {
		return "InviteTicket {" +
				"channelId=" + channelId +
				", inviter=" + inviter +
				(invitee != null ? (", invitee=" + invitee) : "") +
				", expiration=" + expiration +
				", sig=" + Hex.encode(sig) +
				(sessionKey != null ? ", sessionKey=****" : "") +
				'}';
	}
}