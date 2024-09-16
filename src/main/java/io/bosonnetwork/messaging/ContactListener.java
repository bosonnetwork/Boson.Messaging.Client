package io.bosonnetwork.messaging;

import java.util.List;

import io.bosonnetwork.Id;

public interface ContactListener {
	public void onContactsUpdated(String sequenceId, List<Contact> contacts);

	public void onContactsRemoved(String sequenceId, List<Id> contacts);

	public void onContactsSynced(String sequenceId, List<Contact> contacts);
}
