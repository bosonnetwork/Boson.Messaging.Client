package io.bosonnetwork.messaging;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.bosonnetwork.CryptoContext;
import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.CryptoBox;
import io.bosonnetwork.crypto.CryptoException;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.messaging.impl.ContactBuilder;
import io.vertx.core.Vertx;

@JsonDeserialize(builder = ContactBuilder.class)
public abstract class Contact implements Comparable<Contact> {
	private static final long STALE_TIME = TimeUnit.HOURS.toMillis(6);

	@JsonProperty("id")
	private Id id;

	private boolean auto;

	private Signature.KeyPair sessionKeyPair;
	private transient CryptoBox.KeyPair encryptionKeyPair;
	private transient Id sessionId;

	private transient CryptoContext rxCryptoContext;
	private transient CryptoContext txCryptoContext;

	// @JsonProperty("p")
	// @JsonInclude(Include.NON_NULL)
	private Id homePeerId;

	// @JsonProperty("n")
	// @JsonInclude(Include.NON_EMPTY)
	private String name;
	// @JsonProperty("a")
	// @JsonInclude(Include.NON_EMPTY)
	private boolean avatar;

	@JsonProperty("r")
	@JsonInclude(Include.NON_EMPTY)
	private String remark;
	@JsonProperty("ts")
	@JsonInclude(Include.NON_EMPTY)
	private String tags;
	@JsonProperty("d")
	@JsonInclude(Include.NON_DEFAULT)
	private boolean muted;
	@JsonProperty("b")
	@JsonInclude(Include.NON_DEFAULT)
	private boolean blocked;
	@JsonProperty("c")
	@JsonInclude(Include.NON_EMPTY)
	private long created;
	@JsonProperty("m")
	@JsonInclude(Include.NON_EMPTY)
	private long lastModified;

	@JsonProperty("e")
	@JsonInclude(Include.NON_DEFAULT)
	private boolean deleted;

	@JsonProperty("v")
	private int revision;

	private boolean modified;

	private long lastUpdated;
	private transient String displayName;


	public static class Types {
		public static final int UNKNOWN = 0;
		public static final int CONTACT = 1;
		public static final int CHANNEL = 2;
	}

	protected Contact(Id id, Id homePeerId, boolean auto, byte[] sessionKey,
			 String name, boolean avatar, String remark, String tags, boolean muted, boolean blocked,
			long created, long lastModified,  long lastUpdated, boolean deleted,
			int revision, boolean modified) {
		this.id = id;
		this.homePeerId = homePeerId;

		this.auto = auto;

		this.name = name;
		this.avatar = avatar;

		this.remark = remark;
		this.tags = tags;
		this.muted = muted;
		this.blocked = blocked;
		this.created = created;
		this.lastModified = lastModified;
		this.deleted = deleted;
		this.revision = revision;

		this.modified = modified;
		this.lastUpdated = lastUpdated;

		if (sessionKey != null && sessionKey.length > 0)
			initSessionKey(sessionKey);
}

	protected Contact(Id id, Id homePeerId) {
		this.id = id;
		this.homePeerId = homePeerId;
		this.auto = true;
		this.created = System.currentTimeMillis();
		this.lastModified = this.created;
		this.modified = false;
		this.deleted = false;
		this.revision = 1;
		this.lastUpdated = -1;
	}

	public Id getId() {
		return id;
	}

	public Id getHomePeerId() {
		return homePeerId;
	}

	protected void setHomePeerId(Id homePeerId) {
		this.homePeerId = homePeerId;
	}

	@JsonProperty("t")
	public abstract int getType();

	public boolean isAuto() {
		return auto;
	}

	protected void setAuto(boolean auto) {
		this.auto = auto;
	}

	private CryptoContext getSelfEncryptionContext() {
		if (Vertx.currentContext() == null)
			return null;

		return Vertx.currentContext().getLocal("SelfEncryptionContext");
	}

	@JsonProperty("sk")
	@JsonInclude(Include.NON_EMPTY)
	public byte[] getSelfEncryptedSessionKey() {
		if (sessionKeyPair == null)
			return null;

		CryptoContext ctx = getSelfEncryptionContext();
		// if (ctx == null)
		//	 throw new IllegalStateException("no self encryption context set");

		// return ctx.encrypt(sessionKeyPair.privateKey().bytes());

		return ctx != null ? ctx.encrypt(sessionKeyPair.privateKey().bytes()) :
			sessionKeyPair.privateKey().bytes();
	}

	public byte[] getSessionKey() {
		return sessionKeyPair == null ? null : sessionKeyPair.privateKey().bytes();
	}

	private void initSessionKey(byte[] privateKey) {
		if (privateKey.length == Signature.PrivateKey.BYTES + CryptoBox.MAC_BYTES + CryptoBox.Nonce.BYTES) {
			CryptoContext ctx = getSelfEncryptionContext();
			if (ctx == null)
				throw new IllegalStateException("no self encryption context set");

			try {
				privateKey = ctx.decrypt(privateKey);
			} catch (CryptoException e) {
				throw new IllegalArgumentException("invalid session key", e);
			}
		} else if (privateKey.length != Signature.PrivateKey.BYTES) {
			throw new IllegalArgumentException("invalid session key");
		}

		this.sessionKeyPair = Signature.KeyPair.fromPrivateKey(privateKey);
		this.encryptionKeyPair = CryptoBox.KeyPair.fromSignatureKeyPair(sessionKeyPair);
		this.sessionId = Id.of(sessionKeyPair.publicKey().bytes());

	}

	protected void setSessionKey(byte[] privateKey) {
		initSessionKey(privateKey);
		touch();
	}

	public boolean hasSessionKey() {
		return sessionKeyPair != null;
	}

	public Id getSessionId() {
		return sessionId;
	}

	public String getName() {
		return name;
	}

	public boolean getAvatar() {
		return avatar;
	}

	public boolean hasAvatar() {
		return avatar;
	}

	public String getAvatarURI() {
		return avatar ? "bmr://" + homePeerId.toBase58String() + "/" + id.toBase58String() : null;
	}

	public String getRemark() {
		return remark;
	}

	public void setRemark(String remark) {
		this.remark = remark != null && !remark.isEmpty() ? remark : null;
		displayName = null;
		touch();
	}

	public String getTags() {
		return tags;
	}

	public void setTags(String tags) {
		this.tags = tags != null && !tags.isEmpty() ? tags : null;
		touch();
	}

	public boolean isMuted() {
		return muted;
	}

	public void setMuted(boolean muted) {
		this.muted = muted;
		touch();
	}

	public boolean isBlocked() {
		return blocked;
	}

	public void setBlocked(boolean blocked) {
		this.blocked = blocked;
		touch();
	}

	public boolean isDeleted() {
		return deleted;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
		touch();
	}

	public int getRevision() {
		return revision;
	}

	private void incrementRevision() {
		this.revision++;
	}

	public long getCreated() {
		return created;
	}

	public long getLastModified() {
		return lastModified;
	}

	public boolean isModified() {
		return modified;
	}

	public void setSynced() {
		this.modified = false;
	}

	protected void touch() {
		if (this.auto)
			this.auto = false;

		if (!this.modified) {
			this.modified = true;
			this.incrementRevision();
		}

		this.lastModified = System.currentTimeMillis();
	}

	protected void updated() {
		this.lastUpdated = System.currentTimeMillis();
	}

	public long getLastUpdated() {
		return lastUpdated;
	}

	public String getDisplayName() {
		if (displayName == null) {
			if (remark != null && !remark.isEmpty())
				displayName = remark;
			else if (name != null && !name.isEmpty())
				displayName = name;
			else
				displayName = id.toAbbrString();
		}

		return displayName;
	}

	// update the contact or channel information
	public void update(Profile profile) {
		Objects.requireNonNull(profile);
		if (!profile.getId().equals(id))
			throw new IllegalArgumentException("profile does not match the contact");
		if (!profile.isGenuine())
			throw new IllegalArgumentException("profile is not genuine");

		this.homePeerId = profile.getHomePeerId();
		this.name = profile.getName();
		this.avatar = profile.hasAvatar();
		this.displayName = null;

		updated();
	}

	public void update(Contact contact) {
		Objects.requireNonNull(contact);
		if (!contact.getId().equals(id))
			throw new IllegalArgumentException("contact does not matched");

		if (contact.sessionKeyPair != null) {
			this.sessionKeyPair = contact.sessionKeyPair;
			this.encryptionKeyPair = contact.encryptionKeyPair;
		}

		this.homePeerId = contact.homePeerId;
		this.remark = contact.remark;
		this.tags = contact.tags;
		this.muted = contact.muted;
		this.blocked = contact.blocked;
		this.created = contact.created;
		this.lastModified = contact.lastModified;

		this.name = contact.name;
		this.avatar = contact.avatar;

		this.displayName = null;

		updated();
	}

	public boolean isStaled() {
		return System.currentTimeMillis() - lastUpdated > STALE_TIME;
	}

	protected Signature.KeyPair getSessionKeyPair() {
		return sessionKeyPair;
	}

	public byte[] sign(byte[] data) {
		return Signature.sign(data, sessionKeyPair.privateKey());
	}

	public boolean verify(byte[] data, byte[] signature) {
		return Signature.verify(data, signature, sessionKeyPair.publicKey());
	}

	// one-shot encryption
	public byte[] encrypt(Id recipient, byte[] data) {
		// TODO: how to avoid the memory copy?!
		CryptoBox.Nonce nonce = CryptoBox.Nonce.random();
		CryptoBox.PublicKey pk = recipient.toEncryptionKey();
		CryptoBox.PrivateKey sk = encryptionKeyPair.privateKey();
		byte[] cipher = CryptoBox.encrypt(data, pk, sk, nonce);

		byte[] buf = new byte[CryptoBox.Nonce.BYTES + cipher.length];
		System.arraycopy(nonce.bytes(), 0, buf, 0, CryptoBox.Nonce.BYTES);
		System.arraycopy(cipher, 0, buf,CryptoBox. Nonce.BYTES, cipher.length);
		return buf;
	}

	// one-shot decryption
	public byte[] decrypt(Id sender, byte[] data) throws CryptoException {
		if (data.length <= CryptoBox.Nonce.BYTES + CryptoBox.MAC_BYTES)
			throw new CryptoException("Invalid cipher size");

		// TODO: how to avoid the memory copy?!
		byte[] n = Arrays.copyOfRange(data, 0, CryptoBox.Nonce.BYTES);
		CryptoBox.Nonce nonce = CryptoBox.Nonce.fromBytes(n);

		//if (lastPeerNonce != null && nonce.equals(lastPeerNonce))
		//	throw new CryptoException("Duplicated nonce");

		//	lastPeerNonce = nonce;
		CryptoBox.PublicKey pk = sender.toEncryptionKey();
		CryptoBox.PrivateKey sk = encryptionKeyPair.privateKey();
		byte[] cipher = Arrays.copyOfRange(data, CryptoBox.Nonce.BYTES, data.length);
		return CryptoBox.decrypt(cipher, pk, sk, nonce);
	}

	public CryptoContext createCryptoContext(Id id) {
		CryptoBox.PublicKey pk = id.toEncryptionKey();
		CryptoBox box = CryptoBox.fromKeys(pk, encryptionKeyPair.privateKey());
		return new CryptoContext(id, box);
	}

	public CryptoContext getRxCryptoContext() {
		if (rxCryptoContext == null)
			rxCryptoContext = createCryptoContext(id);

		return rxCryptoContext;
	}

	public CryptoContext getTxCryptoContext(Supplier<CryptoContext> supplier) {
		if (txCryptoContext == null)
			txCryptoContext = supplier.get();

		return txCryptoContext;
	}

	public boolean is(Contact contact) {
		if (contact == this)
			return true;

		return Objects.equals(this.id, contact.id);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;

		if (o instanceof Contact that) {
			return Objects.equals(this.id, that.id) &&
					Objects.equals(this.homePeerId, that.homePeerId) &&
					this.getType() == that.getType() &&
					Arrays.equals(this.getSessionKey(), that.getSessionKey()) &&
					Objects.equals(this.name, that.name) &&
					this.avatar == that.avatar &&
					Objects.equals(this.remark, that.remark) &&
					Objects.equals(this.tags, that.tags) &&
					this.muted == that.muted &&
					this.blocked == that.blocked &&
					this.deleted == that.deleted &&
					this.revision == that.revision &&
					this.created == that.created &&
					this.lastModified == that.lastModified;
		}

		return false;
	}

	@Override
	public int compareTo(Contact entry) {
		String n1 = getDisplayName();
		String n2 = entry.getDisplayName();

		int rc = n1.compareTo(n2);
		if (rc != 0)
			return rc;

		return id.compareTo(entry.id);
	}
}
