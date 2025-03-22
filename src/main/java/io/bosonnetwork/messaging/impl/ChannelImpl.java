package io.bosonnetwork.messaging.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Signature.KeyPair;
import io.bosonnetwork.messaging.Channel;

public class ChannelImpl extends Channel {
	private HashMap<Id, Member> members;
	private Callable<List<Member>> memberLoader;

	public ChannelImpl(Id id, Id homePeerId, boolean auto, byte[] sessionKey, String name, boolean avatar,
			 String notice, Id owner, Permission permission, String remark, String tags,
			boolean muted, long created, long lastModified, long lastUpdated) {
		super(id, homePeerId, auto, sessionKey, name, avatar, notice, owner, permission,
				remark, tags, muted, created, lastModified, lastUpdated);
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
				null, null, null, null, null, false, now, now, -1);
	}

	public static Channel auto(Id id, Id homePeerId) {
		return new ChannelImpl(id, homePeerId);
	}

	public static Channel auto(Id id) {
		return new ChannelImpl(id, null);
	}

	protected void setMembers(Callable<List<Member>> memberLoader) {
		this.memberLoader = memberLoader;
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
	protected void setPermission(Permission permission) {
		super.setPermission(permission);
	}

	private Map<Id, Member> members() {
		if (members == null && memberLoader != null) {
			HashMap<Id, Member> map = new HashMap<>();

			try {
				List<Member> lst = memberLoader.call();
				lst.forEach(m -> map.put(m.getId(), m));
				members = map;
			} catch (Exception e) {
				return Collections.emptyMap();
			}
		}

		return members;
	}

	protected void invalidateMembers() {
		members = null;
	}

	@Override
	protected List<Member> setMembersRole(List<Id> memberIds, Role role) {
		return super.setMembersRole(memberIds, role);
	}

	@Override
	protected List<Member> removeMembers(List<Id> memberIds) {
		Map<Id, Member> members = members();

		return memberIds.stream().map(id -> {
			return members.remove(id);
		}).collect(Collectors.toList());
	}

	@Override
	protected void addMember(Member member) {
		members().put(member.getId(), member);
	}

	@Override
	protected Member removeMember(Id memberId) {
		return members.remove(memberId);
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
	protected void update(Channel channel) {
		super.update(channel);
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
