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

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;

import io.bosonnetwork.Id;
import io.bosonnetwork.photonmessaging.Channel;

public class ChannelEditor implements Channel.Editor {
	private Id ownerId;
	private Channel.Permission permission;
	private String name;
	private String notice;
	private boolean announce;

	private final PhotonChannel origin;
	private boolean modified;

	protected ChannelEditor(PhotonChannel channel) {
		this.origin = channel;
		this.ownerId = channel.getOwnerId();
		this.permission = channel.getPermission();
		this.name = channel.getName();
		this.notice = channel.getNotice();
		this.announce = channel.isAnnounce();
		this.modified = false;
	}

	protected ChannelEditor setOwnerId(Id ownerId) {
		if (!Objects.equals(this.ownerId, ownerId)) {
			this.ownerId = ownerId;
			this.modified = true;
		}
		return this;
	}

	@Override
	public ChannelEditor setPermission(Channel.Permission permission) {
		if (this.permission != permission) {
			this.permission = permission;
			this.modified = true;
		}
		return this;
	}

	@Override
	public ChannelEditor setName(String name) {
		if (!Objects.equals(this.name, name)) {
			this.name = name;
			this.modified = true;
		}
		return this;
	}

	@Override
	public ChannelEditor setNotice(String notice) {
		if (!Objects.equals(this.notice, notice)) {
			this.notice = notice;
			this.modified = true;
		}
		return this;
	}

	@Override
	public ChannelEditor setAnnounce(boolean announce) {
		if (this.announce != announce) {
			this.announce = announce;
			this.modified = true;
		}
		return this;
	}

	protected ChannelEditor patch(JsonNode changes) {
		Iterator<Map.Entry<String, JsonNode>> fields = changes.fields();
		while (fields.hasNext()) {
			final Map.Entry<String, JsonNode> field = fields.next();
			final String fieldName = field.getKey();
			final JsonNode value = field.getValue();
			switch (fieldName) {
				case "p" -> setPermission(Channel.Permission.valueOf(value.intValue()));
				case "n" -> setName(value.textValue());
				case "nt" -> setNotice(value.textValue());
				case "a" -> setAnnounce(value.booleanValue());
				default -> {}
			}
		}
		return this;
	}

	@Override
	public PhotonChannel build() {
		if (!modified)
			return origin;

		return new PhotonChannel(origin.getId(), origin.getSessionKey(), ownerId, permission,
				name, notice, announce, origin.getRemark(), origin.getTags(),
				origin.isMuted(), origin.isBlocked(), origin.getCreatedAt(), System.currentTimeMillis(),
				origin.getRevision());
	}
}