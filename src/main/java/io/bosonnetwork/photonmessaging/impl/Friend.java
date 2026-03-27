package io.bosonnetwork.photonmessaging.impl;

import java.time.Instant;

import io.bosonnetwork.Id;

public class Friend extends AbstractContact {
	protected Friend(Id id) {
		super(id);
	}

	protected Friend(Id id, byte[] sessionKey, String name, String remark, String tags, boolean muted, boolean blocked, long createdAt, long updatedAt, int revision) {
		super(id, sessionKey, name, remark, tags, muted, blocked, createdAt, updatedAt, revision);
	}

	@Override
	public Type getType() {
		return Type.FRIEND;
	}

	@Override
	public int hashCode() {
		return 0x6030AF91 + getId().hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;

		if (o instanceof Friend that)
			return this.getId().equals(that.getId());

		return false;
	}

	@Override
	public String toString() {
		StringBuilder repr = new StringBuilder(256);

		repr.append("Friend: ").append(getId().toBase58String());

		if (getName() != null)
			repr.append(", name=").append(getName());

		if (getRemark() != null)
			repr.append(", remark=").append(getRemark());

		if (getTags() != null)
			repr.append(", tags=").append(getTags());

		if (isMuted())
			repr.append(", muted");

		if (isBlocked())
			repr.append(", blocked");

		repr.append(", createdAt=").append(Instant.ofEpochMilli(getCreatedAt()))
				.append(" updatedAt=").append(Instant.ofEpochMilli(getUpdatedAt()))
				.append(']');

		return repr.toString();
	}
}