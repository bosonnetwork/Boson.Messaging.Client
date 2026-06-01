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
 * This exception is thrown to indicate that an operation is forbidden
 * because the user is a banned member of a channel.
 * This exception typically arises when a banned user attempts to interact
 * with a channel for which their access has been restricted.
 */
public class ForbiddenBannedMemberException extends RpcException {
	private static final long serialVersionUID = 8986019310819396035L;

	/**
	 * Constructs a new {@code ForbiddenBannedMemberException} with the specified detail message.
	 * This exception is thrown when an operation is prohibited because the user is a banned member of a channel.
	 *
	 * @param message the detail message explaining the reason for the exception.
	 */
	public ForbiddenBannedMemberException(String message) {
		super(RpcErrorCode.FORBIDDEN_BANNED_CHANNEL_MEMBER, message);
	}

	/**
	 * Constructs a new {@code ForbiddenBannedMemberException} with the specified detail message
	 * and cause. This exception is thrown when an operation is prohibited because the user
	 * is a banned member of a channel.
	 *
	 * @param message the detail message explaining the reason for the exception.
	 * @param cause the underlying cause of the exception, which may provide additional context
	 *              for the failure.
	 */
	public ForbiddenBannedMemberException(String message, Throwable cause) {
		super(RpcErrorCode.FORBIDDEN_BANNED_CHANNEL_MEMBER, message, cause);
	}
}