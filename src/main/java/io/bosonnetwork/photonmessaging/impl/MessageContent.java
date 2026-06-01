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
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import io.bosonnetwork.json.Json;
import io.bosonnetwork.photonmessaging.Message;

/**
 * Represents a compact, cross-language message envelope used in the messaging system.
 *
 * <p>
 * A MessageContent is a CBOR-optimized structure designed for efficient transport between
 * Java and Rust systems without schema translation.
 *
 * <p>
 * The structure is composed of:
 * <ul>
 *   <li>headers (h) - optional metadata map</li>
 *   <li>format (f) - determines body interpretation</li>
 *   <li>body (b) - polymorphic payload based on format</li>
 * </ul>
 *
 * <h2>Wire Format</h2>
 *
 * CBOR-equivalent JSON representation:
 *
 * <pre>
 * {
 *   "h": {
 *     "traceId": "abc",
 *     "Content-Type": "text/plain"
 *   },
 *   "f": "t",
 *   "b": "hello world"
 * }
 * </pre>
 *
 * <h2>Format Mapping</h2>
 *
 * <pre>
 * t → TEXT   (String)
 * b → BINARY (byte[])
 * o → OBJECT (JsonNode / structured object)
 * </pre>
 *
 * <h2>Rust Equivalent (Serde)</h2>
 *
 * <pre>
 * use serde::{Serialize, Deserialize};
 * use serde_json::Value;
 * use std::collections::HashMap;
 *
 * #[derive(Debug, Serialize, Deserialize)]
 * #[serde(rename_all = "lowercase")]
 * pub enum Format {
 *     #[serde(rename = "t")]
 *     Text,
 *     #[serde(rename = "b")]
 *     Binary,
 *     #[serde(rename = "o")]
 *     Object,
 * }
 *
 * #[derive(Debug, Serialize, Deserialize)]
 * pub struct MessageContent {
 *     #[serde(rename = "h", default)]
 *     pub headers: Option<HashMap<String, Value>>,
 *     #[serde(rename = "f")]
 *     pub format: Format,
 *     #[serde(rename = "b")]
 *     pub body: Body,
 * }
 *
 * #[derive(Debug, Serialize, Deserialize)]
 * #[serde(untagged)]
 * pub enum Body {
 *     Text(String),
 *     Binary(Vec<u8>),
 *     Object(Value),
 * }
 *
 * // Alternative stricter version (recommended if format must be trusted):
 * //
 * // #[serde(tag = "f", content = "b")]
 * // pub enum MessageContent {
 * //     #[serde(rename = "t")]
 * //     Text(String),
 * //     #[serde(rename = "b")]
 * //     Binary(Vec<u8>),
 * //     #[serde(rename = "o")]
 * //     Object(Value),
 * // }
 * </pre>
 *
 * <h2>Design Notes</h2>
 *
 * <ul>
 *   <li>Format field (f) is the single source of truth for body decoding</li>
 *   <li>Supports CBOR compact encoding with short field names</li>
 *   <li>Java uses Jackson EXTERNAL_PROPERTY binding (f → b)</li>
 *   <li>Rust uses serde enum mapping or untagged body representation</li>
 *   <li>OBJECT format uses JsonNode in Java and serde_json::Value in Rust</li>
 *   <li>Binary format is raw byte array (no base64 transformation in CBOR)</li>
 * </ul>
 */
public class MessageContent implements Message.Content {
	private static final ObjectReader READER = Json.cborMapper().readerFor(MessageContent.class);
	private static final ObjectWriter WRITER = Json.cborMapper().writerFor(MessageContent.class);

	@JsonProperty("h")
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	private final Map<String, Object> headers;
	@JsonProperty("f")
	private final Format format;

	@JsonProperty("b")
	@JsonTypeInfo(
			use = JsonTypeInfo.Id.NAME,
			include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
			property = "f"
	)
	@JsonSubTypes({
			@JsonSubTypes.Type(value = String.class, name = "t"),
			@JsonSubTypes.Type(value = byte[].class, name = "b"),
			@JsonSubTypes.Type(value = JsonNode.class, name = "o")
	})
	private Object body;

	private transient Object origin;

	public enum Format {
		@JsonProperty("t")
		TEXT,
		@JsonProperty("b")
		BINARY,
		@JsonProperty("o")
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
				body = ((byte[]) body).clone();
			}
			case OBJECT -> {}
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
		// The constructor already defensively clones a binary body, so don't pre-clone here.
		return new MessageContent(headers, Format.BINARY, binary);
	}

	public static MessageContent binary(byte[] binary) {
		return binary(null, binary);
	}

	public static MessageContent object(Map<String, Object> headers, Object object) {
		Objects.requireNonNull(object, "object");
		return new MessageContent(headers, Format.OBJECT, Json.cborMapper().valueToTree(object), object);
	}

	public static MessageContent object(Object object) {
		return object(null, object);
	}

	@Override
	public Map<String, Object> getHeaders() {
		return headers;
	}

	public Format getFormat() {
		return format;
	}

	@SuppressWarnings("unchecked")
	public <T> T getBody() {
		// Defensively copy a binary body so callers cannot mutate the internal array (mirrors
		// asBinary() and the constructor); other body types are immutable (String) or read-only.
		if (body instanceof byte[] b)
			return (T) b.clone();

		return (T) body;
	}

	@Override
	public String asText() {
		return switch (format) {
			case TEXT -> (String) body;
			case BINARY -> new String((byte[]) body, StandardCharsets.UTF_8);
			case OBJECT -> Json.toString(body);
		};
	}

	@Override
	public byte[] asBinary() {
		return switch (format) {
			case TEXT -> ((String) body).getBytes(StandardCharsets.UTF_8);
			case BINARY-> ((byte[]) body).clone();
			case OBJECT -> Json.toBytes(body);
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

		T obj = Json.cborMapper().convertValue(body, type);
		origin = obj;
		return obj;
	}

	@Override
	public <E> List<E> asList(Class<E> elementType) {
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

		List<E> lst = Json.cborMapper().convertValue(body,
				Json.cborMapper().getTypeFactory().constructCollectionType(List.class, elementType));
		origin = lst;
		return lst;
	}

	@Override
	public <K, V> Map<K, V> asMap(Class<K> keyType, Class<V> valueType) {
		if (format != Format.OBJECT)
			throw new UnsupportedOperationException("Not an object content");

		if (origin != null) {
			if (origin instanceof Map<?, ?> map) {
				for (Map.Entry<?, ?> e : map.entrySet()) {
					if (!keyType.isInstance(e.getKey()))
						throw new IllegalArgumentException("Invalid key type");

					if (!valueType.isInstance(e.getValue()))
						throw new IllegalArgumentException("Invalid value type");
				}
				@SuppressWarnings("unchecked")
				Map<K, V> result = (Map<K, V>) map;
				return result;
			}

			throw new IllegalArgumentException("Invalid object type");
		}

		Map<K, V> map = Json.cborMapper().convertValue(body,
				Json.cborMapper().getTypeFactory().constructMapType(Map.class, keyType, valueType));
		origin = map;
		return map;
	}

	public byte[] serialize() {
		try {
			return WRITER.writeValueAsBytes(this);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("INTERNAL ERROR: MessageContent serialization", e);
		}
	}

	public static MessageContent parse(byte[] data) {
		try {
			return READER.readValue(data);
		} catch (Exception e) {
			throw new IllegalStateException("INTERNAL ERROR: MessageContent parsing", e);
		}
	}
}