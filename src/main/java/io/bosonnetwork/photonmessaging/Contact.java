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
 * A contact entry in photon messaging: the local, user-owned relationship metadata for another
 * party (a friend, an auto-added peer, or a channel), plus the state needed to synchronize that
 * metadata across the user's own devices.
 * <p>
 * A contact owns only what the local user controls: the session key, the local {@link #getRemark()
 * remark} (alias), {@link #getTags() tags}, {@link #isMuted() muted} / {@link #isBlocked() blocked}
 * flags, and the {@code createdAt} / {@code updatedAt} / {@code revision} synchronization bookkeeping.
 * Channel contacts additionally carry channel metadata (name, notice) because a channel is owned by a
 * super node rather than a user, so that metadata is itself part of contact synchronization; for all
 * other contact types {@link #getName()} is expected to be empty.
 * <p>
 * <b>Design boundary: contacts do not resolve, cache, or present user profiles.</b> A user's public
 * profile (display name, avatar, bio, home node) is owned and published by that user, normally as a
 * Boson Identity Card (DID document) on the DHT, and is intentionally NOT part of a contact or of
 * contact synchronization. This library therefore exposes only the two profile inputs it legitimately
 * owns (the local remark, and for channels the name); it does not hold a cached profile name or
 * avatar and does not compute a display name. Resolving, caching, and presenting profiles is the
 * application's responsibility, keyed by user {@link Id} rather than by {@code Contact}. The reasons:
 * <ul>
 *   <li><b>The "not a contact yet" cases are not exceptions.</b> Friend requests, channel members,
 *       group-chat senders, search results, and inbound-message notifiers all present a user who may
 *       not be a contact. If the name/avatar selection policy lived on {@code Contact}, every such
 *       surface would need its own second copy of it and the two would drift. The natural key is the
 *       user id, which every surface has - not the {@code Contact}, which many do not.</li>
 *   <li><b>Names and avatars are different kinds of data.</b> A name is a small string; an avatar is
 *       an asynchronously fetched, cached, and rendered image. Returning avatar bytes would duplicate
 *       the application's image pipeline, and returning an avatar URL would need the application's
 *       service endpoint regardless - neither belongs in a messaging library.</li>
 *   <li><b>Resolution is reactive; this API is not.</b> Useful resolution is "show a fallback now,
 *       upgrade when the real value arrives." Grafting that onto a synchronous, listener-based API
 *       either loses the upgrade or re-implements the application's reactive resolver inside the
 *       library.</li>
 *   <li><b>Ownership and threading stay clean.</b> Profile resolution needs the application's
 *       network, auth, and caching, run off the messaging event loop; keeping it in the application
 *       avoids the library calling up into application services on its own event loop.</li>
 *   <li><b>Short-id formatting is presentation.</b> How an unresolved id is abbreviated is a UI
 *       decision that varies by surface and does not belong in a domain library.</li>
 * </ul>
 * In short: the library owns messaging, contact management, and contact synchronization; the
 * application owns profile resolution, caching, and the selection of a display name and avatar
 * (preference: remark, then resolved profile name, then a short id).
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
	 * The name for a channel contact is the channel name. For other contact types should be null.
	 *
	 * @return an {@link Optional} holding the contact name, or an empty {@code Optional} if
	 *         no name is set.
	 */
	Optional<String> getName();

	/**
	 * Returns the local remark/alias for the contact.
	 *
	 * @return an {@link Optional} holding the remark, or an empty {@code Optional} if no
	 *         remark is set.
	 */
	Optional<String> getRemark();

	/**
	 * Returns the tags associated with the contact.
	 *
	 * @return an {@link Optional} holding the tags, or an empty {@code Optional} if no tags
	 *         are set.
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