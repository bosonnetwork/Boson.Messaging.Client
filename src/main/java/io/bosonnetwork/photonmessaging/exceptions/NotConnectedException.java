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
 * Thrown when an operation that requires an active connection to the messaging service is attempted
 * while the client is not connected (for example, sending a message during a reconnect). This is a
 * transient condition: the client keeps trying to reconnect, so the caller may retry once the
 * connection is restored, rather than a terminal {@link ConnectionException}.
 */
public class NotConnectedException extends MessagingException {
	private static final long serialVersionUID = 6820498219239860231L;

	/**
	 * Constructs a new {@code NotConnectedException} with {@code null} as its detail message.
	 * This exception is typically thrown when an operation requiring an active connection
	 * is attempted while the client is not connected to the messaging service.
	 *
	 * The exception indicates a transient failure, as the client is expected to continue
	 * attempting reconnection. Callers may retry the operation once the connection is restored.
	 */
	public NotConnectedException() {
		super();
	}

	/**
	 * Constructs a new {@code NotConnectedException} with the specified detail message.
	 * This exception is typically thrown when an operation requiring an active connection
	 * is attempted while the client is not connected to the messaging service.
	 *
	 * The exception indicates a transient failure, as the client is expected to continue
	 * attempting reconnection. Callers may retry the operation once the connection is restored.
	 *
	 * @param message the detail message. The detail message is saved for later retrieval
	 *                by the {@link Throwable#getMessage()} method.
	 */
	public NotConnectedException(String message) {
		super(message);
	}

	/**
	 * Constructs a new {@code NotConnectedException} with the specified detail message
	 * and cause. This exception is typically thrown when an operation requiring an active
	 * connection is attempted while the client is not connected to the messaging service.
	 *
	 * The exception indicates a transient failure, as the client is expected to continue
	 * attempting reconnection. Callers may retry the operation once the connection is restored.
	 *
	 * @param message the detail message, which is saved for later retrieval by the
	 *                {@link Throwable#getMessage()} method.
	 * @param cause   the cause of the exception, which is saved for later retrieval by
	 *                the {@link Throwable#getCause()} method. A {@code null} value
	 *                indicates that the cause is nonexistent or unknown.
	 */
	public NotConnectedException(String message, Throwable cause) {
		super(message, cause);
	}
}