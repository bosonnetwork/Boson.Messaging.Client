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

import java.io.IOException;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bosonnetwork.json.Json;

/**
 * RPC response envelope.
 * <p>
 * Protocol rules:
 * <ul>
 *   <li>{@code error == null} means the call succeeded</li>
 *   <li>{@code error != null} means the call failed</li>
 *   <li>{@code result} may be {@code null} for successful calls that do not return a value</li>
 *   <li>{@code result} and {@code error} must not both be non-null</li>
 * </ul>
 */
public class RpcResponse<R> {
	@JsonProperty(value = "i", required = true)
	protected final long id;

	@JsonProperty("r")
	@JsonInclude(JsonInclude.Include.NON_NULL)
	protected final R result;

	@JsonProperty("e")
	@JsonInclude(JsonInclude.Include.NON_NULL)
	protected final RpcError error;

	/**
	 * Creates an RPC response.
	 *
	 * @throws IllegalArgumentException if both {@code result} and {@code error} are non-null
	 */
	protected RpcResponse(long id, R result, RpcError error)  {
		if (result != null && error != null)
			throw new IllegalArgumentException("Cannot have both result and error");

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

	public RpcError getError() {
		return error;
	}

	public byte[] serialize() {
		try {
			return Json.cborMapper().writeValueAsBytes(this);
		} catch (IOException e) {
			throw new IllegalStateException("INTERNAL ERROR: RpcResponse serialization", e);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;

		if (o instanceof RpcResponse<?> that)
			return id == that.id &&
					Objects.equals(result, that.result) &&
					Objects.equals(error, that.error);

		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, result, error);
	}
}