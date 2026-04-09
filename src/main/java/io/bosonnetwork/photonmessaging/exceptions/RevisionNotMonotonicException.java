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
 * This exception is thrown when a revision in a contact operation is detected
 * to be non-monotonic. Non-monotonic revisions violate the expected sequence order,
 * potentially indicating an error in the messaging process.
 * <p>
 * The {@code RevisionNotMonotonicException} extends {@link MessagingException},
 * thereby inheriting its capabilities to hold a detailed message and an optional cause.
 */
public class RevisionNotMonotonicException extends MessagingException {
	private static final long serialVersionUID = 8997080698047645760L;

	/**
	 * Constructs a {@code RevisionNotMonotonicException} with the specified detail message.
	 * This exception is thrown when a revision in a contact operation violates
	 * the expected monotonic sequence, indicating a potential error in the process.
	 *
	 * @param message the detail message describing the specific issue. The message is saved
	 *                for later retrieval by the {@link #getMessage()} method.
	 */
	public RevisionNotMonotonicException(String message) {
		super(message);
	}

	/**
	 * Constructs a {@code RevisionNotMonotonicException} with the specified detail message
	 * and cause. This exception is thrown when a revision in a contact operation violates
	 * the expected monotonic sequence, indicating a potential error in the process.
	 *
	 * @param message the detail message describing the specific issue. This message is
	 *                saved for later retrieval by the {@link #getMessage()} method.
	 * @param cause   the cause of the exception, which is saved for later retrieval by
	 *                the {@link #getCause()} method. A {@code null} value indicates
	 *                that the cause is nonexistent or unknown.
	 */
	public RevisionNotMonotonicException(String message, Throwable cause) {
		super(message, cause);
	}
}