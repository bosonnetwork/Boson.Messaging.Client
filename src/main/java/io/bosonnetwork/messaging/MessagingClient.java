package io.bosonnetwork.messaging;

import java.util.concurrent.CompletableFuture;

import io.bosonnetwork.Id;

public interface MessagingClient {
	public Id getUserId();

	public Id getDeviceId();

	public CompletableFuture<Void> connect();

	public CompletableFuture<Void> disconnect();

	public boolean isConnected();

	public CompletableFuture<Void> sendMessage(Message message);

	public CompletableFuture<Void> close();

	public static ClientBuilder builder() {
		return new ClientBuilder();
	}
}
