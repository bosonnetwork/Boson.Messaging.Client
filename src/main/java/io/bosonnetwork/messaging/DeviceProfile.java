package io.bosonnetwork.messaging;

import java.util.Objects;

import io.bosonnetwork.Id;
import io.bosonnetwork.Identity;
import io.bosonnetwork.Node;

public abstract class DeviceProfile {

	private String name;
	private String app;

	protected DeviceProfile(String name, String app) {
		this.name = name;
		this.app = app;
	}

	public abstract Id getId();

	protected abstract Identity getIdentity();

	protected abstract void updateIdentity(Identity identity);

	public void setNode(Node node) {
		Objects.requireNonNull(node, "node");
		updateIdentity(node);
	}

	public String getName() {
		return name;
	}

	public String getAppName() {
		return app;
	}
}
