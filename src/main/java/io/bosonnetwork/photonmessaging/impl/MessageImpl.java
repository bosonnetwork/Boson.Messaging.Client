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
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.vertx.core.Future;
import io.vertx.core.Promise;

import io.bosonnetwork.Id;
import io.bosonnetwork.json.Json;
import io.bosonnetwork.photonmessaging.Message;
import io.bosonnetwork.photonmessaging.impl.rpc.RpcRequest;

@JsonPropertyOrder({"v", "r", "y", "f", "s", "c", "p"})
public class MessageImpl<P> implements Message {
	private static final int VERSION = 2;

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
	private P payload;

	private long id;
	private Id conversationId;
	private long receivedAt;

	private Promise<Void> sentPromise;

	@JsonCreator
	protected MessageImpl() {
		this(Type.CONTENT_MESSAGE, null, null);
	}

	protected MessageImpl(Type type) {
		this(type, null, null);
	}

	protected MessageImpl(Type type, Id recipient, P payload) {
		this.version = VERSION;
		this.type = type;
		this.recipient = recipient;
		this.serialNumber = nextSerialNumber();
		this.createdAt = System.currentTimeMillis();
		this.payload = payload;
	}

	protected MessageImpl(MessageImpl<?> message, P newPayload) {
		this.version = message.version;
		this.recipient = message.recipient;
		this.type = message.type;
		this.from = message.from;
		this.serialNumber = message.serialNumber;
		this.createdAt = message.createdAt;
		this.payload = newPayload;

		this.id = message.id;
		this.conversationId = message.conversationId;
		this.receivedAt = message.receivedAt;
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

	protected MessageImpl<P> setConversationId(Id conversationId) {
		this.conversationId = conversationId;
		return this;
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

	protected void received() {
		this.receivedAt = System.currentTimeMillis();
	}

	@Override
	public byte[] getPayloadAsBytes() {
		if (payload == null)
			return null;

		if (payload instanceof byte[] bytes)
			return bytes;

		if (payload instanceof DefaultContent<?> p)
			return p.serialize();

		if (payload instanceof RpcRequest<?> q)
			return q.serialize();

		try {
			return Json.cborMapper().writeValueAsBytes(payload);
		} catch (IOException e) {
			throw new IllegalStateException("Message payload deserialization failed", e);
		}
	}

	@Override
	public Content getPayloadAsContent() {
		if (payload == null)
			return null;

		if (payload instanceof Content c)
			return c;

		if (payload instanceof byte[] bytes)
			return DefaultContent.parse(bytes);

		throw new IllegalStateException("Message payload is not a Content");
	}

	protected void setPayload(P payload) {
		this.payload = payload;
	}

	protected P getPayload() {
		return payload;
	}

	protected void prepareForSending() {
		this.sentPromise = Promise.promise();
	}

	protected void sent() {
		if (sentPromise == null)
			throw new IllegalStateException("Message has not been sent yet");
		sentPromise.tryComplete();
	}

	protected void failed(Throwable e) {
		if (sentPromise == null)
			throw new IllegalStateException("Message has not been sent yet");
		sentPromise.tryFail(e);
	}

	protected Future<Void> getFuture() {
		if (sentPromise == null)
			throw new IllegalStateException("Message has not been sent yet");
		return sentPromise.future();
	}
}