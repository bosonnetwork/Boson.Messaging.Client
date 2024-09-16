package io.bosonnetwork.messaging.impl;

import io.bosonnetwork.crypto.CryptoIdentity;
import io.bosonnetwork.messaging.UserProfile;

public class UserProfileImpl extends UserProfile {
	private CryptoIdentity identity;

	public UserProfileImpl(CryptoIdentity identity, String name, boolean avatar) {
		super(name, avatar);
		this.identity = identity;
	}

	public UserProfileImpl(CryptoIdentity identity, String name) {
		this(identity, name, false);
	}

	public UserProfileImpl(byte[] privateKey, String name, boolean avatar) {
		super(name, avatar);
		this.identity = new CryptoIdentity(privateKey);
	}

	public UserProfileImpl(byte[] privateKey, String name) {
		this(privateKey, name, false);
	}

	@Override
	protected CryptoIdentity getIdentity() {
		return identity;
	}
}
