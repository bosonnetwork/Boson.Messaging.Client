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

import java.security.MessageDigest;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import io.bosonnetwork.Id;
import io.bosonnetwork.Identity;
import io.bosonnetwork.crypto.Hash;
import io.bosonnetwork.json.Json;
import io.bosonnetwork.utils.Bytes;

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
	public static final long DEFAULT_EXPIRATION = Duration.ofDays(7).toMillis();

	@JsonProperty(value = "c", required = true)
	private final Id channelId;
	@JsonProperty(value = "sid", required = true)
	private final Id sessionId;
	@JsonProperty(value = "i", required = true)
	private final Id inviter;
	@JsonProperty(value = "ie")
	private final @Nullable Id invitee;
	@JsonProperty(value = "e", required = true)
	@JsonInclude(JsonInclude.Include.NON_DEFAULT)
	private final long expiration;
	@JsonProperty(value = "sig", required = true)
	private final byte[] sig;

	@JsonProperty(value = "sk", required = true)
	private final byte[] sessionKey;

	/**
	 * Creates an invite ticket for JSON deserialization.
	 *
	 * @param channelId the channel identifier
	 * @param sessionId the session identifier
	 * @param inviter the inviter identifier
	 * @param invitee the invitee identifier, or {@code null} for a bearer ticket
	 * @param expiration the expiration time in milliseconds since the epoch
	 * @param sig the ticket signature
	 * @param sessionKey the optional session key associated with the ticket
	 */
	@JsonCreator
	protected InviteTicket(@JsonProperty(value = "c", required = true) Id channelId,
	                    @JsonProperty(value = "sid", required = true) Id sessionId,
	                    @JsonProperty(value = "i", required = true) Id inviter,
	                    @JsonProperty(value = "ie") @Nullable Id invitee,
	                    @JsonProperty(value = "e") long expiration,
	                    @JsonProperty(value = "sig", required = true) byte[] sig,
	                    @JsonProperty(value = "sk", required = true) byte[] sessionKey) {
		this.channelId = Objects.requireNonNull(channelId, "channelId");
		this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
		this.inviter = Objects.requireNonNull(inviter, "inviter");
		this.invitee = invitee;
		this.expiration = expiration;
		this.sig = Objects.requireNonNull(sig, "sig").clone();
		this.sessionKey = Objects.requireNonNull(sessionKey, "sessionKey").clone();
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
	 * Returns the session identifier associated with this ticket.
	 *
	 * @return the session identifier
	 */
	public Id getSessionId() {
		return sessionId;
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
	 * @return an {@link Optional} holding the invitee identifier, or an empty {@code Optional}
	 *         for a bearer ticket
	 */
	public Optional<Id> getInvitee() {
		return Optional.ofNullable(invitee);
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
	 * Returns a defensive copy of the session key included with this ticket.
	 *
	 * @return a clone of the session key; never {@code null}
	 */
	public byte[] getSessionKey() {
		return sessionKey.clone();
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
	public boolean isGenuine() {
		MessageDigest sha256 = Hash.sha256();
		sha256.reset();
		sha256.update(channelId.bytesUnsafe());
		sha256.update(sessionId.bytesUnsafe());
		sha256.update(inviter.bytesUnsafe());
		if (invitee != null)
			sha256.update(invitee.bytesUnsafe());
		sha256.update(Bytes.fromLong(expiration));

		return inviter.toSignatureKey().verify(sha256.digest(), sig);
	}

	/**
	 * Creates a revised {@code InviteTicket} by updating the re-encrypted session key.
	 *
	 * @param sessionKey the re-encrypted session key
	 * @return a new {@code InviteTicket} instance with the updated session key
	 */
	public InviteTicket revise(byte[] sessionKey) {
		return new InviteTicket(channelId, sessionId, inviter, invitee, expiration, sig, sessionKey);
	}

	/**
	 * Creates a new {@code InviteTicket} instance with the specified parameters.
	 *
	 * @param inviter the {@link Identity} of the inviter that creates this ticket
	 * @param channelId the unique identifier of the channel associated with this ticket
	 * @param sessionId the unique identifier of the session associated with this ticket
	 * @param invitee the unique identifier of the invitee, or {@code null} for a bearer ticket
	 * @param expiration the expiration time in milliseconds since the epoch
	 * @param sessionKey the session key associated with this ticket; must not be {@code null}
	 * @return a new {@code InviteTicket} instance containing the provided details and a signature
	 */
	public static InviteTicket create(Identity inviter, Id channelId, Id sessionId, @Nullable Id invitee,
									   long expiration, byte[] sessionKey) {
		MessageDigest sha256 = Hash.sha256();
		sha256.reset();
		sha256.update(channelId.bytesUnsafe());
		sha256.update(sessionId.bytesUnsafe());
		sha256.update(inviter.getId().bytesUnsafe());
		if (invitee != null)
			sha256.update(invitee.bytesUnsafe());
		sha256.update(Bytes.fromLong(expiration));
		byte[] sig = inviter.sign(sha256.digest());

		return new InviteTicket(channelId, sessionId, inviter.getId(), invitee, expiration, sig, sessionKey);
	}

	/**
	 * Deserializes a byte array into an {@code InviteTicket} object.
	 *
	 * @param bytes the byte array representing the serialized {@code InviteTicket}; must not be {@code null}
	 * @return an {@code InviteTicket} instance deserialized from the provided byte array
	 */
	public static InviteTicket fromBytes(byte[] bytes) {
		return Json.parse(bytes, InviteTicket.class);
	}

	/**
	 * Serializes this {@code InviteTicket} into a byte array.
	 *
	 * @return a byte array representing the serialized form of this {@code InviteTicket}; never {@code null}
	 */
	public byte[] toBytes() {
		return Json.toBytes(this);
	}

	/**
	 * Deserializes a JSON string into an {@code InviteTicket} object.
	 *
	 * @param json the JSON string representing the {@code InviteTicket}; must not be {@code null}
	 * @return an {@code InviteTicket} instance deserialized from the provided JSON string
	 */
	public static InviteTicket fromString(String json) {
		return Json.parse(json, InviteTicket.class);
	}

	/**
	 * Returns a JSON string representation of this object.
	 *
	 * @return a string containing the JSON representation of this {@code InviteTicket} instance
	 */
	@Override
	public String toString() {
		return Json.toString(this);
	}
}