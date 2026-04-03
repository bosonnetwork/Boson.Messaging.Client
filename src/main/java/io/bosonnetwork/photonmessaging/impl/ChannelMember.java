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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bosonnetwork.Id;
import io.bosonnetwork.photonmessaging.Channel;
import io.bosonnetwork.photonmessaging.Contact;

public class ChannelMember implements Channel.Member {
	@JsonProperty(value = "id", required = true)
	private final Id id;
	@JsonProperty(value = "r", required = true)
	private final Channel.Role role;
	@JsonProperty(value = "j", required = true)
	private final long joined;

	private transient AbstractContact contact;

	/**
	 * Constructs a new Member instance with the specified parameters.
	 *
	 * @param id     The unique identifier for the member. Must not be null.
	 * @param role   The role assigned to the member (e.g., OWNER, MEMBER, MODERATOR). Must not be null.
	 * @param joined The timestamp indicating when the member joined. If set to 0, the current system time is used.
	 */
	@JsonCreator
	public ChannelMember(@JsonProperty(value = "id", required = true) Id id,
	                     @JsonProperty(value = "r", required = true) Channel.Role role,
	                     @JsonProperty(value = "j", required = true) long joined) {
		this.id = id;
		this.role = role;
		this.joined = joined;
	}

	@Override
	public Id getId() {
		return id;
	}

	@Override
	public Channel.Role getRole() {
		return role;
	}

	@Override
	public long getJoined() {
		return joined;
	}

	@Override
	public Contact getContact() {
		return contact;
	}

	// TODO: remove this method,
	protected void setContact(AbstractContact contact) {
		this.contact = contact;
	}

	@Override
	public String getDisplayName() {
		return contact != null ? contact.getDisplayName() : id.toAbbrString();
	}

	@Override
	public int hashCode() {
		return 0x6030A3E3 + getId().hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;

		if (o instanceof Channel.Member that)
			return this.getId().equals(that.getId());

		return false;
	}

	@Override
	public String toString() {
		return "Member: " + id +
				", role=" + role +
				", joined=" + Instant.ofEpochMilli(joined);
	}
}