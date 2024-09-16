package io.bosonnetwork.messaging.rpc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;

import io.bosonnetwork.utils.Json;

public class RPCResponse<R> {
	@JsonProperty(value = "i", required = true)
	private long id;

	@JsonInclude(Include.NON_EMPTY)
	@JsonProperty("r")
	private R result;

	@JsonInclude(Include.NON_NULL)
	@JsonProperty("e")
	private RPCError error;

	// for JSON creator
	protected RPCResponse() {
	}

	public RPCResponse(long id, R result) {
		this(id, result, null);
	}

	public RPCResponse(long id, RPCError error) {
		this(id, null, error);
	}

	public RPCResponse(long id, int errorCode, String errorMessage, String errorData) {
		this(id, null, new RPCError(errorCode, errorMessage, errorData));
	}

	public RPCResponse(long id, int errorCode, String errorMessage) {
		this(id, null, new RPCError(errorCode, errorMessage));
	}

	@JsonCreator
	private RPCResponse(long id, R result, RPCError error) {
		this.id = id;
		this.result = result;
		this.error = error;
	}

	public long getId() {
		return id;
	}

	public boolean succeeded() {
		return error == null;
	}

	public boolean failed() {
		return error != null;
	}

	public R getResult() {
		return result;
	}

	public RPCError getError() {
		return error;
	}

	// type casting helpers
	public <T> RPCResponse<T> cast() {
		@SuppressWarnings("unchecked")
		RPCResponse<T> typed = (RPCResponse<T>)this;
		return typed;
	}

	public <T> RPCResponse<T> map() {
		if (result == null)
			return cast();

		T r =  Json.cborMapper().convertValue(result, new TypeReference<T>() {});
		return new RPCResponse<>(this.id, r, this.error);
	}

	public RPCResponse<R> revised(R result) {
		if (result == this.result)
			return this;

		return new RPCResponse<>(this.id, result, this.error);
	}
}
