package io.bosonnetwork.messaging.rpc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RPCMethod {
	USER_PROFILE(0x01),

	DEVICE_LIST(0x11),
	DEVICE_REVOKE(0x12),

	CONTACT_PUSH(0x21),
	CONTACT_CLEAR(0x22),

	CHANNEL_CREATE(0x31),
	CHANNEL_DELETE(0x32),
	CHANNEL_JOIN(0x33),
	CHANNEL_LEAVE(0x34),
	CHANNEL_INFO(0x35),
	CHANNEL_MEMBERS(0x36),
	CHANNEL_OWNER(0x37),
	CHANNEL_PERMISSION(0x38),
	CHANNEL_NAME(0x39),
	CHANNEL_NOTICE(0x3A),
	CHANNEL_ROLE(0x3B),
	CHANNEL_BAN(0x3C),
	CHANNEL_UNBAN(0x3D),
	CHANNEL_REMOVE(0x3E);

	private final int value;

	private RPCMethod(int value) {
		this.value = value;
	}

	@JsonValue
	public int value() {
		return value;
	}

	@JsonCreator
	public static RPCMethod valueOf(int value) {
		return switch (value) {
		case 0x01 -> USER_PROFILE;
		case 0x11 -> DEVICE_LIST;
		case 0x12 -> DEVICE_REVOKE;
		case 0x21 -> CONTACT_PUSH;
		case 0x22 -> CONTACT_CLEAR;
		case 0x31 -> CHANNEL_CREATE;
		case 0x32 -> CHANNEL_DELETE;
		case 0x33 -> CHANNEL_JOIN;
		case 0x34 -> CHANNEL_LEAVE;
		case 0x35 -> CHANNEL_INFO;
		case 0x36 -> CHANNEL_MEMBERS;
		case 0x37 -> CHANNEL_OWNER;
		case 0x38 -> CHANNEL_PERMISSION;
		case 0x39 -> CHANNEL_NAME;
		case 0x3A -> CHANNEL_NOTICE;
		case 0x3B -> CHANNEL_ROLE;
		case 0x3C -> CHANNEL_BAN;
		case 0x3D -> CHANNEL_UNBAN;
		case 0x3E -> CHANNEL_REMOVE;
		default -> throw new IllegalArgumentException("Invalid method: " + value);
		};
	}
}
