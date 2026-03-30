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
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import io.bosonnetwork.Id;
import io.bosonnetwork.json.Json;
import io.bosonnetwork.photonmessaging.MalformedMessageException;
import io.bosonnetwork.photonmessaging.Message;

public class MessageImpl implements Message {
	private static final int VERSION = 2;

	private static final ObjectReader READER = Json.cborMapper().readerFor(MessageImpl.class);
	private static final ObjectWriter WRITER = Json.cborMapper().writerFor(MessageImpl.class);

	private static final AtomicInteger messageSerialNumber = new AtomicInteger(1);

	@JsonProperty(value = "v" , required = true)
	private final int version;
	@JsonProperty(value = "r", required = true)
	private Id recipient;
	@JsonProperty(value = "y", required = true)
	private final Type type;
	@JsonProperty(value = "f")
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private Id from;
	@JsonProperty(value = "s", required = true)
	private int serialNumber;
	@JsonProperty(value = "c")
	@JsonInclude(JsonInclude.Include.NON_DEFAULT)
	private long createdAt;

	@JsonProperty(value = "p", required = true)
	private byte[] payload;

	private long id;
	private Id conversationId;
	private long receivedAt;

	@JsonCreator
	protected MessageImpl() {
		this(Type.CONTENT_MESSAGE, null);
	}

	protected MessageImpl(Type type) {
		this(type, null);
	}

	protected MessageImpl(Type type, Id recipient) {
		this.version = VERSION;
		this.type = type;
		this.recipient = recipient;
		this.serialNumber = nextSerialNumber();
		this.createdAt = System.currentTimeMillis();
	}

	private static int nextSerialNumber() {
		// if 0 or Integer.MAX_VALUE, it becomes 1 (first valid messageId)
		return messageSerialNumber.getAndUpdate(i -> i == 0 || i == Integer.MAX_VALUE ? 1 : i + 1);
	}

	public int getVersion() {
		return version;
	}

	@Override
	public long getId() {
		return id;
	}

	@Override
	public Id getConversationId() {
		return conversationId;
	}

	@Override
	public Id getRecipient() {
		return recipient;
	}

	protected void setRecipient(Id recipient) {
		this.recipient = recipient;
	}

	@Override
	public Type getType() {
		return type;
	}

	@Override
	public Id getFrom() {
		return from;
	}

	@Override
	public int getSerialNumber() {
		return serialNumber;
	}

	@Override
	public long getCreatedAt() {
		return createdAt;
	}

	@Override
	public long getReceivedAt() {
		return receivedAt;
	}

	public byte[] getPayload() {
		return payload;
	}

	public void setPayload(byte[] payload) {
		this.payload = payload;
	}

	@Override
	public <T> T getPayloadAs() {
		try {
			return Json.cborMapper().readValue(payload, new TypeReference<>() {});
		} catch (IOException e) {
			throw new IllegalStateException("Message payload deserialization failed", e);
		}
	}

	@Override
	public Content getPayloadAsContent() {
		return DefaultMessagePayload.parse(payload);
	}

	public static MessageImpl parse(byte[] data) throws MalformedMessageException {
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
			throw new IllegalStateException("INTERNAL ERROR: Message serialization", e);
		}
	}
}