package io.bosonnetwork.messaging;

public interface MessageListener {
	public void onMessage(Message message);

	public void onSent(Message message);

	public void onBroadcast(Message message);
}

