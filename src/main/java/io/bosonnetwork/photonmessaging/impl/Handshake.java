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

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import io.bosonnetwork.Id;
import io.bosonnetwork.json.Json;

public class Handshake {
	private static final ObjectReader READER = Json.cborMapper().readerFor(Handshake.class);
	private static final ObjectWriter WRITER = Json.cborMapper().writerFor(Handshake.class);

	@JsonProperty(value = "t", required = true)
	private final long timestamp;
	@JsonProperty(value = "y", required = true)
	private final Type type;
	@JsonProperty(value = "b")
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	@JsonTypeInfo(
			use = JsonTypeInfo.Id.NAME,
			include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
			property = "y",
			defaultImpl = Void.class
	)
	@JsonSubTypes({
			@JsonSubTypes.Type(value = String.class, name = "fr"),
			@JsonSubTypes.Type(value = byte[].class, name = "fra")
	})
	private final Object body;

	private transient Id from;

	public enum Type {
		@JsonProperty("fr")
		FRIEND_REQUEST,
		@JsonProperty("fra")
		FRIEND_REQUEST_ACCEPT;

		public int value() {
			return this.ordinal() + 1;
		}

		public static Type valueOf(int value) {
			return switch (value) {
				case 1 -> FRIEND_REQUEST;
				case 2 -> FRIEND_REQUEST_ACCEPT;
				default -> throw new IllegalArgumentException("Invalid handshake type: " + value);
			};
		}
	}

	@JsonCreator
	protected Handshake(@JsonProperty(value = "t", required = true) long timestamp,
	                    @JsonProperty(value = "y", required = true) Type type,
	                    @JsonProperty(value = "b") Object body) {
		this.timestamp = timestamp;
		this.type = type;
		this.body = body;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public Type getType() {
		return type;
	}

	public Id getFrom() {
		return from;
	}

	protected void setFrom(Id from) {
		this.from = from;
	}

	@SuppressWarnings("unchecked")
	public <T> T getBody() {
		// Defensively copy a binary body (FRIEND_REQUEST_ACCEPT session key) so callers cannot
		// mutate the internal array; the String body (FRIEND_REQUEST hello) is immutable.
		if (body instanceof byte[] b)
			return (T) b.clone();

		return (T) body;
	}

	public byte[] serialize() {
		try {
			return WRITER.writeValueAsBytes(this);
		} catch (Exception e) {
			throw new IllegalStateException("INTERNAL ERROR: Handshake serialization", e);
		}
	}

	public static Handshake parse(byte[] data) {
		try {
			return READER.readValue(data);
		} catch (Exception e) {
			throw new IllegalStateException("INTERNAL ERROR: Handshake parsing", e);
		}
	}

	public static Handshake friendRequest(String hello, long timestamp) {
		return new Handshake(timestamp, Type.FRIEND_REQUEST, hello);
	}

	public static Handshake friendRequestAccept(byte[] sessionKey, long timestamp) {
		Objects.requireNonNull(sessionKey, "sessionKey");
		return new Handshake(timestamp, Type.FRIEND_REQUEST_ACCEPT, sessionKey);
	}
}