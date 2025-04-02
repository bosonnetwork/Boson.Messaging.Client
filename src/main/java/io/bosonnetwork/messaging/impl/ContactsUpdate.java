package io.bosonnetwork.messaging.impl;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bosonnetwork.messaging.Contact;

public class ContactsUpdate {
	@JsonProperty("v")
	@JsonInclude(Include.NON_EMPTY)
	private String versionId;

	@JsonProperty("c")
	@JsonInclude(Include.NON_EMPTY)
	private List<Contact> contacts;

	@JsonCreator
	public ContactsUpdate(@JsonProperty(value = "v") String versionId,
			@JsonProperty(value = "c") List<Contact> contacts) {
		this.versionId = versionId;
		this.contacts = contacts == null ? Collections.emptyList() : contacts;
	}

	public String getVersionId() {
		return versionId;
	}

	public List<Contact> getContacts() {
		return contacts;
	}
}