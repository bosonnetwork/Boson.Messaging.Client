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
 * Exception thrown to indicate that an attempt was made to join a channel
 * that has already been joined.
 * It can be used to signal situations where a user or entity attempts
 * to rejoin a channel they are already a member of, which violates the
 * intended protocol conditions or behavior.
 */
public class AlreadyJoinedException extends RpcException {
	private static final long serialVersionUID = 3700874214528678170L;

	/**
	 * Constructs a new {@code AlreadyJoinedException} with the specified detail message.
	 * This exception is thrown to indicate that an attempt was made to join a channel
	 * that has already been joined.
	 *
	 * @param message the detail message explaining the reason for the exception
	 */
	public AlreadyJoinedException(String message) {
		super(RpcErrorCode.ALREADY_JOINED_CHANNEL, message);
	}

	/**
	 * Constructs a new {@code AlreadyJoinedException} with the specified detail message and cause.
	 * This exception is thrown to indicate that an attempt was made to join a channel
	 * that has already been joined.
	 *
	 * @param message the detail message explaining the reason for the exception
	 * @param cause   the cause of the exception, which may be {@code null} to indicate that
	 *                the cause is nonexistent or unknown
	 */
	public AlreadyJoinedException(String message, Throwable cause) {
		super(RpcErrorCode.ALREADY_JOINED_CHANNEL, message, cause);
	}
}