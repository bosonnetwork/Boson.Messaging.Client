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

import java.io.PrintStream;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.Future;

import io.bosonnetwork.Id;
import io.bosonnetwork.photonmessaging.Channel;
import io.bosonnetwork.photonmessaging.Contact;

public class PhotonChannel extends PhotonContact implements Channel {
	/**
	 * The unique identifier of the channel's owner (the creator or current administrator).
	 */
	@JsonProperty(value = "o", required = true)
	private final Id ownerId;

	/**
	 * The join permissions for the channel (e.g., PUBLIC, MEMBER_INVITE, OWNER_INVITE).
	 */
	@JsonProperty(value = "p", required = true)
	private final Permission permission;

	/**
	 * A brief notice or descriptive information about the channel.
	 */
	@JsonProperty(value = "nt")
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	private final String notice;

	/**
	 * Indicates whether the channel is actively being announced to the network.
	 */
	@JsonProperty(value = "a")
	@JsonInclude(JsonInclude.Include.NON_DEFAULT)
	private final boolean announce;

	/**
	 * A thread-safe map of channel members, indexed by their unique IDs.
	 * Uses Copy-On-Write logic: a new map is created on member additions or removals.
	 */
	private volatile Map<Id, ChannelMember> _members;
	private Function<Id, Future<List<ChannelMember>>> membersLoader;

	// Constructor for database OR mapping
	protected PhotonChannel(Id id, byte[] sessionKey, Id ownerId, Permission permission, String name, String notice,
	                        boolean announce, String remark, String tags, boolean muted, boolean blocked,
	                        long createdAt, long updatedAt, int revision) {
		super(id, sessionKey, name, null, remark, tags, muted, blocked, createdAt, updatedAt, revision);
		this.ownerId = ownerId;
		this.permission = permission;
		this.notice = notice;
		this.announce = announce;
	}

	protected PhotonChannel(Id id, byte[] sessionKey, Id ownerId, Permission permission, String name, String notice,
	                        boolean announce, long createdAt, long updatedAt) {
		this(id, sessionKey, ownerId, permission, name, notice, announce,
				null, null, false, false, createdAt, updatedAt, 0);
	}

	@Override
	public Contact.Type getType() {
		return Contact.Type.CHANNEL;
	}

	@Override
	public Id getOwnerId() {
		return ownerId;
	}

	@Override
	public Permission getPermission() {
		return permission;
	}

	@Override
	public String getNotice() {
		return notice;
	}

	@Override
	public boolean isAnnounce() {
		return announce;
	}

	@Override
	public int size() {
		return getMembersMap().size();
	}

	@Override
	public int banned() {
		return getMembersMap().values().stream().mapToInt(m -> m.isBanned() ? 1 : 0).sum();
	}

	@Override
	public List<Member> getMembers() {
		return List.copyOf(getMembersMap().values());
	}

	@Override
	public ChannelMember getMember(Id memberId) {
		return getMembersMap().get(memberId);
	}

	@Override
	public boolean hasMember(Id memberId) {
		return getMembersMap().containsKey(memberId);
	}

	@Override
	public ChannelEditor editChannel() {
		return new ChannelEditor(this);
	}

	protected void setMembersLoader(Function<Id, Future<List<ChannelMember>>> membersLoader) {
		this.membersLoader = membersLoader;
	}

	protected Future<Void> loadMembers() {
		if (_members != null)
			return Future.succeededFuture();

		if (membersLoader == null)
			return Future.failedFuture(new IllegalStateException("Members loader not set"));

		return membersLoader.apply(getId()).map(ml -> {
			setMembers(ml);
			return null;
		});
	}

	void setMembers(Collection<ChannelMember> members) {
		Objects.requireNonNull(members, "members");
		LinkedHashMap<Id, ChannelMember> newMembers = new LinkedHashMap<>();
		for (ChannelMember m : members)
			newMembers.put(m.getId(), m);

		if (!newMembers.containsKey(ownerId))
			throw new IllegalStateException("Owner must be in members");

		setMembers(newMembers);
	}

	void setMembers(Map<Id, ChannelMember> members) {
		Objects.requireNonNull(members, "members");
		this._members = Collections.unmodifiableMap(members);
	}

	void invalidateMembers() {
		_members = null;
	}

	private Map<Id, ChannelMember> getMembersMap() {
		if (_members == null)
			throw new IllegalStateException("Members not loaded");

		return _members;
	}

	@Override
	public int hashCode() {
		return 0x6030AC0A + getId().hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;

		if (o instanceof Channel that)
			return this.getId().equals(that.getId());

		return false;
	}

	@Override
	public String toString() {
		StringBuilder repr = new StringBuilder(256);

		repr.append("Channel: ").append(getId().toBase58String())
				.append("[owner=").append(ownerId.toBase58String())
				.append(", permission=").append(permission.toString())
				.append(", members=").append(size());

		if (getName() != null)
			repr.append(", name=").append(getName());

		if (notice != null)
			repr.append(", notice=").append(notice);

		if (getRemark() != null)
			repr.append(", remark=").append(getRemark());

		if (getTags() != null)
			repr.append(", tags=").append(getTags());

		if (announce)
			repr.append(", announce = true");

		if (isMuted())
			repr.append(", muted");

		if (isBlocked())
			repr.append(", blocked");

		repr.append(", createdAt=").append(Instant.ofEpochMilli(getCreatedAt()))
				.append(" updatedAt=").append(Instant.ofEpochMilli(getUpdatedAt()))
				.append(" revision=").append(getRevision())
				.append(']');

		return repr.toString();
	}

	public void dump(PrintStream out) {
		out.println("Channel: " + getId().toBase58String());
		out.println("  owner = " + ownerId.toBase58String());
		out.println("  permission = " + permission.toString());

		if (getName() != null)
			out.println("  name = " + getName());

		if (notice != null)
			out.println("  notice = " + notice);

		if (getRemark() != null)
			out.println("  remark = " + getRemark());

		if (getTags() != null)
			out.println("  tags = " + getTags());

		if (announce)
			out.println("  announce = true");

		if (isMuted())
			out.println("  muted");

		if (isBlocked())
			out.println("  blocked");

		out.println("  created = " + Instant.ofEpochMilli(getCreatedAt()));
		out.println("  updated = " + Instant.ofEpochMilli(getUpdatedAt()));
		out.println("  revision = " + getRevision());

		out.println("  members = " + size());

		for (Member m : getMembersMap().values())
			out.println("    " + m.toString());
	}
}