package io.bosonnetwork.messaging;

import io.bosonnetwork.Id;
import io.bosonnetwork.Identity;

public interface ChannelIdentity extends Identity {
	public Id getMemberPublicKey();
}
