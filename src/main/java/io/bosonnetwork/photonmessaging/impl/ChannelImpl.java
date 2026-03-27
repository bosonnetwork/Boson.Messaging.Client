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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bosonnetwork.CryptoContext;
import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.CryptoException;
import io.bosonnetwork.photonmessaging.Channel;

public class ChannelImpl extends AbstractContact implements Channel {
	/**
	 * The unique identifier of the channel's owner (the creator or current administrator).
	 */
	@JsonProperty(value = "o", required = true)
	private Id ownerId;

	/**
	 * The join permissions for the channel (e.g., PUBLIC, MEMBER_INVITE, OWNER_INVITE).
	 */
	@JsonProperty(value = "p", required = true)
	private Permission permission;

	/**
	 * A brief notice or descriptive information about the channel.
	 */
	@JsonProperty(value = "nt")
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	private String notice;

	/**
	 * Indicates whether the channel is actively being announced to the network.
	 */
	@JsonProperty(value = "a")
	@JsonInclude(JsonInclude.Include.NON_DEFAULT)
	private boolean announce;

	/**
	 * A thread-safe map of channel members, indexed by their unique IDs.
	 * Uses Copy-On-Write logic: a new map is created on member additions or removals.
	 */
	private volatile Map<Id, Member> _members;

	/**
	 * Cached receiver crypto contexts for each member.
	 */
	private Map<Id, CryptoContext> memberRxCryptoContexts;

	// Constructor for database OR mapping
	protected ChannelImpl(Id id, byte[] sessionKey, Id ownerId, Permission permission,  String name, String notice,
	                  boolean announce, String remark, String tags, boolean muted, boolean blocked,
	                  long createdAt, long updatedAt, int revision) {
		super(id, sessionKey, name, remark, tags, muted, blocked, createdAt, updatedAt, revision);
		this.ownerId = ownerId;
		this.permission = permission;
		this.notice = notice;
		this.announce = announce;
	}

	protected ChannelImpl(Id id, byte[] sessionKey, String name, String remark, String tags,
	                  boolean muted, boolean blocked, long createdAt, long updatedAt, int revision) {
		super(id, sessionKey, name, remark, tags, muted, blocked, createdAt, updatedAt, revision);
	}

	@Override
	public Id getOwner() {
		return ownerId;
	}

	@Override
	public Permission getPermission() {
		return permission;
	}

	protected void setPermission(Permission permission) {
		this.permission = permission;
	}

	@Override
	public String getNotice() {
		return notice;
	}

	protected void setNotice(String notice) {
		this.notice = notice;
	}

	@Override
	public boolean isAnnounce() {
		return announce;
	}

	protected void setAnnounce(boolean announce) {
		this.announce = announce;
	}

	protected CryptoContext getRxCryptoContext(Id memberId) {
		Objects.requireNonNull(memberId, "memberId");

		if (!hasSessionKey())
			return null;

		if (memberRxCryptoContexts == null)
			memberRxCryptoContexts = new HashMap<>();

		return memberRxCryptoContexts.computeIfAbsent(memberId, id -> {
			try {
				return createCryptoContext(id);
			} catch (CryptoException e) {
				// TODO: use sneaky throw?
				throw new IllegalStateException("Failed to create crypto context", e);
			}
		});
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
	public Member getMember(Id memberId) {
		return getMembersMap().get(memberId);
	}

	@Override
	public boolean hasMember(Id memberId) {
		return getMembersMap().containsKey(memberId);
	}

	void setMembers(Collection<Member> members) {
		Objects.requireNonNull(members, "members");
		LinkedHashMap<Id, Member> newMembers = new LinkedHashMap<>();
		for (Member m : members)
			newMembers.put(m.getId(), m);

		if (!newMembers.containsKey(ownerId))
			throw new IllegalStateException("Owner must be in members");

		setMembers(newMembers);
	}

	void setMembers(Map<Id, Member> members) {
		Objects.requireNonNull(members, "members");
		this._members = Collections.unmodifiableMap(members);
	}

	Map<Id, Member> copyMembers() {
		return new LinkedHashMap<>(_members);
	}

	private Map<Id, Member> getMembersMap() {
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
		out.println("  members = " + size());

		for (Member m : getMembersMap().values())
			out.println("    " + m.toString());
	}
}