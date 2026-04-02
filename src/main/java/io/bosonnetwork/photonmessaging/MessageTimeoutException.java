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

package io.bosonnetwork.photonmessaging;

/**
 * Thrown to indicate that a message processing operation has exceeded the allowable time limit.
 * This exception signals that a timeout occurred while waiting for a message operation to complete.
 */
public class MessageTimeoutException extends MessagingException {
	private static final long serialVersionUID = 1182250338303083202L;

	/**
	 * Constructs a new {@code MessageTimeoutException} with the specified detail message.
	 * This exception is thrown to indicate that a message processing operation has exceeded
	 * the allowable time limit, and a timeout has occurred.
	 *
	 * @param message the detail message providing additional information about the timeout.
	 */
	public MessageTimeoutException(String message) {
		super(message);
	}

	/**
	 * Constructs a new {@code MessageTimeoutException} with the specified detail message and cause.
	 * This exception is thrown to indicate that a message processing operation has exceeded
	 * the allowable time limit, resulting in a timeout.
	 *
	 * @param message the detail message providing additional information about the timeout.
	 * @param cause the cause of the exception, which can be retrieved later using the {@code getCause()} method.
	 *              A {@code null} value indicates that the cause is nonexistent or unknown.
	 */
	public MessageTimeoutException(String message, Throwable cause) {
		super(message, cause);
	}
}