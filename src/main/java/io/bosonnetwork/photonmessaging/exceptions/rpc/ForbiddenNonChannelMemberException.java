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
 * Exception thrown when an operation is attempted by a user who is not a member
 * of the specified channel and lacks the necessary permissions.
 * It typically indicates that the user attempting the operation does not have membership
 * rights in the channel and is therefore prohibited from performing the requested action.
 */
public class ForbiddenNonChannelMemberException extends RpcException {
	private static final long serialVersionUID = -675909373237498284L;

	/**
	 * Constructs a new {@code ForbiddenNonChannelMemberException} with the specified
	 * detail message. This exception is thrown when an operation is attempted
	 * by a user who is not a member of the specified channel and lacks the necessary
	 * permissions.
	 *
	 * @param message the detail message that describes the reason for the exception.
	 */
	public ForbiddenNonChannelMemberException(String message) {
		super(RpcErrorCode.FORBIDDEN_NON_CHANNEL_MEMBER, message);
	}

	/**
	 * Constructs a new {@code ForbiddenNonChannelMemberException} with the specified
	 * detail message and cause. This exception is thrown when an operation is attempted
	 * by a user who is not a member of the specified channel and lacks the necessary
	 * permissions.
	 *
	 * @param message the detail message that describes the reason for the exception.
	 * @param cause the cause of the exception, which can be retrieved later using
	 *              {@code Throwable.getCause()}. A {@code null} value is permitted,
	 *              and indicates that the cause is nonexistent or unknown.
	 */
	public ForbiddenNonChannelMemberException(String message, Throwable cause) {
		super(RpcErrorCode.FORBIDDEN_NON_CHANNEL_MEMBER, message, cause);
	}
}