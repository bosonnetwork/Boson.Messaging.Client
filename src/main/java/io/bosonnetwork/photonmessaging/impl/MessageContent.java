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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import io.bosonnetwork.json.Json;
import io.bosonnetwork.photonmessaging.Message;

public class MessageContent implements Message.Content {
	private static final ObjectReader READER = Json.cborMapper().readerFor(MessageContent.class);
	private static final ObjectWriter WRITER = Json.cborMapper().writerFor(MessageContent.class);

	@JsonProperty("h")
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	private final Map<String, Object> headers;
	@JsonProperty("f")
	private final Format format;

	// the format and body type mapping:
	// - TEXT -> String
	// - BINARY -> byte[](raw bytes)
	// - OBJECT -> byte[](CBOR format)
	@JsonProperty("b")
	private Object body;

	private transient Object origin;

	public enum Format {
		@JsonProperty("text")
		TEXT,
		@JsonProperty("binary")
		BINARY,
		@JsonProperty("object")
		OBJECT
	}

	@JsonCreator
	private MessageContent(@JsonProperty(value = "h") Map<String, Object> headers,
	                       @JsonProperty(value = "f", required = true) Format format,
	                       @JsonProperty(value = "b", required = true) Object body) {
		switch (format) {
			case TEXT -> {
				if (!(body instanceof String))
					throw new IllegalArgumentException("Invalid text content");
			}
			case BINARY -> {
				if (!(body instanceof byte[]))
					throw new IllegalArgumentException("Invalid binary content");
			}
			case OBJECT -> {
				if (!(body instanceof byte[]))
					throw new IllegalArgumentException("Invalid object content");
			}
		}

		this.headers = headers == null || headers.isEmpty() ? Map.of() : Map.copyOf(headers);
		this.format = format;
		this.body = body;
	}

	private MessageContent(Map<String, Object> headers, Format format, Object body, Object origin) {
		this(headers, format, body);
		this.origin = origin;
	}

	public static MessageContent text(Map<String, Object> headers, String text) {
		Objects.requireNonNull(text, "text");
		return new MessageContent(headers, Format.TEXT, text, text);
	}

	public static MessageContent text(String text) {
		return text(null, text);
	}

	public static MessageContent binary(Map<String, Object> headers, byte[] binary) {
		Objects.requireNonNull(binary, "binary");
		return new MessageContent(headers, Format.BINARY, binary, binary);
	}

	public static MessageContent binary(byte[] binary) {
		return binary(null, binary);
	}

	public static MessageContent object(Map<String, Object> headers, Object object) {
		Objects.requireNonNull(object, "object");
		return new MessageContent(headers, Format.OBJECT, Json.toBytes(object), object);
	}

	public static MessageContent object(Object object) {
		return object(null, object);
	}

	public static MessageContent objectWithSerialized(Map<String, Object> headers, byte[] serialized) {
		Objects.requireNonNull(serialized, "serialized");
		return new MessageContent(headers, Format.OBJECT, serialized, null);
	}

	public static MessageContent objectWithSerialized(byte[] serialized) {
		return objectWithSerialized(Map.of(), serialized);
	}

	public static MessageContent objectWithSerialized(Map<String, Object> headers, byte[] serialized, Object object) {
		Objects.requireNonNull(serialized, "serialized");
		return new MessageContent(headers, Format.OBJECT, serialized, object);
	}

	public static MessageContent objectWithSerialized(byte[] serialized, Object object) {
		return objectWithSerialized(Map.of(), serialized, object);
	}

	@Override
	public Map<String, Object> getHeaders() {
		return headers;
	}

	public Format getFormat() {
		return format;
	}

	public Object getBody() {
		return body;
	}

	@Override
	public String asText() {
		return switch (format) {
			case TEXT -> (String) body;
			case BINARY -> new String((byte[]) body, StandardCharsets.UTF_8);
			case OBJECT -> Json.cborToJson((byte[]) body);
		};
	}

	@Override
	public byte[] asBinary() {
		return switch (format) {
			case TEXT -> ((String) body).getBytes(StandardCharsets.UTF_8);
			case BINARY, OBJECT -> (byte[]) body;
		};
	}

	@Override
	public <T> T asObject(Class<T> type) {
		if (format != Format.OBJECT)
			throw new UnsupportedOperationException("Not an object content");

		if (origin != null) {
			if (type.isInstance(origin))
				return type.cast(origin);
			else
				throw new IllegalArgumentException("Invalid object type");
		}

		T obj = Json.parse((byte[]) body, type);
		origin = obj;
		return obj;
	}

	@Override
	public <E> List<E> asListOf(Class<E> elementType) {
		if (format != Format.OBJECT)
			throw new UnsupportedOperationException("Not an object content");

		if (origin != null) {
			if (origin instanceof List<?> list) {
				for (Object e : list) {
					if (!elementType.isInstance(e))
						throw new IllegalArgumentException("Invalid element type");
				}
				@SuppressWarnings("unchecked")
				List<E> result = (List<E>) list;
				return result;
			}

			throw new IllegalArgumentException("Invalid object type");
		}

		List<E> lst = Json.parse((byte[]) body,
				Json.cborMapper().getTypeFactory().constructCollectionType(List.class, elementType));
		origin = lst;
		return lst;
	}

	public byte[] serialize() {
		try {
			return WRITER.writeValueAsBytes(this);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("INTERNAL ERROR: DefaultContent serialization", e);
		}
	}

	public static MessageContent parse(byte[] data) {
		try {
			return READER.readValue(data);
		} catch (Exception e) {
			throw new IllegalStateException("INTERNAL ERROR: DefaultContent parsing", e);
		}
	}
}