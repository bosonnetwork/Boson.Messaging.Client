package io.bosonnetwork.messaging.impl;

import io.bosonnetwork.Id;
import io.bosonnetwork.messaging.Contact;
import io.bosonnetwork.messaging.Profile;

public class UnknownContact extends Contact {
	private String notice;

	public UnknownContact(Id id) {
		super(id, null, true);
	}

	@Override
	public int getType() {
		return Types.UNKNOWN;
	}

	@Override
	public void update(Profile profile) {
		if (profile.getNotice() != null) {
			this.notice = profile.getNotice();
		}

		super.update(profile);
	}
}
