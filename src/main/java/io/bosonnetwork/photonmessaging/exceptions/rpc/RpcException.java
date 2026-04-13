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

import io.bosonnetwork.photonmessaging.exceptions.MessagingException;

/**
 * Represents an exception that is thrown during remote procedure call (RPC) operations.
 * This exception serves as a base class for other specific RPC-related exception types.
 * It provides an error code and a detailed message to indicate the nature of the RPC failure.
 */
public class RpcException extends MessagingException {
	private static final long serialVersionUID = -1363876638838365212L;

	private final int code;

	/**
	 * Constructs a new RpcException with the specified error code and detail message.
	 * This exception is typically used to indicate an issue encountered during a
	 * remote procedure call (RPC) operation.
	 *
	 * @param code the error code representing the specific type of RPC error
	 * @param message the detail message explaining the reason for the exception
	 */
	public RpcException(int code, String message) {
		super(message);
		this.code = code;
	}

	/**
	 * Constructs a new RpcException with the specified error code, detail message, and cause.
	 * This exception serves as a base for exceptions that represent issues encountered
	 * during remote procedure call (RPC) operations.
	 *
	 * @param code the error code representing the specific type of RPC error
	 * @param message the detail message explaining the reason for the exception
	 * @param cause the underlying cause of the exception, which may be {@code null} to indicate that
	 *              the cause is nonexistent or unknown
	 */
	public RpcException(int code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
	}

	/**
	 * Retrieves the error code associated with this exception.
	 * The error code signifies the specific type of failure
	 * encountered during the remote procedure call (RPC) operation.
	 *
	 * @return the error code representing the RPC error
	 */
	public int getCode() {
		return code;
	}
}