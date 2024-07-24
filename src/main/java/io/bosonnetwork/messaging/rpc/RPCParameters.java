package io.bosonnetwork.messaging.rpc;

import java.nio.ByteBuffer;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.messaging.Contact;
import io.bosonnetwork.messaging.Group;

public class RPCParameters {
	public static class DeviceId {
		@JsonProperty(value = "d", required = true)
		private Id deviceId;

		public DeviceId(Id deviceId) {
			this.deviceId = deviceId;
		}

		public Id getDeviceId() {
			return deviceId;
		}
	}

	public static class UserId {
		@JsonProperty(value = "u", required = true)
		private Id userId;

		public UserId(Id userId) {
			this.userId = userId;
		}

		public Id getUserId() {
			return userId;
		}
	}

	public static class ContactList {
		@JsonProperty(value = "cs", required = true)
		private List<Contact> contacts;

		public ContactList(List<Contact> contacts) {
			this.contacts = contacts;
		}

		public List<Contact> getContacts() {
			return contacts;
		}
	}

	public static class ContactIdList {
		@JsonProperty(value = "ids", required = true)
		private List<Id> contactIds;

		public ContactIdList(List<Id> contactIds) {
			this.contactIds = contactIds;
		}

		public List<Id> getContactIds() {
			return contactIds;
		}
	}

	public static class GroupCreation {
		@JsonProperty(value = "k", required = true)
		private byte[] memberPublicKey;

		@JsonProperty(value = "p", required = true)
		private Group.Permission permission;

		@JsonProperty("n")
		@JsonInclude(Include.NON_EMPTY)
		private String name;

		@JsonProperty("t")
		@JsonInclude(Include.NON_EMPTY)
		private String notice;

		public GroupCreation(Signature.PublicKey memberPublicKey, Group.Permission permission, String name, String notice) {
			this.memberPublicKey = memberPublicKey.bytes();
			this.permission = permission;
			this.name = name;
			this.notice = notice;
		}

		public GroupCreation(Signature.PublicKey memberPublicKey, Group.Permission permission) {
			this(memberPublicKey, permission, null, null);
		}

		public Signature.PublicKey getMemberPublicKey() {
			return Signature.PublicKey.fromBytes(memberPublicKey);
		}

		public Group.Permission getPermission() {
			return permission;
		}

		public String getName() {
			return name;
		}

		public String getNotice() {
			return notice;
		}
	}

	public static class GroupInfo {
		@JsonProperty("n")
		@JsonInclude(Include.NON_EMPTY)
		private String name;

		@JsonProperty("t")
		@JsonInclude(Include.NON_EMPTY)
		private String notice;

		public GroupInfo(String name, String notice) {
			this.name = name;
			this.notice = notice;
		}

		public String getName() {
			return name;
		}

		public String getNotice() {
			return notice;
		}
	}

	public static class GroupMemberRole {
		@JsonProperty(value = "i", required = true)
		private Id member;

		@JsonProperty(value = "r", required = true)
		private Group.Role role;

		public GroupMemberRole(Id member, Group.Role role) {
			this.member = member;
			this.role = role;
		}

		public Id getMember() {
			return member;
		}

		public Group.Role getRole() {
			return role;
		}
	}

	public static class GroupMemberList {
		@JsonProperty(value = "ids", required = true)
		private List<Id> members;

		public GroupMemberList(List<Id> members) {
			this.members = members;
		}

		public List<Id> getMembers() {
			return members;
		}
	}

	public static class Ticket {
		@JsonProperty(value = "i", required = true)
		private Id inviter;

		@JsonProperty(value = "o")
		@JsonInclude(Include.NON_EMPTY)
		private boolean openToAnyone;

		@JsonProperty(value = "e")
		@JsonInclude(Include.NON_EMPTY)
		private long expire;

		@JsonProperty(value = "s", required = true)
		private byte[] sig;

		public Ticket(Id inviter, boolean openToAnyone, long expire, byte[] sig) {
			this.inviter = inviter;
			this.openToAnyone = openToAnyone;
			this.expire = expire;
			this.sig = sig;
		}

		public Id getInviter() {
			return inviter;
		}

		public boolean isExpired() {
			return expire < System.currentTimeMillis();
		}

		public boolean isValid(Id invitee) {
			int size = Id.BYTES + Byte.BYTES + (openToAnyone ? 0 : Id.BYTES) + Long.BYTES;
			byte[] data = new byte[size];

			ByteBuffer buf = ByteBuffer.wrap(data);
			buf.put(inviter.bytes());
			buf.put((byte)(openToAnyone ? 1 : 0));
			if (!openToAnyone)
				buf.put(invitee.bytes());
			buf.putLong(expire);

			Signature.PublicKey pk = Signature.PublicKey.fromBytes(inviter.bytes());
			return pk.verify(data, sig);
		}
	}

	public static class GroupJoin {
		@JsonProperty("t")
		@JsonInclude(Include.NON_NULL)
		private Ticket ticket;

		public GroupJoin(Ticket ticket) {
			this.ticket = ticket;
		}

		public GroupJoin() {
			this(null);
		}

		public Ticket getTicket() {
			return ticket;
		}
	}
}
