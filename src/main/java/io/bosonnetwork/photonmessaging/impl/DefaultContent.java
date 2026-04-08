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
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import io.bosonnetwork.json.Json;
import io.bosonnetwork.photonmessaging.ContentDisposition;
import io.bosonnetwork.photonmessaging.ContentType;
import io.bosonnetwork.photonmessaging.Message;

public class DefaultContent<B> implements Message.Content {
	private static final ObjectReader READER = Json.cborMapper().readerFor(new TypeReference<DefaultContent<JsonNode>>() {});
	private static final ObjectWriter WRITER = Json.cborMapper().writerFor(new TypeReference<DefaultContent<?>>() {});

	private final Map<String, Object> headers;
	private final B body;

	@JsonCreator
	public DefaultContent(@JsonProperty("headers") Map<String, Object> headers,
	                         @JsonProperty("body") B body) {
		this.headers = headers;
		this.body = body;
	}

	@Override
	public Map<String, Object> getHeaders() {
		return headers;
	}

	@Override
	public String getContentType() {
		return (String) headers.get(ContentType.HEADER_NAME);
	}

	@Override
	public ContentDisposition getContentDisposition() {
		if (headers.containsKey(ContentDisposition.HEADER_NAME))
			return ContentDisposition.parse((String) headers.get(ContentDisposition.HEADER_NAME));
		else
			return null;
	}

	public byte[] getBody() {
		return body != null ? Json.toBytes(body) : null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getBodyAs(Class<T> type) {
		if (body instanceof JsonNode n)
			return Json.cborMapper().convertValue(n, type);
		else
			return (T) body;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <E> List<E> getBodyAsListOf(Class<E> elementType) {
		if (body instanceof JsonNode n) {
			JavaType type = Json.cborMapper().getTypeFactory().constructCollectionType(List.class, elementType);
			return Json.cborMapper().convertValue(n, type);
		} else {
			return (List<E>) body;
		}
	}

	public byte[] serialize() {
		try {
			return WRITER.writeValueAsBytes(this);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("INTERNAL ERROR: DefaultMessageBody serialization", e);
		}
	}

	public static DefaultContent<JsonNode> parse(byte[] data) {
		try {
			return READER.readValue(data);
		} catch (Exception e) {
			throw new IllegalStateException("INTERNAL ERROR: DefaultMessageBody parsing", e);
		}
	}
}