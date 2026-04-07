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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import io.bosonnetwork.photonmessaging.Contact;

/**
 * Represents a contact sync notification or response.
 */
public class ContactSync {
	@JsonProperty(value = "t", required = true)
	private final Type type;

	// last revision of the server
	@JsonProperty(value = "v", required = true)
	private final int revision;

	@JsonProperty("d")
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	private final List<ContactMutation.GenericContactMutation> mutations;

	@JsonProperty("s")
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	private final List<Contact> contacts;

	public enum Type {
		UP_TO_DATE(0),
		DELTA(1),
		SNAPSHOT(2);

		private final int value;

		Type(int value) {
			this.value = value;
		}

		@JsonValue
		public int value() {
			return value;
		}

		@JsonCreator
		public static Type valueOf(int value) {
			return switch (value) {
				case 0 -> UP_TO_DATE;
				case 1 -> DELTA;
				case 2 -> SNAPSHOT;
				default -> throw new IllegalArgumentException("Invalid contact sync type: " + value);
			};
		}
	}

	@JsonCreator
	protected ContactSync(@JsonProperty(value = "t", required = true) Type type,
	                      @JsonProperty(value = "v", required = true) int revision,
	                      @JsonProperty("d") List<ContactMutation.GenericContactMutation> mutations,
	                      @JsonProperty("s") List<Contact> contacts) {
		if ((mutations != null && !mutations.isEmpty()) && (contacts != null && !contacts.isEmpty()))
			throw new IllegalArgumentException("Mutations and contacts can not be both present");

		if (type == Type.UP_TO_DATE && mutations != null && contacts != null)
			throw new IllegalArgumentException("Up-to-date sync does not require mutations or contacts");

		if (type == Type.DELTA && (mutations == null || mutations.isEmpty()))
			throw new IllegalArgumentException("Delta sync requires mutations");

		if (type == Type.SNAPSHOT && (contacts == null || contacts.isEmpty()))
			throw new IllegalArgumentException("Snapshot sync requires contacts");

		this.type = type;
		this.revision = revision;
		this.contacts = contacts == null || contacts.isEmpty() ? null : contacts;
		this.mutations = mutations == null || mutations.isEmpty() ? null : mutations;
	}

	public Type getType() {
		return type;
	}

	public int getRevision() {
		return revision;
	}

	public List<ContactMutation.GenericContactMutation> getMutations() {
		return mutations;
	}

	public List<Contact> getContacts() {
		return contacts;
	}
}