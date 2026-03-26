package io.bosonnetwork.messaging;

import java.io.PrintStream;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import io.bosonnetwork.CryptoContext;
import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.CryptoException;

public abstract class Channel extends Contact {
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

	/**
	 * Represents the set of permissions that can be applied to a channel.
	 * Each value defines the level of authorization for user interaction and
	 * invitation rights within the context of a channel.
	 */
	public enum Permission {
		/**
		 * Indicates that the channel is open to all users without requiring any specific
		 * invitation or approval. Users can freely join and interact without restrictions.
		 */
		PUBLIC,
		// new members should be invited by the members
		/**
		 * Represents a permission level where new members can only join a channel
		 * if they are explicitly invited by existing members. This enforces a more
		 * controlled and selective process for adding participants to the channel.
		 */
		MEMBER_INVITE,
		/**
		 * Represents a permission level where new members can only join a channel
		 * if they are invited by a moderator. This ensures that moderators have
		 * control over who can participate in the channel, providing an added layer
		 * of oversight and security.
		 */
		MODERATOR_INVITE,
		/**
		 * Represents a permission level where new members can only join a channel
		 * if they are invited by the owner. This provides the highest degree of control
		 * over whom is allowed to participate, ensuring that only individuals explicitly
		 * approved by the owner can join.
		 */
		OWNER_INVITE;

		/**
		 * Returns the ordinal value of the enum constant, which represents its position
		 * in the declaration order. This value is used for serialization purposes
		 * and can be mapped back to the corresponding enum constant.
		 *
		 * @return the integer ordinal value of the enum constant
		 */
		@JsonValue
		public int value() {
			return ordinal();
		}

		/**
		 * Converts an integer value to the corresponding {@code Permission} enum constant.
		 * The mapping is based on the ordinal positions of the constants:
		 *
		 * @param value the integer value representing a {@code Permission} level
		 * @return the corresponding {@code Permission} enum constant
		 * @throws IllegalArgumentException if the provided value is not a valid permission
		 */
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

	/**
	 * Represents the roles a member can have within a channel.
	 * Each role is associated with a specific integer value for identification purposes.
	 */
	public enum Role {
		/**
		 * The creator or principal administrator of the channel.
		 */
		OWNER(0),
		/**
		 * A member with elevated permissions to manage the channel.
		 */
		MODERATOR(1),
		/**
		 * A regular member with standard permissions.
		 */
		MEMBER(2),
		/**
		 * A member who is restricted from accessing the channel.
		 */
		BANNED(-1);

		private final int value;

		Role(int value) {
			this.value = value;
		}

		/**
		 * Retrieves the integer value associated with this role.
		 * This value is used for serialization purposes and can be mapped
		 * back to the corresponding enum constant.
		 *
		 * @return the integer value representing the role.
		 */
		@JsonValue
		public int value() {
			return value;
		}

		/**
		 * Checks if the current role represents a banned user.
		 *
		 * @return {@code true} if the current role is {@code BANNED}; {@code false} otherwise.
		 */
		public boolean isBanned() {
			return value == BANNED.value;
		}

		/**
		 * Maps an integer value to its corresponding {@code Role} enumeration constant.
		 * This method is primarily used for deserialization of roles from their numeric representation.
		 *
		 * @param value the integer value representing a specific role.
		 * @return the {@code Role} corresponding to the given integer value.
		 * @throws IllegalArgumentException if the provided value does not map to any valid role.
		 */
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

	/**
	 * Represents a member in a channel, containing details such as the member's unique identifier,
	 * home peer identifier, role, join timestamp, and the last updated timestamp.
	 * This class provides various methods to access and manipulate a member's role and identity.
	 */
	public static class Member {
		@JsonProperty(value = "id", required = true)
		private final Id id;
		@JsonProperty(value = "r", required = true)
		private final Role role;
		@JsonProperty(value = "j", required = true)
		private final long joined;

		private transient Contact contact;

		/**
		 * Constructs a new Member instance with the specified parameters.
		 *
		 * @param id          The unique identifier for the member. Must not be null.
		 * @param role        The role assigned to the member (e.g., OWNER, MEMBER, MODERATOR). Must not be null.
		 * @param joined      The timestamp indicating when the member joined. If set to 0, the current system time is used.
		 */
		@JsonCreator
		public Member(@JsonProperty(value = "id", required = true) Id id,
					  @JsonProperty(value = "r", required = true) Role role,
					  @JsonProperty(value = "j", required = true) long joined) {
			this.id = id;
			this.role = role;
			this.joined = joined;
		}

		/**
		 * Retrieves the unique identifier associated with this member.
		 *
		 * @return the unique identifier of the member.
		 */
		public Id getId() {
			return id;
		}

		/**
		 * Retrieves the role assigned to this member.
		 *
		 * @return the role of the member, such as OWNER, MEMBER, MODERATOR, or BANNED.
		 */
		public Role getRole() {
			return role;
		}

		/**
		 * Updates the role of the member and returns a new {@code Member} instance
		 * with the updated role while preserving the other properties.
		 *
		 * @param role The new role to assign to the member. Must not be null.
		 * @return A new {@code Member} instance with the specified role.
		 */
		protected Member setRole(Role role) {
			return new Member(id, role, joined);
		}

		/**
		 * Checks whether the member holds the role of an owner.
		 *
		 * @return {@code true} if the member's role is {@code OWNER};
		 *         {@code false} otherwise.
		 */
		public boolean isOwner() {
			return role == Role.OWNER;
		}

		/**
		 * Checks whether the member holds the role of a moderator.
		 *
		 * @return {@code true} if the member's role is {@code MODERATOR};
		 *         {@code false} otherwise.
		 */
		public boolean isModerator() {
			return role == Role.MODERATOR;
		}

		/**
		 * Checks whether the member is banned.
		 *
		 * @return {@code true} if the member's role is {@code BANNED};
		 *         {@code false} otherwise.
		 */
		public boolean isBanned() {
			return role == Role.BANNED;
		}

		/**
		 * Retrieves the timestamp indicating when the member joined.
		 *
		 * @return the join timestamp of the member in milliseconds since the epoch.
		 */
		public long getJoined() {
			return joined;
		}

		public Contact getContact() {
			return contact;
		}

		// TODO: remove this method,
		public void setContact(Contact contact) {
			this.contact = contact;
		}

		public String getDisplayName() {
			return contact != null ? contact.getDisplayName() : id.toAbbrString();
		}

		/**
		 * Determines if the provided {@code Member} instance is the same as this instance.
		 *
		 * @param member The {@code Member} to compare with this instance. Must not be null.
		 * @return {@code true} if the provided {@code Member} is the same as this instance,
		 *         or if their unique identifiers are equal; {@code false} otherwise.
		 */
		public boolean is(Member member) {
			if (this == member)
				return true;

			return this.id.equals(member.id);
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
			return "Member: " + id +
					", role=" + role +
					", joined=" + Instant.ofEpochMilli(joined);
		}
	}

	// Constructor for database OR mapping
	protected Channel(Id id, byte[] sessionKey, Id ownerId, Permission permission,  String name, String notice,
					  boolean announce, String remark, String tags, boolean muted, boolean blocked,
					  long createdAt, long updatedAt, int revision) {
		super(id, sessionKey, name, remark, tags, muted, blocked, createdAt, updatedAt, revision);
		this.ownerId = ownerId;
		this.permission = permission;
		this.notice = notice;
		this.announce = announce;
	}

	protected Channel(Id id, byte[] sessionKey, String name, String remark, String tags,
					  boolean muted, boolean blocked, long createdAt, long updatedAt, int revision) {
		super(id, sessionKey, name, remark, tags, muted, blocked, createdAt, updatedAt, revision);
	}

	@Override
	public Contact.Type getType() {
		return Contact.Type.CHANNEL;
	}

	public Id getOwner() {
		return ownerId;
	}

	protected void setPermission(Permission permission) {
		this.permission = permission;
	}

	public Permission getPermission() {
		return permission;
	}

	protected void setNotice(String notice) {
		this.notice = notice;
	}

	public String getNotice() {
		return notice;
	}

	protected void setAnnounce(boolean announce) {
		this.announce = announce;
	}

	public boolean isAnnounce() {
		return announce;
	}

	public int size() {
		return getMembersMap().size();
	}

	public int banned() {
		return getMembersMap().values().stream().mapToInt(m -> m.isBanned() ? 1 : 0).sum();
	}

	// only for the constructor and tests
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

	public List<Member> getMembers() {
		return List.copyOf(getMembersMap().values());
	}

	/**
	 * Retrieves a member from the current channel using their unique identifier.
	 *
	 * @param memberId the unique identifier of the member to retrieve
	 * @return the {@code Member} associated with the provided {@code memberId}, or {@code null} if no such member exists
	 */
	public Member getMember(Id memberId) {
		return getMembersMap().get(memberId);
	}

	public boolean hasMember(Id memberId) {
		return getMembersMap().containsKey(memberId);
	}

	public CryptoContext getRxCryptoContext(Id memberId) {
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
	public int hashCode() {
		return Objects.hash(0x6030A, "Channel", getId());
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

		for (Member m : getMembers())
			out.println("    " + m.toString());
	}
}