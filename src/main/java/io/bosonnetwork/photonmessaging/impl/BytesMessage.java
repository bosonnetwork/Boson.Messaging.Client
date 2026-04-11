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

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import io.bosonnetwork.Id;
import io.bosonnetwork.json.Json;
import io.bosonnetwork.photonmessaging.exceptions.MalformedMessageException;

public class BytesMessage extends PhotonMessage<byte[]> {
	private static final ObjectReader READER = Json.cborMapper().readerFor(BytesMessage.class);
	private static final ObjectWriter WRITER = Json.cborMapper().writerFor(BytesMessage.class);

	@JsonCreator
	public BytesMessage(@JsonProperty(value = "v", required = true) int version,
						@JsonProperty(value = "id", required = true) Id id,
						@JsonProperty(value = "r", required = true) Id recipient,
						@JsonProperty(value = "y", required = true) Type type,
						@JsonProperty(value = "f") Id from,
						@JsonProperty(value = "c", required = true) long createdAt,
						@JsonProperty(value = "p", required = true) byte[] payload) {
		super(version, id, recipient, type, from, createdAt, payload);
	}

	public BytesMessage(Id id, Id recipient, Type type, long createdAt, byte[] payload) {
		super(id, recipient, type, createdAt, payload);
	}

	private BytesMessage(PhotonMessage<?> ref, byte[] payload) {
		super(ref, payload);
	}

	public static BytesMessage parse(byte[] data) throws MalformedMessageException {
		try {
			return READER.readValue(data);
		} catch (IOException e) {
			throw new MalformedMessageException("Malformed message", e);
		}
	}

	/**
	 * Serializes this message to CBOR format.
	 *
	 * @return a buffer containing the serialized message
	 */
	public byte[] serialize() {
		try {
			return WRITER.writeValueAsBytes(this);
		} catch (IOException e) {
			throw new IllegalStateException("INTERNAL ERROR: BytesMessage serialization", e);
		}
	}

	protected <T> PhotonMessage<T> dup(T newPayload) {
		return new PhotonMessage<>(this, newPayload);
	}

	protected static BytesMessage dup(PhotonMessage<?> ref, byte[] payload) {
		return new BytesMessage(ref, payload);
	}
}