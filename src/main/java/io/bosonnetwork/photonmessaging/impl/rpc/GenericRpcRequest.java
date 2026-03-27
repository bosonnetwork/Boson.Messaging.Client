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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;

import io.bosonnetwork.json.Json;

public class GenericRpcRequest extends RpcRequest<JsonNode> {
	private static final ObjectReader READER = Json.cborMapper().readerFor(GenericRpcRequest.class);

	@JsonCreator
	protected GenericRpcRequest(@JsonProperty(value = "i", required = true) long id,
							 @JsonProperty(value = "m", required = true) RpcMethod method,
							 @JsonProperty("p") JsonNode params,
							 @JsonProperty("c") byte[] cookie) {
		super(id, method, params, cookie);
	}

	public <P> P getParamsAs(Class<P> clazz) throws InvalidRpcParametersException {
		if (params == null)
			return null;

		try {
			return Json.cborMapper().convertValue(params, clazz);
		} catch (IllegalArgumentException e) {
			throw new InvalidRpcParametersException("Invalid RPC parameters", e);
		}
	}

	public <P> P getParamsAs(TypeReference<P> type) throws InvalidRpcParametersException {
		if (params == null)
			return null;

		try {
			return Json.cborMapper().convertValue(params, type);
		} catch (IllegalArgumentException e) {
			throw new InvalidRpcParametersException("Invalid RPC parameters", e);
		}
	}

	public static GenericRpcRequest parse(byte[] bytes) throws MalformedRpcRequestException {
		try {
			return READER.readValue(bytes);
		} catch (IOException e) {
			throw new MalformedRpcRequestException("Malformed RPC request", e);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;

		return o instanceof GenericRpcRequest && super.equals(o);
	}

	@Override
	public int hashCode() {
		return super.hashCode();
	}
}