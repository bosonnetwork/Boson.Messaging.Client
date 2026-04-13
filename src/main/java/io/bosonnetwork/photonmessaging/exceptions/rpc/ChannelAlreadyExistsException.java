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
 * Exception thrown when attempting to create or register a channel that already exists.
 * This exception is typically used in situations where a unique channel identifier
 * conflicts with an existing channel, preventing the creation of duplicate channels.
 */
public class ChannelAlreadyExistsException extends RpcException {
	private static final long serialVersionUID = 3655849290770142141L;

	/**
	 * Constructs a {@code ChannelAlreadyExistsException} with the specified detail message.
	 * This exception is thrown when attempting to create or register a channel that already exists.
	 *
	 * @param message the detail message explaining the reason for the exception.
	 */
	public ChannelAlreadyExistsException(String message) {
		super(RpcErrorCode.CHANNEL_ALREADY_EXISTS, message);
	}

	/**
	 * Constructs a {@code ChannelAlreadyExistsException} with the specified detail message
	 * and cause. This exception is thrown when attempting to create or register a channel
	 * that already exists.
	 *
	 * @param message the detail message explaining the reason for the exception.
	 * @param cause the underlying cause of the exception, which is saved for later retrieval
	 *              by the {@link Throwable#getCause()} method.
	 */
	public ChannelAlreadyExistsException(String message, Throwable cause) {
		super(RpcErrorCode.CHANNEL_ALREADY_EXISTS, message, cause);
	}
}