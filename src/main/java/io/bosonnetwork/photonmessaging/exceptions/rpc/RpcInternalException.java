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

package io.bosonnetwork.photonmessaging.exceptions.rpc;

import io.bosonnetwork.photonmessaging.impl.rpc.RpcErrorCode;

/**
 * Represents an internal exception thrown during remote procedure call (RPC) operations.
 * This exception is used to indicate internal errors that occur within the RPC system,
 * such as unexpected conditions or failures that prevent the system from processing
 * the RPC request or response correctly.
 */
public class RpcInternalException extends RpcException {
	private static final long serialVersionUID = 560188105191393593L;

	/**
	 * Constructs a new instance of RpcInternalException with the specified detail message.
	 * This exception is thrown to indicate internal errors occurring within the RPC system,
	 * such as unexpected conditions or failures that prevent processing of the RPC request
	 * or response.
	 *
	 * @param message the detail message explaining the reason for the internal error
	 */
	public RpcInternalException(String message) {
		super(RpcErrorCode.RPC_INTERNAL_ERROR, message);
	}

	/**
	 * Constructs a new {@code RpcInternalException} with the specified detail message and cause.
	 * This exception indicates an internal error that occurs during remote procedure call (RPC)
	 * operations, typically as a result of unexpected conditions or failures within the RPC system.
	 *
	 * @param message the detail message explaining the reason for the internal error
	 * @param cause the underlying cause of the exception, which may be {@code null} to indicate
	 *              that the cause is nonexistent or unknown
	 */
	public RpcInternalException(String message, Throwable cause) {
		super(RpcErrorCode.RPC_INTERNAL_ERROR, message, cause);
	}
}