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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bosonnetwork.photonmessaging.exceptions.rpc.RpcException;

public class RpcError {
	private static final boolean INCLUDE_STACKTRACE_IN_ERROR = false;

	@JsonProperty(value = "c", required = true)
	private final int code;
	@JsonProperty(value = "m", required = true)
	private final String message;
	@JsonProperty(value = "d")
	private final String data;

	public static final RpcError RpcInternalError = new RpcError(RpcErrorCode.RPC_INTERNAL_ERROR, "Super node internal error");
	public static final RpcError MalformedRequest = new RpcError(RpcErrorCode.MALFORMED_REQUEST, "Malformed RPC request");
	public static final RpcError MalformedResponse = new RpcError(RpcErrorCode.MALFORMED_RESPONSE, "Malformed RPC response");
	public static final RpcError InvalidMethod = new RpcError(RpcErrorCode.INVALID_METHOD, "Invalid RPC method");
	public static final RpcError UnimplementedMethod = new RpcError(RpcErrorCode.UNIMPLEMENTED_METHOD, "Unimplemented RPC method");
	public static final RpcError InvalidParameters = new RpcError(RpcErrorCode.INVALID_PARAMS, "Invalid RPC parameters");
	public static final RpcError InvalidResult = new RpcError(RpcErrorCode.INVALID_RESULT, "Invalid RPC result");
	public static final RpcError Forbidden = new RpcError(RpcErrorCode.FORBIDDEN, "Forbidden");
	public static final RpcError Timeout = new RpcError(RpcErrorCode.TIMEOUT, "RPC call timed out");

	public static final RpcError SessionNotExists = new RpcError(RpcErrorCode.SESSION_NOT_EXISTS, "Session not exists");
	public static final RpcError RevokeCurrentSession = new RpcError(RpcErrorCode.REVOKE_CURRENT_SESSION, "Cannot revoke the current session");

	public static final RpcError AlreadyJoinedChannel = new RpcError(RpcErrorCode.ALREADY_JOINED_CHANNEL, "Already joined channel");
	public static final RpcError ForbiddenNonChannelMember = new RpcError(RpcErrorCode.FORBIDDEN_NON_CHANNEL_MEMBER, "Non-channel member is forbidden");
	public static final RpcError ForbiddenBannedChannelMember = new RpcError(RpcErrorCode.FORBIDDEN_BANNED_CHANNEL_MEMBER, "Banned channel member is forbidden");

	@JsonCreator
	public RpcError(@JsonProperty(value = "c", required = true) int code,
					@JsonProperty(value = "m", required = true) String message,
					@JsonProperty(value = "d") String data) {
		this.code = code;
		this.message = message;
		this.data = data;
	}

	public RpcError(int code, String message) {
		this(code, message, null);
	}

	public RpcError(Throwable cause) {
		this.code = cause instanceof RpcException re ? re.getCode() : RpcErrorCode.RPC_INTERNAL_ERROR;
		this.message = cause.getMessage();
		if (INCLUDE_STACKTRACE_IN_ERROR) {
			try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
				cause.printStackTrace(pw);
				this.data = sw.toString();
			} catch (IOException ignore) {
			}
		} else {
			this.data = null;
		}
	}

	public int getCode() {
		return code;
	}

	public String getMessage() {
		return message;
	}

	public String getData() {
		return data;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;

		if (o instanceof RpcError that)
			return code == that.code && 
					Objects.equals(message, that.message) && 
					Objects.equals(data, that.data);

		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(code, message, data);
	}
}