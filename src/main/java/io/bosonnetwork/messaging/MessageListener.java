package io.bosonnetwork.messaging;

public interface MessageListener {
	public default void connecting() {
	}

	public default void connected() {
	}

	public default void disconnected() {
	}

	public void message(Message message);

	public void sent(Message message);

	public void broadcast(Message message);
}

