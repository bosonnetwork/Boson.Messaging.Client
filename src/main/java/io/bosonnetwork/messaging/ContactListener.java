package io.bosonnetwork.messaging;

import java.util.List;

public interface ContactListener {
	public void onContactsUpdated(String baseVersionId, String newVersionId, List<Contact> contacts);

	public void onContactsCleared();
}
