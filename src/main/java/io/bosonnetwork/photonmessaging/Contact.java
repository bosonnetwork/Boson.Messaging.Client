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

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.jspecify.annotations.Nullable;

import io.bosonnetwork.Id;
import io.bosonnetwork.photonmessaging.impl.ContactBuilder;

/**
 * Represents a contact entry within the photon messaging.
 * This class serves as a data model for storing contact information, including
 * identity, session keys, profile details, and synchronization state.
 */
@JsonDeserialize(builder = ContactBuilder.class)
public interface Contact extends Comparable<Contact> {
	/**
	 * Enum Type represents the classification of a contact.
	 */
	enum Type {
		/**
		 * The system automatically adds the contact.
		 */
		AUTO(0),
		/**
		 * Represents a contact explicitly added as a friend by the user.
		 */
		FRIEND(1),
		/**
		 * Represents a contact associated with a specific messaging channel.
		 */
		CHANNEL(2);

		private final int value;

		Type(int value) {
			this.value = value;
		}

		/**
		 * Returns the stable numeric value associated with this type. This value is used
		 * for serialization purposes and is independent of the constant's declaration
		 * order, so it can be safely mapped back via {@link #valueOf(int)}.
		 *
		 * @return the contact type value.
		 */
		@JsonValue
		public int value() {
			return value;
		}

		/**
		 * Returns the {@code Type} corresponding to the specified numeric value.
		 *
		 * @param value the numeric value.
		 * @return the corresponding {@code Type}.
		 * @throws IllegalArgumentException if the value is invalid.
		 */
		@JsonCreator
		public static Type valueOf(int value) {
			return switch (value) {
				case 0 -> AUTO;
				case 1 -> FRIEND;
				case 2 -> CHANNEL;
				default -> throw new IllegalArgumentException("invalid type value");
			};
		}
	}

	/**
	 * Returns the unique identifier of the contact.
	 *
	 * @return the contact ID
	 */
	Id getId();

	/**
	 * Returns the type of the contact.
	 *
	 * @return the contact type
	 */
	Type getType();

	/**
	 * Returns the name of the contact.
	 *
	 * @return the contact name
	 */
	Optional<String> getName();

	/**
	 * Returns the local remark/alias for the contact.
	 *
	 * @return the remark string
	 */
	Optional<String> getRemark();

	/**
	 * Returns the tags associated with the contact.
	 *
	 * @return the tags string
	 */
	Optional<String> getTags();

	/**
	 * Returns whether the contact is muted.
	 *
	 * @return true if muted, false otherwise
	 */
	boolean isMuted();

	/**
	 * Returns whether the contact is blocked.
	 *
	 * @return true if blocked, false otherwise
	 */
	boolean isBlocked();

	/**
	 * Returns the creation timestamp of this contact entry.
	 *
	 * @return the creation time in milliseconds
	 */
	long getCreatedAt();

	/**
	 * Returns the timestamp of the last update to this contact.
	 *
	 * @return the update time in milliseconds
	 */
	long getUpdatedAt();

	/**
	 * Returns the synchronization revision number.
	 *
	 * @return the current revision
	 */
	int getRevision();

	/**
	 * Retrieves the avatar URL associated with the contact.
	 *
	 * @return a string representing the avatar URL or identifier
	 */
	Optional<String> getAvatar();

	/**
	 * Checks if the contact has an avatar associated with it.
	 *
	 * @return true if the avatar is not null, false otherwise
	 */
	default boolean hasAvatar() {
		return getAvatar().isPresent();
	}

	/**
	 * Retrieves the display name of the contact. The display name is typically
	 * derived from the contact's name or remark, providing a human-readable
	 * identifier for the contact.
	 *
	 * @return the display name of the contact
	 */
	String getDisplayName();

	/**
	 * Compares the specified contact with the current contact to determine if they are the same.
	 * A contact is considered the same if it is the same instance or if their unique identifiers match.
	 *
	 * @param contact the contact to compare with the current contact
	 * @return true if the specified contact is the same as the current contact, false otherwise
	 */
	default boolean is(Contact contact) {
		if (contact == this)
			return true;

		return this.getId().equals(contact.getId());
	}

	/**
	 * Creates a {@link Editor} instance for editing the current {@link Contact}.
	 * <p>
	 * Since {@link Contact} is immutable, the builder will return a new instance
	 * reflecting any modifications when {@link Editor#build()} is called.
	 *
	 * @return a {@link Editor} instance to modify and update the contact details
	 */
	Editor edit();

	/**
	 * A builder for creating or updating {@link Contact} instances.
	 */
	interface Editor {
		/**
		 * Sets the remark (alias) for the contact.
		 *
		 * @param remark the contact remark
		 * @return this builder instance
		 */
		Editor setRemark(@Nullable String remark);

		/**
		 * Sets the tags associated with the contact.
		 *
		 * @param tags the contact tags
		 * @return this builder instance
		 */
		Editor setTags(@Nullable String tags);

		/**
		 * Sets whether the contact is muted.
		 *
		 * @param muted true if muted, false otherwise
		 * @return this builder instance
		 */
		Editor setMuted(boolean muted);

		/**
		 * Sets whether the contact is blocked.
		 *
		 * @param blocked true if blocked, false otherwise
		 * @return this builder instance
		 */
		Editor setBlocked(boolean blocked);

		/**
		 * Builds and returns the {@link Contact} instance.
		 *
		 * @return the constructed Contact
		 */
		Contact build();
	}
}