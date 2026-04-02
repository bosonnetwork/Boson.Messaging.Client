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
	public Friend dup() {
		return new Friend(getId(), getSessionKey(), getName(), getRemark(), getTags(), isMuted(), isBlocked(), getCreatedAt(), getUpdatedAt(), getRevision());
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