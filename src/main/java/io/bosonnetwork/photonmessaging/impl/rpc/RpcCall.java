/*
 * Copyright (c) 2023 -      bosonnetwork.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.bosonnetwork.photonmessaging.impl.rpc;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.core.type.TypeReference;
import io.vertx.core.Future;
import io.vertx.core.Promise;

public class RpcCall<P, R> {
	private static final long DEFAULT_TIMEOUT = 30 * 1000; // 30 seconds
	private final RpcRequest<P> request;
	private RpcResponse<R> response;
	private final long timeout; // in milliseconds
	private TypeReference<R> resultType;
	private final Promise<R> responsePromise;

	private static final AtomicLong nextId = new AtomicLong(new Random().nextLong(8192, 65535));

	public RpcCall(RpcMethod method, P params, long timeout) {
		this.request = new RpcRequest<>(nextId.getAndIncrement(), method, params, null);
		this.timeout = timeout;
		this.responsePromise = Promise.promise();
	}

	public RpcCall(RpcMethod method, P params) {
		this(method, params, DEFAULT_TIMEOUT);
	}

	public RpcCall(RpcMethod method) {
		this(method, null, DEFAULT_TIMEOUT);
	}

	public void resultType(TypeReference<R> resultType) {
		this.resultType = resultType;
	}

	public long getId() {
		return request.getId();
	}

	public RpcMethod getMethod() {
		return request.getMethod();
	}

	public RpcRequest<P> getRequest() {
		return request;
	}

	public  RpcResponse<R> getResponse() {
		return response;
	}

	public long getTimeout() {
		return timeout;
	}

	public void setResponse(GenericRpcResponse response) {
		if (response.getError() != null) {
			// noinspection unchecked
			this.response = (RpcResponse<R>) response;
			RpcError error = response.getError();
			responsePromise.tryFail(rpcErrorToException(error));
		} else {
			try {
				this.response = new RpcResponse<>(response.getId(), response.getResultAs(resultType), null);
				responsePromise.tryComplete(this.response.getResult());
			} catch (InvalidRpcResultException e) {
				responsePromise.tryFail(e);
			}
		}
	}

	public Future<R> getFuture() {
		return timeout <= 0 ? responsePromise.future() :
				responsePromise.future().timeout(timeout, TimeUnit.MILLISECONDS);
	}

	private static RpcException rpcErrorToException(RpcError error) {
		// TODO: map error codes to the corresponding exceptions
		return new RpcException(error.getCode(), error.getMessage());
	}
}