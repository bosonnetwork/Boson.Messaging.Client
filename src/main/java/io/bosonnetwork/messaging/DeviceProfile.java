package io.bosonnetwork.messaging;

import java.util.Objects;

import io.bosonnetwork.Id;
import io.bosonnetwork.Identity;

public abstract class DeviceProfile {
	private Identity identity;
	private String name;
	private String app;

	protected DeviceProfile(Identity identity, String name, String app) {
		this.identity = identity;
		this.name = name;
		this.app = app;
	}

	public Id getId() {
		return getIdentity().getId();
	}

	protected Identity getIdentity() {
		if (identity == null)
			throw new IllegalStateException("identity not set");

		return identity;
	}

	public boolean hasIdentity() {
		return identity != null;
	}

	public void setIdentity(Identity identity) {
		Objects.requireNonNull(identity, "identity");

		if (this.identity != null)
			throw new IllegalStateException("identity already set");

		this.identity = identity;
	}

	public String getName() {
		return name;
	}

	public String getAppName() {
		return app;
	}
}
