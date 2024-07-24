package io.bosonnetwork.messaging.rpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bosonnetwork.utils.ThreadLocals;

public class RPCRequest<P> {
	@JsonProperty(value = "id", required = true)
	private long id;
	@JsonProperty(value = "m", required = true)
	private RPCMethod method;
	@JsonInclude(Include.NON_EMPTY)
	@JsonProperty("p")
	private P params;

	public RPCRequest(long id, RPCMethod method, P params) {
		this.id = id;
		this.method = method;
		this.params = params;
	}

	public long getId() {
		return id;
	}

	public RPCMethod getMethod() {
		return method;
	}

	public P getParameters() {
		return params;
	}

	public <T> T getParameters(Class<T> clazz) {
		return ThreadLocals.CBORMapper().convertValue(params, clazz);
	}
}
