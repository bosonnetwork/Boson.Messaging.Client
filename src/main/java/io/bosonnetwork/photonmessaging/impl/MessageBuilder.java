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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.bosonnetwork.Id;
import io.bosonnetwork.json.Json;
import io.bosonnetwork.photonmessaging.ContentDisposition;
import io.bosonnetwork.photonmessaging.ContentType;
import io.bosonnetwork.photonmessaging.Message;
import io.bosonnetwork.vertx.VertxFuture;

public class MessageBuilder implements Message.Builder {
	private final PhotonMessagingClient client;
	private Id recipient;
	private final Map<String, Object> headers;
	MessageContent.Format format;
	private Object content;
	private Object origin;

	protected MessageBuilder(PhotonMessagingClient client, Id recipient) {
		this.client = client;
		this.headers = new HashMap<>();
		this.recipient = recipient;
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
		if (value != null)
			headers.put(name, value);
		else
			headers.remove(name);

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
	public MessageBuilder contentText(String text) {
		Objects.requireNonNull(text, "text");
		this.format = MessageContent.Format.TEXT;
		this.content = text;
		return this;
	}

	@Override
	public MessageBuilder contentBinary(byte[] binary) {
		Objects.requireNonNull(binary, "binary");
		this.format = MessageContent.Format.BINARY;
		this.content = binary;
		if (!headers.containsKey(ContentType.HEADER_NAME))
			headers.put(ContentType.HEADER_NAME, ContentType.BINARY);

		return this;
	}

	@Override
	public MessageBuilder contentObject(Object object) {
		Objects.requireNonNull(object, "object");
		this.format = MessageContent.Format.OBJECT;
		this.content = Json.toBytes(object);
		this.origin = object;
		if (!headers.containsKey(ContentType.HEADER_NAME))
			headers.put(ContentType.HEADER_NAME, ContentType.CBOR);

		return this;
	}

	@Override
	public Message.Builder contentJson(String json) {
		Objects.requireNonNull(json, "json");
		this.format = MessageContent.Format.OBJECT;
		this.content = Json.jsonToCbor(json);
		this.origin = null;
		if (!headers.containsKey(ContentType.HEADER_NAME))
			headers.put(ContentType.HEADER_NAME, ContentType.JSON);
		return this;
	}

	@Override
	public Message.Builder contentCbor(byte[] cbor) {
		Objects.requireNonNull(cbor, "cbor");
		this.format = MessageContent.Format.OBJECT;
		this.content = cbor;
		this.origin = null;
		if (!headers.containsKey(ContentType.HEADER_NAME))
			headers.put(ContentType.HEADER_NAME, ContentType.CBOR);
		return this;
	}

	protected Message build() {
		if (recipient == null)
			throw new IllegalStateException("Recipient not set");

		long now = System.currentTimeMillis();
		Id messageId = PhotonMessage.generateId(client.getDeviceId(), now);

		if (content == null)
			throw new IllegalStateException("content not set");

		MessageContent payload = switch (format) {
			case TEXT -> MessageContent.text(headers, (String) content);
			case BINARY -> MessageContent.binary(headers, (byte[]) content);
			case OBJECT -> MessageContent.objectWithSerialized(headers, (byte[]) content, origin);
		};
		return new PhotonMessage<>(messageId, recipient, Message.Type.CONTENT_MESSAGE, now, payload);
	}

	@Override
	public VertxFuture<Message> send() {
		return VertxFuture.of(client.sendMessage(build()));
	}
}