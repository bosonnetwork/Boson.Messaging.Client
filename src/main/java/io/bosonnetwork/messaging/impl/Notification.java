package io.bosonnetwork.messaging.impl;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import io.bosonnetwork.Id;
import io.bosonnetwork.messaging.Channel;
import io.bosonnetwork.messaging.Profile;
import io.bosonnetwork.utils.Json;

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
		public static final int CHANNEL_DELETED = 2;
		public static final int CHANNEL_JOINED = 3;
		public static final int CHANNEL_LEFT = 4;
		public static final int CHANNEL_PROFILE = 5; // owner, permission, name, notice
		public static final int CHANNEL_ROLE = 6;
		public static final int CHANNEL_BANNED = 7;
		public static final int CHANNEL_UNBANNED = 8;
		public static final int CHANNEL_REMOVED = 9;
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

	public <T> Notification<T> map(Class<T> clazz) {
		if (data == null) {
			@SuppressWarnings("unchecked")
			Notification<T> n = (Notification<T>)this;
			return n;
		}

		T mappedData = Json.cborMapper().convertValue(data, clazz);
		return new Notification<>(event, operator, mappedData);
	}

	public <T> Notification<T> map(TypeReference<T> type) {
		if (data == null) {
			@SuppressWarnings("unchecked")
			Notification<T> n = (Notification<T>)this;
			return n;
		}

		T mappedData = Json.cborMapper().convertValue(data, type);
		return new Notification<>(event, operator, mappedData);
	}

	public static class Preparsed extends Notification<JsonNode> {}

	public static class UserProfileUpdated extends Notification<Profile> {}

	public static class ChannelDeleted extends Notification<Void> {}
	public static class ChannelJoined extends Notification<Channel.Member> {}
	public static class ChannelLeft extends Notification<Void> {}
	public static class ChannelProfileUpdated extends Notification<Channel> {}

	public static class ChannelRole extends Notification<ChannelRole.Data> {
		public static class Data {
			@JsonProperty("r")
			private Channel.Role role;
			@JsonProperty("id")
			private List<Id> memberIds;

			public Channel.Role getRole() {
				return role;
			}

			public List<Id> getMemberIds() {
				return Collections.unmodifiableList(memberIds);
			}
		}
	}

	public static class ChannelBanned extends Notification<List<Id>> {}
	public static class ChannelUnbanned extends Notification<List<Id>> {}
	public static class ChannelRemoved extends Notification<List<Id>> {}
}
