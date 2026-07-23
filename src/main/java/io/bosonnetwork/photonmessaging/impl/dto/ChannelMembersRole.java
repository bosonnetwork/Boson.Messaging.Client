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
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bosonnetwork.Id;
import io.bosonnetwork.photonmessaging.Channel;

/**
 * A plain class rather than a record: Jackson's record support calls
 * {@code Class.getRecordComponents()}, which the Android runtime does not implement, so a record
 * wire type fails to deserialize on-device. Accessors keep the record-style names so call sites
 * are unaffected.
 */
public final class ChannelMembersRole {
	@JsonProperty(value = "ids", required = true)
	private final List<Id> memberIds;
	@JsonProperty(value = "r", required = true)
	private final Channel.Role role;

	@JsonCreator
	public ChannelMembersRole(@JsonProperty(value = "ids", required = true) List<Id> memberIds,
			@JsonProperty(value = "r", required = true) Channel.Role role) {
		this.memberIds = memberIds;
		this.role = role;
	}

	public List<Id> memberIds() {
		return memberIds;
	}

	public Channel.Role role() {
		return role;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof ChannelMembersRole that))
			return false;
		return Objects.equals(memberIds, that.memberIds) && Objects.equals(role, that.role);
	}

	@Override
	public int hashCode() {
		return Objects.hash(memberIds, role);
	}

	@Override
	public String toString() {
		return "ChannelMembersRole[memberIds=" + memberIds + ", role=" + role + "]";
	}
}
