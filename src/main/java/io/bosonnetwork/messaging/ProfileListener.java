package io.bosonnetwork.messaging;

public interface ProfileListener {
	public void onUserProfileAcquired(UserProfile profile);
	public void onUserProfileChanged(String name, boolean avatar);
}
