package io.bosonnetwork.messaging.persistence;

import io.bosonnetwork.messaging.Contact;

public class ContactRowMapper extends AbstractContactRowMapper<Contact> {
	public ContactRowMapper() {
		super(Contact.class);
	}
}