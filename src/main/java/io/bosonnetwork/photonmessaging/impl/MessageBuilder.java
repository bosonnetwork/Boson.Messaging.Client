package io.bosonnetwork.photonmessaging.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.bosonnetwork.Id;
import io.bosonnetwork.photonmessaging.ContentDisposition;
import io.bosonnetwork.photonmessaging.ContentType;
import io.bosonnetwork.photonmessaging.Message;
import io.bosonnetwork.vertx.VertxFuture;

public class MessageBuilder implements Message.Builder {
	private final static Map<String, Object> EMPTY_HEADERS = Map.of();

	private final MessagingClientImpl client;
	private Id recipient;
	private Map<String, Object> headers;
	private Object body;

	protected MessageBuilder(MessagingClientImpl client, Id recipient) {
		this.client = client;
		this.headers = EMPTY_HEADERS;
	}

	@Override
	public MessageBuilder to(Id to) {
		Objects.requireNonNull(to, "to");
		this.recipient = to;
		return this;
	}

	@Override
	public MessageBuilder header(String name, Object value) {
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
	public MessageBuilder contentType(String contentType) {
		Objects.requireNonNull(contentType, "contentType");
		header(ContentType.HEADER_NAME, contentType);
		return this;
	}

	@Override
	public MessageBuilder contentDisposition(String contentDisposition) {
		Objects.requireNonNull(contentDisposition, "contentDisposition");
		header(ContentDisposition.HEADER_NAME, contentDisposition);
		return this;
	}

	@Override
	public MessageBuilder contentDisposition(ContentDisposition contentDisposition) {
		Objects.requireNonNull(contentDisposition, "contentDisposition");
		header(ContentDisposition.HEADER_NAME, contentDisposition.getValue());
		return this;
	}

	@Override
	public MessageBuilder body(byte[] body) {
		Objects.requireNonNull(body, "body");
		this.body = body;
		if (!headers.containsKey(ContentType.HEADER_NAME))
			headers.put(ContentType.HEADER_NAME, ContentType.BINARY);

		return this;
	}

	@Override
	public MessageBuilder body(String body) {
		Objects.requireNonNull(body, "body");
		this.body = body;
		return this;
	}

	@Override
	public MessageBuilder body(Object body) {
		Objects.requireNonNull(body, "body");
		this.body = body;
		return this;
	}

	protected Message build() {
		if (recipient == null)
			throw new IllegalStateException("Recipient not set");

		if (body == null)
			throw new IllegalStateException("Body not set");

		long now = System.currentTimeMillis();
		Id messageId = MessageImpl.generateId(client.getDeviceId(), now);
		DefaultContent<?> payload = new DefaultContent<>(headers, body);

		return new MessageImpl<>(messageId, recipient, Message.Type.CONTENT_MESSAGE, now, payload);
	}

	@Override
	public VertxFuture<Message> send() {
		return VertxFuture.of(client.sendMessage(build()));
	}
}