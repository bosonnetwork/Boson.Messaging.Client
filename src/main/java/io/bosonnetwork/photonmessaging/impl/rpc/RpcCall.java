package io.bosonnetwork.photonmessaging.impl.rpc;

import java.util.concurrent.TimeUnit;

import io.vertx.core.Future;
import io.vertx.core.Promise;

public class RpcCall<P, R> {
	private static final long DEFAULT_TIMEOUT = 30 * 1000; // 30 seconds

	private final Promise<R> promise;
	private final RpcRequest<P> request;
	private final long timeout; // in milliseconds

	public RpcCall(RpcRequest<P> request, long timeout) {
		this.promise = Promise.promise();
		this.request = request;
		this.timeout = timeout;
	}

	public RpcCall(RpcRequest<P> request) {
		this(request, DEFAULT_TIMEOUT);
	}

	public long getId() {
		return request.getId();
	}

	public RpcRequest<P> getRequest() {
		return request;
	}

	public long getTimeout() {
		return timeout;
	}

	public void setResponse(RpcResponse<R> response) {
		if (response.getError() != null) {
			RpcError error = response.getError();
			promise.tryFail(rpcErrorToException(error));
		} else {
			promise.tryComplete(response.getResult());
		}
	}

	public Future<R> getFuture() {
		return timeout > 0 ? promise.future().timeout(timeout, TimeUnit.MILLISECONDS) : promise.future();
	}

	private static RpcException rpcErrorToException(RpcError error) {
		// TODO: map error codes to the corresponding exceptions
		return new RpcException(error.getCode(), error.getMessage());
	}
}