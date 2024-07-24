package io.bosonnetwork.messaging.rpc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RPCMethod {
	DEVICE_LIST(0x00),
	DEVICE_REVOKE(0x01),

	CONTACT_ADD(0x10),
	CONTACT_UPDATE(0x11),
	CONTACT_REMOVE(0x12),
	CONTACT_LIST(0x13),
	CONTACT_CLEAR(0x14),

	GROUP_CREATE(0x20),
	GROUP_UPDATE(0x21),
	GROUP_DELETE(0x22),
	GROUP_ROLE(0x23),
	GROUP_BAN(0x24),
	GROUP_UNBAN(0x25),
	GROUP_REMOVE(0x26),
	GROUP_JOIN(0x27),
	GROUP_LEAVE(0x28);

	private final int value;

	final static RPCMethod[] METHODS = {
			DEVICE_LIST,
			DEVICE_REVOKE,
			// 14 nulls
			null, null, null, null, null, null, null, null, null, null, null, null, null, null,
			CONTACT_ADD,
			CONTACT_UPDATE,
			CONTACT_REMOVE,
			CONTACT_LIST,
			CONTACT_CLEAR,
			// 11 nulls
			null, null, null, null, null, null, null, null, null, null, null,
			GROUP_CREATE,
			GROUP_UPDATE,
			GROUP_DELETE,
			GROUP_ROLE,
			GROUP_BAN,
			GROUP_UNBAN,
			GROUP_REMOVE,
			GROUP_JOIN,
			GROUP_LEAVE
	};

	private RPCMethod(int value) {
		this.value = value;
	}

	@JsonValue
	public int value() {
		return value;
	}

	@JsonCreator
	public static RPCMethod valueOf(int value) {
		if (value < 0 || value >= METHODS.length)
			throw new IllegalArgumentException("Invalid method: " + value);

		RPCMethod type = METHODS[value];
		if (type != null)
			return type;
		else
			throw new IllegalArgumentException("Invalid method: " + value);
	}

	public boolean isNodeContext() {
		return this.value <= GROUP_CREATE.value;
	}

	public boolean isGroupContext() {
		return this.value >= GROUP_DELETE.value;
	}
}
