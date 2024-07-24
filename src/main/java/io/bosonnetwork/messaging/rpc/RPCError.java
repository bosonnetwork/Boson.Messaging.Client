package io.bosonnetwork.messaging.rpc;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RPCError {
	@JsonProperty(value = "c", required = true)
	private int code;
	@JsonProperty(value = "m", required = true)
	private String message;
	@JsonProperty(value = "d")
	private String data;

	public static final RPCError InvalidParameters = new RPCError(-1, "Invalid parameters");
	public static final RPCError Forbidden = new RPCError(-2, "Forbidden");
	public static final RPCError Timeout = new RPCError(-3, "Timeout");
	public static final RPCError AlreadyExists = new RPCError(-4, "Already exists");
	public static final RPCError SuperNodeError = new RPCError(-5, "Super node error");

	public RPCError(int code, String message, String data) {
		this.code = code;
		this.message = message;
		this.data = data;
	}

	public RPCError(int code, String message) {
		this(code, message, null);
	}

	public int getCode() {
		return code;
	}

	public String getMessage() {
		return message;
	}

	public String getData() {
		return data;
	}
}
