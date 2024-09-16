package io.bosonnetwork.messaging.impl;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bosonnetwork.messaging.Contact;

public class ContactSyncResult {
	@JsonProperty("s")
	@JsonInclude(Include.NON_NULL)
	ContactSequence sequence;

	@JsonProperty("c")
	@JsonInclude(Include.NON_EMPTY)
	List<Contact> contacts;

	private ContactSyncResult(ContactSequence lastSequence, List<Contact> contacts) {
		this.sequence = lastSequence;
		this.contacts = contacts == null ? Collections.emptyList() : contacts;
	}

	public ContactSequence getLastSequence() {
		return sequence;
	}

	public List<Contact> getContacts() {
		return contacts;
	}
}
