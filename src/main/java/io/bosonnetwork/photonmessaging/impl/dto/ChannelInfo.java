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

package io.bosonnetwork.photonmessaging.impl.dto;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import io.bosonnetwork.Id;
import io.bosonnetwork.photonmessaging.Channel;

/**
 * A plain class rather than a record: Jackson's record support calls
 * {@code Class.getRecordComponents()}, which the Android runtime does not implement, so a record
 * wire type fails to deserialize on-device. Accessors keep the record-style names so call sites
 * are unaffected.
 */
public final class ChannelInfo {
	@JsonProperty(value = "id", required = true)
	private final Id channelId;
	@JsonProperty(value = "o", required = true)
	private final Id ownerId;
	@JsonProperty(value = "sid", required = true)
	private final Id sessionId;
	@JsonProperty(value = "sk")
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	private final byte @Nullable [] sessionKey;
	@JsonProperty(value = "p", required = true)
	private final Channel.Permission permission;
	@JsonProperty(value = "n")
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private final String name;
	@JsonProperty(value = "nt")
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	private final @Nullable String notice;
	@JsonProperty(value = "a")
	@JsonInclude(JsonInclude.Include.NON_DEFAULT)
	private final boolean announce;
	@JsonProperty(value = "c")
	private final long createdAt;
	@JsonProperty(value = "u")
	private final long updateAt;
	@JsonProperty(value = "m")
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	private final List<Channel.Member> members;

	@JsonCreator
	public ChannelInfo(@JsonProperty(value = "id", required = true) Id channelId,
			@JsonProperty(value = "o", required = true) Id ownerId,
			@JsonProperty(value = "sid", required = true) Id sessionId,
			@JsonProperty(value = "sk") byte @Nullable [] sessionKey,
			@JsonProperty(value = "p", required = true) Channel.Permission permission,
			@JsonProperty(value = "n") String name,
			@JsonProperty(value = "nt") @Nullable String notice,
			@JsonProperty(value = "a") boolean announce,
			@JsonProperty(value = "c") long createdAt,
			@JsonProperty(value = "u") long updateAt,
			@JsonProperty(value = "m") List<Channel.Member> members) {
		this.channelId = channelId;
		this.ownerId = ownerId;
		this.sessionId = sessionId;
		this.sessionKey = sessionKey;
		this.permission = permission;
		this.name = name;
		this.notice = notice;
		this.announce = announce;
		this.createdAt = createdAt;
		this.updateAt = updateAt;
		this.members = members;
	}

	public Id channelId() {
		return channelId;
	}

	public Id ownerId() {
		return ownerId;
	}

	public Id sessionId() {
		return sessionId;
	}

	public byte @Nullable [] sessionKey() {
		return sessionKey;
	}

	public Channel.Permission permission() {
		return permission;
	}

	public String name() {
		return name;
	}

	public @Nullable String notice() {
		return notice;
	}

	public boolean announce() {
		return announce;
	}

	public long createdAt() {
		return createdAt;
	}

	public long updateAt() {
		return updateAt;
	}

	public List<Channel.Member> members() {
		return members;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof ChannelInfo that))
			return false;
		return announce == that.announce && createdAt == that.createdAt && updateAt == that.updateAt
				&& Objects.equals(channelId, that.channelId) && Objects.equals(ownerId, that.ownerId)
				&& Objects.equals(sessionId, that.sessionId) && Objects.equals(sessionKey, that.sessionKey)
				&& Objects.equals(permission, that.permission) && Objects.equals(name, that.name)
				&& Objects.equals(notice, that.notice) && Objects.equals(members, that.members);
	}

	@Override
	public int hashCode() {
		return Objects.hash(channelId, ownerId, sessionId, sessionKey, permission, name, notice, announce,
				createdAt, updateAt, members);
	}

	@Override
	public String toString() {
		return "ChannelInfo[channelId=" + channelId + ", ownerId=" + ownerId + ", sessionId=" + sessionId
				+ ", permission=" + permission + ", name=" + name + ", notice=" + notice + ", announce=" + announce
				+ ", createdAt=" + createdAt + ", updateAt=" + updateAt + ", members=" + members + "]";
	}
}
