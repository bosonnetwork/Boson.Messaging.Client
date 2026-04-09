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

package io.bosonnetwork.photonmessaging.exceptions;

/**
 * Thrown to indicate that a specific messaging channel does not exist.
 * This exception is a subclass of {@code MessagingException} and is used
 * to signify errors specific to the absence of a messaging channel.
 */
public class ChannelNotExistsException extends ContactNotExistsException {
	private static final long serialVersionUID = 1639133317420744651L;

	/**
	 * Constructs a new {@code ChannelNotExistsException} with the specified detail message.
	 *
	 * @param message the detail message, providing additional information about the error.
	 *                This message is saved for later retrieval by the {@code getMessage()} method.
	 */
	public ChannelNotExistsException(String message) {
		super(message);
	}

	/**
	 * Constructs a new {@code ChannelNotExistsException} with the specified detail message
	 * and cause.
	 *
	 * @param message the detail message, providing additional information about the error.
	 *                This message is saved for later retrieval by the {@code getMessage()} method.
	 * @param cause the cause of the exception, which is saved for later retrieval by the
	 *              {@code getCause()} method. A {@code null} value indicates that the cause
	 *              is nonexistent or unknown.
	 */
	public ChannelNotExistsException(String message, Throwable cause) {
		super(message, cause);
	}
}