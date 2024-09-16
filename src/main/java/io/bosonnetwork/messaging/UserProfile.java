package io.bosonnetwork.messaging;

import io.bosonnetwork.Id;
import io.bosonnetwork.Identity;

public abstract class UserProfile {
	private String name;
	private boolean avatar;

	protected UserProfile(String name, boolean avatar) {
		this.name = name;
		this.avatar = avatar;
	}

	public Id getId() {
		return getIdentity().getId();
	}

	protected abstract Identity getIdentity();

	public String getName() {
		return name;
	}

	public boolean hasAvatar() {
		return avatar;
	}
}
