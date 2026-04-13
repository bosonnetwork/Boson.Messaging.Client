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
 * Exception indicating that the current session should be revoked.
 * This exception is typically thrown when an operation or request
 * is explicitly intended to terminate the current session.
 */
public class RevokeCurrentSessionException extends RpcException {
	private static final long serialVersionUID = -227849662954116980L;

	/**
	 * Constructs a new {@code RevokeCurrentSessionException} with the specified detail message.
	 * This exception indicates that the current session should be revoked, typically
	 * when a session-specific operation or request is intended to terminate the session.
	 *
	 * @param message the detail message explaining the reason for revoking the session.
	 */
	public RevokeCurrentSessionException(String message) {
		super(RpcErrorCode.REVOKE_CURRENT_SESSION, message);
	}

	/**
	 * Constructs a new {@code RevokeCurrentSessionException} with the specified detail message and cause.
	 * This exception signifies that the current session should be revoked due to a specific reason
	 * and provides additional context with the supplied cause.
	 *
	 * @param message the detail message explaining the reason for revoking the session.
	 * @param cause the underlying cause of the exception, which may provide further context.
	 */
	public RevokeCurrentSessionException(String message, Throwable cause) {
		super(RpcErrorCode.REVOKE_CURRENT_SESSION, message, cause);
	}
}