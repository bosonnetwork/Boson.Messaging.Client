package io.bosonnetwork.messaging.rpc;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RPCError {
	@JsonProperty(value = "c", required = true)
	private int code;
	@JsonProperty(value = "m", required = true)
	private String message;
	@JsonProperty(value = "d")
	private String data;

	public static final RPCError SuperNodeError = new RPCError(-1, "Super node internal error");
	public static final RPCError InvalidParameters = new RPCError(-2, "Invalid parameters");
	public static final RPCError InvalidMethod = new RPCError(-3, "Invalid method");
	public static final RPCError Forbidden = new RPCError(-4, "Forbidden");
	public static final RPCError Timeout = new RPCError(-5, "Timeout");
	public static final RPCError NotUpToDate = new RPCError(-6, "Not up to date");
	public static final RPCError AlreadyExists = new RPCError(-7, "Already exists");


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
