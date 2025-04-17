package io.bosonnetwork.messaging;

import java.util.List;

import io.bosonnetwork.Id;

public interface ContactListener {
	// before push update
	public void onContactsUpdating(String versionId, List<Contact> contacts);
	// after push update
	public void onContactsUpdated(String baseVersionId, String newVersionId, List<Contact> contacts);

	public void onContactsCleared();

	public void onContactProfile(Id contactId, Profile profile);
}
