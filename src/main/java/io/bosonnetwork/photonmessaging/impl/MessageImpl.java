package io.bosonnetwork.photonmessaging.impl;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
	private Type type;
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
		this.version = VERSION;
	}

	public MessageImpl(Id from, Id recipient, Type type, byte[] payload) {
		this.version = VERSION;
		this.from = from;
		this.recipient = recipient;
		this.type = type;
		this.serialNumber = nextSerialNumber();
		this.createdAt = System.currentTimeMillis();
		this.payload = payload;
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

	@Override
	public <T> T getPayloadAs() {
		return null;
	}

	@Override
	public Content getPayloadAsContent() {
		return null;
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