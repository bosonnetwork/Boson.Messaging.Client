package io.bosonnetwork.photonmessaging.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import io.bosonnetwork.Id;
import io.bosonnetwork.photonmessaging.ContentDisposition;
import io.bosonnetwork.photonmessaging.ContentType;
import io.bosonnetwork.photonmessaging.Message;

public class MessageBuilder implements Message.Builder {
	private final static Map<String, Object> EMPTY_HEADERS = Map.of();

	private final MessageImpl message;
	private final MessagingClientImpl client;
	private Map<String, Object> headers;
	private Object body;

	protected MessageBuilder(MessagingClientImpl client, Id recipient) {
		this.message = new MessageImpl(Message.Type.CONTENT_MESSAGE, recipient);
		this.client = client;
		this.headers = EMPTY_HEADERS;
	}

	protected MessageBuilder(MessagingClientImpl client) {
		this(client, null);
	}

	@Override
	public Message.Builder to(Id to) {
		Objects.requireNonNull(to, "to");
		message.setRecipient(to);
		return this;
	}

	@Override
	public Message.Builder header(String name, Object value) {
		Objects.requireNonNull(name, "name");
		if (value != null) {
			if (headers == EMPTY_HEADERS)
				headers = new HashMap<>();

			headers.put(name, value);
		} else {
			if (headers != EMPTY_HEADERS)
				headers.remove(name);
		}

		return this;
	}

	@Override
	public Message.Builder contentType(String contentType) {
		Objects.requireNonNull(contentType, "contentType");
		header(ContentType.HEADER_NAME, contentType);
		return this;
	}

	@Override
	public Message.Builder contentDisposition(String contentDisposition) {
		Objects.requireNonNull(contentDisposition, "contentDisposition");
		header(ContentDisposition.HEADER_NAME, contentDisposition);
		return this;
	}

	@Override
	public Message.Builder contentDisposition(ContentDisposition contentDisposition) {
		Objects.requireNonNull(contentDisposition, "contentDisposition");
		header(ContentDisposition.HEADER_NAME, contentDisposition.getValue());
		return this;
	}

	@Override
	public Message.Builder body(byte[] body) {
		Objects.requireNonNull(body, "body");
		this.body = body;
		if (!headers.containsKey(ContentType.HEADER_NAME))
			headers.put(ContentType.HEADER_NAME, ContentType.BINARY);

		return this;
	}

	@Override
	public Message.Builder body(String body) {
		Objects.requireNonNull(body, "body");
		this.body = body;
		return this;
	}

	@Override
	public Message.Builder body(Object body) {
		Objects.requireNonNull(body, "body");
		this.body = body;
		return this;
	}

	protected Message build() {
		if (message.getRecipient() == null)
			throw new IllegalStateException("Recipient not set");

		if (body == null)
			throw new IllegalStateException("Body not set");

		DefaultMessagePayload<?> payload = new DefaultMessagePayload<>(headers, body);
		message.setPayload(payload.serialize());
		return message;
	}

	@Override
	public CompletableFuture<Message> send() {
		return client.send(build);
	}
}