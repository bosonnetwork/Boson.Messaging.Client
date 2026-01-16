package io.bosonnetwork.messaging.rpc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;

import io.bosonnetwork.json.Json;

public class RPCResponse<R> {
	@JsonProperty(value = "i", required = true)
	private long id;

	@JsonInclude(Include.NON_EMPTY)
	@JsonProperty("r")
	private R result;

	@JsonInclude(Include.NON_NULL)
	@JsonProperty("e")
	private RPCError error;

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
	private RPCResponse(@JsonProperty(value = "i", required = true) long id,
			@JsonProperty("r") R result,
			@JsonProperty("e") RPCError error) {
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

	public <T> RPCResponse<T> map(Class<T> resultType) {
		if (result == null)
			return cast();

		T r =  Json.cborMapper().convertValue(result, resultType);
		return new RPCResponse<>(this.id, r, this.error);
	}

	public <T> RPCResponse<T> map(TypeReference<T> resultType) {
		if (result == null)
			return cast();

		T r =  Json.cborMapper().convertValue(result, resultType);
		return new RPCResponse<>(this.id, r, this.error);
	}

	public <RR> RPCResponse<RR> revised(RR result) {
		if (result == this.result) {
			@SuppressWarnings("unchecked")
			RPCResponse<RR> response = (RPCResponse<RR>)this;
			return response;
		}

		return new RPCResponse<>(this.id, result, this.error);
	}
}