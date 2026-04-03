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

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Hash;
import io.bosonnetwork.json.Json;

public class Notification<T> {
	private static final ObjectReader READER = Json.cborMapper().readerFor(GenericNotification.class);

	@JsonProperty(value = "i", required = true)
	private final Id id;
	@JsonProperty(value = "e", required = true)
	private final Event event;
	@JsonProperty(value = "s", required = true)
	private final Id source;
	@JsonProperty(value = "t", required = true)
	private final long timestamp;
	@JsonProperty(value = "c")
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private final T content;

	public enum Event {
		SESSION_NEW(0x01),
		CONTACT_MUTATE(0x02),
		CHANNEL_CREATE(0x11),
		CHANNEL_DELETE(0x12),
		CHANNEL_JOIN(0x13),
		CHANNEL_LEAVE(0x14),
		CHANNEL_TRANSFER_OWNERSHIP(0x15),
		CHANNEL_ROTATE_SESSION_KEY(0x16),
		CHANNEL_UPDATE_INFO(0x17),
		CHANNEL_UPDATE_MEMBERS_ROLE(0x18),
		CHANNEL_BAN_MEMBERS(0x19),
		CHANNEL_UNBAN_MEMBERS(0x1A),
		CHANNEL_REMOVE_MEMBERS(0x1B),
		CHANNEL_MEMBER_JOIN(0x1C),
		CHANNEL_MEMBER_LEAVE(0x1D),
		FRIEND_REQUEST(0x51);

		private final int value;

		Event(int value) {
			this.value = value;
		}

		@JsonValue
		public int value() {
			return value;
		}

		@JsonCreator
		public Event valueOf(int value) {
			return switch (value) {
				case 0x01 -> SESSION_NEW;
				case 0x02 -> CONTACT_MUTATE;
				case 0x11 -> CHANNEL_CREATE;
				case 0x12 -> CHANNEL_DELETE;
				case 0x13 -> CHANNEL_JOIN;
				case 0x14 -> CHANNEL_LEAVE;
				case 0x15 -> CHANNEL_TRANSFER_OWNERSHIP;
				case 0x16 -> CHANNEL_ROTATE_SESSION_KEY;
				case 0x17 -> CHANNEL_UPDATE_INFO;
				case 0x18 -> CHANNEL_UPDATE_MEMBERS_ROLE;
				case 0x19 -> CHANNEL_BAN_MEMBERS;
				case 0x1A -> CHANNEL_UNBAN_MEMBERS;
				case 0x1B -> CHANNEL_REMOVE_MEMBERS;
				case 0x1C -> CHANNEL_MEMBER_JOIN;
				case 0x1D -> CHANNEL_MEMBER_LEAVE;
				case 0x51 -> FRIEND_REQUEST;
				default -> throw new IllegalArgumentException("Invalid event value: " + value);
			};
		}
	}

	public Notification(Id id, Event event, Id source, long timestamp, T content) {
		this.id = id;
		this.event = event;
		this.source = source;
		this.timestamp = timestamp;
		this.content = content;
	}

	public Id getId() {
		return id;
	}

	public Event getEvent() {
		return event;
	}

	public Id getSource() {
		return source;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public T getContent() {
		return content;
	}

	public byte[] serialize() {
		try {
			return Json.cborMapper().writeValueAsBytes(this);
		} catch (Exception e) {
			throw new IllegalStateException("INTERNAL ERROR: Notification serialization", e);
		}
	}

	public static GenericNotification parse(byte[] data) {
		try {
			return READER.readValue(data);
		} catch (Exception e) {
			throw new IllegalStateException("INTERNAL ERROR: Notification parsing", e);
		}
	}

	protected boolean isAssociated(Id deviceId) {
		byte[] seedBytes = ByteBuffer.allocate(Long.BYTES).putLong(timestamp).array();
		byte[] hash = Hash.sha256(deviceId.bytes(), seedBytes);
		return Arrays.equals(id.bytes(), hash);
	}

	protected static Id generateId(Id deviceId, long seed) {
		byte[] seedBytes = ByteBuffer.allocate(Long.BYTES).putLong(seed).array();
		return Id.of(Hash.sha256(deviceId.bytes(), seedBytes));
	}

	public static class GenericNotification extends Notification<JsonNode> {
		@JsonCreator
		private GenericNotification(@JsonProperty(value = "i", required = true) Id id,
									@JsonProperty(value = "e", required = true) Event event,
		                            @JsonProperty(value = "s", required = true) Id source,
		                            @JsonProperty(value = "t", required = true) long timestamp,
		                            @JsonProperty(value = "c") JsonNode content) {
			super(id, event, source, timestamp, content);
		}

		public <T> T getContentAs(Class<T> clazz) {
			return Json.cborMapper().convertValue(getContent(), clazz);
		}

		public <T> List<T> getContentAsListOf(Class<T> clazz) {
			JavaType type = Json.cborMapper().getTypeFactory().constructCollectionType(List.class, clazz);
			return Json.cborMapper().convertValue(getContent(), type);
		}
	}
}