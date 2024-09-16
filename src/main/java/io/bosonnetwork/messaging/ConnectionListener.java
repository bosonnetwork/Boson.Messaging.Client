package io.bosonnetwork.messaging;

public interface ConnectionListener {
	public default void onConnecting() {
	}

	public default void onConnected() {
	}

	public default void onDisconnected() {
	}
}
