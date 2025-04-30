package io.bosonnetwork.messaging.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Signature.KeyPair;
import io.bosonnetwork.messaging.Channel;

public class ChannelImpl extends Channel {
	private HashMap<Id, Member> members;
	private Function<Id, List<Member>> memberLoader;

	public ChannelImpl(Id id, Id homePeerId, boolean auto, byte[] sessionKey, String name, boolean avatar,
			 String notice, Id owner, Permission permission, String remark, String tags,
			boolean muted, long created, long lastModified, long lastUpdated,
			boolean deleted, int revision, boolean modified) {
		super(id, homePeerId, auto, sessionKey, name, avatar, notice, owner, permission,
				remark, tags, muted, created, lastModified, lastUpdated, deleted, revision, modified);
	}

	/*
	public ChannelImpl(Id id, Id homePeerId, boolean auto, byte[] privateKey,
			String remark, String tags, boolean muted, long created, long lastModified) {
		this(id, homePeerId, auto, null, false, null, privateKey, null, null,
				remark, tags, muted, created, lastModified, -1);
	}

	public ChannelImpl(Id id, Id homePeerId, boolean auto, String name, boolean avatar, String notice,
			byte[] privateKey, Id owner, Permission permission, long created, long lastModified) {
		this(id, homePeerId, auto, name, avatar, notice, privateKey, owner, permission,
				null, null, false, created, lastModified, System.currentTimeMillis());
	}
	*/

	private ChannelImpl(Id id, Id homePeerId) {
		super(id, homePeerId);
	}

	public static Channel create(Id id, Id homePeerId, byte[] sessionKey, String name, boolean avatar) {
		long now = System.currentTimeMillis();

		return new ChannelImpl(id, homePeerId, false, sessionKey, name, avatar,
				null, null, null, null, null, false, now, now, -1, false, 1, true);
	}

	public static Channel auto(Id id, Id homePeerId) {
		return new ChannelImpl(id, homePeerId);
	}

	public static Channel auto(Id id) {
		return new ChannelImpl(id, null);
	}

	private static final Logger log = LoggerFactory.getLogger(ChannelImpl.class);

	public void setMembers(Function<Id, List<Member>> memberLoader) {
		this.memberLoader = memberLoader;
	}

	@Override
	public void setMembers(Collection<Member> members) {
		HashMap<Id, Member> newMembers = new HashMap<>(members.size());
		for (Member member : members)
			newMembers.put(member.getId(), member);

		this.members = newMembers;
	}

	@Override
	public int getType() {
		return Types.CHANNEL;
	}

	@Override
	public void setSessionKey(byte[] privateKey) {
		super.setSessionKey(privateKey);
	}

	@Override
	public KeyPair getSessionKeyPair() {
		return super.getSessionKeyPair();
	}

	@Override
	protected void setOwner(Id owner) {
		super.setOwner(owner);
	}

	@Override
	protected void setPermission(Permission permission) {
		super.setPermission(permission);
	}

	@Override
	protected void setName(String name) {
		super.setName(name);
	}

	@Override
	protected void setNotice(String notice) {
		super.setNotice(notice);
	}

	private Map<Id, Member> members() {
		if (members == null && memberLoader != null) {
			HashMap<Id, Member> map = new HashMap<>();

			try {
				List<Member> lst = memberLoader.apply(getId());
				lst.forEach(m -> map.put(m.getId(), m));
				members = map;
			} catch (Exception e) {
				return Collections.emptyMap();
			}
		}

		return members;
	}

	public void invalidateMembers() {
		members = null;
	}

	@Override
	protected List<Member> setMembersRole(List<Id> memberIds, Role role) {
		return super.setMembersRole(memberIds, role);
	}

	@Override
	protected void addMember(Member member) {
		members().put(member.getId(), member);
	}

	@Override
	protected Member removeMember(Id memberId) {
		return members().remove(memberId);
	}

	@Override
	protected List<Member> removeMembers(List<Id> memberIds) {
		Map<Id, Member> members = members();

		return memberIds.stream().map(id -> {
			return members.remove(id);
		}).collect(Collectors.toList());
	}

	@Override
	public int size() {
		return members().size();
	}

	@Override
	public List<Member> getMembers() {
		return new ArrayList<>(members().values());
	}

	@Override
	public Member getMember(Id id) {
		return members().get(id);
	}

	@Override
	public int hashCode() {
		return getId().hashCode() + 0x8A7B3340;
	}

	@Override
	public String toString() {
		StringBuilder repr = new StringBuilder(512);

		repr.append("Channel: ")
			.append(getId().toString()).append('[');

		if (getHomePeerId() != null)
			repr.append("homePeer= ").append(getHomePeerId().toString()).append(", ");

		if (getSessionKeyPair() != null)
			repr.append("sessionKey*, ");

		if (getName() != null)
			repr.append("name= ").append(getName()).append(", ");

		if (getAvatar())
			repr.append("avatar, ");

		if (getNotice() != null)
			repr.append("notice= ").append(getNotice()).append(", ");

		if (getOwner() != null)
			repr.append("owner= ").append(getOwner().toString()).append(", ");

		if (getPermission() != null)
			repr.append("permission= ").append(getPermission()).append(", ");

		if (getRemark() != null)
			repr.append("remark= ").append(getRemark()).append(", ");

		if (getTags() != null)
			repr.append("tags= ").append(getTags()).append(", ");

		if (isMuted())
			repr.append("muted, ");

		repr.append("created: ").append(Instant.ofEpochMilli(getCreated())).append(", ")
			.append("modified: ").append(Instant.ofEpochMilli(getLastModified())).append(']');

		return repr.toString();
	}
}
