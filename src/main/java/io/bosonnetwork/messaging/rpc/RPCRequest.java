package io.bosonnetwork.messaging.rpc;

import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;

import io.bosonnetwork.messaging.RPCException;
import io.bosonnetwork.json.Json;
import io.vertx.core.Future;
import io.vertx.core.Promise;

public class RPCRequest<P, R> {
	@JsonProperty(value = "i", required = true)
	private long id;

	@JsonProperty(value = "m", required = true)
	private RPCMethod method;

	@JsonProperty("p")
	@JsonInclude(Include.NON_EMPTY)
	private P params;

	// This is the client side cookie for data sync between multiple device.
	// Because all messages go through the super node, so the sensitive data should
	// be encrypted(by user's key pair) can only can be decrypted by the user self-only.
	// The server should ignore this field.
	@JsonProperty("c")
	@JsonInclude(Include.NON_EMPTY)
	private byte[] cookie;

	private transient Promise<R> promise;
	private transient RPCResponse<R> response;

	// for JSON creator, the promise object will be null
	@JsonCreator
	protected RPCRequest() {
	}

	// New RPC request, promise object will be created automatically
	public RPCRequest(long id, RPCMethod method, P params) {
		this.id = id;
		this.method = method;
		this.params = params;
		this.promise = Promise.promise();
	}

	private RPCRequest(RPCRequest<?, ?> ref, P params, Promise<R> promise) {
		this.id = ref.id;
		this.method = ref.method;
		this.params = params;
		this.promise = promise;
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
		return params == null ? null : Json.cborMapper().convertValue(params, clazz);
	}

	public <T> T getParameters(TypeReference<T> type) {
		return params == null ? null : Json.cborMapper().convertValue(params, type);
	}

	public void setCookie(byte[] cookie) {
		this.cookie = cookie;
	}

	public byte[] getCookie() {
		return cookie;
	}

	public <T> void setCookie(T cookie, Function<T, byte[]> transform) {
		this.cookie = transform.apply(cookie);
	}

	public <T> T getCookie(Function<byte[], T> transform) {
		return transform.apply(cookie);
	}

	public boolean isInitiator() {
		return promise != null;
	}

	public Future<R> getFuture() {
		return promise != null ? promise.future() : null;
	}

	public void complete(RPCResponse<R> response) {
		this.response = response;
		if (promise != null) {
			if (response.succeeded())
				promise.tryComplete(response.getResult());
			else
				promise.tryFail(new RPCException(response.getError()));
		}
	}

	public void failed(Throwable t) {
		if (promise != null)
			promise.tryFail(t);
	}

	public RPCResponse<R> getResponse() {
		return response;
	}

	// type casting helpers
	public <PT, RT> RPCRequest<PT, RT> cast() {
		@SuppressWarnings("unchecked")
		RPCRequest<PT, RT> typed = (RPCRequest<PT, RT>)this;
		return typed;
	}

	public <PT, RT> RPCRequest<PT, RT> map(Class<PT> paramsType) {
		if (params == null)
			return cast();

		PT p =  Json.cborMapper().convertValue(params, paramsType);
		@SuppressWarnings("unchecked")
		RPCRequest<PT, RT> r = new RPCRequest<>(this, p, (Promise<RT>)promise);

		if (response != null)
			r.response = response.cast();

		return r;
	}

	public <PT, RT> RPCRequest<PT, RT> map(TypeReference<PT> paramsType) {
		if (params == null)
			return cast();

		PT p =  Json.cborMapper().convertValue(params, paramsType);
		@SuppressWarnings("unchecked")
		RPCRequest<PT, RT> r = new RPCRequest<>(this, p, (Promise<RT>)promise);

		if (response != null)
			r.response = response.cast();

		return r;
	}
}