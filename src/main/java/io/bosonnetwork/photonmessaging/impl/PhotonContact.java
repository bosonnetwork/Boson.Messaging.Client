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

package io.bosonnetwork.photonmessaging.impl;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.bosonnetwork.CryptoContext;
import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.CryptoBox;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.json.Json;
import io.bosonnetwork.photonmessaging.Contact;
import io.bosonnetwork.photonmessaging.impl.dto.OpaqueContact;

/**
 * Represents a contact entry within the photon messaging.
 * This class serves as a data model for storing contact information, including
 * identity, session keys, profile details, and synchronization state.
 */
@JsonPropertyOrder({"id", "t", "sk", "n", "r", "ts", "m", "b", "c", "u", "v"})
public abstract class PhotonContact implements Contact {
	public static final int ENCRYPTED_SESSION_KEY_BYTES = Signature.PrivateKey.BYTES +
			CryptoBox.MAC_BYTES + CryptoBox.Nonce.BYTES;

	private static final long STALE_TIME = 6 * 60 * 60 * 1000; // 6 hours

	@JsonProperty(value = "id", required = true)
	private final Id id;

	@JsonProperty("sk")
	@JsonInclude(Include.NON_EMPTY)
	private final byte[] sessionKey;

	@JsonProperty("n")
	@JsonInclude(Include.NON_EMPTY)
	private final String name;

	@JsonProperty("r")
	@JsonInclude(Include.NON_EMPTY)
	private final String remark;

	@JsonProperty("ts")
	@JsonInclude(Include.NON_EMPTY)
	private String tags;

	@JsonProperty("m")
	@JsonInclude(Include.NON_DEFAULT)
	private boolean muted;

	@JsonProperty("b")
	@JsonInclude(Include.NON_DEFAULT)
	private boolean blocked;

	@JsonProperty("c")
	private final long createdAt;

	@JsonProperty("u")
	private final long updatedAt;

	@JsonProperty("v")
	private final int revision;

	private final String avatar;

	private transient String displayName;
	private transient long lastRefresh;

	/**
	 * Full constructor to initialize all fields of a Contact.
	 *
	 * @param id the unique identifier for this contact
	 * @param sessionKey the private session key bytes for secure communication
	 * @param name the name of the contact
	 * @param remark a local alias or remark for the contact
	 * @param tags labels or tags associated with the contact
	 * @param muted whether notifications are muted for this contact
	 * @param blocked whether this contact is blocked
	 * @param createdAt the timestamp when the contact was created
	 * @param updatedAt the timestamp of the last update
	 * @param revision the synchronization revision number
	 */
	protected PhotonContact(Id id, byte[] sessionKey, String name, String avatar, String remark, String tags,
	                        boolean muted, boolean blocked, long createdAt, long updatedAt, int revision) {
		this.id = id;
		this.name = name;
		this.avatar = avatar;
		this.remark = remark;
		this.tags = tags;
		this.muted = muted;
		this.blocked = blocked;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt == 0 ? createdAt : updatedAt;
		this.revision = revision;

		this.sessionKey = checkSessionKey(sessionKey);
	}

	private static byte[] checkSessionKey(byte[] key) {
		if (key == null || key.length == 0)
			return null;

		if (key.length != ENCRYPTED_SESSION_KEY_BYTES)
			throw new IllegalArgumentException("invalid session key");

		return key;
	}

	/**
	 * Returns the unique identifier of the contact.
	 *
	 * @return the contact ID
	 */
	public Id getId() {
		return id;
	}

	/**
	 * Retrieves the type of the contact.
	 *
	 * @return an instance of {@link Contact.Type} representing the type of the contact
	 */
	@JsonProperty("t")
	@Override
	public abstract Contact.Type getType();

	/**
	 * Returns the session private key.
	 *
	 * @return the session key bytes
	 */
	public byte[] getSessionKey() {
		return sessionKey;
	}

	/**
	 * Checks if the contact has a session key associated with it.
	 *
	 * @return true if the session key is not null, false otherwise
	 */
	public boolean hasSessionKey() {
		return sessionKey != null;
	}

	/**
	 * Returns the name of the contact.
	 *
	 * @return the contact name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Returns the local remark/alias for the contact.
	 *
	 * @return the remark string
	 */
	public String getRemark() {
		return remark;
	}

	/**
	 * Returns the tags associated with the contact.
	 *
	 * @return the tag string
	 */
	public String getTags() {
		return tags;
	}

	/**
	 * Returns whether the contact is muted.
	 *
	 * @return true if muted, false otherwise
	 */
	public boolean isMuted() {
		return muted;
	}

	/**
	 * Returns whether the contact is blocked.
	 *
	 * @return true if blocked, false otherwise
	 */
	public boolean isBlocked() {
		return blocked;
	}

	/**
	 * Returns the creation timestamp of this contact entry.
	 *
	 * @return the creation time in milliseconds
	 */
	public long getCreatedAt() {
		return createdAt;
	}

	/**
	 * Returns the timestamp of the last update to this contact.
	 *
	 * @return the update time in milliseconds
	 */
	public long getUpdatedAt() {
		return updatedAt;
	}

	/**
	 * Returns the synchronization revision number.
	 *
	 * @return the current revision
	 */
	public int getRevision() {
		return revision;
	}

	/**
	 * Retrieves the avatar URL associated with the contact.
	 *
	 * @return a string representing the avatar URL or identifier
	 */
	public String getAvatar() {
		return avatar;
	}

	/**
	 * Checks if the contact has an avatar associated with it.
	 *
	 * @return true if the avatar is not null, false otherwise
	 */
	public boolean hasAvatar() {
		return avatar != null;
	}

	public String getDisplayName() {
		String dn = displayName;
		if (dn == null) {
			if (remark != null && !remark.isEmpty())
				dn = remark;
			else if (name != null && !name.isEmpty())
				dn = name;
			else
				dn = id.toAbbrString();

			displayName = dn;
		}

		return dn;
	}

	public boolean is(PhotonContact contact) {
		if (contact == this)
			return true;

		return Objects.equals(this.id, contact.id);
	}

	protected void refresh() {
		lastRefresh = System.currentTimeMillis();
	}

	// TODO: refresh the contact data with the DHT lookup result?
	protected void refresh(Object data) {
		refresh();
	}

	public boolean isStaled() {
		return System.currentTimeMillis() - lastRefresh > STALE_TIME;
	}

	@Override
	public int compareTo(Contact contact) {
		int rc = getDisplayName().compareTo(contact.getDisplayName());
		return rc != 0 ? rc : getId().compareTo(contact.getId());
	}

	@Override
	public ContactEditor edit() {
		return new ContactEditor(this);
	}

	protected OpaqueContact toOpaque(CryptoContext cryptoContext) {
		return new OpaqueContact(id, revision, cryptoContext.encrypt(Json.toBytes(this)));
	}

	protected static PhotonContact fromOpaque(OpaqueContact opaque, CryptoContext cryptoContext) throws IllegalArgumentException {
		PhotonContact contact;
		try {
			byte[] data = cryptoContext.decrypt(opaque.data());
			contact = (PhotonContact) Json.parse(data, Contact.class);
		} catch (Exception e) {
			throw new IllegalArgumentException("Invalid contact data", e);
		}

		if (!contact.getId().equals(opaque.id()))
			throw new IllegalArgumentException("Contact ID mismatch");
		return contact.edit().setRevision(opaque.revision()).build();
	}
}