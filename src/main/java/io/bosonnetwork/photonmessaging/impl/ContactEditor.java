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

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.NullUnmarked;

import io.bosonnetwork.photonmessaging.Contact;

@NullUnmarked
public class ContactEditor implements Contact.Editor {
	private byte[] sessionKey;
	private String name;
	private String remark;
	private String tags;
	private boolean muted;
	private boolean blocked;
	private long updatedAt;
	private int revision;

	private final PhotonContact origin;
	private boolean modified;

	protected ContactEditor(PhotonContact contact) {
		this.origin = contact;
		this.sessionKey = contact.getSessionKey();
		this.name = contact.getName().orElse(null);
		this.remark = contact.getRemark().orElse(null);
		this.tags = contact.getTags().orElse(null);
		this.muted = contact.isMuted();
		this.blocked = contact.isBlocked();
		this.updatedAt = contact.getUpdatedAt();
		this.revision = contact.getRevision();
		this.modified = false;
	}

	protected ContactEditor setSessionKey(byte[] sessionKey) {
		if (!Arrays.equals(this.sessionKey, sessionKey)) {
			this.sessionKey = sessionKey;
			this.modified = true;
			this.updatedAt = System.currentTimeMillis();
		}
		return this;
	}

	protected ContactEditor setName(String name) {
		if (!Objects.equals(this.name, name)) {
			this.name = name;
			this.modified = true;
			this.updatedAt = System.currentTimeMillis();
		}
		return this;
	}

	@Override
	public ContactEditor setRemark(String remark) {
		if (!Objects.equals(this.remark, remark)) {
			this.remark = remark;
			this.modified = true;
			this.updatedAt = System.currentTimeMillis();
		}
		return this;
	}

	@Override
	public ContactEditor setTags(String tags) {
		if (!Objects.equals(this.tags, tags)) {
			this.tags = tags;
			this.modified = true;
			this.updatedAt = System.currentTimeMillis();
		}
		return this;
	}

	@Override
	public ContactEditor setMuted(boolean muted) {
		if (this.muted != muted) {
			this.muted = muted;
			this.modified = true;
			this.updatedAt = System.currentTimeMillis();
		}
		return this;
	}

	@Override
	public ContactEditor setBlocked(boolean blocked) {
		if (this.blocked != blocked) {
			this.blocked = blocked;
			this.modified = true;
			this.updatedAt = System.currentTimeMillis();
		}
		return this;
	}

	protected ContactEditor setUpdatedAt(long updatedAt) {
		if (this.updatedAt != updatedAt) {
			this.updatedAt = updatedAt;
			this.modified = true;
		}
		return this;
	}

	protected ContactEditor setRevision(int revision) {
		if (origin.getRevision() != revision) {
			this.revision = revision;
			this.modified = true;
		}
		return this;
	}

	protected ContactEditor patch(JsonNode changes) {
		Iterator<Map.Entry<String, JsonNode>> fields = changes.fields();
		while (fields.hasNext()) {
			final Map.Entry<String, JsonNode> field = fields.next();
			final String fieldName = field.getKey();
			final JsonNode value = field.getValue();
			switch (fieldName) {
				case "t" -> {}
				case "sk" -> {
					try {
						setSessionKey(value.binaryValue());
					} catch (IOException e) {
						throw new IllegalArgumentException("invalid sessionKey", e);
					}
				}
				case "n" -> setName(value.textValue());
				case "r" -> setRemark(value.textValue());
				case "ts" -> setTags(value.textValue());
				case "m" -> setMuted(value.booleanValue());
				case "b" -> setBlocked(value.booleanValue());
				case "u" -> setUpdatedAt(value.longValue());
				case "v" -> setRevision(value.intValue());
				default -> {} // ignore "id", "v" and the unknown fields
			}
		}
		return this;
	}

	@Override
	public PhotonContact build() {
		if (!modified)
			return origin;

		if (origin instanceof Friend)
			return new Friend(origin.getId(), sessionKey, name, origin.getAvatar().orElse(null), remark, tags,
					muted, blocked, origin.getCreatedAt(), updatedAt, revision);
		else if (origin instanceof PhotonChannel channel)
			return new PhotonChannel(channel.getId(), sessionKey, channel.getOwnerId(), channel.getPermission(),
					name, channel.getNotice().orElse(null), channel.isAnnounce(), remark, tags,
					muted, blocked, channel.getCreatedAt(), updatedAt, revision);
		else
			return new AutoContact(origin.getId(), name, origin.getAvatar().orElse(null), remark, tags,
					muted, blocked, origin.getCreatedAt(), updatedAt);
	}
}