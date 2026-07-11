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

package io.bosonnetwork.photonmessaging;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.jspecify.annotations.Nullable;

import io.bosonnetwork.Id;
import io.bosonnetwork.photonmessaging.impl.ChannelMember;

/**
 * Represents a communication channel, which can be owned and managed by a specific user.
 * A channel maintains members, permissions, and metadata such as a notice and announcement flag.
 * It provides methods for accessing, managing, and inspecting its members and properties.
 */
public interface Channel extends Contact {
	/**
	 * Represents the set of permissions that can be applied to a channel.
	 * Each value defines the level of authorization for user interaction and
	 * invitation rights within the context of a channel.
	 */
	enum Permission {
		/**
		 * Indicates that the channel is open to all users without requiring any specific
		 * invitation or approval. Users can freely join and interact without restrictions.
		 */
		PUBLIC(0),
		/**
		 * Represents a permission level where new members can only join a channel
		 * if they are explicitly invited by existing members. This enforces a more
		 * controlled and selective process for adding participants to the channel.
		 */
		MEMBER_INVITE(1),
		/**
		 * Represents a permission level where new members can only join a channel
		 * if they are invited by a moderator. This ensures that moderators have
		 * control over who can participate in the channel, providing an added layer
		 * of oversight and security.
		 */
		MODERATOR_INVITE(2),
		/**
		 * Represents a permission level where new members can only join a channel
		 * if they are invited by the owner. This provides the highest degree of control
		 * over whom is allowed to participate, ensuring that only individuals explicitly
		 * approved by the owner can join.
		 */
		OWNER_INVITE(3);

		private final int value;

		Permission(int value) {
			this.value = value;
		}

		/**
		 * Returns the stable integer value associated with this permission. This value is
		 * used for serialization purposes and is independent of the constant's declaration
		 * order, so it can be safely mapped back via {@link #valueOf(int)}.
		 *
		 * @return the integer value representing the permission
		 */
		@JsonValue
		public int value() {
			return value;
		}

		/**
		 * Converts an integer value to the corresponding {@code Permission} enum constant.
		 * The mapping is based on the stable {@link #value()} of each constant.
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
	enum Role {
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
	 * Represents a member of a channel with associated properties and behavior.
	 * A channel member is identified by a unique ID and is assigned a specific role
	 * within the channel, such as OWNER, MODERATOR, MEMBER, or BANNED.
	 */
	@JsonDeserialize(as = ChannelMember.class)
	interface Member {
		/**
		 * Retrieves the unique identifier associated with this member.
		 *
		 * @return the unique identifier of the member.
		 */
		Id getId();

		/**
		 * Retrieves the role assigned to this member.
		 *
		 * @return the role of the member, such as OWNER, MEMBER, MODERATOR, or BANNED.
		 */
		Channel.Role getRole();

		/**
		 * Checks whether the member holds the role of an owner.
		 *
		 * @return {@code true} if the member's role is {@code OWNER};
		 *         {@code false} otherwise.
		 */
		default boolean isOwner() {
			return getRole() == Channel.Role.OWNER;
		}

		/**
		 * Checks whether the member holds the role of a moderator.
		 *
		 * @return {@code true} if the member's role is {@code MODERATOR};
		 *         {@code false} otherwise.
		 */
		default boolean isModerator() {
			return getRole() == Channel.Role.MODERATOR;
		}

		/**
		 * Checks whether the member is banned.
		 *
		 * @return {@code true} if the member's role is {@code BANNED};
		 *         {@code false} otherwise.
		 */
		default boolean isBanned() {
			return getRole() == Channel.Role.BANNED;
		}

		/**
		 * Retrieves the timestamp indicating when the member joined.
		 *
		 * @return the join timestamp of the member in milliseconds since the epoch.
		 */
		long getJoined();

		/**
		 * Determines if the provided {@code Member} instance is the same as this instance.
		 *
		 * @param member The {@code Member} to compare with this instance. Must not be null.
		 * @return {@code true} if the provided {@code Member} is the same as this instance,
		 *         or if their unique identifiers are equal; {@code false} otherwise.
		 */
		default boolean is(Member member) {
			if (this == member)
				return true;

			return this.getId().equals(member.getId());
		}
	}

	/**
	 * Retrieves the contact type for the channel.
	 *
	 * @return the {@code Contact.Type} representing a channel.
	 */
	@Override
	default Contact.Type getType() {
		return Contact.Type.CHANNEL;
	}

	/**
	 * Retrieves the owner of the channel.
	 *
	 * @return the unique identifier of the owner for the current channel.
	 */
	Id getOwnerId();

	/**
	 * Retrieves the permission level for the current channel.
	 * The permission level determines how users can interact with the channel
	 * and the conditions for joining or inviting new members.
	 *
	 * @return the {@code Permission} enum instance representing the channel's permission level
	 */
	Permission getPermission();

	/**
	 * Retrieves the notice associated with the channel.
	 *
	 * @return an {@link Optional} holding the current notice of the channel, or an empty
	 *         {@code Optional} if no notice is set.
	 */
	Optional<String> getNotice();

	/**
	 * Checks if the channel is configured to be announced to the network.
	 *
	 * @return {@code true} if the channel is announced; {@code false} otherwise.
	 */
	boolean isAnnounce();

	/**
	 * Loads the members of the channel asynchronously. This method initiates the process
	 * of retrieving and populating the member data for the channel. The members may include
	 * regular users, moderators, and the channel owner, and will be updated based on the
	 * most recent state of the channel.
	 *
	 * @return a {@code CompletableFuture<Void>} that completes once the member-loading process is finished.
	 */
	CompletableFuture<Void> loadMembers();

	/**
	 * Retrieves the total number of members in the channel.
	 * The size includes all users who are part of the channel,
	 * such as the owner, regular members, moderators, and banned members.
	 *
	 * @return an integer representing the current number of members in the channel
	 */
	int size();

	/**
	 * Retrieves the number of members in the channel who are currently banned.
	 * The banned count includes all users with the {@code BANNED} role,
	 * regardless of their previous role or status within the channel.
	 *
	 * @return an integer representing the total number of banned members in the channel
	 */
	int banned();

	/**
	 * Retrieves the list of members currently part of the channel.
	 * The list includes regular members, moderators, and the owner.
	 * The members are ordered by their join timestamp, with unbanned members first.
	 *
	 * @return a list of {@code ChannelMember} objects representing all members of the channel.
	 *         The list includes regular members, moderators, and the owner, but may also
	 *         include members with the {@code BANNED} role if such members are being tracked.
	 */
	List<Member> getMembers();

	/**
	 * Retrieves a specific member of the channel based on their unique identifier.
	 *
	 * @param memberId the unique identifier of the member to retrieve
	 * @return an {@link Optional} holding the {@code Member} corresponding to the specified
	 *         identifier, or an empty {@code Optional} if no member with the given identifier
	 *         exists in the channel
	 */
	Optional<Member> getMember(Id memberId);

	/**
	 * Checks if a member with the specified identifier is part of the channel.
	 *
	 * @param memberId the unique identifier of the member to check
	 * @return {@code true} if the member exists in the channel; {@code false} otherwise
	 */
	boolean hasMember(Id memberId);

	/**
	 * Prepares an {@link Editor} instance to modify the channel-specific properties of the
	 * current channel (permission, name, notice and announce flag).
	 * <p>
	 * Common contact-level properties (remark, tags, muted and blocked) are edited
	 * separately through the inherited {@link Contact#edit()} method.
	 * <p>
	 * Since {@link Channel} is immutable, the editor returns a new instance reflecting any
	 * modifications when {@link Editor#build()} is called.
	 *
	 * @return an {@link Editor} instance to configure and apply changes to the channel
	 */
	Editor editChannel();

	/**
	 * A builder for updating the channel-specific properties of a {@link Channel}.
	 * <p>
	 * This editor covers only channel-specific fields. To change common contact-level
	 * fields, use the {@link Contact.Editor} obtained from {@link Contact#edit()}.
	 */
	interface Editor {
		/**
		 * Sets the join permission policy for the channel.
		 *
		 * @param permission the join permission level
		 * @return this builder instance
		 */
		Editor setPermission(Permission permission);

		/**
		 * Sets the name of the channel.
		 *
		 * @param name the channel name
		 * @return this builder instance
		 */
		Editor setName(String name);

		/**
		 * Sets the channel notice or description.
		 *
		 * @param notice the channel notice
		 * @return this builder instance
		 */
		Editor setNotice(@Nullable String notice);

		/**
		 * Sets whether the channel is announced to the network.
		 *
		 * @param announce true if the channel should be announced, false otherwise
		 * @return this builder instance
		 */
		Editor setAnnounce(boolean announce);

		/**
		 * Builds and returns the {@link Channel} instance.
		 *
		 * @return the constructed Channel
		 */
		Channel build();
	}
}