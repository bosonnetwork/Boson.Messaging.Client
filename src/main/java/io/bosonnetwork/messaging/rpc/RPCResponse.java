package io.bosonnetwork.messaging.rpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RPCResponse<R> {
	@JsonProperty(value = "id", required = true)
	private long id;

	@JsonInclude(Include.NON_EMPTY)
	@JsonProperty("r")
	private R result;

	@JsonInclude(Include.NON_EMPTY)
	@JsonProperty("e")
	private RPCError error;

	public RPCResponse(long id, R result) {
		this.id = id;
		this.result = result;
	}

	public RPCResponse(long id, RPCError error) {
		this.id = id;
		this.error = error;
	}

	public RPCResponse(long id, int errorCode, String errorMessage, String errorData) {
		this.id = id;
		this.error = new RPCError(errorCode, errorMessage, errorData);
	}

	public RPCResponse(long id, int errorCode, String errorMessage) {
		this(id, errorCode, errorMessage, null);
	}

	public long getId() {
		return id;
	}

	public boolean isError() {
		return result == null && error != null;
	}

	public R getResult() {
		return result;
	}

	public RPCError getError() {
		return error;
	}
}
