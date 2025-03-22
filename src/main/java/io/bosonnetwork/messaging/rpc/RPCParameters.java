package io.bosonnetwork.messaging.rpc;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bosonnetwork.Id;
import io.bosonnetwork.messaging.Channel;
import io.bosonnetwork.messaging.Contact;


public class RPCParameters {
	public static class UserProfile {
		@JsonProperty("n")
		private String name;	// null to clear the name

		public UserProfile(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

	public static class ContactPut {
		@JsonProperty("s")
		@JsonInclude(Include.NON_EMPTY)
		private String sequenceId;

		@JsonProperty("c")
		@JsonInclude(Include.NON_EMPTY)
		private List<Contact> contacts;

		public ContactPut(String sequenceId, List<Contact> contacts) {
			this.sequenceId = sequenceId;
			this.contacts = contacts == null ? Collections.emptyList() : contacts;
		}

		public String getSequenceId() {
			return sequenceId;
		}

		public List<Contact> getContacts() {
			return contacts;
		}
	}

	public static class ContactRemove {
		@JsonProperty("s")
		@JsonInclude(Include.NON_EMPTY)
		private String sequenceId;

		@JsonProperty("c")
		@JsonInclude(Include.NON_EMPTY)
		private List<Id> contacts;

		public ContactRemove(String sequenceId, List<Id> contacts) {
			this.sequenceId = sequenceId;
			this.contacts = contacts == null ? Collections.emptyList() : contacts;
		}

		public String getSequenceId() {
			return sequenceId;
		}

		public List<Id> getContacts() {
			return contacts;
		}
	}

	public static class ChannelCreate {
		@JsonProperty(value = "sid", required = true)
		private Id sessionId;

		@JsonProperty(value = "p", required = true)
		private Channel.Permission permission;

		@JsonProperty("n")
		@JsonInclude(Include.NON_EMPTY)
		private String name;

		@JsonProperty("nt")
		@JsonInclude(Include.NON_EMPTY)
		private String notice;

		public ChannelCreate(Id sessionId, Channel.Permission permission, String name, String notice) {
			this.sessionId = sessionId;
			this.permission = permission;
			this.name = name;
			this.notice = notice;
		}

		public Id getSessionId() {
			return sessionId;
		}

		public Channel.Permission getPermission() {
			return permission;
		}

		public String getName() {
			return name;
		}

		public String getNotice() {
			return notice;
		}
	}

	public static class ChannelMemberRole {
		@JsonProperty(value = "id", required = true)
		private List<Id> members;

		@JsonProperty(value = "r", required = true)
		private Channel.Role role;

		public ChannelMemberRole(List<Id> members, Channel.Role role) {
			this.members = members;
			this.role = role;
		}

		public List<Id> getMembers() {
			return members;
		}

		public Channel.Role getRole() {
			return role;
		}
	}
}
