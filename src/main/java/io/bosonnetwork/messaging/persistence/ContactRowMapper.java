package io.bosonnetwork.messaging.persistence;

import io.bosonnetwork.photonmessaging.impl.AbstractContact;

public class ContactRowMapper extends AbstractContactRowMapper<AbstractContact> {
	public ContactRowMapper() {
		super(AbstractContact.class);
	}
}