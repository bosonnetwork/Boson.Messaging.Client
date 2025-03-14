package io.bosonnetwork.messaging.impl;

import io.bosonnetwork.crypto.CryptoIdentity;
import io.bosonnetwork.messaging.UserProfile;

public class UserProfileImpl extends UserProfile {
	public UserProfileImpl(CryptoIdentity identity, String name, boolean avatar) {
		super(identity, name, avatar);
	}

	public UserProfileImpl(CryptoIdentity identity, String name) {
		this(identity, name, false);
	}

	public UserProfileImpl(byte[] privateKey, String name, boolean avatar) {
		super(new CryptoIdentity(privateKey), name, avatar);
	}

	public UserProfileImpl(byte[] privateKey, String name) {
		this(privateKey, name, false);
	}

	@Override
	public CryptoIdentity getIdentity() {
		return (CryptoIdentity)super.getIdentity();
	}
}
