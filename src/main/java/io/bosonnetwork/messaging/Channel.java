package io.bosonnetwork.messaging;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.bosonnetwork.CryptoContext;
import io.bosonnetwork.Id;
import io.bosonnetwork.messaging.impl.ChannelBuilder;

@JsonDeserialize(builder = ChannelBuilder.class)
public abstract class Channel extends Contact {
	@JsonProperty("o")
	@JsonInclude(Include.NON_NULL)
	private Id owner;

	@JsonProperty("pm")
	@JsonInclude(Include.NON_DEFAULT)
	private Permission permission;

	// @JsonProperty("nt")
	// @JsonInclude(Include.NON_EMPTY)
	private String notice;

	private Map<Id, CryptoContext> memberCryptoContexts;

	public static enum Permission {
		PUBLIC(0), MEMBER_INVITE(1), MODERATOR_INVITE(2), OWNER_INVITE(3);

		private int value;

		Permission(int value) {
			this.value = value;
		}

		@JsonValue
		public int value() {
			return value;
		}

		@JsonCreator
		public static Permission valueOf(int value) {
			return switch (value) {
			case 0 -> PUBLIC;
			case 1 -> MEMBER_INVITE;
			case 2 -> MODERATOR_INVITE;
			case 3 -> OWNER_INVITE;
			default -> throw new IllegalArgumentException("Invalid permission value");
			};
		}
	}

	public static enum Role {
		OWNER(0), MODERATOR(1), MEMBER(2), BANNED(-1);

		private int value;

		Role(int value) {
			this.value = value;
		}

		@JsonValue
		public int value() {
			return value;
		}

		public boolean isBanned() {
			return value == BANNED.value;
		}

		@JsonCreator
		public static Role valueOf(int value) {
			return switch (value) {
			case 0 -> OWNER;
			case 1 -> MODERATOR;
			case 2 -> MEMBER;
			case -1 -> BANNED;
			default -> throw new IllegalArgumentException("Invalid role value");
			};
		}
	}

	public static class Member {
		@JsonProperty(value = "id", required = true)
		private Id id;

		@JsonProperty(value = "r", required = true)
		private Role role;

		@JsonProperty(value = "j", required = true)
		private long joined;

		private transient Contact contact;

		public Member(Id id, Role role, long joined) {
			this.id = id;
			this.role = role;
			this.joined = joined;
		}

		// only used for local
		public static Member unknown(Id id) {
			return new Member(id, null, -1);
		}

		public Id getId() {
			return id;
		}

		public Role getRole() {
			return role;
		}

		protected void setRole(Role role) {
			this.role = role;
		}

		public boolean isOwner() {
			return role == Role.OWNER;
		}

		public boolean isModerator() {
			return role == Role.MODERATOR;
		}

		public boolean isBanned() {
			return role == Role.BANNED;
		}

		public long getJoined() {
			return joined;
		}

		public Contact getContact() {
			return contact;
		}

		// TODO: remove this method
		public void setContact(Contact contact) {
			this.contact = contact;
		}

		public String getDisplayName() {
			return contact != null ? contact.getDisplayName() : id.toAbbrString();
		}

		public boolean is(Member member) {
			if (this == member)
				return true;

			return this.getId().equals(member.getId());
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;

			if (o instanceof Member that) {
				return this.id.equals(that.id) &&
						this.role == that.role &&
						this.joined == that.joined;
			}

			return false;
		}

		@Override
		public String toString() {
			StringBuffer repr = new StringBuffer(128);

			repr.append(getId().toBase58String()).append(", ")
				.append(role).append(", ")
				.append(Instant.ofEpochMilli(joined));

			return repr.toString();
		}
	}

	// for local storage
	protected Channel(Id id, Id homePeerId,  boolean auto, byte[] sessionKey, String name, boolean avatar,
			String notice, Id owner, Permission permission, String remark, String tags,
			boolean muted, long created, long lastModified, long lastUpdated,
			boolean deleted, int revision, boolean modified) {
		super(id, homePeerId, auto, sessionKey, name, avatar, remark, tags,
				muted, false, created, lastModified, lastUpdated, deleted, revision, modified);

		this.notice = notice;
		this.owner = owner;
		this.permission = permission;
	}

	protected Channel(Id id, Id homePeerId) {
		super(id, homePeerId);
	}

	public Id getOwner() {
		return owner;
	}

	protected void setOwner(Id owner) {
		this.owner = owner;
		touch();
	}

	public Permission getPermission() {
		return permission;
	}

	protected void setPermission(Permission permission) {
		this.permission = permission;
		touch();
	}

	public String getNotice() {
		return notice;
	}

	protected void setNotice(String notice) {
		this.notice = notice;
	}

	@Override
	public void setBlocked(boolean blocked) {
		// Do nothing on channel contact.
		throw new UnsupportedOperationException();
	}

	@Override
	public void update(Profile profile) {
		this.notice = profile.getNotice();
		super.update(profile);
	}

	protected void update(Channel channel) {
		this.permission = channel.permission;
		this.owner = channel.owner;
		this.notice = channel.notice;
		super.update(channel);
	}

	public CryptoContext getRxCryptoContext(Id memberId) {
		Objects.requireNonNull(memberId, "memberId");

		if (!hasSessionKey())
			return null;

		if (memberCryptoContexts == null)
			memberCryptoContexts = new HashMap<>();

		return memberCryptoContexts.computeIfAbsent(memberId, id -> {
			return createCryptoContext(id);
		});
	}

	public abstract int size();

	// Return a modifiable copy of the members to make it easier for apps to sort or filter
	public abstract List<Member> getMembers();

	public abstract Member getMember(Id id);

	protected List<Member> setMembersRole(List<Id> memberIds, Role role) {
		return memberIds.stream().map(id -> {
			Member member = getMember(id);
			member.setRole(role);
			return member;
		}).collect(Collectors.toList());
	}

	protected abstract List<Member> removeMembers(List<Id> memberIds);

	protected abstract void addMember(Member member);

	protected abstract Member removeMember(Id memberId);

	public boolean isOwner(Id id) {
		return id.equals(owner);
	}

	public boolean isModerator(Id id) {
		Member member = getMember(id);
		return member != null && member.isModerator();
	}

	public boolean isBanned(Id id) {
		Member member = getMember(id);
		return member != null && member.isBanned();
	}

	public boolean isMember(Id id) {
		Member member = getMember(id);
		return member != null && !member.isBanned();
	}

	public boolean isQualifiedInviter(Id inviter) {
		Member member = getMember(inviter);
		if (member == null)
			return false;

		Role role = member.getRole();
		switch (permission) {
		case PUBLIC:
		case MEMBER_INVITE:
			return role.value <= Role.MEMBER.value;

		case MODERATOR_INVITE:
			return role.value <= Role.MODERATOR.value;

		case OWNER_INVITE:
			return inviter.equals(owner);

		default:
			return false;
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;

		if (o instanceof Channel that) {
			return super.equals(o) &&
					Objects.equals(this.owner, that.owner) &&
					Objects.equals(this.permission, that.permission) &&
					Objects.equals(this.notice, that.notice);
		}

		return false;
	}
}
