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
import java.util.Arrays;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bosonnetwork.json.Json;

public class RpcRequest<P> {
	@JsonProperty(value = "i", required = true)
	protected final long id;

	@JsonProperty(value = "m", required = true)
	protected final RpcMethod method;

	@JsonProperty("p")
	@JsonInclude(JsonInclude.Include.NON_NULL)
	protected final P params;

	@JsonProperty("c")
	@JsonInclude(JsonInclude.Include.NON_NULL)
	protected final byte[] cookie;

	public RpcRequest(long id, RpcMethod method, P params, byte[] cookie) {
		this.id = id;
		this.method = method;
		this.params = params;
		this.cookie = cookie;
	}

	public RpcRequest(long id, RpcMethod method, P params) {
		this(id, method, params, null);
	}

	public RpcRequest(long id, RpcMethod method) {
		this(id, method, null, null);
	}

	public long getId() {
		return id;
	}

	public RpcMethod getMethod() {
		return method;
	}

	public P getParams() {
		return params;
	}

	/**
	 * Returns the request cookie.
	 * <p>
	 * The returned array is the internal reference and is not defensively copied.
	 * This is intentional for internal-use performance reasons, so callers must not
	 * modify the returned array unless they explicitly own that contract.
	 *
	 * @return the cookie bytes, or {@code null} if no cookie is present
	 */
	public byte[] getCookie() {
		return cookie;
	}

	public byte[] serialize() {
		try {
			return Json.cborMapper().writeValueAsBytes(this);
		} catch (IOException e) {
			throw new IllegalStateException("INTERNAL ERROR: RpcRequest serialization", e);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;

		if (o instanceof RpcRequest<?> that)
			return id == that.id &&
					method == that.method &&
					Objects.equals(params, that.params) &&
					Arrays.equals(cookie, that.cookie);

		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, method, params, Arrays.hashCode(cookie));
	}
}