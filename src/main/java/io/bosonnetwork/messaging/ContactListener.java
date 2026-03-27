package io.bosonnetwork.messaging;

import java.util.List;

import io.bosonnetwork.Id;
import io.bosonnetwork.photonmessaging.impl.AbstractContact;

public interface ContactListener {
	// before push update
	public void onContactsUpdating(String versionId, List<AbstractContact> contacts);
	// after push update
	public void onContactsUpdated(String baseVersionId, String newVersionId, List<AbstractContact> contacts);

	public void onContactsCleared();

	public void onContactProfile(Id contactId, Profile profile);
}