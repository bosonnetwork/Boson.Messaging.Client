package io.bosonnetwork.messaging.impl;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;

import io.bosonnetwork.Id;
import io.bosonnetwork.messaging.Channel;
import io.bosonnetwork.messaging.Profile;
import io.bosonnetwork.json.Json;

public class Notification<DT> {
	@JsonProperty("e")
	private int event;

	@JsonProperty("o")
	private Id operator;

	// Optional
	@JsonProperty("d")
	@JsonInclude(Include.NON_DEFAULT)
	private DT data;

	public static final class Events {
		public static final int USER_PROFILE = 1;
		public static final int CHANNEL_PROFILE = 2; // owner, permission, name, notice
		public static final int CHANNEL_DELETED = 3;
		public static final int CHANNEL_MEMBER_JOINED = 4;
		public static final int CHANNEL_MEMBER_LEFT = 5;
		public static final int CHANNEL_MEMBERS_ROLE = 6;
		public static final int CHANNEL_MEMBERS_BANNED = 7;
		public static final int CHANNEL_MEMBERS_UNBANNED = 8;
		public static final int CHANNEL_MEMBERS_REMOVED = 9;
	};

	@JsonCreator
	protected Notification() {}

	@JsonCreator
	public Notification(@JsonProperty(value = "e", required = true) int event,
			@JsonProperty(value = "o", required = true) Id operator,
			@JsonProperty(value = "d") DT data) {
		Objects.requireNonNull(operator);

		this.event = event;
		this.operator = operator;
		this.data = data;
	}

	public Notification(int event, Id operator) {
		this(event, operator, null);
	}

	public int getEvent() {
		return event;
	}

	public Id getOperator() {
		return operator;
	}

	public DT getData() {
		return data;
	}

	private <T> Notification<T> map(Class<T> clazz) {
		if (data == null) {
			@SuppressWarnings("unchecked")
			Notification<T> n = (Notification<T>)this;
			return n;
		}

		T mappedData = Json.cborMapper().convertValue(data, clazz);
		return new Notification<>(event, operator, mappedData);
	}

	private <T> Notification<T> map(TypeReference<T> type) {
		if (data == null) {
			@SuppressWarnings("unchecked")
			Notification<T> n = (Notification<T>)this;
			return n;
		}

		T mappedData = Json.cborMapper().convertValue(data, type);
		return new Notification<>(event, operator, mappedData);
	}

	public Notification<Profile> asUserProfile() {
		return map(Profile.class);
	}

	public Notification<Channel> asChannelProfile() {
		return map(Channel.class);
	}

	@SuppressWarnings("unchecked")
	public Notification<Void> asChannelDeleted() {
		return (Notification<Void>)this;
	}

	public Notification<Channel.Member> asChannelMemberJoined() {
		return map(Channel.Member.class);
	}

	@SuppressWarnings("unchecked")
	public Notification<Void> asChannelMemberLeft() {
		return (Notification<Void>)this;
	}


	public record ChannelMembersRoleUpdated(
			@JsonProperty(value = "r", required = true) Channel.Role role,
			@JsonProperty(value = "id", required = true) List<Id> members) {
		@JsonCreator
		public ChannelMembersRoleUpdated {
		}
	}

	public Notification<ChannelMembersRoleUpdated> asChannelMembersRole() {
		return map(ChannelMembersRoleUpdated.class);
	}

	public Notification<List<Id>> asChannelMembersBanned() {
		return map(new TypeReference<List<Id>>() {});
	}

	public Notification<List<Id>> asChannelMembersUnbanned() {
		return map(new TypeReference<List<Id>>() {});
	}

	public Notification<List<Id>> asChannelMembersRemoved() {
		return map(new TypeReference<List<Id>>() {});
	}
}