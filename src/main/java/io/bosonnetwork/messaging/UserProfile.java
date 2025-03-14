package io.bosonnetwork.messaging;

import io.bosonnetwork.Id;
import io.bosonnetwork.Identity;

public abstract class UserProfile {
	private Identity identity;
	private String name;
	private boolean avatar;

	protected UserProfile(Identity identity, String name, boolean avatar) {
		this.identity = identity;
		this.name = name;
		this.avatar = avatar;
	}

	public Id getId() {
		return getIdentity().getId();
	}

	protected Identity getIdentity() {
		return identity;
	}

	public String getName() {
		return name;
	}

	public boolean hasAvatar() {
		return avatar;
	}
}
