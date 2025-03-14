package io.bosonnetwork.messaging.impl;

import io.bosonnetwork.Identity;
import io.bosonnetwork.crypto.CryptoIdentity;
import io.bosonnetwork.messaging.DeviceProfile;

public class DeviceProfileImpl extends DeviceProfile {
	public DeviceProfileImpl(Identity identity, String name, String app) {
		super(identity, name, app);
	}

	public DeviceProfileImpl(byte[] privateKey, String name, String app) {
		super(new CryptoIdentity(privateKey), name, app);
	}

	public DeviceProfileImpl(String name, String app) {
		super(null, name, app);
	}

	@Override
	public Identity getIdentity() {
		return super.getIdentity();
	}

}
