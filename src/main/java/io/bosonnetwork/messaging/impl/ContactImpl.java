package io.bosonnetwork.messaging.impl;

import java.time.Instant;

import io.bosonnetwork.Id;
import io.bosonnetwork.messaging.Contact;

public class ContactImpl extends Contact {
	// for local storage
	public ContactImpl(Id id, Id homePeerId, boolean auto, byte[] sessionKey,
			String name, boolean avatar, String remark, String tags, boolean muted,
			boolean blocked, long created, long lastModified, long lastUpdated,
			boolean deleted, int revision, boolean modified) {
		super(id, homePeerId, auto, sessionKey, name, avatar, remark, tags,
				muted, blocked, created, lastModified, lastUpdated, deleted, revision, modified);
	}

	/*
	// for contact synchronization
	public ContactImpl(Id id, Id homePeerId, String remark, String tags, boolean muted, boolean blocked,
			long created, long lastModified) {
		this(id, homePeerId, false, null, false, remark, tags, muted, blocked, created, lastModified, -1);
	}
	*/

	private ContactImpl(Id id, Id homePeerId) {
		super(id, homePeerId);
	}

	public static Contact create(Id id, Id homePeerId, byte[] sessionKey, String name, boolean avatar) {
		long now = System.currentTimeMillis();

		return new ContactImpl(id, homePeerId, false, sessionKey, name, avatar,
				null, null, false, false, now, now, -1, false, 1, true);
	}

	public static Contact create(Id id, Id homePeerId, byte[] sessionKey, String remark) {
		long now = System.currentTimeMillis();

		return new ContactImpl(id, homePeerId, false, sessionKey, null, false,
				remark, null, false, false, now, now, -1, false, 1, true);
	}

	public static Contact auto(Id id, Id homePeerId) {
		return new ContactImpl(id, homePeerId);
	}

	public static Contact auto(Id id) {
		return new ContactImpl(id, null);
	}

	@Override
	public int getType() {
		return Types.CONTACT;
	}

	@Override
	public int hashCode() {
		return getId().hashCode() + 0xFFB534A1;
	}

	@Override
	public String toString() {
		StringBuilder repr = new StringBuilder(512);

		repr.append("Contact:")
			.append(getId().toBase58String()).append('[');

		if (getHomePeerId() != null)
			repr.append("homePeer= ").append(getHomePeerId().toBase58String()).append(", ");

		if (getSessionKeyPair() != null)
			repr.append("sessionKey*, ");

		if (getName() != null)
			repr.append("name= ").append(getName()).append(", ");

		if (getAvatar())
			repr.append("avatar, ");

		if (getRemark() != null)
			repr.append("remark= ").append(getRemark()).append(", ");

		if (getTags() != null)
			repr.append("tags= ").append(getTags()).append(", ");

		if (isMuted())
			repr.append("muted, ");

		if (isBlocked())
			repr.append("blocked, ");

		if (isAuto())
			repr.append("auto, ");

		if (isDeleted())
			repr.append("deleted, ");

		repr.append("revision= ").append(getRevision()).append(", ");

		repr.append("created= ").append(Instant.ofEpochMilli(getCreated())).append(", ")
			.append("modified= ").append(Instant.ofEpochMilli(getLastModified())).append(']');

		return repr.toString();
	}
}
