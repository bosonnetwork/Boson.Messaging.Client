package io.bosonnetwork.messaging.impl;

import io.bosonnetwork.Id;
import io.bosonnetwork.Identity;
import io.bosonnetwork.crypto.CryptoIdentity;
import io.bosonnetwork.messaging.DeviceProfile;

public class DeviceProfileImpl extends DeviceProfile {
	private Identity identity;

	public DeviceProfileImpl(Identity identity, String name, String app) {
		super(name, app);
		this.identity = identity;
	}

	public DeviceProfileImpl(byte[] privateKey, String name, String app) {
		super(name, app);
		this.identity = new CryptoIdentity(privateKey);
	}

	public DeviceProfileImpl(String name, String app) {
		super(name, app);
		this.identity = null;
	}

	@Override
	protected void updateIdentity(Identity identity) {
		if (this.identity != null)
			throw new IllegalStateException("identity already set");

		this.identity = identity;
	}

	@Override
	public Id getId() {
		return identity.getId();
	}

	@Override
	protected Identity getIdentity() {
		return identity;
	}

}
