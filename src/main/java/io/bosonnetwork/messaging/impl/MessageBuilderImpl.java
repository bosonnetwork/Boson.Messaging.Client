package io.bosonnetwork.messaging.impl;

import java.util.concurrent.CompletableFuture;

import io.bosonnetwork.messaging.Message;

public class MessageBuilderImpl extends Message.Builder {
	private MessagingClientImpl client;

	MessageBuilderImpl(MessagingClientImpl client, int type) {
		super(new MessageImpl(client.getUserId(), client.getNextMessageIndex(), type));
		this.client = client;
	}

	@Override
	public CompletableFuture<Message> send() {
		return client.sendMessage(build());
	}

	@Override
	public Message build() {
		return super.build();
	}
}
