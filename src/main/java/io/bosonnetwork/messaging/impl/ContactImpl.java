package io.bosonnetwork.messaging.impl;

import java.time.Instant;

import io.bosonnetwork.Id;
import io.bosonnetwork.messaging.Contact;

public class ContactImpl extends Contact {
	// for local storage
	public ContactImpl(Id id, Id homePeerId, boolean auto, String name, boolean avatar,
			String remark, String tags, boolean muted, boolean blocked,
			long created, long lastModified, long lastUpdated) {
		super(id, homePeerId, auto, name, avatar, remark, tags,
				muted, blocked, created, lastModified, lastUpdated);
	}

	// for contact synchronization
	public ContactImpl(Id id, Id homePeerId, String remark, String tags, boolean muted, boolean blocked,
			long created, long lastModified) {
		this(id, homePeerId, false, null, false, remark, tags, muted, blocked, created, lastModified, -1);
	}

	public ContactImpl(Id id, Id homePeerId, boolean auto) {
		super(id, homePeerId, auto);
	}

	public static Contact auto(Id id, Id homePeerId) {
		return new ContactImpl(id, homePeerId, true);
	}

	public static Contact auto(Id id) {
		return new ContactImpl(id, null, true);
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
		StringBuilder repr = new StringBuilder();

		repr.append("Contact:")
			.append(getId().toBase58String()).append('[');

		if (getHomePeerId() != null)
			repr.append("homePeer= ").append(getHomePeerId().toBase58String()).append(", ");

		if (getName() != null)
			repr.append("name= ").append(getName()).append(", ");

		if (hasAvatar())
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

		repr.append("created= ").append(Instant.ofEpochMilli(getCreated())).append(", ")
			.append("modified= ").append(Instant.ofEpochMilli(getLastModified())).append(']');

		return repr.toString();
	}
}
